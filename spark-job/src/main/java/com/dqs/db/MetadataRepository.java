package com.dqs.db;

import java.sql.*;
import java.util.List;

public class MetadataRepository implements AutoCloseable {
    private final Connection conn;

    public MetadataRepository(Connection conn) {
        this.conn = conn;
    }

    /**
     * Inserts a dataset row if src_sys_nm is new; returns the existing dataset_id if it already exists.
     * Uses SELECT-then-INSERT to remain compatible with both H2 (tests) and PostgreSQL (production).
     */
    public int upsertDataset(String srcSysNm) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT dataset_id FROM dataset WHERE src_sys_nm=?")) {
            sel.setString(1, srcSysNm);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("dataset_id");
                }
            }
        }
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO dataset(src_sys_nm) VALUES(?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ins.setString(1, srcSysNm);
            ins.executeUpdate();
            try (ResultSet gk = ins.getGeneratedKeys()) {
                gk.next();
                return gk.getInt(1);
            }
        }
    }

    /** Opens a new dq_run record with status RUNNING and returns its run_id. */
    public int openRun(int datasetId) throws SQLException {
        String sql = "INSERT INTO dq_run(dataset_id, run_timestamp, status) VALUES(?, NOW(), 'RUNNING')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, datasetId);
            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                gk.next();
                return gk.getInt(1);
            }
        }
    }

    /** Closes a dq_run by updating its status and duration. */
    public void closeRun(int runId, String status, long durationMs) throws SQLException {
        String sql = "UPDATE dq_run SET status=?, duration_ms=? WHERE run_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, durationMs);
            ps.setInt(3, runId);
            ps.executeUpdate();
        }
    }

    /** Batch-inserts check results for a run. */
    public void insertCheckResults(int runId, int datasetId, List<CheckResult> results) throws SQLException {
        String sql = "INSERT INTO dq_check_result(run_id, dataset_id, check_type, column_name, status, failure_reason)" +
                     " VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CheckResult r : results) {
                ps.setInt(1, runId);
                ps.setInt(2, datasetId);
                ps.setString(3, r.getCheckType());
                ps.setString(4, r.getColumnName());
                ps.setString(5, r.getStatus());
                ps.setString(6, r.getFailureReason());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Batch-inserts metric snapshots for a run. */
    public void insertMetrics(int runId, int datasetId, List<MetricSnapshot> metrics) throws SQLException {
        String sql = "INSERT INTO dq_metric_snapshot(run_id, dataset_id, metric_name, column_name, metric_value)" +
                     " VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (MetricSnapshot m : metrics) {
                ps.setInt(1, runId);
                ps.setInt(2, datasetId);
                ps.setString(3, m.getMetricName());
                ps.setString(4, m.getColumnName());
                ps.setDouble(5, m.getMetricValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public java.util.List<Double> getHistoricalMetricValues(int datasetId, String metricName, int limit) throws SQLException {
        String sql = "SELECT m.metric_value FROM dq_metric_snapshot m JOIN dq_run r ON m.run_id = r.run_id " +
                     "WHERE m.dataset_id = ? AND m.metric_name = ? ORDER BY r.run_timestamp DESC LIMIT ?";
        java.util.List<Double> values = new java.util.ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            ps.setString(2, metricName);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) values.add(rs.getDouble(1));
            }
        }
        return values;
    }

    public Double getLatestMetricValue(int datasetId, String metricName) throws SQLException {
        java.util.List<Double> vals = getHistoricalMetricValues(datasetId, metricName, 1);
        return vals.isEmpty() ? null : vals.get(0);
    }
    
    public java.sql.Timestamp getLastSuccessfulRun(int datasetId) throws SQLException {
        String sql = "SELECT run_timestamp FROM dq_run WHERE dataset_id = ? AND status = 'PASSED' ORDER BY run_timestamp DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, datasetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getTimestamp(1);
            }
        }
        return null;
    }

    @Override
    public void close() throws Exception {
        conn.close();
    }
}
