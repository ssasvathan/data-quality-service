package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.DqsConstants;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.DoubleType;
import org.apache.spark.sql.types.FloatType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
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

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.get_json_object;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.mean;
import static org.apache.spark.sql.functions.percentile_approx;
import static org.apache.spark.sql.functions.stddev_pop;

/**
 * Distribution Check — Tier 2 quality check.
 *
 * <p>Computes distribution metrics (mean, stddev, p50, p95) per numeric column using a
 * single Spark aggregation pass, compares against historical baseline via z-score thresholds,
 * and optionally explodes {@code eventAttribute} JSON string columns into virtual numeric
 * sub-paths at configurable depth.
 *
 * <p>Explosion levels (stored as integer in {@code check_config.explosion_level}):
 * <ul>
 *   <li>0 = OFF — skip {@code eventAttribute} columns entirely</li>
 *   <li>1 = TOP_LEVEL — explode only first-level JSON keys (non-nested)</li>
 *   <li>2 = ALL — recursively explode all nested JSON keys</li>
 * </ul>
 *
 * <p>Z-score thresholds: {@code |z| > 2.0} → WARN, {@code |z| > 3.0} → FAIL.
 *
 * <p>This check preserves per-dataset failure isolation: any exception is converted into
 * a deterministic {@code NOT_RUN} diagnostic detail metric.
 */
public final class DistributionCheck implements DqCheck {

    private static final Logger LOG = LoggerFactory.getLogger(DistributionCheck.class);

    public static final String CHECK_TYPE = "DISTRIBUTION";

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_WARN    = "WARN";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_MISSING_CONTEXT          = "missing_context";
    private static final String REASON_MISSING_DATAFRAME        = "missing_dataframe";
    private static final String REASON_EXECUTION_ERROR          = "execution_error";
    private static final String REASON_BASELINE_UNAVAILABLE     = "baseline_unavailable";
    private static final String REASON_WITHIN_BASELINE          = "within_baseline";
    private static final String REASON_DISTRIBUTION_SHIFT       = "distribution_shift_detected";
    private static final String REASON_NO_NUMERIC_COLUMNS       = "no_numeric_columns";

    private static final String DETAIL_SUMMARY = "distribution_summary";

    private static final double Z_WARN_THRESHOLD = 2.0;
    private static final double Z_FAIL_THRESHOLD = 3.0;

    // Jackson ObjectMapper is thread-safe after configuration and can be shared as a static field.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ExplodeConfigProvider explodeConfigProvider;
    private final BaselineStatsProvider baselineProvider;

    /**
     * No-arg constructor used by {@code DqsJob.buildCheckFactory()}.
     * Defaults to {@link NoOpExplodeConfigProvider} and {@link NoOpBaselineStatsProvider}.
     */
    public DistributionCheck() {
        this(new NoOpExplodeConfigProvider(), new NoOpBaselineStatsProvider());
    }

    /**
     * Constructor with explicit provider injection (used by tests and production wiring).
     *
     * @param explodeConfigProvider provides the explosion level for eventAttribute; must not be null
     * @param baselineProvider      provides historical column stats for comparison; must not be null
     */
    public DistributionCheck(ExplodeConfigProvider explodeConfigProvider,
                              BaselineStatsProvider baselineProvider) {
        if (explodeConfigProvider == null) {
            throw new IllegalArgumentException("explodeConfigProvider must not be null");
        }
        if (baselineProvider == null) {
            throw new IllegalArgumentException("baselineProvider must not be null");
        }
        this.explodeConfigProvider = explodeConfigProvider;
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

            if (context.getDf() == null) {
                metrics.add(notRunDetail(REASON_MISSING_DATAFRAME));
                return metrics;
            }

            LOG.info("Executing DistributionCheck for dataset={}", context.getDatasetName());

            // Determine explosion level
            ExplosionLevel explosionLevel = explodeConfigProvider.getExplosionLevel(context);

            // Collect numeric columns to analyze
            List<ExplodedColumn> columns = collectNumericColumns(context.getDf(), explosionLevel);

            if (columns.isEmpty()) {
                metrics.add(summaryDetail(STATUS_PASS, REASON_NO_NUMERIC_COLUMNS, 0, 0));
                return metrics;
            }

            // Fetch historical baseline
            Optional<Map<String, ColumnStats>> maybeBaseline = baselineProvider.getBaseline(context);
            Map<String, ColumnStats> baseline = maybeBaseline.orElse(Map.of());

            int shiftedColumns = 0;
            String overallStatus = STATUS_PASS;

            for (ExplodedColumn col : columns) {
                // Compute stats via single Spark aggregation pass
                ColumnStats stats = computeColumnStats(context.getDf(), col);

                // Emit MetricNumeric per statistic
                metrics.add(new MetricNumeric(CHECK_TYPE, col.virtualPath() + ".mean",   stats.mean()));
                metrics.add(new MetricNumeric(CHECK_TYPE, col.virtualPath() + ".stddev", stats.stddev()));
                metrics.add(new MetricNumeric(CHECK_TYPE, col.virtualPath() + ".p50",    stats.p50()));
                metrics.add(new MetricNumeric(CHECK_TYPE, col.virtualPath() + ".p95",    stats.p95()));
                metrics.add(new MetricNumeric(CHECK_TYPE, col.virtualPath() + ".count",  (double) stats.sampleCount()));

                // Compare to baseline or emit baseline_unavailable
                ColumnStats baselineStats = baseline.get(col.virtualPath());
                if (baselineStats == null) {
                    metrics.add(columnStatusDetail(col.virtualPath(), STATUS_PASS, REASON_BASELINE_UNAVAILABLE,
                            null));
                } else {
                    String colStatus = classifyShift(stats.mean(), baselineStats);
                    double z = computeZScore(stats.mean(), baselineStats);

                    if (STATUS_FAIL.equals(colStatus) || STATUS_WARN.equals(colStatus)) {
                        shiftedColumns++;
                        metrics.add(columnShiftDetail(col.virtualPath(), colStatus, REASON_DISTRIBUTION_SHIFT,
                                z, stats.mean(), baselineStats));
                        // Escalate overall status
                        if (STATUS_FAIL.equals(colStatus)) {
                            overallStatus = STATUS_FAIL;
                        } else if (STATUS_WARN.equals(colStatus) && !STATUS_FAIL.equals(overallStatus)) {
                            overallStatus = STATUS_WARN;
                        }
                    } else {
                        metrics.add(columnStatusDetail(col.virtualPath(), STATUS_PASS, REASON_WITHIN_BASELINE,
                                z));
                    }
                }
            }

            // Emit final distribution_summary detail
            metrics.add(summaryDetail(overallStatus, null, columns.size(), shiftedColumns));
            return metrics;

        } catch (Exception e) {
            LOG.warn("DistributionCheck execution failed", e);
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Column collection
    // -------------------------------------------------------------------------

    /**
     * Collect numeric columns to analyze from the DataFrame schema, respecting explosion level.
     */
    private List<ExplodedColumn> collectNumericColumns(Dataset<Row> df, ExplosionLevel level) {
        List<ExplodedColumn> result = new ArrayList<>();
        for (StructField field : df.schema().fields()) {
            DataType dtype = field.dataType();
            String name = field.name();

            // Handle eventAttribute StringType column
            if (dtype instanceof StringType && "eventAttribute".equals(name)) {
                if (level == ExplosionLevel.OFF) {
                    // Skip entirely
                    continue;
                }
                // Explode JSON keys
                List<String> virtualPaths = explodeEventAttribute(df, name, level);
                for (String vp : virtualPaths) {
                    result.add(new ExplodedColumn(vp, vp));
                }
                continue;
            }

            // Include regular numeric columns
            if (isNumericType(dtype)) {
                result.add(new ExplodedColumn(name, name));
            }
        }
        return result;
    }

    private boolean isNumericType(DataType dtype) {
        return dtype instanceof IntegerType
                || dtype instanceof LongType
                || dtype instanceof FloatType
                || dtype instanceof DoubleType
                || dtype instanceof DecimalType;
    }

    // -------------------------------------------------------------------------
    // eventAttribute explosion
    // -------------------------------------------------------------------------

    /**
     * Explode the eventAttribute JSON column into virtual numeric column paths.
     * Returns dot-path strings like {@code "eventAttribute.amount"} or
     * {@code "eventAttribute.details.price"}.
     */
    private List<String> explodeEventAttribute(Dataset<Row> df, String colName, ExplosionLevel level) {
        try {
            // Sample non-null rows to infer JSON schema
            Dataset<String> sampleDs = df.select(col(colName))
                    .filter(col(colName).isNotNull())
                    .limit(1000)
                    .as(Encoders.STRING());

            if (sampleDs.isEmpty()) {
                return List.of();
            }

            // Infer schema from JSON sample
            SparkSession spark = df.sparkSession();
            StructType inferredSchema = spark.read().json(sampleDs).schema();

            // Walk schema and collect numeric leaf paths
            List<String> paths = new ArrayList<>();
            collectNumericLeafPaths(inferredSchema, colName, 0, level, paths);
            return paths;

        } catch (Exception e) {
            LOG.warn("DistributionCheck: failed to explode eventAttribute column '{}': {}",
                    colName, e.getMessage());
            return List.of();
        }
    }

    private void collectNumericLeafPaths(StructType schema, String prefix,
                                          int depth, ExplosionLevel level, List<String> paths) {
        for (StructField field : schema.fields()) {
            String path = prefix + "." + field.name();
            DataType dtype = field.dataType();

            if (isNumericType(dtype)) {
                // For TOP_LEVEL: only include fields at depth 0 (first-level keys of eventAttribute)
                if (level == ExplosionLevel.TOP_LEVEL && depth > 0) {
                    continue;
                }
                paths.add(path);
            } else if (dtype instanceof StructType nested) {
                if (level == ExplosionLevel.ALL) {
                    collectNumericLeafPaths(nested, path, depth + 1, level, paths);
                }
                // TOP_LEVEL: don't recurse into nested structs
            }
        }
    }

    // -------------------------------------------------------------------------
    // Stats computation
    // -------------------------------------------------------------------------

    /**
     * Compute distribution statistics for a column in a single Spark aggregation pass.
     */
    private ColumnStats computeColumnStats(Dataset<Row> df, ExplodedColumn column) {
        org.apache.spark.sql.Column colExpr = resolveColumnExpression(df, column);

        Row statsRow = df.agg(
                mean(colExpr).alias("mean"),
                stddev_pop(colExpr).alias("stddev"),
                percentile_approx(colExpr, lit(0.5), lit(10000)).alias("p50"),
                percentile_approx(colExpr, lit(0.95), lit(10000)).alias("p95"),
                count(colExpr).alias("cnt")
        ).first();

        double meanVal   = statsRow.isNullAt(0) ? 0.0 : statsRow.getDouble(0);
        double stddevVal = statsRow.isNullAt(1) ? 0.0 : statsRow.getDouble(1);
        double p50Val    = statsRow.isNullAt(2) ? 0.0 : statsRow.getDouble(2);
        double p95Val    = statsRow.isNullAt(3) ? 0.0 : statsRow.getDouble(3);
        long   cntVal    = statsRow.isNullAt(4) ? 0L  : statsRow.getLong(4);

        return new ColumnStats(meanVal, stddevVal, p50Val, p95Val, cntVal);
    }

    /**
     * Resolve the Spark Column expression for an ExplodedColumn.
     * For virtual paths (eventAttribute.xxx), uses get_json_object + cast to Double.
     * For regular columns, uses col() directly.
     */
    private org.apache.spark.sql.Column resolveColumnExpression(Dataset<Row> df,
                                                                  ExplodedColumn column) {
        String path = column.virtualPath();

        // Check if this is an eventAttribute virtual path (contains a dot and starts with "eventAttribute.")
        if (path.startsWith("eventAttribute.")) {
            // Extract the JSON path relative to the eventAttribute column
            // e.g., "eventAttribute.amount" → jsonPath = "$.amount"
            // e.g., "eventAttribute.details.price" → jsonPath = "$.details.price"
            String jsonPath = "$." + path.substring("eventAttribute.".length());
            return get_json_object(col("eventAttribute"), jsonPath)
                    .cast(DataTypes.DoubleType);
        }

        // Regular numeric column — check if it's actually a field in the schema
        return col(path).cast(DataTypes.DoubleType);
    }

    // -------------------------------------------------------------------------
    // Z-score classification
    // -------------------------------------------------------------------------

    private String classifyShift(double currentMean, ColumnStats baseline) {
        if (baseline.stddev() == 0.0) {
            return currentMean == baseline.mean() ? STATUS_PASS : STATUS_WARN;
        }
        double z = Math.abs((currentMean - baseline.mean()) / baseline.stddev());
        if (z > Z_FAIL_THRESHOLD) return STATUS_FAIL;
        if (z > Z_WARN_THRESHOLD) return STATUS_WARN;
        return STATUS_PASS;
    }

    private double computeZScore(double currentMean, ColumnStats baseline) {
        if (baseline.stddev() == 0.0) {
            // When baseline stddev is zero, any deviation is represented as the absolute difference.
            // Using Double.MAX_VALUE for any non-zero shift would be extreme; instead report the
            // absolute difference so the z_score field is informative rather than misleadingly 0.0.
            return Math.abs(currentMean - baseline.mean());
        }
        return Math.abs((currentMean - baseline.mean()) / baseline.stddev());
    }

    // -------------------------------------------------------------------------
    // Detail metric helpers
    // -------------------------------------------------------------------------

    private MetricDetail notRunDetail(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", reason);
        return new MetricDetail(CHECK_TYPE, DETAIL_SUMMARY, toJson(payload));
    }

    private MetricDetail errorDetail(Exception e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("error_type", e.getClass().getSimpleName());
        return new MetricDetail(CHECK_TYPE, DETAIL_SUMMARY, toJson(payload));
    }

    private MetricDetail summaryDetail(String status, String reason, int totalColumns, int shiftedColumns) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        if (reason != null) {
            payload.put("reason", reason);
        }
        payload.put("total_columns_analyzed", totalColumns);
        payload.put("shifted_columns", shiftedColumns);
        return new MetricDetail(CHECK_TYPE, DETAIL_SUMMARY, toJson(payload));
    }

    private MetricDetail columnStatusDetail(String columnPath, String status, String reason,
                                             Double zScore) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("column", columnPath);
        if (zScore != null) {
            payload.put("z_score", zScore);
        }
        return new MetricDetail(CHECK_TYPE, columnPath + ".status", toJson(payload));
    }

    private MetricDetail columnShiftDetail(String columnPath, String status, String reason,
                                            double zScore, double currentMean,
                                            ColumnStats baseline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("z_score", zScore);
        payload.put("column", columnPath);
        payload.put("current_mean", currentMean);
        payload.put("baseline_mean", baseline.mean());
        payload.put("baseline_stddev", baseline.stddev());
        return new MetricDetail(CHECK_TYPE, columnPath + ".status", toJson(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize payload", e);
            return "{\"status\":\"NOT_RUN\",\"reason\":\"execution_error\"}";
        }
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Explosion depth level for {@code eventAttribute} JSON column processing.
     * Stored as integer in {@code check_config.explosion_level}.
     */
    public enum ExplosionLevel {
        OFF(0),
        TOP_LEVEL(1),
        ALL(2);

        private final int value;

        ExplosionLevel(int value) {
            this.value = value;
        }

        /** @return the integer value stored in {@code check_config.explosion_level} */
        public int getValue() {
            return value;
        }

        /**
         * Convert integer to ExplosionLevel, defaulting to OFF for unknown values.
         *
         * @param intValue integer from {@code check_config.explosion_level}
         * @return corresponding ExplosionLevel, or OFF if unrecognized
         */
        public static ExplosionLevel fromInt(int intValue) {
            for (ExplosionLevel level : values()) {
                if (level.value == intValue) {
                    return level;
                }
            }
            return OFF;
        }
    }

    /**
     * Value record representing a column to analyze — either a regular schema field
     * or a virtual path derived from eventAttribute JSON explosion.
     *
     * @param columnName  the physical or logical column name
     * @param virtualPath the dot-path used in metric names (e.g., {@code "eventAttribute.amount"})
     */
    public record ExplodedColumn(String columnName, String virtualPath) {}

    /**
     * Immutable value class holding computed distribution statistics for a single column.
     *
     * @param mean        arithmetic mean of the column values
     * @param stddev      population standard deviation
     * @param p50         50th percentile (median)
     * @param p95         95th percentile
     * @param sampleCount number of non-null values included in the statistics
     */
    public record ColumnStats(double mean, double stddev, double p50, double p95, long sampleCount) {}

    /**
     * Functional interface for obtaining the eventAttribute explosion level.
     * The real implementation queries {@code check_config} via JDBC.
     * Tests inject a lambda.
     */
    @FunctionalInterface
    public interface ExplodeConfigProvider {
        ExplosionLevel getExplosionLevel(DatasetContext ctx) throws Exception;
    }

    /**
     * Functional interface for providing a JDBC connection.
     * Package-private: only JDBC inner classes within this package use it.
     */
    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * Functional interface for retrieving historical baseline column statistics.
     * The real implementation queries {@code dq_metric_numeric} via JDBC.
     * Tests inject a lambda.
     */
    @FunctionalInterface
    public interface BaselineStatsProvider {
        Optional<Map<String, ColumnStats>> getBaseline(DatasetContext ctx) throws Exception;
    }

    /**
     * JDBC implementation of {@link ExplodeConfigProvider}.
     *
     * <p>Queries the {@code check_config} table for the configured explosion level for
     * this dataset and check type. Returns OFF if no row is found.
     */
    public static final class JdbcExplodeConfigProvider implements ExplodeConfigProvider {

        private static final String CONFIG_QUERY =
                "SELECT explosion_level "
                + "FROM check_config "
                + "WHERE ? LIKE dataset_pattern "
                + "  AND check_type = ? "
                + "  AND enabled = TRUE "
                + "  AND expiry_date = ? "
                + "ORDER BY id ASC "
                + "LIMIT 1";

        private final ConnectionProvider connectionProvider;

        public JdbcExplodeConfigProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public ExplosionLevel getExplosionLevel(DatasetContext ctx) throws Exception {
            if (ctx == null) {
                throw new IllegalArgumentException("context must not be null");
            }
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(CONFIG_QUERY)) {

                ps.setString(1, ctx.getDatasetName());
                ps.setString(2, CHECK_TYPE);
                ps.setString(3, DqsConstants.EXPIRY_SENTINEL);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return ExplosionLevel.OFF;
                    }
                    return ExplosionLevel.fromInt(rs.getInt("explosion_level"));
                }
            }
        }
    }

    /**
     * No-op explosion config provider — always returns OFF.
     * Used by the no-arg constructor (safe default for deployments without explicit config).
     */
    private static final class NoOpExplodeConfigProvider implements ExplodeConfigProvider {
        @Override
        public ExplosionLevel getExplosionLevel(DatasetContext ctx) {
            return ExplosionLevel.OFF;
        }
    }

    /**
     * JDBC implementation of {@link BaselineStatsProvider}.
     *
     * <p>Queries the {@code dq_metric_numeric} table for the most recent prior run for each
     * column/statistic and assembles a {@code Map<columnPath, ColumnStats>}.
     */
    public static final class JdbcBaselineStatsProvider implements BaselineStatsProvider {

        private static final String BASELINE_QUERY =
                "SELECT r.dataset_name, mn.metric_name, mn.metric_value "
                + "FROM dq_metric_numeric mn "
                + "JOIN dq_run r ON r.id = mn.dq_run_id "
                + "WHERE r.dataset_name = ? "
                + "  AND mn.check_type = ? "
                + "  AND r.partition_date < ? "
                + "  AND r.expiry_date = ? "
                + "  AND mn.expiry_date = ? "
                + "ORDER BY r.partition_date DESC "
                + "LIMIT 200";

        private final ConnectionProvider connectionProvider;

        public JdbcBaselineStatsProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<Map<String, ColumnStats>> getBaseline(DatasetContext ctx) throws Exception {
            if (ctx == null) {
                throw new IllegalArgumentException("context must not be null");
            }
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(BASELINE_QUERY)) {

                ps.setString(1, ctx.getDatasetName());
                ps.setString(2, CHECK_TYPE);
                ps.setObject(3, ctx.getPartitionDate());
                ps.setString(4, DqsConstants.EXPIRY_SENTINEL);
                ps.setString(5, DqsConstants.EXPIRY_SENTINEL);

                // Collect raw metric rows grouped by column path
                // metric_name pattern: "{columnPath}.{statName}" e.g., "amount.mean"
                Map<String, Map<String, Double>> statsByColumn = new LinkedHashMap<>();

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String metricName = rs.getString("metric_name");
                        double metricValue = rs.getDouble("metric_value");

                        // Parse column path prefix (everything before last dot)
                        int lastDot = metricName.lastIndexOf('.');
                        if (lastDot < 0) {
                            continue;
                        }
                        String columnPath = metricName.substring(0, lastDot);
                        String statName   = metricName.substring(lastDot + 1);

                        // Only store the first row per column+stat (most recent, due to ORDER BY DESC)
                        statsByColumn.computeIfAbsent(columnPath, k -> new LinkedHashMap<>())
                                .putIfAbsent(statName, metricValue);
                    }
                }

                if (statsByColumn.isEmpty()) {
                    return Optional.empty();
                }

                // Assemble ColumnStats objects
                Map<String, ColumnStats> result = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, Double>> entry : statsByColumn.entrySet()) {
                    Map<String, Double> stats = entry.getValue();
                    double mean    = stats.getOrDefault("mean",   0.0);
                    double stddev  = stats.getOrDefault("stddev", 0.0);
                    double p50     = stats.getOrDefault("p50",    0.0);
                    double p95     = stats.getOrDefault("p95",    0.0);
                    long   cnt     = stats.containsKey("count")
                            ? stats.get("count").longValue() : 0L;
                    result.put(entry.getKey(), new ColumnStats(mean, stddev, p50, p95, cnt));
                }
                return Optional.of(result);
            }
        }
    }

    /**
     * No-op baseline stats provider — always returns empty (no baseline comparison).
     * Used by the no-arg constructor.
     */
    private static final class NoOpBaselineStatsProvider implements BaselineStatsProvider {
        @Override
        public Optional<Map<String, ColumnStats>> getBaseline(DatasetContext ctx) {
            return Optional.empty();
        }
    }
}
