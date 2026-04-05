package com.bank.dqs.scanner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link EnrichmentResolver}.
 *
 * <p>Uses H2 in-memory database with a {@code v_dataset_enrichment_active} VIEW
 * matching the Postgres schema DDL. H2 supports the LIMIT syntax
 * used in the resolver's SQL and the {@code LIKE} reversal pattern.
 *
 * <p>Test naming follows the project {@code <action><expectation>} convention.
 */
class EnrichmentResolverTest {

    // Distinct H2 DB name — avoids collision with CheckFactoryTest (checkfactory_testdb)
    private static final String JDBC_URL =
            "jdbc:h2:mem:enrichment_testdb;DB_CLOSE_DELAY=-1";

    private static Connection conn;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        conn = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS dataset_enrichment (
                        id              INTEGER PRIMARY KEY AUTO_INCREMENT,
                        dataset_pattern TEXT NOT NULL,
                        lookup_code     TEXT,
                        custom_weights  VARCHAR(4096),
                        sla_hours       NUMERIC,
                        explosion_level INTEGER NOT NULL DEFAULT 0,
                        create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
                        expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
                    )
                    """);
            stmt.execute("""
                    CREATE OR REPLACE VIEW v_dataset_enrichment_active AS
                        SELECT * FROM dataset_enrichment
                        WHERE expiry_date = '9999-12-31 23:59:59'
                    """);
        }
    }

    @BeforeEach
    void cleanData() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM dataset_enrichment");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void insertEnrichment(String pattern, String lookupCode) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO dataset_enrichment(dataset_pattern, lookup_code) VALUES (?, ?)")) {
            ps.setString(1, pattern);
            ps.setString(2, lookupCode);
            ps.executeUpdate();
        }
    }

    private void insertEnrichmentNullCode(String pattern) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO dataset_enrichment(dataset_pattern, lookup_code) VALUES (?, NULL)")) {
            ps.setString(1, pattern);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------------
    // AC1, AC3: Happy path — enrichment record found
    // ---------------------------------------------------------------------------

    /**
     * AC1 + AC3: Given a dataset path with src_sys_nm=omni, when the resolver queries
     * v_dataset_enrichment_active for a pattern match, it returns the enriched lookup code UE90.
     */
    @Test
    void resolveReturnsEnrichedLookupCodeWhenPatternMatches() throws Exception {
        // Dataset pattern stores "src_sys_nm=omni" as a literal LIKE pattern.
        // The resolver builds candidate "src_sys_nm=omni" and tests: candidate LIKE dataset_pattern.
        insertEnrichment("src_sys_nm=omni", "UE90");

        EnrichmentResolver resolver = new EnrichmentResolver(conn);

        String result = resolver.resolve("omni");

        assertEquals("UE90", result,
                "Expected enriched lookup code UE90 for omni -> src_sys_nm=omni pattern match");
    }

    // ---------------------------------------------------------------------------
    // AC2: No enrichment record — returns raw code unchanged
    // ---------------------------------------------------------------------------

    /**
     * AC2: Given a dataset path with no matching enrichment record, the resolver
     * returns the raw lookup code unchanged.
     */
    @Test
    void resolveReturnsRawCodeWhenNoEnrichmentRecord() throws Exception {
        // No rows inserted — view is empty

        EnrichmentResolver resolver = new EnrichmentResolver(conn);

        String result = resolver.resolve("unknown_system");

        assertEquals("unknown_system", result,
                "Expected raw lookup code returned when no enrichment record found");
    }

    // ---------------------------------------------------------------------------
    // Edge case: multiple patterns match — first result used, no crash
    // ---------------------------------------------------------------------------

    /**
     * When multiple patterns could match the same candidate (e.g., exact pattern and
     * wildcard pattern both match), the resolver returns the first inserted active result.
     * The SQL uses ORDER BY id ASC LIMIT 1.
     */
    @Test
    void resolveUsesFirstMatchWhenMultiplePatternsMatch() throws Exception {
        insertEnrichment("src_sys_nm=omni", "UE90");
        // Wildcard pattern also matches "src_sys_nm=omni"
        insertEnrichment("src_sys_nm=omni%", "UE91");

        EnrichmentResolver resolver = new EnrichmentResolver(conn);

        // Should not throw — LIMIT 1 handles multiple matches
        String result = resolver.resolve("omni");

        // Deterministic "first match": first inserted row (id ASC) wins.
        assertEquals("UE90", result,
                "Expected first inserted matching row to be returned");
    }

    // ---------------------------------------------------------------------------
    // Edge case: row exists but lookup_code IS NULL — raw code returned
    // ---------------------------------------------------------------------------

    /**
     * A dataset_enrichment row may carry only custom_weights or sla_hours,
     * with lookup_code = NULL. In that case the resolver must return the raw
     * lookup code rather than null. The SQL filters AND lookup_code IS NOT NULL.
     */
    @Test
    void resolveReturnsRawCodeWhenLookupCodeIsNull() throws Exception {
        // Row exists with matching pattern but lookup_code IS NULL
        insertEnrichmentNullCode("src_sys_nm=omni");

        EnrichmentResolver resolver = new EnrichmentResolver(conn);

        String result = resolver.resolve("omni");

        assertEquals("omni", result,
                "Expected raw code returned when matching row has NULL lookup_code");
    }

    // ---------------------------------------------------------------------------
    // Constructor validation
    // ---------------------------------------------------------------------------

    /**
     * Constructor must throw IllegalArgumentException when Connection is null.
     */
    @Test
    void constructorThrowsOnNullConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> new EnrichmentResolver(null),
                "EnrichmentResolver constructor should throw on null connection");
    }

    // ---------------------------------------------------------------------------
    // resolve() method null guard
    // ---------------------------------------------------------------------------

    /**
     * resolve() must throw IllegalArgumentException when rawLookupCode is null.
     */
    @Test
    void resolveThrowsOnNullRawLookupCode() throws Exception {
        EnrichmentResolver resolver = new EnrichmentResolver(conn);

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(null),
                "resolve() should throw IllegalArgumentException for null rawLookupCode");
    }

    // ---------------------------------------------------------------------------
    // LIKE wildcard pattern — src_sys_nm=omni% matches src_sys_nm=omni_ext
    // ---------------------------------------------------------------------------

    /**
     * A dataset_enrichment pattern like "src_sys_nm=omni%" (trailing wildcard) should
     * match candidates like "src_sys_nm=omni_ext" via the LIKE reversal in the SQL.
     */
    @Test
    void resolveMatchesWildcardPatternForPrefixVariants() throws Exception {
        // Pattern "src_sys_nm=omni%" matches any candidate starting with "src_sys_nm=omni"
        insertEnrichment("src_sys_nm=omni%", "UE90");

        EnrichmentResolver resolver = new EnrichmentResolver(conn);

        // "omni_ext" builds candidate "src_sys_nm=omni_ext" — matches "src_sys_nm=omni%"
        String result = resolver.resolve("omni_ext");

        assertEquals("UE90", result,
                "Expected UE90 for wildcard pattern src_sys_nm=omni% matching omni_ext");
    }

    // ---------------------------------------------------------------------------
    // Expired enrichment record — not returned via view
    // ---------------------------------------------------------------------------

    /**
     * An expired enrichment row (expiry_date != sentinel) must NOT be returned
     * by the active-record view. The resolver should fall back to raw code.
     */
    @Test
    void resolveReturnsRawCodeWhenMatchingRowIsExpired() throws Exception {
        // Insert expired row (expiry_date in the past — not active)
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO dataset_enrichment(dataset_pattern, lookup_code, expiry_date) "
                + "VALUES (?, ?, '2026-01-01 00:00:00')")) {
            ps.setString(1, "src_sys_nm=omni");
            ps.setString(2, "UE90");
            ps.executeUpdate();
        }

        EnrichmentResolver resolver = new EnrichmentResolver(conn);

        String result = resolver.resolve("omni");

        assertEquals("omni", result,
                "Expired enrichment row must not be visible through v_dataset_enrichment_active view");
    }
}
