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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inferred SLA check (Tier 3).
 *
 * <p>Infers an SLA window from 30 days of historical freshness data for datasets that have
 * no explicit SLA configured in {@code dataset_enrichment}. Queries
 * {@code v_dq_metric_numeric_active} (joined to {@code v_dq_run_active}) for {@code FRESHNESS} /
 * {@code hours_since_update} values, computes {@code inferredSlaHours = mean + 2.0 * stddev},
 * and emits metrics indicating whether the current delivery is within or beyond the inferred window.
 *
 * <p>Emits (when applicable):
 * <ul>
 *   <li>A {@code MetricNumeric} for {@code inferred_sla_hours} — the computed upper-bound estimate.
 *   <li>A {@code MetricNumeric} for {@code deviation_from_inferred} — current hours minus inferred SLA.
 *   <li>A {@code MetricDetail} with status FAIL/WARN/PASS based on deviation.
 * </ul>
 *
 * <p>Graceful skips (returns empty list or PASS detail):
 * <ul>
 *   <li>Explicit SLA configured → return empty list ({@code SlaCountdownCheck} handles this dataset).
 *   <li>Fewer than {@link #MIN_DATA_POINTS} data points → MetricDetail status=PASS, reason=insufficient_history.
 *   <li>Null context → MetricDetail status=NOT_RUN, no exception.
 * </ul>
 *
 * <p>No Spark DataFrame operations — purely JDBC-based single-dataset history analysis.
 */
public final class InferredSlaCheck implements DqCheck {

    public static final String CHECK_TYPE = "INFERRED_SLA";

    public static final String METRIC_INFERRED_SLA_HOURS      = "inferred_sla_hours";
    public static final String METRIC_DEVIATION_FROM_INFERRED = "deviation_from_inferred";
    public static final String DETAIL_TYPE_STATUS              = "inferred_sla_status";

    public static final int    HISTORY_DAYS         = 30;
    public static final int    MIN_DATA_POINTS      = 7;
    public static final double WARN_DEVIATION_RATIO = 0.20;

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_WARN    = "WARN";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_INSUFFICIENT_HISTORY = "insufficient_history";
    private static final String REASON_EXECUTION_ERROR      = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(InferredSlaCheck.class);

    private final SlaHistoryProvider historyProvider;

    /**
     * No-arg constructor — delegates to {@link NoOpSlaHistoryProvider}.
     *
     * <p>In production, use the JDBC-provider constructor.
     */
    public InferredSlaCheck() {
        this(new NoOpSlaHistoryProvider());
    }

    /**
     * Testable constructor accepting an explicit provider.
     *
     * @param historyProvider the provider for SLA history data
     * @throws IllegalArgumentException if provider is null
     */
    public InferredSlaCheck(SlaHistoryProvider historyProvider) {
        if (historyProvider == null) {
            throw new IllegalArgumentException("historyProvider must not be null");
        }
        this.historyProvider = historyProvider;
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

            // Query SLA history
            Optional<SlaHistory> maybeHistory = historyProvider.getHistory(context);

            if (maybeHistory.isEmpty()) {
                // No data available — return empty list (not even a graceful skip metric)
                return metrics;
            }

            SlaHistory history = maybeHistory.get();

            // Explicit SLA configured → SlaCountdownCheck handles this dataset
            if (history.hasExplicitSla()) {
                return metrics;
            }

            List<Double> hoursHistory = history.hoursHistory();

            // Insufficient history — graceful skip
            if (hoursHistory.size() < MIN_DATA_POINTS) {
                metrics.add(insufficientHistoryDetail(hoursHistory.size()));
                return metrics;
            }

            // Current delivery = last entry in history (most recent partition_date)
            double currentHours = hoursHistory.get(hoursHistory.size() - 1);

            // Compute statistics from HISTORICAL values only (all except current day)
            // The inferred window must not be skewed by today's anomalous delivery
            List<Double> historicalValues = hoursHistory.subList(0, hoursHistory.size() - 1);
            double mean = computeMean(historicalValues);
            double stddev = computeStdDev(historicalValues, mean);
            double inferredSlaHours = mean + 2.0 * stddev;

            double deviationFromInferred = currentHours - inferredSlaHours;

            // Emit numeric metrics
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_INFERRED_SLA_HOURS, inferredSlaHours));
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_DEVIATION_FROM_INFERRED, deviationFromInferred));

            // Determine status
            // WARN zone: delivery is within WARN_DEVIATION_RATIO of the inferred window.
            // Guard: if inferredSlaHours <= 0 (pathological high-variance data), the WARN threshold
            // would be non-negative, collapsing the zone — skip WARN and emit PASS instead.
            String status;
            if (deviationFromInferred > 0.0) {
                status = STATUS_FAIL;
            } else if (inferredSlaHours > 0.0 && deviationFromInferred > -inferredSlaHours * WARN_DEVIATION_RATIO) {
                status = STATUS_WARN;
            } else {
                status = STATUS_PASS;
            }

            // Emit detail metric with full statistics payload (data_points = historical values used for inference, excluding current day)
            metrics.add(statusDetail(status, currentHours, inferredSlaHours,
                    deviationFromInferred, mean, stddev, historicalValues.size()));

            return metrics;
        } catch (Exception e) {
            LOG.warn("InferredSlaCheck execution error: {}", e.getMessage(), e);
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Statistics computation (pure Java, no Spark)
    // -------------------------------------------------------------------------

    private static double computeMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double computeStdDev(List<Double> values, double mean) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sumSqDiff = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .sum();
        return Math.sqrt(sumSqDiff / values.size());
    }

    // -------------------------------------------------------------------------
    // Payload helpers
    // -------------------------------------------------------------------------

    private MetricDetail statusDetail(String status, double currentHours, double inferredSlaHours,
                                       double deviationFromInferred, double mean, double stddev,
                                       int dataPoints) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("current_hours", currentHours);
        payload.put("inferred_sla_hours", inferredSlaHours);
        payload.put("deviation_from_inferred", deviationFromInferred);
        payload.put("mean_hours", mean);
        payload.put("stddev_hours", stddev);
        payload.put("data_points", dataPoints);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail insufficientHistoryDetail(int dataPoints) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_PASS);
        payload.put("reason", REASON_INSUFFICIENT_HISTORY);
        payload.put("data_points", dataPoints);
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
     * Historical SLA data for a dataset.
     *
     * @param hoursHistory  list of {@code hours_since_update} values ordered by partition_date ASC
     *                      (last entry = current day's freshness value)
     * @param hasExplicitSla true if {@code dataset_enrichment} has a non-null {@code sla_hours}
     *                       for this dataset
     */
    public record SlaHistory(List<Double> hoursHistory, boolean hasExplicitSla) {}

    /**
     * Provider interface for SLA history data.
     *
     * <p>Returns {@link Optional#empty()} only on connection failure.
     * An empty {@code hoursHistory} list signals "no history" (caller checks list size).
     */
    @FunctionalInterface
    public interface SlaHistoryProvider {
        Optional<SlaHistory> getHistory(DatasetContext ctx) throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link SlaHistoryProvider} that queries {@code v_dataset_enrichment_active}
     * for explicit SLA configuration and {@code v_dq_metric_numeric_active} for freshness history.
     *
     * <p>Both queries run on a single connection using separate {@link PreparedStatement}s.
     */
    public static final class JdbcSlaHistoryProvider implements SlaHistoryProvider {

        private static final String EXPLICIT_SLA_QUERY =
                "SELECT sla_hours FROM v_dataset_enrichment_active " +
                "WHERE ? LIKE dataset_pattern AND sla_hours IS NOT NULL " +
                "ORDER BY id ASC LIMIT 1";

        private static final String FRESHNESS_HISTORY_QUERY =
                "SELECT mn.metric_value " +
                "FROM v_dq_metric_numeric_active mn " +
                "JOIN v_dq_run_active r ON mn.dq_run_id = r.id " +
                "WHERE r.dataset_name = ? " +
                "  AND mn.check_type = 'FRESHNESS' " +
                "  AND mn.metric_name = 'hours_since_update' " +
                "  AND r.partition_date BETWEEN ? AND ? " +
                "  AND mn.metric_value IS NOT NULL " +
                "ORDER BY r.partition_date ASC";

        private final ConnectionProvider connectionProvider;

        public JdbcSlaHistoryProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<SlaHistory> getHistory(DatasetContext ctx) throws SQLException {
            if (ctx == null) {
                throw new IllegalArgumentException("context must not be null");
            }

            try (Connection conn = connectionProvider.getConnection()) {
                // Query 1: check for explicit SLA
                boolean hasExplicitSla = false;
                try (PreparedStatement ps = conn.prepareStatement(EXPLICIT_SLA_QUERY)) {
                    ps.setString(1, ctx.getDatasetName());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            hasExplicitSla = !rs.wasNull();
                        }
                    }
                }

                // Query 2: fetch freshness history
                List<Double> values = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(FRESHNESS_HISTORY_QUERY)) {
                    ps.setString(1, ctx.getDatasetName());
                    ps.setDate(2, Date.valueOf(ctx.getPartitionDate().minusDays(HISTORY_DAYS)));
                    ps.setDate(3, Date.valueOf(ctx.getPartitionDate()));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            values.add(rs.getDouble(1));
                        }
                    }
                }

                return Optional.of(new SlaHistory(values, hasExplicitSla));
            }
        }
    }

    /**
     * No-op {@link SlaHistoryProvider} — always returns empty (check skipped).
     *
     * <p>Used as the default in the no-arg constructor.
     */
    private static final class NoOpSlaHistoryProvider implements SlaHistoryProvider {
        @Override
        public Optional<SlaHistory> getHistory(DatasetContext ctx) {
            return Optional.empty();
        }
    }
}
