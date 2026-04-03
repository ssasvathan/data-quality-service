package com.bank.dqs.model;

/**
 * Marker interface — root type for all DQ metric results produced by DqCheck implementations.
 *
 * <p>Concrete types:
 * <ul>
 *   <li>{@link MetricNumeric} — numeric measurement (row count, null rate, staleness hours)</li>
 *   <li>{@link MetricDetail} — structured detail (schema diff, flagged columns, JSON payloads)</li>
 * </ul>
 *
 * <p>{@code dq_run_id} is NOT stored in metric objects — it is resolved by {@code BatchWriter}
 * at write time after the {@code dq_run} record is created.
 */
public interface DqMetric {
}
