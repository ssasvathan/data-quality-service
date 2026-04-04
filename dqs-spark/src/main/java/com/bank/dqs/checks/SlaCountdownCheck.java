package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SLA Countdown check implementation.
 *
 * <p>Computes hours remaining until SLA breach from the start of the partition date.
 * The check returns one numeric metric ({@code hours_remaining}) and one detail metric
 * carrying the classification payload ({@code PASS}/{@code WARN}/{@code FAIL}).
 *
 * <p>Unlike Freshness or Volume checks, SLA Countdown does NOT process
 * {@code context.getDf()}. It computes purely from the partition date, the configured
 * {@code sla_hours} from {@code dataset_enrichment}, and the injected {@link Clock}.
 *
 * <p>If no SLA is configured for the dataset, the check returns an empty list
 * (check not applicable — graceful skip).
 *
 * <p>The class preserves per-dataset isolation: any exception is converted into a
 * diagnostic detail metric instead of propagating.
 */
public final class SlaCountdownCheck implements DqCheck {

    public static final String CHECK_TYPE = "SLA_COUNTDOWN";

    public static final String METRIC_HOURS_REMAINING = "hours_remaining";
    public static final String DETAIL_TYPE_STATUS = "sla_countdown_status";

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_WITHIN_SLA = "within_sla";
    private static final String REASON_APPROACHING = "approaching_sla";
    private static final String REASON_BREACHED = "sla_breached";
    private static final String REASON_EXECUTION_ERROR = "execution_error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SlaProvider slaProvider;
    private final Clock clock;

    /**
     * No-arg constructor used by {@code DqsJob.buildCheckFactory()}.
     *
     * <p>Uses {@link NoOpSlaProvider} as default — returns empty (check skipped).
     * The {@link JdbcSlaProvider} is available for full JDBC wiring via the 2-arg constructor.
     */
    public SlaCountdownCheck() {
        this(new NoOpSlaProvider(), Clock.systemDefaultZone());
    }

    /**
     * Testable constructor accepting explicit provider and clock.
     *
     * @param slaProvider the provider for {@code sla_hours} configuration
     * @param clock       the clock used to compute "now" (injectable for tests)
     * @throws IllegalArgumentException if either argument is null
     */
    public SlaCountdownCheck(SlaProvider slaProvider, Clock clock) {
        if (slaProvider == null) {
            throw new IllegalArgumentException("slaProvider must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.slaProvider = slaProvider;
        this.clock = clock;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public List<DqMetric> execute(DatasetContext context) {
        List<DqMetric> metrics = new ArrayList<>();
        try {
            // Guard: null context → not applicable, return empty
            if (context == null) {
                return metrics;
            }

            // Query sla_hours from dataset_enrichment; if not configured → skip gracefully
            Optional<Double> maybeSlaHours = slaProvider.getSlaHours(context);
            if (maybeSlaHours.isEmpty()) {
                return metrics;
            }

            double slaHours = maybeSlaHours.get();

            // Compute hours elapsed since partition-date midnight
            long hoursElapsed = Duration.between(
                    context.getPartitionDate()
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant(),
                    clock.instant()
            ).toHours();

            double hoursRemaining = slaHours - hoursElapsed;

            // Always write the numeric metric when SLA is configured
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_HOURS_REMAINING, hoursRemaining));

            // Determine status
            double warnThreshold = slaHours * 0.20;
            String status;
            String reason;
            if (hoursRemaining < 0.0) {
                status = STATUS_FAIL;
                reason = REASON_BREACHED;
            } else if (hoursRemaining <= warnThreshold) {
                status = STATUS_WARN;
                reason = REASON_APPROACHING;
            } else {
                status = STATUS_PASS;
                reason = REASON_WITHIN_SLA;
            }

            // Write the detail metric with full payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", status);
            payload.put("reason", reason);
            payload.put("hours_remaining", hoursRemaining);
            payload.put("sla_hours", slaHours);
            payload.put("warn_threshold", warnThreshold);
            metrics.add(new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload)));

            return metrics;
        } catch (Exception e) {
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    private MetricDetail errorDetail(Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("hours_remaining", 0.0);
        payload.put("sla_hours", 0.0);
        payload.put("warn_threshold", 0.0);
        payload.put("message", safeMessage(exception));
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"NOT_RUN\",\"reason\":\"execution_error\"}";
        }
    }

    // -------------------------------------------------------------------------
    // Inner interfaces and implementations
    // -------------------------------------------------------------------------

    /**
     * Provider interface for {@code sla_hours} configuration lookup.
     *
     * <p>Returns {@link Optional#empty()} when no SLA is configured for the dataset
     * (no matching row or {@code sla_hours IS NULL}).
     */
    @FunctionalInterface
    public interface SlaProvider {
        Optional<Double> getSlaHours(DatasetContext ctx) throws Exception;
    }

    /**
     * Functional interface for JDBC connection creation — same pattern as
     * {@link FreshnessCheck.ConnectionProvider}.
     */
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * JDBC-backed {@link SlaProvider} that queries {@code v_dataset_enrichment_active}.
     *
     * <p>Uses LIKE reversal pattern (same as {@code EnrichmentResolver}):
     * {@code WHERE ? LIKE dataset_pattern} — the dataset name is matched against
     * stored patterns (e.g., {@code lob=retail/%}).
     */
    public static final class JdbcSlaProvider implements SlaProvider {

        private static final String SLA_QUERY =
                "SELECT sla_hours FROM v_dataset_enrichment_active "
                + "WHERE ? LIKE dataset_pattern AND sla_hours IS NOT NULL "
                + "ORDER BY id ASC LIMIT 1";

        private final ConnectionProvider connectionProvider;

        public JdbcSlaProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<Double> getSlaHours(DatasetContext ctx) throws SQLException {
            if (ctx == null) {
                throw new IllegalArgumentException("context must not be null");
            }
            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SLA_QUERY)) {
                ps.setString(1, ctx.getDatasetName());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double slaHours = rs.getDouble(1);
                        if (!rs.wasNull()) {
                            return Optional.of(slaHours);
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * No-op {@link SlaProvider} — always returns empty (check skipped).
     *
     * <p>Used as the default in the no-arg constructor so that {@code DqsJob}
     * registration does not require a JDBC connection at construction time.
     */
    private static final class NoOpSlaProvider implements SlaProvider {
        @Override
        public Optional<Double> getSlaHours(DatasetContext ctx) {
            return Optional.empty();
        }
    }
}
