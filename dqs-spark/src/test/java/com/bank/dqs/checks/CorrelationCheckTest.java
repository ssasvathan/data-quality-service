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
 * ATDD RED PHASE — tests will not compile until CorrelationCheck.java is implemented.
 *
 * <p>CorrelationCheck detects cross-dataset co-degradation within a source system. It queries
 * {@code v_dq_run_active} to find how many datasets sharing the same {@code src_sys_nm} path
 * segment simultaneously experienced DQS score drops compared to their own recent baseline.
 * It emits {@code MetricNumeric} for {@code correlated_dataset_count} and {@code correlation_ratio},
 * plus a {@code MetricDetail} with status FAIL/WARN/PASS based on the ratio thresholds:
 * FAIL if {@code correlation_ratio >= 0.50}, WARN if {@code >= 0.25}, PASS otherwise.
 *
 * <p>No SparkSession required: this check does NOT call {@code context.getDf()}.
 * It is purely JDBC-based. All tests use plain JUnit 5 with a mock
 * {@code CorrelationStatsProvider} injected via lambda.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/CorrelationCheck.java} is created.
 */
class CorrelationCheckTest {

    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

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
     * {@link CorrelationCheck#DETAIL_TYPE_STATUS}.
     */
    private Optional<MetricDetail> findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> CorrelationCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC1: correlation_ratio >= FAIL_RATIO (0.50) → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — provider returns correlationRatio=0.6, correlatedCount=3, totalCount=5 →
     * MetricNumeric correlated_dataset_count=3.0, MetricNumeric correlation_ratio=0.6,
     * MetricDetail status=FAIL.
     *
     * <p>Given datasets in source system "alpha" where 3 of 5 datasets degraded simultaneously
     * When the CorrelationCheck executes and correlation_ratio=0.6 (>= 0.50 FAIL threshold)
     * Then it writes MetricNumeric for correlated_dataset_count=3.0 and correlation_ratio=0.6
     * And it writes MetricDetail with status=FAIL.
     */
    @Test
    void executeWritesFailMetricsWhenMajorityOfSourceSystemDegraded() {
        // Given: provider returns correlatedCount=3, totalCount=5, ratio=0.6 (>= 0.50 FAIL threshold)
        CorrelationCheck check = new CorrelationCheck(
                (srcSysNm, date) -> Optional.of(
                        new CorrelationCheck.CorrelationStats(3, 5, 0.60))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric correlated_dataset_count=3.0
        Optional<MetricNumeric> correlatedCount = findNumericMetric(metrics,
                CorrelationCheck.METRIC_CORRELATED_DATASET_COUNT);
        assertTrue(correlatedCount.isPresent(),
                "Expected MetricNumeric for correlated_dataset_count");
        assertEquals(CorrelationCheck.CHECK_TYPE, correlatedCount.get().getCheckType(),
                "check_type must be CORRELATION");
        assertEquals(3.0, correlatedCount.get().getMetricValue(), 0.001,
                "correlated_dataset_count must be 3.0");

        // And: MetricNumeric correlation_ratio=0.6
        Optional<MetricNumeric> correlationRatio = findNumericMetric(metrics,
                CorrelationCheck.METRIC_CORRELATION_RATIO);
        assertTrue(correlationRatio.isPresent(),
                "Expected MetricNumeric for correlation_ratio");
        assertEquals(CorrelationCheck.CHECK_TYPE, correlationRatio.get().getCheckType(),
                "check_type must be CORRELATION for correlation_ratio metric");
        assertEquals(0.60, correlationRatio.get().getMetricValue(), 0.001,
                "correlation_ratio must be 0.60");

        // And: MetricDetail status=FAIL
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with correlation_status detailType");
        assertEquals(CorrelationCheck.CHECK_TYPE, detail.get().getCheckType(),
                "check_type must be CORRELATION for detail metric");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when correlation_ratio >= 0.50");
        assertTrue(detail.get().getDetailValue().contains("\"correlation_ratio\":0.6"),
                "Expected correlation_ratio in FAIL detail payload");
    }

    // -------------------------------------------------------------------------
    // AC1: WARN_RATIO (0.25) <= correlation_ratio < FAIL_RATIO (0.50) → WARN
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — provider returns correlationRatio=0.33 (between 0.25 and 0.50) → status=WARN.
     *
     * <p>Given datasets in source system "beta" where only a minority degraded simultaneously
     * When the CorrelationCheck executes and correlation_ratio=0.33 (>= 0.25 WARN, < 0.50 FAIL)
     * Then MetricDetail status=WARN.
     */
    @Test
    void executeWritesWarnMetricsWhenMinorityDegraded() {
        // Given: provider returns ratio=0.33 (>= 0.25 WARN threshold, < 0.50 FAIL threshold)
        CorrelationCheck check = new CorrelationCheck(
                (srcSysNm, date) -> Optional.of(
                        new CorrelationCheck.CorrelationStats(2, 6, 0.33))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=beta/dataset=loans_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric correlation_ratio=0.33
        Optional<MetricNumeric> correlationRatio = findNumericMetric(metrics,
                CorrelationCheck.METRIC_CORRELATION_RATIO);
        assertTrue(correlationRatio.isPresent(),
                "Expected MetricNumeric for correlation_ratio in WARN case");
        assertEquals(0.33, correlationRatio.get().getMetricValue(), 0.001,
                "correlation_ratio must be 0.33");

        // And: MetricDetail status=WARN
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for WARN case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"WARN\""),
                "Expected WARN status when 0.25 <= correlation_ratio < 0.50");
    }

    // -------------------------------------------------------------------------
    // AC1: correlation_ratio < WARN_RATIO (0.25) → PASS
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — provider returns correlationRatio=0.10 (< 0.25) → status=PASS.
     *
     * <p>Given datasets where very few co-degraded (only isolated degradation)
     * When the CorrelationCheck executes and correlation_ratio=0.10 (< 0.25 WARN threshold)
     * Then MetricDetail status=PASS.
     */
    @Test
    void executeWritesPassMetricsWhenNoDegradationCorrelation() {
        // Given: provider returns ratio=0.10 (< 0.25 WARN threshold)
        CorrelationCheck check = new CorrelationCheck(
                (srcSysNm, date) -> Optional.of(
                        new CorrelationCheck.CorrelationStats(1, 10, 0.10))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=gamma/dataset=archive_log");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric correlation_ratio=0.10
        Optional<MetricNumeric> correlationRatio = findNumericMetric(metrics,
                CorrelationCheck.METRIC_CORRELATION_RATIO);
        assertTrue(correlationRatio.isPresent(),
                "Expected MetricNumeric for correlation_ratio in PASS case");
        assertEquals(0.10, correlationRatio.get().getMetricValue(), 0.001,
                "correlation_ratio must be 0.10");

        // And: MetricDetail status=PASS
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for PASS case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when correlation_ratio < 0.25");
    }

    // -------------------------------------------------------------------------
    // AC2: no src_sys_nm= segment → graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P0] AC2 — dataset_name without src_sys_nm= segment → MetricDetail status=PASS,
     * reason=no_source_system_segment.
     *
     * <p>Given a dataset whose dataset_name does not contain a src_sys_nm= path segment
     * When the CorrelationCheck executes
     * Then it returns a MetricDetail with status=PASS and reason=no_source_system_segment
     * (graceful skip, not an error).
     */
    @Test
    void executeReturnsPassWhenNoSourceSystemSegmentInDatasetName() {
        // Given: dataset_name with no src_sys_nm= segment; provider should NOT be called
        CorrelationCheck check = new CorrelationCheck(
                (srcSysNm, date) -> Optional.of(
                        new CorrelationCheck.CorrelationStats(3, 5, 0.60))
        );
        DatasetContext ctx = context("lob=retail/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_source_system_segment
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when src_sys_nm not in dataset_name");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with correlation_status detailType");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no src_sys_nm segment found");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"no_source_system_segment\""),
                "Expected no_source_system_segment reason");

        // And: no MetricNumeric (no stats to report)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when src_sys_nm not found");
    }

    // -------------------------------------------------------------------------
    // AC1 edge: provider returns empty (no current-day data) → graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P1] AC1 — provider returns empty (totalCount=0, no runs for this source system today) →
     * MetricDetail status=PASS, reason=no_current_day_data.
     *
     * <p>Given the source system has no dq_run entries for the current partition_date
     * When the check executes
     * Then it returns MetricDetail with status=PASS and reason=no_current_day_data.
     */
    @Test
    void executeReturnsPassWhenNoCurrentDayData() {
        // Given: provider returns empty (no current-day data for this source system)
        CorrelationCheck check = new CorrelationCheck(
                (srcSysNm, date) -> Optional.empty()
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_current_day_data
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when no current-day data");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail when no current-day data available");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no current-day data");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"no_current_day_data\""),
                "Expected no_current_day_data reason");

        // And: no MetricNumeric
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when no current-day data found");
    }

    // -------------------------------------------------------------------------
    // AC6: null context → NOT_RUN, no exception
    // -------------------------------------------------------------------------

    /**
     * [P0] AC6 — null context → MetricDetail with status=NOT_RUN, no exception propagation.
     *
     * <p>Given a null context is passed to execute()
     * When the check executes
     * Then it returns a MetricDetail with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        CorrelationCheck check = new CorrelationCheck(
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
     * [P1] Exception path — CorrelationStatsProvider throws RuntimeException →
     * errorDetail returned, no exception propagation.
     *
     * <p>Given a CorrelationStatsProvider that throws a RuntimeException (simulated JDBC failure)
     * When the check executes
     * Then it returns at least one MetricDetail error detail and does NOT propagate the exception.
     */
    @Test
    void executeHandlesExceptionGracefully() {
        // Given: provider throws RuntimeException
        CorrelationCheck check = new CorrelationCheck(
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
    // AC7: contract test — getCheckType()
    // -------------------------------------------------------------------------

    /**
     * [P1] AC7 — getCheckType() returns "CORRELATION".
     *
     * <p>CorrelationCheck implements DqCheck; the canonical check_type constant must be
     * "CORRELATION". Zero changes to serve/API/dashboard — the check type is transparent to
     * all layers except the Spark job.
     */
    @Test
    void getCheckTypeReturnsCorrelation() {
        // Given: any valid check instance
        CorrelationCheck check = new CorrelationCheck(
                (srcSysNm, date) -> Optional.empty()
        );

        // When / Then
        assertEquals("CORRELATION", check.getCheckType(),
                "getCheckType() must return the canonical CORRELATION string");
        assertEquals(CorrelationCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC1 edge: FAIL detail payload contains all required context fields
    // -------------------------------------------------------------------------

    /**
     * [P1] AC1 — FAIL detail payload contains src_sys_nm, correlated_dataset_count,
     * total_dataset_count, correlation_ratio, history_days, threshold_fail, threshold_warn —
     * all required for downstream alerting and observability.
     *
     * <p>Given the correlation check produces a FAIL result (ratio=0.6, correlated=3, total=5)
     * When the MetricDetail is inspected
     * Then its detailValue JSON contains all required context fields per the story spec.
     */
    @Test
    void executeFailDetailContainsAllRequiredContextFields() {
        // Given: provider returns ratio=0.6, correlated=3, total=5 (FAIL case)
        CorrelationCheck check = new CorrelationCheck(
                (srcSysNm, date) -> Optional.of(
                        new CorrelationCheck.CorrelationStats(3, 5, 0.60))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: FAIL detail contains all required fields
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(), "Expected MetricDetail for FAIL case");

        String payload = detail.get().getDetailValue();
        assertTrue(payload.contains("\"status\":\"FAIL\""),
                "Expected FAIL status in detail payload");
        assertTrue(payload.contains("\"src_sys_nm\""),
                "Expected src_sys_nm field in FAIL detail payload");
        assertTrue(payload.contains("\"correlated_dataset_count\""),
                "Expected correlated_dataset_count field in FAIL detail payload");
        assertTrue(payload.contains("\"total_dataset_count\""),
                "Expected total_dataset_count field in FAIL detail payload");
        assertTrue(payload.contains("\"correlation_ratio\""),
                "Expected correlation_ratio field in FAIL detail payload");
        assertTrue(payload.contains("\"history_days\""),
                "Expected history_days field in FAIL detail payload");
        assertTrue(payload.contains("\"threshold_fail\""),
                "Expected threshold_fail field in FAIL detail payload");
        assertTrue(payload.contains("\"threshold_warn\""),
                "Expected threshold_warn field in FAIL detail payload");
    }
}
