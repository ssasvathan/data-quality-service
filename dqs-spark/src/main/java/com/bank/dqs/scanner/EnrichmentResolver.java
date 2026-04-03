package com.bank.dqs.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Resolves raw HDFS src_sys_nm values to canonical lookup codes via the
 * dataset_enrichment table. Legacy path patterns like "omni" are mapped to
 * proper lookup codes (e.g., "UE90") to enable correct quality tracking.
 *
 * <p>Queries the active-record view {@code v_dataset_enrichment_active}.
 * The view already filters on the expiry_date sentinel — no redundant sentinel
 * filter is added here.
 *
 * <p>LIKE reversal pattern: the query tests whether the candidate string
 * ({@code "src_sys_nm=" + rawLookupCode}) matches any stored dataset_pattern
 * using SQL {@code ? LIKE dataset_pattern}. This allows stored patterns to
 * contain SQL LIKE wildcards (e.g., {@code src_sys_nm=omni%}).
 */
public final class EnrichmentResolver {

    private static final Logger LOG = LoggerFactory.getLogger(EnrichmentResolver.class);

    /**
     * SQL using LIKE reversal: candidate LIKE dataset_pattern.
     * The stored dataset_pattern may contain SQL LIKE wildcards (%, _).
     * ORDER BY id ASC picks the first inserted active match deterministically.
     * LIMIT 1 ensures a single result when multiple patterns match.
     * AND lookup_code IS NOT NULL skips enrichment rows that only carry
     * custom_weights or sla_hours.
     */
    private static final String SQL =
            "SELECT lookup_code FROM v_dataset_enrichment_active "
            + "WHERE ? LIKE dataset_pattern AND lookup_code IS NOT NULL "
            + "ORDER BY id ASC LIMIT 1";

    private final Connection conn;

    /**
     * @param conn active JDBC connection to the DQS database
     * @throws IllegalArgumentException if conn is null
     */
    public EnrichmentResolver(Connection conn) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection must not be null");
        }
        this.conn = conn;
    }

    /**
     * Resolve a raw src_sys_nm value to the canonical lookup code.
     *
     * <p>Builds the candidate string {@code "src_sys_nm=" + rawLookupCode} and
     * queries {@code v_dataset_enrichment_active} for a matching dataset_pattern.
     *
     * @param rawLookupCode the src_sys_nm value extracted from the HDFS path
     * @return enriched lookup code if a matching active record is found;
     *         {@code rawLookupCode} unchanged if no match or lookup_code is null
     * @throws IllegalArgumentException if rawLookupCode is null
     * @throws SQLException             on JDBC errors (caller should catch for per-dataset isolation)
     */
    public String resolve(String rawLookupCode) throws SQLException {
        if (rawLookupCode == null) {
            throw new IllegalArgumentException("rawLookupCode must not be null");
        }

        String candidate = "src_sys_nm=" + rawLookupCode;

        try (PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, candidate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String resolved = rs.getString("lookup_code");
                    LOG.debug("Resolved '{}' to lookup_code='{}'", rawLookupCode, resolved);
                    return resolved;
                }
            }
        }

        LOG.warn("No enrichment record found for raw lookup code: '{}' — using raw value", rawLookupCode);
        return rawLookupCode;
    }

    @Override
    public String toString() {
        return "EnrichmentResolver{conn=" + conn + "}";
    }
}
