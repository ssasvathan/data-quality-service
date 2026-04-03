package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;

import java.util.List;

/**
 * Strategy interface for all DQ check implementations.
 *
 * <p>Adding a new check = new class implementing this interface + factory registration +
 * a {@code check_config} row. Zero changes required in the serve layer, API, or dashboard.
 *
 * <p>Implementations must follow per-dataset failure isolation:
 * catch all exceptions internally, log them, and return an appropriate failure metric.
 * Never let an exception escape — a single check failure must NOT crash the Spark job.
 */
public interface DqCheck {

    /**
     * Execute this quality check against the given dataset context.
     *
     * <p>Implementations should handle exceptions internally and return a failure metric
     * rather than propagating exceptions. This preserves per-dataset isolation.
     *
     * @param context the immutable context for the dataset being checked
     * @return a non-null list of metric results (may be empty if the check could not run)
     */
    List<DqMetric> execute(DatasetContext context);

    /**
     * Returns the canonical {@code check_type} string used in the {@code check_config} table.
     *
     * <p>This string is used as the registry key in {@link CheckFactory} and as the
     * {@code check_type} column value in {@code dq_metric_numeric} and {@code dq_metric_detail}.
     *
     * <p>Examples: {@code "FRESHNESS"}, {@code "VOLUME"}, {@code "SCHEMA"}, {@code "OPS"},
     * {@code "DQS_SCORE"}.
     *
     * @return the canonical check type string (non-null, non-blank)
     */
    String getCheckType();
}
