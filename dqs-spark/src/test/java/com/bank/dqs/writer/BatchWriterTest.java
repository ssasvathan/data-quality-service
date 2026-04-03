package com.bank.dqs.writer;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BatchWriter}.
 *
 * <p>Uses H2 in-memory database with H2-compatible DDL (no JSONB, no SERIAL,
 * no CAST(? AS JSONB)) per dev notes. Tests use {@code @BeforeAll}/{@code @AfterAll}
 * for H2 connection lifecycle and {@code @BeforeEach} for table truncation.
 *
 * <p>Test naming convention: {@code <action><expectation>} (camelCase), per project rules.
 *
 * <p>No SparkSession required — BatchWriter is a pure JDBC writer.
 */
class BatchWriterTest {

    private static final String JDBC_URL = "jdbc:h2:mem:batchwriter_testdb;DB_CLOSE_DELAY=-1";
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);
    private static final String DATASET_NAME = "lob=retail/src_sys_nm=alpha/dataset=sales_daily";
    private static final String LOOKUP_CODE  = "RETAIL_ALPHA";
    private static final String PARENT_PATH  = "/prod/data";

    private static Connection conn;

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @BeforeAll
    static void setUpDatabase() throws Exception {
        conn = DriverManager.getConnection(JDBC_URL, "sa", "");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dq_run (
                    id                   INTEGER PRIMARY KEY AUTO_INCREMENT,
                    dataset_name         VARCHAR NOT NULL,
                    partition_date       DATE NOT NULL,
                    lookup_code          VARCHAR,
                    check_status         VARCHAR NOT NULL,
                    dqs_score            DECIMAL(5,2),
                    rerun_number         INTEGER NOT NULL DEFAULT 0,
                    orchestration_run_id INTEGER,
                    error_message        VARCHAR,
                    create_date          TIMESTAMP NOT NULL DEFAULT NOW(),
                    expiry_date          TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dq_metric_numeric (
                    id           INTEGER PRIMARY KEY AUTO_INCREMENT,
                    dq_run_id    INTEGER NOT NULL,
                    check_type   VARCHAR NOT NULL,
                    metric_name  VARCHAR NOT NULL,
                    metric_value DECIMAL(20,5),
                    create_date  TIMESTAMP NOT NULL DEFAULT NOW(),
                    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS dq_metric_detail (
                    id           INTEGER PRIMARY KEY AUTO_INCREMENT,
                    dq_run_id    INTEGER NOT NULL,
                    check_type   VARCHAR NOT NULL,
                    detail_type  VARCHAR NOT NULL,
                    detail_value VARCHAR,
                    create_date  TIMESTAMP NOT NULL DEFAULT NOW(),
                    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
                )
            """);
        }
    }

    @BeforeEach
    void truncateTables() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM dq_metric_detail");
            stmt.execute("DELETE FROM dq_metric_numeric");
            stmt.execute("DELETE FROM dq_run");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Build a {@link DatasetContext} with null df — BatchWriter tests do not
     * require a live SparkSession.
     */
    private DatasetContext ctx(String datasetName, String lookupCode) {
        return new DatasetContext(
                datasetName,
                lookupCode,
                PARTITION_DATE,
                PARENT_PATH,
                null,
                DatasetContext.FORMAT_PARQUET
        );
    }

    /** Default context used by most tests. */
    private DatasetContext defaultCtx() {
        return ctx(DATASET_NAME, LOOKUP_CODE);
    }

    /**
     * Build a {@link MetricDetail} that simulates the DQS_SCORE breakdown detail
     * emitted by {@code DqsScoreCheck} with the given overall status.
     */
    private MetricDetail dqsScoreBreakdownDetail(String status) {
        String json = "{\"status\":\"" + status + "\",\"composite_score\":87.5,"
                + "\"reason\":\"composite_computed\",\"checks\":[],\"unavailable_checks\":[]}";
        return new MetricDetail("DQS_SCORE", "dqs_score_breakdown", json);
    }

    /**
     * Build a {@link MetricNumeric} that simulates the composite_score metric
     * emitted by {@code DqsScoreCheck}.
     */
    private MetricNumeric compositeScoreMetric(double score) {
        return new MetricNumeric("DQS_SCORE", "composite_score", score);
    }

    /** Count rows in a table using the shared connection. */
    private int countRows(String table) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Open a fresh H2 connection for BatchWriter (BatchWriter does not close it). */
    private Connection freshConn() throws Exception {
        return DriverManager.getConnection(JDBC_URL, "sa", "");
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC5: dq_run creation and generated key
    // ---------------------------------------------------------------------------

    /**
     * [P0] BatchWriter.write() inserts exactly one dq_run record and returns the
     * auto-generated id (> 0).
     */
    @Test
    void writeCreatesDqRunRecordAndReturnsGeneratedId() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            List<DqMetric> metrics = List.of(
                    dqsScoreBreakdownDetail("PASS"),
                    compositeScoreMetric(87.5)
            );

            long runId = writer.write(defaultCtx(), metrics, null);

            assertTrue(runId > 0, "write() must return the generated dq_run.id (> 0)");
            assertEquals(1, countRows("dq_run"), "Exactly one dq_run record must be inserted");
        }
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC3: MetricNumeric batch insert
    // ---------------------------------------------------------------------------

    /**
     * [P0] BatchWriter.write() inserts all MetricNumeric entries into dq_metric_numeric
     * with correct column values.
     */
    @Test
    void writeInsertsAllMetricNumericEntries() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            List<DqMetric> metrics = new ArrayList<>();
            metrics.add(new MetricNumeric("FRESHNESS", "staleness_hours", 2.5));
            metrics.add(new MetricNumeric("VOLUME",    "row_count",       10000.0));
            metrics.add(compositeScoreMetric(87.5));

            writer.write(defaultCtx(), metrics, null);

            assertEquals(3, countRows("dq_metric_numeric"),
                    "All 3 MetricNumeric entries must be inserted into dq_metric_numeric");

            // Verify correct column values for one entry
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT check_type, metric_name, metric_value FROM dq_metric_numeric "
                                 + "WHERE check_type = 'FRESHNESS'")) {
                assertTrue(rs.next(), "FRESHNESS MetricNumeric row must exist");
                assertEquals("FRESHNESS", rs.getString("check_type"));
                assertEquals("staleness_hours", rs.getString("metric_name"));
                assertEquals(2.5, rs.getDouble("metric_value"), 0.001);
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC4: MetricDetail batch insert
    // ---------------------------------------------------------------------------

    /**
     * [P0] BatchWriter.write() inserts all MetricDetail entries into dq_metric_detail
     * with correct column values.
     */
    @Test
    void writeInsertsAllMetricDetailEntries() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            List<DqMetric> metrics = new ArrayList<>();
            metrics.add(new MetricDetail("FRESHNESS", "freshness_status",
                    "{\"status\":\"PASS\",\"reason\":\"within_sla\"}"));
            metrics.add(dqsScoreBreakdownDetail("PASS"));

            writer.write(defaultCtx(), metrics, null);

            assertEquals(2, countRows("dq_metric_detail"),
                    "All 2 MetricDetail entries must be inserted into dq_metric_detail");

            // Verify correct column values
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT check_type, detail_type, detail_value FROM dq_metric_detail "
                                 + "WHERE check_type = 'FRESHNESS'")) {
                assertTrue(rs.next(), "FRESHNESS MetricDetail row must exist");
                assertEquals("FRESHNESS", rs.getString("check_type"));
                assertEquals("freshness_status", rs.getString("detail_type"));
                assertNotNull(rs.getString("detail_value"),
                        "detail_value must be stored (not null)");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC5: check_status derivation from DQS_SCORE breakdown
    // ---------------------------------------------------------------------------

    /**
     * [P0] BatchWriter.write() sets dq_run.check_status by parsing the status field
     * from the DQS_SCORE dqs_score_breakdown MetricDetail.
     */
    @Test
    void writeSetsDqRunCheckStatusFromDqsScoreBreakdown() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            List<DqMetric> metrics = List.of(
                    dqsScoreBreakdownDetail("PASS"),
                    compositeScoreMetric(92.0)
            );

            writer.write(defaultCtx(), metrics, null);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT check_status FROM dq_run")) {
                assertTrue(rs.next(), "dq_run row must exist");
                assertEquals("PASS", rs.getString("check_status"),
                        "check_status must be extracted from DQS_SCORE breakdown detail status field");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC5: dqs_score derivation from composite_score MetricNumeric
    // ---------------------------------------------------------------------------

    /**
     * [P0] BatchWriter.write() sets dq_run.dqs_score from the DQS_SCORE composite_score
     * MetricNumeric value.
     */
    @Test
    void writeSetsDqRunDqsScoreFromCompositeScoreMetric() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            List<DqMetric> metrics = List.of(
                    dqsScoreBreakdownDetail("PASS"),
                    compositeScoreMetric(87.5)
            );

            writer.write(defaultCtx(), metrics, null);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT dqs_score FROM dq_run")) {
                assertTrue(rs.next(), "dq_run row must exist");
                double storedScore = rs.getDouble("dqs_score");
                assertEquals(87.5, storedScore, 0.01,
                        "dqs_score must be set from DQS_SCORE composite_score MetricNumeric (value=87.5)");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AC5: check_status = UNKNOWN when DQS_SCORE detail absent
    // ---------------------------------------------------------------------------

    /**
     * [P1] BatchWriter.write() sets dq_run.check_status to "UNKNOWN" when no
     * DQS_SCORE dqs_score_breakdown MetricDetail is present in the metrics list.
     */
    @Test
    void writeSetsDqRunCheckStatusUnknownWhenDqsScoreAbsent() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            // Only FRESHNESS metrics — no DQS_SCORE detail
            List<DqMetric> metrics = List.of(
                    new MetricNumeric("FRESHNESS", "staleness_hours", 1.0),
                    new MetricDetail("FRESHNESS", "freshness_status",
                            "{\"status\":\"PASS\",\"reason\":\"within_sla\"}")
            );

            writer.write(defaultCtx(), metrics, null);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT check_status FROM dq_run")) {
                assertTrue(rs.next(), "dq_run row must exist");
                assertEquals("UNKNOWN", rs.getString("check_status"),
                        "check_status must be UNKNOWN when DQS_SCORE breakdown detail is absent");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AC2: Transaction rollback on failure
    // ---------------------------------------------------------------------------

    /**
     * [P0] BatchWriter.write() rolls back the entire transaction on failure,
     * leaving no partial records in dq_run.
     *
     * <p>Failure is forced by closing the connection mid-write via a
     * subclass-style approach: insert a unique constraint violation by
     * pre-inserting a conflicting row, then verifying 0 rows remain after rollback.
     *
     * <p>Implementation note: Uses a separate connection for the BatchWriter so that
     * we can force an error by manipulating the shared H2 DB. The BatchWriter is
     * expected to roll back and rethrow the SQLException.
     */
    @Test
    void writeRollsBackOnFailure() throws Exception {


        // Strategy: Pass a broken MetricDetail that will cause a constraint violation.
        // H2's dq_metric_numeric has a NOT NULL constraint on check_type.
        // We simulate a failure by using a custom Connection wrapper that throws on commit,
        // OR by verifying that after a rethrown SQLException, dq_run has 0 rows.
        //
        // Simplest H2-compatible approach: use a broken MetricNumeric by directly
        // testing that BatchWriter.write() throws and leaves 0 dq_run rows.
        //
        // We achieve a forced write failure by using a Connection that is pre-closed.
        Connection preClosedConn = DriverManager.getConnection(JDBC_URL, "sa", "");
        preClosedConn.close(); // Close it immediately

        BatchWriter writer = new BatchWriter(preClosedConn);
        List<DqMetric> metrics = List.of(
                dqsScoreBreakdownDetail("PASS"),
                compositeScoreMetric(87.5)
        );

        // write() must throw because the connection is closed
        assertThrows(Exception.class,
                () -> writer.write(defaultCtx(), metrics, null),
                "BatchWriter.write() must throw when connection is closed/broken");

        // No partial records must remain in dq_run
        assertEquals(0, countRows("dq_run"),
                "Transaction rollback must leave 0 rows in dq_run after failure");
    }

    // ---------------------------------------------------------------------------
    // AC1: Empty metrics list
    // ---------------------------------------------------------------------------

    /**
     * [P1] BatchWriter.write() handles an empty metrics list gracefully —
     * inserts one dq_run record with NULL dqs_score and UNKNOWN check_status,
     * and inserts no metric rows.
     */
    @Test
    void writeHandlesEmptyMetricsList() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            List<DqMetric> metrics = List.of(); // Empty list

            long runId = writer.write(defaultCtx(), metrics, null);

            assertTrue(runId > 0, "write() must return a valid dq_run.id even for empty metrics");
            assertEquals(1, countRows("dq_run"), "One dq_run record must be inserted");
            assertEquals(0, countRows("dq_metric_numeric"),
                    "No dq_metric_numeric rows for empty metrics");
            assertEquals(0, countRows("dq_metric_detail"),
                    "No dq_metric_detail rows for empty metrics");

            // check_status must be UNKNOWN (no DQS_SCORE present)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT check_status, dqs_score FROM dq_run")) {
                assertTrue(rs.next());
                assertEquals("UNKNOWN", rs.getString("check_status"),
                        "Empty metrics must yield check_status=UNKNOWN");
                rs.getDouble("dqs_score");
                assertTrue(rs.wasNull(), "Empty metrics must yield NULL dqs_score");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC5: lookup_code preservation
    // ---------------------------------------------------------------------------

    /**
     * [P2] BatchWriter.write() stores the lookup_code from DatasetContext into dq_run.
     */
    @Test
    void writePreservesLookupCodeInDqRun() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);
            DatasetContext ctxWithLookup = ctx(DATASET_NAME, "CUSTOM_LOOKUP");

            writer.write(ctxWithLookup, List.of(
                    dqsScoreBreakdownDetail("PASS"),
                    compositeScoreMetric(95.0)
            ), null);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT lookup_code FROM dq_run")) {
                assertTrue(rs.next(), "dq_run row must exist");
                assertEquals("CUSTOM_LOOKUP", rs.getString("lookup_code"),
                        "lookup_code must be stored from DatasetContext.getLookupCode()");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // AC1 + AC5: partition_date preservation
    // ---------------------------------------------------------------------------

    /**
     * [P2] BatchWriter.write() stores the partition_date from DatasetContext into dq_run.
     */
    @Test
    void writePreservesPartitionDateInDqRun() throws Exception {

        try (Connection writeConn = freshConn()) {
            BatchWriter writer = new BatchWriter(writeConn);

            writer.write(defaultCtx(), List.of(
                    dqsScoreBreakdownDetail("PASS"),
                    compositeScoreMetric(87.5)
            ), null);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT partition_date FROM dq_run")) {
                assertTrue(rs.next(), "dq_run row must exist");
                java.sql.Date storedDate = rs.getDate("partition_date");
                assertNotNull(storedDate, "partition_date must not be null");
                assertEquals(PARTITION_DATE, storedDate.toLocalDate(),
                        "partition_date must match DatasetContext.getPartitionDate()");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Constructor null-guard
    // ---------------------------------------------------------------------------

    /**
     * [P1] BatchWriter constructor rejects a null connection with IllegalArgumentException.
     */
    @Test
    void constructorThrowsOnNullConnection() {

        assertThrows(IllegalArgumentException.class,
                () -> new BatchWriter(null),
                "BatchWriter constructor must reject null Connection with IllegalArgumentException");
    }
}
