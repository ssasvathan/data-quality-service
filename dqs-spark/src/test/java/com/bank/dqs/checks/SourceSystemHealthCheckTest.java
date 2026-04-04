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
 * ATDD RED PHASE — tests will not compile until SourceSystemHealthCheck.java is implemented.
 *
 * <p>SourceSystemHealthCheck aggregates DQS history across datasets sharing the same
 * {@code src_sys_nm} path segment (from {@code v_dq_run_active}, last 7 days by default).
 * It emits {@code MetricNumeric} for {@code aggregate_score} and {@code dataset_count},
 * plus a {@code MetricDetail} with status FAIL/WARN/PASS based on thresholds.
 *
 * <p>No SparkSession required: this check does NOT call {@code context.getDf()}.
 * It is purely JDBC-based. All tests use plain JUnit 5 with a mock
 * {@code SourceSystemStatsProvider} injected via lambda.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/SourceSystemHealthCheck.java} is created.
 */
class SourceSystemHealthCheckTest {

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
     * {@link SourceSystemHealthCheck#DETAIL_TYPE_STATUS}.
     */
    private Optional<MetricDetail> findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> SourceSystemHealthCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC4: aggregate_score < FAIL_THRESHOLD (60.0) → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — provider returns stats with aggregateScore=45.0, datasetCount=3 →
     * MetricNumeric aggregate_score=45.0, MetricNumeric dataset_count=3, MetricDetail status=FAIL.
     *
     * <p>Given multiple datasets from the same source system (src_sys_nm=alpha)
     * When the SourceSystemHealthCheck executes and average DQS score is 45.0 (below 60.0 FAIL threshold)
     * Then it writes MetricNumeric for aggregate_score=45.0 and dataset_count=3.0
     * And it writes MetricDetail with status=FAIL.
     */
    @Test
    void executeWritesFailMetricsWhenAggregateScoreBelowFailThreshold() {
        // Given: provider returns score=45.0 (< 60.0 FAIL threshold), count=3
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> Optional.of(
                        new SourceSystemHealthCheck.SourceSystemStats(45.0, 3))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric aggregate_score=45.0
        Optional<MetricNumeric> aggregateScore = findNumericMetric(metrics,
                SourceSystemHealthCheck.METRIC_AGGREGATE_SCORE);
        assertTrue(aggregateScore.isPresent(),
                "Expected MetricNumeric for aggregate_score");
        assertEquals(SourceSystemHealthCheck.CHECK_TYPE, aggregateScore.get().getCheckType(),
                "check_type must be SOURCE_SYSTEM_HEALTH");
        assertEquals(45.0, aggregateScore.get().getMetricValue(), 0.001,
                "aggregate_score must be 45.0");

        // And: MetricNumeric dataset_count=3.0
        Optional<MetricNumeric> datasetCount = findNumericMetric(metrics,
                SourceSystemHealthCheck.METRIC_DATASET_COUNT);
        assertTrue(datasetCount.isPresent(),
                "Expected MetricNumeric for dataset_count");
        assertEquals(3.0, datasetCount.get().getMetricValue(), 0.001,
                "dataset_count must be 3.0");

        // And: MetricDetail status=FAIL
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with source_system_health_status detailType");
        assertEquals(SourceSystemHealthCheck.CHECK_TYPE, detail.get().getCheckType(),
                "check_type must be SOURCE_SYSTEM_HEALTH for detail");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when aggregate_score < 60.0");
        assertTrue(detail.get().getDetailValue().contains("\"aggregate_score\":45.0"),
                "Expected aggregate_score in FAIL detail payload");
    }

    // -------------------------------------------------------------------------
    // AC4: 60.0 <= aggregate_score < WARN_THRESHOLD (75.0) → WARN
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — provider returns aggregateScore=68.0 (between 60.0 and 75.0) → status=WARN.
     *
     * <p>Given the aggregate DQS score for the source system is 68.0
     * When the check executes
     * Then MetricDetail status=WARN.
     */
    @Test
    void executeWritesWarnMetricsWhenAggregateScoreBetweenThresholds() {
        // Given: score=68.0 (>= 60.0 FAIL threshold, < 75.0 WARN threshold)
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> Optional.of(
                        new SourceSystemHealthCheck.SourceSystemStats(68.0, 5))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: aggregate_score=68.0
        Optional<MetricNumeric> aggregateScore = findNumericMetric(metrics,
                SourceSystemHealthCheck.METRIC_AGGREGATE_SCORE);
        assertTrue(aggregateScore.isPresent(),
                "Expected MetricNumeric for aggregate_score");
        assertEquals(68.0, aggregateScore.get().getMetricValue(), 0.001,
                "aggregate_score must be 68.0");

        // And: status=WARN
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for WARN case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"WARN\""),
                "Expected WARN status when 60.0 <= aggregate_score < 75.0");
    }

    // -------------------------------------------------------------------------
    // AC4: aggregate_score >= WARN_THRESHOLD (75.0) → PASS
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — provider returns aggregateScore=88.0 (>= 75.0 WARN threshold) → status=PASS.
     *
     * <p>Given the aggregate DQS score for the source system is 88.0
     * When the check executes
     * Then MetricDetail status=PASS.
     */
    @Test
    void executeWritesPassMetricsWhenAggregateScoreAboveWarnThreshold() {
        // Given: score=88.0 (>= 75.0 WARN threshold)
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> Optional.of(
                        new SourceSystemHealthCheck.SourceSystemStats(88.0, 7))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: aggregate_score=88.0
        Optional<MetricNumeric> aggregateScore = findNumericMetric(metrics,
                SourceSystemHealthCheck.METRIC_AGGREGATE_SCORE);
        assertTrue(aggregateScore.isPresent(),
                "Expected MetricNumeric for aggregate_score");
        assertEquals(88.0, aggregateScore.get().getMetricValue(), 0.001,
                "aggregate_score must be 88.0");

        // And: status=PASS
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for PASS case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when aggregate_score >= 75.0");
    }

    // -------------------------------------------------------------------------
    // AC5: no src_sys_nm segment in dataset_name → graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P0] AC5 — dataset_name without src_sys_nm= segment → MetricDetail status=PASS,
     * reason=no_source_system_segment.
     *
     * <p>Given a dataset whose dataset_name does not contain a src_sys_nm= path segment
     * When the SourceSystemHealthCheck executes
     * Then it returns a MetricDetail with status=PASS and reason=no_source_system_segment
     * (graceful skip, not an error).
     */
    @Test
    void executeReturnsPassWhenNoSourceSystemSegmentInDatasetName() {
        // Given: dataset_name with no src_sys_nm= segment
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> Optional.of(
                        new SourceSystemHealthCheck.SourceSystemStats(50.0, 2))
        );
        DatasetContext ctx = context("lob=retail/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_source_system_segment
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when src_sys_nm not in dataset_name");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with source_system_health_status detailType");
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
    // AC4: provider returns empty (no history) → graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P1] AC4 — provider returns empty (no history available) → MetricDetail status=PASS,
     * reason=no_history_available.
     *
     * <p>Given the source system has no dq_run history in the last 7 days
     * When the check executes
     * Then it returns MetricDetail with status=PASS and reason=no_history_available.
     */
    @Test
    void executeReturnsPassWhenNoHistoryAvailable() {
        // Given: provider returns empty (no historical data for this source system)
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> Optional.empty()
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_history_available
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when no history available");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail when no history available");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no history available");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"no_history_available\""),
                "Expected no_history_available reason");

        // And: no MetricNumeric
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when no history found");
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
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
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
     * [P1] Exception path — SourceSystemStatsProvider throws RuntimeException →
     * errorDetail returned, no exception propagation.
     *
     * <p>Given a SourceSystemStatsProvider that throws a RuntimeException (simulated JDBC failure)
     * When the check executes
     * Then it returns at least one MetricDetail error detail and does NOT propagate the exception.
     */
    @Test
    void executeHandlesExceptionGracefully() {
        // Given: provider throws RuntimeException
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
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
     * [P1] AC7 — getCheckType() returns "SOURCE_SYSTEM_HEALTH".
     *
     * <p>SourceSystemHealthCheck implements DqCheck; the canonical check_type constant must be
     * "SOURCE_SYSTEM_HEALTH". Zero changes to serve/API/dashboard — the check type is
     * transparent to all layers except Spark job.
     */
    @Test
    void getCheckTypeReturnsSourceSystemHealth() {
        // Given: any valid check instance
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> Optional.empty()
        );

        // When / Then
        assertEquals("SOURCE_SYSTEM_HEALTH", check.getCheckType(),
                "getCheckType() must return the canonical SOURCE_SYSTEM_HEALTH string");
        assertEquals(SourceSystemHealthCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC4 edge: src_sys_nm extracted correctly from complex path
    // -------------------------------------------------------------------------

    /**
     * [P1] AC4 — src_sys_nm correctly extracted from a multi-segment dataset_name path.
     *
     * <p>Given a dataset_name of "lob=retail/src_sys_nm=alpha/dataset=sales_daily"
     * When the check executes
     * Then it passes srcSysNm="alpha" to the stats provider (not the full path).
     */
    @Test
    void executeExtractsCorrectSrcSysNmFromDatasetName() {
        // Given: dataset_name with lob/src_sys_nm/dataset structure; capture the srcSysNm
        String[] capturedSrcSysNm = new String[1];
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> {
                    capturedSrcSysNm[0] = srcSysNm;
                    return Optional.of(new SourceSystemHealthCheck.SourceSystemStats(88.0, 4));
                }
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: provider received srcSysNm="alpha" (not the full path or lob= prefix)
        assertEquals("alpha", capturedSrcSysNm[0],
                "Expected srcSysNm='alpha' extracted from 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'");

        // And: metrics written (PASS — score=88.0 >= 75.0)
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail after successful src_sys_nm extraction");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status for extracted source system with score=88.0");
    }

    // -------------------------------------------------------------------------
    // AC4 edge: detail payload contains required context fields
    // -------------------------------------------------------------------------

    /**
     * [P1] AC4 — FAIL detail payload contains src_sys_nm, aggregate_score, dataset_count,
     * history_days, threshold_fail, threshold_warn — all required for downstream alerting.
     *
     * <p>Given the source system health check produces a FAIL result
     * When the MetricDetail is inspected
     * Then its detailValue JSON contains all required context fields per the story spec.
     */
    @Test
    void executeFailDetailContainsAllRequiredContextFields() {
        // Given: provider returns score=45.0, count=3 (FAIL case)
        SourceSystemHealthCheck check = new SourceSystemHealthCheck(
                (srcSysNm, date) -> Optional.of(
                        new SourceSystemHealthCheck.SourceSystemStats(45.0, 3))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: detail payload contains all required fields
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(), "Expected MetricDetail for FAIL case");

        String payload = detail.get().getDetailValue();
        assertTrue(payload.contains("\"src_sys_nm\""),
                "Expected src_sys_nm field in FAIL detail payload");
        assertTrue(payload.contains("\"aggregate_score\""),
                "Expected aggregate_score field in FAIL detail payload");
        assertTrue(payload.contains("\"dataset_count\""),
                "Expected dataset_count field in FAIL detail payload");
        assertTrue(payload.contains("\"history_days\""),
                "Expected history_days field in FAIL detail payload");
        assertTrue(payload.contains("\"threshold_fail\""),
                "Expected threshold_fail field in FAIL detail payload");
        assertTrue(payload.contains("\"threshold_warn\""),
                "Expected threshold_warn field in FAIL detail payload");
        assertTrue(payload.contains("\"status\":\"FAIL\""),
                "Expected FAIL status in detail payload");
    }
}
