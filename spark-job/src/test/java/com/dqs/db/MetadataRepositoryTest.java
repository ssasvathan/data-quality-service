package com.dqs.db;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MetadataRepositoryTest {

    private Connection conn;
    private MetadataRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:dqstest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS dataset (
                    dataset_id SERIAL PRIMARY KEY,
                    src_sys_nm VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_dataset UNIQUE(src_sys_nm)
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS dq_run (
                    run_id SERIAL PRIMARY KEY,
                    dataset_id INTEGER REFERENCES dataset(dataset_id),
                    run_timestamp TIMESTAMP NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    duration_ms BIGINT
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS dq_check_result (
                    check_id SERIAL PRIMARY KEY,
                    run_id INTEGER REFERENCES dq_run(run_id),
                    dataset_id INTEGER REFERENCES dataset(dataset_id),
                    check_type VARCHAR(100) NOT NULL,
                    column_name VARCHAR(255),
                    status VARCHAR(50) NOT NULL,
                    failure_reason TEXT
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS dq_metric_snapshot (
                    metric_id SERIAL PRIMARY KEY,
                    run_id INTEGER REFERENCES dq_run(run_id),
                    dataset_id INTEGER REFERENCES dataset(dataset_id),
                    metric_name VARCHAR(100) NOT NULL,
                    column_name VARCHAR(255),
                    metric_value NUMERIC NOT NULL
                )""");
        }
        repo = new MetadataRepository(conn);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }
        conn.close();
    }

    @Test
    void upsertDatasetCreatesNewRecord() throws SQLException {
        int id = repo.upsertDataset("MY_SYS");
        assertTrue(id > 0);
    }

    @Test
    void upsertDatasetIsIdempotent() throws SQLException {
        int id1 = repo.upsertDataset("MY_SYS");
        int id2 = repo.upsertDataset("MY_SYS");
        assertEquals(id1, id2);
    }

    @Test
    void openAndCloseRun() throws SQLException {
        int datasetId = repo.upsertDataset("MY_SYS");
        int runId = repo.openRun(datasetId);
        assertTrue(runId > 0);

        repo.closeRun(runId, "PASSED", 1234L);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status, duration_ms FROM dq_run WHERE run_id=?")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("PASSED", rs.getString("status"));
            assertEquals(1234L, rs.getLong("duration_ms"));
        }
    }

    @Test
    void insertCheckResults() throws SQLException {
        int datasetId = repo.upsertDataset("MY_SYS");
        int runId = repo.openRun(datasetId);

        List<CheckResult> results = List.of(
            new CheckResult("rowCount", null, "PASSED", null),
            new CheckResult("rowCount", "amount", "FAILED", "Row count 5 is below minimum 10")
        );
        repo.insertCheckResults(runId, datasetId, results);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM dq_check_result WHERE run_id=?")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    void insertMetrics() throws SQLException {
        int datasetId = repo.upsertDataset("MY_SYS");
        int runId = repo.openRun(datasetId);

        List<MetricSnapshot> metrics = List.of(
            new MetricSnapshot("rowCount", null, 42.0)
        );
        repo.insertMetrics(runId, datasetId, metrics);

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT metric_value FROM dq_metric_snapshot WHERE run_id=?")) {
            ps.setInt(1, runId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(42.0, rs.getDouble("metric_value"), 0.001);
        }
    }
}
