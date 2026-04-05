package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD RED PHASE — tests will not compile until LineageCheck.java is implemented.
 *
 * <p>LineageCheck detects upstream health signals by querying {@code v_dq_run_active} for other
 * source systems ({@code src_sys_nm} groups) that ran on the same {@code partition_date}.
 * It emits {@code MetricNumeric} for {@code upstream_dataset_count} and
 * {@code upstream_failed_count}, plus a {@code MetricDetail} with status WARN (if any upstream
 * failed) or PASS (if all upstreams healthy or no upstream data found).
 *
 * <p>Graceful skips:
 * <ul>
 *   <li>No {@code src_sys_nm=} segment in dataset_name → MetricDetail status=PASS,
 *       reason=no_source_system_segment</li>
 *   <li>No upstream datasets found (provider returns empty) → MetricDetail status=PASS,
 *       reason=no_upstream_datasets</li>
 *   <li>Null context → MetricDetail status=NOT_RUN, no exception</li>
 * </ul>
 *
 * <p>No SparkSession required: this check does NOT call {@code context.getDf()}.
 * It is purely JDBC-based. All tests use plain JUnit 5 with a mock
 * {@code LineageStatsProvider} injected via lambda.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/LineageCheck.java} is created.
 */
class LineageCheckTest {

    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 4);

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a minimal {@code DatasetContext} with df=null (safe — this check never calls getDf()).
     */
    private DatasetContext context(String datasetName) {
        return new DatasetContext(
                datasetName,
                "LOB_RETAIL",
                PARTITION_DATE,
                "/prod/data",
                null,
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
     * Find the first {@code MetricDetail} whose {@code detailType} matches
     * {@link LineageCheck#DETAIL_TYPE_STATUS}.
     */
    private Optional<MetricDetail> findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> LineageCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC1: upstream datasets failing → WARN
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — provider returns stats with upstreamDatasetCount=5, upstreamFailedCount=2 →
     * MetricNumeric upstream_dataset_count=5.0, MetricNumeric upstream_failed_count=2.0,
     * MetricDetail status=WARN.
     *
     * <p>Given a dataset with src_sys_nm=alpha and 5 upstream datasets where 2 are failing
     * When the LineageCheck executes
     * Then it emits MetricNumeric for upstream_dataset_count=5 and upstream_failed_count=2
     * And it emits MetricDetail with status=WARN.
     */
    @Test
    void executeWritesWarnMetricWhenUpstreamDatasetsFailing() {
        // Given: provider returns upstreamDatasetCount=5, upstreamFailedCount=2
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.of(new LineageCheck.LineageStats(5, 2))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric upstream_failed_count=2.0
        Optional<MetricNumeric> failedCount = findNumericMetric(metrics,
                LineageCheck.METRIC_UPSTREAM_FAILED_COUNT);
        assertTrue(failedCount.isPresent(),
                "Expected MetricNumeric for upstream_failed_count");
        assertEquals(LineageCheck.CHECK_TYPE, failedCount.get().getCheckType(),
                "check_type must be LINEAGE");
        assertEquals(2.0, failedCount.get().getMetricValue(), 0.001,
                "upstream_failed_count must be 2.0");

        // And: MetricDetail status=WARN
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with lineage_status detailType");
        assertEquals(LineageCheck.CHECK_TYPE, detail.get().getCheckType(),
                "check_type must be LINEAGE for detail");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"WARN\""),
                "Expected WARN status when upstream datasets are failing");
    }

    // -------------------------------------------------------------------------
    // AC1: all upstreams healthy → PASS
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — provider returns stats with upstreamDatasetCount=3, upstreamFailedCount=0 →
     * MetricDetail status=PASS.
     *
     * <p>Given a dataset with src_sys_nm=alpha and 3 upstream datasets where none are failing
     * When the LineageCheck executes
     * Then it emits MetricDetail with status=PASS.
     */
    @Test
    void executeWritesPassMetricWhenAllUpstreamsHealthy() {
        // Given: provider returns upstreamDatasetCount=3, upstreamFailedCount=0
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.of(new LineageCheck.LineageStats(3, 0))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail status=PASS
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for PASS case when all upstreams healthy");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when upstream_failed_count=0");

        // And: MetricNumeric upstream_failed_count=0.0
        Optional<MetricNumeric> failedCount = findNumericMetric(metrics,
                LineageCheck.METRIC_UPSTREAM_FAILED_COUNT);
        assertTrue(failedCount.isPresent(),
                "Expected MetricNumeric for upstream_failed_count even when count=0");
        assertEquals(0.0, failedCount.get().getMetricValue(), 0.001,
                "upstream_failed_count must be 0.0 when all upstreams healthy");
    }

    // -------------------------------------------------------------------------
    // AC2: no src_sys_nm segment → graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P0] AC2 — dataset_name without src_sys_nm= segment → MetricDetail status=PASS,
     * reason=no_source_system_segment.
     *
     * <p>Given a dataset whose dataset_name does not contain a src_sys_nm= path segment
     * When the LineageCheck executes
     * Then it returns MetricDetail with status=PASS and reason=no_source_system_segment
     * (graceful skip — lineage requires src_sys_nm for peer identification).
     */
    @Test
    void executeReturnsPassWhenNoSourceSystemSegmentInDatasetName() {
        // Given: dataset_name with no src_sys_nm= segment
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.of(new LineageCheck.LineageStats(5, 2))
        );
        DatasetContext ctx = context("lob=retail/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_source_system_segment
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when src_sys_nm not in dataset_name");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with lineage_status detailType");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no src_sys_nm segment found");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"no_source_system_segment\""),
                "Expected no_source_system_segment reason");

        // And: no MetricNumeric (no upstream data to report)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when src_sys_nm not found");
    }

    // -------------------------------------------------------------------------
    // AC1: provider returns empty (no upstream datasets found) → graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — provider returns empty (no upstream datasets on this partition_date) →
     * MetricDetail status=PASS, reason=no_upstream_datasets.
     *
     * <p>Given a dataset with src_sys_nm=alpha but no other source system datasets exist
     * on this partition_date
     * When the LineageCheck executes
     * Then it returns MetricDetail with status=PASS and reason=no_upstream_datasets.
     */
    @Test
    void executeReturnsPassWhenNoUpstreamDatasetsFound() {
        // Given: provider returns empty (no upstream datasets on this partition_date)
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.empty()
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_upstream_datasets
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when no upstream datasets found");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail when no upstream datasets found");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no upstream datasets found");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"no_upstream_datasets\""),
                "Expected no_upstream_datasets reason");

        // And: no MetricNumeric
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when no upstream datasets found");
    }

    // -------------------------------------------------------------------------
    // AC3: null context → NOT_RUN, no exception
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — null context → MetricDetail with status=NOT_RUN, no exception propagation.
     *
     * <p>Given a null context is passed to execute()
     * When the check executes
     * Then it returns a MetricDetail with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.empty()
        );

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

        // And: no MetricNumeric
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when context is null");
    }

    // -------------------------------------------------------------------------
    // Exception handling — provider throws → errorDetail, no propagation
    // -------------------------------------------------------------------------

    /**
     * [P1] Exception path — LineageStatsProvider throws RuntimeException →
     * errorDetail returned, no exception propagation.
     *
     * <p>Given a LineageStatsProvider that throws a RuntimeException (simulated JDBC failure)
     * When the check executes
     * Then it returns at least one MetricDetail error detail and does NOT propagate the exception.
     */
    @Test
    void executeHandlesExceptionGracefully() {
        // Given: provider throws RuntimeException
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> { throw new RuntimeException("simulated JDBC failure"); }
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: at least one MetricDetail returned (error detail)
        assertFalse(metrics.isEmpty(),
                "Expected at least one error detail metric when provider throws");

        boolean hasErrorStatus = metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .anyMatch(d -> d.getDetailValue() != null
                        && (d.getDetailValue().contains("\"status\":\"NOT_RUN\"")
                         || d.getDetailValue().contains("\"status\":\"ERROR\"")));
        assertTrue(hasErrorStatus,
                "Expected NOT_RUN or error status when provider throws");

        // And: no MetricNumeric (metrics cleared in catch block)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be present — metrics.clear() in catch block");
    }

    // -------------------------------------------------------------------------
    // Contract test — getCheckType()
    // -------------------------------------------------------------------------

    /**
     * [P1] Contract test — getCheckType() returns "LINEAGE".
     *
     * <p>LineageCheck implements DqCheck; the canonical check_type constant must be "LINEAGE".
     * Zero changes to serve/API/dashboard — the check type is transparent to all layers
     * except the Spark job.
     */
    @Test
    void getCheckTypeReturnsLineage() {
        // Given: any valid check instance
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.empty()
        );

        // When / Then
        assertEquals("LINEAGE", check.getCheckType(),
                "getCheckType() must return the canonical LINEAGE string");
        assertEquals(LineageCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC1: WARN detail payload contains all required context fields
    // -------------------------------------------------------------------------

    /**
     * [P1] AC1 — WARN detail payload contains src_sys_nm, upstream_dataset_count,
     * upstream_failed_count, upstream_fail_threshold — all required for downstream alerting.
     *
     * <p>Given the lineage check produces a WARN result
     * When the MetricDetail is inspected
     * Then its detailValue JSON contains all required context fields per the story spec.
     */
    @Test
    void executeWarnDetailContainsAllRequiredContextFields() {
        // Given: provider returns upstreamDatasetCount=5, upstreamFailedCount=2 (WARN case)
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.of(new LineageCheck.LineageStats(5, 2))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: detail payload contains all required fields
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(), "Expected MetricDetail for WARN case");

        String payload = detail.get().getDetailValue();
        assertTrue(payload.contains("\"status\":\"WARN\""),
                "Expected WARN status in detail payload");
        assertTrue(payload.contains("\"src_sys_nm\""),
                "Expected src_sys_nm field in WARN detail payload");
        assertTrue(payload.contains("\"upstream_dataset_count\""),
                "Expected upstream_dataset_count field in WARN detail payload");
        assertTrue(payload.contains("\"upstream_failed_count\""),
                "Expected upstream_failed_count field in WARN detail payload");
        assertTrue(payload.contains("\"upstream_fail_threshold\""),
                "Expected upstream_fail_threshold field in WARN detail payload");
    }

    // -------------------------------------------------------------------------
    // AC1: both MetricNumeric metrics emitted with correct values
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — verify both MetricNumeric for upstream_dataset_count and upstream_failed_count
     * are emitted with correct values when upstream datasets exist.
     *
     * <p>Given the lineage check executes with upstreamDatasetCount=5, upstreamFailedCount=2
     * When the metrics are inspected
     * Then upstream_dataset_count=5.0 and upstream_failed_count=2.0 are both present.
     */
    @Test
    void executeEmitsCorrectMetricNumericsForUpstreamCounts() {
        // Given: provider returns upstreamDatasetCount=5, upstreamFailedCount=2
        LineageCheck check = new LineageCheck(
                (srcSysNm, date) -> Optional.of(new LineageCheck.LineageStats(5, 2))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric upstream_dataset_count=5.0
        Optional<MetricNumeric> datasetCount = findNumericMetric(metrics,
                LineageCheck.METRIC_UPSTREAM_DATASET_COUNT);
        assertTrue(datasetCount.isPresent(),
                "Expected MetricNumeric for upstream_dataset_count");
        assertEquals(LineageCheck.CHECK_TYPE, datasetCount.get().getCheckType(),
                "check_type must be LINEAGE for upstream_dataset_count metric");
        assertEquals(5.0, datasetCount.get().getMetricValue(), 0.001,
                "upstream_dataset_count must be 5.0");

        // And: MetricNumeric upstream_failed_count=2.0
        Optional<MetricNumeric> failedCount = findNumericMetric(metrics,
                LineageCheck.METRIC_UPSTREAM_FAILED_COUNT);
        assertTrue(failedCount.isPresent(),
                "Expected MetricNumeric for upstream_failed_count");
        assertEquals(2.0, failedCount.get().getMetricValue(), 0.001,
                "upstream_failed_count must be 2.0");
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    /**
     * [P1] Constructor validation — null statsProvider throws IllegalArgumentException.
     *
     * <p>Given a null LineageStatsProvider is passed to the constructor
     * When the LineageCheck is instantiated
     * Then it throws IllegalArgumentException (fail-fast contract per project rules).
     */
    @Test
    void constructorThrowsWhenStatsProviderIsNull() {
        // When / Then
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LineageCheck(null),
                "Constructor must throw IllegalArgumentException when statsProvider is null"
        );
    }
}
