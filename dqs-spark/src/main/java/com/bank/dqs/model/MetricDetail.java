package com.bank.dqs.model;

/**
 * Structured DQ metric result — maps to {@code dq_metric_detail} columns:
 * {@code check_type}, {@code detail_type}, {@code detail_value}.
 *
 * <p>{@code detail_value} is a JSON-formatted string stored as {@code JSONB} in Postgres.
 * {@code BatchWriter} casts the string to JSONB at write time.
 *
 * <p>Data sensitivity rule: detail metrics may contain field names and schema structures,
 * but NEVER source data values (no PII/PCI).
 *
 * <p>Immutable — no setters.
 */
public final class MetricDetail implements DqMetric {

    private final String checkType;
    private final String detailType;
    private final String detailValue;

    public MetricDetail(String checkType, String detailType, String detailValue) {
        if (checkType == null || checkType.isBlank()) {
            throw new IllegalArgumentException("checkType must not be null or blank");
        }
        if (detailType == null || detailType.isBlank()) {
            throw new IllegalArgumentException("detailType must not be null or blank");
        }
        this.checkType   = checkType;
        this.detailType  = detailType;
        this.detailValue = detailValue;
    }

    /** Returns the canonical check_type string (e.g., {@code "SCHEMA"}, {@code "OPS"}). */
    public String getCheckType()  { return checkType; }

    /** Returns the detail type stored in {@code dq_metric_detail.detail_type}. */
    public String getDetailType() { return detailType; }

    /**
     * Returns the JSON string stored in {@code dq_metric_detail.detail_value} (JSONB column).
     * May be {@code null} when no detail payload is needed.
     */
    public String getDetailValue() { return detailValue; }

    @Override
    public String toString() {
        return "MetricDetail{checkType='" + checkType + "', detailType='" + detailType
                + "', detailValue='" + detailValue + "'}";
    }
}
