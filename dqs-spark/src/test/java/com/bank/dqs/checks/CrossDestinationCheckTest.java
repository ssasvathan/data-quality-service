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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD RED PHASE — tests will not compile until CrossDestinationCheck.java is implemented.
 *
 * <p>CrossDestinationCheck detects datasets replicated to multiple destinations (same
 * {@code dataset_name} appearing across multiple rows with different run contexts on the same
 * {@code partition_date} in {@code v_dq_run_active}). It queries DQS scores across all active
 * runs, computes the mean, and flags destinations whose score deviates by more than
 * {@code SCORE_DEVIATION_THRESHOLD} (15.0 points) from the mean.
 *
 * <p>It emits {@code MetricNumeric} for {@code destination_count} and
 * {@code inconsistent_destination_count}, plus a {@code MetricDetail} with status FAIL
 * (if any inconsistent destinations) or PASS (if all consistent or single destination).
 *
 * <p>Graceful skips:
 * <ul>
 *   <li>Single destination (provider returns empty) → MetricDetail status=PASS,
 *       reason=single_destination</li>
 *   <li>Null context → MetricDetail status=NOT_RUN, no exception</li>
 * </ul>
 *
 * <p>No SparkSession required: this check does NOT call {@code context.getDf()}.
 * It is purely JDBC-based. All tests use plain JUnit 5 with a mock
 * {@code DestinationStatsProvider} injected via lambda.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/CrossDestinationCheck.java} is created.
 */
class CrossDestinationCheckTest {

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
     * {@link CrossDestinationCheck#DETAIL_TYPE_STATUS}.
     */
    private Optional<MetricDetail> findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> CrossDestinationCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC6: destinations inconsistent → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC6 — provider returns stats with destinationCount=3, inconsistentDestinationCount=1,
     * meanScore=80.0, maxDeviation=18.0 → MetricNumeric destination_count=3.0,
     * MetricNumeric inconsistent_destination_count=1.0, MetricDetail status=FAIL.
     *
     * <p>Given a dataset replicated to 3 destinations where 1 destination deviates by 18 points
     * (exceeding SCORE_DEVIATION_THRESHOLD of 15.0)
     * When the CrossDestinationCheck executes
     * Then it emits MetricNumeric for destination_count=3 and inconsistent_destination_count=1
     * And it emits MetricDetail with status=FAIL.
     */
    @Test
    void executeWritesFailMetricWhenDestinationsAreInconsistent() {
        // Given: provider returns destinationCount=3, inconsistentDestinationCount=1,
        //        meanScore=80.0, maxDeviation=18.0 (exceeds SCORE_DEVIATION_THRESHOLD=15.0)
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> Optional.of(
                        new CrossDestinationCheck.DestinationStats(3, 1, 80.0, 18.0))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric destination_count=3.0
        Optional<MetricNumeric> destCount = findNumericMetric(metrics,
                CrossDestinationCheck.METRIC_DESTINATION_COUNT);
        assertTrue(destCount.isPresent(),
                "Expected MetricNumeric for destination_count");
        assertEquals(CrossDestinationCheck.CHECK_TYPE, destCount.get().getCheckType(),
                "check_type must be CROSS_DESTINATION");
        assertEquals(3.0, destCount.get().getMetricValue(), 0.001,
                "destination_count must be 3.0");

        // And: MetricNumeric inconsistent_destination_count=1.0
        Optional<MetricNumeric> inconsistentCount = findNumericMetric(metrics,
                CrossDestinationCheck.METRIC_INCONSISTENT_DESTINATION_COUNT);
        assertTrue(inconsistentCount.isPresent(),
                "Expected MetricNumeric for inconsistent_destination_count");
        assertEquals(1.0, inconsistentCount.get().getMetricValue(), 0.001,
                "inconsistent_destination_count must be 1.0");

        // And: MetricDetail status=FAIL
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with cross_destination_status detailType");
        assertEquals(CrossDestinationCheck.CHECK_TYPE, detail.get().getCheckType(),
                "check_type must be CROSS_DESTINATION for detail");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when inconsistent_destination_count > 0");
    }

    // -------------------------------------------------------------------------
    // AC6: all destinations consistent → PASS
    // -------------------------------------------------------------------------

    /**
     * [P0] AC6 — provider returns stats with destinationCount=3, inconsistentDestinationCount=0 →
     * MetricDetail status=PASS.
     *
     * <p>Given a dataset replicated to 3 destinations where all scores are within 15 points of mean
     * When the CrossDestinationCheck executes
     * Then it emits MetricDetail with status=PASS.
     */
    @Test
    void executeWritesPassMetricWhenAllDestinationsConsistent() {
        // Given: provider returns destinationCount=3, inconsistentDestinationCount=0,
        //        meanScore=85.0, maxDeviation=5.0 (within SCORE_DEVIATION_THRESHOLD=15.0)
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> Optional.of(
                        new CrossDestinationCheck.DestinationStats(3, 0, 85.0, 5.0))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail status=PASS
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for PASS case when all destinations consistent");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when inconsistent_destination_count=0");

        // And: MetricNumeric destination_count=3.0
        Optional<MetricNumeric> destCount = findNumericMetric(metrics,
                CrossDestinationCheck.METRIC_DESTINATION_COUNT);
        assertTrue(destCount.isPresent(),
                "Expected MetricNumeric for destination_count even in PASS case");
        assertEquals(3.0, destCount.get().getMetricValue(), 0.001,
                "destination_count must be 3.0 in PASS case");
    }

    // -------------------------------------------------------------------------
    // AC7: single destination → PASS, reason=single_destination
    // -------------------------------------------------------------------------

    /**
     * [P0] AC7 — provider returns empty (only one destination, no replication detected) →
     * MetricDetail status=PASS, reason=single_destination.
     *
     * <p>Given a dataset that appears in only one destination (single dq_run row for this
     * dataset_name and partition_date)
     * When the CrossDestinationCheck executes
     * Then it returns MetricDetail with status=PASS and reason=single_destination.
     */
    @Test
    void executeReturnsPassWhenSingleDestination() {
        // Given: provider returns empty (only one destination, no cross-destination comparison)
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> Optional.empty()
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail with status=PASS and reason=single_destination
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when single destination (no replication)");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail for single destination case");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status for single destination");
        assertTrue(detail.get().getDetailValue().contains("\"reason\":\"single_destination\""),
                "Expected reason=single_destination");

        // And: no MetricNumeric (no multi-destination data)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "No MetricNumeric should be emitted for single destination");
    }

    // -------------------------------------------------------------------------
    // AC8: null context → NOT_RUN, no exception
    // -------------------------------------------------------------------------

    /**
     * [P0] AC8 — null context → MetricDetail with status=NOT_RUN, no exception propagation.
     *
     * <p>Given a null context is passed to execute()
     * When the check executes
     * Then it returns a MetricDetail with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> Optional.empty()
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
     * [P1] Exception path — DestinationStatsProvider throws RuntimeException →
     * errorDetail returned, no exception propagation.
     *
     * <p>Given a DestinationStatsProvider that throws a RuntimeException (simulated JDBC failure)
     * When the check executes
     * Then it returns at least one MetricDetail error detail and does NOT propagate the exception.
     */
    @Test
    void executeHandlesExceptionGracefully() {
        // Given: provider throws RuntimeException
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> { throw new RuntimeException("simulated JDBC failure"); }
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
     * [P1] Contract test — getCheckType() returns "CROSS_DESTINATION".
     *
     * <p>CrossDestinationCheck implements DqCheck; the canonical check_type constant must be
     * "CROSS_DESTINATION". Zero changes to serve/API/dashboard required.
     */
    @Test
    void getCheckTypeReturnsCrossDestination() {
        // Given: any valid check instance
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> Optional.empty()
        );

        // When / Then
        assertEquals("CROSS_DESTINATION", check.getCheckType(),
                "getCheckType() must return the canonical CROSS_DESTINATION string");
        assertEquals(CrossDestinationCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC6: FAIL detail contains all required context fields
    // -------------------------------------------------------------------------

    /**
     * [P1] AC6 — FAIL detail payload contains destination_count, inconsistent_destination_count,
     * mean_score, max_deviation, threshold — all required for downstream alerting.
     *
     * <p>Given the cross-destination check produces a FAIL result
     * When the MetricDetail is inspected
     * Then its detailValue JSON contains all required context fields per the story spec.
     */
    @Test
    void executeFailDetailContainsAllRequiredContextFields() {
        // Given: provider returns stats indicating inconsistency (FAIL case)
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> Optional.of(
                        new CrossDestinationCheck.DestinationStats(3, 1, 80.0, 18.0))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: detail payload contains all required fields
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(), "Expected MetricDetail for FAIL case");

        String payload = detail.get().getDetailValue();
        assertTrue(payload.contains("\"status\":\"FAIL\""),
                "Expected FAIL status in detail payload");
        assertTrue(payload.contains("\"destination_count\""),
                "Expected destination_count field in FAIL detail payload");
        assertTrue(payload.contains("\"inconsistent_destination_count\""),
                "Expected inconsistent_destination_count field in FAIL detail payload");
        assertTrue(payload.contains("\"mean_score\""),
                "Expected mean_score field in FAIL detail payload");
        assertTrue(payload.contains("\"max_deviation\""),
                "Expected max_deviation field in FAIL detail payload");
        assertTrue(payload.contains("\"threshold\""),
                "Expected threshold field in FAIL detail payload");
    }

    // -------------------------------------------------------------------------
    // AC6: both MetricNumeric metrics emitted with correct values
    // -------------------------------------------------------------------------

    /**
     * [P0] AC6 — verify MetricNumeric for destination_count and inconsistent_destination_count
     * are emitted with correct values when multiple destinations exist.
     *
     * <p>Given the cross-destination check executes with destinationCount=3,
     * inconsistentDestinationCount=1
     * When the metrics are inspected
     * Then destination_count=3.0 and inconsistent_destination_count=1.0 are both present.
     */
    @Test
    void executeEmitsCorrectMetricNumericsForDestinationCounts() {
        // Given: provider returns destinationCount=3, inconsistentDestinationCount=1
        CrossDestinationCheck check = new CrossDestinationCheck(
                (datasetName, date) -> Optional.of(
                        new CrossDestinationCheck.DestinationStats(3, 1, 80.0, 18.0))
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricNumeric destination_count=3.0
        Optional<MetricNumeric> destCount = findNumericMetric(metrics,
                CrossDestinationCheck.METRIC_DESTINATION_COUNT);
        assertTrue(destCount.isPresent(),
                "Expected MetricNumeric for destination_count");
        assertEquals(CrossDestinationCheck.CHECK_TYPE, destCount.get().getCheckType(),
                "check_type must be CROSS_DESTINATION for destination_count metric");
        assertEquals(3.0, destCount.get().getMetricValue(), 0.001,
                "destination_count must be 3.0");

        // And: MetricNumeric inconsistent_destination_count=1.0
        Optional<MetricNumeric> inconsistentCount = findNumericMetric(metrics,
                CrossDestinationCheck.METRIC_INCONSISTENT_DESTINATION_COUNT);
        assertTrue(inconsistentCount.isPresent(),
                "Expected MetricNumeric for inconsistent_destination_count");
        assertEquals(CrossDestinationCheck.CHECK_TYPE, inconsistentCount.get().getCheckType(),
                "check_type must be CROSS_DESTINATION for inconsistent_destination_count metric");
        assertEquals(1.0, inconsistentCount.get().getMetricValue(), 0.001,
                "inconsistent_destination_count must be 1.0");
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    /**
     * [P1] Constructor validation — null statsProvider throws IllegalArgumentException.
     *
     * <p>Given a null DestinationStatsProvider is passed to the constructor
     * When the CrossDestinationCheck is instantiated
     * Then it throws IllegalArgumentException (fail-fast contract per project rules).
     */
    @Test
    void constructorThrowsWhenStatsProviderIsNull() {
        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> new CrossDestinationCheck(null),
                "Constructor must throw IllegalArgumentException when statsProvider is null"
        );
    }
}
