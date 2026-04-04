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
import org.mockito.Mockito;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD RED PHASE — tests will not compile until TimestampSanityCheck.java is implemented.
 *
 * <p>TimestampSanityCheck scans DataFrame schema for {@code TimestampType} and {@code DateType}
 * columns, counts future-dated and unreasonably old values per column, emits {@code MetricNumeric}
 * for both percentages per column, and emits {@code MetricDetail} with PASS/FAIL status based
 * on the 5% threshold.
 *
 * <p>SparkSession is required because {@code TimestampSanityCheck} calls
 * {@code context.getDf().schema().fields()} and Spark filter/count operations.
 * Follows the exact SparkSession lifecycle pattern from {@code ZeroRowCheckTest} and
 * {@code DistributionCheckTest}.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/TimestampSanityCheck.java} is created.
 *
 * <p>Partition date for tests: {@code LocalDate.of(2026, 4, 3)}.
 * Future threshold: {@code 2026-04-04} (partition + 1 day clock-skew tolerance).
 * Stale threshold: {@code 2016-04-03} (partition - 10 years).
 */
public class TimestampSanityCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    // Timestamps used across tests:
    // Future: after 2026-04-04 → flagged as future-dated
    private static final Timestamp FUTURE_TS  = Timestamp.valueOf(LocalDateTime.of(2026, 5, 1, 0, 0));
    // Normal: within acceptable range
    private static final Timestamp NORMAL_TS  = Timestamp.valueOf(LocalDateTime.of(2025, 6, 1, 0, 0));
    // Stale: before 2016-04-03 → flagged as unreasonably old
    private static final Timestamp STALE_TS   = Timestamp.valueOf(LocalDateTime.of(2000, 1, 1, 0, 0));

    // -------------------------------------------------------------------------
    // SparkSession lifecycle
    // -------------------------------------------------------------------------

    @BeforeAll
    public static void initSpark() {
        spark = SparkSession.builder()
                .appName("TimestampSanityCheckTest")
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
     * Build a {@code DatasetContext} wrapping the given DataFrame.
     * Uses the canonical path/code/date constants matching {@code ZeroRowCheckTest}.
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
     * Find the first {@code MetricNumeric} whose {@code metricName} matches.
     */
    private Optional<MetricNumeric> findNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst();
    }

    /**
     * Find the first {@code MetricDetail} whose {@code detailType} matches.
     */
    private Optional<MetricDetail> findDetailByType(List<DqMetric> metrics, String detailType) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> detailType.equals(d.getDetailType()))
                .findFirst();
    }

    /**
     * Find the {@code timestamp_sanity_summary} MetricDetail (always emitted once per execution).
     */
    private MetricDetail findSummaryDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> TimestampSanityCheck.DETAIL_TYPE_SUMMARY.equals(d.getDetailType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MetricDetail with detailType=" + TimestampSanityCheck.DETAIL_TYPE_SUMMARY
                                + " in " + metrics));
    }

    // -------------------------------------------------------------------------
    // AC1: >5% future-dated → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — 6 out of 10 rows are future-dated (60%) → future_pct > 5% → FAIL.
     *
     * <p>Given a dataset with timestamp column {@code event_ts} where 6 of 10 rows have
     * timestamps after the future threshold (2026-04-04),
     * When the Timestamp Sanity check executes,
     * Then it writes {@code MetricNumeric(TIMESTAMP_SANITY, "future_pct.event_ts", 0.6)}
     * and a {@code MetricDetail} with status=FAIL for that column.
     */
    @Test
    public void executeFlagsColumnAsFailWhenFuturePctExceedsThreshold() {
        // Given: 10 rows, 6 are future-dated (future_pct = 0.6 > 5% threshold)
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("event_ts", DataTypes.TimestampType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, FUTURE_TS),
                        RowFactory.create(2L, FUTURE_TS),
                        RowFactory.create(3L, FUTURE_TS),
                        RowFactory.create(4L, FUTURE_TS),
                        RowFactory.create(5L, FUTURE_TS),
                        RowFactory.create(6L, FUTURE_TS),
                        RowFactory.create(7L, NORMAL_TS),
                        RowFactory.create(8L, NORMAL_TS),
                        RowFactory.create(9L, NORMAL_TS),
                        RowFactory.create(10L, NORMAL_TS)
                ),
                schema
        );

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric with future_pct.event_ts = 0.6
        Optional<MetricNumeric> futurePctMetric = findNumericMetric(metrics, "future_pct.event_ts");
        assertTrue(futurePctMetric.isPresent(),
                "Expected MetricNumeric for future_pct.event_ts");
        assertEquals(TimestampSanityCheck.CHECK_TYPE, futurePctMetric.get().getCheckType(),
                "check_type must be TIMESTAMP_SANITY");
        assertEquals(0.6, futurePctMetric.get().getMetricValue(), 0.001,
                "future_pct must be 0.6 (6 future out of 10 non-null rows)");

        // And: MetricDetail with status=FAIL for event_ts column
        Optional<MetricDetail> columnDetail = findDetailByType(metrics, "timestamp_sanity.event_ts");
        assertTrue(columnDetail.isPresent(),
                "Expected MetricDetail for timestamp_sanity.event_ts");
        assertEquals(TimestampSanityCheck.CHECK_TYPE, columnDetail.get().getCheckType(),
                "check_type must be TIMESTAMP_SANITY for column detail");
        assertTrue(columnDetail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when future_pct > 5% threshold");
        assertTrue(columnDetail.get().getDetailValue().contains("\"column\":\"event_ts\""),
                "Expected column name in detail payload");
    }

    // -------------------------------------------------------------------------
    // AC2: ≤5% future-dated → PASS
    // -------------------------------------------------------------------------

    /**
     * [P0] AC2 — 0 of 10 rows are future-dated (0%) → future_pct ≤ 5% → PASS.
     *
     * <p>Given a dataset with timestamp column {@code event_ts} where none of the rows
     * are future-dated,
     * When the Timestamp Sanity check executes,
     * Then it writes {@code MetricNumeric(TIMESTAMP_SANITY, "future_pct.event_ts", 0.0)}
     * and a {@code MetricDetail} with status=PASS for that column.
     */
    @Test
    public void executePassesColumnWhenFuturePctBelowThreshold() {
        // Given: 10 rows, 0 are future-dated (future_pct = 0.0 ≤ 5% threshold)
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("event_ts", DataTypes.TimestampType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, NORMAL_TS),
                        RowFactory.create(2L, NORMAL_TS),
                        RowFactory.create(3L, NORMAL_TS),
                        RowFactory.create(4L, NORMAL_TS),
                        RowFactory.create(5L, NORMAL_TS),
                        RowFactory.create(6L, NORMAL_TS),
                        RowFactory.create(7L, NORMAL_TS),
                        RowFactory.create(8L, NORMAL_TS),
                        RowFactory.create(9L, NORMAL_TS),
                        RowFactory.create(10L, NORMAL_TS)
                ),
                schema
        );

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric with future_pct.event_ts = 0.0
        Optional<MetricNumeric> futurePctMetric = findNumericMetric(metrics, "future_pct.event_ts");
        assertTrue(futurePctMetric.isPresent(),
                "Expected MetricNumeric for future_pct.event_ts");
        assertEquals(0.0, futurePctMetric.get().getMetricValue(), 0.0,
                "future_pct must be 0.0 when no future-dated timestamps");

        // And: MetricDetail with status=PASS for event_ts column
        Optional<MetricDetail> columnDetail = findDetailByType(metrics, "timestamp_sanity.event_ts");
        assertTrue(columnDetail.isPresent(),
                "Expected MetricDetail for timestamp_sanity.event_ts");
        assertTrue(columnDetail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when future_pct ≤ 5% threshold");
    }

    // -------------------------------------------------------------------------
    // AC3: >5% stale (beyond max-age threshold) → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — 6 out of 10 rows are beyond the 10-year stale threshold → stale_pct > 5% → FAIL.
     *
     * <p>Given a dataset with timestamp column {@code event_ts} where 6 of 10 rows have
     * timestamps before the stale threshold (2016-04-03, i.e., 10 years before partition date),
     * When the Timestamp Sanity check executes,
     * Then it writes {@code MetricNumeric(TIMESTAMP_SANITY, "stale_pct.event_ts", 0.6)}
     * and a {@code MetricDetail} with status=FAIL for that column.
     */
    @Test
    public void executeFlagsColumnAsFailWhenStalePctExceedsThreshold() {
        // Given: 10 rows, 6 are older than 10 years (stale_pct = 0.6 > 5% threshold)
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("event_ts", DataTypes.TimestampType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, STALE_TS),
                        RowFactory.create(2L, STALE_TS),
                        RowFactory.create(3L, STALE_TS),
                        RowFactory.create(4L, STALE_TS),
                        RowFactory.create(5L, STALE_TS),
                        RowFactory.create(6L, STALE_TS),
                        RowFactory.create(7L, NORMAL_TS),
                        RowFactory.create(8L, NORMAL_TS),
                        RowFactory.create(9L, NORMAL_TS),
                        RowFactory.create(10L, NORMAL_TS)
                ),
                schema
        );

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric with stale_pct.event_ts = 0.6
        Optional<MetricNumeric> stalePctMetric = findNumericMetric(metrics, "stale_pct.event_ts");
        assertTrue(stalePctMetric.isPresent(),
                "Expected MetricNumeric for stale_pct.event_ts");
        assertEquals(TimestampSanityCheck.CHECK_TYPE, stalePctMetric.get().getCheckType(),
                "check_type must be TIMESTAMP_SANITY");
        assertEquals(0.6, stalePctMetric.get().getMetricValue(), 0.001,
                "stale_pct must be 0.6 (6 stale out of 10 non-null rows)");

        // And: MetricDetail with status=FAIL for event_ts column
        Optional<MetricDetail> columnDetail = findDetailByType(metrics, "timestamp_sanity.event_ts");
        assertTrue(columnDetail.isPresent(),
                "Expected MetricDetail for timestamp_sanity.event_ts");
        assertTrue(columnDetail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when stale_pct > 5% threshold");
    }

    // -------------------------------------------------------------------------
    // AC4: no timestamp columns → PASS with reason=no_timestamp_columns
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — DataFrame with only int/string columns → no timestamp columns detected.
     *
     * <p>Given a dataset with only {@code LongType} and {@code StringType} columns,
     * When the Timestamp Sanity check executes,
     * Then it writes a single {@code MetricDetail} with status=PASS and
     * reason=no_timestamp_columns and returns immediately (no MetricNumeric entries).
     */
    @Test
    public void executePassesWhenNoTimestampColumns() {
        // Given: DataFrame with no TimestampType or DateType columns
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(RowFactory.create(1L, "Alice")),
                schema
        );

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail summary with status=PASS and reason=no_timestamp_columns
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when no timestamp columns");

        MetricDetail summary = findSummaryDetail(metrics);
        assertEquals(TimestampSanityCheck.CHECK_TYPE, summary.getCheckType(),
                "check_type must be TIMESTAMP_SANITY");
        assertTrue(summary.getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no timestamp columns present");
        assertTrue(summary.getDetailValue().contains("\"reason\":\"no_timestamp_columns\""),
                "Expected no_timestamp_columns reason in summary detail");

        // And: no MetricNumeric emitted (no columns to analyze)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when no timestamp columns exist");
    }

    // -------------------------------------------------------------------------
    // AC5: null context → NOT_RUN, no exception propagation
    // -------------------------------------------------------------------------

    /**
     * [P0] AC5 — null context → MetricDetail with status=NOT_RUN, no exception.
     *
     * <p>Given a null context is passed,
     * When the Timestamp Sanity check executes,
     * Then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        TimestampSanityCheck check = new TimestampSanityCheck();

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

        // And: no MetricNumeric should be emitted
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when context is null");
    }

    // -------------------------------------------------------------------------
    // AC5: null DataFrame → NOT_RUN, no exception propagation
    // -------------------------------------------------------------------------

    /**
     * [P0] AC5 — context with null DataFrame → MetricDetail with status=NOT_RUN, no exception.
     *
     * <p>Given a valid context with a null DataFrame is passed,
     * When the Timestamp Sanity check executes,
     * Then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenDataFrameIsNull() {
        // Given: valid context but df=null
        TimestampSanityCheck check = new TimestampSanityCheck();
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

        // And: no MetricNumeric should be emitted
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when df is null");
    }

    // -------------------------------------------------------------------------
    // Edge: all-null timestamp column → 0.0 pct, PASS (divide-by-zero guard)
    // -------------------------------------------------------------------------

    /**
     * [P1] Edge — column with all-null values → futurePct=0.0, stalePct=0.0, status=PASS.
     *
     * <p>Given a dataset with a timestamp column where all values are null (non-null count = 0),
     * When the Timestamp Sanity check executes,
     * Then it does NOT throw (divide-by-zero guard) and emits MetricNumeric with 0.0
     * for both future_pct and stale_pct, with status=PASS.
     */
    @Test
    public void executeHandlesAllNullTimestampColumn() {
        // Given: timestamp column with all-null values (non-null count = 0)
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("event_ts", DataTypes.TimestampType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, (Timestamp) null),
                        RowFactory.create(2L, (Timestamp) null),
                        RowFactory.create(3L, (Timestamp) null)
                ),
                schema
        );

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(df);

        // When — must NOT throw (divide-by-zero guard when nonNullCount == 0)
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric with future_pct.event_ts = 0.0 (guarded division)
        Optional<MetricNumeric> futurePctMetric = findNumericMetric(metrics, "future_pct.event_ts");
        assertTrue(futurePctMetric.isPresent(),
                "Expected MetricNumeric for future_pct.event_ts even with all-null column");
        assertEquals(0.0, futurePctMetric.get().getMetricValue(), 0.0,
                "future_pct must be 0.0 when all values are null (nonNullCount == 0 guard)");

        // And: MetricNumeric with stale_pct.event_ts = 0.0
        Optional<MetricNumeric> stalePctMetric = findNumericMetric(metrics, "stale_pct.event_ts");
        assertTrue(stalePctMetric.isPresent(),
                "Expected MetricNumeric for stale_pct.event_ts even with all-null column");
        assertEquals(0.0, stalePctMetric.get().getMetricValue(), 0.0,
                "stale_pct must be 0.0 when all values are null");

        // And: column detail with PASS (0% < 5% threshold)
        Optional<MetricDetail> columnDetail = findDetailByType(metrics, "timestamp_sanity.event_ts");
        assertTrue(columnDetail.isPresent(),
                "Expected MetricDetail for timestamp_sanity.event_ts");
        assertTrue(columnDetail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status for all-null column (0% future, 0% stale)");
    }

    // -------------------------------------------------------------------------
    // MetricNumeric for both future_pct and stale_pct per column
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1/AC3 — verify both MetricNumeric entries (future_pct and stale_pct) are emitted
     * for each timestamp column with correct check_type.
     *
     * <p>Given a dataset with timestamp column {@code amount_date} containing only normal values,
     * When the Timestamp Sanity check executes,
     * Then it writes both {@code MetricNumeric(TIMESTAMP_SANITY, "future_pct.amount_date", 0.0)}
     * and {@code MetricNumeric(TIMESTAMP_SANITY, "stale_pct.amount_date", 0.0)}.
     */
    @Test
    public void executeWritesMetricNumericForFutureAndStalePct() {
        // Given: DataFrame with timestamp column amount_date — all values normal
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("amount_date", DataTypes.TimestampType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, NORMAL_TS),
                        RowFactory.create(2L, NORMAL_TS),
                        RowFactory.create(3L, NORMAL_TS)
                ),
                schema
        );

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: both MetricNumeric entries present for amount_date
        Optional<MetricNumeric> futurePct = findNumericMetric(metrics, "future_pct.amount_date");
        assertTrue(futurePct.isPresent(),
                "Expected MetricNumeric for future_pct.amount_date");
        assertEquals(TimestampSanityCheck.CHECK_TYPE, futurePct.get().getCheckType(),
                "check_type must be TIMESTAMP_SANITY for future_pct metric");
        assertEquals(0.0, futurePct.get().getMetricValue(), 0.0,
                "future_pct must be 0.0 for column with only normal timestamps");

        Optional<MetricNumeric> stalePct = findNumericMetric(metrics, "stale_pct.amount_date");
        assertTrue(stalePct.isPresent(),
                "Expected MetricNumeric for stale_pct.amount_date");
        assertEquals(TimestampSanityCheck.CHECK_TYPE, stalePct.get().getCheckType(),
                "check_type must be TIMESTAMP_SANITY for stale_pct metric");
        assertEquals(0.0, stalePct.get().getMetricValue(), 0.0,
                "stale_pct must be 0.0 for column with only normal timestamps");
    }

    // -------------------------------------------------------------------------
    // Summary detail reflects overall status across multiple columns
    // -------------------------------------------------------------------------

    /**
     * [P1] — one FAIL column + one PASS column → overall summary status=FAIL.
     *
     * <p>Given a dataset with two timestamp columns where one has >5% future-dated values
     * (FAIL) and one has 0% (PASS),
     * When the Timestamp Sanity check executes,
     * Then the summary {@code MetricDetail} has status=FAIL (any column FAIL → overall FAIL).
     */
    @Test
    public void executeSummaryDetailReflectsOverallStatus() {
        // Given: two timestamp columns — event_ts (6 future → FAIL), created_ts (0 future → PASS)
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("event_ts", DataTypes.TimestampType, true)
                .add("created_ts", DataTypes.TimestampType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(
                        RowFactory.create(1L, FUTURE_TS, NORMAL_TS),
                        RowFactory.create(2L, FUTURE_TS, NORMAL_TS),
                        RowFactory.create(3L, FUTURE_TS, NORMAL_TS),
                        RowFactory.create(4L, FUTURE_TS, NORMAL_TS),
                        RowFactory.create(5L, FUTURE_TS, NORMAL_TS),
                        RowFactory.create(6L, FUTURE_TS, NORMAL_TS),
                        RowFactory.create(7L, NORMAL_TS, NORMAL_TS),
                        RowFactory.create(8L, NORMAL_TS, NORMAL_TS),
                        RowFactory.create(9L, NORMAL_TS, NORMAL_TS),
                        RowFactory.create(10L, NORMAL_TS, NORMAL_TS)
                ),
                schema
        );

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: event_ts column detail is FAIL
        Optional<MetricDetail> eventTsDetail = findDetailByType(metrics, "timestamp_sanity.event_ts");
        assertTrue(eventTsDetail.isPresent(),
                "Expected MetricDetail for timestamp_sanity.event_ts");
        assertTrue(eventTsDetail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL for event_ts column (future_pct = 0.6 > threshold)");

        // And: created_ts column detail is PASS
        Optional<MetricDetail> createdTsDetail = findDetailByType(metrics, "timestamp_sanity.created_ts");
        assertTrue(createdTsDetail.isPresent(),
                "Expected MetricDetail for timestamp_sanity.created_ts");
        assertTrue(createdTsDetail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS for created_ts column (future_pct = 0.0 ≤ threshold)");

        // And: summary detail overall status = FAIL (any FAIL → overall FAIL)
        MetricDetail summary = findSummaryDetail(metrics);
        assertEquals(TimestampSanityCheck.CHECK_TYPE, summary.getCheckType(),
                "check_type must be TIMESTAMP_SANITY for summary detail");
        assertTrue(summary.getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected overall FAIL in summary when at least one column is FAIL");
    }

    // -------------------------------------------------------------------------
    // AC6: contract test — getCheckType() returns "TIMESTAMP_SANITY"
    // -------------------------------------------------------------------------

    /**
     * [P1] AC6 — getCheckType() returns "TIMESTAMP_SANITY".
     *
     * <p>TimestampSanityCheck implements DqCheck; the canonical check_type constant must be
     * "TIMESTAMP_SANITY". This test also confirms zero changes to serve/API/dashboard —
     * the check type is transparent to all layers except Spark.
     */
    @Test
    public void getCheckTypeReturnsTimestampSanity() {
        // Given: any valid check instance
        TimestampSanityCheck check = new TimestampSanityCheck();

        // When / Then
        assertEquals("TIMESTAMP_SANITY", check.getCheckType(),
                "getCheckType() must return the canonical TIMESTAMP_SANITY string");
        assertEquals(TimestampSanityCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // Exception path → errorDetail, no exception propagation
    // -------------------------------------------------------------------------

    /**
     * [P1] Exception path — context whose getDf() call throws → errorDetail returned, no propagation.
     *
     * <p>Given a context whose underlying DataFrame schema() or filter() call throws a RuntimeException,
     * When the Timestamp Sanity check executes,
     * Then it returns at least one MetricDetail with status=NOT_RUN (or similar error detail)
     * and does NOT propagate the exception.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void executeHandlesExceptionGracefully() {
        // Given: a mocked Dataset that throws when schema() is called
        Dataset<Row> brokenDf = (Dataset<Row>) Mockito.mock(Dataset.class);
        Mockito.when(brokenDf.schema()).thenThrow(
                new RuntimeException("simulated Spark schema failure"));

        TimestampSanityCheck check = new TimestampSanityCheck();
        DatasetContext ctx = context(brokenDf);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: at least one MetricDetail returned (error detail)
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when DataFrame.schema() throws");

        boolean hasErrorDetail = metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .anyMatch(d -> d.getDetailValue() != null
                        && (d.getDetailValue().contains("\"status\":\"NOT_RUN\"")
                            || d.getDetailValue().contains("\"status\":\"ERROR\"")));
        assertTrue(hasErrorDetail,
                "Expected NOT_RUN or error status when DataFrame.schema() throws");

        // And: no MetricNumeric should be present (exception occurred before any metrics emitted)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when execution throws before column analysis");
    }
}
