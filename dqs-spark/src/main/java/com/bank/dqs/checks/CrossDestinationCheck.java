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
 * Cross-Destination Consistency check (Tier 3 — Epic 7, Story 7.3).
 *
 * <p>Detects datasets replicated to multiple destinations (same {@code dataset_name} appearing
 * across multiple rows on the same {@code partition_date} in {@code v_dq_run_active}).
 * Compares DQS scores across all active runs and flags significant score divergence.
 *
 * <p>Emits:
 * <ul>
 *   <li>A {@code MetricNumeric} for {@code destination_count} — total distinct runs found.
 *   <li>A {@code MetricNumeric} for {@code inconsistent_destination_count} — destinations
 *       whose DQS score deviates from the mean by more than {@link #SCORE_DEVIATION_THRESHOLD}.
 *   <li>A {@code MetricDetail} with status FAIL (if inconsistency detected) or PASS (if consistent).
 * </ul>
 *
 * <p>No Spark DataFrame operations — purely JDBC-based cross-destination consistency detection.
 */
public final class CrossDestinationCheck implements DqCheck {

    public static final String CHECK_TYPE = "CROSS_DESTINATION";

    public static final String METRIC_DESTINATION_COUNT              = "destination_count";
    public static final String METRIC_INCONSISTENT_DESTINATION_COUNT = "inconsistent_destination_count";
    public static final String DETAIL_TYPE_STATUS                    = "cross_destination_status";

    public static final double SCORE_DEVIATION_THRESHOLD = 15.0;

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_SINGLE_DESTINATION = "single_destination";
    private static final String REASON_EXECUTION_ERROR    = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(CrossDestinationCheck.class);

    private final DestinationStatsProvider statsProvider;

    /**
     * No-arg constructor — delegates to {@link NoOpDestinationStatsProvider}.
     *
     * <p>In production, use the JDBC-provider constructor.
     */
    public CrossDestinationCheck() {
        this(new NoOpDestinationStatsProvider());
    }

    /**
     * Testable constructor accepting an explicit provider.
     *
     * @param statsProvider the provider for destination stats
     * @throws IllegalArgumentException if provider is null
     */
    public CrossDestinationCheck(DestinationStatsProvider statsProvider) {
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

            // Query destination stats
            Optional<DestinationStats> maybeStats =
                    statsProvider.getStats(context.getDatasetName(), context.getPartitionDate());

            if (maybeStats.isEmpty()) {
                // Single or no destination — no cross-destination comparison possible
                metrics.add(passDetail(REASON_SINGLE_DESTINATION));
                return metrics;
            }

            DestinationStats stats = maybeStats.get();
            int destinationCount             = stats.destinationCount();
            int inconsistentDestinationCount = stats.inconsistentDestinationCount();
            double meanScore                 = stats.meanScore();
            double maxDeviation              = stats.maxDeviation();

            // Emit numeric metrics
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_DESTINATION_COUNT,              (double) destinationCount));
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_INCONSISTENT_DESTINATION_COUNT, (double) inconsistentDestinationCount));

            // Determine status
            String status = inconsistentDestinationCount > 0 ? STATUS_FAIL : STATUS_PASS;

            // Emit detail metric with full context payload
            metrics.add(statusDetail(status, destinationCount, inconsistentDestinationCount, meanScore, maxDeviation));

            return metrics;
        } catch (Exception e) {
            LOG.warn("CrossDestinationCheck execution error: {}", e.getMessage(), e);
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Payload helpers
    // -------------------------------------------------------------------------

    private MetricDetail statusDetail(String status, int destinationCount,
                                       int inconsistentDestinationCount,
                                       double meanScore, double maxDeviation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("destination_count", destinationCount);
        payload.put("inconsistent_destination_count", inconsistentDestinationCount);
        payload.put("mean_score", meanScore);
        payload.put("max_deviation", maxDeviation);
        payload.put("threshold", SCORE_DEVIATION_THRESHOLD);
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
     * Destination stats for a dataset on a specific partition date.
     *
     * @param destinationCount             total number of distinct runs (destinations)
     * @param inconsistentDestinationCount number of destinations whose score deviates from mean
     * @param meanScore                    average DQS score across all destinations
     * @param maxDeviation                 maximum absolute deviation from the mean score
     */
    public record DestinationStats(int destinationCount, int inconsistentDestinationCount,
                                   double meanScore, double maxDeviation) {}

    /**
     * Provider interface for destination stats.
     *
     * <p>Returns {@link Optional#empty()} when only one (or zero) runs exist for this
     * dataset on the partition date (single destination — no cross-destination comparison possible).
     */
    @FunctionalInterface
    public interface DestinationStatsProvider {
        Optional<DestinationStats> getStats(String datasetName, LocalDate partitionDate) throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link DestinationStatsProvider} that queries {@code v_dq_run_active}.
     *
     * <p>Fetches all DQS scores for the dataset_name on the given partition_date, then
     * computes the mean and counts destinations that deviate by more than
     * {@link #SCORE_DEVIATION_THRESHOLD} from the mean.
     */
    public static final class JdbcDestinationStatsProvider implements DestinationStatsProvider {

        private static final String DESTINATION_SCORES_QUERY =
                "SELECT dqs_score " +
                "FROM v_dq_run_active " +
                "WHERE dataset_name = ? " +
                "  AND partition_date = ? " +
                "  AND dqs_score IS NOT NULL";

        private final ConnectionProvider connectionProvider;

        public JdbcDestinationStatsProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<DestinationStats> getStats(String datasetName, LocalDate partitionDate)
                throws SQLException {
            List<Double> scores = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DESTINATION_SCORES_QUERY)) {
                ps.setString(1, datasetName);
                ps.setDate(2, Date.valueOf(partitionDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        scores.add(rs.getDouble(1));
                    }
                }
            }

            // Single or no destination — no cross-destination comparison possible
            if (scores.size() <= 1) {
                return Optional.empty();
            }

            // Compute mean
            double sum = 0.0;
            for (double score : scores) {
                sum += score;
            }
            double meanScore = sum / scores.size();

            // Count inconsistent destinations and find max deviation
            int inconsistentCount = 0;
            double maxDeviation = 0.0;
            for (double score : scores) {
                double deviation = Math.abs(score - meanScore);
                if (deviation > maxDeviation) {
                    maxDeviation = deviation;
                }
                if (deviation > SCORE_DEVIATION_THRESHOLD) {
                    inconsistentCount++;
                }
            }

            return Optional.of(new DestinationStats(scores.size(), inconsistentCount, meanScore, maxDeviation));
        }
    }

    /**
     * No-op {@link DestinationStatsProvider} — always returns empty (check skipped).
     *
     * <p>Used as the default in the no-arg constructor.
     */
    private static final class NoOpDestinationStatsProvider implements DestinationStatsProvider {
        @Override
        public Optional<DestinationStats> getStats(String datasetName, LocalDate partitionDate) {
            return Optional.empty();
        }
    }
}
