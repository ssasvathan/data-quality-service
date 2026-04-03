package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqMetric;
import com.bank.dqs.model.DqsConstants;
import com.bank.dqs.model.MetricDetail;
import com.bank.dqs.model.MetricNumeric;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schema drift check implementation.
 *
 * <p>Compares the current dataset schema against a stored baseline schema hash and emits:
 * numeric counts ({@code added_fields_count}, {@code removed_fields_count},
 * {@code changed_fields_count}) and detail payloads for status/hash/diff.
 *
 * <p>The check preserves per-dataset failure isolation: any exception is converted into
 * a deterministic {@code NOT_RUN} diagnostic detail metric.
 */
public final class SchemaCheck implements DqCheck {

    public static final String CHECK_TYPE = "SCHEMA";

    static final String METRIC_ADDED_FIELDS_COUNT = "added_fields_count";
    static final String METRIC_REMOVED_FIELDS_COUNT = "removed_fields_count";
    static final String METRIC_CHANGED_FIELDS_COUNT = "changed_fields_count";

    static final String DETAIL_TYPE_STATUS = "schema_status";
    static final String DETAIL_TYPE_HASH = "schema_hash";
    static final String DETAIL_TYPE_DIFF = "schema_diff";

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_FAIL = "FAIL";
    private static final String STATUS_NOT_RUN = "NOT_RUN";

    private static final String REASON_BASELINE_UNAVAILABLE = "baseline_unavailable";
    private static final String REASON_NO_SCHEMA_DRIFT = "no_schema_drift";
    private static final String REASON_SCHEMA_ADDITIVE_CHANGE = "schema_additive_change";
    private static final String REASON_SCHEMA_BREAKING_CHANGE = "schema_breaking_change";
    private static final String REASON_MISSING_CONTEXT = "missing_context";
    private static final String REASON_MISSING_DATAFRAME = "missing_dataframe";
    private static final String REASON_EXECUTION_ERROR = "execution_error";

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final BaselineProvider baselineProvider;

    public SchemaCheck() {
        this(new NoOpBaselineProvider());
    }

    public SchemaCheck(BaselineProvider baselineProvider) {
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

            Dataset<Row> df = context.getDf();
            if (df == null) {
                metrics.add(notRunDetail(REASON_MISSING_DATAFRAME));
                return metrics;
            }

            StructType currentSchema = df.schema();
            String currentSchemaJson = currentSchema.json();
            String currentHash = sha256(currentSchemaJson);
            Map<String, String> currentFields = buildFieldDescriptorMap(currentSchema);

            Optional<BaselineSnapshot> maybeBaseline = baselineProvider.getBaseline(context);
            BaselineSnapshot baseline = maybeBaseline.orElse(null);

            DiffResult diffResult = evaluateDiff(currentHash, currentFields, baseline);

            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_ADDED_FIELDS_COUNT, diffResult.addedFields.size()));
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_REMOVED_FIELDS_COUNT, diffResult.removedFields.size()));
            metrics.add(new MetricNumeric(CHECK_TYPE, METRIC_CHANGED_FIELDS_COUNT, diffResult.changedFields.size()));
            metrics.add(statusDetail(
                    diffResult.status,
                    diffResult.reason,
                    currentHash,
                    baseline == null ? null : baseline.getHash(),
                    diffResult.addedFields.size(),
                    diffResult.removedFields.size(),
                    diffResult.changedFields.size()
            ));
            metrics.add(hashDetail(currentHash, currentSchemaJson));

            if (diffResult.hasDrift()) {
                metrics.add(diffDetail(diffResult));
            }

            return metrics;
        } catch (Exception exception) {
            metrics.clear();
            metrics.add(errorDetail(exception));
            return metrics;
        }
    }

    private DiffResult evaluateDiff(String currentHash, Map<String, String> currentFields, BaselineSnapshot baseline) {
        if (baseline == null || baseline.getHash() == null || baseline.getHash().isBlank()) {
            return new DiffResult(STATUS_PASS, REASON_BASELINE_UNAVAILABLE);
        }

        if (currentHash.equals(baseline.getHash())) {
            return new DiffResult(STATUS_PASS, REASON_NO_SCHEMA_DRIFT);
        }

        if (baseline.getSchemaJson() == null || baseline.getSchemaJson().isBlank()) {
            List<ChangedField> changedFields = List.of(
                    new ChangedField(
                            "$schema",
                            "hash=" + baseline.getHash(),
                            "hash=" + currentHash
                    )
            );
            return new DiffResult(
                    STATUS_FAIL,
                    REASON_SCHEMA_BREAKING_CHANGE,
                    List.of(),
                    List.of(),
                    changedFields
            );
        }

        StructType baselineSchema = parseBaselineSchema(baseline.getSchemaJson());
        Map<String, String> baselineFields = buildFieldDescriptorMap(baselineSchema);

        List<String> addedFields = new ArrayList<>();
        List<String> removedFields = new ArrayList<>();
        List<ChangedField> changedFields = new ArrayList<>();

        for (Map.Entry<String, String> entry : currentFields.entrySet()) {
            String path = entry.getKey();
            String currentDescriptor = entry.getValue();
            if (!baselineFields.containsKey(path)) {
                addedFields.add(path);
            } else {
                String baselineDescriptor = baselineFields.get(path);
                if (!currentDescriptor.equals(baselineDescriptor)) {
                    changedFields.add(new ChangedField(path, baselineDescriptor, currentDescriptor));
                }
            }
        }

        for (Map.Entry<String, String> entry : baselineFields.entrySet()) {
            String path = entry.getKey();
            if (!currentFields.containsKey(path)) {
                removedFields.add(path);
            }
        }

        addedFields.sort(String::compareTo);
        removedFields.sort(String::compareTo);
        changedFields.sort(Comparator.comparing(ChangedField::field));

        if (addedFields.isEmpty() && removedFields.isEmpty() && changedFields.isEmpty()) {
            return new DiffResult(STATUS_PASS, REASON_NO_SCHEMA_DRIFT, addedFields, removedFields, changedFields);
        }
        if (!addedFields.isEmpty() && removedFields.isEmpty() && changedFields.isEmpty()) {
            return new DiffResult(
                    STATUS_WARN,
                    REASON_SCHEMA_ADDITIVE_CHANGE,
                    addedFields,
                    removedFields,
                    changedFields
            );
        }
        return new DiffResult(
                STATUS_FAIL,
                REASON_SCHEMA_BREAKING_CHANGE,
                addedFields,
                removedFields,
                changedFields
        );
    }

    private StructType parseBaselineSchema(String schemaJson) {
        DataType parsed = DataType.fromJson(schemaJson);
        if (!(parsed instanceof StructType)) {
            throw new IllegalStateException("Baseline schema must be StructType");
        }
        return (StructType) parsed;
    }

    private Map<String, String> buildFieldDescriptorMap(StructType schema) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (StructField field : schema.fields()) {
            addFieldDescriptor(fields, field.name(), field.dataType(), field.nullable());
        }
        return fields;
    }

    private void addFieldDescriptor(
            Map<String, String> fieldMap,
            String path,
            DataType dataType,
            boolean nullable
    ) {
        fieldMap.put(path, descriptor(dataType, nullable));

        if (dataType instanceof StructType structType) {
            for (StructField nested : structType.fields()) {
                addFieldDescriptor(
                        fieldMap,
                        path + "." + nested.name(),
                        nested.dataType(),
                        nested.nullable()
                );
            }
            return;
        }

        if (dataType instanceof ArrayType arrayType) {
            addFieldDescriptor(
                    fieldMap,
                    path + "[]",
                    arrayType.elementType(),
                    arrayType.containsNull()
            );
            return;
        }

        if (dataType instanceof MapType mapType) {
            addFieldDescriptor(fieldMap, path + "{}.key", mapType.keyType(), false);
            addFieldDescriptor(
                    fieldMap,
                    path + "{}.value",
                    mapType.valueType(),
                    mapType.valueContainsNull()
            );
        }
    }

    private String descriptor(DataType dataType, boolean nullable) {
        return "type=" + dataType.catalogString() + ",nullable=" + nullable;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private MetricDetail hashDetail(String currentHash, String schemaJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("algorithm", HASH_ALGORITHM);
        payload.put("hash", currentHash);
        payload.put("schema_json", schemaJson);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_HASH, toJson(payload));
    }

    private MetricDetail statusDetail(
            String status,
            String reason,
            String schemaHash,
            String baselineHash,
            int addedCount,
            int removedCount,
            int changedCount
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reason", reason);
        payload.put("schema_hash", schemaHash);
        payload.put("baseline_hash", baselineHash);
        payload.put("added_count", addedCount);
        payload.put("removed_count", removedCount);
        payload.put("changed_count", changedCount);
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private MetricDetail diffDetail(DiffResult diffResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("added_fields", diffResult.addedFields);
        payload.put("removed_fields", diffResult.removedFields);

        List<Map<String, Object>> changedFieldsPayload = new ArrayList<>();
        for (ChangedField changedField : diffResult.changedFields) {
            Map<String, Object> changed = new LinkedHashMap<>();
            changed.put("field", changedField.field());
            changed.put("before", changedField.before());
            changed.put("after", changedField.after());
            changedFieldsPayload.add(changed);
        }
        payload.put("changed_fields", changedFieldsPayload);

        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_DIFF, toJson(payload));
    }

    private MetricDetail notRunDetail(String reason) {
        return statusDetail(STATUS_NOT_RUN, reason, null, null, 0, 0, 0);
    }

    private MetricDetail errorDetail(Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", STATUS_NOT_RUN);
        payload.put("reason", REASON_EXECUTION_ERROR);
        payload.put("schema_hash", null);
        payload.put("baseline_hash", null);
        payload.put("added_count", 0);
        payload.put("removed_count", 0);
        payload.put("changed_count", 0);
        payload.put("error_type", safeErrorType(exception));
        return new MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, toJson(payload));
    }

    private String safeErrorType(Exception exception) {
        if (exception == null) {
            return "UnknownError";
        }
        return exception.getClass().getSimpleName();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"NOT_RUN\",\"reason\":\"execution_error\"}";
        }
    }

    @FunctionalInterface
    public interface BaselineProvider {
        Optional<BaselineSnapshot> getBaseline(DatasetContext context) throws Exception;
    }

    @FunctionalInterface
    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public static final class JdbcBaselineProvider implements BaselineProvider {
        private static final String BASELINE_QUERY =
                "SELECT md.detail_value "
                        + "FROM dq_metric_detail md "
                        + "JOIN dq_run r ON r.id = md.dq_run_id "
                        + "WHERE r.dataset_name = ? "
                        + "  AND md.check_type = ? "
                        + "  AND md.detail_type = ? "
                        + "  AND r.partition_date < ? "
                        + "  AND r.expiry_date = ? "
                        + "  AND md.expiry_date = ? "
                        + "ORDER BY r.partition_date DESC "
                        + "LIMIT 1";

        private final ConnectionProvider connectionProvider;

        public JdbcBaselineProvider(ConnectionProvider connectionProvider) {
            if (connectionProvider == null) {
                throw new IllegalArgumentException("connectionProvider must not be null");
            }
            this.connectionProvider = connectionProvider;
        }

        @Override
        public Optional<BaselineSnapshot> getBaseline(DatasetContext context) throws Exception {
            if (context == null) {
                throw new IllegalArgumentException("context must not be null");
            }

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(BASELINE_QUERY)) {
                ps.setString(1, context.getDatasetName());
                ps.setString(2, CHECK_TYPE);
                ps.setString(3, DETAIL_TYPE_HASH);
                ps.setObject(4, context.getPartitionDate());
                ps.setString(5, DqsConstants.EXPIRY_SENTINEL);
                ps.setString(6, DqsConstants.EXPIRY_SENTINEL);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    String detailValue = rs.getString(1);
                    if (detailValue == null || detailValue.isBlank()) {
                        return Optional.empty();
                    }

                    Map<String, Object> payload = OBJECT_MAPPER.readValue(detailValue, MAP_TYPE);
                    Object hashObj = payload.get("hash");
                    if (!(hashObj instanceof String hashValue) || hashValue.isBlank()) {
                        return Optional.empty();
                    }

                    String schemaJson = null;
                    Object schemaJsonObj = payload.get("schema_json");
                    if (schemaJsonObj instanceof String schemaValue && !schemaValue.isBlank()) {
                        schemaJson = schemaValue;
                    }

                    return Optional.of(new BaselineSnapshot(hashValue, schemaJson));
                }
            }
        }
    }

    public static final class BaselineSnapshot {
        private final String hash;
        private final String schemaJson;

        public BaselineSnapshot(String hash, String schemaJson) {
            if (hash == null || hash.isBlank()) {
                throw new IllegalArgumentException("hash must not be null or blank");
            }
            this.hash = hash;
            this.schemaJson = schemaJson;
        }

        public String getHash() {
            return hash;
        }

        public String getSchemaJson() {
            return schemaJson;
        }
    }

    private static final class NoOpBaselineProvider implements BaselineProvider {
        @Override
        public Optional<BaselineSnapshot> getBaseline(DatasetContext context) {
            return Optional.empty();
        }
    }

    private record ChangedField(String field, String before, String after) {
    }

    private static final class DiffResult {
        private final String status;
        private final String reason;
        private final List<String> addedFields;
        private final List<String> removedFields;
        private final List<ChangedField> changedFields;

        private DiffResult(String status, String reason) {
            this(status, reason, List.of(), List.of(), List.of());
        }

        private DiffResult(
                String status,
                String reason,
                List<String> addedFields,
                List<String> removedFields,
                List<ChangedField> changedFields
        ) {
            this.status = status;
            this.reason = reason;
            this.addedFields = addedFields;
            this.removedFields = removedFields;
            this.changedFields = changedFields;
        }

        private boolean hasDrift() {
            return !addedFields.isEmpty() || !removedFields.isEmpty() || !changedFields.isEmpty();
        }
    }
}
