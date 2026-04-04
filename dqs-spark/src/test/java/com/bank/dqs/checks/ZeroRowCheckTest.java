package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD RED PHASE — tests will not compile until ZeroRowCheck.java is implemented.
 *
 * <p>ZeroRowCheck is the simplest Tier 2 check: calls {@code context.getDf().count()}
 * and returns FAIL when rowCount == 0, PASS otherwise.
 *
 * <p>SparkSession is required because {@code ZeroRowCheck} calls
 * {@code context.getDf().count()} — unlike {@code SlaCountdownCheck}, which is time-based
 * only.  Follow the exact SparkSession lifecycle pattern from {@code VolumeCheckTest}.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/ZeroRowCheck.java} is created.
 */
public class ZeroRowCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    // -------------------------------------------------------------------------
    // SparkSession lifecycle
    // -------------------------------------------------------------------------

    @BeforeAll
    public static void initSpark() {
        spark = SparkSession.builder()
                .appName("ZeroRowCheckTest")
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
     * Build a DataFrame with {@code rowCount} rows using Spark range.
     * Use {@code spark.range(0).toDF("id")} for the empty case.
     */
    private Dataset<Row> dfWithRows(long rowCount) {
        return spark.range(rowCount).toDF("id");
    }

    /**
     * Build a DatasetContext wrapping the given DataFrame.
     * Uses the canonical path/code/date constants matching {@code VolumeCheckTest}.
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
    private MetricNumeric findNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MetricNumeric with metricName=" + metricName + " in " + metrics));
    }

    /**
     * Find the first {@code MetricDetail} whose {@code detailType} matches
     * {@code ZeroRowCheck.DETAIL_TYPE_STATUS}.
     */
    private MetricDetail findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> ZeroRowCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MetricDetail with detailType=" + ZeroRowCheck.DETAIL_TYPE_STATUS
                                + " in " + metrics));
    }

    // -------------------------------------------------------------------------
    // AC1: empty DataFrame → FAIL + row_count = 0.0
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — DataFrame with zero rows → FAIL, row_count = 0.0.
     *
     * <p>Given a dataset DataFrame with zero rows,
     * When the Zero-Row check executes,
     * Then it writes MetricNumeric(ZERO_ROW, row_count, 0.0) and a detail with status=FAIL.
     */
    @Test
    public void executeReturnsFailAndZeroRowCountWhenDataFrameIsEmpty() {
        // Given: DataFrame with 0 rows
        ZeroRowCheck check = new ZeroRowCheck();
        DatasetContext ctx = context(dfWithRows(0));

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric with check_type=ZERO_ROW, metric_name=row_count, value=0.0
        MetricNumeric rowCount = findNumericMetric(metrics, ZeroRowCheck.METRIC_ROW_COUNT);
        assertEquals(ZeroRowCheck.CHECK_TYPE, rowCount.getCheckType(),
                "check_type must be ZERO_ROW");
        assertEquals(0.0, rowCount.getMetricValue(), 0.0,
                "row_count must be 0.0 for an empty DataFrame");

        // And: status detail must be FAIL
        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status in detail payload for zero-row DataFrame");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"zero_rows\""),
                "Expected zero_rows reason in detail payload");
        assertTrue(detail.getDetailValue().contains("\"row_count\":0"),
                "Expected row_count:0 in detail payload");
    }

    // -------------------------------------------------------------------------
    // AC2: non-empty DataFrame → PASS + row_count = actual count
    // -------------------------------------------------------------------------

    /**
     * [P0] AC2 — DataFrame with 5 rows → PASS, row_count = 5.0.
     *
     * <p>Given a dataset DataFrame with 5 rows,
     * When the Zero-Row check executes,
     * Then it writes MetricNumeric(ZERO_ROW, row_count, 5.0) and a detail with status=PASS.
     */
    @Test
    public void executeReturnsPassAndRowCountWhenDataFrameHasRows() {
        // Given: DataFrame with 5 rows
        ZeroRowCheck check = new ZeroRowCheck();
        DatasetContext ctx = context(dfWithRows(5));

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric with value=5.0
        MetricNumeric rowCount = findNumericMetric(metrics, ZeroRowCheck.METRIC_ROW_COUNT);
        assertEquals(ZeroRowCheck.CHECK_TYPE, rowCount.getCheckType(),
                "check_type must be ZERO_ROW");
        assertEquals(5.0, rowCount.getMetricValue(), 0.001,
                "row_count must equal actual DataFrame row count");

        // And: status detail must be PASS
        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status in detail payload for non-empty DataFrame");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"has_rows\""),
                "Expected has_rows reason in detail payload");
        assertTrue(detail.getDetailValue().contains("\"row_count\":5"),
                "Expected row_count:5 in detail payload");
    }

    /**
     * [P1] AC2 boundary — DataFrame with exactly 1 row → PASS, row_count = 1.0.
     *
     * <p>Given a dataset DataFrame with exactly 1 row (boundary condition),
     * When the Zero-Row check executes,
     * Then it returns PASS (1 row is sufficient — not empty).
     */
    @Test
    public void executeReturnsSingleRowHandledCorrectly() {
        // Given: DataFrame with 1 row (boundary)
        ZeroRowCheck check = new ZeroRowCheck();
        DatasetContext ctx = context(dfWithRows(1));

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: row_count = 1.0, status = PASS
        MetricNumeric rowCount = findNumericMetric(metrics, ZeroRowCheck.METRIC_ROW_COUNT);
        assertEquals(1.0, rowCount.getMetricValue(), 0.0,
                "row_count must be 1.0 for a single-row DataFrame");

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"PASS\""),
                "Single row must produce PASS — not zero means not empty");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"has_rows\""),
                "Expected has_rows reason for single-row boundary case");
    }

    // -------------------------------------------------------------------------
    // AC3: null context or null DataFrame → NOT_RUN, no exception propagation
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — null context → detail with status=NOT_RUN, no exception.
     *
     * <p>Given a null context is passed,
     * When the Zero-Row check executes,
     * Then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        ZeroRowCheck check = new ZeroRowCheck();

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(null);

        // Then: at least one MetricDetail with status=NOT_RUN
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when context is null (NOT_RUN detail)");

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Expected NOT_RUN status when context is null");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"missing_context\""),
                "Expected missing_context reason when context is null");

        // And: no numeric metrics should be present (nothing to count)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when context is null");
    }

    /**
     * [P0] AC3 — context with null DataFrame → detail with status=NOT_RUN, no exception.
     *
     * <p>Given a context with a null DataFrame is passed,
     * When the Zero-Row check executes,
     * Then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenDataFrameIsNull() {
        // Given: valid context but df=null
        ZeroRowCheck check = new ZeroRowCheck();
        DatasetContext ctx = context(null);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: NOT_RUN detail with missing_dataframe reason
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when df is null (NOT_RUN detail)");

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Expected NOT_RUN status when df is null");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"missing_dataframe\""),
                "Expected missing_dataframe reason when df is null");

        // And: no numeric metrics
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when df is null");
    }

    // -------------------------------------------------------------------------
    // AC4: contract test — getCheckType() returns "ZERO_ROW"
    // -------------------------------------------------------------------------

    /**
     * [P1] AC4 — getCheckType() returns "ZERO_ROW".
     *
     * <p>ZeroRowCheck implements DqCheck; the canonical check_type constant must be "ZERO_ROW".
     */
    @Test
    public void getCheckTypeReturnsZeroRow() {
        // Given: any valid check instance
        ZeroRowCheck check = new ZeroRowCheck();

        // When / Then
        assertEquals("ZERO_ROW", check.getCheckType(),
                "getCheckType() must return the canonical ZERO_ROW string");
        assertEquals(ZeroRowCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC3+: exception path → errorDetail with NOT_RUN and error_type
    // -------------------------------------------------------------------------

    /**
     * [P1] Exception path — broken DataFrame throws RuntimeException → errorDetail produced.
     *
     * <p>Given a context whose DataFrame.count() throws a RuntimeException,
     * When the Zero-Row check executes,
     * Then it returns a single MetricDetail with status=NOT_RUN, reason=execution_error,
     * and the error_type captured from the exception class, without propagating the exception.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void executeReturnsErrorDetailWhenDataFrameCountThrows() {
        // Given: a mocked Dataset that throws when count() is called
        Dataset<Row> brokenDf = (Dataset<Row>) Mockito.mock(Dataset.class);
        Mockito.when(brokenDf.count()).thenThrow(new RuntimeException("simulated Spark failure"));

        ZeroRowCheck check = new ZeroRowCheck();
        DatasetContext ctx = context(brokenDf);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: exactly one MetricDetail with NOT_RUN / execution_error
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when DataFrame.count() throws");

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Expected NOT_RUN status when execution throws");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"execution_error\""),
                "Expected execution_error reason when execution throws");
        assertTrue(detail.getDetailValue().contains("\"error_type\":\"RuntimeException\""),
                "Expected error_type to capture the exception class name");

        // And: no numeric metrics should be present
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when execution throws");
    }
}
