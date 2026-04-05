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
 * Lineage check (Tier 3 — Epic 7, Story 7.3).
 *
 * <p>Detects upstream health signals by querying {@code v_dq_run_active} for other source systems
 * ({@code src_sys_nm} groups) that ran on the same {@code partition_date}. If any upstream
 * source system has failing datasets, it emits a WARN status to indicate potential data flow issues.
 *
 * <p>Emits:
 * <ul>
 *   <li>A {@code MetricNumeric} for {@code upstream_dataset_count} — total upstream datasets found.
 *   <li>A {@code MetricNumeric} for {@code upstream_failed_count} — upstream datasets with failing health.
 *   <li>A {@code MetricDetail} with status WARN (if any upstream failed) or PASS (if all healthy or no data).
 * </ul>
 *
 * <p>No Spark DataFrame operations — purely JDBC-based upstream health detection.
 */
public final class LineageCheck implements DqCheck {

    public static final String CHECK_TYPE = "LINEAGE";

    public static final String METRIC_UPSTREAM_DATASET_COUNT = "upstream_dataset_count";
    public static final String METRIC_UPSTREAM_FAILED_COUNT  = "upstream_failed_count";
    public static final String DETAIL_TYPE_STATUS            = "lineage_status";

    public static final double UPSTREAM_FAIL_SCORE_THRESHOLD = 60.0;
    public static final String SRC_SYS_SEGMENT_PREFIX        = "src_sys_nm=";

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_WARN    = "WARN";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_NO_SRC_SYS_SEGMENT  = "no_source_system_segment";
    private static final String REASON_NO_UPSTREAM_DATASETS = "no_upstream_datasets";
    private static final String REASON_EXECUTION_ERROR      = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(LineageCheck.class);

    private final LineageStatsProvider statsProvider;

    /**
     * No-arg constructor — delegates to {@link NoOpLineageStatsProvider}.
     *
     * <p>In production, use the JDBC-provider constructor.
     */
    public LineageCheck() {
        this(new NoOpLineageStatsProvider());
    }

    /**
     * Testable constructor accepting an explicit provider.
     *
     * @param statsProvider the provider for lineage stats
     * @throws IllegalArgumentException if provider is null
     */
    public LineageCheck(LineageStatsProvider statsProvider) {
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

            // Query lineage stats
            Optional<LineageStats> maybeStats =
                    statsProvider.getStats(srcSysNm, context.getPartitionDate());

            if (maybeStats.isEmpty()) {
                // No upstream datasets on this partition_date — graceful skip
                metrics.add(passDetail(REASON_NO_UPSTREAM_DATASETS));
                return metrics;
            }

            LineageStats stats = maybeStats.get();
            int upstreamDatasetCount = stats.upstreamDatasetCount();
            int upstreamFailedCount  = stats.upstreamFailedCount();

            // Emit numeric metrics
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_UPSTREAM_DATASET_COUNT, (double) upstreamDatasetCount));
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_UPSTREAM_FAILED_COUNT,  (double) upstreamFailedCount));

            // Determine status: if any upstream is failing → WARN; else PASS
            String status = upstreamFailedCount > 0 ? STATUS_WARN : STATUS_PASS;

            // Emit detail metric with full context payload
            metrics.add(statusDetail(status, srcSysNm, upstreamDatasetCount, upstreamFailedCount));

            return metrics;
        } catch (Exception e) {
            LOG.warn("LineageCheck execution error: {}", e.getMessage(), e);
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
                                       int upstreamDatasetCount, int upstreamFailedCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("src_sys_nm", srcSysNm);
        payload.put("upstream_dataset_count", upstreamDatasetCount);
        payload.put("upstream_failed_count", upstreamFailedCount);
        payload.put("upstream_fail_threshold", UPSTREAM_FAIL_SCORE_THRESHOLD);
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
     * Lineage stats for a dataset on a specific partition date.
     *
     * @param upstreamDatasetCount total number of upstream (other source system) datasets found
     * @param upstreamFailedCount  number of upstream datasets with failing health
     */
    public record LineageStats(int upstreamDatasetCount, int upstreamFailedCount) {}

    /**
     * Provider interface for lineage stats.
     *
     * <p>Returns {@link Optional#empty()} when no upstream datasets exist on this partition date
     * (i.e. {@code upstreamCount == 0}).
     */
    @FunctionalInterface
    public interface LineageStatsProvider {
        Optional<LineageStats> getStats(String srcSysNm, LocalDate partitionDate) throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link LineageStatsProvider} that queries {@code v_dq_run_active}.
     *
     * <p>Query 1 counts total upstream (other source system) datasets on the same partition_date.
     * Query 2 counts those with failing health (dqs_score &lt; threshold or check_status = 'FAIL').
     * Both queries use a single connection.
     */
    public static final class JdbcLineageStatsProvider implements LineageStatsProvider {

        private static final String UPSTREAM_TOTAL_QUERY =
                "SELECT COUNT(DISTINCT dataset_name) " +
                "FROM v_dq_run_active " +
                "WHERE dataset_name NOT LIKE ? " +
                "  AND partition_date = ? " +
                "  AND dqs_score IS NOT NULL";

        private static final String UPSTREAM_FAILED_QUERY =
                "SELECT COUNT(DISTINCT dataset_name) " +
                "FROM v_dq_run_active " +
                "WHERE dataset_name NOT LIKE ? " +
                "  AND partition_date = ? " +
                "  AND (dqs_score < ? OR check_status = 'FAIL') " +
                "  AND dqs_score IS NOT NULL";

        private final ConnectionProvider connectionProvider;

        public JdbcLineageStatsProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<LineageStats> getStats(String srcSysNm, LocalDate partitionDate)
                throws SQLException {
            if (srcSysNm == null || srcSysNm.isBlank()) {
                return Optional.empty();
            }
            String notLikePattern = "%src_sys_nm=" + srcSysNm + "/%";

            try (Connection conn = connectionProvider.getConnection()) {
                // Query 1: total count of upstream datasets (other source systems)
                int upstreamCount;
                try (PreparedStatement ps = conn.prepareStatement(UPSTREAM_TOTAL_QUERY)) {
                    ps.setString(1, notLikePattern);
                    ps.setDate(2, Date.valueOf(partitionDate));
                    try (ResultSet rs = ps.executeQuery()) {
                        upstreamCount = rs.next() ? rs.getInt(1) : 0;
                    }
                }

                if (upstreamCount == 0) {
                    return Optional.empty();
                }

                // Query 2: count of upstream datasets with failing health
                int failedCount;
                try (PreparedStatement ps = conn.prepareStatement(UPSTREAM_FAILED_QUERY)) {
                    ps.setString(1, notLikePattern);
                    ps.setDate(2, Date.valueOf(partitionDate));
                    ps.setDouble(3, UPSTREAM_FAIL_SCORE_THRESHOLD);
                    try (ResultSet rs = ps.executeQuery()) {
                        failedCount = rs.next() ? rs.getInt(1) : 0;
                    }
                }

                return Optional.of(new LineageStats(upstreamCount, failedCount));
            }
        }
    }

    /**
     * No-op {@link LineageStatsProvider} — always returns empty (check skipped).
     *
     * <p>Used as the default in the no-arg constructor.
     */
    private static final class NoOpLineageStatsProvider implements LineageStatsProvider {
        @Override
        public Optional<LineageStats> getStats(String srcSysNm, LocalDate partitionDate) {
            return Optional.empty();
        }
    }
}
