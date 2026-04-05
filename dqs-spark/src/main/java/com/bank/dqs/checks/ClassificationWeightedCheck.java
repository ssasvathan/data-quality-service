package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
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
import java.util.Optional;

/**
 * Classification-Weighted Alerting check (Tier 3).
 *
 * <p>Resolves the dataset's LOB classification from {@code v_lob_lookup_active}
 * (via {@code context.getLookupCode()}) and emits:
 * <ul>
 *   <li>A {@code MetricNumeric} priority multiplier signal: 2.0 for Tier 1 Critical,
 *       1.0 for Tier 2 Standard, 0.5 for unknown/low-priority.
 *   <li>A {@code MetricDetail} status payload with classification context.
 * </ul>
 *
 * <p>This check runs BEFORE {@link DqsScoreCheck}. The DQS score has NOT yet been
 * computed when this check executes. The multiplier is therefore a classification
 * priority signal for downstream alerting systems, not a real-time score modifier.
 *
 * <p>No Spark DataFrame operations — purely JDBC-based classification lookup.
 */
public final class ClassificationWeightedCheck implements DqCheck {

    public static final String CHECK_TYPE = "CLASSIFICATION_WEIGHTED";

    public static final String METRIC_WEIGHTED_SCORE = "classification_weighted_score";
    public static final String DETAIL_TYPE_STATUS    = "classification_weighted_status";

    public static final double CRITICAL_MULTIPLIER = 2.0;
    public static final double STANDARD_MULTIPLIER = 1.0;
    public static final double LOW_MULTIPLIER       = 0.5;

    public static final String CLASSIFICATION_CRITICAL = "Tier 1 Critical";
    public static final String CLASSIFICATION_STANDARD = "Tier 2 Standard";

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_CLASSIFICATION_CAPTURED   = "classification_captured";
    private static final String REASON_NO_CLASSIFICATION_FOUND   = "no_classification_found";
    private static final String REASON_EXECUTION_ERROR           = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(ClassificationWeightedCheck.class);

    private final ClassificationProvider classificationProvider;

    /**
     * No-arg constructor used by {@code DqsJob.buildCheckFactory()} for no-op default.
     *
     * <p>In production, use the JDBC-provider constructor.
     */
    public ClassificationWeightedCheck() {
        this(new NoOpClassificationProvider());
    }

    /**
     * Testable constructor accepting an explicit provider.
     *
     * @param classificationProvider the provider for dataset classification lookup
     * @throws IllegalArgumentException if provider is null
     */
    public ClassificationWeightedCheck(ClassificationProvider classificationProvider) {
        if (classificationProvider == null) {
            throw new IllegalArgumentException("classificationProvider must not be null");
        }
        this.classificationProvider = classificationProvider;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public List<DqMetric> execute(DatasetContext context) {
        List<DqMetric> metrics = new ArrayList<>();
        try {
            // Guard: null context → NOT_RUN
            if (context == null) {
                metrics.add(notRunDetail("missing_context"));
                return metrics;
            }

            // Call provider — if lookup_code is null, provider may return empty
            Optional<String> maybeClassification = classificationProvider.getClassification(context);

            if (maybeClassification.isEmpty()) {
                // Graceful skip — no classification data found
                metrics.add(passDetail(REASON_NO_CLASSIFICATION_FOUND));
                return metrics;
            }

            String classification = maybeClassification.get();

            // Determine multiplier from classification string
            double multiplier;
            if (CLASSIFICATION_CRITICAL.equals(classification)) {
                multiplier = CRITICAL_MULTIPLIER;
            } else if (CLASSIFICATION_STANDARD.equals(classification)) {
                multiplier = STANDARD_MULTIPLIER;
            } else {
                multiplier = LOW_MULTIPLIER;
            }

            // Emit MetricNumeric — multiplier IS the meaningful metric (priority signal)
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_WEIGHTED_SCORE, multiplier));

            // Emit MetricDetail — status is always PASS (informational check, not a quality failure)
            metrics.add(classificationDetail(STATUS_PASS, REASON_CLASSIFICATION_CAPTURED,
                    classification, multiplier));

            return metrics;
        } catch (Exception e) {
            LOG.warn("ClassificationWeightedCheck execution error: {}", e.getMessage(), e);
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Payload helpers
    // -------------------------------------------------------------------------

    private MetricDetail classificationDetail(String status, String reason,
                                               String classification, double multiplier) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("classification", classification);
        payload.put("multiplier", multiplier);
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
    // Inner interfaces and implementations
    // -------------------------------------------------------------------------

    /**
     * Provider interface for dataset classification lookup.
     *
     * <p>Returns {@link Optional#empty()} when no classification row is found
     * (or {@code lookup_code} is null — graceful skip).
     */
    @FunctionalInterface
    public interface ClassificationProvider {
        Optional<String> getClassification(DatasetContext ctx) throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link ClassificationProvider} that queries {@code v_lob_lookup_active}.
     */
    public static final class JdbcClassificationProvider implements ClassificationProvider {

        private static final String CLASSIFICATION_QUERY =
                "SELECT classification FROM v_lob_lookup_active WHERE lookup_code = ?";

        private final ConnectionProvider connectionProvider;

        public JdbcClassificationProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<String> getClassification(DatasetContext ctx) throws SQLException {
            if (ctx == null) {
                throw new IllegalArgumentException("context must not be null");
            }
            String lookupCode = ctx.getLookupCode();
            if (lookupCode == null) {
                return Optional.empty();
            }
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(CLASSIFICATION_QUERY)) {
                ps.setString(1, lookupCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String classification = rs.getString(1);
                        if (classification != null && !classification.isBlank()) {
                            return Optional.of(classification);
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * No-op {@link ClassificationProvider} — always returns empty (check skipped).
     *
     * <p>Used as the default in the no-arg constructor.
     */
    private static final class NoOpClassificationProvider implements ClassificationProvider {
        @Override
        public Optional<String> getClassification(DatasetContext ctx) {
            return Optional.empty();
        }
    }
}
