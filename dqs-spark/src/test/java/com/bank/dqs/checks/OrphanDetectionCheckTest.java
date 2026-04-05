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
 * ATDD RED PHASE — tests will not compile until OrphanDetectionCheck.java is implemented.
 *
 * <p>OrphanDetectionCheck detects datasets that have DQS run history but no active
 * {@code check_config} entries configured — datasets being processed by the Spark job with
 * no quality checks registered. It queries {@code v_check_config_active} using the LIKE
 * reversal pattern: {@code WHERE ? LIKE dataset_pattern AND enabled = TRUE}.
 *
 * <p>Result:
 * <ul>
 *   <li>No matching check_config rows → MetricDetail status=FAIL, reason=no_check_config</li>
 *   <li>At least one matching check_config row → MetricDetail status=PASS,
 *       reason=check_config_present</li>
 *   <li>Null context → MetricDetail status=NOT_RUN, no exception</li>
 * </ul>
 *
 * <p>No MetricNumeric emitted — OrphanDetectionCheck is a binary check (present/absent).
 *
 * <p>No SparkSession required: this check does NOT call {@code context.getDf()}.
 * It is purely JDBC-based. All tests use plain JUnit 5 with a mock
 * {@code CheckConfigProvider} injected via lambda.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/OrphanDetectionCheck.java} is created.
 */
class OrphanDetectionCheckTest {

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
     * Find the first {@code MetricDetail} whose {@code detailType} matches
     * {@link OrphanDetectionCheck#DETAIL_TYPE_STATUS}.
     */
    private Optional<MetricDetail> findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> OrphanDetectionCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC4: no check_config exists → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — provider returns false (no enabled check_config entries for this dataset) →
     * MetricDetail status=FAIL, reason=no_check_config.
     *
     * <p>Given a dataset that has DQS run history but no check_config rows
     * When the OrphanDetectionCheck executes
     * Then it writes MetricDetail with status=FAIL and reason=no_check_config.
     */
    @Test
    void executeWritesFailMetricWhenNoCheckConfigExists() {
        // Given: provider returns false (dataset is orphaned — no check_config)
        OrphanDetectionCheck check = new OrphanDetectionCheck(
                datasetName -> false
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=orphaned_dataset");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail status=FAIL
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when no check_config exists");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with orphan_detection_status detailType");
        assertEquals(OrphanDetectionCheck.CHECK_TYPE, detail.get().getCheckType(),
                "check_type must be ORPHAN_DETECTION");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when no enabled check_config exists");

        // And: no MetricNumeric (OrphanDetectionCheck emits only MetricDetail)
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "OrphanDetectionCheck must not emit MetricNumeric — binary check only");
    }

    // -------------------------------------------------------------------------
    // AC4: check_config present → PASS
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — provider returns true (at least one enabled check_config row matches) →
     * MetricDetail status=PASS, reason=check_config_present.
     *
     * <p>Given a dataset with at least one active check_config entry
     * When the OrphanDetectionCheck executes
     * Then it writes MetricDetail with status=PASS and reason=check_config_present.
     */
    @Test
    void executeWritesPassMetricWhenCheckConfigExists() {
        // Given: provider returns true (check_config present for this dataset)
        OrphanDetectionCheck check = new OrphanDetectionCheck(
                datasetName -> true
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: MetricDetail status=PASS
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when check_config exists");

        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(),
                "Expected MetricDetail with orphan_detection_status detailType");
        assertTrue(detail.get().getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when at least one enabled check_config row matches");

        // And: no MetricNumeric
        long numericCount = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .count();
        assertEquals(0L, numericCount,
                "OrphanDetectionCheck must not emit MetricNumeric — binary check only");
    }

    // -------------------------------------------------------------------------
    // AC5: null context → NOT_RUN, no exception
    // -------------------------------------------------------------------------

    /**
     * [P0] AC5 — null context → MetricDetail with status=NOT_RUN, no exception propagation.
     *
     * <p>Given a null context is passed to execute()
     * When the check executes
     * Then it returns a MetricDetail with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        OrphanDetectionCheck check = new OrphanDetectionCheck(
                datasetName -> false
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
     * [P1] Exception path — CheckConfigProvider throws RuntimeException →
     * errorDetail returned, no exception propagation.
     *
     * <p>Given a CheckConfigProvider that throws a RuntimeException (simulated JDBC failure)
     * When the check executes
     * Then it returns at least one MetricDetail error detail and does NOT propagate the exception.
     */
    @Test
    void executeHandlesExceptionGracefully() {
        // Given: provider throws RuntimeException
        OrphanDetectionCheck check = new OrphanDetectionCheck(
                datasetName -> { throw new RuntimeException("simulated JDBC failure"); }
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
     * [P1] Contract test — getCheckType() returns "ORPHAN_DETECTION".
     *
     * <p>OrphanDetectionCheck implements DqCheck; the canonical check_type constant must be
     * "ORPHAN_DETECTION". Zero changes to serve/API/dashboard required.
     */
    @Test
    void getCheckTypeReturnsOrphanDetection() {
        // Given: any valid check instance
        OrphanDetectionCheck check = new OrphanDetectionCheck(
                datasetName -> false
        );

        // When / Then
        assertEquals("ORPHAN_DETECTION", check.getCheckType(),
                "getCheckType() must return the canonical ORPHAN_DETECTION string");
        assertEquals(OrphanDetectionCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC4: FAIL detail contains reason field
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — FAIL detail payload contains reason=no_check_config field.
     *
     * <p>Given the orphan detection check produces a FAIL result
     * When the MetricDetail is inspected
     * Then its detailValue JSON contains reason=no_check_config per the story spec.
     */
    @Test
    void executeFailDetailContainsReasonField() {
        // Given: provider returns false (FAIL case)
        OrphanDetectionCheck check = new OrphanDetectionCheck(
                datasetName -> false
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=orphaned_dataset");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: FAIL detail contains reason=no_check_config
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(), "Expected MetricDetail for FAIL case");

        String payload = detail.get().getDetailValue();
        assertTrue(payload.contains("\"status\":\"FAIL\""),
                "Expected FAIL status in detail payload");
        assertTrue(payload.contains("\"reason\":\"no_check_config\""),
                "Expected reason=no_check_config in FAIL detail payload");
    }

    // -------------------------------------------------------------------------
    // AC4: PASS detail contains reason field
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — PASS detail payload contains reason=check_config_present field.
     *
     * <p>Given the orphan detection check produces a PASS result
     * When the MetricDetail is inspected
     * Then its detailValue JSON contains reason=check_config_present per the story spec.
     */
    @Test
    void executePassDetailContainsReasonField() {
        // Given: provider returns true (PASS case)
        OrphanDetectionCheck check = new OrphanDetectionCheck(
                datasetName -> true
        );
        DatasetContext ctx = context("lob=retail/src_sys_nm=alpha/dataset=sales_daily");

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: PASS detail contains reason=check_config_present
        Optional<MetricDetail> detail = findStatusDetail(metrics);
        assertTrue(detail.isPresent(), "Expected MetricDetail for PASS case");

        String payload = detail.get().getDetailValue();
        assertTrue(payload.contains("\"status\":\"PASS\""),
                "Expected PASS status in detail payload");
        assertTrue(payload.contains("\"reason\":\"check_config_present\""),
                "Expected reason=check_config_present in PASS detail payload");
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    /**
     * [P1] Constructor validation — null configProvider throws IllegalArgumentException.
     *
     * <p>Given a null CheckConfigProvider is passed to the constructor
     * When the OrphanDetectionCheck is instantiated
     * Then it throws IllegalArgumentException (fail-fast contract per project rules).
     */
    @Test
    void constructorThrowsWhenConfigProviderIsNull() {
        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> new OrphanDetectionCheck(null),
                "Constructor must throw IllegalArgumentException when configProvider is null"
        );
    }
}
