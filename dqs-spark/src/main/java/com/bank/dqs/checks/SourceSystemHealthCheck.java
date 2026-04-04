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
 * Source System Health check (Tier 3 — Epic 7, Story 7.1).
 *
 * <p>Aggregates DQS history across all datasets sharing the same {@code src_sys_nm}
 * path segment (parsed from {@code context.getDatasetName()}). Queries {@code v_dq_run_active}
 * for the last {@link #HISTORY_DAYS} days and computes the average DQS score.
 *
 * <p>Emits:
 * <ul>
 *   <li>A {@code MetricNumeric} for {@code aggregate_score} (average DQS across source system).
 *   <li>A {@code MetricNumeric} for {@code dataset_count} (distinct datasets in history window).
 *   <li>A {@code MetricDetail} with status FAIL/WARN/PASS based on thresholds.
 * </ul>
 *
 * <p>No Spark DataFrame operations — purely JDBC-based cross-dataset aggregation.
 */
public final class SourceSystemHealthCheck implements DqCheck {

    public static final String CHECK_TYPE = "SOURCE_SYSTEM_HEALTH";

    public static final String METRIC_AGGREGATE_SCORE = "aggregate_score";
    public static final String METRIC_DATASET_COUNT   = "dataset_count";
    public static final String DETAIL_TYPE_STATUS     = "source_system_health_status";

    public static final int    HISTORY_DAYS    = 7;
    public static final double FAIL_THRESHOLD  = 60.0;
    public static final double WARN_THRESHOLD  = 75.0;

    public static final String SRC_SYS_SEGMENT_PREFIX = "src_sys_nm=";

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_WARN    = "WARN";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_NO_SRC_SYS_SEGMENT = "no_source_system_segment";
    private static final String REASON_NO_HISTORY          = "no_history_available";
    private static final String REASON_EXECUTION_ERROR     = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(SourceSystemHealthCheck.class);

    private final SourceSystemStatsProvider statsProvider;

    /**
     * No-arg constructor used by {@code DqsJob.buildCheckFactory()} for no-op default.
     *
     * <p>In production, use the JDBC-provider constructor.
     */
    public SourceSystemHealthCheck() {
        this(new NoOpSourceSystemStatsProvider());
    }

    /**
     * Testable constructor accepting an explicit provider.
     *
     * @param statsProvider the provider for source system aggregate stats
     * @throws IllegalArgumentException if provider is null
     */
    public SourceSystemHealthCheck(SourceSystemStatsProvider statsProvider) {
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

            // Query historical stats
            Optional<SourceSystemStats> maybeStats =
                    statsProvider.getStats(srcSysNm, context.getPartitionDate());

            if (maybeStats.isEmpty()) {
                // No history in the window — graceful skip
                metrics.add(passDetail(REASON_NO_HISTORY));
                return metrics;
            }

            SourceSystemStats stats = maybeStats.get();
            double aggregateScore = stats.aggregateScore();
            int datasetCount = stats.datasetCount();

            // Emit numeric metrics
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_AGGREGATE_SCORE, aggregateScore));
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_DATASET_COUNT, (double) datasetCount));

            // Determine status
            String status;
            if (aggregateScore < FAIL_THRESHOLD) {
                status = STATUS_FAIL;
            } else if (aggregateScore < WARN_THRESHOLD) {
                status = STATUS_WARN;
            } else {
                status = STATUS_PASS;
            }

            // Emit detail metric with full context payload
            metrics.add(statusDetail(status, srcSysNm, aggregateScore, datasetCount));

            return metrics;
        } catch (Exception e) {
            LOG.warn("SourceSystemHealthCheck execution error: {}", e.getMessage(), e);
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Source system name extraction
    // -------------------------------------------------------------------------

    /**
     * Extract {@code src_sys_nm} value from the dataset name path.
     *
     * <p>Supports paths like {@code lob=retail/src_sys_nm=alpha/dataset=sales_daily}
     * or legacy {@code src_sys_nm=alpha/dataset=sales_daily}.
     */
    private static Optional<String> extractSrcSysNm(String datasetName) {
        if (datasetName == null) {
            return Optional.empty();
        }
        int start = datasetName.indexOf("src_sys_nm=");
        if (start < 0) {
            return Optional.empty();
        }
        int valueStart = start + "src_sys_nm=".length();
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
                                       double aggregateScore, int datasetCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("src_sys_nm", srcSysNm);
        payload.put("aggregate_score", aggregateScore);
        payload.put("dataset_count", datasetCount);
        payload.put("history_days", HISTORY_DAYS);
        payload.put("threshold_fail", FAIL_THRESHOLD);
        payload.put("threshold_warn", WARN_THRESHOLD);
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
     * Aggregate health stats for a source system over a recent time window.
     *
     * @param aggregateScore average DQS score across all datasets in the source system
     * @param datasetCount   number of distinct datasets found in the history window
     */
    public record SourceSystemStats(double aggregateScore, int datasetCount) {}

    /**
     * Provider interface for source system aggregate health stats.
     *
     * <p>Returns {@link Optional#empty()} when no history is found for the source system
     * in the configured time window.
     */
    @FunctionalInterface
    public interface SourceSystemStatsProvider {
        Optional<SourceSystemStats> getStats(String srcSysNm, LocalDate partitionDate)
                throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link SourceSystemStatsProvider} that queries {@code v_dq_run_active}.
     *
     * <p>Uses a LIKE pattern to match all datasets sharing the same {@code src_sys_nm} segment:
     * {@code '%src_sys_nm=<value>/%'}.
     */
    public static final class JdbcSourceSystemStatsProvider implements SourceSystemStatsProvider {

        private static final String STATS_QUERY =
                "SELECT AVG(dqs_score) AS avg_score, COUNT(DISTINCT dataset_name) AS ds_count " +
                "FROM v_dq_run_active " +
                "WHERE dataset_name LIKE ? " +
                "AND partition_date BETWEEN ? AND ? " +
                "AND dqs_score IS NOT NULL";

        private final ConnectionProvider connectionProvider;

        public JdbcSourceSystemStatsProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<SourceSystemStats> getStats(String srcSysNm, LocalDate partitionDate)
                throws SQLException {
            if (srcSysNm == null || srcSysNm.isBlank()) {
                return Optional.empty();
            }
            String likePattern = "%src_sys_nm=" + srcSysNm + "/%";
            LocalDate fromDate = partitionDate.minusDays(HISTORY_DAYS);

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(STATS_QUERY)) {
                ps.setString(1, likePattern);
                ps.setDate(2, Date.valueOf(fromDate));
                ps.setDate(3, Date.valueOf(partitionDate));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt("ds_count");
                        if (count == 0) {
                            return Optional.empty();
                        }
                        double avgScore = rs.getDouble("avg_score");
                        return Optional.of(new SourceSystemStats(avgScore, count));
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * No-op {@link SourceSystemStatsProvider} — always returns empty (check skipped).
     *
     * <p>Used as the default in the no-arg constructor.
     */
    private static final class NoOpSourceSystemStatsProvider implements SourceSystemStatsProvider {
        @Override
        public Optional<SourceSystemStats> getStats(String srcSysNm, LocalDate partitionDate) {
            return Optional.empty();
        }
    }
}
