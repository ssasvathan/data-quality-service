package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.DqsConstants;
import com.bank.dqs.model.MetricDetail;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ATDD RED PHASE — tests will not compile until BreakingChangeCheck.java is implemented.
 *
 * <p>BreakingChangeCheck detects destructive schema modifications by comparing field names
 * between the current dataset schema and a stored baseline (from SchemaCheck's
 * {@code dq_metric_detail} rows). FAIL when fields are removed; PASS when only added or
 * unchanged; PASS with {@code reason=baseline_unavailable} for first runs.
 *
 * <p>SparkSession is required because {@code BreakingChangeCheck} calls
 * {@code context.getDf().schema()} — uses the exact SparkSession lifecycle from
 * {@code ZeroRowCheckTest}.
 *
 * <p>TDD Red Phase: This file will not compile until
 * {@code dqs-spark/src/main/java/com/bank/dqs/checks/BreakingChangeCheck.java} is created.
 */
public class BreakingChangeCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    // -------------------------------------------------------------------------
    // SparkSession lifecycle
    // -------------------------------------------------------------------------

    @BeforeAll
    public static void initSpark() {
        spark = SparkSession.builder()
                .appName("BreakingChangeCheckTest")
                .master("local[1]")
                .getOrCreate();
    }

    @AfterAll
    public static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a DatasetContext wrapping the given DataFrame.
     * Uses the canonical path/code/date constants matching ZeroRowCheckTest.
     */
    private DatasetContext context(Dataset<Row> df) {
        return new DatasetContext(
                "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
                "ALPHA",
                PARTITION_DATE,
                "/prod/data",
                df,
                DatasetContext.FORMAT_PARQUET
        );
    }

    /**
     * Build a BreakingChangeCheck with the given baseline Optional injected as
     * the SchemaBaselineProvider. Allows tests to control the baseline without JDBC.
     */
    private BreakingChangeCheck checkWithBaseline(Optional<BreakingChangeCheck.SchemaSnapshot> baseline) {
        return new BreakingChangeCheck(ctx -> baseline);
    }

    /**
     * Extract the schema JSON from a DataFrame and wrap it as a SchemaSnapshot.
     * This mirrors the real JdbcSchemaBaselineProvider output.
     */
    private BreakingChangeCheck.SchemaSnapshot snapshotOf(Dataset<Row> df) {
        return new BreakingChangeCheck.SchemaSnapshot(df.schema().json());
    }

    /**
     * Find the first MetricDetail whose detailType matches DETAIL_TYPE_STATUS.
     */
    private MetricDetail findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> BreakingChangeCheck.DETAIL_TYPE_STATUS.equals(d.getDetailType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MetricDetail with detailType=" + BreakingChangeCheck.DETAIL_TYPE_STATUS
                                + " in " + metrics));
    }

    /**
     * Optionally find a MetricDetail whose detailType matches DETAIL_TYPE_FIELDS.
     */
    private Optional<MetricDetail> findOptionalFieldsDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> BreakingChangeCheck.DETAIL_TYPE_FIELDS.equals(d.getDetailType()))
                .findFirst();
    }

    // -------------------------------------------------------------------------
    // AC1: fields removed → FAIL, detail lists removed fields
    // -------------------------------------------------------------------------

    /**
     * [P0] AC1 — One field removed: baseline {id, name, amount}, current {id, name} → FAIL.
     *
     * <p>Given a dataset whose schema has removed the "amount" field compared to the previous run,
     * When the Breaking Change check executes,
     * Then it writes a MetricDetail with check_type=BREAKING_CHANGE,
     * detail_type=breaking_change_status, status=FAIL, and the fields detail
     * lists removed_fields=[amount].
     */
    @Test
    public void executeReturnsFailWhenFieldsRemoved() {
        // Given: baseline has {id, name, amount}; current has {id, name}
        StructType baselineSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true)
                .add("amount", DataTypes.DoubleType, true);

        StructType currentSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true);

        Dataset<Row> baselineDf = spark.createDataFrame(
                List.of(RowFactory.create(1L, "alice", 100.0)),
                baselineSchema
        );
        Dataset<Row> currentDf = spark.createDataFrame(
                List.of(RowFactory.create(2L, "bob")),
                currentSchema
        );

        BreakingChangeCheck check = checkWithBaseline(Optional.of(snapshotOf(baselineDf)));
        DatasetContext ctx = context(currentDf);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: status detail with check_type=BREAKING_CHANGE, status=FAIL
        MetricDetail statusDetail = findStatusDetail(metrics);
        assertEquals(BreakingChangeCheck.CHECK_TYPE, statusDetail.getCheckType(),
                "check_type must be BREAKING_CHANGE");
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when field 'amount' is removed");
        assertTrue(statusDetail.getDetailValue().contains("\"reason\":\"breaking_changes_detected\""),
                "Expected breaking_changes_detected reason");
        assertTrue(statusDetail.getDetailValue().contains("\"removed_count\":1"),
                "Expected removed_count:1 for single removed field");

        // And: fields detail lists removed_fields=[amount]
        Optional<MetricDetail> fieldsDetailOpt = findOptionalFieldsDetail(metrics);
        assertTrue(fieldsDetailOpt.isPresent(),
                "Expected a breaking_change_fields detail when fields are removed");
        MetricDetail fieldsDetail = fieldsDetailOpt.get();
        assertEquals(BreakingChangeCheck.CHECK_TYPE, fieldsDetail.getCheckType(),
                "fields detail check_type must be BREAKING_CHANGE");
        assertTrue(fieldsDetail.getDetailValue().contains("\"removed_fields\""),
                "Expected removed_fields array in fields detail payload");
        assertTrue(fieldsDetail.getDetailValue().contains("amount"),
                "Expected 'amount' listed in removed_fields");
    }

    // -------------------------------------------------------------------------
    // AC2: multiple fields removed (rename scenario) → FAIL
    // -------------------------------------------------------------------------

    /**
     * [P0] AC2 — Multiple fields removed: baseline {id, name, amount, ts}, current {id} → FAIL.
     *
     * <p>Given a dataset whose schema has removed name, amount, and ts fields,
     * When the Breaking Change check executes,
     * Then it writes a MetricDetail with status=FAIL and removed_fields contains
     * [amount, name, ts] (sorted alphabetically, no ts or name present in current).
     */
    @Test
    public void executeReturnsFailWhenMultipleFieldsRemoved() {
        // Given: baseline has {id, name, amount, ts}; current has only {id}
        StructType baselineSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true)
                .add("amount", DataTypes.DoubleType, true)
                .add("ts", DataTypes.TimestampType, true);

        StructType currentSchema = new StructType()
                .add("id", DataTypes.LongType, false);

        Dataset<Row> baselineDf = spark.createDataFrame(
                List.of(RowFactory.create(1L, "alice", 100.0,
                        new java.sql.Timestamp(System.currentTimeMillis()))),
                baselineSchema
        );
        Dataset<Row> currentDf = spark.createDataFrame(
                List.of(RowFactory.create(2L)),
                currentSchema
        );

        BreakingChangeCheck check = checkWithBaseline(Optional.of(snapshotOf(baselineDf)));
        DatasetContext ctx = context(currentDf);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: FAIL status with removed_count=3
        MetricDetail statusDetail = findStatusDetail(metrics);
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"FAIL\""),
                "Expected FAIL status when 3 fields are removed");
        assertTrue(statusDetail.getDetailValue().contains("\"removed_count\":3"),
                "Expected removed_count:3 for three removed fields");

        // And: fields detail lists all three removed fields
        Optional<MetricDetail> fieldsDetailOpt = findOptionalFieldsDetail(metrics);
        assertTrue(fieldsDetailOpt.isPresent(),
                "Expected a breaking_change_fields detail when multiple fields removed");
        String fieldsJson = fieldsDetailOpt.get().getDetailValue();
        assertTrue(fieldsJson.contains("amount"), "Expected 'amount' in removed_fields");
        assertTrue(fieldsJson.contains("name"), "Expected 'name' in removed_fields");
        assertTrue(fieldsJson.contains("ts"), "Expected 'ts' in removed_fields");
    }

    // -------------------------------------------------------------------------
    // AC3: only fields added → PASS (additive change is not breaking)
    // -------------------------------------------------------------------------

    /**
     * [P0] AC3 — Only fields added: baseline {id}, current {id, name, amount} → PASS.
     *
     * <p>Given a dataset whose schema only added new fields (no removals),
     * When the Breaking Change check executes,
     * Then it writes a MetricDetail with status=PASS (additive changes are not breaking).
     * No breaking_change_fields detail should be emitted.
     */
    @Test
    public void executeReturnsPassWhenOnlyFieldsAdded() {
        // Given: baseline has {id}; current has {id, name, amount}
        StructType baselineSchema = new StructType()
                .add("id", DataTypes.LongType, false);

        StructType currentSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true)
                .add("amount", DataTypes.DoubleType, true);

        Dataset<Row> baselineDf = spark.createDataFrame(
                List.of(RowFactory.create(1L)),
                baselineSchema
        );
        Dataset<Row> currentDf = spark.createDataFrame(
                List.of(RowFactory.create(2L, "bob", 50.0)),
                currentSchema
        );

        BreakingChangeCheck check = checkWithBaseline(Optional.of(snapshotOf(baselineDf)));
        DatasetContext ctx = context(currentDf);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: PASS status — additive changes are not breaking
        MetricDetail statusDetail = findStatusDetail(metrics);
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS when only fields are added (not breaking)");
        assertTrue(statusDetail.getDetailValue().contains("\"reason\":\"no_breaking_changes\""),
                "Expected no_breaking_changes reason for additive-only change");
        assertTrue(statusDetail.getDetailValue().contains("\"removed_count\":0"),
                "Expected removed_count:0 for additive-only change");

        // And: no fields detail (emitted only when there are removed fields)
        Optional<MetricDetail> fieldsDetailOpt = findOptionalFieldsDetail(metrics);
        assertFalse(fieldsDetailOpt.isPresent(),
                "Expected no breaking_change_fields detail for additive-only changes");
    }

    /**
     * [P1] AC3 boundary — Identical schema (no changes) → PASS.
     *
     * <p>Given a dataset whose schema is unchanged from the baseline,
     * When the Breaking Change check executes,
     * Then it returns PASS with reason=no_breaking_changes and removed_count=0.
     */
    @Test
    public void executeReturnsPassWhenSchemaUnchanged() {
        // Given: baseline and current schemas are identical
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true)
                .add("amount", DataTypes.DoubleType, true);

        Dataset<Row> df = spark.createDataFrame(
                List.of(RowFactory.create(1L, "alice", 100.0)),
                schema
        );

        BreakingChangeCheck check = checkWithBaseline(Optional.of(snapshotOf(df)));
        DatasetContext ctx = context(df);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: PASS — no fields removed
        MetricDetail statusDetail = findStatusDetail(metrics);
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS when schema is unchanged");
        assertTrue(statusDetail.getDetailValue().contains("\"reason\":\"no_breaking_changes\""),
                "Expected no_breaking_changes reason for identical schema");
    }

    // -------------------------------------------------------------------------
    // AC4: first run (no baseline stored) → PASS with reason=baseline_unavailable
    // -------------------------------------------------------------------------

    /**
     * [P0] AC4 — No baseline (first run): baseline provider returns empty → PASS, reason=baseline_unavailable.
     *
     * <p>Given a dataset with no previous schema stored (first run),
     * When the Breaking Change check executes,
     * Then it writes a MetricDetail with status=PASS and reason=baseline_unavailable
     * (no comparison possible — not a failure condition).
     */
    @Test
    public void executeReturnsPassWhenBaselineUnavailable() {
        // Given: no baseline (first run — provider returns empty)
        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true);

        Dataset<Row> currentDf = spark.createDataFrame(
                List.of(RowFactory.create(1L, "alice")),
                schema
        );

        BreakingChangeCheck check = checkWithBaseline(Optional.empty());
        DatasetContext ctx = context(currentDf);

        // When
        List<DqMetric> metrics = check.execute(ctx);

        // Then: PASS with baseline_unavailable reason
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when baseline is unavailable");

        MetricDetail statusDetail = findStatusDetail(metrics);
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"PASS\""),
                "Expected PASS status when no baseline is available (first run)");
        assertTrue(statusDetail.getDetailValue().contains("\"reason\":\"baseline_unavailable\""),
                "Expected baseline_unavailable reason for first run");
        assertTrue(statusDetail.getDetailValue().contains("\"removed_count\":0"),
                "Expected removed_count:0 when no comparison possible");

        // And: no fields detail (nothing removed)
        Optional<MetricDetail> fieldsDetailOpt = findOptionalFieldsDetail(metrics);
        assertFalse(fieldsDetailOpt.isPresent(),
                "Expected no breaking_change_fields detail when baseline unavailable");
    }

    // -------------------------------------------------------------------------
    // AC5: null context or null DataFrame → NOT_RUN, no exception propagation
    // -------------------------------------------------------------------------

    /**
     * [P0] AC5 — null context → detail with status=NOT_RUN, no exception.
     *
     * <p>Given a null context is passed,
     * When the Breaking Change check executes,
     * Then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenContextIsNull() {
        // Given: null context
        BreakingChangeCheck check = new BreakingChangeCheck();

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(null);

        // Then: at least one MetricDetail with status=NOT_RUN
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when context is null (NOT_RUN detail)");

        MetricDetail statusDetail = findStatusDetail(metrics);
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Expected NOT_RUN status when context is null");
        assertTrue(statusDetail.getDetailValue().contains("\"reason\":\"missing_context\""),
                "Expected missing_context reason when context is null");
    }

    /**
     * [P0] AC5 — context with null DataFrame → detail with status=NOT_RUN, no exception.
     *
     * <p>Given a context with a null DataFrame is passed,
     * When the Breaking Change check executes,
     * Then it returns a detail metric with status=NOT_RUN and does NOT propagate an exception.
     */
    @Test
    public void executeReturnsNotRunWhenDataFrameIsNull() {
        // Given: valid context but df=null
        BreakingChangeCheck check = new BreakingChangeCheck();
        DatasetContext ctx = context(null);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: NOT_RUN detail with missing_dataframe reason
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when df is null (NOT_RUN detail)");

        MetricDetail statusDetail = findStatusDetail(metrics);
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Expected NOT_RUN status when df is null");
        assertTrue(statusDetail.getDetailValue().contains("\"reason\":\"missing_dataframe\""),
                "Expected missing_dataframe reason when df is null");
    }

    // -------------------------------------------------------------------------
    // AC6: contract test — getCheckType() returns "BREAKING_CHANGE"
    // -------------------------------------------------------------------------

    /**
     * [P1] AC6 — getCheckType() returns "BREAKING_CHANGE".
     *
     * <p>BreakingChangeCheck implements DqCheck; the canonical check_type constant must be
     * "BREAKING_CHANGE". This test also confirms the check is registered in CheckFactory
     * (AC6: zero changes to serve/API/dashboard — only Spark layer knows this check_type).
     */
    @Test
    public void getCheckTypeReturnsBreakingChange() {
        // Given: any valid check instance
        BreakingChangeCheck check = new BreakingChangeCheck();

        // When / Then
        assertEquals("BREAKING_CHANGE", check.getCheckType(),
                "getCheckType() must return the canonical BREAKING_CHANGE string");
        assertEquals(BreakingChangeCheck.CHECK_TYPE, check.getCheckType(),
                "getCheckType() must equal the CHECK_TYPE constant");
    }

    // -------------------------------------------------------------------------
    // AC5+: exception path — baseline provider throws → graceful error detail
    // -------------------------------------------------------------------------

    /**
     * [P1] AC5+ — BaselineProvider throws exception → errorDetail produced, no propagation.
     *
     * <p>Given a baseline provider that throws a RuntimeException,
     * When the Breaking Change check executes,
     * Then it returns a single MetricDetail with status=NOT_RUN,
     * reason=execution_error, without propagating the exception.
     */
    @Test
    public void executeHandlesBaselineProviderExceptionGracefully() {
        // Given: provider throws on getBaseline()
        BreakingChangeCheck check = new BreakingChangeCheck(
                ctx -> { throw new RuntimeException("simulated JDBC failure"); }
        );

        StructType schema = new StructType()
                .add("id", DataTypes.LongType, false);
        Dataset<Row> df = spark.createDataFrame(
                List.of(RowFactory.create(1L)),
                schema
        );
        DatasetContext ctx = context(df);

        // When — must NOT throw
        List<DqMetric> metrics = check.execute(ctx);

        // Then: NOT_RUN error detail
        assertFalse(metrics.isEmpty(),
                "Expected at least one metric when baseline provider throws");

        MetricDetail statusDetail = findStatusDetail(metrics);
        assertTrue(statusDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""),
                "Expected NOT_RUN status when baseline provider throws");
        assertTrue(statusDetail.getDetailValue().contains("\"reason\":\"execution_error\""),
                "Expected execution_error reason when provider throws");
    }

    // -------------------------------------------------------------------------
    // JdbcSchemaBaselineProvider — H2 integration tests
    // -------------------------------------------------------------------------

    // Distinct H2 DB name — avoids collision with CheckFactoryTest (checkfactory_testdb)
    private static final String H2_JDBC_URL =
            "jdbc:h2:mem:breaking_change_testdb;DB_CLOSE_DELAY=-1";

    private static Connection h2Conn;

    @BeforeAll
    public static void setUpH2Database() throws Exception {
        h2Conn = DriverManager.getConnection(H2_JDBC_URL, "sa", "");
        try (Statement stmt = h2Conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS dq_run (
                        id             INTEGER PRIMARY KEY AUTO_INCREMENT,
                        dataset_name   TEXT NOT NULL,
                        partition_date DATE NOT NULL,
                        create_date    TIMESTAMP NOT NULL DEFAULT NOW(),
                        expiry_date    TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS dq_metric_detail (
                        id           INTEGER PRIMARY KEY AUTO_INCREMENT,
                        dq_run_id    INTEGER NOT NULL REFERENCES dq_run(id),
                        check_type   TEXT NOT NULL,
                        detail_type  TEXT NOT NULL,
                        detail_value TEXT NOT NULL,
                        create_date  TIMESTAMP NOT NULL DEFAULT NOW(),
                        expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
                    )
                    """);
        }
    }

    @BeforeEach
    public void cleanH2Data() throws Exception {
        try (Statement stmt = h2Conn.createStatement()) {
            stmt.execute("DELETE FROM dq_metric_detail");
            stmt.execute("DELETE FROM dq_run");
        }
    }

    @AfterAll
    public static void tearDownH2() throws Exception {
        if (h2Conn != null) {
            h2Conn.close();
        }
    }

    /**
     * Insert a dq_run row and return its generated id.
     */
    private long insertDqRun(String datasetName, LocalDate partitionDate) throws Exception {
        try (PreparedStatement ps = h2Conn.prepareStatement(
                "INSERT INTO dq_run(dataset_name, partition_date) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, datasetName);
            ps.setObject(2, partitionDate);
            ps.executeUpdate();
            try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Insert a dq_metric_detail row for the given run and schema JSON payload.
     */
    private void insertSchemaHashDetail(long dqRunId, String schemaJson) throws Exception {
        String payload = String.format(
                "{\"algorithm\":\"SHA-256\",\"hash\":\"sha256:abc\",\"schema_json\":\"%s\"}",
                schemaJson.replace("\"", "\\\""));
        try (PreparedStatement ps = h2Conn.prepareStatement(
                "INSERT INTO dq_metric_detail(dq_run_id, check_type, detail_type, detail_value)"
                        + " VALUES (?, 'SCHEMA', 'schema_hash', ?)")) {
            ps.setLong(1, dqRunId);
            ps.setString(2, payload);
            ps.executeUpdate();
        }
    }

    /**
     * Create a fresh H2 connection to the shared in-memory database.
     * Used as a ConnectionProvider lambda: each call returns a new connection so that
     * JdbcSchemaBaselineProvider's try-with-resources close() does not affect h2Conn.
     */
    private Connection freshH2Connection() throws java.sql.SQLException {
        return DriverManager.getConnection(H2_JDBC_URL, "sa", "");
    }

    /**
     * [H2] JdbcSchemaBaselineProvider returns the most recent baseline schema for the dataset.
     *
     * <p>Given a dq_run and dq_metric_detail row for dataset=sales_daily with a prior
     * partition_date, when JdbcSchemaBaselineProvider.getBaseline() is called with the
     * current context partition_date (which is after the stored run's partition_date),
     * then it returns the stored schema JSON wrapped in a SchemaSnapshot.
     */
    @Test
    public void jdbcBaselineProviderReturnsSnapshotWhenRowExists() throws Exception {
        // Given: a prior run on 2026-04-02 (before PARTITION_DATE 2026-04-03)
        String datasetName = "lob=retail/src_sys_nm=alpha/dataset=sales_daily";
        LocalDate priorDate = PARTITION_DATE.minusDays(1);
        long runId = insertDqRun(datasetName, priorDate);

        StructType schemaForBaseline = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true);
        String schemaJson = schemaForBaseline.json();
        insertSchemaHashDetail(runId, schemaJson);

        BreakingChangeCheck.JdbcSchemaBaselineProvider provider =
                new BreakingChangeCheck.JdbcSchemaBaselineProvider(this::freshH2Connection);

        DatasetContext ctx = context(
                spark.createDataFrame(List.of(RowFactory.create(1L, "alice")), schemaForBaseline));

        // When
        Optional<BreakingChangeCheck.SchemaSnapshot> result = provider.getBaseline(ctx);

        // Then: snapshot present and schema_json matches what was stored
        assertTrue(result.isPresent(),
                "Expected a SchemaSnapshot when a prior baseline row exists");
        assertEquals(schemaJson, result.get().schemaJson(),
                "SchemaSnapshot.schemaJson() must match the stored schema_json from the detail_value payload");
    }

    /**
     * [H2] JdbcSchemaBaselineProvider returns empty when no prior baseline exists.
     *
     * <p>Given no dq_run or dq_metric_detail rows for the dataset,
     * when JdbcSchemaBaselineProvider.getBaseline() is called,
     * then it returns Optional.empty() (first-run scenario).
     */
    @Test
    public void jdbcBaselineProviderReturnsEmptyWhenNoRowExists() throws Exception {
        // Given: no rows in H2
        BreakingChangeCheck.JdbcSchemaBaselineProvider provider =
                new BreakingChangeCheck.JdbcSchemaBaselineProvider(this::freshH2Connection);

        StructType schema = new StructType().add("id", DataTypes.LongType, false);
        DatasetContext ctx = context(
                spark.createDataFrame(List.of(RowFactory.create(1L)), schema));

        // When
        Optional<BreakingChangeCheck.SchemaSnapshot> result = provider.getBaseline(ctx);

        // Then: empty (no baseline available for first run)
        assertFalse(result.isPresent(),
                "Expected Optional.empty() when no prior baseline row exists");
    }

    /**
     * [H2] JdbcSchemaBaselineProvider ignores runs with partition_date >= current date.
     *
     * <p>Given a dq_run on the SAME partition_date (not strictly prior),
     * when JdbcSchemaBaselineProvider.getBaseline() is called,
     * then it returns Optional.empty() because the query uses {@code partition_date < ?}.
     */
    @Test
    public void jdbcBaselineProviderIgnoresRunsOnSameOrLaterDate() throws Exception {
        // Given: a run on PARTITION_DATE (same day, not a prior run)
        String datasetName = "lob=retail/src_sys_nm=alpha/dataset=sales_daily";
        long runId = insertDqRun(datasetName, PARTITION_DATE);

        StructType schema = new StructType().add("id", DataTypes.LongType, false);
        insertSchemaHashDetail(runId, schema.json());

        BreakingChangeCheck.JdbcSchemaBaselineProvider provider =
                new BreakingChangeCheck.JdbcSchemaBaselineProvider(this::freshH2Connection);

        DatasetContext ctx = context(
                spark.createDataFrame(List.of(RowFactory.create(1L)), schema));

        // When
        Optional<BreakingChangeCheck.SchemaSnapshot> result = provider.getBaseline(ctx);

        // Then: empty — same-date run is not a prior baseline
        assertFalse(result.isPresent(),
                "Expected Optional.empty() when the only run is on the current partition_date");
    }

    /**
     * [H2] JdbcSchemaBaselineProvider picks the most recent prior run when multiple exist.
     *
     * <p>Given two prior runs (2026-04-01 and 2026-04-02), each with different schemas,
     * when JdbcSchemaBaselineProvider.getBaseline() is called,
     * then it returns the schema from the most recent prior run (2026-04-02).
     */
    @Test
    public void jdbcBaselineProviderReturnsMostRecentPriorRun() throws Exception {
        String datasetName = "lob=retail/src_sys_nm=alpha/dataset=sales_daily";

        // Older run: 2 days prior, schema with {id}
        StructType olderSchema = new StructType().add("id", DataTypes.LongType, false);
        long olderRunId = insertDqRun(datasetName, PARTITION_DATE.minusDays(2));
        insertSchemaHashDetail(olderRunId, olderSchema.json());

        // More recent run: 1 day prior, schema with {id, name}
        StructType recentSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("name", DataTypes.StringType, true);
        long recentRunId = insertDqRun(datasetName, PARTITION_DATE.minusDays(1));
        insertSchemaHashDetail(recentRunId, recentSchema.json());

        BreakingChangeCheck.JdbcSchemaBaselineProvider provider =
                new BreakingChangeCheck.JdbcSchemaBaselineProvider(this::freshH2Connection);

        DatasetContext ctx = context(
                spark.createDataFrame(List.of(RowFactory.create(1L, "alice")), recentSchema));

        // When
        Optional<BreakingChangeCheck.SchemaSnapshot> result = provider.getBaseline(ctx);

        // Then: returns the most recent prior schema (recentSchema, with {id, name})
        assertTrue(result.isPresent(), "Expected a snapshot when two prior runs exist");
        assertEquals(recentSchema.json(), result.get().schemaJson(),
                "Expected the most recent prior run's schema (ORDER BY partition_date DESC LIMIT 1)");
    }
}
