package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD RED PHASE — tests fail until SlaCountdownCheck.java is implemented.
 *
 * <p>No SparkSession needed: SlaCountdownCheck is time-based only and never calls
 * {@code context.getDf()}. All tests use plain JUnit 5 with a mock {@code SlaProvider}
 * and a fixed {@code Clock}.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/SlaCountdownCheck.java} is created.
 */
class SlaCountdownCheckTest {

    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a minimal DatasetContext with df=null (safe — SLA check never calls getDf()).
     */
    private DatasetContext context() {
        return new DatasetContext(
                "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
                "ALPHA",
                PARTITION_DATE,
                "/prod/data",
                null,
                DatasetContext.FORMAT_PARQUET
        );
    }

    /**
     * Build an SlaCountdownCheck with a fixed SlaProvider and a Clock fixed to
     * {@code hoursElapsed} hours after midnight on {@code PARTITION_DATE}.
     */
    private SlaCountdownCheck checkWithSlaAndElapsed(Optional<Double> slaHours, long hoursElapsed) {
        Instant fixedNow = PARTITION_DATE
                .atStartOfDay(ZoneId.systemDefault())
                .plusHours(hoursElapsed)
                .toInstant();
        SlaCountdownCheck.SlaProvider provider = ctx -> slaHours;
        Clock clock = Clock.fixed(fixedNow, ZoneId.systemDefault());
        return new SlaCountdownCheck(provider, clock);
    }

    private MetricNumeric findNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MetricNumeric with metricName=" + metricName + " in " + metrics));
    }

    private MetricDetail findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> SlaCountdownCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MetricDetail with detailType=" + SlaCountdownCheck.DETAIL_TYPE_STATUS
                                + " in " + metrics));
    }

    // -------------------------------------------------------------------------
    // AC1: happy-path classification tests
    // -------------------------------------------------------------------------

    /**
     * AC1 — SLA 24 h, 12 h elapsed → hours_remaining = 12, status = PASS.
     */
    @Test
    void executeReturnsHoursRemainingAndPassWhenWithinSla() {
        // Given: SLA=24h, 12h have elapsed since partition-date midnight
        SlaCountdownCheck check = checkWithSlaAndElapsed(Optional.of(24.0), 12L);

        // When
        List<DqMetric> metrics = check.execute(context());

        // Then: numeric metric is present with correct value
        MetricNumeric hoursRemaining = findNumericMetric(metrics,
                SlaCountdownCheck.METRIC_HOURS_REMAINING);
        assertEquals(SlaCountdownCheck.CHECK_TYPE, hoursRemaining.getCheckType());
        assertEquals(12.0, hoursRemaining.getMetricValue(), 0.001,
                "Expected hours_remaining = sla_hours - hours_elapsed = 24 - 12 = 12");

        // And: status detail is PASS
        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status in detail payload");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"within_sla\""),
                "Expected within_sla reason");
    }

    /**
     * AC1 — SLA 10 h, 8.5 h elapsed → hours_remaining = 1.5, ≤ 20% of 10 h (warn_threshold = 2.0)
     * → status = WARN.
     */
    @Test
    void executeReturnsWarnWhenApproachingSlaThreshold() {
        // Given: SLA=10h, 8.5h elapsed → 1.5h remaining, warn_threshold = 10*0.20 = 2.0
        // Use a sub-hour granularity: 8h30m elapsed = 510 minutes
        Instant fixedNow = PARTITION_DATE
                .atStartOfDay(ZoneId.systemDefault())
                .plusMinutes(510)
                .toInstant();
        SlaCountdownCheck.SlaProvider provider = ctx -> Optional.of(10.0);
        Clock clock = Clock.fixed(fixedNow, ZoneId.systemDefault());
        SlaCountdownCheck check = new SlaCountdownCheck(provider, clock);

        // When
        List<DqMetric> metrics = check.execute(context());

        // Then: hours_remaining ≈ 1.5 (within warn window ≤ 2.0)
        MetricNumeric hoursRemaining = findNumericMetric(metrics,
                SlaCountdownCheck.METRIC_HOURS_REMAINING);
        assertTrue(hoursRemaining.getMetricValue() <= 2.0,
                "hours_remaining should be within warn threshold of 2.0h");
        assertTrue(hoursRemaining.getMetricValue() >= 0.0,
                "hours_remaining should not be negative (not yet breached)");

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"WARN\""),
                "Expected WARN status when approaching SLA deadline");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"approaching_sla\""),
                "Expected approaching_sla reason");
    }

    /**
     * AC1 — SLA 12 h, 13 h elapsed → hours_remaining = -1, status = FAIL.
     */
    @Test
    void executeReturnsFailWhenSlaBreached() {
        // Given: SLA=12h, 13h elapsed → hours_remaining = -1 (breach)
        SlaCountdownCheck check = checkWithSlaAndElapsed(Optional.of(12.0), 13L);

        // When
        List<DqMetric> metrics = check.execute(context());

        // Then: numeric metric is negative
        MetricNumeric hoursRemaining = findNumericMetric(metrics,
                SlaCountdownCheck.METRIC_HOURS_REMAINING);
        assertTrue(hoursRemaining.getMetricValue() < 0.0,
                "hours_remaining should be negative when SLA is breached");

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when SLA is breached");
        assertTrue(detail.getDetailValue().contains("\"reason\":\"sla_breached\""),
                "Expected sla_breached reason");
    }

    // -------------------------------------------------------------------------
    // AC2: skip gracefully when no SLA configured
    // -------------------------------------------------------------------------

    /**
     * AC2 — SlaProvider returns empty → execute returns empty list (check not applicable).
     */
    @Test
    void executeReturnsEmptyListWhenNoSlaConfigured() {
        // Given: no SLA configured for this dataset
        SlaCountdownCheck check = checkWithSlaAndElapsed(Optional.empty(), 5L);

        // When
        List<DqMetric> metrics = check.execute(context());

        // Then: empty list returned — no metrics written
        assertTrue(metrics.isEmpty(),
                "Expected empty list when SLA is not configured (check not applicable)");
    }

    /**
     * AC2 — null context → execute returns empty list (not applicable guard).
     */
    @Test
    void executeReturnsEmptyListWhenContextIsNull() {
        // Given: SLA configured but context is null
        SlaCountdownCheck check = checkWithSlaAndElapsed(Optional.of(24.0), 5L);

        // When
        List<DqMetric> metrics = check.execute(null);

        // Then: empty list returned — guard clause exits early
        assertTrue(metrics.isEmpty(),
                "Expected empty list when context is null");
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    /**
     * AC1 — SlaProvider throws → execute returns error detail metric, does NOT propagate.
     */
    @Test
    void executeHandlesExceptionFromSlaProviderGracefully() {
        // Given: SlaProvider throws a runtime exception
        SlaCountdownCheck.SlaProvider failingProvider = ctx -> {
            throw new RuntimeException("simulated JDBC failure");
        };
        Instant fixedNow = PARTITION_DATE
                .atStartOfDay(ZoneId.systemDefault())
                .plusHours(5)
                .toInstant();
        SlaCountdownCheck check = new SlaCountdownCheck(
                failingProvider, Clock.fixed(fixedNow, ZoneId.systemDefault()));

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(context());

        // Then: returns exactly one error detail metric
        assertEquals(1, metrics.size(),
                "Expected exactly one error detail metric when SlaProvider throws");
        MetricDetail errorDetail = (MetricDetail) metrics.get(0);
        assertEquals(SlaCountdownCheck.CHECK_TYPE, errorDetail.getCheckType());
        assertEquals(SlaCountdownCheck.DETAIL_TYPE_STATUS, errorDetail.getDetailType());
        assertTrue(errorDetail.getDetailValue().contains("\"reason\":\"execution_error\""),
                "Expected execution_error reason in error detail payload");
    }

    // -------------------------------------------------------------------------
    // AC3: contract test
    // -------------------------------------------------------------------------

    /**
     * AC3 — getCheckType() returns "SLA_COUNTDOWN".
     */
    @Test
    void getCheckTypeReturnsSlaCountdown() {
        // Given: any valid check instance
        SlaCountdownCheck check = checkWithSlaAndElapsed(Optional.empty(), 0L);

        // When / Then
        assertEquals("SLA_COUNTDOWN", check.getCheckType(),
                "getCheckType() must return the canonical SLA_COUNTDOWN string");
    }
}
