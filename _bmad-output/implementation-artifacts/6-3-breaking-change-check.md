# Story 6.3: Breaking Change Check

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the Breaking Change check to detect schema changes that remove or rename fields,
so that destructive schema modifications are flagged separately from additive drift.

## Acceptance Criteria

1. **Given** a dataset whose schema has removed fields compared to the previous run
   **When** the Breaking Change check executes
   **Then** it writes a `MetricDetail` with `check_type=BREAKING_CHANGE`, `detail_type=breaking_change_status` listing removed fields and status=FAIL

2. **Given** a dataset whose schema has renamed fields (a field appears removed and a different one added simultaneously, per structural diff)
   **When** the Breaking Change check executes
   **Then** it writes a `MetricDetail` with status=FAIL and the removed field names listed in the payload

3. **Given** a dataset whose schema only added new fields (no removals)
   **When** the Breaking Change check executes
   **Then** it writes a `MetricDetail` with status=PASS (additive changes are not breaking)

4. **Given** a dataset with no previous schema stored (first run)
   **When** the Breaking Change check executes
   **Then** it writes a `MetricDetail` with status=PASS and reason=baseline_unavailable (no comparison possible)

5. **Given** a null context or null DataFrame is passed
   **When** the Breaking Change check executes
   **Then** it returns a detail metric with status=NOT_RUN and does NOT propagate an exception

6. **And** the check implements `DqCheck`, is registered in `CheckFactory` via `DqsJob.buildCheckFactory()`, and requires zero changes to serve/API/dashboard

## Tasks / Subtasks

- [x] Task 1: Create `BreakingChangeCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 1, 2, 3, 4, 5)
  - [x] Declare `public final class BreakingChangeCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "BREAKING_CHANGE"`
  - [x] Define detail name constants: `DETAIL_TYPE_STATUS = "breaking_change_status"`, `DETAIL_TYPE_FIELDS = "breaking_change_fields"`
  - [x] Define status constants: `STATUS_PASS`, `STATUS_FAIL`, `STATUS_NOT_RUN`
  - [x] Define reason constants: `REASON_NO_BREAKING_CHANGES`, `REASON_BREAKING_CHANGES_DETECTED`, `REASON_BASELINE_UNAVAILABLE`, `REASON_MISSING_CONTEXT`, `REASON_MISSING_DATAFRAME`, `REASON_EXECUTION_ERROR`
  - [x] Define `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`
  - [x] Define inner `SchemaBaselineProvider` functional interface: `Optional<SchemaSnapshot> getBaseline(DatasetContext ctx) throws Exception`
  - [x] Define inner `SchemaSnapshot` value class: `String schemaJson()` (stores the full Spark schema JSON from previous run)
  - [x] Define inner `JdbcSchemaBaselineProvider` implementing `SchemaBaselineProvider` — queries `dq_metric_detail` WHERE `check_type='SCHEMA'` AND `detail_type='schema_hash'` for this dataset's most recent previous run, extracts `schema_json` from the stored JSON payload (reuses the data SchemaCheck already stores)
  - [x] Define inner `ConnectionProvider` functional interface (same pattern as `SchemaCheck.ConnectionProvider`)
  - [x] Define inner `NoOpSchemaBaselineProvider` implementing `SchemaBaselineProvider` — returns `Optional.empty()`
  - [x] Implement no-arg constructor: `this(new NoOpSchemaBaselineProvider())`
  - [x] Implement 1-arg constructor: `BreakingChangeCheck(SchemaBaselineProvider baselineProvider)` — validates non-null
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - [x] Wrap entire body in try/catch — catch Exception → return `errorDetail(e)` list
    - [x] Guard null context → return `notRunDetail(REASON_MISSING_CONTEXT)` list
    - [x] Guard `context.getDf() == null` → return `notRunDetail(REASON_MISSING_DATAFRAME)` list
    - [x] Extract current schema field names: `Set<String> currentFields = extractFlatFieldNames(context.getDf().schema())`
    - [x] Call `baselineProvider.getBaseline(context)` — if empty → return `passDetail(REASON_BASELINE_UNAVAILABLE, List.of())` list
    - [x] Parse baseline schema JSON: `StructType baselineSchema = (StructType) DataType.fromJson(snapshot.schemaJson())`
    - [x] Extract baseline field names: `Set<String> baselineFields = extractFlatFieldNames(baselineSchema)`
    - [x] Compute `removedFields = baselineFields - currentFields` (fields in baseline but not in current)
    - [x] If `removedFields.isEmpty()` → return `[statusDetail(PASS, REASON_NO_BREAKING_CHANGES, List.of())]`
    - [x] Else → return `[statusDetail(FAIL, REASON_BREAKING_CHANGES_DETECTED, removedFields), fieldsDetail(removedFields)]`
  - [x] Implement private `extractFlatFieldNames(StructType schema)` — returns `Set<String>` of dot-path field names (mirrors `SchemaCheck.buildFieldDescriptorMap()` key extraction, WITHOUT type descriptors — only names matter for removed/renamed detection)
  - [x] Implement private `statusDetail(String status, String reason, List<String> removedFields)` helper
  - [x] Implement private `passDetail(String reason, List<String> removedFields)` convenience wrapper
  - [x] Implement private `fieldsDetail(List<String> removedFields)` — writes `MetricDetail(CHECK_TYPE, DETAIL_TYPE_FIELDS, json)` with payload: `{removed_fields: [...]}`
  - [x] Implement private `notRunDetail(String reason)` helper
  - [x] Implement private `errorDetail(Exception e)` helper
  - [x] Implement private `toJson(Map<String, Object> payload)` helper

- [x] Task 2: Register `BreakingChangeCheck` in `DqsJob.buildCheckFactory()` (AC: 6)
  - [x] Add `f.register(new BreakingChangeCheck())` AFTER `ZeroRowCheck` registration and BEFORE `DqsScoreCheck`
  - [x] Add import: `import com.bank.dqs.checks.BreakingChangeCheck;`
  - [x] NOTE: Also fix malformed comment lines 315 and 318 in `DqsJob.java` that start with `/ TODO` and `/ Lambda` instead of `// TODO` and `// Lambda` — these are syntax errors

- [x] Task 3: Write `BreakingChangeCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 1, 2, 3, 4, 5, 6)
  - [x] Test: `executeReturnsFailWhenFieldsRemoved` — baseline has {id, name, amount}, current has {id, name} → FAIL, `removed_fields=[amount]`
  - [x] Test: `executeReturnsFailWhenMultipleFieldsRemoved` — baseline has {id, name, amount, ts}, current has {id} → FAIL, removed_fields contains [amount, name, ts]
  - [x] Test: `executeReturnsPassWhenOnlyFieldsAdded` — baseline has {id}, current has {id, name, amount} → PASS
  - [x] Test: `executeReturnsPassWhenSchemaUnchanged` — baseline and current identical → PASS
  - [x] Test: `executeReturnsPassWhenBaselineUnavailable` — no baseline → PASS, reason=baseline_unavailable
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeReturnsNotRunWhenDataFrameIsNull` — context with null df → NOT_RUN detail
  - [x] Test: `getCheckTypeReturnsBreakingChange` — `assertEquals("BREAKING_CHANGE", check.getCheckType())`
  - [x] Test: `executeHandlesBaselineProviderExceptionGracefully` — provider throws → returns NOT_RUN error detail, does NOT propagate
  - [x] Requires SparkSession — use `@BeforeAll`/`@AfterAll` lifecycle matching `ZeroRowCheckTest` or `SchemaCheckTest` pattern
  - [x] Use `spark.createDataFrame(List.of(...), schema)` with `RowFactory` and explicit `StructType`/`DataTypes` schemas
  - [x] Use lambda for `SchemaBaselineProvider` mock: `ctx -> Optional.of(new BreakingChangeCheck.SchemaSnapshot(baselineSchemaJson))`

## Dev Notes

### Critical Context: Relationship to SchemaCheck — Do NOT Duplicate Logic

`SchemaCheck.java` already detects breaking changes (removed + changed fields) as part of its full schema drift analysis. `BreakingChangeCheck` is a **separate, dedicated check** with a distinct `check_type=BREAKING_CHANGE`. It:

- Has its own `check_config` row — enabled/disabled independently
- Writes to its own `dq_metric_detail` rows (different `check_type` column value)
- Is designed to run alongside `SchemaCheck`, not replace it
- Focuses ONLY on field removal (destructive changes) — not type changes or additions
- Serves as a higher-severity alarm: even if `SchemaCheck` is warn-only for drift, `BreakingChangeCheck` is always FAIL for removal

**Do NOT modify SchemaCheck.java.** The two checks coexist.

### Baseline Data Source — Reuse SchemaCheck's Stored Schema JSON

`SchemaCheck` already stores full schema JSON per dataset run in `dq_metric_detail`:
- `check_type='SCHEMA'`, `detail_type='schema_hash'`, `detail_value={"algorithm":"SHA-256","hash":"sha256:...","schema_json":"<full JSON>"}`

`BreakingChangeCheck.JdbcSchemaBaselineProvider` must query this same row to retrieve the previous schema JSON. This avoids storing duplicate schema data.

```java
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
```

Then extract `schema_json` from the JSON payload:
```java
Map<String, Object> payload = OBJECT_MAPPER.readValue(detailValue, MAP_TYPE);
Object schemaJsonObj = payload.get("schema_json");
if (schemaJsonObj instanceof String schemaValue && !schemaValue.isBlank()) {
    return Optional.of(new SchemaSnapshot(schemaValue));
}
return Optional.empty();
```

Use `DqsConstants.EXPIRY_SENTINEL` (not `"9999-12-31 23:59:59"`) for `expiry_date` parameters.

### Check Type and Metric/Detail Constants

```java
public static final String CHECK_TYPE           = "BREAKING_CHANGE";
static final String DETAIL_TYPE_STATUS          = "breaking_change_status";
static final String DETAIL_TYPE_FIELDS          = "breaking_change_fields";

private static final String STATUS_PASS         = "PASS";
private static final String STATUS_FAIL         = "FAIL";
private static final String STATUS_NOT_RUN      = "NOT_RUN";

private static final String REASON_NO_BREAKING_CHANGES      = "no_breaking_changes";
private static final String REASON_BREAKING_CHANGES_DETECTED = "breaking_changes_detected";
private static final String REASON_BASELINE_UNAVAILABLE     = "baseline_unavailable";
private static final String REASON_MISSING_CONTEXT          = "missing_context";
private static final String REASON_MISSING_DATAFRAME        = "missing_dataframe";
private static final String REASON_EXECUTION_ERROR          = "execution_error";
```

### Metric Output Structure

For a dataset with removed fields (e.g., `amount` removed):
```
MetricDetail(BREAKING_CHANGE, breaking_change_status, {"status":"FAIL","reason":"breaking_changes_detected","removed_count":1})
MetricDetail(BREAKING_CHANGE, breaking_change_fields, {"removed_fields":["amount"]})
```

For a dataset with only added fields (no removals):
```
MetricDetail(BREAKING_CHANGE, breaking_change_status, {"status":"PASS","reason":"no_breaking_changes","removed_count":0})
```
(No `breaking_change_fields` detail when no removals — consistent with SchemaCheck's diffDetail only emitted when hasDrift())

For first run (no baseline):
```
MetricDetail(BREAKING_CHANGE, breaking_change_status, {"status":"PASS","reason":"baseline_unavailable","removed_count":0})
```

### Status Detail Payload Structure

```java
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("status", status);
payload.put("reason", reason);
payload.put("removed_count", removedFields.size());
// Serialise with ObjectMapper
```

Fields detail payload:
```java
Map<String, Object> payload = new LinkedHashMap<>();
payload.put("removed_fields", sortedRemovedFields);  // List<String>, sorted alphabetically
```

### Field Name Extraction (extractFlatFieldNames)

Extract nested dot-path names recursively from `StructType`, exactly like `SchemaCheck.buildFieldDescriptorMap()` key extraction — but store only the name (String), not the type descriptor:

```java
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
        if (field.dataType() instanceof ArrayType arr && arr.elementType() instanceof StructType arrStruct) {
            collectFieldNames(names, arrStruct, path + "[]");
        }
    }
}
```

Note: Consistent with SchemaCheck field path conventions: nested struct paths use `.`, array element structs use `[]` suffix. MapType sub-field handling optional (MapType fields are rare in the schema check baseline).

### DqsJob Registration — Exact Location

In `DqsJob.java`, method `buildCheckFactory()` (around lines 308-321):

```java
private static CheckFactory buildCheckFactory(List<DqMetric> accumulator) {
    CheckFactory f = new CheckFactory();
    f.register(new FreshnessCheck());
    f.register(new VolumeCheck());
    f.register(new SchemaCheck());
    f.register(new OpsCheck());
    f.register(new SlaCountdownCheck()); // Tier 2 — Epic 6, Story 6.1
    // TODO: wire JdbcSlaProvider via ConnectionProvider once JDBC connection threading is resolved
    f.register(new ZeroRowCheck());      // Tier 2 — Epic 6, Story 6.2
    f.register(new BreakingChangeCheck()); // Tier 2 — Epic 6, Story 6.3
    // DqsScoreCheck is registered LAST — always runs after all other checks
    // Lambda captures the accumulator list: reads prior check results for score computation
    f.register(new DqsScoreCheck(ctx -> accumulator));
    return f;
}
```

**IMPORTANT BUG TO FIX:** Current `DqsJob.java` lines 315 and 318 have malformed single-slash comments (`/ TODO` and `/ Lambda` instead of `// TODO` and `// Lambda`). These must be fixed to `// TODO` and `// Lambda` when adding the BreakingChangeCheck registration.

Add import at top: `import com.bank.dqs.checks.BreakingChangeCheck;`

### File Structure — Exact Locations

```
dqs-spark/
  src/main/java/com/bank/dqs/
    checks/
      BreakingChangeCheck.java      ← NEW file
    DqsJob.java                     ← MODIFY: add import + register BreakingChangeCheck + fix comments
  src/test/java/com/bank/dqs/
    checks/
      BreakingChangeCheckTest.java  ← NEW file
```

**Do NOT touch:** `CheckFactory.java`, `DqCheck.java`, `SchemaCheck.java`, any model files, writer, scanner, serve-layer files, ZeroRowCheck, SlaCountdownCheck, or any dashboard/API components.

### Java Patterns — Follow Exactly

- **try-with-resources** for all JDBC: `try (Connection conn = ...; PreparedStatement ps = ...; ResultSet rs = ...)` — never manual `.close()`
- **PreparedStatement** with `?` for all parameterized values — NEVER string concatenation
- **`throws SQLException`** on JDBC method signatures
- **Constructor validation** with `IllegalArgumentException` for null args
- **`static final ObjectMapper`** — same thread-safe pattern as `ZeroRowCheck`, `SchemaCheck`, `VolumeCheck`
- **Full generic types**: `List<DqMetric>`, `Set<String>`, `Optional<SchemaSnapshot>` — never raw types
- **Static imports** NOT needed (no Spark SQL functions)
- **Naming**: `BreakingChangeCheck` (PascalCase class), `CHECK_TYPE` (UPPER_SNAKE constant), `execute()` (camelCase method)
- **Package**: `com.bank.dqs.checks`
- **`TypeReference<Map<String, Object>>`** needed for Jackson parsing of JSON payload (same as `SchemaCheck.MAP_TYPE`)
- **`DqsConstants.EXPIRY_SENTINEL`** — use for expiry_date in JDBC queries, never hardcode `9999-12-31 23:59:59`
- **Never query `dq_metric_detail` raw table with manual expiry filters** — but here `expiry_date = ?` with `EXPIRY_SENTINEL` is the active-record pattern for Spark-side JDBC (not serve-layer; views are for Python serve only)

### Test Pattern — SparkSession IS Required

`BreakingChangeCheck` calls `context.getDf().schema()` — SparkSession is needed to create DataFrames with schemas.

Follow **exact pattern from `ZeroRowCheckTest`** (SparkSession lifecycle):

```java
class BreakingChangeCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    @BeforeAll
    static void initSpark() {
        spark = SparkSession.builder()
                .appName("BreakingChangeCheckTest")
                .master("local[1]")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
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

    private BreakingChangeCheck checkWithBaseline(Optional<BreakingChangeCheck.SchemaSnapshot> baseline) {
        return new BreakingChangeCheck(ctx -> baseline);
    }
    // ...
}
```

For creating DataFrames with explicit schemas:
```java
StructType schema = new StructType()
    .add("id", DataTypes.LongType, false)
    .add("name", DataTypes.StringType, true)
    .add("amount", DataTypes.DoubleType, true);

Dataset<Row> df = spark.createDataFrame(
    List.of(RowFactory.create(1L, "alice", 100.0)),
    schema
);
```

For baseline snapshot, extract schema JSON from the DataFrame schema:
```java
String baselineSchemaJson = baselineDf.schema().json();
BreakingChangeCheck.SchemaSnapshot snapshot = new BreakingChangeCheck.SchemaSnapshot(baselineSchemaJson);
```

### Comparison with Previous Checks (Critical Context)

| Check | DataFrame? | External Deps? | Baseline Source | Complexity |
|---|---|---|---|---|
| SchemaCheck | YES (schema inspection) | JDBC BaselineProvider (optional) | Own check_type=SCHEMA hash+JSON | High |
| ZeroRowCheck | YES (`df.count()`) | None | None | Low |
| SlaCountdownCheck | NO (time-based) | JDBC SlaProvider (optional) | `v_dataset_enrichment_active` | Medium |
| **BreakingChangeCheck** | **YES (schema inspection)** | **JDBC BaselineProvider (optional)** | **Reads SchemaCheck's stored schema_hash JSON** | **Medium** |

BreakingChangeCheck is structurally similar to SchemaCheck but:
1. ONLY checks field removal (a subset of SchemaCheck's full diff)
2. Uses SchemaCheck's already-stored `schema_json` as its baseline (no separate schema storage)
3. Does NOT hash the schema itself — just parses field names

### Previous Story Learnings (Stories 6.1 and 6.2)

From 6-2-zero-row-check completion:
- **Regression suite is at 190 tests, 0 failures** — do not break this
- **`DqsJob.buildCheckFactory()` already has SlaCountdownCheck and ZeroRowCheck registered** — add `BreakingChangeCheck` AFTER `ZeroRowCheck` and BEFORE `DqsScoreCheck`
- **`DqsJob.java` lines 315 and 318 have malformed single-slash comments** (`/ TODO` and `/ Lambda`) — fix these to `// TODO` and `// Lambda` when modifying the file
- **Test class naming**: `BreakingChangeCheckTest` (PascalCase), in `com.bank.dqs.checks` package
- **Logger pattern**: `private static final Logger LOG = LoggerFactory.getLogger(BreakingChangeCheck.class)` — used in execute() for INFO/WARN logging (see ZeroRowCheck.java for reference)
- **`notRunDetail` returns a list with single detail item** — caller wraps in `metrics.add()` then `return metrics`
- **`errorDetail` returns single detail item, not a list** — consistent with ZeroRowCheck/SchemaCheck pattern
- **SparkSession is required** — BreakingChangeCheck accesses `context.getDf().schema()`, needs live SparkSession in tests

From 6-1-sla-countdown-check completion:
- **`TypeReference<Map<String, Object>> MAP_TYPE`** for JDBC JSON parsing — same as SchemaCheck (needed for JdbcSchemaBaselineProvider)
- **Constructor validation** pattern: `if (param == null) throw new IllegalArgumentException(...)`
- **Inner interface + class pattern** (SlaProvider/JdbcSlaProvider, BaselineProvider/JdbcBaselineProvider) — follow same nesting structure for `SchemaBaselineProvider` + `JdbcSchemaBaselineProvider`

### Anti-Patterns — NEVER Do These

- **NEVER call `context.getDf()` without null-checking context first** — guard order: null context → null df → then schema()
- **NEVER let exceptions propagate from `execute()`** — entire body wrapped in try/catch
- **NEVER add check-type-specific logic to serve/API/dashboard** — only Spark knows `BREAKING_CHANGE`
- **NEVER create a second `CheckFactory` class** — register via `DqsJob.buildCheckFactory()`
- **NEVER modify `SchemaCheck.java`** — BreakingChangeCheck is separate, not a modification
- **NEVER hardcode `9999-12-31 23:59:59`** — use `DqsConstants.EXPIRY_SENTINEL`
- **NEVER query raw tables in serve layer** — this check is Spark-only; the rule about `v_*_active` views applies to the dqs-serve Python layer, not Spark's JDBC queries (which use the sentinel directly)
- **NEVER bundle Spark JARs** — Spark is `provided` scope in pom.xml
- **NEVER persist field VALUES** — `extractFlatFieldNames()` collects only field names (path strings), never values from rows

### Data Sensitivity Note

BreakingChangeCheck inspects only schema structure (field paths/names) from `context.getDf().schema()`. It NEVER reads row values. The `detail_value` JSON payload contains field names (structural metadata), which is explicitly permitted by the architecture's data sensitivity boundary: "Detail metrics may contain field names and schema structures, but NEVER source data values."

### Project Structure Notes

- New file `BreakingChangeCheck.java` goes in `dqs-spark/src/main/java/com/bank/dqs/checks/` alongside `FreshnessCheck`, `VolumeCheck`, `SchemaCheck`, `OpsCheck`, `SlaCountdownCheck`, `ZeroRowCheck`
- New test file `BreakingChangeCheckTest.java` goes in `dqs-spark/src/test/java/com/bank/dqs/checks/` — mirroring main source tree (Maven standard)
- Only `DqsJob.java` is modified (import + one line registration + fix malformed comment lines)
- No schema DDL changes needed — `BreakingChangeCheck` uses existing `dq_metric_detail` table and reads existing `SCHEMA` check rows

### References

- Epic 6 Story 6.3 AC: `_bmad-output/planning-artifacts/epics/epic-6-tier-2-quality-checks-phase-2.md`
- DqCheck interface: `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- CheckFactory: `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- SchemaCheck (structural template — BaselineProvider pattern + field extraction): `dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java`
- ZeroRowCheck (simpler structural template — ObjectMapper, Logger, no-arg constructor): `dqs-spark/src/main/java/com/bank/dqs/checks/ZeroRowCheck.java`
- ZeroRowCheckTest (SparkSession lifecycle to copy): `dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java`
- SchemaCheckTest (DataFrame-with-schema creation patterns): `dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java`
- DqsJob (register here + fix malformed comments): `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
- DatasetContext: `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- MetricDetail: `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- DqsConstants (EXPIRY_SENTINEL): `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
- Project Context — Java rules + anti-patterns: `_bmad-output/project-context.md`
- Architecture — Check extensibility: `_bmad-output/planning-artifacts/architecture.md`
- Previous stories: `_bmad-output/implementation-artifacts/6-1-sla-countdown-check.md`, `_bmad-output/implementation-artifacts/6-2-zero-row-check.md`

## Review Findings

**Retroactive review — Epic 6 retrospective action item #4 (2026-04-04)**

| # | Finding | Severity | Resolution |
|---|---------|----------|------------|
| 1 | **Hidden coupling to SchemaCheck JSON payload format**: `JdbcSchemaBaselineProvider` reads `dq_metric_detail` rows where `check_type = 'SCHEMA'` and `detail_type = 'schema_hash'`, then extracts the `schema_json` key from the stored JSON. If SchemaCheck changes its `schema_hash` payload structure (e.g., renames `schema_json` key), `BreakingChangeCheck` silently returns `Optional.empty()` (baseline_unavailable) on every run — no error, no alert, just a perpetual PASS. | Medium | Add format contract to both class Javadocs. SchemaCheck's `schema_hash` detail MUST preserve the `schema_json` key. Any change to SchemaCheck's payload format requires updating `JdbcSchemaBaselineProvider`. **Deferred to technical debt backlog.** |
| 2 | **`baseline_unavailable` on first run is indistinguishable from missing JDBC wiring**: When `NoOpSchemaBaselineProvider` is used (no-arg constructor), every run returns `PASS/baseline_unavailable`. The same result occurs on a genuine first-run with the JDBC provider. Operators cannot tell from metrics alone whether the check is wired or not. | Low | Accepted. The distinction requires log-level context (INFO vs WARN). Once JDBC wiring is completed (Action #1), genuine first-runs will naturally transition to comparison runs. |
| 3 | **`collectFieldNames` handles `ArrayType` but not `MapType`**: Map-typed fields are not recursed into, so a field like `metadata: Map<String, Struct>` would track only `metadata` and not its nested keys. If a map value type changes structurally, BreakingChangeCheck would not detect it. | Low | Accepted. MapType field names are tracked at the top level — struct removal inside a map value is out of scope for this check. Add note to class Javadoc if MapType nesting becomes a production concern. |

**Verdict:** Approved with one deferred medium finding (SchemaCheck coupling documentation). No blocking issues.

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation completed without issues.

### Completion Notes List

- Implemented `BreakingChangeCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` following exact patterns from `ZeroRowCheck` (Logger, ObjectMapper) and `SchemaCheck` (BaselineProvider pattern, JDBC query, field extraction, TypeReference MAP_TYPE).
- Inner types: `SchemaBaselineProvider` (functional interface), `SchemaSnapshot` (value class with `schemaJson()` accessor), `JdbcSchemaBaselineProvider` (JDBC reads SchemaCheck's stored `schema_hash` detail), `ConnectionProvider` (functional interface), `NoOpSchemaBaselineProvider` (returns `Optional.empty()`).
- Field extraction via `extractFlatFieldNames()` + `collectFieldNames()` handles flat, nested struct (dot-path), and array-of-struct (`[]` suffix) — consistent with SchemaCheck conventions.
- `DqsJob.java` modified: added `import com.bank.dqs.checks.BreakingChangeCheck;` and registered `new BreakingChangeCheck()` after `ZeroRowCheck` and before `DqsScoreCheck`. Comments in DqsJob.java at lines 315/316/319/320 were already properly formatted (`//`) — no malformed comment fix required.
- ATDD test file (`BreakingChangeCheckTest.java`) was pre-generated in RED phase with all 9 tests `@Disabled`. Removed `@Disabled` annotations and the `import org.junit.jupiter.api.Disabled` to activate the GREEN phase.
- Full regression suite: **200 tests, 0 failures** (was 191 before this story; +9 new BreakingChangeCheck tests).
- All 6 ACs satisfied: removed fields → FAIL (AC1+2), additive-only → PASS (AC3), no baseline → PASS/baseline_unavailable (AC4), null guards → NOT_RUN (AC5), registered in CheckFactory via DqsJob (AC6).

### File List

- `dqs-spark/src/main/java/com/bank/dqs/checks/BreakingChangeCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` (MODIFIED — import + registration)
- `dqs-spark/src/test/java/com/bank/dqs/checks/BreakingChangeCheckTest.java` (MODIFIED — removed @Disabled annotations to activate tests)

## Change Log

- 2026-04-04: Implemented Story 6.3 — Breaking Change Check. Created BreakingChangeCheck.java with SchemaBaselineProvider/SchemaSnapshot/JdbcSchemaBaselineProvider inner types. Registered in DqsJob.buildCheckFactory(). Activated 9 ATDD tests. Regression suite: 200 tests, 0 failures.
