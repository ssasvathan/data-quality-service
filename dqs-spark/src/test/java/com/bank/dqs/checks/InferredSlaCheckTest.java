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
 * ATDD RED PHASE — tests will not compile until InferredSlaCheck.java is implemented.
 *
 * <p>InferredSlaCheck infers an SLA window from 30 days of historical freshness data for datasets
 * that have no explicit SLA configured in {@code dataset_enrichment}. It queries
 * {@code v_dq_metric_numeric_active} (joined to {@code v_dq_run_active}) for {@code FRESHNESS} /
 * {@code hours_since_update} values, computes {@code inferredSlaHours = mean + 2.0 * stddev},
 * and emits {@code MetricNumeric} for {@code inferred_sla_hours} and
 * {@code deviation_from_inferred}, plus a {@code MetricDetail} with status FAIL/WARN/PASS.
 *
 * <p>Status logic:
 * <ul>
 *   <li>FAIL if {@code deviation_from_inferred > 0} (current delivery is later than inferred window)</li>
 *   <li>WARN if {@code deviation_from_inferred > -inferredSlaHours * 0.20} (within 20% of inferred window)</li>
 *   <li>PASS otherwise</li>
 * </ul>
 *
 * <p>Graceful skips:
 * <ul>
 *   <li>Explicit SLA configured → return empty list (SlaCountdownCheck handles this dataset)</li>
 *   <li>Fewer than 7 data points → MetricDetail with status=PASS, reason=insufficient_history</li>
 *   <li>Null context → MetricDetail with status=NOT_RUN, no exception</li>
 * </ul>
 *
 * <p>No SparkSession required: this check does NOT call {@code context.getDf()}.
 * It is purely JDBC-based. All tests use plain JUnit 5 with a mock
 * {@code SlaHistoryProvider} injected via lambda.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/InferredSlaCheck.java} is created.
 */
class InferredSlaCheckTest {

    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    // History base (14 historical values): mean=10.0, pop-stddev=2.0 → inferredSlaHours=14.0
    // Last value = current delivery. Inference computed from first 14 values only (excludes current).
    // Verification: sum=140, sum(v-10)^2=56 → pop-stddev=sqrt(56/14)=2.0 ✓

    // Last value (current delivery) = 16.0 → deviation=+2.0 (FAIL)
    private static final List<Double> HISTORY_FAIL = List.of(
            6.0, 8.0, 8.0, 8.0, 8.0, 10.0, 10.0, 10.0, 12.0, 12.0,
            12.0, 12.0, 12.0, 12.0, 16.0
    );

    // Last value = 12.0 → deviation=-2.0 (within 20% of 14.0 = within 2.8 → WARN since -2.0 > -2.8)
    private static final List<Double> HISTORY_WARN = List.of(
            6.0, 8.0, 8.0, 8.0, 8.0, 10.0, 10.0, 10.0, 12.0, 12.0,
            12.0, 12.0, 12.0, 12.0, 12.0
    );

    // Last value = 5.0 → deviation=-9.0 (well within window → PASS, -9.0 < -2.8)
    private static final List<Double> HISTORY_PASS = List.of(
            6.0, 8.0, 8.0, 8.0, 8.0, 10.0, 10.0, 10.0, 12.0, 12.0,
            12.0, 12.0, 12.0, 12.0, 5.0
    );

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
     * {@link InferredSlaCheck#DETAIL_TYPE_STATUS}.
     */
    private Optional<MetricDetail> findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> InferredSlaCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC3: deviation_from_inferred > 0 → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — 15 history values with mean=10.0, stddev=2.0 → inferredSlaHours=14.0;
     * current=16.0 (last in list) → deviation=+2.0 > 0 → status=FAIL.
     *
     * <p>Given a dataset with 30+ days of freshness history and no explicit SLA configured
     * When the current delivery (16.0 hours) exceeds the inferred SLA window (14.0 hours)
     * Then MetricNumeric inferred_sla_hours=14.0, MetricNumeric deviation_from_inferred=2.0,
     * And MetricDetail status=FAIL.
     */
    @Test
    void executeWritesFailMetricsWhenCurrentDeliveryLaterThanInferredWindow() {
        // Given: 15 history values, mean=10.0, stddev=2.0, inferredSlaHours=14.0; current=16.0
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.of(new InferredSlaCheck.SlaHistory(HISTORY_FAIL, false))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric inferred_sla_hours=14.0 (mean=10 + 2*stddev=2 → 14)
        Optional<MetricNumeric> inferredSla = findNumericMetric(metrics,
                InferredSlaCheck.METRIC_INFERRED_SLA_HOURS);
        assertTrue(inferredSla.isPresent(),
                "Expected MetricNumeric for inferred_sla_hours");
        assertEquals(InferredSlaCheck.CHECK_TYPE, inferredSla.get().getCheckType(),
                "check_type must be INFERRED_SLA");
        assertEquals(14.0, inferredSla.get().getMetricValue(), 0.01,
                "inferred_sla_hours must be 14.0 (mean=10 + 2*stddev=2)");

        // And: MetricNumeric deviation_from_inferred=2.0 (16.0 - 14.0)
        Optional<MetricNumeric> deviation = findNumericMetric(metrics,
                InferredSlaCheck.METRIC_DEVIATION_FROM_INFERRED);
        assertTrue(deviation.isPresent(),
                "Expected MetricNumeric for deviation_from_inferred");
        assertEquals(2.0, deviation.get().getMetricValue(), 0.01,
                "deviation_from_inferred must be 2.0 (16.0 current - 14.0 inferred)");

        // And: MetricDetail status=FAIL
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with inferred_sla_status detailType");
        assertEquals(InferredSlaCheck.CHECK_TYPE, detail.get().getCheckType(),
                "check_type must be INFERRED_SLA for detail metric");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when current delivery > inferred SLA window");
    }

    // -------------------------------------------------------------------------
    // AC3: approaching breach (within 20% of inferredSlaHours) → WARN
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — current delivery just below inferredSlaHours but within 20% → status=WARN.
     *
     * <p>Given history with mean=10.0, stddev=2.0 → inferredSlaHours=14.0
     * And current delivery = 12.0 hours (deviation=-2.0, within 20% of 14.0 = within 2.8)
     * When the InferredSlaCheck executes (-2.0 > -2.8 → approaching breach)
     * Then MetricDetail status=WARN.
     */
    @Test
    void executeWritesWarnMetricsWhenApproachingInferredWindow() {
        // Given: history with inferredSlaHours=14.0; current=12.0 → deviation=-2.0
        // -2.0 > -14.0 * 0.20 = -2.8 → WARN (approaching breach)
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.of(new InferredSlaCheck.SlaHistory(HISTORY_WARN, false))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=transactions");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric inferred_sla_hours=14.0
        Optional<MetricNumeric> inferredSla = findNumericMetric(metrics,
                InferredSlaCheck.METRIC_INFERRED_SLA_HOURS);
        assertTrue(inferredSla.isPresent(),
                "Expected MetricNumeric for inferred_sla_hours in WARN case");
        assertEquals(14.0, inferredSla.get().getMetricValue(), 0.01,
                "inferred_sla_hours must be 14.0");

        // And: MetricDetail status=WARN
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for WARN case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"WARN\""),
                "Expected WARN status when current delivery within 20% of inferred window");
    }

    // -------------------------------------------------------------------------
    // AC3: well within inferred window → PASS
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — current delivery well within inferred window → status=PASS.
     *
     * <p>Given history with mean=10.0, stddev=2.0 → inferredSlaHours=14.0
     * And current delivery = 5.0 hours (deviation=-9.0, outside 20% zone)
     * When the InferredSlaCheck executes (-9.0 < -2.8 → well within window)
     * Then MetricDetail status=PASS.
     */
    @Test
    void executeWritesPassMetricsWhenDeliveryWellWithinInferredWindow() {
        // Given: history with inferredSlaHours=14.0; current=5.0 → deviation=-9.0
        // -9.0 < -14.0 * 0.20 = -2.8 → PASS (well within window)
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.of(new InferredSlaCheck.SlaHistory(HISTORY_PASS, false))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=reports");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric inferred_sla_hours=14.0
        Optional<MetricNumeric> inferredSla = findNumericMetric(metrics,
                InferredSlaCheck.METRIC_INFERRED_SLA_HOURS);
        assertTrue(inferredSla.isPresent(),
                "Expected MetricNumeric for inferred_sla_hours in PASS case");

        // And: MetricDetail status=PASS
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for PASS case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when delivery well within inferred window");
    }

    // -------------------------------------------------------------------------
    // AC4: explicit SLA configured → return empty list
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — dataset has explicit SLA in dataset_enrichment → empty list returned.
     *
     * <p>Given a dataset with an explicit SLA configured (hasExplicitSla=true)
     * When the InferredSlaCheck executes
     * Then it returns an empty list (SlaCountdownCheck handles this dataset)
     * And no metrics are emitted.
     */
    @Test
    void executeReturnsEmptyWhenExplicitSlaIsConfigured() {
        // Given: provider returns hasExplicitSla=true (explicit SLA takes precedence)
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.of(new InferredSlaCheck.SlaHistory(HISTORY_FAIL, true))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: empty list — check not applicable, SlaCountdownCheck owns this dataset
        assertTrue(metrics.isEmpty(),
                "Expected empty list when explicit SLA is configured — SlaCountdownCheck handles this dataset");
    }

    // -------------------------------------------------------------------------
    // AC5: fewer than 7 data points → graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P0] AC5 — fewer than 7 data points of freshness history → MetricDetail with
     * status=PASS, reason=insufficient_history.
     *
     * <p>Given a dataset with only 4 days of freshness history (insufficient baseline)
     * When the InferredSlaCheck executes
     * Then it returns MetricDetail with status=PASS and reason=insufficient_history
     * (minimum 7 days required to infer a pattern).
     */
    @Test
    void executeReturnsPassWhenInsufficientHistory() {
        // Given: only 4 data points (< 7 minimum required)
        List<Double> shortHistory = List.of(10.0, 11.0, 9.5, 10.5);
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.of(new InferredSlaCheck.SlaHistory(shortHistory, false))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=new_dataset");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=insufficient_history
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when history is insufficient");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with inferred_sla_status detailType");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when history has fewer than 7 data points");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"insufficient_history\""),
                "Expected insufficient_history reason");

        // And: no MetricNumeric (cannot infer SLA without sufficient history)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when history is insufficient");
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
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.empty()
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
     * [P1] Exception path — SlaHistoryProvider throws RuntimeException →
     * errorDetail returned, no exception propagation.
     *
     * <p>Given a SlaHistoryProvider that throws a RuntimeException (simulated JDBC failure)
     * When the check executes
     * Then it returns at least one MetricDetail error detail and does NOT propagate the exception.
     */
    @Test
    void executeHandlesExceptionGracefully() {
        // Given: provider throws RuntimeException
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> { throw new RuntimeException("simulated JDBC failure"); }
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
     * [P1] AC7 — getCheckType() returns "INFERRED_SLA".
     *
     * <p>InferredSlaCheck implements DqCheck; the canonical check_type constant must be
     * "INFERRED_SLA". Zero changes to serve/API/dashboard — the check type is transparent to
     * all layers except the Spark job.
     */
    @Test
    void getCheckTypeReturnsInferredSla() {
        // Given: any valid check instance
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.empty()
        );

        // When / Then
        assertEquals("INFERRED_SLA", check.getCheckType(),
                "getCheckType() must return the canonical INFERRED_SLA string");
        assertEquals(InferredSlaCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC3 edge: PASS detail contains required statistics fields
    // -------------------------------------------------------------------------

    /**
     * [P1] AC3 — PASS detail payload contains current_hours, inferred_sla_hours,
     * deviation_from_inferred, mean_hours, stddev_hours, data_points — all required for
     * observability and downstream analysis.
     *
     * <p>Given the inferred SLA check produces a PASS result
     * When the MetricDetail is inspected
     * Then its detailValue JSON contains all required statistics fields per the story spec.
     */
    @Test
    void executePassDetailContainsRequiredStatisticsFields() {
        // Given: history with PASS outcome (current=5.0, inferredSlaHours=14.0)
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.of(new InferredSlaCheck.SlaHistory(HISTORY_PASS, false))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=reports");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: PASS detail contains all required statistics fields
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(), "Expected MetricDetail for PASS case");

        String payload = detail.get().getDetailValue();
        assertTrue(payload.contains("\"status\":\"PASS\""),
                "Expected PASS status in detail payload");
        assertTrue(payload.contains("\"current_hours\""),
                "Expected current_hours field in detail payload");
        assertTrue(payload.contains("\"inferred_sla_hours\""),
                "Expected inferred_sla_hours field in detail payload");
        assertTrue(payload.contains("\"deviation_from_inferred\""),
                "Expected deviation_from_inferred field in detail payload");
        assertTrue(payload.contains("\"mean_hours\""),
                "Expected mean_hours field in detail payload");
        assertTrue(payload.contains("\"stddev_hours\""),
                "Expected stddev_hours field in detail payload");
        assertTrue(payload.contains("\"data_points\""),
                "Expected data_points field in detail payload");
    }

    // -------------------------------------------------------------------------
    // AC3 edge: MetricNumeric values are mathematically correct
    // -------------------------------------------------------------------------

    /**
     * [P1] AC3 — MetricNumeric for inferred_sla_hours and deviation_from_inferred emit
     * mathematically correct values (mean=10.0, stddev=2.0 → inferredSlaHours=14.0;
     * current=16.0 → deviation=+2.0).
     *
     * <p>Given 15 history values with computable mean=10.0 and stddev=2.0
     * When the InferredSlaCheck executes
     * Then MetricNumeric inferred_sla_hours=14.0 (within tolerance)
     * And MetricNumeric deviation_from_inferred=2.0 (within tolerance).
     */
    @Test
    void executeEmitsCorrectMetricNumericsForInferredWindow() {
        // Given: HISTORY_FAIL — mean≈10.0, stddev≈2.0, current=16.0
        InferredSlaCheck check = new InferredSlaCheck(
                ctx -> Optional.of(new InferredSlaCheck.SlaHistory(HISTORY_FAIL, false))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: inferred_sla_hours ≈ 14.0 (mean=10 + 2*stddev=2)
        Optional<MetricNumeric> inferredSla = findNumericMetric(metrics,
                InferredSlaCheck.METRIC_INFERRED_SLA_HOURS);
        assertTrue(inferredSla.isPresent(),
                "Expected MetricNumeric for inferred_sla_hours");
        // Tolerance of 0.5 to allow for population stddev variance across 15 values
        assertEquals(14.0, inferredSla.get().getMetricValue(), 0.5,
                "inferred_sla_hours must be approximately 14.0 (mean + 2*stddev)");

        // And: deviation_from_inferred ≈ +2.0 (16.0 - 14.0)
        Optional<MetricNumeric> deviation = findNumericMetric(metrics,
                InferredSlaCheck.METRIC_DEVIATION_FROM_INFERRED);
        assertTrue(deviation.isPresent(),
                "Expected MetricNumeric for deviation_from_inferred");
        // Positive deviation confirms FAIL status
        assertTrue(deviation.get().getMetricValue() > 0.0,
                "deviation_from_inferred must be positive (current delivery > inferred window)");

        // And: MetricDetail confirms FAIL for positive deviation
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for numeric verification case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status for positive deviation_from_inferred");
    }
}
