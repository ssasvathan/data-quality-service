package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DQS Score check — computes a weighted composite quality score (0-100) from the
 * in-memory results of all four Tier 1 checks (Freshness, Volume, Schema, Ops).
 *
 * <p>This is the <em>only</em> component that knows about Tier 1 check types.
 * The serve layer, API, and dashboard have zero check-type awareness.
 *
 * <p>Score mapping per check status:
 * <ul>
 *   <li>{@code PASS} → 100.0</li>
 *   <li>{@code WARN} → 50.0</li>
 *   <li>{@code FAIL} → 0.0</li>
 *   <li>{@code NOT_RUN} or missing → unavailable (excluded with weight redistribution)</li>
 * </ul>
 *
 * <p>Overall status thresholds:
 * <ul>
 *   <li>composite_score &ge; 80.0 → PASS</li>
 *   <li>composite_score &ge; 50.0 → WARN</li>
 *   <li>composite_score &lt; 50.0 → FAIL</li>
 * </ul>
 *
 * <p>Preserves per-dataset failure isolation: all exceptions are converted into
 * deterministic {@code NOT_RUN} detail payloads and never propagated.
 */
public final class DqsScoreCheck implements DqCheck {

    public static final String CHECK_TYPE = "DQS_SCORE";

    private static final String DETAIL_TYPE_BREAKDOWN = "dqs_score_breakdown";
    private static final String METRIC_NAME_COMPOSITE = "composite_score";

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_COMPOSITE_COMPUTED = "composite_computed";
    private static final String REASON_PARTIAL_CHECKS = "partial_checks";
    private static final String REASON_ALL_CHECKS_UNAVAILABLE = "all_checks_unavailable";
    private static final String REASON_MISSING_CONTEXT = "missing_context";
    private static final String REASON_EXECUTION_ERROR = "execution_error";

    /** Ordered list of Tier 1 check descriptors (type + detail_type to read). */
    private static final List<CheckDescriptor> TIER1_CHECKS = List.of(
            new CheckDescriptor(FreshnessCheck.CHECK_TYPE,  "freshness_status"),
            new CheckDescriptor(VolumeCheck.CHECK_TYPE,     "volume_status"),
            new CheckDescriptor(SchemaCheck.CHECK_TYPE,     "schema_status"),
            new CheckDescriptor(OpsCheck.CHECK_TYPE,        "ops_status")
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ScoreInputProvider scoreInputProvider;
    private final WeightProvider weightProvider;

    /** No-arg constructor: uses NoOpScoreInputProvider + DefaultWeightProvider. */
    public DqsScoreCheck() {
        this(new NoOpScoreInputProvider(), new DefaultWeightProvider());
    }

    /** Single-arg constructor: uses the supplied provider + DefaultWeightProvider. */
    public DqsScoreCheck(ScoreInputProvider scoreInputProvider) {
        this(scoreInputProvider, new DefaultWeightProvider());
    }

    /** Two-arg constructor: full injection for both provider types. */
    public DqsScoreCheck(ScoreInputProvider scoreInputProvider, WeightProvider weightProvider) {
        if (scoreInputProvider == null) {
            throw new IllegalArgumentException("scoreInputProvider must not be null");
        }
        if (weightProvider == null) {
            throw new IllegalArgumentException("weightProvider must not be null");
        }
        this.scoreInputProvider = scoreInputProvider;
        this.weightProvider = weightProvider;
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public List<DqMetric> execute(DatasetContext context) {
        List<DqMetric> metrics = new ArrayList<>();
        try {
            if (context == null) {
                metrics.add(notRunDetail(REASON_MISSING_CONTEXT, null, List.of(), List.of()));
                return metrics;
            }

            List<DqMetric> priorMetrics = scoreInputProvider.getCheckResults(context);
            if (priorMetrics == null) {
                priorMetrics = List.of();
            }

            Map<String, Double> weights = weightProvider.getWeights(context);
            if (weights == null) {
                weights = Map.of();
            }

            // Extract per-check scores
            List<CheckResult> available = new ArrayList<>();
            List<String> unavailableCheckTypes = new ArrayList<>();

            for (CheckDescriptor descriptor : TIER1_CHECKS) {
                double score = extractScore(priorMetrics, descriptor.checkType, descriptor.detailType);
                double weight = weights.getOrDefault(descriptor.checkType, 0.0);
                if (Double.isNaN(score)) {
                    unavailableCheckTypes.add(descriptor.checkType);
                } else {
                    available.add(new CheckResult(descriptor.checkType, score, weight));
                }
            }

            if (available.isEmpty()) {
                // All checks unavailable → NOT_RUN; unavailableCheckTypes already contains all four
                metrics.add(notRunDetail(REASON_ALL_CHECKS_UNAVAILABLE, null, List.of(), unavailableCheckTypes));
                return metrics;
            }

            // Compute normalized weights and contributions
            double totalAvailableWeight = 0.0;
            for (CheckResult cr : available) {
                totalAvailableWeight += cr.weight;
            }

            // Guard against zero-weight edge case
            if (totalAvailableWeight <= 0.0) {
                // Equal distribution
                totalAvailableWeight = available.size();
                for (CheckResult cr : available) {
                    cr.weight = 1.0;
                }
            }

            double compositeScore = 0.0;
            for (CheckResult cr : available) {
                cr.normalizedWeight = cr.weight / totalAvailableWeight;
                cr.contribution = cr.normalizedWeight * cr.score;
                compositeScore += cr.contribution;
            }

            // Clamp to [0.0, 100.0]
            compositeScore = Math.max(0.0, Math.min(100.0, compositeScore));

            String overallStatus = deriveStatus(compositeScore);
            String reason = unavailableCheckTypes.isEmpty()
                    ? REASON_COMPOSITE_COMPUTED
                    : REASON_PARTIAL_CHECKS;

            // Emit MetricNumeric
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_NAME_COMPOSITE, compositeScore));

            // Emit MetricDetail breakdown
            metrics.add(buildBreakdownDetail(overallStatus, compositeScore, reason, available, unavailableCheckTypes));

            return metrics;

        } catch (Exception exception) {
            metrics.clear();
            metrics.add(notRunDetail(
                    REASON_EXECUTION_ERROR,
                    safeErrorType(exception),
                    List.of(),
                    List.of()
            ));
            return metrics;
        }
    }

    // ---------------------------------------------------------------------------
    // Score extraction
    // ---------------------------------------------------------------------------

    private double extractScore(List<DqMetric> allMetrics, String checkType, String detailType) {
        return allMetrics.stream()
                .filter(m -> m instanceof MetricDetail)
                .map(m -> (MetricDetail) m)
                .filter(m -> checkType.equals(m.getCheckType()) && detailType.equals(m.getDetailType()))
                .findFirst()
                .map(m -> parseStatusScore(m.getDetailValue()))
                .orElse(Double.NaN);
    }

    private double parseStatusScore(String detailValueJson) {
        if (detailValueJson == null || detailValueJson.isBlank()) {
            return Double.NaN;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = OBJECT_MAPPER.readValue(detailValueJson, Map.class);
            Object statusObj = payload.get("status");
            if (statusObj == null) {
                return Double.NaN;
            }
            String status = String.valueOf(statusObj);
            return switch (status) {
                case "PASS"    -> 100.0;
                case "WARN"    -> 50.0;
                case "FAIL"    -> 0.0;
                default        -> Double.NaN; // NOT_RUN or anything else
            };
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    // ---------------------------------------------------------------------------
    // Status derivation
    // ---------------------------------------------------------------------------

    private String deriveStatus(double compositeScore) {
        if (compositeScore >= 80.0) {
            return STATUS_PASS;
        } else if (compositeScore >= 50.0) {
            return STATUS_WARN;
        } else {
            return STATUS_FAIL;
        }
    }

    // ---------------------------------------------------------------------------
    // Metric builders
    // ---------------------------------------------------------------------------

    private MetricDetail notRunDetail(
            String reason,
            String errorType,
            List<CheckResult> checks,
            List<String> unavailableCheckTypes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("composite_score", 0.0);
        payload.put("reason", reason);
        payload.put("checks", buildChecksArray(checks));
        payload.put("unavailable_checks", unavailableCheckTypes);
        if (errorType != null) {
            payload.put("error_type", errorType);
        }
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_BREAKDOWN, toJson(payload, "{}"));
    }

    private MetricDetail buildBreakdownDetail(
            String status,
            double compositeScore,
            String reason,
            List<CheckResult> available,
            List<String> unavailableCheckTypes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("composite_score", compositeScore);
        payload.put("reason", reason);
        payload.put("checks", buildChecksArray(available));
        payload.put("unavailable_checks", unavailableCheckTypes);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_BREAKDOWN, toJson(payload, "{}"));
    }

    private List<Map<String, Object>> buildChecksArray(List<CheckResult> checks) {
        List<Map<String, Object>> array = new ArrayList<>();
        for (CheckResult cr : checks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("check_type", cr.checkType);
            entry.put("score", cr.score);
            entry.put("weight", cr.weight);
            entry.put("normalized_weight", cr.normalizedWeight);
            entry.put("contribution", cr.contribution);
            array.add(entry);
        }
        return array;
    }

    // ---------------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------------

    private String safeErrorType(Exception exception) {
        if (exception == null) {
            return "UnknownError";
        }
        return exception.getClass().getSimpleName();
    }

    private String toJson(Object payload, String fallback) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------------------
    // Public interfaces
    // ---------------------------------------------------------------------------

    /**
     * Provides the {@link DqMetric} results already computed for a dataset in the current run.
     * These are the outputs from Freshness, Volume, Schema, and Ops checks.
     */
    @FunctionalInterface
    public interface ScoreInputProvider {
        /**
         * Returns the DqMetric results already computed for this dataset in the current run.
         * May return null or empty list when no prior checks have run.
         */
        List<DqMetric> getCheckResults(DatasetContext context) throws Exception;
    }

    /**
     * Provides check weights keyed by check_type (e.g., {@code "FRESHNESS"} → 0.30).
     * Weights should sum to approximately 1.0 for all four Tier 1 checks.
     */
    @FunctionalInterface
    public interface WeightProvider {
        /**
         * Returns check weights keyed by check_type.
         * May return a subset — missing checks use 0.0 weight.
         */
        Map<String, Double> getWeights(DatasetContext context) throws Exception;
    }

    // ---------------------------------------------------------------------------
    // Default implementations
    // ---------------------------------------------------------------------------

    /**
     * Default weight provider returning the standard Tier 1 check weights.
     * FRESHNESS: 0.30, VOLUME: 0.30, SCHEMA: 0.20, OPS: 0.20.
     */
    public static final class DefaultWeightProvider implements WeightProvider {

        private static final Map<String, Double> DEFAULT_WEIGHTS;

        static {
            Map<String, Double> w = new LinkedHashMap<>();
            w.put(FreshnessCheck.CHECK_TYPE, 0.30);
            w.put(VolumeCheck.CHECK_TYPE,    0.30);
            w.put(SchemaCheck.CHECK_TYPE,    0.20);
            w.put(OpsCheck.CHECK_TYPE,       0.20);
            DEFAULT_WEIGHTS = Collections.unmodifiableMap(w);
        }

        @Override
        public Map<String, Double> getWeights(DatasetContext context) {
            return DEFAULT_WEIGHTS;
        }
    }

    /**
     * JDBC-based weight provider. Reads custom weights from {@code check_config} or
     * {@code dataset_enrichment}. For MVP this implementation falls back to defaults;
     * full JDBC resolution is wired in Story 2.10.
     */
    public static final class JdbcWeightProvider implements WeightProvider {

        private final WeightProvider fallback;

        public JdbcWeightProvider() {
            this.fallback = new DefaultWeightProvider();
        }

        @Override
        public Map<String, Double> getWeights(DatasetContext context) throws Exception {
            // MVP: fall back to defaults — full JDBC resolution wired in Story 2.10
            return fallback.getWeights(context);
        }
    }

    /** No-op score input provider: returns an empty list (all checks treated as unavailable). */
    private static final class NoOpScoreInputProvider implements ScoreInputProvider {
        @Override
        public List<DqMetric> getCheckResults(DatasetContext context) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------------------
    // Internal data holders
    // ---------------------------------------------------------------------------

    private static final class CheckDescriptor {
        final String checkType;
        final String detailType;

        CheckDescriptor(String checkType, String detailType) {
            this.checkType = checkType;
            this.detailType = detailType;
        }
    }

    private static final class CheckResult {
        final String checkType;
        final double score;
        double weight;
        double normalizedWeight;
        double contribution;

        CheckResult(String checkType, double score, double weight) {
            this.checkType = checkType;
            this.score = score;
            this.weight = weight;
            this.normalizedWeight = 0.0;
            this.contribution = 0.0;
        }
    }
}
