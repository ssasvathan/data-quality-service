package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zero-Row check implementation.
 *
 * <p>Detects datasets with no records by calling {@code context.getDf().count()}.
 * Returns FAIL when the row count is zero (empty delivery), PASS otherwise.
 *
 * <p>Unlike VolumeCheck, there is no external baseline provider — this is the
 * simplest possible Tier 2 check: a pure Spark DataFrame count with a binary decision.
 *
 * <p>The class preserves per-dataset isolation: any exception is converted into a
 * diagnostic detail metric instead of propagating.
 */
public final class ZeroRowCheck implements DqCheck {

    private static final Logger LOG = LoggerFactory.getLogger(ZeroRowCheck.class);

    public static final String CHECK_TYPE = "ZERO_ROW";

    public static final String METRIC_ROW_COUNT = "row_count";
    public static final String DETAIL_TYPE_STATUS = "zero_row_status";

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_HAS_ROWS = "has_rows";
    private static final String REASON_ZERO_ROWS = "zero_rows";
    private static final String REASON_MISSING_CONTEXT = "missing_context";
    private static final String REASON_MISSING_DATAFRAME = "missing_dataframe";
    private static final String REASON_EXECUTION_ERROR = "execution_error";

    // Jackson ObjectMapper is thread-safe after configuration and can be shared as a static field.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * No-arg constructor used by {@code DqsJob.buildCheckFactory()}.
     *
     * <p>ZeroRowCheck has no external dependencies — it is purely a Spark DataFrame check.
     */
    public ZeroRowCheck() {
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
                metrics.add(notRunDetail(REASON_MISSING_CONTEXT));
                return metrics;
            }

            if (context.getDf() == null) {
                metrics.add(notRunDetail(REASON_MISSING_DATAFRAME));
                return metrics;
            }

            LOG.info("Executing ZeroRowCheck for dataset={}", context.getDatasetName());

            long rowCount = context.getDf().count();

            String status;
            String reason;
            if (rowCount == 0) {
                status = STATUS_FAIL;
                reason = REASON_ZERO_ROWS;
            } else {
                status = STATUS_PASS;
                reason = REASON_HAS_ROWS;
            }

            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_ROW_COUNT, (double) rowCount));
            metrics.add(statusDetail(status, reason, rowCount));

            return metrics;
        } catch (Exception e) {
            LOG.warn("ZeroRowCheck execution failed", e);
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    private MetricDetail notRunDetail(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", reason);
        payload.put("row_count", -1L);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail statusDetail(String status, String reason, long rowCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("row_count", rowCount);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail errorDetail(Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("row_count", -1L);
        payload.put("error_type", exception.getClass().getSimpleName());
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"NOT_RUN\",\"reason\":\"execution_error\"}";
        }
    }
}
