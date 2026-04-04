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
 * ATDD RED PHASE — tests will not compile until ClassificationWeightedCheck.java is implemented.
 *
 * <p>ClassificationWeightedCheck resolves the dataset's classification from {@code v_lob_lookup_active}
 * via {@code context.getLookupCode()} and emits a {@code MetricNumeric} priority multiplier and a
 * {@code MetricDetail} status payload. It runs BEFORE {@code DqsScoreCheck} and therefore cannot
 * read the final DQS score — the multiplier is a classification priority signal only.
 *
 * <p>No SparkSession required: this check does NOT call {@code context.getDf()}.
 * All tests use plain JUnit 5 with a mock {@code ClassificationProvider} injected via lambda.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/ClassificationWeightedCheck.java} is created.
 */
class ClassificationWeightedCheckTest {

    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a minimal {@code DatasetContext} with df=null (safe — this check never calls getDf()).
     */
    private DatasetContext context(String datasetName, String lookupCode) {
        return new DatasetContext(
                datasetName,
                lookupCode,
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
     * {@link ClassificationWeightedCheck#DETAIL_TYPE_STATUS}.
     */
    private Optional<MetricDetail> findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> ClassificationWeightedCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC1/AC2: Tier 1 Critical classification — multiplier=2.0
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1/AC2 — provider returns "Tier 1 Critical" → MetricNumeric multiplier=2.0,
     * MetricDetail present with classification captured.
     *
     * <p>Given a dataset whose lookup_code resolves to classification="Tier 1 Critical"
     * When the ClassificationWeightedCheck executes
     * Then it emits MetricNumeric(CLASSIFICATION_WEIGHTED, "classification_weighted_score", 2.0)
     * And it emits MetricDetail with status=PASS and classification in payload
     */
    @Test
    void executesReturnsPassForCriticalDataset() {
        // Given: provider returns "Tier 1 Critical"
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
                ctx -> Optional.of("Tier 1 Critical")
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily", "LOB_RETAIL");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric with multiplier=2.0 (critical weight)
        Optional<MetricNumeric> numeric = findNumericMetric(metrics,
                ClassificationWeightedCheck.METRIC_WEIGHTED_SCORE);
        assertTrue(numeric.isPresent(),
                "Expected MetricNumeric for classification_weighted_score");
        assertEquals(ClassificationWeightedCheck.CHECK_TYPE, numeric.get().getCheckType(),
                "check_type must be CLASSIFICATION_WEIGHTED");
        assertEquals(2.0, numeric.get().getMetricValue(), 0.001,
                "multiplier must be 2.0 for Tier 1 Critical");

        // And: MetricDetail with CHECK_TYPE, DETAIL_TYPE_STATUS, and Tier 1 Critical in payload
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with classification_weighted_status detailType");
        assertEquals(ClassificationWeightedCheck.CHECK_TYPE, detail.get().getCheckType(),
                "check_type must be CLASSIFICATION_WEIGHTED for detail metric");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status in detail payload for critical classification");
        assertTrue(detail.get().getDetailValue().contains("Tier 1 Critical"),
                "Expected classification value in detail payload");
        assertTrue(detail.get().getDetailValue().contains("\"multiplier\":2.0"),
                "Expected multiplier=2.0 in detail payload");
    }

    // -------------------------------------------------------------------------
    // AC1: Tier 2 Standard classification — multiplier=1.0
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — provider returns "Tier 2 Standard" → multiplier=1.0, MetricDetail present.
     *
     * <p>Given a dataset whose lookup_code resolves to classification="Tier 2 Standard"
     * When the check executes
     * Then MetricNumeric has multiplier=1.0 and MetricDetail status=PASS with classification captured.
     */
    @Test
    void executeReturnsPassForStandardDataset() {
        // Given: provider returns "Tier 2 Standard"
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
                ctx -> Optional.of("Tier 2 Standard")
        );
        DatasetContext ctx = context("lob=corp/src_sys_nm=beta/dataset=loan_data", "LOB_CORP");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: multiplier=1.0
        Optional<MetricNumeric> numeric = findNumericMetric(metrics,
                ClassificationWeightedCheck.METRIC_WEIGHTED_SCORE);
        assertTrue(numeric.isPresent(),
                "Expected MetricNumeric for Tier 2 Standard dataset");
        assertEquals(1.0, numeric.get().getMetricValue(), 0.001,
                "multiplier must be 1.0 for Tier 2 Standard");

        // And: detail status=PASS
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for Tier 2 Standard dataset");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status for standard classification");
        assertTrue(detail.get().getDetailValue().contains("Tier 2 Standard"),
                "Expected classification in payload");
    }

    // -------------------------------------------------------------------------
    // AC1: Unknown/low-priority classification — multiplier=0.5
    // -------------------------------------------------------------------------

    /**
     * [P1] AC1 — provider returns unknown classification string → multiplier=0.5, status=PASS.
     *
     * <p>Given a dataset whose classification is an unrecognised string (not Tier 1 Critical,
     * not Tier 2 Standard)
     * When the check executes
     * Then MetricNumeric multiplier=0.5 (low priority default) and MetricDetail status=PASS.
     */
    @Test
    void executeReturnsPassForLowPriorityDataset() {
        // Given: provider returns an unrecognised classification
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
                ctx -> Optional.of("Tier 3 Low")
        );
        DatasetContext ctx = context("lob=ops/src_sys_nm=gamma/dataset=archive_log", "LOB_OPS");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: multiplier=0.5 (low / unclassified default)
        Optional<MetricNumeric> numeric = findNumericMetric(metrics,
                ClassificationWeightedCheck.METRIC_WEIGHTED_SCORE);
        assertTrue(numeric.isPresent(),
                "Expected MetricNumeric for low-priority dataset");
        assertEquals(0.5, numeric.get().getMetricValue(), 0.001,
                "multiplier must be 0.5 for unknown/low-priority classification");

        // And: detail present with PASS
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for low-priority dataset");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status for low-priority classification");
    }

    // -------------------------------------------------------------------------
    // AC3: No classification found — graceful skip
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — provider returns empty → MetricDetail with status=PASS and
     * reason=no_classification_found.
     *
     * <p>Given a dataset whose lookup_code resolves to no lob_lookup row
     * When the check executes
     * Then it returns a MetricDetail with status=PASS and reason=no_classification_found
     * And no MetricNumeric is emitted (skip, not an error).
     */
    @Test
    void executeReturnsPassWhenNoClassificationFound() {
        // Given: provider returns empty (no matching row)
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
                ctx -> Optional.empty()
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily", "LOB_RETAIL");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_classification_found
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when no classification found");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with classification_weighted_status detailType");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no classification found");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"no_classification_found\""),
                "Expected no_classification_found reason");

        // And: no MetricNumeric (no multiplier when classification not found)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when no classification found");
    }

    // -------------------------------------------------------------------------
    // AC3: null lookupCode — treated as no_classification_found
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — context with null lookupCode → no provider call (or returns empty),
     * MetricDetail with reason=no_classification_found.
     *
     * <p>Given a dataset whose lookupCode is null (no enrichment record)
     * When the check executes
     * Then it returns MetricDetail with status=PASS and reason=no_classification_found
     * And no MetricNumeric is emitted.
     */
    @Test
    void executeReturnsPassWhenLookupCodeIsNull() {
        // Given: context with null lookupCode — provider should return empty or not be called
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
                ctx -> Optional.empty()  // provider returns empty when lookupCode is null
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily", null);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=no_classification_found
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail when lookupCode is null");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when lookupCode is null");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"no_classification_found\""),
                "Expected no_classification_found reason when lookupCode is null");

        // And: no MetricNumeric
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted when lookupCode is null");
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
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
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
     * [P1] Exception path — ClassificationProvider throws RuntimeException →
     * errorDetail returned, no exception propagation.
     *
     * <p>Given a ClassificationProvider that throws a RuntimeException (simulated JDBC failure)
     * When the check executes
     * Then it returns exactly one MetricDetail error detail and does NOT propagate the exception.
     */
    @Test
    void executeHandlesExceptionGracefully() {
        // Given: provider throws RuntimeException
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
                ctx -> { throw new RuntimeException("simulated JDBC failure"); }
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily", "LOB_RETAIL");

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
                "Expected NOT_RUN or error status in error detail payload");

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
     * [P1] AC7 — getCheckType() returns "CLASSIFICATION_WEIGHTED".
     *
     * <p>ClassificationWeightedCheck implements DqCheck; the canonical check_type constant must be
     * "CLASSIFICATION_WEIGHTED". This also confirms zero changes to serve/API/dashboard are needed.
     */
    @Test
    void getCheckTypeReturnsClassificationWeighted() {
        // Given: any valid check instance
        ClassificationWeightedCheck check = new ClassificationWeightedCheck(
                ctx -> Optional.empty()
        );

        // When / Then
        assertEquals("CLASSIFICATION_WEIGHTED", check.getCheckType(),
                "getCheckType() must return the canonical CLASSIFICATION_WEIGHTED string");
        assertEquals(ClassificationWeightedCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }
}
