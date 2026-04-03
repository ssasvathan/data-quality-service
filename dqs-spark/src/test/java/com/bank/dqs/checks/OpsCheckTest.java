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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    @BeforeAll
    static void initSpark() {
        spark = SparkSession.builder()
                .appName("OpsCheckTest")
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

    private OpsCheck checkWithBaselines(Map<String, OpsCheck.BaselineStats> baselines) {
        OpsCheck.BaselineProvider provider = ctx -> baselines;
        return new OpsCheck(provider);
    }

    private MetricNumeric findNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst()
                .orElseThrow();
    }

    private Optional<MetricNumeric> findOptionalNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst();
    }

    private MetricDetail findDetail(List<DqMetric> metrics, String detailType) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> detailType.equals(d.getDetailType()))
                .findFirst()
                .orElseThrow();
    }

    private StructType baseSchema() {
        return new StructType()
                .add("id", DataTypes.LongType, true)
                .add("status", DataTypes.StringType, true);
    }

    @Test
    void executeComputesPerColumnNullRateMetrics() {
        OpsCheck check = checkWithBaselines(Map.of());
        Dataset<Row> input = df(
                baseSchema(),
                RowFactory.create(1L, "ok"),
                RowFactory.create(2L, ""),
                RowFactory.create(null, "active"),
                RowFactory.create(4L, null)
        );

        List<DqMetric> metrics = check.execute(context(input));

        MetricNumeric idNullRate = findNumericMetric(metrics, "null_rate_pct::id");
        MetricNumeric statusNullRate = findNumericMetric(metrics, "null_rate_pct::status");
        assertEquals("OPS", idNullRate.getCheckType());
        assertEquals(25.0d, idNullRate.getMetricValue(), 0.001d);
        assertEquals(25.0d, statusNullRate.getMetricValue(), 0.001d);
    }

    @Test
    void executeComputesEmptyStringRatesForStringColumnsOnly() {
        OpsCheck check = checkWithBaselines(Map.of());
        Dataset<Row> input = df(
                baseSchema(),
                RowFactory.create(1L, "ok"),
                RowFactory.create(2L, ""),
                RowFactory.create(3L, "   "),
                RowFactory.create(4L, null)
        );

        List<DqMetric> metrics = check.execute(context(input));

        MetricNumeric statusEmptyRate = findNumericMetric(metrics, "empty_string_rate_pct::status");
        assertEquals(50.0d, statusEmptyRate.getMetricValue(), 0.001d);
        assertTrue(findOptionalNumericMetric(metrics, "empty_string_rate_pct::id").isEmpty());
    }

    @Test
    void executeClassifiesWarnWhenNullRateExceedsWarnThreshold() {
        OpsCheck.BaselineStats baseline = new OpsCheck.BaselineStats(10.0d, 2.0d, 30);
        OpsCheck check = checkWithBaselines(Map.of("status", baseline));
        Dataset<Row> input = df(
                baseSchema(),
                RowFactory.create(1L, "a"),
                RowFactory.create(2L, "b"),
                RowFactory.create(3L, "c"),
                RowFactory.create(4L, null),
                RowFactory.create(5L, "d"),
                RowFactory.create(6L, "e"),
                RowFactory.create(7L, "f")
        );

        List<DqMetric> metrics = check.execute(context(input));

        MetricDetail status = findDetail(metrics, "ops_status");
        MetricDetail anomalies = findDetail(metrics, "ops_anomalies");
        assertTrue(status.getDetailValue().contains("\"status\":\"WARN\""));
        assertTrue(anomalies.getDetailValue().contains("\"column\":\"status\""));
        assertTrue(anomalies.getDetailValue().contains("\"severity\":\"warn\""));
        assertTrue(anomalies.getDetailValue().contains("\"reason\":\"above_warn_threshold\""));
    }

    @Test
    void executeClassifiesFailWhenNullRateExceedsFailThreshold() {
        OpsCheck.BaselineStats baseline = new OpsCheck.BaselineStats(5.0d, 2.0d, 30);
        OpsCheck check = checkWithBaselines(Map.of("status", baseline));
        Dataset<Row> input = df(
                baseSchema(),
                RowFactory.create(1L, "ok"),
                RowFactory.create(2L, "ok"),
                RowFactory.create(3L, null),
                RowFactory.create(4L, "ok"),
                RowFactory.create(5L, "ok")
        );

        List<DqMetric> metrics = check.execute(context(input));

        MetricDetail status = findDetail(metrics, "ops_status");
        MetricDetail anomalies = findDetail(metrics, "ops_anomalies");
        assertTrue(status.getDetailValue().contains("\"status\":\"FAIL\""));
        assertTrue(anomalies.getDetailValue().contains("\"column\":\"status\""));
        assertTrue(anomalies.getDetailValue().contains("\"severity\":\"fail\""));
        assertTrue(anomalies.getDetailValue().contains("\"reason\":\"above_fail_threshold\""));
    }

    @Test
    void executeFlagsAllNullColumnsAsCriticalAnomalies() {
        OpsCheck check = checkWithBaselines(Map.of());
        Dataset<Row> input = df(
                baseSchema(),
                RowFactory.create(1L, null),
                RowFactory.create(2L, null),
                RowFactory.create(3L, null)
        );

        List<DqMetric> metrics = check.execute(context(input));

        MetricNumeric statusNullRate = findNumericMetric(metrics, "null_rate_pct::status");
        MetricDetail status = findDetail(metrics, "ops_status");
        MetricDetail anomalies = findDetail(metrics, "ops_anomalies");
        assertEquals(100.0d, statusNullRate.getMetricValue(), 0.001d);
        assertTrue(status.getDetailValue().contains("\"status\":\"FAIL\""));
        assertTrue(anomalies.getDetailValue().contains("\"column\":\"status\""));
        assertTrue(anomalies.getDetailValue().contains("\"severity\":\"critical\""));
        assertTrue(anomalies.getDetailValue().contains("\"reason\":\"all_null_column\""));
    }

    @Test
    void executeUsesBaselineInitializationModeWhenHistoryUnavailable() {
        OpsCheck check = checkWithBaselines(Map.of());
        Dataset<Row> input = df(
                baseSchema(),
                RowFactory.create(1L, "ok"),
                RowFactory.create(2L, null),
                RowFactory.create(3L, "ok"),
                RowFactory.create(4L, "ok")
        );

        List<DqMetric> metrics = check.execute(context(input));

        MetricDetail status = findDetail(metrics, "ops_status");
        MetricDetail anomalies = findDetail(metrics, "ops_anomalies");
        assertTrue(status.getDetailValue().contains("\"status\":\"PASS\""));
        assertTrue(status.getDetailValue().contains("\"reason\":\"baseline_unavailable\""));
        assertTrue(status.getDetailValue().contains("\"flagged_columns\":0"));
        assertTrue(anomalies.getDetailValue().contains("[]"));
    }

    @Test
    void executeReturnsNotRunForMissingContextOrDataFrame() {
        OpsCheck check = checkWithBaselines(Map.of());

        List<DqMetric> nullContextMetrics = check.execute(null);
        MetricDetail nullContextDetail = findDetail(nullContextMetrics, "ops_status");
        assertTrue(nullContextDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(nullContextDetail.getDetailValue().contains("\"reason\":\"missing_context\""));

        List<DqMetric> nullDfMetrics = check.execute(context(null));
        MetricDetail nullDfDetail = findDetail(nullDfMetrics, "ops_status");
        assertTrue(nullDfDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(nullDfDetail.getDetailValue().contains("\"reason\":\"missing_dataframe\""));
    }

    @Test
    void executeSanitizesExecutionErrorPayload() {
        OpsCheck check = new OpsCheck(ctx -> {
            throw new RuntimeException("raw dataset values must not be exposed");
        });
        Dataset<Row> input = df(
                baseSchema(),
                RowFactory.create(1L, "ok")
        );

        List<DqMetric> metrics = check.execute(context(input));
        MetricDetail status = findDetail(metrics, "ops_status");

        assertTrue(status.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(status.getDetailValue().contains("\"reason\":\"execution_error\""));
        assertTrue(status.getDetailValue().contains("\"error_type\":\"RuntimeException\""));
        assertFalse(status.getDetailValue().contains("raw dataset values must not be exposed"));
    }

    @Test
    void getCheckTypeReturnsOps() {
        OpsCheck check = checkWithBaselines(Map.of());
        assertEquals("OPS", check.getCheckType());
    }
}
