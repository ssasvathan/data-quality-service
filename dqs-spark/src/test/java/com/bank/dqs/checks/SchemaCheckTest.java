package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    @BeforeAll
    static void initSpark() {
        spark = SparkSession.builder()
                .appName("SchemaCheckTest")
                .master("local[1]")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    private Dataset<Row> df(StructType schema, Row... rows) {
        return spark.createDataFrame(List.of(rows), schema);
    }

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

    private SchemaCheck checkWithBaseline(Optional<SchemaCheck.BaselineSnapshot> baseline) {
        SchemaCheck.BaselineProvider provider = ctx -> baseline;
        return new SchemaCheck(provider);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private MetricNumeric findNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst()
                .orElseThrow();
    }

    private MetricDetail findDetail(List<DqMetric> metrics, String detailType) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> detailType.equals(d.getDetailType()))
                .findFirst()
                .orElseThrow();
    }

    private Optional<MetricDetail> findOptionalDetail(List<DqMetric> metrics, String detailType) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> detailType.equals(d.getDetailType()))
                .findFirst();
    }

    @Test
    void executePassesAndStoresBaselineWhenNoPreviousHash() {
        StructType currentSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("status", DataTypes.StringType, true);
        SchemaCheck check = checkWithBaseline(Optional.empty());

        List<DqMetric> metrics = check.execute(context(df(currentSchema, RowFactory.create(1L, "ok"))));

        MetricNumeric added = findNumericMetric(metrics, "added_fields_count");
        MetricNumeric removed = findNumericMetric(metrics, "removed_fields_count");
        MetricNumeric changed = findNumericMetric(metrics, "changed_fields_count");
        assertEquals("SCHEMA", added.getCheckType());
        assertEquals(0.0d, added.getMetricValue(), 0.0d);
        assertEquals(0.0d, removed.getMetricValue(), 0.0d);
        assertEquals(0.0d, changed.getMetricValue(), 0.0d);

        MetricDetail status = findDetail(metrics, "schema_status");
        assertTrue(status.getDetailValue().contains("\"status\":\"PASS\""));
        assertTrue(status.getDetailValue().contains("\"reason\":\"baseline_unavailable\""));
        assertTrue(status.getDetailValue().contains("\"added_count\":0"));
        assertTrue(status.getDetailValue().contains("\"removed_count\":0"));
        assertTrue(status.getDetailValue().contains("\"changed_count\":0"));

        MetricDetail hash = findDetail(metrics, "schema_hash");
        assertTrue(hash.getDetailValue().contains("\"algorithm\":\"SHA-256\""));
        assertTrue(hash.getDetailValue().contains("\"hash\":\"sha256:"));
        assertTrue(hash.getDetailValue().contains("\"schema_json\":"));

        assertTrue(findOptionalDetail(metrics, "schema_diff").isEmpty());
    }

    @Test
    void executePassesWhenSchemaHashMatchesBaseline() {
        StructType currentSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("status", DataTypes.StringType, true);
        String baselineSchemaJson = currentSchema.json();
        String baselineHash = sha256(baselineSchemaJson);
        SchemaCheck.BaselineSnapshot baseline =
                new SchemaCheck.BaselineSnapshot(baselineHash, baselineSchemaJson);
        SchemaCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(df(currentSchema, RowFactory.create(2L, "ok"))));

        MetricNumeric added = findNumericMetric(metrics, "added_fields_count");
        MetricNumeric removed = findNumericMetric(metrics, "removed_fields_count");
        MetricNumeric changed = findNumericMetric(metrics, "changed_fields_count");
        assertEquals(0.0d, added.getMetricValue(), 0.0d);
        assertEquals(0.0d, removed.getMetricValue(), 0.0d);
        assertEquals(0.0d, changed.getMetricValue(), 0.0d);

        MetricDetail status = findDetail(metrics, "schema_status");
        assertTrue(status.getDetailValue().contains("\"status\":\"PASS\""));
        assertTrue(status.getDetailValue().contains("\"reason\":\"no_schema_drift\""));
        assertTrue(status.getDetailValue().contains("\"baseline_hash\":\"" + baselineHash + "\""));

        assertTrue(findOptionalDetail(metrics, "schema_diff").isEmpty());
    }

    @Test
    void executeWarnsAndListsAddedFieldsWhenDriftIsAddOnly() {
        StructType baselineSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("status", DataTypes.StringType, true);
        StructType currentSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("status", DataTypes.StringType, true)
                .add("new_field", DataTypes.StringType, true);

        String baselineSchemaJson = baselineSchema.json();
        SchemaCheck.BaselineSnapshot baseline =
                new SchemaCheck.BaselineSnapshot(sha256(baselineSchemaJson), baselineSchemaJson);
        SchemaCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(
                context(df(currentSchema, RowFactory.create(3L, "ok", "new")))
        );

        MetricNumeric added = findNumericMetric(metrics, "added_fields_count");
        MetricNumeric removed = findNumericMetric(metrics, "removed_fields_count");
        MetricNumeric changed = findNumericMetric(metrics, "changed_fields_count");
        assertEquals(1.0d, added.getMetricValue(), 0.0d);
        assertEquals(0.0d, removed.getMetricValue(), 0.0d);
        assertEquals(0.0d, changed.getMetricValue(), 0.0d);

        MetricDetail status = findDetail(metrics, "schema_status");
        assertTrue(status.getDetailValue().contains("\"status\":\"WARN\""));
        assertTrue(status.getDetailValue().contains("\"reason\":\"schema_additive_change\""));

        MetricDetail diff = findDetail(metrics, "schema_diff");
        assertTrue(diff.getDetailValue().contains("\"added_fields\""));
        assertTrue(diff.getDetailValue().contains("new_field"));
        assertTrue(diff.getDetailValue().contains("\"removed_fields\":[]"));
        assertTrue(diff.getDetailValue().contains("\"changed_fields\":[]"));
    }

    @Test
    void executeFailsWhenFieldsRemovedOrChanged() {
        StructType baselineSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("status", DataTypes.StringType, true);
        StructType currentSchema = new StructType()
                .add("id", DataTypes.StringType, false);

        String baselineSchemaJson = baselineSchema.json();
        SchemaCheck.BaselineSnapshot baseline =
                new SchemaCheck.BaselineSnapshot(sha256(baselineSchemaJson), baselineSchemaJson);
        SchemaCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(df(currentSchema, RowFactory.create("4"))));

        MetricNumeric added = findNumericMetric(metrics, "added_fields_count");
        MetricNumeric removed = findNumericMetric(metrics, "removed_fields_count");
        MetricNumeric changed = findNumericMetric(metrics, "changed_fields_count");
        assertEquals(0.0d, added.getMetricValue(), 0.0d);
        assertEquals(1.0d, removed.getMetricValue(), 0.0d);
        assertEquals(1.0d, changed.getMetricValue(), 0.0d);

        MetricDetail status = findDetail(metrics, "schema_status");
        assertTrue(status.getDetailValue().contains("\"status\":\"FAIL\""));
        assertTrue(status.getDetailValue().contains("\"reason\":\"schema_breaking_change\""));

        MetricDetail diff = findDetail(metrics, "schema_diff");
        assertTrue(diff.getDetailValue().contains("\"removed_fields\""));
        assertTrue(diff.getDetailValue().contains("status"));
        assertTrue(diff.getDetailValue().contains("\"changed_fields\""));
        assertTrue(diff.getDetailValue().contains("id"));
    }

    @Test
    void executeFailsWhenBaselineHashDiffersButSchemaJsonMissing() {
        StructType currentSchema = new StructType()
                .add("id", DataTypes.LongType, false)
                .add("status", DataTypes.StringType, true);
        String baselineHash = sha256("{\"type\":\"struct\",\"fields\":[]}");

        SchemaCheck.BaselineSnapshot baseline =
                new SchemaCheck.BaselineSnapshot(baselineHash, null);
        SchemaCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(df(currentSchema, RowFactory.create(9L, "ok"))));

        MetricNumeric added = findNumericMetric(metrics, "added_fields_count");
        MetricNumeric removed = findNumericMetric(metrics, "removed_fields_count");
        MetricNumeric changed = findNumericMetric(metrics, "changed_fields_count");
        assertEquals(0.0d, added.getMetricValue(), 0.0d);
        assertEquals(0.0d, removed.getMetricValue(), 0.0d);
        assertEquals(1.0d, changed.getMetricValue(), 0.0d);

        MetricDetail status = findDetail(metrics, "schema_status");
        assertTrue(status.getDetailValue().contains("\"status\":\"FAIL\""));
        assertTrue(status.getDetailValue().contains("\"reason\":\"schema_breaking_change\""));
        assertTrue(status.getDetailValue().contains("\"baseline_hash\":\"" + baselineHash + "\""));

        MetricDetail diff = findDetail(metrics, "schema_diff");
        assertTrue(diff.getDetailValue().contains("\"field\":\"$schema\""));
        assertTrue(diff.getDetailValue().contains("hash=" + baselineHash));
    }

    @Test
    void executeReturnsNotRunForMissingContextOrDataFrame() {
        SchemaCheck check = checkWithBaseline(Optional.empty());

        List<DqMetric> nullContextMetrics = check.execute(null);
        MetricDetail nullContextDetail = findDetail(nullContextMetrics, "schema_status");
        assertTrue(nullContextDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(nullContextDetail.getDetailValue().contains("\"reason\":\"missing_context\""));

        List<DqMetric> nullDfMetrics = check.execute(context(null));
        MetricDetail nullDfDetail = findDetail(nullDfMetrics, "schema_status");
        assertTrue(nullDfDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(nullDfDetail.getDetailValue().contains("\"reason\":\"missing_dataframe\""));
    }

    @Test
    void executeSanitizesExecutionErrorPayload() {
        SchemaCheck check = new SchemaCheck(ctx -> {
            throw new RuntimeException("sensitive payload should not leak");
        });

        StructType currentSchema = new StructType().add("id", DataTypes.LongType, false);
        List<DqMetric> metrics = check.execute(context(df(currentSchema, RowFactory.create(5L))));
        MetricDetail detail = findDetail(metrics, "schema_status");

        assertTrue(detail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"execution_error\""));
        assertTrue(detail.getDetailValue().contains("\"error_type\":\"RuntimeException\""));
        assertFalse(detail.getDetailValue().contains("sensitive payload should not leak"));
    }

    @Test
    void getCheckTypeReturnsSchema() {
        SchemaCheck check = checkWithBaseline(Optional.empty());
        assertEquals("SCHEMA", check.getCheckType());
    }
}
