package com.bank.dqs.scanner;

import com.bank.dqs.model.DatasetContext;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for {@link ConsumerZoneScanner} enrichment behavior (Story 2.4).
 *
 * <p>Tests the new two-argument constructor {@code ConsumerZoneScanner(FileSystem, EnrichmentResolver)}
 * and verifies that the {@code lookupCode} field in the returned {@link DatasetContext} is correctly
 * enriched (or left as raw code on failure/no-resolver).
 *
 * <p>Uses:
 * <ul>
 *   <li>Hadoop {@code LocalFileSystem} with {@link TempDir} for HDFS-like directory structure
 *   <li>H2 in-memory database (distinct name: {@code scanner_enrichment_testdb}) backing a real
 *       {@link EnrichmentResolver} — no Mockito, per project conventions
 * </ul>
 *
 * <p>All 19 existing {@link ConsumerZoneScannerTest} tests must still pass after this story.
 */
class ConsumerZoneScannerEnrichmentTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:scanner_enrichment_testdb;DB_CLOSE_DELAY=-1";

    private static FileSystem fs;
    private static Connection dbConn;

    @TempDir
    java.nio.file.Path tempDir;

    private String parentPath;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 3, 30);

    @BeforeAll
    static void setUpResources() throws Exception {
        // --- Hadoop LocalFileSystem ---
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        fs = FileSystem.get(conf);

        // --- H2 in-memory DB for EnrichmentResolver ---
        dbConn = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (Statement stmt = dbConn.createStatement()) {
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
    void setUp() throws Exception {
        parentPath = tempDir.toAbsolutePath().toString();
        // Reset enrichment table before each test
        try (Statement stmt = dbConn.createStatement()) {
            stmt.execute("DELETE FROM dataset_enrichment");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (fs != null) {
            fs.close();
        }
        if (dbConn != null) {
            dbConn.close();
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void createDatasetDir(String srcSysNm) throws IOException {
        Path dir = new Path(parentPath, "partition_date=20260330/src_sys_nm=" + srcSysNm);
        fs.mkdirs(dir);
    }

    private void createFile(String srcSysNm, String fileName) throws IOException {
        Path filePath = new Path(parentPath,
                "partition_date=20260330/src_sys_nm=" + srcSysNm + "/" + fileName);
        fs.create(filePath).close();
    }

    private void insertEnrichment(String pattern, String lookupCode) throws Exception {
        try (PreparedStatement ps = dbConn.prepareStatement(
                "INSERT INTO dataset_enrichment(dataset_pattern, lookup_code) VALUES (?, ?)")) {
            ps.setString(1, pattern);
            ps.setString(2, lookupCode);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------------
    // AC1, AC3: Scanner with resolver enriches lookup code
    // ---------------------------------------------------------------------------

    /**
     * AC1 + AC3: When ConsumerZoneScanner is constructed with an EnrichmentResolver and
     * the resolver finds a matching pattern for "omni" -> "UE90", the returned DatasetContext
     * must carry lookupCode = "UE90", not the raw "omni".
     */
    @Test
    void scanUsesEnrichedLookupCodeWhenResolverProvided() throws Exception {
        // Seed enrichment record: "src_sys_nm=omni" pattern maps to UE90
        insertEnrichment("src_sys_nm=omni", "UE90");

        createDatasetDir("omni");
        createFile("omni", "part-00000.avro");

        EnrichmentResolver resolver = new EnrichmentResolver(dbConn);
        ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, resolver);

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        DatasetContext ctx = results.get(0);

        // lookupCode must be the enriched value, not raw "omni"
        assertEquals("UE90", ctx.getLookupCode(),
                "lookupCode should be enriched to UE90 when resolver finds matching pattern");

        // datasetName still uses raw HDFS directory name (not the enriched code)
        assertNotNull(ctx.getDatasetName());
        org.junit.jupiter.api.Assertions.assertTrue(
                ctx.getDatasetName().contains("src_sys_nm=omni"),
                "datasetName must still use the raw HDFS directory name, got: " + ctx.getDatasetName());
    }

    // ---------------------------------------------------------------------------
    // AC2: Scanner without resolver returns raw lookup code unchanged
    // ---------------------------------------------------------------------------

    /**
     * AC2: When ConsumerZoneScanner is constructed with the single-arg constructor
     * (no EnrichmentResolver), the raw src_sys_nm value is used as the lookup code.
     * This preserves backward-compatible behavior.
     */
    @Test
    void scanUsesRawLookupCodeWhenNoResolverProvided() throws Exception {
        // Single-arg constructor — enrichment disabled
        ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs);

        createDatasetDir("omni");
        createFile("omni", "part-00000.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals("omni", results.get(0).getLookupCode(),
                "Without resolver, lookupCode must be the raw src_sys_nm value");
    }

    // ---------------------------------------------------------------------------
    // Per-dataset isolation: SQLException from enrichment does not crash scanner
    // ---------------------------------------------------------------------------

    /**
     * When the EnrichmentResolver throws a SQLException (simulated via a closed connection),
     * the scanner must not crash — it must log the error and use the raw lookup code.
     * This validates the per-dataset isolation principle from project-context.md.
     */
    @Test
    void scanContinuesAfterEnrichmentSqlException() throws Exception {
        createDatasetDir("omni");
        createFile("omni", "part-00000.avro");
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");

        // Create a resolver backed by a connection that will throw SQLException
        Connection closedConn = DriverManager.getConnection(JDBC_URL, "sa", "");
        closedConn.close();  // Close immediately — resolve() will throw SQLException
        EnrichmentResolver faultyResolver = new EnrichmentResolver(closedConn);

        ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, faultyResolver);

        // Must not throw — per-dataset isolation requires catch and continue
        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        // Both datasets should be returned — scanner continues despite enrichment failure
        assertEquals(2, results.size(),
                "Scanner must continue and return all datasets even when enrichment fails");

        // Both datasets get raw lookup codes (fallback)
        List<String> codes = results.stream()
                .map(DatasetContext::getLookupCode)
                .sorted()
                .toList();
        assertEquals(List.of("cb10", "omni"), codes,
                "Raw lookup codes must be used as fallback when enrichment throws SQLException");
    }

    // ---------------------------------------------------------------------------
    // AC2: No enrichment match — raw code returned by resolver, scanner uses it
    // ---------------------------------------------------------------------------

    /**
     * AC2: When the resolver finds no enrichment record for the raw lookup code,
     * it returns the raw code unchanged. The scanner must use that raw code.
     */
    @Test
    void scanUsesRawCodeWhenResolverFindsNoMatch() throws Exception {
        // No enrichment records inserted

        createDatasetDir("legacy_system");
        createFile("legacy_system", "part-00000.parquet");

        EnrichmentResolver resolver = new EnrichmentResolver(dbConn);
        ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, resolver);

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals("legacy_system", results.get(0).getLookupCode(),
                "Raw lookup code should be used when resolver finds no matching enrichment record");
    }

    // ---------------------------------------------------------------------------
    // AC3: Multiple datasets — each resolved independently
    // ---------------------------------------------------------------------------

    /**
     * AC3: When multiple datasets are scanned and only some have enrichment records,
     * each is resolved independently: matched ones get enriched codes, unmatched ones
     * retain their raw codes.
     */
    @Test
    void scanResolvesEachDatasetIndependently() throws Exception {
        // omni -> UE90 (enriched); cb10 has no enrichment record (raw)
        insertEnrichment("src_sys_nm=omni", "UE90");

        createDatasetDir("omni");
        createFile("omni", "part-00000.avro");
        createDatasetDir("cb10");
        createFile("cb10", "part-00000.avro");

        EnrichmentResolver resolver = new EnrichmentResolver(dbConn);
        ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, resolver);

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(2, results.size());

        // Find by dataset name
        DatasetContext omniCtx = results.stream()
                .filter(c -> c.getDatasetName().contains("src_sys_nm=omni"))
                .findFirst()
                .orElseThrow();
        DatasetContext cb10Ctx = results.stream()
                .filter(c -> c.getDatasetName().contains("src_sys_nm=cb10"))
                .findFirst()
                .orElseThrow();

        assertEquals("UE90", omniCtx.getLookupCode(),
                "omni must be enriched to UE90");
        assertEquals("cb10", cb10Ctx.getLookupCode(),
                "cb10 must retain raw code when no enrichment record found");
    }

    // ---------------------------------------------------------------------------
    // New two-arg constructor: null resolver accepted (enrichment disabled)
    // ---------------------------------------------------------------------------

    /**
     * The two-arg constructor ConsumerZoneScanner(fs, null) is equivalent to the
     * single-arg constructor — enrichment is disabled, raw codes used.
     */
    @Test
    void scanWithNullResolverUsesRawLookupCode() throws Exception {
        // Two-arg constructor with explicit null resolver
        ConsumerZoneScanner scanner = new ConsumerZoneScanner(fs, null);

        createDatasetDir("alpha");
        createFile("alpha", "part-00000.avro");

        List<DatasetContext> results = scanner.scan(parentPath, PARTITION_DATE);

        assertEquals(1, results.size());
        assertEquals("alpha", results.get(0).getLookupCode(),
                "null resolver disables enrichment — raw lookup code used");
    }

    // ---------------------------------------------------------------------------
    // toString() updated — enrichment indicator
    // ---------------------------------------------------------------------------

    /**
     * ConsumerZoneScanner.toString() must indicate whether enrichment is enabled,
     * supporting debugging and log tracing.
     */
    @Test
    void toStringIndicatesEnrichmentEnabled() throws Exception {
        EnrichmentResolver resolver = new EnrichmentResolver(dbConn);
        ConsumerZoneScanner withResolver = new ConsumerZoneScanner(fs, resolver);
        ConsumerZoneScanner withoutResolver = new ConsumerZoneScanner(fs);

        String withStr = withResolver.toString();
        String withoutStr = withoutResolver.toString();

        // With resolver: toString should indicate enrichment is active
        org.junit.jupiter.api.Assertions.assertTrue(
                withStr.contains("enrichment") || withStr.contains("resolver") || withStr.contains("EnrichmentResolver"),
                "toString() with resolver should indicate enrichment enabled, got: " + withStr);

        // Without resolver: toString should indicate enrichment is disabled or absent
        // (either no mention, or explicit "null" / "disabled" indication)
        assertNotNull(withoutStr, "toString() must not return null");
    }
}
