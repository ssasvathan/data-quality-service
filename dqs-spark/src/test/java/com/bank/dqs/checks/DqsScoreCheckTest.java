package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD failing tests for DqsScoreCheck (TDD RED PHASE).
 *
 * <p>ALL tests in this file are annotated with {@code @Disabled} because
 * {@code DqsScoreCheck}, {@code ScoreInputProvider}, and {@code WeightProvider}
 * do not exist yet. Once Story 2-9 is implemented, remove {@code @Disabled}
 * from each test and verify all pass (TDD green phase).
 *
 * <p>No SparkSession is required — DqsScoreCheck processes in-memory DqMetric
 * objects only, not Spark DataFrames.
 *
 * <p>Test naming convention: {@code <action><expectation>} per project rules.
 */
class DqsScoreCheckTest {

    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    private DatasetContext context;

    @BeforeEach
    void setUp() {
        context = new DatasetContext(
                "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
                "RETAIL",
                PARTITION_DATE,
                "/prod/data",
                null,
                DatasetContext.FORMAT_PARQUET
        );
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Build a MetricDetail that simulates the status detail emitted by a prior check.
     * This is the input that DqsScoreCheck reads to derive per-check scores.
     */
    private MetricDetail checkStatusDetail(String checkType, String detailType, String status) {
        String json = "{\"status\":\"" + status + "\",\"reason\":\"test_fixture\"}";
        return new MetricDetail(checkType, detailType, json);
    }

    /** Build a full set of PASS status details for all four Tier 1 checks. */
    private List<DqMetric> allPassMetrics() {
        List<DqMetric> metrics = new ArrayList<>();
        metrics.add(checkStatusDetail("FRESHNESS", "freshness_status", "PASS"));
        metrics.add(checkStatusDetail("VOLUME",    "volume_status",    "PASS"));
        metrics.add(checkStatusDetail("SCHEMA",    "schema_status",    "PASS"));
        metrics.add(checkStatusDetail("OPS",       "ops_status",       "PASS"));
        return metrics;
    }

    private Optional<MetricNumeric> findNumeric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst();
    }

    private Optional<MetricDetail> findDetail(List<DqMetric> metrics, String detailType) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> detailType.equals(d.getDetailType()))
                .findFirst();
    }

    // ---------------------------------------------------------------------------
    // Constructor null-guard contracts
    // ---------------------------------------------------------------------------

    @Test
    void constructorThrowsOnNullScoreInputProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> new DqsScoreCheck(null),
                "Single-arg constructor must reject null ScoreInputProvider");
    }

    @Test
    void constructorThrowsOnNullScoreInputProviderInTwoArgConstructor() {
        assertThrows(IllegalArgumentException.class,
                () -> new DqsScoreCheck(null, ctx -> java.util.Map.of()),
                "Two-arg constructor must reject null ScoreInputProvider");
    }

    @Test
    void constructorThrowsOnNullWeightProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> new DqsScoreCheck(ctx -> java.util.List.of(), null),
                "Two-arg constructor must reject null WeightProvider");
    }

    // ---------------------------------------------------------------------------
    // AC: getCheckType contract
    // ---------------------------------------------------------------------------

    @Test
    void getCheckTypeReturnsDqsScore() {
        DqsScoreCheck check = new DqsScoreCheck();
        assertEquals("DQS_SCORE", check.getCheckType());
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC2: All four checks PASS → composite = 100
    // ---------------------------------------------------------------------------

    @Test
    void executeComputesCompositeScoreFromAllFourChecks() {
        List<DqMetric> priorMetrics = allPassMetrics();
        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics);

        List<DqMetric> result = check.execute(context);

        // Must emit MetricNumeric with metric_name="composite_score"
        MetricNumeric compositeMetric = findNumeric(result, "composite_score")
                .orElseThrow(() -> new AssertionError("Missing composite_score MetricNumeric"));
        assertEquals("DQS_SCORE", compositeMetric.getCheckType());
        assertEquals(100.0, compositeMetric.getMetricValue(), 0.001,
                "All four PASS checks with default weights must yield composite=100");
    }

    // ---------------------------------------------------------------------------
    // AC2: Mix of PASS/WARN/FAIL statuses → weighted average
    // ---------------------------------------------------------------------------

    @Test
    void executeComputesCompositeScoreWhenSomeChecksFail() {
        // FRESHNESS=PASS(100)*0.30, VOLUME=WARN(50)*0.30, SCHEMA=FAIL(0)*0.20, OPS=PASS(100)*0.20
        // composite = 30 + 15 + 0 + 20 = 65 → WARN
        List<DqMetric> priorMetrics = new ArrayList<>();
        priorMetrics.add(checkStatusDetail("FRESHNESS", "freshness_status", "PASS"));
        priorMetrics.add(checkStatusDetail("VOLUME",    "volume_status",    "WARN"));
        priorMetrics.add(checkStatusDetail("SCHEMA",    "schema_status",    "FAIL"));
        priorMetrics.add(checkStatusDetail("OPS",       "ops_status",       "PASS"));

        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics);
        List<DqMetric> result = check.execute(context);

        MetricNumeric compositeMetric = findNumeric(result, "composite_score")
                .orElseThrow(() -> new AssertionError("Missing composite_score MetricNumeric"));
        assertEquals(65.0, compositeMetric.getMetricValue(), 0.001,
                "Mixed PASS/WARN/FAIL with default weights must yield composite=65");
    }

    // ---------------------------------------------------------------------------
    // AC5: One check NOT_RUN → weight redistributed among remaining three
    // ---------------------------------------------------------------------------

    @Test
    void executeRedistributesWeightForUnavailableCheck() {
        // SCHEMA is NOT_RUN (unavailable)
        // Available: FRESHNESS(100)*0.30, VOLUME(100)*0.30, OPS(100)*0.20 → total_weight=0.80
        // Normalized: FRESHNESS=0.375, VOLUME=0.375, OPS=0.25 → composite=100
        List<DqMetric> priorMetrics = new ArrayList<>();
        priorMetrics.add(checkStatusDetail("FRESHNESS", "freshness_status", "PASS"));
        priorMetrics.add(checkStatusDetail("VOLUME",    "volume_status",    "PASS"));
        priorMetrics.add(checkStatusDetail("SCHEMA",    "schema_status",    "NOT_RUN"));
        priorMetrics.add(checkStatusDetail("OPS",       "ops_status",       "PASS"));

        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics);
        List<DqMetric> result = check.execute(context);

        MetricNumeric compositeMetric = findNumeric(result, "composite_score")
                .orElseThrow(() -> new AssertionError("Missing composite_score MetricNumeric"));
        assertEquals(100.0, compositeMetric.getMetricValue(), 0.001,
                "Three PASS checks with SCHEMA NOT_RUN must still yield 100 after weight redistribution");

        // Breakdown must list SCHEMA in unavailable_checks
        MetricDetail breakdown = findDetail(result, "dqs_score_breakdown")
                .orElseThrow(() -> new AssertionError("Missing dqs_score_breakdown MetricDetail"));
        assertTrue(breakdown.getDetailValue().contains("\"SCHEMA\""),
                "dqs_score_breakdown must list SCHEMA in unavailable_checks");
        assertTrue(breakdown.getDetailValue().contains("partial_checks"),
                "reason must be partial_checks when one check is unavailable");
    }

    // ---------------------------------------------------------------------------
    // AC5: ALL checks NOT_RUN → NOT_RUN with reason all_checks_unavailable
    // ---------------------------------------------------------------------------

    @Test
    void executeReturnsNotRunWhenAllChecksUnavailable() {
        // All four checks report NOT_RUN — no available checks to compute composite
        List<DqMetric> priorMetrics = new ArrayList<>();
        priorMetrics.add(checkStatusDetail("FRESHNESS", "freshness_status", "NOT_RUN"));
        priorMetrics.add(checkStatusDetail("VOLUME",    "volume_status",    "NOT_RUN"));
        priorMetrics.add(checkStatusDetail("SCHEMA",    "schema_status",    "NOT_RUN"));
        priorMetrics.add(checkStatusDetail("OPS",       "ops_status",       "NOT_RUN"));

        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics);
        List<DqMetric> result = check.execute(context);

        MetricDetail breakdown = findDetail(result, "dqs_score_breakdown")
                .orElseThrow(() -> new AssertionError("Missing dqs_score_breakdown MetricDetail"));
        assertTrue(breakdown.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "All checks unavailable must yield status=NOT_RUN");
        assertTrue(breakdown.getDetailValue().contains("all_checks_unavailable"),
                "Reason must be all_checks_unavailable");
        assertTrue(breakdown.getDetailValue().contains("\"composite_score\":0.0"),
                "Composite score must be 0.0 when all checks unavailable");
    }

    // ---------------------------------------------------------------------------
    // AC1: Default weights used when no custom weights configured
    // ---------------------------------------------------------------------------

    @Test
    void executeUsesDefaultWeightsWhenNoCustomWeightsConfigured() {
        // FRESHNESS=PASS(100), VOLUME=FAIL(0), SCHEMA=PASS(100), OPS=PASS(100)
        // Default: FRESHNESS=0.30, VOLUME=0.30, SCHEMA=0.20, OPS=0.20
        // composite = 100*0.30 + 0*0.30 + 100*0.20 + 100*0.20 = 70
        List<DqMetric> priorMetrics = new ArrayList<>();
        priorMetrics.add(checkStatusDetail("FRESHNESS", "freshness_status", "PASS"));
        priorMetrics.add(checkStatusDetail("VOLUME",    "volume_status",    "FAIL"));
        priorMetrics.add(checkStatusDetail("SCHEMA",    "schema_status",    "PASS"));
        priorMetrics.add(checkStatusDetail("OPS",       "ops_status",       "PASS"));

        // Single-arg constructor exercises DefaultWeightProvider
        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics);

        List<DqMetric> result = check.execute(context);

        MetricNumeric compositeMetric = findNumeric(result, "composite_score")
                .orElseThrow(() -> new AssertionError("Missing composite_score MetricNumeric"));
        assertEquals(70.0, compositeMetric.getMetricValue(), 0.001,
                "Default weights with VOLUME=FAIL must yield composite=70");
    }

    // ---------------------------------------------------------------------------
    // AC4: Custom weights injected via WeightProvider
    // ---------------------------------------------------------------------------

    @Test
    void executeUsesCustomWeightsWhenProvided() {
        // Custom weights: FRESHNESS=0.10, VOLUME=0.50, SCHEMA=0.30, OPS=0.10
        // All PASS: composite = 100*0.10 + 100*0.50 + 100*0.30 + 100*0.10 = 100
        Map<String, Double> customWeights = Map.of(
                "FRESHNESS", 0.10,
                "VOLUME",    0.50,
                "SCHEMA",    0.30,
                "OPS",       0.10
        );
        List<DqMetric> priorMetrics = allPassMetrics();

        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics, ctx -> customWeights);

        List<DqMetric> result = check.execute(context);

        MetricNumeric compositeMetric = findNumeric(result, "composite_score")
                .orElseThrow(() -> new AssertionError("Missing composite_score MetricNumeric"));
        assertEquals(100.0, compositeMetric.getMetricValue(), 0.001,
                "Custom weights with all-PASS checks must yield composite=100");

        // Verify custom VOLUME weight reflected in breakdown
        MetricDetail breakdown = findDetail(result, "dqs_score_breakdown")
                .orElseThrow(() -> new AssertionError("Missing dqs_score_breakdown MetricDetail"));
        assertTrue(breakdown.getDetailValue().contains("0.5") || breakdown.getDetailValue().contains("0.50"),
                "Breakdown must reflect custom VOLUME weight of 0.50");
    }

    // ---------------------------------------------------------------------------
    // Guard: null context → deterministic NOT_RUN
    // ---------------------------------------------------------------------------

    @Test
    void executeReturnsNotRunForNullContext() {
        DqsScoreCheck check = new DqsScoreCheck();

        List<DqMetric> result = check.execute(null);

        assertNotNull(result, "execute(null) must return non-null list");
        assertFalse(result.isEmpty(), "execute(null) must return at least one metric");

        MetricDetail breakdown = findDetail(result, "dqs_score_breakdown")
                .orElseThrow(() -> new AssertionError("Missing dqs_score_breakdown on null context"));
        assertTrue(breakdown.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Null context must yield status=NOT_RUN");
        assertTrue(breakdown.getDetailValue().contains("missing_context"),
                "Null context must include reason=missing_context");
    }

    // ---------------------------------------------------------------------------
    // Guard: internal exception → sanitized error payload (no raw message)
    // ---------------------------------------------------------------------------

    @Test
    void executeSanitizesExecutionErrorPayload() {
        // ScoreInputProvider throws to simulate internal failure
        DqsScoreCheck check = new DqsScoreCheck(ctx -> {
            throw new RuntimeException("raw sensitive details must not be exposed");
        });

        List<DqMetric> result = check.execute(context);

        assertNotNull(result, "execute() must return non-null list even on internal error");
        MetricDetail breakdown = findDetail(result, "dqs_score_breakdown")
                .orElseThrow(() -> new AssertionError("Missing dqs_score_breakdown on execution error"));

        assertTrue(breakdown.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Execution error must yield status=NOT_RUN");
        assertTrue(breakdown.getDetailValue().contains("execution_error"),
                "Execution error must include reason=execution_error");
        assertTrue(breakdown.getDetailValue().contains("\"error_type\":\"RuntimeException\""),
                "Execution error must include sanitized error_type class name");
        assertFalse(breakdown.getDetailValue().contains("raw sensitive details must not be exposed"),
                "Raw exception message must NOT appear in the payload");
    }

    // ---------------------------------------------------------------------------
    // AC3: Score breakdown detail emitted with per-check contributions
    // ---------------------------------------------------------------------------

    @Test
    void executeComputesScoreBreakdownDetailWithPerCheckContributions() {
        // FRESHNESS=PASS(100)*0.30=30, VOLUME=WARN(50)*0.30=15,
        // SCHEMA=PASS(100)*0.20=20, OPS=PASS(100)*0.20=20 → composite=85 → PASS
        List<DqMetric> priorMetrics = new ArrayList<>();
        priorMetrics.add(checkStatusDetail("FRESHNESS", "freshness_status", "PASS"));
        priorMetrics.add(checkStatusDetail("VOLUME",    "volume_status",    "WARN"));
        priorMetrics.add(checkStatusDetail("SCHEMA",    "schema_status",    "PASS"));
        priorMetrics.add(checkStatusDetail("OPS",       "ops_status",       "PASS"));

        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics);
        List<DqMetric> result = check.execute(context);

        MetricDetail breakdown = findDetail(result, "dqs_score_breakdown")
                .orElseThrow(() -> new AssertionError("Missing dqs_score_breakdown MetricDetail"));
        String json = breakdown.getDetailValue();

        // Must be emitted as DQS_SCORE check type
        assertEquals("DQS_SCORE", breakdown.getCheckType());

        // Breakdown must contain all required fields per JSON contract
        assertTrue(json.contains("\"composite_score\""),    "Breakdown must contain composite_score field");
        assertTrue(json.contains("\"status\""),             "Breakdown must contain status field");
        assertTrue(json.contains("\"checks\""),             "Breakdown must contain checks array");
        assertTrue(json.contains("\"check_type\""),         "Each check entry must include check_type");
        assertTrue(json.contains("\"contribution\""),       "Each check entry must include contribution");
        assertTrue(json.contains("\"normalized_weight\""),  "Each check entry must include normalized_weight");
        assertTrue(json.contains("\"unavailable_checks\""), "Breakdown must include unavailable_checks list");

        // Spot-check FRESHNESS presence in checks array
        assertTrue(json.contains("FRESHNESS"), "Breakdown checks array must include FRESHNESS");

        // composite=85 → PASS (≥80)
        assertTrue(json.contains("\"status\":\"PASS\""),
                "composite=85 must yield overall status=PASS");
    }

    // ---------------------------------------------------------------------------
    // AC2: Final score clamped to [0.0, 100.0]
    // ---------------------------------------------------------------------------

    @Test
    void executeClampsFinalScoreToZeroToHundredRange() {
        // Weight normalization guarantees composite ≤ 100 in normal operation.
        // The clamp is a safety net for floating-point edge cases.
        // This test verifies the guard does not corrupt a valid [0,100] result.
        //
        // FRESHNESS=0.60, VOLUME=0.60, SCHEMA=0.00, OPS=0.00 (sum=1.20)
        // After normalization: FRESHNESS=0.50, VOLUME=0.50, SCHEMA=0.0, OPS=0.0
        // All PASS(100) → composite = 0.50*100 + 0.50*100 + 0*100 + 0*100 = 100.0
        Map<String, Double> overlappingWeights = Map.of(
                "FRESHNESS", 0.60,
                "VOLUME",    0.60,
                "SCHEMA",    0.00,
                "OPS",       0.00
        );
        List<DqMetric> priorMetrics = allPassMetrics();

        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics, ctx -> overlappingWeights);

        List<DqMetric> result = check.execute(context);

        MetricNumeric compositeMetric = findNumeric(result, "composite_score")
                .orElseThrow(() -> new AssertionError("Missing composite_score MetricNumeric"));
        double score = compositeMetric.getMetricValue();
        assertTrue(score >= 0.0 && score <= 100.0,
                "Composite score must be within [0.0, 100.0], got: " + score);
        assertEquals(100.0, score, 0.001,
                "All-PASS with normalized weights must yield composite=100");
    }

    @Test
    void executeClampsFinalScoreAtLowerBound() {
        // All four checks FAIL (score=0) — composite must be 0.0, not negative
        List<DqMetric> priorMetrics = new ArrayList<>();
        priorMetrics.add(checkStatusDetail("FRESHNESS", "freshness_status", "FAIL"));
        priorMetrics.add(checkStatusDetail("VOLUME",    "volume_status",    "FAIL"));
        priorMetrics.add(checkStatusDetail("SCHEMA",    "schema_status",    "FAIL"));
        priorMetrics.add(checkStatusDetail("OPS",       "ops_status",       "FAIL"));

        DqsScoreCheck check = new DqsScoreCheck(ctx -> priorMetrics);
        List<DqMetric> result = check.execute(context);

        MetricNumeric compositeMetric = findNumeric(result, "composite_score")
                .orElseThrow(() -> new AssertionError("Missing composite_score MetricNumeric"));
        double score = compositeMetric.getMetricValue();
        assertEquals(0.0, score, 0.001, "All-FAIL checks must yield composite=0.0");
        assertTrue(score >= 0.0, "Composite score must never be negative");

        MetricDetail breakdown = findDetail(result, "dqs_score_breakdown")
                .orElseThrow(() -> new AssertionError("Missing dqs_score_breakdown MetricDetail"));
        assertTrue(breakdown.getDetailValue().contains("\"status\":\"FAIL\""),
                "All-FAIL checks with composite=0 must yield overall status=FAIL");
    }
}
