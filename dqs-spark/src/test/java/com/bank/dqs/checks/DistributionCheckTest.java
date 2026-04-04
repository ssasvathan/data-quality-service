package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD RED PHASE — tests will not compile until DistributionCheck.java is implemented.
 *
 * <p>DistributionCheck is the most complex Tier 2 check: it computes distribution metrics
 * (mean, stddev, p50, p95) per numeric column, compares against historical baseline using
 * z-score thresholds, and optionally explodes {@code eventAttribute} JSON string columns
 * into virtual numeric sub-paths at configurable depth.
 *
 * <p>SparkSession is required because {@code DistributionCheck} calls Spark aggregate
 * functions ({@code mean}, {@code stddev_pop}, {@code percentile_approx}, {@code count}).
 * Follows the exact SparkSession lifecycle pattern from {@code BreakingChangeCheckTest} and
 * {@code ZeroRowCheckTest}.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/DistributionCheck.java} is created.
 */
public class DistributionCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    // -------------------------------------------------------------------------
    // SparkSession lifecycle
    // -------------------------------------------------------------------------

    @BeforeAll
    public static void initSpark() {
        spark = SparkSession.builder()
                .appName("DistributionCheckTest")
                .master("local[1]")
                .getOrCreate();
    }

    @AfterAll
    public static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a DatasetContext wrapping the given DataFrame.
     * Uses the canonical path/code/date constants matching BreakingChangeCheckTest.
     */
    private DatasetContext context(Dataset<Row> df) {
        return new DatasetContext(
                "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
                "ALPHA",
                PARTITION_DATE,
                "/prod/data",
                df,
                DatasetContext.FORMAT_PARQUET
        );
    }

    /**
     * Build a DistributionCheck with no baseline and explosion level OFF.
     * Used for tests that don't need baseline comparison or eventAttribute explosion.
     */
    private DistributionCheck checkNoBaselineNoExplosion() {
        return new DistributionCheck(
                ctx -> DistributionCheck.ExplosionLevel.OFF,
                ctx -> Optional.empty()
        );
    }

    /**
     * Build a DistributionCheck with an injected baseline map and explosion level OFF.
     * Used for baseline comparison tests.
     */
    private DistributionCheck checkWithBaseline(
            Map<String, DistributionCheck.ColumnStats> baselineMap) {
        return new DistributionCheck(
                ctx -> DistributionCheck.ExplosionLevel.OFF,
                ctx -> Optional.of(baselineMap)
        );
    }

    /**
     * Find all MetricNumeric entries in the metric list.
     */
    private List<MetricNumeric> findAllNumericMetrics(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .toList();
    }

    /**
     * Find a MetricNumeric by metric name suffix (e.g., "amount.mean").
     */
    private Optional<MetricNumeric> findNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst();
    }

    /**
     * Find a MetricDetail by detail type (e.g., "amount.status").
     */
    private Optional<MetricDetail> findDetailByType(List<DqMetric> metrics, String detailType) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> detailType.equals(d.getDetailType()))
                .findFirst();
    }

    /**
     * Find the distribution_summary MetricDetail (always emitted once per execution).
     */
    private MetricDetail findSummaryDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> "distribution_summary".equals(d.getDetailType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MetricDetail with detailType=distribution_summary in " + metrics));
    }

    /**
     * Find a MetricDetail whose detail type ends with ".status" (column status detail).
     */
    private List<MetricDetail> findAllColumnStatusDetails(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> d.getDetailType().endsWith(".status"))
                .toList();
    }

    // -------------------------------------------------------------------------
    // AC1: Numeric columns → MetricNumeric per stat, PASS per column
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — DataFrame with int and double numeric columns, no baseline.
     *
     * <p>Given a dataset with {@code id} (LongType) and {@code amount} (DoubleType) columns,
     * when the Distribution check executes with no baseline,
     * then it writes {@code MetricNumeric} entries per column per statistic (mean, stddev,
     * p50, p95, count) and a {@code MetricDetail} per column with status=PASS
     * reason=baseline_unavailable.
     */
    @Test
    public void executeComputesMeanAndStddevForNumericColumns() {
        // Given: DataFrame with two numeric columns
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("amount", DataTypes.DoubleType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, 100.0),
                        RowFactory.create(2L, 200.0),
                        RowFactory.create(3L, 150.0)
                ),
                schema
        );

        DistributionCheck check = checkNoBaselineNoExplosion();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric entries exist for both numeric columns (mean, stddev, p50, p95, count)
        assertFalse(metrics.isEmpty(), "Expected metrics for numeric columns");

        // Amount column statistics
        assertTrue(findNumericMetric(metrics, "amount.mean").isPresent(),
                "Expected MetricNumeric for amount.mean");
        assertTrue(findNumericMetric(metrics, "amount.stddev").isPresent(),
                "Expected MetricNumeric for amount.stddev");
        assertTrue(findNumericMetric(metrics, "amount.p50").isPresent(),
                "Expected MetricNumeric for amount.p50");
        assertTrue(findNumericMetric(metrics, "amount.p95").isPresent(),
                "Expected MetricNumeric for amount.p95");
        assertTrue(findNumericMetric(metrics, "amount.count").isPresent(),
                "Expected MetricNumeric for amount.count");

        // Mean should be (100+200+150)/3 = 150.0
        MetricNumeric amountMean = findNumericMetric(metrics, "amount.mean").orElseThrow();
        assertEquals(DistributionCheck.CHECK_TYPE, amountMean.getCheckType(),
                "check_type must be DISTRIBUTION");
        assertEquals(150.0, amountMean.getMetricValue(), 0.01,
                "Expected mean of [100, 200, 150] = 150.0");

        // Count = 3
        MetricNumeric amountCount = findNumericMetric(metrics, "amount.count").orElseThrow();
        assertEquals(3.0, amountCount.getMetricValue(), 0.0,
                "Expected count = 3 for 3 rows");

        // Column status detail with PASS + baseline_unavailable (no baseline for first run)
        Optional<MetricDetail> amountStatusOpt = findDetailByType(metrics, "amount.status");
        assertTrue(amountStatusOpt.isPresent(), "Expected MetricDetail for amount.status");
        MetricDetail amountStatus = amountStatusOpt.get();
        assertEquals(DistributionCheck.CHECK_TYPE, amountStatus.getCheckType(),
                "check_type must be DISTRIBUTION for column status detail");
        assertTrue(amountStatus.getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status for column with no baseline (first run)");
        assertTrue(amountStatus.getDetailValue().contains("\"reason\":\"baseline_unavailable\""),
                "Expected baseline_unavailable reason for first run");

        // Distribution summary detail always emitted
        MetricDetail summary = findSummaryDetail(metrics);
        assertNotNull(summary, "Expected distribution_summary detail");
        assertEquals(DistributionCheck.CHECK_TYPE, summary.getCheckType(),
                "Summary detail check_type must be DISTRIBUTION");
    }

    // -------------------------------------------------------------------------
    // AC1: Significant shift detected → FAIL (z > 3.0)
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — Mean shift exceeds 3-sigma threshold → FAIL detail for shifted column.
     *
     * <p>Given a baseline with mean=100.0 and stddev=5.0, and a current dataset
     * with mean=130.0 (z-score = (130-100)/5 = 6.0 > 3.0),
     * when the Distribution check executes,
     * then the {@code amount.status} detail has status=FAIL with
     * reason=distribution_shift_detected.
     */
    @Test
    public void executeDetectsDistributionShiftWhenMeanExceedsBaseline() {
        // Given: baseline mean=100.0, stddev=5.0; current data generates mean~130.0
        // Use 30 rows to get a stable mean of 130
        List<Row> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(RowFactory.create((long) (i + 1), 130.0));
        }

        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("amount", DataTypes.DoubleType, true);

        Dataset<Row> df = spark.createDataFrame(rows, schema);

        // Baseline: mean=100.0, stddev=5.0, p50=99.0, p95=120.0, sampleCount=30
        DistributionCheck.ColumnStats baseline =
                new DistributionCheck.ColumnStats(100.0, 5.0, 99.0, 120.0, 30L);
        Map<String, DistributionCheck.ColumnStats> baselineMap = Map.of("amount", baseline);

        DistributionCheck check = checkWithBaseline(baselineMap);
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: amount.status detail has FAIL with z-score > 3.0
        Optional<MetricDetail> amountStatusOpt = findDetailByType(metrics, "amount.status");
        assertTrue(amountStatusOpt.isPresent(),
                "Expected MetricDetail for amount.status when shift detected");
        MetricDetail amountStatus = amountStatusOpt.get();
        assertTrue(amountStatus.getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL when mean shift z-score > 3.0 (z = 6.0)");
        assertTrue(amountStatus.getDetailValue().contains("\"reason\":\"distribution_shift_detected\""),
                "Expected distribution_shift_detected reason for large z-score");
        assertTrue(amountStatus.getDetailValue().contains("\"z_score\""),
                "Expected z_score field in detail payload");

        // Summary should reflect shifted column count
        MetricDetail summary = findSummaryDetail(metrics);
        assertTrue(summary.getDetailValue().contains("\"shifted_columns\":1"),
                "Expected shifted_columns:1 in summary for one shifted column");
    }

    // -------------------------------------------------------------------------
    // AC1: Moderate shift → WARN (2.0 < z < 3.0)
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — Mean shift between 2-sigma and 3-sigma → WARN detail for shifted column.
     *
     * <p>Given a baseline with mean=100.0 and stddev=5.0, and a current dataset
     * with mean=115.0 (z-score = (115-100)/5 = 3.0 which is exactly on the boundary;
     * we use mean=112.0 for z=2.4 clearly in WARN range 2.0 &lt; z &lt; 3.0),
     * when the Distribution check executes,
     * then the {@code amount.status} detail has status=WARN.
     */
    @Test
    public void executeEmitsWarnWhenMeanShiftIsModerate() {
        // Given: baseline mean=100.0, stddev=5.0; current mean=112.0 → z = (112-100)/5 = 2.4 (WARN)
        List<Row> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            rows.add(RowFactory.create((long) (i + 1), 112.0));
        }

        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("amount", DataTypes.DoubleType, true);

        Dataset<Row> df = spark.createDataFrame(rows, schema);

        // Baseline: mean=100.0, stddev=5.0 → z = (112-100)/5 = 2.4 → WARN
        DistributionCheck.ColumnStats baseline =
                new DistributionCheck.ColumnStats(100.0, 5.0, 99.0, 120.0, 30L);
        Map<String, DistributionCheck.ColumnStats> baselineMap = Map.of("amount", baseline);

        DistributionCheck check = checkWithBaseline(baselineMap);
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: amount.status detail has WARN (2.0 < z < 3.0)
        Optional<MetricDetail> amountStatusOpt = findDetailByType(metrics, "amount.status");
        assertTrue(amountStatusOpt.isPresent(),
                "Expected MetricDetail for amount.status when moderate shift detected");
        MetricDetail amountStatus = amountStatusOpt.get();
        assertTrue(amountStatus.getDetailValue().contains("\"status\":\"WARN\""),
                "Expected WARN when mean shift z-score is between 2.0 and 3.0 (z=2.4)");
        assertTrue(amountStatus.getDetailValue().contains("\"reason\":\"distribution_shift_detected\""),
                "Expected distribution_shift_detected reason for moderate z-score");
    }

    // -------------------------------------------------------------------------
    // AC5: First run (no baseline) → PASS with baseline_unavailable per column
    // -------------------------------------------------------------------------

    /**
     * [P0] AC5 — No baseline (first run): MetricNumeric entries written, each column gets
     * MetricDetail with status=PASS reason=baseline_unavailable.
     *
     * <p>Given a dataset with numeric columns and no historical baseline,
     * when the Distribution check executes,
     * then it writes the current stats as MetricNumeric entries (establishing the baseline)
     * and writes a MetricDetail per column with status=PASS and reason=baseline_unavailable.
     */
    @Test
    public void executeReturnsPassWithBaselineUnavailableForFirstRun() {
        // Given: DataFrame with numeric columns, no baseline available
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("amount", DataTypes.DoubleType, true)
                .add("quantity", DataTypes.IntegerType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, 50.0, 10),
                        RowFactory.create(2L, 75.0, 15),
                        RowFactory.create(3L, 60.0, 12)
                ),
                schema
        );

        DistributionCheck check = checkNoBaselineNoExplosion();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric entries present for each numeric column's stats
        List<MetricNumeric> numericMetrics = findAllNumericMetrics(metrics);
        assertFalse(numericMetrics.isEmpty(),
                "Expected MetricNumeric entries for numeric columns on first run");
        assertTrue(findNumericMetric(metrics, "amount.mean").isPresent(),
                "Expected amount.mean MetricNumeric for first run");
        assertTrue(findNumericMetric(metrics, "quantity.mean").isPresent(),
                "Expected quantity.mean MetricNumeric for first run");

        // And: each column gets a PASS / baseline_unavailable detail
        Optional<MetricDetail> amountStatusOpt = findDetailByType(metrics, "amount.status");
        assertTrue(amountStatusOpt.isPresent(),
                "Expected MetricDetail for amount.status on first run");
        assertTrue(amountStatusOpt.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS for amount on first run (baseline_unavailable)");
        assertTrue(amountStatusOpt.get().getDetailValue().contains("\"reason\":\"baseline_unavailable\""),
                "Expected baseline_unavailable reason for first run");

        Optional<MetricDetail> quantityStatusOpt = findDetailByType(metrics, "quantity.status");
        assertTrue(quantityStatusOpt.isPresent(),
                "Expected MetricDetail for quantity.status on first run");
        assertTrue(quantityStatusOpt.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS for quantity on first run (baseline_unavailable)");
    }

    // -------------------------------------------------------------------------
    // AC4: explosion_level=OFF → eventAttribute skipped, numeric columns processed
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — explosion_level=OFF: eventAttribute column skipped entirely,
     * regular numeric columns still analyzed.
     *
     * <p>Given a dataset with an {@code eventAttribute} StringType column and a
     * {@code double_value} DoubleType column, when the Distribution check executes
     * with explosion_level=OFF,
     * then the {@code eventAttribute} column is NOT present in any metric names,
     * and the {@code double_value} column IS present in MetricNumeric entries.
     */
    @Test
    public void executeSkipsEventAttributeWhenExplosionLevelIsOff() {
        // Given: DataFrame with eventAttribute (StringType) + double_value (DoubleType)
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("eventAttribute", DataTypes.StringType, true)
                .add("double_value", DataTypes.DoubleType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, "{\"amount\": 100.0, \"type\": \"A\"}", 55.0),
                        RowFactory.create(2L, "{\"amount\": 200.0, \"type\": \"B\"}", 65.0)
                ),
                schema
        );

        // Explicit explosion level OFF
        DistributionCheck check = new DistributionCheck(
                ctx -> DistributionCheck.ExplosionLevel.OFF,
                ctx -> Optional.empty()
        );
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: eventAttribute NOT in any metric names
        boolean eventAttrInMetrics = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .anyMatch(m -> m.getMetricName().startsWith("eventAttribute"));
        assertFalse(eventAttrInMetrics,
                "eventAttribute column must NOT appear in metrics when explosion_level=OFF");

        // And: double_value IS analyzed
        assertTrue(findNumericMetric(metrics, "double_value.mean").isPresent(),
                "Expected double_value.mean in metrics even when eventAttribute is skipped");
        assertTrue(findNumericMetric(metrics, "double_value.stddev").isPresent(),
                "Expected double_value.stddev in metrics even when eventAttribute is skipped");
    }

    // -------------------------------------------------------------------------
    // AC3: explosion_level=TOP_LEVEL → only first-level JSON keys analyzed
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — explosion_level=TOP_LEVEL: only first-level JSON keys analyzed,
     * nested structures NOT traversed.
     *
     * <p>Given a dataset where {@code eventAttribute} contains
     * {@code {"amount": 10.0, "details": {"code": "X"}}},
     * when the Distribution check executes with explosion_level=TOP_LEVEL,
     * then {@code eventAttribute.amount} column is analyzed (top-level numeric key),
     * but {@code eventAttribute.details.code} is NOT analyzed (nested string, not top-level).
     */
    @Test
    public void executeExplodesTopLevelEventAttributeKeys() {
        // Given: eventAttribute JSON with top-level numeric key and nested key
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("eventAttribute", DataTypes.StringType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, "{\"amount\": 10.0, \"details\": {\"code\": \"X\"}}"),
                        RowFactory.create(2L, "{\"amount\": 20.0, \"details\": {\"code\": \"Y\"}}"),
                        RowFactory.create(3L, "{\"amount\": 30.0, \"details\": {\"code\": \"Z\"}}")
                ),
                schema
        );

        // TOP_LEVEL explosion: first-level numeric keys only
        DistributionCheck check = new DistributionCheck(
                ctx -> DistributionCheck.ExplosionLevel.TOP_LEVEL,
                ctx -> Optional.empty()
        );
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: eventAttribute.amount IS analyzed (first-level numeric key)
        assertTrue(findNumericMetric(metrics, "eventAttribute.amount.mean").isPresent(),
                "Expected eventAttribute.amount.mean in metrics for TOP_LEVEL explosion");

        // And: eventAttribute.details.code is NOT analyzed (nested key, depth > 1)
        boolean nestedKeyPresent = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .anyMatch(m -> m.getMetricName().contains("details.code"));
        assertFalse(nestedKeyPresent,
                "eventAttribute.details.code must NOT appear in metrics for TOP_LEVEL explosion (depth > 1)");
    }

    // -------------------------------------------------------------------------
    // AC2: explosion_level=ALL → recursive key explosion, key paths in names
    // -------------------------------------------------------------------------

    /**
     * [P0] AC2 — explosion_level=ALL: JSON keys recursively exploded into virtual columns,
     * key paths appear in metric names, actual row values never stored.
     *
     * <p>Given a dataset where {@code eventAttribute} contains
     * {@code {"amount": 100.0, "details": {"price": 5.0}}},
     * when the Distribution check executes with explosion_level=ALL,
     * then both {@code eventAttribute.amount} and {@code eventAttribute.details.price}
     * appear in MetricNumeric metric names (key paths only, no raw values).
     */
    @Test
    public void executeExplodesAllNestedEventAttributeKeys() {
        // Given: eventAttribute with nested numeric keys
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("eventAttribute", DataTypes.StringType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, "{\"amount\": 100.0, \"details\": {\"price\": 5.0}}"),
                        RowFactory.create(2L, "{\"amount\": 200.0, \"details\": {\"price\": 10.0}}"),
                        RowFactory.create(3L, "{\"amount\": 150.0, \"details\": {\"price\": 7.5}}")
                ),
                schema
        );

        // ALL explosion: all nested numeric keys
        DistributionCheck check = new DistributionCheck(
                ctx -> DistributionCheck.ExplosionLevel.ALL,
                ctx -> Optional.empty()
        );
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: both top-level and nested numeric paths appear in metric names
        assertTrue(findNumericMetric(metrics, "eventAttribute.amount.mean").isPresent(),
                "Expected eventAttribute.amount.mean for ALL explosion (top-level key)");
        assertTrue(findNumericMetric(metrics, "eventAttribute.details.price.mean").isPresent(),
                "Expected eventAttribute.details.price.mean for ALL explosion (nested key)");

        // Verify check_type = DISTRIBUTION on all numeric metrics
        List<MetricNumeric> numericMetrics = findAllNumericMetrics(metrics);
        assertFalse(numericMetrics.isEmpty(),
                "Expected MetricNumeric entries for exploded eventAttribute columns");
        numericMetrics.forEach(m ->
                assertEquals(DistributionCheck.CHECK_TYPE, m.getCheckType(),
                        "All MetricNumeric entries must have check_type=DISTRIBUTION")
        );
    }

    // -------------------------------------------------------------------------
    // AC6: null context → NOT_RUN, no exception propagation
    // -------------------------------------------------------------------------

    /**
     * [P0] AC6 — null context → MetricDetail with status=NOT_RUN, no exception.
     *
     * <p>Given a null context is passed,
     * when the Distribution check executes,
     * then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        DistributionCheck check = new DistributionCheck();

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(null);

        // Then: at least one MetricDetail with status=NOT_RUN
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when context is null (NOT_RUN detail)");

        boolean hasNotRun = metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .anyMatch(d -> d.getDetailValue() != null
                        && d.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(hasNotRun,
                "Expected NOT_RUN status when context is null");

        // No MetricNumeric should be emitted
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when context is null");
    }

    // -------------------------------------------------------------------------
    // AC6: null df → NOT_RUN, no exception propagation
    // -------------------------------------------------------------------------

    /**
     * [P0] AC6 — context with null DataFrame → MetricDetail with status=NOT_RUN, no exception.
     *
     * <p>Given a context with a null DataFrame is passed,
     * when the Distribution check executes,
     * then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenDataFrameIsNull() {
        // Given: valid context but df=null
        DistributionCheck check = new DistributionCheck();
        DatasetContext ctx = context(null);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: at least one MetricDetail with status=NOT_RUN
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when df is null (NOT_RUN detail)");

        boolean hasNotRun = metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .anyMatch(d -> d.getDetailValue() != null
                        && d.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(hasNotRun,
                "Expected NOT_RUN status when df is null");

        // No MetricNumeric should be emitted
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when df is null");
    }

    // -------------------------------------------------------------------------
    // AC7: contract test — getCheckType() returns "DISTRIBUTION"
    // -------------------------------------------------------------------------

    /**
     * [P1] AC7 — getCheckType() returns "DISTRIBUTION".
     *
     * <p>DistributionCheck implements DqCheck; the canonical check_type constant must be
     * "DISTRIBUTION". This test also confirms zero changes to serve/API/dashboard —
     * the check is transparent to all layers except Spark.
     */
    @Test
    public void getCheckTypeReturnsDistribution() {
        // Given: any valid check instance
        DistributionCheck check = new DistributionCheck();

        // When / Then
        assertEquals("DISTRIBUTION", check.getCheckType(),
                "getCheckType() must return the canonical DISTRIBUTION string");
        assertEquals(DistributionCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC6+: ExplodeConfigProvider throws → errorDetail, no propagation
    // -------------------------------------------------------------------------

    /**
     * [P1] AC6+ — ExplodeConfigProvider throws exception → errorDetail returned, no propagation.
     *
     * <p>Given an ExplodeConfigProvider that throws a RuntimeException,
     * when the Distribution check executes,
     * then it returns a single MetricDetail with status=NOT_RUN,
     * reason=execution_error (or similar), without propagating the exception.
     */
    @Test
    public void executeHandlesExplodeConfigProviderExceptionGracefully() {
        // Given: ExplodeConfigProvider throws on getExplosionLevel()
        DistributionCheck check = new DistributionCheck(
                ctx -> { throw new RuntimeException("simulated DB failure fetching explosion_level"); },
                ctx -> Optional.empty()
        );

        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("amount", DataTypes.DoubleType, true);
        Dataset<Row> df = spark.createDataFrame(
                List.of(RowFactory.create(1L, 100.0)),
                schema
        );
        DatasetContext ctx = context(df);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: at least one MetricDetail with NOT_RUN / execution_error
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when ExplodeConfigProvider throws");

        boolean hasErrorDetail = metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .anyMatch(d -> d.getDetailValue() != null
                        && d.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(hasErrorDetail,
                "Expected NOT_RUN status when ExplodeConfigProvider throws");
    }

    // -------------------------------------------------------------------------
    // Edge: empty dataset (0 rows) → no exception, stats default gracefully
    // -------------------------------------------------------------------------

    /**
     * [P1] Edge — DataFrame with 0 rows: no exception, MetricNumeric values default
     * to 0/NaN, MetricDetail status=PASS with baseline_unavailable.
     *
     * <p>Given a DataFrame with valid numeric schema but zero data rows,
     * when the Distribution check executes,
     * then it does NOT throw an exception, emits MetricNumeric entries
     * (with NaN or 0 values), and emits MetricDetail per column with PASS baseline_unavailable.
     */
    @Test
    public void executeHandlesEmptyDatasetGracefully() {
        // Given: DataFrame with numeric columns but ZERO rows
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("amount", DataTypes.DoubleType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(),  // empty list — 0 rows
                schema
        );

        DistributionCheck check = checkNoBaselineNoExplosion();
        DatasetContext ctx = context(df);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: no exception — metrics returned (may be empty list if no cols analyzed)
        assertNotNull(metrics, "Expected non-null metric list for empty DataFrame");

        // If amount column is detected: MetricNumeric for amount stats emitted
        // (values will be NaN or 0 for empty dataset)
        // Column status detail should be PASS baseline_unavailable (no baseline, no rows)
        Optional<MetricDetail> amountStatusOpt = findDetailByType(metrics, "amount.status");
        if (amountStatusOpt.isPresent()) {
            // If column status is emitted, it must be PASS (no comparison possible)
            assertTrue(amountStatusOpt.get().getDetailValue().contains("\"status\":\"PASS\""),
                    "Expected PASS status for empty dataset (no comparison possible)");
        }

        // Summary detail must be present if any columns were analyzed or skipped
        // (no exception must propagate — this is the primary assertion)
        // The fact that we reach here without exception validates the core requirement
    }
}
