package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.DqsConstants;
import com.bank.dqs.model.MetricDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Breaking Change check implementation.
 *
 * <p>Detects schema changes that remove or rename fields by comparing field names
 * between the current dataset schema and a stored baseline (from SchemaCheck's
 * {@code dq_metric_detail} rows). Returns FAIL when fields are removed; PASS when
 * only added or unchanged; PASS with {@code reason=baseline_unavailable} for first runs.
 *
 * <p>This check coexists with {@link SchemaCheck} — it does NOT replace it.
 * BreakingChangeCheck focuses exclusively on field removal (destructive changes),
 * serving as a higher-severity alarm that is always FAIL for removal regardless of
 * SchemaCheck's warn-only policy for additive drift.
 *
 * <p>The class preserves per-dataset failure isolation: any exception is converted into
 * a deterministic {@code NOT_RUN} diagnostic detail metric.
 */
public final class BreakingChangeCheck implements DqCheck {

    private static final Logger LOG = LoggerFactory.getLogger(BreakingChangeCheck.class);

    public static final String CHECK_TYPE = "BREAKING_CHANGE";

    public static final String DETAIL_TYPE_STATUS = "breaking_change_status";
    public static final String DETAIL_TYPE_FIELDS = "breaking_change_fields";

    private static final String STATUS_PASS    = "PASS";
    private static final String STATUS_FAIL    = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_NO_BREAKING_CHANGES       = "no_breaking_changes";
    private static final String REASON_BREAKING_CHANGES_DETECTED = "breaking_changes_detected";
    private static final String REASON_BASELINE_UNAVAILABLE      = "baseline_unavailable";
    private static final String REASON_MISSING_CONTEXT           = "missing_context";
    private static final String REASON_MISSING_DATAFRAME         = "missing_dataframe";
    private static final String REASON_EXECUTION_ERROR           = "execution_error";

    // Jackson ObjectMapper is thread-safe after configuration and can be shared as a static field.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    // Anonymous subclass of TypeReference captures the generic parameter at class-load time,
    // working around Java type erasure so Jackson can deserialize into Map<String, Object>.
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final SchemaBaselineProvider baselineProvider;

    /**
     * No-arg constructor used by {@code DqsJob.buildCheckFactory()}.
     * Defaults to {@link NoOpSchemaBaselineProvider} — no JDBC baseline lookup.
     */
    public BreakingChangeCheck() {
        this(new NoOpSchemaBaselineProvider());
    }

    /**
     * Constructor with explicit baseline provider injection (used by tests and production wiring).
     *
     * @param baselineProvider the provider to retrieve the previous schema baseline; must not be null
     */
    public BreakingChangeCheck(SchemaBaselineProvider baselineProvider) {
        if (baselineProvider == null) {
            throw new IllegalArgumentException("baselineProvider must not be null");
        }
        this.baselineProvider = baselineProvider;
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

            LOG.info("Executing BreakingChangeCheck for dataset={}", context.getDatasetName());

            Set<String> currentFields = extractFlatFieldNames(context.getDf().schema());

            Optional<SchemaSnapshot> maybeBaseline = baselineProvider.getBaseline(context);
            if (maybeBaseline.isEmpty()) {
                metrics.add(passDetail(REASON_BASELINE_UNAVAILABLE));
                return metrics;
            }

            SchemaSnapshot snapshot = maybeBaseline.get();
            DataType parsedType = DataType.fromJson(snapshot.schemaJson());
            if (!(parsedType instanceof StructType)) {
                LOG.warn("BreakingChangeCheck: baseline schema JSON did not parse to a StructType " +
                        "(got {}); treating as baseline_unavailable for dataset={}",
                        parsedType.getClass().getSimpleName(), context.getDatasetName());
                metrics.add(passDetail(REASON_BASELINE_UNAVAILABLE));
                return metrics;
            }
            StructType baselineSchema = (StructType) parsedType;
            Set<String> baselineFields = extractFlatFieldNames(baselineSchema);

            // Compute removed fields: present in baseline but NOT in current
            List<String> removedFields = new ArrayList<>();
            for (String field : baselineFields) {
                if (!currentFields.contains(field)) {
                    removedFields.add(field);
                }
            }
            Collections.sort(removedFields);

            if (removedFields.isEmpty()) {
                metrics.add(statusDetail(STATUS_PASS, REASON_NO_BREAKING_CHANGES, List.of()));
            } else {
                metrics.add(statusDetail(STATUS_FAIL, REASON_BREAKING_CHANGES_DETECTED, removedFields));
                metrics.add(fieldsDetail(removedFields));
            }

            return metrics;
        } catch (Exception e) {
            LOG.warn("BreakingChangeCheck execution failed", e);
            // Clear any partially-added metrics before the exception: if future refactoring adds
            // metrics earlier in execute(), this ensures the error detail is the sole output.
            metrics.clear();
            metrics.add(errorDetail(e));
            return metrics;
        }
    }

    // -------------------------------------------------------------------------
    // Field name extraction
    // -------------------------------------------------------------------------

    private Set<String> extractFlatFieldNames(StructType schema) {
        Set<String> names = new LinkedHashSet<>();
        collectFieldNames(names, schema, "");
        return names;
    }

    private void collectFieldNames(Set<String> names, StructType struct, String prefix) {
        for (StructField field : struct.fields()) {
            String path = prefix.isEmpty() ? field.name() : prefix + "." + field.name();
            names.add(path);
            if (field.dataType() instanceof StructType nested) {
                collectFieldNames(names, nested, path);
            }
            if (field.dataType() instanceof ArrayType arr
                    && arr.elementType() instanceof StructType arrStruct) {
                collectFieldNames(names, arrStruct, path + "[]");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Detail metric helpers
    // -------------------------------------------------------------------------

    private MetricDetail statusDetail(String status, String reason, List<String> removedFields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("removed_count", removedFields.size());
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail passDetail(String reason) {
        return statusDetail(STATUS_PASS, reason, List.of());
    }

    private MetricDetail fieldsDetail(List<String> removedFields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("removed_fields", List.copyOf(removedFields));
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_FIELDS, toJson(payload));
    }

    private MetricDetail notRunDetail(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", reason);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail errorDetail(Exception e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("error_type", e.getClass().getSimpleName());
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize payload", e);
            return "{\"status\":\"NOT_RUN\",\"reason\":\"execution_error\"}";
        }
    }

    // -------------------------------------------------------------------------
    // Inner interfaces and classes
    // -------------------------------------------------------------------------

    /**
     * Functional interface for retrieving the previous schema baseline.
     * The real implementation queries the {@code dq_metric_detail} table via JDBC.
     * Tests inject a lambda.
     */
    @FunctionalInterface
    public interface SchemaBaselineProvider {
        Optional<SchemaSnapshot> getBaseline(DatasetContext ctx) throws Exception;
    }

    /**
     * Functional interface for providing a JDBC connection (same pattern as SchemaCheck).
     * Package-private: only {@link JdbcSchemaBaselineProvider} within this package uses it.
     */
    @FunctionalInterface
    interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    /**
     * Immutable value class holding the stored schema JSON from a previous run.
     * The JSON is the full Spark StructType JSON as stored by SchemaCheck's schema_hash detail.
     */
    public static final class SchemaSnapshot {
        private final String schemaJson;

        public SchemaSnapshot(String schemaJson) {
            if (schemaJson == null || schemaJson.isBlank()) {
                throw new IllegalArgumentException("schemaJson must not be null or blank");
            }
            this.schemaJson = schemaJson;
        }

        public String schemaJson() {
            return schemaJson;
        }
    }

    /**
     * JDBC implementation of {@link SchemaBaselineProvider}.
     *
     * <p>Queries the {@code dq_metric_detail} table for SchemaCheck's most recent
     * {@code schema_hash} row (which stores the full schema JSON). This avoids storing
     * duplicate schema data — BreakingChangeCheck reuses SchemaCheck's already-stored payload.
     */
    public static final class JdbcSchemaBaselineProvider implements SchemaBaselineProvider {

        private static final String BASELINE_QUERY =
                "SELECT md.detail_value "
                + "FROM dq_metric_detail md "
                + "JOIN dq_run r ON r.id = md.dq_run_id "
                + "WHERE r.dataset_name = ? "
                + "  AND md.check_type = 'SCHEMA' "
                + "  AND md.detail_type = 'schema_hash' "
                + "  AND r.partition_date < ? "
                + "  AND r.expiry_date = ? "
                + "  AND md.expiry_date = ? "
                + "ORDER BY r.partition_date DESC "
                + "LIMIT 1";

        private final ConnectionProvider connectionProvider;

        public JdbcSchemaBaselineProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<SchemaSnapshot> getBaseline(DatasetContext context) throws Exception {
            // Defensive guard: execute() always passes a non-null context (guarded before calling
            // getBaseline), but this class is public-API injectable so we validate defensively.
            if (context == null) {
                throw new IllegalArgumentException("context must not be null");
            }

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(BASELINE_QUERY)) {

                ps.setString(1, context.getDatasetName());
                ps.setObject(2, context.getPartitionDate());
                ps.setString(3, DqsConstants.EXPIRY_SENTINEL);
                ps.setString(4, DqsConstants.EXPIRY_SENTINEL);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    String detailValue = rs.getString(1);
                    if (detailValue == null || detailValue.isBlank()) {
                        return Optional.empty();
                    }

                    Map<String, Object> payload = OBJECT_MAPPER.readValue(detailValue, MAP_TYPE);
                    Object schemaJsonObj = payload.get("schema_json");
                    if (schemaJsonObj instanceof String schemaValue && !schemaValue.isBlank()) {
                        return Optional.of(new SchemaSnapshot(schemaValue));
                    }
                    return Optional.empty();
                }
            }
        }
    }

    /**
     * No-op baseline provider — always returns empty (used by the no-arg constructor).
     * Effectively disables baseline comparison for deployments without JDBC wiring.
     */
    private static final class NoOpSchemaBaselineProvider implements SchemaBaselineProvider {
        @Override
        public Optional<SchemaSnapshot> getBaseline(DatasetContext context) {
            return Optional.empty();
        }
    }
}
