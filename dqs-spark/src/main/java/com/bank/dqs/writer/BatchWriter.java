package com.bank.dqs.writer;

import com.bank.dqs.checks.DqsScoreCheck;
import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

/**
 * BatchWriter — pure JDBC batch writer for DQ check results.
 *
 * <p>Writes to three tables in a single JDBC transaction:
 * <ul>
 *   <li>{@code dq_run} — one record per dataset per run</li>
 *   <li>{@code dq_metric_numeric} — one row per {@link MetricNumeric} in the metrics list</li>
 *   <li>{@code dq_metric_detail} — one row per {@link MetricDetail} in the metrics list</li>
 * </ul>
 *
 * <p>BatchWriter does NOT close the supplied {@link Connection} — callers must use
 * try-with-resources around the connection lifecycle.
 *
 * <p>BatchWriter declares {@code throws SQLException} on its public method — never
 * catch-and-swallow. The caller ({@code DqsJob}) handles per-dataset failure isolation.
 *
 * <p>No Spark dependency — this class is a pure JDBC component.
 */
public class BatchWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String INSERT_DQ_RUN =
            "INSERT INTO dq_run "
            + "(dataset_name, partition_date, lookup_code, check_status, dqs_score, "
            + "rerun_number, orchestration_run_id, error_message) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_METRIC_NUMERIC =
            "INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value) "
            + "VALUES (?, ?, ?, ?)";

    private static final String INSERT_METRIC_DETAIL_POSTGRES =
            "INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value) "
            + "VALUES (?, ?, ?, CAST(? AS JSONB))";

    private static final String INSERT_METRIC_DETAIL_H2 =
            "INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value) "
            + "VALUES (?, ?, ?, ?)";

    private final Connection conn;
    private final boolean isPostgres;

    /**
     * Constructs a {@code BatchWriter} with the given JDBC connection.
     *
     * @param conn the JDBC connection to use — must not be null
     * @throws IllegalArgumentException if conn is null
     */
    public BatchWriter(Connection conn) {
        if (conn == null) {
            throw new IllegalArgumentException("conn must not be null");
        }
        this.conn = conn;
        boolean postgres = false;
        try {
            postgres = conn.getMetaData().getDatabaseProductName()
                    .toLowerCase().contains("postgresql");
        } catch (SQLException ignored) {
            // Connection may be closed or metadata unavailable — default to non-postgres
        }
        this.isPostgres = postgres;
    }

    /**
     * Writes all check results for one dataset in a single JDBC transaction.
     *
     * <p>Execution order within the transaction:
     * <ol>
     *   <li>INSERT into {@code dq_run} — derives {@code check_status} and {@code dqs_score}
     *       from the metrics list</li>
     *   <li>Batch INSERT into {@code dq_metric_numeric} (if any MetricNumeric entries)</li>
     *   <li>Batch INSERT into {@code dq_metric_detail} (if any MetricDetail entries)</li>
     * </ol>
     *
     * <p>On any failure the transaction is rolled back and the exception is rethrown.
     *
     * @param ctx     the dataset context (name, partition date, lookup code)
     * @param metrics all DQ metrics produced for this dataset in this run
     * @return the auto-generated {@code dq_run.id}
     * @throws SQLException if any DB operation fails
     */
    public long write(DatasetContext ctx, List<DqMetric> metrics) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            long runId = insertDqRun(ctx, metrics);
            insertMetricNumericBatch(runId, metrics);
            insertMetricDetailBatch(runId, metrics);
            conn.commit();
            return runId;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                // Rollback failed (e.g., connection already closed) — attach as suppressed
                e.addSuppressed(rollbackEx);
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
                // Best-effort restore — connection may already be invalid
            }
        }
    }

    // ---------------------------------------------------------------------------
    // dq_run INSERT
    // ---------------------------------------------------------------------------

    private long insertDqRun(DatasetContext ctx, List<DqMetric> metrics) throws SQLException {
        String checkStatus = deriveCheckStatus(metrics);
        Double dqsScore = deriveDqsScore(metrics);

        try (PreparedStatement ps = conn.prepareStatement(INSERT_DQ_RUN,
                Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, ctx.getDatasetName());
            ps.setDate(2, java.sql.Date.valueOf(ctx.getPartitionDate()));

            if (ctx.getLookupCode() != null) {
                ps.setString(3, ctx.getLookupCode());
            } else {
                ps.setNull(3, Types.VARCHAR);
            }

            ps.setString(4, checkStatus);

            if (dqsScore != null) {
                ps.setDouble(5, dqsScore);
            } else {
                ps.setNull(5, Types.DECIMAL);
            }

            // TODO(story-3-4): increment rerun_number for reruns
            ps.setInt(6, 0);

            // TODO(epic-3): set orchestration_run_id once assigned by orchestrator
            ps.setNull(7, Types.INTEGER);

            ps.setNull(8, Types.VARCHAR);

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                } else {
                    throw new SQLException("INSERT into dq_run did not return a generated key");
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // dq_metric_numeric batch INSERT
    // ---------------------------------------------------------------------------

    private void insertMetricNumericBatch(long runId, List<DqMetric> metrics) throws SQLException {
        List<MetricNumeric> numerics = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .toList();

        if (numerics.isEmpty()) {
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement(INSERT_METRIC_NUMERIC)) {
            for (MetricNumeric m : numerics) {
                ps.setLong(1, runId);
                ps.setString(2, m.getCheckType());
                ps.setString(3, m.getMetricName());
                ps.setDouble(4, m.getMetricValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------------------------------------------------------------------------
    // dq_metric_detail batch INSERT
    // ---------------------------------------------------------------------------

    private void insertMetricDetailBatch(long runId, List<DqMetric> metrics) throws SQLException {
        List<MetricDetail> details = metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .toList();

        if (details.isEmpty()) {
            return;
        }

        String sql = isPostgres ? INSERT_METRIC_DETAIL_POSTGRES : INSERT_METRIC_DETAIL_H2;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (MetricDetail m : details) {
                ps.setLong(1, runId);
                ps.setString(2, m.getCheckType());
                ps.setString(3, m.getDetailType());
                if (m.getDetailValue() != null) {
                    ps.setString(4, m.getDetailValue());
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------------------------------------------------------------------------
    // check_status and dqs_score derivation
    // ---------------------------------------------------------------------------

    /**
     * Derives {@code check_status} by parsing the {@code status} field from the
     * {@code DQS_SCORE} {@code dqs_score_breakdown} {@link MetricDetail}.
     * Falls back to {@code "UNKNOWN"} if the detail is absent or unparseable.
     */
    private String deriveCheckStatus(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(m -> DqsScoreCheck.CHECK_TYPE.equals(m.getCheckType())
                        && "dqs_score_breakdown".equals(m.getDetailType()))
                .findFirst()
                .map(m -> extractStatusFromJson(m.getDetailValue()))
                .orElse("UNKNOWN");
    }

    /**
     * Derives {@code dqs_score} from the {@code DQS_SCORE} {@code composite_score}
     * {@link MetricNumeric}. Returns {@code null} if not present.
     */
    private Double deriveDqsScore(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> DqsScoreCheck.CHECK_TYPE.equals(m.getCheckType())
                        && "composite_score".equals(m.getMetricName()))
                .findFirst()
                .map(MetricNumeric::getMetricValue)
                .orElse(null);
    }

    /**
     * Parses the {@code "status"} field from a JSON string.
     * Returns {@code "UNKNOWN"} on any parse failure.
     */
    private String extractStatusFromJson(String json) {
        if (json == null || json.isBlank()) {
            return "UNKNOWN";
        }
        try {
            Map<String, Object> payload =
                    OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            Object statusObj = payload.get("status");
            if (statusObj == null) {
                return "UNKNOWN";
            }
            return String.valueOf(statusObj);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
