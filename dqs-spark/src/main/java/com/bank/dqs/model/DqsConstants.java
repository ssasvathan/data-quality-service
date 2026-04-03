package com.bank.dqs.model;

/**
 * Shared constants for the Data Quality Service Spark component.
 *
 * <p>Temporal pattern: active records use {@code expiry_date = EXPIRY_SENTINEL}.
 * Soft-delete = update expiry_date to current timestamp, then insert new active row.
 * NEVER hardcode {@code "9999-12-31 23:59:59"} inline — always reference this constant.
 * See project-context.md § Temporal Data Pattern (ALL COMPONENTS) for the cross-runtime rule.
 *
 * <p>This is a utility class — not instantiable.
 */
public final class DqsConstants {

    private DqsConstants() {
        // Utility class — prevent instantiation
    }

    /**
     * Sentinel timestamp marking an "active" (non-expired) record in the temporal pattern.
     *
     * <p>All DQS tables store {@code expiry_date = EXPIRY_SENTINEL} for current/active rows.
     * The same value is the DDL DEFAULT for the {@code expiry_date} column in Postgres.
     */
    public static final String EXPIRY_SENTINEL = "9999-12-31 23:59:59";
}
