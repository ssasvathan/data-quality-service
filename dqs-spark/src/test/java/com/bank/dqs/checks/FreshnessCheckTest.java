package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.to_timestamp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreshnessCheckTest {

    private static SparkSession spark;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-04-03T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    @BeforeAll
    static void initSpark() {
        spark = SparkSession.builder()
                .appName("FreshnessCheckTest")
                .master("local[1]")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    private Dataset<Row> timestampDf(String... utcTimestamps) {
        List<String> iso8601UtcTimestamps = Arrays.stream(utcTimestamps)
                .map(ts -> ts.replace(" ", "T") + "Z")
                .collect(Collectors.toList());
        return spark.createDataset(iso8601UtcTimestamps, Encoders.STRING())
                .toDF("source_event_timestamp")
                .withColumn("source_event_timestamp", to_timestamp(col("source_event_timestamp")));
    }

    private Dataset<Row> timestampInstantDf(Instant... utcInstants) {
        List<Row> rows = Arrays.stream(utcInstants)
                .map(instant -> RowFactory.create(Timestamp.from(instant)))
                .collect(Collectors.toList());
        StructType schema = DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("source_event_timestamp", DataTypes.TimestampType, true)
        });
        return spark.createDataFrame(rows, schema);
    }

    private Dataset<Row> nonTimestampDf() {
        return spark.createDataset(List.of("x"), Encoders.STRING())
                .toDF("non_timestamp_column");
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

    private FreshnessCheck checkWithBaseline(Optional<FreshnessCheck.BaselineStats> baseline) {
        FreshnessCheck.BaselineProvider provider = ctx -> baseline;
        return new FreshnessCheck(provider, FIXED_CLOCK);
    }

    private MetricNumeric findHoursMetric(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .filter(m -> "hours_since_update".equals(m.getMetricName()))
                .findFirst()
                .orElseThrow();
    }

    private MetricDetail findDetailMetric(List<DqMetric> metrics) {
        return metrics.stream()
                .filter(MetricDetail.class::isInstance)
                .map(MetricDetail.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void executeComputesHoursSinceUpdateMetricWhenTimestampColumnPresent() {
        FreshnessCheck check = checkWithBaseline(Optional.empty());
        Dataset<Row> df = timestampDf(
                "2026-04-03 08:00:00",
                "2026-04-03 10:00:00"
        );

        List<DqMetric> metrics = check.execute(context(df));

        MetricNumeric hoursSinceUpdate = findHoursMetric(metrics);
        assertEquals("FRESHNESS", hoursSinceUpdate.getCheckType());
        assertEquals(2.0, hoursSinceUpdate.getMetricValue(), 0.001);
    }

    @Test
    void executeReturnsCheckNotRunDetailWhenTimestampColumnMissing() {
        FreshnessCheck check = checkWithBaseline(Optional.empty());

        List<DqMetric> metrics = check.execute(context(nonTimestampDf()));

        boolean hasHoursMetric = metrics.stream()
                .filter(MetricNumeric.class::isInstance)
                .map(MetricNumeric.class::cast)
                .anyMatch(m -> "hours_since_update".equals(m.getMetricName()));
        assertFalse(hasHoursMetric);

        MetricDetail detail = findDetailMetric(metrics);
        assertEquals("FRESHNESS", detail.getCheckType());
        assertTrue(detail.getDetailValue().contains("\"status\":\"NOT_RUN\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"missing_source_event_timestamp\""));
    }

    @Test
    void executeClassifiesWarnWhenStalenessExceedsWarnThreshold() {
        FreshnessCheck.BaselineStats baseline = new FreshnessCheck.BaselineStats(4.0, 1.0, 30);
        FreshnessCheck check = checkWithBaseline(Optional.of(baseline));
        Dataset<Row> df = timestampDf("2026-04-03 06:30:00");

        List<DqMetric> metrics = check.execute(context(df));

        MetricNumeric hoursSinceUpdate = findHoursMetric(metrics);
        assertEquals(5.5, hoursSinceUpdate.getMetricValue(), 0.001);

        MetricDetail detail = findDetailMetric(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"WARN\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"above_warn_threshold\""));
    }

    @Test
    void executeClassifiesFailWhenStalenessExceedsFailThreshold() {
        FreshnessCheck.BaselineStats baseline = new FreshnessCheck.BaselineStats(4.0, 1.0, 30);
        FreshnessCheck check = checkWithBaseline(Optional.of(baseline));
        Dataset<Row> df = timestampDf("2026-04-03 04:00:00");

        List<DqMetric> metrics = check.execute(context(df));

        MetricNumeric hoursSinceUpdate = findHoursMetric(metrics);
        assertEquals(8.0, hoursSinceUpdate.getMetricValue(), 0.001);

        MetricDetail detail = findDetailMetric(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"FAIL\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"above_fail_threshold\""));
    }

    @Test
    void executeUsesBaselineInitializationModeWhenHistoryUnavailable() {
        FreshnessCheck check = checkWithBaseline(Optional.empty());
        Dataset<Row> df = timestampDf("2026-04-03 09:00:00");

        List<DqMetric> metrics = check.execute(context(df));

        MetricNumeric hoursSinceUpdate = findHoursMetric(metrics);
        assertEquals(3.0, hoursSinceUpdate.getMetricValue(), 0.001);

        MetricDetail detail = findDetailMetric(metrics);
        assertTrue(detail.getDetailValue().contains("\"status\":\"PASS\""));
        assertTrue(detail.getDetailValue().contains("\"reason\":\"baseline_unavailable\""));
    }

    @Test
    void executeClampsNegativeStalenessToZeroForFutureTimestamps() {
        FreshnessCheck check = checkWithBaseline(Optional.empty());
        Dataset<Row> df = timestampDf("2026-04-03 15:00:00");

        List<DqMetric> metrics = check.execute(context(df));

        MetricNumeric hoursSinceUpdate = findHoursMetric(metrics);
        assertEquals(0.0, hoursSinceUpdate.getMetricValue(), 0.0);
    }

    @Test
    void executeComputesHoursIndependentOfSparkSessionTimezone() {
        String originalTimezone = spark.conf().get("spark.sql.session.timeZone", "UTC");
        try {
            spark.conf().set("spark.sql.session.timeZone", "America/Toronto");
            FreshnessCheck check = checkWithBaseline(Optional.empty());
            Dataset<Row> df = timestampInstantDf(
                    Instant.parse("2026-04-03T08:00:00Z"),
                    Instant.parse("2026-04-03T10:00:00Z")
            );

            List<DqMetric> metrics = check.execute(context(df));

            MetricNumeric hoursSinceUpdate = findHoursMetric(metrics);
            assertEquals(2.0, hoursSinceUpdate.getMetricValue(), 0.001);
        } finally {
            spark.conf().set("spark.sql.session.timeZone", originalTimezone);
        }
    }

    @Test
    void baselineStatsFromSamplesIgnoresNullEntries() {
        FreshnessCheck.BaselineStats stats = FreshnessCheck.BaselineStats.fromSamples(
                Arrays.asList(2.0, null, 4.0)
        );

        assertEquals(3.0, stats.getMeanHours(), 0.001);
        assertEquals(1.0, stats.getStddevHours(), 0.001);
        assertEquals(2L, stats.getSampleCount());
    }

    @Test
    void getCheckTypeReturnsFreshness() {
        FreshnessCheck check = checkWithBaseline(Optional.empty());
        assertEquals("FRESHNESS", check.getCheckType());
    }
}
