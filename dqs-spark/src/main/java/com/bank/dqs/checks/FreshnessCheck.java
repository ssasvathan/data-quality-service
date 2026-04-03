package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.DqsConstants;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.to_timestamp;

/**
 * Freshness check implementation.
 *
 * <p>Computes staleness as hours since the latest {@code source_event_timestamp}. The check
 * returns one numeric metric ({@code hours_since_update}) and one detail metric carrying the
 * classification payload ({@code PASS}/{@code WARN}/{@code FAIL} or {@code NOT_RUN}).
 *
 * <p>The class preserves per-dataset isolation: any exception is converted into a diagnostic
 * detail metric instead of propagating.
 */
public final class FreshnessCheck implements DqCheck {

    public static final String CHECK_TYPE = "FRESHNESS";

    static final String TIMESTAMP_COLUMN = "source_event_timestamp";
    static final String HOURS_SINCE_UPDATE = "hours_since_update";
    static final String DETAIL_TYPE_STATUS = "freshness_status";

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_BASELINE_UNAVAILABLE = "baseline_unavailable";
    private static final String REASON_WITHIN_BASELINE = "within_baseline";
    private static final String REASON_ABOVE_WARN_THRESHOLD = "above_warn_threshold";
    private static final String REASON_ABOVE_FAIL_THRESHOLD = "above_fail_threshold";
    private static final String REASON_MISSING_TIMESTAMP_COLUMN = "missing_source_event_timestamp";
    private static final String REASON_NO_PARSEABLE_TIMESTAMPS = "no_parseable_timestamps";
    private static final String REASON_MISSING_CONTEXT = "missing_context";
    private static final String REASON_MISSING_DATAFRAME = "missing_dataframe";
    private static final String REASON_EXECUTION_ERROR = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BaselineProvider baselineProvider;
    private final Clock clock;

    public FreshnessCheck() {
        this(new NoOpBaselineProvider(), Clock.systemUTC());
    }

    public FreshnessCheck(BaselineProvider baselineProvider, Clock clock) {
        if (baselineProvider == null) {
            throw new IllegalArgumentException("baselineProvider must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.baselineProvider = baselineProvider;
        this.clock = clock;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public List<DqMetric> execute(DatasetContext context) {
        List<DqMetric> metrics = new ArrayList<>();
        try {
            if (context == null) {
                metrics.add(notRunDetail(REASON_MISSING_CONTEXT));
                return metrics;
            }

            Dataset<Row> df = context.getDf();
            if (df == null) {
                metrics.add(notRunDetail(REASON_MISSING_DATAFRAME));
                return metrics;
            }

            if (!hasColumn(df, TIMESTAMP_COLUMN)) {
                metrics.add(notRunDetail(REASON_MISSING_TIMESTAMP_COLUMN));
                return metrics;
            }

            Optional<Double> maybeStalenessHours = computeStalenessHours(df);
            if (maybeStalenessHours.isEmpty()) {
                metrics.add(notRunDetail(REASON_NO_PARSEABLE_TIMESTAMPS));
                return metrics;
            }

            double stalenessHours = maybeStalenessHours.get();
            metrics.add(new MetricNumeric(CHECK_TYPE, HOURS_SINCE_UPDATE, stalenessHours));

            Optional<BaselineStats> baseline = baselineProvider.getBaseline(context);
            Classification classification = classifyStaleness(stalenessHours, baseline);
            metrics.add(statusDetail(
                    classification.status,
                    classification.reason,
                    stalenessHours,
                    classification.baselineStats
            ));

            return metrics;
        } catch (Exception e) {
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    private boolean hasColumn(Dataset<Row> df, String columnName) {
        for (String fieldName : df.columns()) {
            if (columnName.equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Double> computeStalenessHours(Dataset<Row> df) {
        Dataset<Row> parsed = df.select(
                to_timestamp(col(TIMESTAMP_COLUMN)).alias("event_timestamp")
        );
        Row maxRow = parsed.agg(max(col("event_timestamp")).alias("latest_event_timestamp")).first();
        if (maxRow == null || maxRow.isNullAt(0)) {
            return Optional.empty();
        }

        Timestamp latestTimestamp = maxRow.getTimestamp(0);
        if (latestTimestamp == null) {
            return Optional.empty();
        }
        Instant latestInstant = latestTimestamp.toInstant();

        double stalenessHours = Duration.between(latestInstant, clock.instant()).toSeconds() / 3600.0d;
        return Optional.of(Math.max(0.0d, stalenessHours));
    }

    /**
     * Statistical policy used by Tier-1 checks for deterministic pass/warn/fail classification:
     *
     * <p>{@code warn_threshold = mean + max(stddev, 0.5)}<br>
     * {@code fail_threshold = mean + max(2 * stddev, 1.0)}
     */
    private Classification classifyStaleness(double stalenessHours, Optional<BaselineStats> baselineStats) {
        if (baselineStats.isEmpty() || baselineStats.get().getSampleCount() < 2L) {
            return new Classification(STATUS_PASS, REASON_BASELINE_UNAVAILABLE, BaselineStats.empty());
        }

        BaselineStats baseline = baselineStats.get();
        double warnThreshold = baseline.getMeanHours() + Math.max(baseline.getStddevHours(), 0.5d);
        double failThreshold = baseline.getMeanHours() + Math.max(2.0d * baseline.getStddevHours(), 1.0d);

        if (stalenessHours <= warnThreshold) {
            return new Classification(STATUS_PASS, REASON_WITHIN_BASELINE, baseline);
        }
        if (stalenessHours <= failThreshold) {
            return new Classification(STATUS_WARN, REASON_ABOVE_WARN_THRESHOLD, baseline);
        }
        return new Classification(STATUS_FAIL, REASON_ABOVE_FAIL_THRESHOLD, baseline);
    }

    private MetricDetail notRunDetail(String reason) {
        return statusDetail(STATUS_NOT_RUN, reason, 0.0d, BaselineStats.empty());
    }

    private MetricDetail errorDetail(Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("staleness_hours", 0.0d);
        payload.put("baseline_mean_hours", 0.0d);
        payload.put("baseline_stddev_hours", 0.0d);
        payload.put("baseline_count", 0);
        payload.put("message", safeMessage(exception));
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail statusDetail(String status, String reason, double stalenessHours, BaselineStats baseline) {
        BaselineStats safeBaseline = baseline == null ? BaselineStats.empty() : baseline;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("staleness_hours", stalenessHours);
        payload.put("baseline_mean_hours", safeBaseline.getMeanHours());
        payload.put("baseline_stddev_hours", safeBaseline.getStddevHours());
        payload.put("baseline_count", safeBaseline.getSampleCount());
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

    @FunctionalInterface
    public interface BaselineProvider {
        Optional<BaselineStats> getBaseline(DatasetContext context) throws Exception;
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public static final class JdbcBaselineProvider implements BaselineProvider {
        private static final String BASELINE_QUERY =
                "SELECT mn.metric_value "
                        + "FROM dq_metric_numeric mn "
                        + "JOIN dq_run r ON r.id = mn.dq_run_id "
                        + "WHERE r.dataset_name = ? "
                        + "  AND mn.check_type = ? "
                        + "  AND mn.metric_name = ? "
                        + "  AND r.partition_date < ? "
                        + "  AND r.expiry_date = ? "
                        + "  AND mn.expiry_date = ? "
                        + "ORDER BY r.partition_date DESC "
                        + "LIMIT 30";

        private final ConnectionProvider connectionProvider;

        public JdbcBaselineProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<BaselineStats> getBaseline(DatasetContext context) throws SQLException {
            if (context == null) {
                throw new IllegalArgumentException("context must not be null");
            }

            List<Double> history = new ArrayList<>();
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(BASELINE_QUERY)) {
                ps.setString(1, context.getDatasetName());
                ps.setString(2, CHECK_TYPE);
                ps.setString(3, HOURS_SINCE_UPDATE);
                ps.setObject(4, context.getPartitionDate());
                ps.setString(5, DqsConstants.EXPIRY_SENTINEL);
                ps.setString(6, DqsConstants.EXPIRY_SENTINEL);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double sample = rs.getDouble(1);
                        if (!rs.wasNull()) {
                            history.add(sample);
                        }
                    }
                }
            }

            if (history.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(BaselineStats.fromSamples(history));
        }
    }

    public static final class BaselineStats {
        private final double meanHours;
        private final double stddevHours;
        private final long sampleCount;

        public BaselineStats(double meanHours, double stddevHours, long sampleCount) {
            if (sampleCount < 0L) {
                throw new IllegalArgumentException("sampleCount must be >= 0");
            }
            if (Double.isNaN(meanHours) || Double.isInfinite(meanHours)) {
                throw new IllegalArgumentException("meanHours must be finite");
            }
            if (Double.isNaN(stddevHours) || Double.isInfinite(stddevHours) || stddevHours < 0.0d) {
                throw new IllegalArgumentException("stddevHours must be finite and >= 0");
            }
            this.meanHours = meanHours;
            this.stddevHours = stddevHours;
            this.sampleCount = sampleCount;
        }

        public double getMeanHours() {
            return meanHours;
        }

        public double getStddevHours() {
            return stddevHours;
        }

        public long getSampleCount() {
            return sampleCount;
        }

        static BaselineStats empty() {
            return new BaselineStats(0.0d, 0.0d, 0L);
        }

        static BaselineStats fromSamples(List<Double> samples) {
            if (samples == null || samples.isEmpty()) {
                return empty();
            }

            double sum = 0.0d;
            long nonNullCount = 0L;
            for (Double sample : samples) {
                if (sample != null) {
                    sum += sample;
                    nonNullCount++;
                }
            }
            if (nonNullCount == 0L) {
                return empty();
            }
            double mean = sum / nonNullCount;

            double varianceSum = 0.0d;
            for (Double sample : samples) {
                if (sample != null) {
                    double diff = sample - mean;
                    varianceSum += diff * diff;
                }
            }
            double stddev = Math.sqrt(varianceSum / nonNullCount);
            return new BaselineStats(mean, stddev, nonNullCount);
        }
    }

    private static final class NoOpBaselineProvider implements BaselineProvider {
        @Override
        public Optional<BaselineStats> getBaseline(DatasetContext context) {
            return Optional.empty();
        }
    }

    private static final class Classification {
        private final String status;
        private final String reason;
        private final BaselineStats baselineStats;

        private Classification(String status, String reason, BaselineStats baselineStats) {
            this.status = status;
            this.reason = reason;
            this.baselineStats = baselineStats;
        }
    }
}
