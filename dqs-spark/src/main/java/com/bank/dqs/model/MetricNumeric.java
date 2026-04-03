package com.bank.dqs.model;

/**
 * Numeric DQ metric result — maps to {@code dq_metric_numeric} columns:
 * {@code check_type}, {@code metric_name}, {@code metric_value}.
 *
 * <p>{@code dq_run_id} is resolved by {@code BatchWriter} at write time; not stored here.
 *
 * <p>Immutable — no setters.
 */
public final class MetricNumeric implements DqMetric {

    private final String checkType;
    private final String metricName;
    private final double metricValue;

    public MetricNumeric(String checkType, String metricName, double metricValue) {
        if (checkType == null || checkType.isBlank()) {
            throw new IllegalArgumentException("checkType must not be null or blank");
        }
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("metricName must not be null or blank");
        }
        this.checkType   = checkType;
        this.metricName  = metricName;
        this.metricValue = metricValue;
    }

    /** Returns the canonical check_type string (e.g., {@code "VOLUME"}, {@code "FRESHNESS"}). */
    public String getCheckType()   { return checkType; }

    /** Returns the metric name stored in {@code dq_metric_numeric.metric_name}. */
    public String getMetricName()  { return metricName; }

    /** Returns the numeric value stored in {@code dq_metric_numeric.metric_value}. */
    public double getMetricValue() { return metricValue; }

    @Override
    public String toString() {
        return "MetricNumeric{checkType='" + checkType + "', metricName='" + metricName
                + "', metricValue=" + metricValue + '}';
    }
}
