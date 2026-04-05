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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Correlation check (Tier 3).
 *
 * <p>Detects cross-dataset co-degradation within a source system. For the dataset's
 * {@code src_sys_nm}, it queries {@code v_dq_run_active} to count how many datasets
 * in the same source system simultaneously experienced a DQS score drop of at least
 * {@link #DEGRADATION_THRESHOLD} points compared to their own recent baseline.
 *
 * <p>Emits:
 * <ul>
 *   <li>A {@code MetricNumeric} for {@code correlated_dataset_count} — number of co-degrading datasets.
 *   <li>A {@code MetricNumeric} for {@code correlation_ratio} — proportion of datasets that degraded.
 *   <li>A {@code MetricDetail} with status FAIL/WARN/PASS based on the ratio thresholds.
 * </ul>
 *
 * <p>No Spark DataFrame operations — purely JDBC-based cross-dataset co-degradation detection.
 */
public final class CorrelationCheck implements DqCheck {

    public static final String CHECK_TYPE = "CORRELATION";

    public static final String METRIC_CORRELATED_DATASET_COUNT = "correlated_dataset_count";
    public static final String METRIC_CORRELATION_RATIO        = "correlation_ratio";
    public static final String DETAIL_TYPE_STATUS              = "correlation_status";

    public static final int    HISTORY_DAYS          = 7;
    public static final double DEGRADATION_THRESHOLD = 10.0;
    public static final double FAIL_RATIO            = 0.50;
    public static final double WARN_RATIO            = 0.25;
    public static final String SRC_SYS_SEGMENT_PREFIX = "src_sys_nm=";

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_WARN    = "WARN";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_NO_SRC_SYS_SEGMENT = "no_source_system_segment";
    private static final String REASON_NO_CURRENT_DAY_DATA = "no_current_day_data";
    private static final String REASON_EXECUTION_ERROR     = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(CorrelationCheck.class);

    private final CorrelationStatsProvider statsProvider;

    /**
     * No-arg constructor — delegates to {@link NoOpCorrelationStatsProvider}.
     *
     * <p>In production, use the JDBC-provider constructor.
     */
    public CorrelationCheck() {
        this(new NoOpCorrelationStatsProvider());
    }

    /**
     * Testable constructor accepting an explicit provider.
     *
     * @param statsProvider the provider for correlation stats
     * @throws IllegalArgumentException if provider is null
     */
    public CorrelationCheck(CorrelationStatsProvider statsProvider) {
        if (statsProvider == null) {
            throw new IllegalArgumentException("statsProvider must not be null");
        }
        this.statsProvider = statsProvider;
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

            // Extract src_sys_nm from dataset_name path
            Optional<String> maybeSrcSysNm = extractSrcSysNm(context.getDatasetName());
            if (maybeSrcSysNm.isEmpty()) {
                // Graceful skip — no src_sys_nm segment in dataset path
                metrics.add(passDetail(REASON_NO_SRC_SYS_SEGMENT));
                return metrics;
            }

            String srcSysNm = maybeSrcSysNm.get();

            // Query correlation stats
            Optional<CorrelationStats> maybeStats =
                    statsProvider.getStats(srcSysNm, context.getPartitionDate());

            if (maybeStats.isEmpty()) {
                // No current-day data for this source system — graceful skip
                metrics.add(passDetail(REASON_NO_CURRENT_DAY_DATA));
                return metrics;
            }

            CorrelationStats stats = maybeStats.get();
            int correlatedCount = stats.correlatedDatasetCount();
            int totalCount = stats.totalDatasetCount();
            double ratio = stats.correlationRatio();

            // Emit numeric metrics
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_CORRELATED_DATASET_COUNT, (double) correlatedCount));
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_CORRELATION_RATIO, ratio));

            // Determine status
            String status;
            if (ratio >= FAIL_RATIO) {
                status = STATUS_FAIL;
            } else if (ratio >= WARN_RATIO) {
                status = STATUS_WARN;
            } else {
                status = STATUS_PASS;
            }

            // Emit detail metric with full context payload
            metrics.add(statusDetail(status, srcSysNm, correlatedCount, totalCount, ratio));

            return metrics;
        } catch (Exception e) {
            LOG.warn("CorrelationCheck execution error: {}", e.getMessage(), e);
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Source system name extraction — duplicated per-class (self-containment pattern)
    // -------------------------------------------------------------------------

    /**
     * Extract {@code src_sys_nm} value from the dataset name path.
     *
     * <p>Supports paths like {@code lob=retail/src_sys_nm=alpha/dataset=sales_daily}.
     */
    private static Optional<String> extractSrcSysNm(String datasetName) {
        if (datasetName == null) {
            return Optional.empty();
        }
        int start = datasetName.indexOf(SRC_SYS_SEGMENT_PREFIX);
        if (start < 0) {
            return Optional.empty();
        }
        int valueStart = start + SRC_SYS_SEGMENT_PREFIX.length();
        int end = datasetName.indexOf('/', valueStart);
        String value = end < 0
                ? datasetName.substring(valueStart)
                : datasetName.substring(valueStart, end);
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    // -------------------------------------------------------------------------
    // Payload helpers
    // -------------------------------------------------------------------------

    private MetricDetail statusDetail(String status, String srcSysNm,
                                       int correlatedCount, int totalCount, double ratio) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("src_sys_nm", srcSysNm);
        payload.put("correlated_dataset_count", correlatedCount);
        payload.put("total_dataset_count", totalCount);
        payload.put("correlation_ratio", ratio);
        payload.put("history_days", HISTORY_DAYS);
        payload.put("threshold_fail", FAIL_RATIO);
        payload.put("threshold_warn", WARN_RATIO);
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
     * Correlation stats for a source system on a specific partition date.
     *
     * @param correlatedDatasetCount number of datasets that degraded simultaneously
     * @param totalDatasetCount      total number of datasets in the source system with current-day runs
     * @param correlationRatio       proportion of datasets that degraded (correlatedCount / totalCount)
     */
    public record CorrelationStats(int correlatedDatasetCount, int totalDatasetCount, double correlationRatio) {}

    /**
     * Provider interface for correlation stats.
     *
     * <p>Returns {@link Optional#empty()} when no current-day data exists for the source system
     * (i.e. {@code totalCount == 0}).
     */
    @FunctionalInterface
    public interface CorrelationStatsProvider {
        Optional<CorrelationStats> getStats(String srcSysNm, LocalDate partitionDate) throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link CorrelationStatsProvider} that queries {@code v_dq_run_active}.
     *
     * <p>Query 1 counts the total number of distinct datasets in the source system with runs
     * on the current partition date. Query 2 counts how many of those datasets experienced a
     * DQS score drop of at least {@link #DEGRADATION_THRESHOLD} compared to their own
     * prior-period baseline (average of the preceding {@link #HISTORY_DAYS} days).
     */
    public static final class JdbcCorrelationStatsProvider implements CorrelationStatsProvider {

        private static final String TOTAL_COUNT_QUERY =
                "SELECT COUNT(DISTINCT dataset_name) " +
                "FROM v_dq_run_active " +
                "WHERE dataset_name LIKE ? " +
                "  AND partition_date = ? " +
                "  AND dqs_score IS NOT NULL";

        private static final String CORRELATED_COUNT_QUERY =
                "SELECT COUNT(DISTINCT r.dataset_name) " +
                "FROM v_dq_run_active r " +
                "WHERE r.dataset_name LIKE ? " +
                "  AND r.partition_date = ? " +
                "  AND r.dqs_score IS NOT NULL " +
                "  AND r.dqs_score < (" +
                "      SELECT AVG(h.dqs_score) - ? " +
                "      FROM v_dq_run_active h " +
                "      WHERE h.dataset_name = r.dataset_name " +
                "        AND h.partition_date BETWEEN ? AND ? " +
                "        AND h.dqs_score IS NOT NULL" +
                "  )";

        private final ConnectionProvider connectionProvider;

        public JdbcCorrelationStatsProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<CorrelationStats> getStats(String srcSysNm, LocalDate partitionDate)
                throws SQLException {
            if (srcSysNm == null || srcSysNm.isBlank()) {
                return Optional.empty();
            }
            String likePattern = "%src_sys_nm=" + srcSysNm + "/%";

            try (Connection conn = connectionProvider.getConnection()) {
                // Query 1: total count of datasets with current-day runs
                int totalCount;
                try (PreparedStatement ps = conn.prepareStatement(TOTAL_COUNT_QUERY)) {
                    ps.setString(1, likePattern);
                    ps.setDate(2, Date.valueOf(partitionDate));
                    try (ResultSet rs = ps.executeQuery()) {
                        totalCount = rs.next() ? rs.getInt(1) : 0;
                    }
                }

                if (totalCount == 0) {
                    return Optional.empty();
                }

                // Query 2: count of correlated (co-degraded) datasets
                int correlatedCount;
                try (PreparedStatement ps = conn.prepareStatement(CORRELATED_COUNT_QUERY)) {
                    ps.setString(1, likePattern);
                    ps.setDate(2, Date.valueOf(partitionDate));
                    ps.setDouble(3, DEGRADATION_THRESHOLD);
                    ps.setDate(4, Date.valueOf(partitionDate.minusDays(HISTORY_DAYS + 1)));
                    ps.setDate(5, Date.valueOf(partitionDate.minusDays(1)));
                    try (ResultSet rs = ps.executeQuery()) {
                        correlatedCount = rs.next() ? rs.getInt(1) : 0;
                    }
                }

                double ratio = (double) correlatedCount / Math.max(totalCount, 1);
                return Optional.of(new CorrelationStats(correlatedCount, totalCount, ratio));
            }
        }
    }

    /**
     * No-op {@link CorrelationStatsProvider} — always returns empty (check skipped).
     *
     * <p>Used as the default in the no-arg constructor.
     */
    private static final class NoOpCorrelationStatsProvider implements CorrelationStatsProvider {
        @Override
        public Optional<CorrelationStats> getStats(String srcSysNm, LocalDate partitionDate) {
            return Optional.empty();
        }
    }
}
