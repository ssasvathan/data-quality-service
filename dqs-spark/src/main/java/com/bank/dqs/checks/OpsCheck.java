package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.DqsConstants;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.length;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.trim;
import static org.apache.spark.sql.functions.when;

/**
 * Ops check implementation.
 *
 * <p>Computes per-column null-rate and string-only empty-string-rate metrics and compares
 * null-rate behavior against historical baselines for deterministic anomaly classification.
 *
 * <p>The check preserves per-dataset isolation: all exceptions are converted into
 * deterministic {@code NOT_RUN} detail payloads and never propagated.
 */
public final class OpsCheck implements DqCheck {

    public static final String CHECK_TYPE = "OPS";

    static final String NULL_RATE_METRIC_PREFIX = "null_rate_pct::";
    static final String EMPTY_STRING_RATE_METRIC_PREFIX = "empty_string_rate_pct::";
    static final String DETAIL_TYPE_STATUS = "ops_status";
    static final String DETAIL_TYPE_ANOMALIES = "ops_anomalies";

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_BASELINE_UNAVAILABLE = "baseline_unavailable";
    private static final String REASON_WITHIN_BASELINE = "within_baseline";
    private static final String REASON_ABOVE_WARN_THRESHOLD = "above_warn_threshold";
    private static final String REASON_ABOVE_FAIL_THRESHOLD = "above_fail_threshold";
    private static final String REASON_ALL_NULL_COLUMN = "all_null_column";
    private static final String REASON_MISSING_CONTEXT = "missing_context";
    private static final String REASON_MISSING_DATAFRAME = "missing_dataframe";
    private static final String REASON_EXECUTION_ERROR = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BaselineProvider baselineProvider;

    public OpsCheck() {
        this(new NoOpBaselineProvider());
    }

    public OpsCheck(BaselineProvider baselineProvider) {
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
                metrics.add(statusDetail(STATUS_NOT_RUN, REASON_MISSING_CONTEXT, 0, 0, 0, null));
                metrics.add(anomaliesDetail(List.of()));
                return metrics;
            }

            Dataset<Row> df = context.getDf();
            if (df == null) {
                metrics.add(statusDetail(STATUS_NOT_RUN, REASON_MISSING_DATAFRAME, 0, 0, 0, null));
                metrics.add(anomaliesDetail(List.of()));
                return metrics;
            }

            StructField[] fields = df.schema().fields();
            List<ColumnComputation> computations = new ArrayList<>();
            Row aggregateRow = computeAggregateRow(df, fields, computations);
            long totalRows = asLong(aggregateRow, 0);

            Map<String, BaselineStats> baselineByColumn = baselineProvider.getBaseline(context);
            if (baselineByColumn == null) {
                baselineByColumn = Map.of();
            }

            int baselineSampleCount = 0;
            int baselineAvailableColumns = 0;
            List<Anomaly> anomalies = new ArrayList<>();

            for (ColumnComputation computation : computations) {
                double nullRatePct = ratePct(asLong(aggregateRow, computation.nullCountIndex), totalRows);
                metrics.add(new MetricNumeric(
                        CHECK_TYPE,
                        NULL_RATE_METRIC_PREFIX + computation.columnPath,
                        nullRatePct
                ));

                if (computation.emptyCountIndex != null) {
                    double emptyRatePct = ratePct(asLong(aggregateRow, computation.emptyCountIndex), totalRows);
                    metrics.add(new MetricNumeric(
                            CHECK_TYPE,
                            EMPTY_STRING_RATE_METRIC_PREFIX + computation.columnPath,
                            emptyRatePct
                    ));
                }

                BaselineStats baseline = baselineByColumn.getOrDefault(
                        computation.columnPath,
                        BaselineStats.empty()
                );
                baselineSampleCount += baseline.getSampleCount();

                Classification classification = classifyNullRate(nullRatePct, baseline);
                if (classification.baselineAvailable) {
                    baselineAvailableColumns++;
                }
                if (classification.anomaly) {
                    anomalies.add(new Anomaly(
                            computation.columnPath,
                            nullRatePct,
                            baseline,
                            classification.severity,
                            classification.reason
                    ));
                }
            }

            SummaryStatus summary = summarize(anomalies, baselineAvailableColumns);
            metrics.add(statusDetail(
                    summary.status,
                    summary.reason,
                    fields.length,
                    anomalies.size(),
                    baselineSampleCount,
                    null
            ));
            metrics.add(anomaliesDetail(anomalies));

            return metrics;
        } catch (Exception exception) {
            metrics.clear();
            metrics.add(statusDetail(
                    STATUS_NOT_RUN,
                    REASON_EXECUTION_ERROR,
                    0,
                    0,
                    0,
                    safeErrorType(exception)
            ));
            metrics.add(anomaliesDetail(List.of()));
            return metrics;
        }
    }

    private Row computeAggregateRow(
            Dataset<Row> df,
            StructField[] fields,
            List<ColumnComputation> computations
    ) {
        List<Column> aggregateExpressions = new ArrayList<>();
        aggregateExpressions.add(count(lit(1L)).alias("row_count"));

        for (StructField field : fields) {
            String columnPath = field.name();
            int nullCountIndex = aggregateExpressions.size();
            aggregateExpressions.add(
                    sum(when(columnRef(columnPath).isNull(), lit(1L)).otherwise(lit(0L)))
                            .alias("null_count_" + nullCountIndex)
            );

            Integer emptyCountIndex = null;
            if (field.dataType() instanceof StringType) {
                emptyCountIndex = aggregateExpressions.size();
                aggregateExpressions.add(
                        sum(
                                when(
                                        columnRef(columnPath).isNotNull()
                                                .and(length(trim(columnRef(columnPath))).equalTo(0)),
                                        lit(1L)
                                ).otherwise(lit(0L))
                        ).alias("empty_count_" + emptyCountIndex)
                );
            }

            computations.add(new ColumnComputation(columnPath, nullCountIndex, emptyCountIndex));
        }

        Column firstExpression = aggregateExpressions.get(0);
        Column[] remainingExpressions = aggregateExpressions
                .subList(1, aggregateExpressions.size())
                .toArray(new Column[0]);
        return df.agg(firstExpression, remainingExpressions).first();
    }

    private Classification classifyNullRate(double nullRatePct, BaselineStats baselineStats) {
        if (nullRatePct == 100.0d) {
            return new Classification(
                    STATUS_FAIL,
                    REASON_ALL_NULL_COLUMN,
                    "critical",
                    true,
                    baselineStats.getSampleCount() >= 2
            );
        }

        if (baselineStats.getSampleCount() < 2L) {
            return new Classification(
                    STATUS_PASS,
                    REASON_BASELINE_UNAVAILABLE,
                    null,
                    false,
                    false
            );
        }

        double warnThreshold = baselineStats.getMeanValue() + Math.max(baselineStats.getStddevValue(), 1.0d);
        double failThreshold = baselineStats.getMeanValue() + Math.max(2.0d * baselineStats.getStddevValue(), 5.0d);

        if (nullRatePct <= warnThreshold) {
            return new Classification(STATUS_PASS, REASON_WITHIN_BASELINE, null, false, true);
        }
        if (nullRatePct <= failThreshold) {
            return new Classification(STATUS_WARN, REASON_ABOVE_WARN_THRESHOLD, "warn", true, true);
        }
        return new Classification(STATUS_FAIL, REASON_ABOVE_FAIL_THRESHOLD, "fail", true, true);
    }

    private SummaryStatus summarize(List<Anomaly> anomalies, int baselineAvailableColumns) {
        if (anomalies.isEmpty()) {
            if (baselineAvailableColumns == 0) {
                return new SummaryStatus(STATUS_PASS, REASON_BASELINE_UNAVAILABLE);
            }
            return new SummaryStatus(STATUS_PASS, REASON_WITHIN_BASELINE);
        }

        Anomaly firstCritical = null;
        Anomaly firstFail = null;
        Anomaly firstWarn = null;
        for (Anomaly anomaly : anomalies) {
            if ("critical".equals(anomaly.severity)) {
                firstCritical = anomaly;
                break;
            }
            if ("fail".equals(anomaly.severity) && firstFail == null) {
                firstFail = anomaly;
            }
            if ("warn".equals(anomaly.severity) && firstWarn == null) {
                firstWarn = anomaly;
            }
        }

        if (firstCritical != null) {
            return new SummaryStatus(STATUS_FAIL, firstCritical.reason);
        }
        if (firstFail != null) {
            return new SummaryStatus(STATUS_FAIL, firstFail.reason);
        }
        return new SummaryStatus(STATUS_WARN, firstWarn.reason);
    }

    private MetricDetail statusDetail(
            String status,
            String reason,
            int totalColumns,
            int flaggedColumns,
            int baselineSampleCount,
            String errorType
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("total_columns", totalColumns);
        payload.put("flagged_columns", flaggedColumns);
        payload.put("baseline_sample_count", baselineSampleCount);
        if (errorType != null) {
            payload.put("error_type", errorType);
        }
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload, "{}"));
    }

    private MetricDetail anomaliesDetail(List<Anomaly> anomalies) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (Anomaly anomaly : anomalies) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("column", anomaly.columnPath);
            row.put("null_rate_pct", anomaly.nullRatePct);
            row.put("baseline_mean", anomaly.baselineStats.getMeanValue());
            row.put("baseline_stddev", anomaly.baselineStats.getStddevValue());
            row.put("baseline_count", anomaly.baselineStats.getSampleCount());
            row.put("severity", anomaly.severity);
            row.put("reason", anomaly.reason);
            payload.add(row);
        }
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_ANOMALIES, toJson(payload, "[]"));
    }

    private Column columnRef(String columnPath) {
        return col("`" + columnPath.replace("`", "``") + "`");
    }

    private long asLong(Row row, int index) {
        if (row == null || row.isNullAt(index)) {
            return 0L;
        }
        Object value = row.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private double ratePct(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0d;
        }
        double pct = (numerator * 100.0d) / denominator;
        return Math.max(0.0d, Math.min(100.0d, pct));
    }

    private String safeErrorType(Exception exception) {
        if (exception == null) {
            return "UnknownError";
        }
        return exception.getClass().getSimpleName();
    }

    private String toJson(Object payload, String fallback) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    @FunctionalInterface
    public interface BaselineProvider {
        Map<String, BaselineStats> getBaseline(DatasetContext context) throws Exception;
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
        public Map<String, BaselineStats> getBaseline(DatasetContext context) throws SQLException {
            if (context == null) {
                throw new IllegalArgumentException("context must not be null");
            }
            if (context.getDf() == null) {
                return Map.of();
            }

            Map<String, BaselineStats> baselineByColumn = new LinkedHashMap<>();
            try (Connection connection = connectionProvider.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(BASELINE_QUERY)) {
                for (StructField field : context.getDf().schema().fields()) {
                    String columnPath = field.name();
                    List<Double> samples = new ArrayList<>();

                    preparedStatement.setString(1, context.getDatasetName());
                    preparedStatement.setString(2, CHECK_TYPE);
                    preparedStatement.setString(3, NULL_RATE_METRIC_PREFIX + columnPath);
                    preparedStatement.setObject(4, context.getPartitionDate());
                    preparedStatement.setString(5, DqsConstants.EXPIRY_SENTINEL);
                    preparedStatement.setString(6, DqsConstants.EXPIRY_SENTINEL);

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            double sample = resultSet.getDouble(1);
                            if (!resultSet.wasNull()) {
                                samples.add(sample);
                            }
                        }
                    }

                    if (!samples.isEmpty()) {
                        baselineByColumn.put(columnPath, BaselineStats.fromSamples(samples));
                    }
                }
            }

            return baselineByColumn;
        }
    }

    public static final class BaselineStats {
        private final double meanValue;
        private final double stddevValue;
        private final int sampleCount;

        public BaselineStats(double meanValue, double stddevValue, int sampleCount) {
            if (sampleCount < 0) {
                throw new IllegalArgumentException("sampleCount must be >= 0");
            }
            if (Double.isNaN(meanValue) || Double.isInfinite(meanValue)) {
                throw new IllegalArgumentException("meanValue must be finite");
            }
            if (Double.isNaN(stddevValue) || Double.isInfinite(stddevValue) || stddevValue < 0.0d) {
                throw new IllegalArgumentException("stddevValue must be finite and >= 0");
            }
            this.meanValue = meanValue;
            this.stddevValue = stddevValue;
            this.sampleCount = sampleCount;
        }

        public double getMeanValue() {
            return meanValue;
        }

        public double getStddevValue() {
            return stddevValue;
        }

        public int getSampleCount() {
            return sampleCount;
        }

        static BaselineStats empty() {
            return new BaselineStats(0.0d, 0.0d, 0);
        }

        static BaselineStats fromSamples(List<Double> samples) {
            if (samples == null || samples.isEmpty()) {
                return empty();
            }

            double sum = 0.0d;
            int nonNullCount = 0;
            for (Double sample : samples) {
                if (sample != null) {
                    sum += sample;
                    nonNullCount++;
                }
            }
            if (nonNullCount == 0) {
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
        public Map<String, BaselineStats> getBaseline(DatasetContext context) {
            return Map.of();
        }
    }

    private static final class ColumnComputation {
        private final String columnPath;
        private final int nullCountIndex;
        private final Integer emptyCountIndex;

        private ColumnComputation(String columnPath, int nullCountIndex, Integer emptyCountIndex) {
            this.columnPath = columnPath;
            this.nullCountIndex = nullCountIndex;
            this.emptyCountIndex = emptyCountIndex;
        }
    }

    private static final class Classification {
        private final String status;
        private final String reason;
        private final String severity;
        private final boolean anomaly;
        private final boolean baselineAvailable;

        private Classification(
                String status,
                String reason,
                String severity,
                boolean anomaly,
                boolean baselineAvailable
        ) {
            this.status = status;
            this.reason = reason;
            this.severity = severity;
            this.anomaly = anomaly;
            this.baselineAvailable = baselineAvailable;
        }
    }

    private static final class Anomaly {
        private final String columnPath;
        private final double nullRatePct;
        private final BaselineStats baselineStats;
        private final String severity;
        private final String reason;

        private Anomaly(
                String columnPath,
                double nullRatePct,
                BaselineStats baselineStats,
                String severity,
                String reason
        ) {
            this.columnPath = columnPath;
            this.nullRatePct = nullRatePct;
            this.baselineStats = baselineStats == null ? BaselineStats.empty() : baselineStats;
            this.severity = severity;
            this.reason = reason;
        }
    }

    private static final class SummaryStatus {
        private final String status;
        private final String reason;

        private SummaryStatus(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }
    }
}
