package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orphan Detection check (Tier 3).
 *
 * <p>Detects datasets that have DQS run history but no active {@code check_config} entries —
 * datasets being processed by the Spark job without any quality checks configured. Queries
 * {@code v_check_config_active} using the LIKE reversal pattern:
 * {@code WHERE ? LIKE dataset_pattern AND enabled = TRUE}.
 *
 * <p>Emits:
 * <ul>
 *   <li>A {@code MetricDetail} with status FAIL and reason=no_check_config (orphan detected).
 *   <li>A {@code MetricDetail} with status PASS and reason=check_config_present (configured).
 * </ul>
 *
 * <p>No MetricNumeric emitted — this is a binary (present/absent) check.
 * No Spark DataFrame operations — purely JDBC-based.
 */
public final class OrphanDetectionCheck implements DqCheck {

    public static final String CHECK_TYPE        = "ORPHAN_DETECTION";
    public static final String DETAIL_TYPE_STATUS = "orphan_detection_status";

    public static final String REASON_NO_CHECK_CONFIG     = "no_check_config";
    public static final String REASON_CHECK_CONFIG_PRESENT = "check_config_present";

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_EXECUTION_ERROR = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(OrphanDetectionCheck.class);

    private final CheckConfigProvider configProvider;

    /**
     * No-arg constructor — delegates to {@link NoOpCheckConfigProvider}.
     *
     * <p>In production, use the JDBC-provider constructor.
     */
    public OrphanDetectionCheck() {
        this(new NoOpCheckConfigProvider());
    }

    /**
     * Testable constructor accepting an explicit provider.
     *
     * @param configProvider the provider for check config existence
     * @throws IllegalArgumentException if provider is null
     */
    public OrphanDetectionCheck(CheckConfigProvider configProvider) {
        if (configProvider == null) {
            throw new IllegalArgumentException("configProvider must not be null");
        }
        this.configProvider = configProvider;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public List<DqMetric> execute(DatasetContext context) {
        List<DqMetric> metrics = new ArrayList<>();
        try {
            // Guard: null context or null dataset_name → NOT_RUN
            if (context == null) {
                metrics.add(notRunDetail("missing_context"));
                return metrics;
            }
            if (context.getDatasetName() == null) {
                metrics.add(notRunDetail("missing_dataset_name"));
                return metrics;
            }

            // Query whether any enabled check_config exists for this dataset
            boolean hasConfig = configProvider.hasEnabledCheckConfig(context.getDatasetName());

            if (hasConfig) {
                metrics.add(passDetail(REASON_CHECK_CONFIG_PRESENT));
            } else {
                metrics.add(failDetail(REASON_NO_CHECK_CONFIG));
            }

            return metrics;
        } catch (Exception e) {
            LOG.warn("OrphanDetectionCheck execution error: {}", e.getMessage(), e);
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Payload helpers
    // -------------------------------------------------------------------------

    private MetricDetail failDetail(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_FAIL);
        payload.put("reason", reason);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail passDetail(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_PASS);
        payload.put("reason", reason);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail notRunDetail(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", reason);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail errorDetail(Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("message", safeMessage(exception));
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"NOT_RUN\",\"reason\":\"execution_error\"}";
        }
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Provider interface for check config existence.
     *
     * <p>Returns {@code true} if at least one enabled {@code check_config} row matches the
     * dataset name (using LIKE reversal pattern), {@code false} if the dataset is orphaned.
     */
    @FunctionalInterface
    public interface CheckConfigProvider {
        boolean hasEnabledCheckConfig(String datasetName) throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link CheckConfigProvider} that queries {@code v_check_config_active}.
     *
     * <p>Uses the LIKE reversal pattern: the dataset name is tested against stored
     * {@code dataset_pattern} values using {@code WHERE ? LIKE dataset_pattern AND enabled = TRUE}.
     */
    public static final class JdbcCheckConfigProvider implements CheckConfigProvider {

        private static final String CHECK_CONFIG_QUERY =
                "SELECT COUNT(*) " +
                "FROM v_check_config_active " +
                "WHERE ? LIKE dataset_pattern " +
                "  AND enabled = TRUE";

        private final ConnectionProvider connectionProvider;

        public JdbcCheckConfigProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public boolean hasEnabledCheckConfig(String datasetName) throws SQLException {
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(CHECK_CONFIG_QUERY)) {
                ps.setString(1, datasetName);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }
    }

    /**
     * No-op {@link CheckConfigProvider} — always returns {@code false} (dataset assumed orphan).
     *
     * <p>Used as the default in the no-arg constructor. Production ALWAYS uses
     * {@link JdbcCheckConfigProvider}.
     */
    private static final class NoOpCheckConfigProvider implements CheckConfigProvider {
        @Override
        public boolean hasEnabledCheckConfig(String datasetName) {
            return false;
        }
    }
}
