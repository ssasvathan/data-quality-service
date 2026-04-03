package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckFactoryTest {

    private static Connection conn;
    private CheckFactory factory;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        conn = DriverManager.getConnection("jdbc:h2:mem:checkfactory_testdb;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS check_config (" +
                "    id              INTEGER PRIMARY KEY AUTO_INCREMENT," +
                "    dataset_pattern TEXT NOT NULL," +
                "    check_type      TEXT NOT NULL," +
                "    enabled         BOOLEAN NOT NULL DEFAULT TRUE," +
                "    explosion_level INTEGER NOT NULL DEFAULT 0," +
                "    create_date     TIMESTAMP NOT NULL DEFAULT NOW()," +
                "    expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'" +
                ")"
            );
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        factory = new CheckFactory();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM check_config");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        conn.close();
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Stub DqCheck — only getCheckType() is meaningful for factory tests. */
    private DqCheck stubCheck(String type) {
        return new DqCheck() {
            @Override
            public String getCheckType() { return type; }

            @Override
            public List<DqMetric> execute(DatasetContext ctx) { return List.of(); }
        };
    }

    /** DatasetContext with null df — factory tests do not need SparkSession. */
    private DatasetContext ctx(String datasetName) {
        return new DatasetContext(
                datasetName,
                "LOB_TEST",
                LocalDate.of(2026, 4, 3),
                "/prod/data",
                null,
                DatasetContext.FORMAT_PARQUET
        );
    }

    private void insertConfig(String pattern, String checkType, boolean enabled) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO check_config(dataset_pattern, check_type, enabled) VALUES (?, ?, ?)")) {
            ps.setString(1, pattern);
            ps.setString(2, checkType);
            ps.setBoolean(3, enabled);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------------
    // AC4: Registration
    // ---------------------------------------------------------------------------

    @Test
    void registerAddsCheckWithoutError() {
        assertDoesNotThrow(() -> factory.register(stubCheck("FRESHNESS")));
    }

    @Test
    void registerMultipleChecksAddsAll() {
        assertDoesNotThrow(() -> {
            factory.register(stubCheck("FRESHNESS"));
            factory.register(stubCheck("VOLUME"));
            factory.register(stubCheck("SCHEMA"));
            factory.register(stubCheck("OPS"));
        });
    }

    @Test
    void registerFreshnessCheckImplementationAddsRealCheck() {
        assertDoesNotThrow(() -> factory.register(new FreshnessCheck()));
    }

    @Test
    void registerVolumeCheckImplementationAddsRealCheck() {
        assertDoesNotThrow(() -> factory.register(new VolumeCheck()));
    }

    @Test
    void registerSchemaCheckImplementationAddsRealCheck() {
        assertDoesNotThrow(() -> factory.register(new SchemaCheck()));
    }

    @Test
    void registerOpsCheckImplementationAddsRealCheck() {
        assertDoesNotThrow(() -> factory.register(new OpsCheck()));
    }

    @Test
    void registerThrowsOnNullCheck() {
        assertThrows(IllegalArgumentException.class, () -> factory.register(null));
    }

    // ---------------------------------------------------------------------------
    // AC4 + AC5: getEnabledChecks — happy paths
    // ---------------------------------------------------------------------------

    @Test
    void getEnabledChecksReturnsSingleMatchingCheck() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        insertConfig("lob=retail/%", "FRESHNESS", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertEquals("FRESHNESS", result.get(0).getCheckType());
    }

    @Test
    void getEnabledChecksReturnsRegisteredFreshnessCheckImplementation() throws Exception {
        factory.register(new FreshnessCheck());
        insertConfig("lob=retail/%", "FRESHNESS", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof FreshnessCheck);
        assertEquals("FRESHNESS", result.get(0).getCheckType());
    }

    @Test
    void getEnabledChecksReturnsRegisteredVolumeCheckImplementation() throws Exception {
        factory.register(new VolumeCheck());
        insertConfig("lob=retail/%", "VOLUME", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof VolumeCheck);
        assertEquals("VOLUME", result.get(0).getCheckType());
    }

    @Test
    void getEnabledChecksReturnsRegisteredSchemaCheckImplementation() throws Exception {
        factory.register(new SchemaCheck());
        insertConfig("lob=retail/%", "SCHEMA", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof SchemaCheck);
        assertEquals("SCHEMA", result.get(0).getCheckType());
    }

    @Test
    void getEnabledChecksReturnsRegisteredOpsCheckImplementation() throws Exception {
        factory.register(new OpsCheck());
        insertConfig("lob=retail/%", "OPS", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertTrue(result.get(0) instanceof OpsCheck);
        assertEquals("OPS", result.get(0).getCheckType());
    }

    @Test
    void getEnabledChecksReturnsMultipleMatchingChecks() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        factory.register(stubCheck("VOLUME"));
        insertConfig("lob=retail/%", "FRESHNESS", true);
        insertConfig("lob=retail/%", "VOLUME", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(2, result.size());
    }

    // ---------------------------------------------------------------------------
    // AC5: Disabled filtering
    // ---------------------------------------------------------------------------

    @Test
    void getEnabledChecksExcludesDisabledRows() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        factory.register(stubCheck("VOLUME"));
        insertConfig("lob=retail/%", "FRESHNESS", true);
        insertConfig("lob=retail/%", "VOLUME", false);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertEquals("FRESHNESS", result.get(0).getCheckType());
    }

    // ---------------------------------------------------------------------------
    // AC5: LIKE pattern matching
    // ---------------------------------------------------------------------------

    @Test
    void getEnabledChecksReturnsEmptyWhenNoPatternMatches() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        insertConfig("lob=commercial/%", "FRESHNESS", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertTrue(result.isEmpty());
    }

    @Test
    void getEnabledChecksUsesWildcardPatternMatching() throws Exception {
        factory.register(stubCheck("VOLUME"));
        // Wildcard pattern that covers all datasets
        insertConfig("%", "VOLUME", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertEquals(1, result.size());
        assertEquals("VOLUME", result.get(0).getCheckType());
    }

    @Test
    void getEnabledChecksMatchesSpecificDatasetPattern() throws Exception {
        factory.register(stubCheck("OPS"));
        insertConfig("lob=retail/src_sys_nm=alpha/%", "OPS", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=customers"), conn);

        assertEquals(1, result.size());
    }

    // ---------------------------------------------------------------------------
    // AC5: Unregistered check type
    // ---------------------------------------------------------------------------

    @Test
    void getEnabledChecksIgnoresUnregisteredCheckTypes() throws Exception {
        // DB has SCHEMA config but SCHEMA check is not registered in factory
        insertConfig("lob=retail/%", "SCHEMA", true);

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertTrue(result.isEmpty());
    }

    // ---------------------------------------------------------------------------
    // AC5: EXPIRY_SENTINEL filtering (expired rows excluded)
    // ---------------------------------------------------------------------------

    @Test
    void getEnabledChecksExcludesExpiredConfigRows() throws Exception {
        factory.register(stubCheck("FRESHNESS"));
        // Insert an expired (soft-deleted) check_config row
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO check_config(dataset_pattern, check_type, enabled, expiry_date) " +
                "VALUES (?, ?, ?, '2026-01-01 00:00:00')")) {
            ps.setString(1, "lob=retail/%");
            ps.setString(2, "FRESHNESS");
            ps.setBoolean(3, true);
            ps.executeUpdate();
        }

        List<DqCheck> result = factory.getEnabledChecks(
                ctx("lob=retail/src_sys_nm=alpha/dataset=sales_daily"), conn);

        assertTrue(result.isEmpty());
    }
}
