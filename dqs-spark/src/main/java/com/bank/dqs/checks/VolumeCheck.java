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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Volume check implementation.
 *
 * <p>Computes dataset row count and compares it against historical row count baseline.
 * The check returns numeric metrics ({@code row_count}, {@code pct_change},
 * {@code row_count_stddev}) and one detail metric with classification payload
 * ({@code PASS}/{@code WARN}/{@code FAIL} or {@code NOT_RUN}).
 *
 * <p>The class preserves per-dataset isolation: any exception is converted into a
 * diagnostic detail metric instead of propagating.
 */
public final class VolumeCheck implements DqCheck {

    public static final String CHECK_TYPE = "VOLUME";

    static final String METRIC_ROW_COUNT = "row_count";
    static final String METRIC_PCT_CHANGE = "pct_change";
    static final String METRIC_ROW_COUNT_STDDEV = "row_count_stddev";
    static final String DETAIL_TYPE_STATUS = "volume_status";

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_BASELINE_UNAVAILABLE = "baseline_unavailable";
    private static final String REASON_WITHIN_BASELINE = "within_baseline";
    private static final String REASON_ABOVE_WARN_THRESHOLD = "above_warn_threshold";
    private static final String REASON_ABOVE_FAIL_THRESHOLD = "above_fail_threshold";
    private static final String REASON_MISSING_CONTEXT = "missing_context";
    private static final String REASON_MISSING_DATAFRAME = "missing_dataframe";
    private static final String REASON_EXECUTION_ERROR = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BaselineProvider baselineProvider;

    public VolumeCheck() {
        this(new NoOpBaselineProvider());
    }

    public VolumeCheck(BaselineProvider baselineProvider) {
        if (baselineProvider == null) {
            throw new IllegalArgumentException("baselineProvider must not be null");
        }
        this.baselineProvider = baselineProvider;
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

            double rowCount = (double) df.count();
            Optional<BaselineStats> maybeBaseline = baselineProvider.getBaseline(context);
            BaselineStats baselineStats = maybeBaseline.orElse(BaselineStats.empty());

            Classification classification;
            double pctChange;
            if (baselineStats.getSampleCount() >= 2L) {
                pctChange = computePctChange(rowCount, baselineStats.getMeanRowCount());
                classification = classify(rowCount, baselineStats);
            } else {
                pctChange = 0.0d;
                classification = new Classification(
                        STATUS_PASS,
                        REASON_BASELINE_UNAVAILABLE,
                        baselineStats
                );
            }

            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_ROW_COUNT, rowCount));
            metrics.add(new MetricNumeric(
                    CHECK_TYPE,
                    METRIC_PCT_CHANGE,
                    pctChange
            ));
            metrics.add(new MetricNumeric(
                    CHECK_TYPE,
                    METRIC_ROW_COUNT_STDDEV,
                    classification.baselineStats.getStddevRowCount()
            ));
            metrics.add(statusDetail(
                    classification.status,
                    classification.reason,
                    rowCount,
                    pctChange,
                    classification.baselineStats
            ));

            return metrics;
        } catch (Exception e) {
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    private double computePctChange(double rowCount, double baselineMean) {
        if (baselineMean > 0.0d) {
            return ((rowCount - baselineMean) / baselineMean) * 100.0d;
        }
        return 0.0d;
    }

    /**
     * Statistical policy used by Tier-1 checks for deterministic pass/warn/fail classification:
     *
     * <p>{@code warn_threshold = max(stddev, max(1.0, baseline_mean * 0.05))}<br>
     * {@code fail_threshold = max(2 * stddev, max(1.0, baseline_mean * 0.10))}
     */
    private Classification classify(double rowCount, BaselineStats baseline) {
        double baselineMean = baseline.getMeanRowCount();
        double deviation = Math.abs(rowCount - baselineMean);
        double warnThreshold = Math.max(
                baseline.getStddevRowCount(),
                Math.max(1.0d, baselineMean * 0.05d)
        );
        double failThreshold = Math.max(
                2.0d * baseline.getStddevRowCount(),
                Math.max(1.0d, baselineMean * 0.10d)
        );

        if (deviation <= warnThreshold) {
            return new Classification(STATUS_PASS, REASON_WITHIN_BASELINE, baseline);
        }
        if (deviation <= failThreshold) {
            return new Classification(STATUS_WARN, REASON_ABOVE_WARN_THRESHOLD, baseline);
        }
        return new Classification(STATUS_FAIL, REASON_ABOVE_FAIL_THRESHOLD, baseline);
    }

    private MetricDetail notRunDetail(String reason) {
        return statusDetail(STATUS_NOT_RUN, reason, 0.0d, 0.0d, BaselineStats.empty());
    }

    private MetricDetail errorDetail(Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("row_count", 0.0d);
        payload.put("pct_change", 0.0d);
        payload.put("baseline_mean_row_count", 0.0d);
        payload.put("baseline_stddev_row_count", 0.0d);
        payload.put("baseline_count", 0);
        payload.put("error_type", safeErrorType(exception));
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail statusDetail(
            String status,
            String reason,
            double rowCount,
            double pctChange,
            BaselineStats baseline
    ) {
        BaselineStats safeBaseline = baseline == null ? BaselineStats.empty() : baseline;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("row_count", rowCount);
        payload.put("pct_change", pctChange);
        payload.put("baseline_mean_row_count", safeBaseline.getMeanRowCount());
        payload.put("baseline_stddev_row_count", safeBaseline.getStddevRowCount());
        payload.put("baseline_count", safeBaseline.getSampleCount());
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private String safeErrorType(Exception exception) {
        if (exception == null) {
            return "UnknownError";
        }
        return exception.getClass().getSimpleName();
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
                ps.setString(3, METRIC_ROW_COUNT);
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
        private final double meanRowCount;
        private final double stddevRowCount;
        private final long sampleCount;

        public BaselineStats(double meanRowCount, double stddevRowCount, long sampleCount) {
            if (sampleCount < 0L) {
                throw new IllegalArgumentException("sampleCount must be >= 0");
            }
            if (Double.isNaN(meanRowCount) || Double.isInfinite(meanRowCount)) {
                throw new IllegalArgumentException("meanRowCount must be finite");
            }
            if (Double.isNaN(stddevRowCount)
                    || Double.isInfinite(stddevRowCount)
                    || stddevRowCount < 0.0d) {
                throw new IllegalArgumentException("stddevRowCount must be finite and >= 0");
            }
            this.meanRowCount = meanRowCount;
            this.stddevRowCount = stddevRowCount;
            this.sampleCount = sampleCount;
        }

        public double getMeanRowCount() {
            return meanRowCount;
        }

        public double getStddevRowCount() {
            return stddevRowCount;
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
