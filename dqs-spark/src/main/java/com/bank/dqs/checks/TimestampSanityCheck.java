package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.TimestampType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

/**
 * Timestamp Sanity check implementation.
 *
 * <p>Scans the DataFrame schema for {@code TimestampType} and {@code DateType} columns.
 * For each such column it:
 * <ol>
 *   <li>Counts values greater than {@code partitionDate + DEFAULT_FUTURE_TOLERANCE_DAYS}
 *       (future-dated, with clock skew tolerance)</li>
 *   <li>Counts values less than {@code partitionDate - DEFAULT_MAX_AGE_YEARS}
 *       (unreasonably old)</li>
 *   <li>Computes percentages of anomalous values</li>
 *   <li>Flags as FAIL if either percentage exceeds {@code FUTURE_FAIL_THRESHOLD} (5%)</li>
 *   <li>Emits {@code MetricNumeric} for both percentages per column</li>
 *   <li>Emits {@code MetricDetail} with PASS/FAIL status per column and a summary detail</li>
 * </ol>
 *
 * <p>This is a pure Spark DataFrame check — no external JDBC providers needed.
 * Thresholds are hardcoded constants.
 */
public final class TimestampSanityCheck implements DqCheck {

    private static final Logger LOG = LoggerFactory.getLogger(TimestampSanityCheck.class);

    public static final String CHECK_TYPE = "TIMESTAMP_SANITY";

    // Metric names (column suffix appended in practice: "future_pct.event_ts")
    public static final String METRIC_FUTURE_PCT = "future_pct";
    public static final String METRIC_STALE_PCT  = "stale_pct";

    // Detail types
    public static final String DETAIL_TYPE_COLUMN  = "timestamp_sanity";         // per-column prefix (appended with ".<columnName>")
    public static final String DETAIL_TYPE_SUMMARY = "timestamp_sanity_summary"; // one per execution

    // Thresholds
    public static final int    DEFAULT_FUTURE_TOLERANCE_DAYS = 1;
    public static final int    DEFAULT_MAX_AGE_YEARS         = 10;
    public static final double FUTURE_FAIL_THRESHOLD         = 0.05;

    // Status constants
    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    // Reason constants
    private static final String REASON_MISSING_CONTEXT    = "missing_context";
    private static final String REASON_MISSING_DATAFRAME  = "missing_dataframe";
    private static final String REASON_NO_TIMESTAMP_COLUMNS = "no_timestamp_columns";
    private static final String REASON_EXECUTION_ERROR    = "execution_error";
    private static final String REASON_FUTURE_EXCEEDS_THRESHOLD = "future_timestamps_exceed_threshold";
    private static final String REASON_STALE_EXCEEDS_THRESHOLD  = "stale_timestamps_exceed_threshold";

    // Jackson ObjectMapper is thread-safe after construction — shared static instance.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Inner record holding per-column analysis results.
     */
    record TimestampColumnResult(String columnName, double futurePct, double stalePct,
                                  long futureCount, long staleCount, long nonNullCount,
                                  String status) {}

    /**
     * No-arg constructor — no external providers needed.
     * Used by {@code DqsJob.buildCheckFactory()}.
     */
    public TimestampSanityCheck() {
    }

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public List<DqMetric> execute(DatasetContext context) {
        List<DqMetric> metrics = new ArrayList<>();
        try {
            if (context == null) {
                metrics.addAll(notRunDetail(REASON_MISSING_CONTEXT));
                return metrics;
            }

            if (context.getDf() == null) {
                metrics.addAll(notRunDetail(REASON_MISSING_DATAFRAME));
                return metrics;
            }

            Dataset<Row> df = context.getDf();
            LocalDate partitionDate = context.getPartitionDate();
            if (partitionDate == null) {
                metrics.addAll(notRunDetail("missing_partition_date"));
                return metrics;
            }

            LOG.info("Executing TimestampSanityCheck for dataset={}", context.getDatasetName());

            // Collect timestamp and date columns from schema
            List<String> tsColumns = new ArrayList<>();
            for (StructField field : df.schema().fields()) {
                if (field.dataType() instanceof TimestampType || field.dataType() instanceof DateType) {
                    tsColumns.add(field.name());
                }
            }

            // If no timestamp/date columns, return summary PASS with reason
            if (tsColumns.isEmpty()) {
                LOG.info("TimestampSanityCheck: no timestamp/date columns found in dataset={}",
                        context.getDatasetName());
                metrics.add(summaryDetail(STATUS_PASS, REASON_NO_TIMESTAMP_COLUMNS, 0, 0));
                return metrics;
            }

            // Analyze each timestamp/date column
            List<TimestampColumnResult> results = new ArrayList<>();
            for (String colName : tsColumns) {
                TimestampColumnResult result = analyzeColumn(df, colName, partitionDate);
                results.add(result);

                // Emit MetricNumeric for both percentages
                metrics.add(new MetricNumeric(CHECK_TYPE,
                        METRIC_FUTURE_PCT + "." + colName, result.futurePct()));
                metrics.add(new MetricNumeric(CHECK_TYPE,
                        METRIC_STALE_PCT + "." + colName, result.stalePct()));

                // Emit MetricDetail per column
                metrics.add(new MetricDetail(CHECK_TYPE,
                        "timestamp_sanity." + colName, columnStatusPayload(result)));
            }

            // Determine overall status: FAIL if any column is FAIL, else PASS
            long failedColumns = results.stream()
                    .filter(r -> STATUS_FAIL.equals(r.status()))
                    .count();
            String overallStatus = failedColumns > 0 ? STATUS_FAIL : STATUS_PASS;

            // Emit summary detail
            metrics.add(summaryDetail(overallStatus, null, results.size(), (int) failedColumns));

            return metrics;

        } catch (Exception e) {
            LOG.warn("TimestampSanityCheck execution failed", e);
            metrics.clear();
            metrics.addAll(errorDetail(e));
            return metrics;
        }
    }

    /**
     * Analyzes a single timestamp/date column for future-dated and stale values.
     *
     * @param df            the DataFrame
     * @param columnName    the name of the column to analyze
     * @param partitionDate the partition date used to derive thresholds
     * @return a {@code TimestampColumnResult} with percentages and status
     */
    private TimestampColumnResult analyzeColumn(Dataset<Row> df, String columnName,
                                                 LocalDate partitionDate) {
        // Future threshold: partition date + 1 day (clock skew tolerance)
        Timestamp futureThreshold = Timestamp.valueOf(
                partitionDate.plusDays(DEFAULT_FUTURE_TOLERANCE_DAYS).atStartOfDay());

        // Stale threshold: 10 years before partition date
        Timestamp staleThreshold = Timestamp.valueOf(
                partitionDate.minusYears(DEFAULT_MAX_AGE_YEARS).atStartOfDay());

        // Cast DateType columns to TimestampType for uniform comparison
        Column tsCol = col(columnName).cast(DataTypes.TimestampType);

        long futureCount  = df.filter(tsCol.gt(lit(futureThreshold))).count();
        long staleCount   = df.filter(tsCol.lt(lit(staleThreshold))).count();
        long nonNullCount = df.filter(col(columnName).isNotNull()).count();

        double futurePct = nonNullCount == 0 ? 0.0 : (double) futureCount / nonNullCount;
        double stalePct  = nonNullCount == 0 ? 0.0 : (double) staleCount  / nonNullCount;

        String status = classifyStatus(futurePct, stalePct);

        LOG.debug("TimestampSanityCheck column={} futurePct={} stalePct={} status={}",
                columnName, futurePct, stalePct, status);

        return new TimestampColumnResult(columnName, futurePct, stalePct,
                futureCount, staleCount, nonNullCount, status);
    }

    /**
     * Classifies column status based on future and stale percentages.
     * FAIL if either percentage exceeds {@code FUTURE_FAIL_THRESHOLD} (5%), else PASS.
     */
    private String classifyStatus(double futurePct, double stalePct) {
        if (futurePct > FUTURE_FAIL_THRESHOLD || stalePct > FUTURE_FAIL_THRESHOLD) {
            return STATUS_FAIL;
        }
        return STATUS_PASS;
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private List<MetricDetail> notRunDetail(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", reason);
        MetricDetail detail = new MetricDetail(CHECK_TYPE, DETAIL_TYPE_SUMMARY, toJson(payload));
        List<MetricDetail> list = new ArrayList<>();
        list.add(detail);
        return list;
    }

    private List<MetricDetail> errorDetail(Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("error_type", exception.getClass().getSimpleName());
        MetricDetail detail = new MetricDetail(CHECK_TYPE, DETAIL_TYPE_SUMMARY, toJson(payload));
        List<MetricDetail> list = new ArrayList<>();
        list.add(detail);
        return list;
    }

    private MetricDetail summaryDetail(String status, String reason, int totalColumns,
                                        int failedColumns) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        if (reason != null) {
            payload.put("reason", reason);
        }
        payload.put("total_columns_analyzed", totalColumns);
        payload.put("failed_columns", failedColumns);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_SUMMARY, toJson(payload));
    }

    private String columnStatusPayload(TimestampColumnResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result.status());
        payload.put("column", result.columnName());
        payload.put("future_pct", result.futurePct());
        payload.put("stale_pct", result.stalePct());
        if (STATUS_FAIL.equals(result.status())) {
            boolean futureFail = result.futurePct() > FUTURE_FAIL_THRESHOLD;
            boolean staleFail  = result.stalePct()  > FUTURE_FAIL_THRESHOLD;
            if (futureFail && staleFail) {
                payload.put("reason", REASON_FUTURE_EXCEEDS_THRESHOLD + "_and_" + REASON_STALE_EXCEEDS_THRESHOLD);
            } else if (futureFail) {
                payload.put("reason", REASON_FUTURE_EXCEEDS_THRESHOLD);
            } else {
                payload.put("reason", REASON_STALE_EXCEEDS_THRESHOLD);
            }
        }
        payload.put("non_null_rows", result.nonNullCount());
        payload.put("future_count", result.futureCount());
        payload.put("stale_count", result.staleCount());
        return toJson(payload);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"NOT_RUN\",\"reason\":\"execution_error\"}";
        }
    }
}
