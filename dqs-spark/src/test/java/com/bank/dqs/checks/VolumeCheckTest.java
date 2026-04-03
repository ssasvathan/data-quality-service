package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    @BeforeAll
    static void initSpark() {
        spark = SparkSession.builder()
                .appName("VolumeCheckTest")
                .master("local[1]")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    private Dataset<Row> dfWithRows(long rowCount) {
        return spark.range(rowCount).toDF("id");
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

    private VolumeCheck checkWithBaseline(Optional<VolumeCheck.BaselineStats> baseline) {
        VolumeCheck.BaselineProvider provider = ctx -> baseline;
        return new VolumeCheck(provider);
    }

    private MetricNumeric findNumericMetric(List<DqMetric> metrics, String metricName) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> metricName.equals(m.getMetricName()))
                .findFirst()
                .orElseThrow();
    }

    private MetricDetail findStatusDetail(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .filter(d -> "volume_status".equals(d.getDetailType()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void executeComputesRowCountAndPctChangeMetricsWhenBaselinePresent() {
        VolumeCheck.BaselineStats baseline = new VolumeCheck.BaselineStats(100.0, 10.0, 30);
        VolumeCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(dfWithRows(104)));

        MetricNumeric rowCount = findNumericMetric(metrics, "row_count");
        MetricNumeric pctChange = findNumericMetric(metrics, "pct_change");
        MetricNumeric rowCountStdDev = findNumericMetric(metrics, "row_count_stddev");

        assertEquals("VOLUME", rowCount.getCheckType());
        assertEquals(104.0, rowCount.getMetricValue(), 0.001);
        assertEquals(4.0, pctChange.getMetricValue(), 0.001);
        assertEquals(10.0, rowCountStdDev.getMetricValue(), 0.001);

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"PASS\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"within_baseline\""));
    }

    @Test
    void executeClassifiesFailWhenDeviationExceedsTwoStdDev() {
        VolumeCheck.BaselineStats baseline = new VolumeCheck.BaselineStats(100.0, 10.0, 30);
        VolumeCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(dfWithRows(130)));

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"FAIL\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"above_fail_threshold\""));
    }

    @Test
    void executeClassifiesWarnWhenDeviationExceedsWarnThresholdButNotFailThreshold() {
        VolumeCheck.BaselineStats baseline = new VolumeCheck.BaselineStats(100.0, 10.0, 30);
        VolumeCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(dfWithRows(115)));

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"WARN\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"above_warn_threshold\""));
    }

    @Test
    void executeUsesBaselineInitializationModeWhenHistoryUnavailable() {
        VolumeCheck check = checkWithBaseline(Optional.empty());

        List<DqMetric> metrics = check.execute(context(dfWithRows(75)));

        MetricNumeric rowCount = findNumericMetric(metrics, "row_count");
        MetricNumeric pctChange = findNumericMetric(metrics, "pct_change");
        MetricNumeric rowCountStdDev = findNumericMetric(metrics, "row_count_stddev");
        MetricDetail detail = findStatusDetail(metrics);

        assertEquals(75.0, rowCount.getMetricValue(), 0.001);
        assertEquals(0.0, pctChange.getMetricValue(), 0.0);
        assertEquals(0.0, rowCountStdDev.getMetricValue(), 0.0);
        assertTrue(detail.getDetailValue().contains("\"status\":\"PASS\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"baseline_unavailable\""));
    }

    @Test
    void executeKeepsObservedBaselineMetadataWhenHistoryIsInsufficient() {
        VolumeCheck.BaselineStats baseline = new VolumeCheck.BaselineStats(42.0, 0.0, 1);
        VolumeCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(dfWithRows(50)));

        MetricNumeric pctChange = findNumericMetric(metrics, "pct_change");
        MetricNumeric rowCountStdDev = findNumericMetric(metrics, "row_count_stddev");
        MetricDetail detail = findStatusDetail(metrics);

        assertEquals(0.0, pctChange.getMetricValue(), 0.0);
        assertEquals(0.0, rowCountStdDev.getMetricValue(), 0.0);
        assertTrue(detail.getDetailValue().contains("\"status\":\"PASS\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"baseline_unavailable\""));
        assertTrue(detail.getDetailValue().contains("\"baseline_mean_row_count\":42.0"));
        assertTrue(detail.getDetailValue().contains("\"baseline_count\":1"));
    }

    @Test
    void executeHandlesZeroMeanBaselineWithoutDivisionByZero() {
        VolumeCheck.BaselineStats baseline = new VolumeCheck.BaselineStats(0.0, 0.0, 30);
        VolumeCheck check = checkWithBaseline(Optional.of(baseline));

        List<DqMetric> metrics = check.execute(context(dfWithRows(5)));

        MetricNumeric pctChange = findNumericMetric(metrics, "pct_change");
        assertEquals(0.0, pctChange.getMetricValue(), 0.0);
        assertFalse(Double.isNaN(pctChange.getMetricValue()));
        assertFalse(Double.isInfinite(pctChange.getMetricValue()));

        MetricDetail detail = findStatusDetail(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"FAIL\""));
    }

    @Test
    void executeReturnsNotRunDetailWhenContextOrDataframeMissing() {
        VolumeCheck check = checkWithBaseline(Optional.empty());

        List<DqMetric> nullContextMetrics = check.execute(null);
        MetricDetail nullContextDetail = findStatusDetail(nullContextMetrics);
        assertTrue(nullContextDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(nullContextDetail.getDetailValue().contains("\"reason\":\"missing_context\""));

        List<DqMetric> nullDfMetrics = check.execute(context(null));
        MetricDetail nullDfDetail = findStatusDetail(nullDfMetrics);
        assertTrue(nullDfDetail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(nullDfDetail.getDetailValue().contains("\"reason\":\"missing_dataframe\""));
    }

    @Test
    void executeSanitizesExecutionErrorPayload() {
        VolumeCheck check = new VolumeCheck(ctx -> {
            throw new RuntimeException("raw message with dataset_name and values");
        });

        List<DqMetric> metrics = check.execute(context(dfWithRows(5)));
        MetricDetail detail = findStatusDetail(metrics);

        assertTrue(detail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"execution_error\""));
        assertTrue(detail.getDetailValue().contains("\"error_type\":\"RuntimeException\""));
        assertFalse(detail.getDetailValue().contains("raw message with dataset_name and values"));
    }

    @Test
    void getCheckTypeReturnsVolume() {
        VolumeCheck check = checkWithBaseline(Optional.empty());
        assertEquals("VOLUME", check.getCheckType());
    }
}
