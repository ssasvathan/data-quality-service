# Story 6.5: Timestamp Sanity Check

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want the Timestamp Sanity check to detect out-of-range or future-dated timestamps,
so that time-based anomalies in source data are flagged.

## Acceptance Criteria

1. **Given** a dataset with timestamp columns and >5% future-dated values in a column
   **When** the Timestamp Sanity check executes
   **Then** it writes a `MetricNumeric` with the percentage of future-dated timestamps per column
   **And** it writes a `MetricDetail` with status=FAIL for that column

2. **Given** a dataset with timestamp columns and ≤5% future-dated values in a column
   **When** the Timestamp Sanity check executes
   **Then** it writes a `MetricNumeric` with the percentage (≤5%) and `MetricDetail` with status=PASS

3. **Given** a dataset with timestamp columns that have values beyond the configurable max-age threshold (default: 10 years before partition date)
   **When** the Timestamp Sanity check executes
   **Then** it writes a `MetricNumeric` with the percentage of unreasonably old timestamps and a `MetricDetail` with status=FAIL when >5% are beyond the threshold

4. **Given** a dataset with no timestamp or date columns
   **When** the Timestamp Sanity check executes
   **Then** it writes a `MetricDetail` with status=PASS and reason=no_timestamp_columns and returns immediately (no further metrics)

5. **Given** a null context or null DataFrame is passed
   **When** the Timestamp Sanity check executes
   **Then** it returns a detail metric with status=NOT_RUN and does NOT propagate an exception

6. **And** the check implements `DqCheck`, is registered in `CheckFactory` via `DqsJob.buildCheckFactory()`, and requires zero changes to serve/API/dashboard

## Tasks / Subtasks

- [x] Task 1: Create `TimestampSanityCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 1–5)
  - [x] Declare `public final class TimestampSanityCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "TIMESTAMP_SANITY"`
  - [x] Define constants:
    - `METRIC_FUTURE_PCT = "future_pct"` (appended with `.<columnName>` in practice: `future_pct.amount_date`)
    - `METRIC_STALE_PCT = "stale_pct"` (same column-suffix pattern)
    - `DETAIL_TYPE_STATUS = "timestamp_sanity_status"` (per-column)
    - `DETAIL_TYPE_SUMMARY = "timestamp_sanity_summary"` (one per execution)
    - `DEFAULT_FUTURE_TOLERANCE_DAYS = 1` — timestamps up to 1 day in the future are allowed (clock skew tolerance)
    - `DEFAULT_MAX_AGE_YEARS = 10` — timestamps more than 10 years before partition date are flagged as unreasonably old
    - `FUTURE_FAIL_THRESHOLD = 0.05` — 5% threshold for FAIL (both future and stale)
  - [x] Define `TimestampColumnResult` inner record: `String columnName`, `double futurePct`, `double stalePct`, `String status`
  - [x] No-arg constructor: `public TimestampSanityCheck()` — no external providers needed
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - [x] Wrap entire body in try/catch — catch Exception → return `errorDetail(e)` list
    - [x] Guard null context → return `notRunDetail(REASON_MISSING_CONTEXT)` list
    - [x] Guard `context.getDf() == null` → return `notRunDetail(REASON_MISSING_DATAFRAME)` list
    - [x] Collect timestamp and date columns from `context.getDf().schema().fields()` — include `TimestampType`, `DateType`
    - [x] If no timestamp/date columns found → return `[summaryDetail(STATUS_PASS, REASON_NO_TIMESTAMP_COLUMNS, 0, 0)]`
    - [x] For each timestamp/date column: call `analyzeColumn(df, columnName, partitionDate)`
    - [x] Emit `MetricNumeric(CHECK_TYPE, "future_pct." + colName, result.futurePct())`
    - [x] Emit `MetricNumeric(CHECK_TYPE, "stale_pct." + colName, result.stalePct())`
    - [x] Emit `MetricDetail(CHECK_TYPE, "timestamp_sanity." + colName, columnStatusPayload(result))`
    - [x] Determine overall status: FAIL if any column is FAIL, WARN if any column is WARN, else PASS
    - [x] Emit final `MetricDetail(CHECK_TYPE, DETAIL_TYPE_SUMMARY, summaryPayload(...))`
  - [x] Implement `analyzeColumn(Dataset<Row> df, String columnName, LocalDate partitionDate)` → `TimestampColumnResult`:
    - [x] Compute `futureThreshold`: `partitionDate.plusDays(DEFAULT_FUTURE_TOLERANCE_DAYS)` as `java.sql.Timestamp`
    - [x] Compute `staleThreshold`: `partitionDate.minusYears(DEFAULT_MAX_AGE_YEARS)` as `java.sql.Timestamp`
    - [x] Use Spark filter + count for future: `df.filter(col(columnName).cast(DataTypes.TimestampType).gt(lit(futureThreshold))).count()`
    - [x] Use Spark filter + count for stale: `df.filter(col(columnName).cast(DataTypes.TimestampType).lt(lit(staleThreshold))).count()`
    - [x] Get total non-null count: `df.filter(col(columnName).isNotNull()).count()`
    - [x] Compute percentages: `futureCount / (double) nonNullCount` (handle nonNullCount == 0 → 0.0)
    - [x] Classify status: future_pct > FUTURE_FAIL_THRESHOLD → FAIL; stale_pct > FUTURE_FAIL_THRESHOLD → FAIL; else PASS
    - [x] Return `new TimestampColumnResult(columnName, futurePct, stalePct, status)`
  - [x] Implement private helpers: `notRunDetail(String reason)`, `errorDetail(Exception e)`, `summaryDetail(String status, String reason, int total, int failed)`, `columnStatusPayload(TimestampColumnResult result)`, `toJson(Map<String, Object> payload)`

- [x] Task 2: Register `TimestampSanityCheck` in `DqsJob.buildCheckFactory()` (AC: 6)
  - [x] Add `f.register(new TimestampSanityCheck());` AFTER `DistributionCheck` and BEFORE `DqsScoreCheck`
  - [x] Add import: `import com.bank.dqs.checks.TimestampSanityCheck;`
  - [x] Update the Javadoc comment listing Tier 2 checks in `buildCheckFactory()`

- [x] Task 3: Write `TimestampSanityCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 1–6)
  - [x] Test: `executeFlagsColumnAsFailWhenFuturePctExceedsThreshold` — DataFrame with 6 future-dated timestamps out of 10 rows → future_pct > 5%, status=FAIL for that column
  - [x] Test: `executePassesColumnWhenFuturePctBelowThreshold` — DataFrame with 0 future timestamps → future_pct=0.0, status=PASS
  - [x] Test: `executeFlagsColumnAsFailWhenStalePctExceedsThreshold` — DataFrame with 6 rows beyond 10-year threshold → stale_pct > 5%, status=FAIL
  - [x] Test: `executePassesWhenNoTimestampColumns` — DataFrame with only int/string columns → MetricDetail with reason=no_timestamp_columns, status=PASS
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeReturnsNotRunWhenDataFrameIsNull` — context with null df → NOT_RUN detail
  - [x] Test: `executeHandlesAllNullTimestampColumn` — column with all-null values → 0.0 pct, status=PASS (divide-by-zero guard)
  - [x] Test: `executeWritesMetricNumericForFutureAndStalePct` — verify MetricNumeric entries are present for both metrics per column
  - [x] Test: `executeSummaryDetailReflectsOverallStatus` — one FAIL column + one PASS column → summary status=FAIL
  - [x] Test: `getCheckTypeReturnsTimestampSanity` — `assertEquals("TIMESTAMP_SANITY", check.getCheckType())`
  - [x] Test: `executeHandlesExceptionGracefully` — pass a context whose `getDf()` call throws → errorDetail returned, no propagation
  - [x] Requires SparkSession — use `@BeforeAll`/`@AfterAll` lifecycle matching `ZeroRowCheckTest`/`DistributionCheckTest` pattern
  - [x] Use `spark.createDataFrame(List.of(...), schema)` with `RowFactory` and explicit `StructType`/`DataTypes`
  - [x] Use `DataTypes.TimestampType` for timestamp columns; `RowFactory.create(new java.sql.Timestamp(instant.toEpochMilli()))` for values
  - [x] Partition date for tests: `LocalDate.of(2026, 4, 3)` — future timestamps would be after `2026-04-04`

## Dev Notes

### What This Check Does (High-Level)

`TimestampSanityCheck` scans the DataFrame schema for `TimestampType` and `DateType` columns. For each such column it:
1. Counts values greater than `partitionDate + 1 day` (future-dated, accounting for clock skew)
2. Counts values less than `partitionDate - 10 years` (unreasonably old)
3. Computes percentages of anomalous values
4. Flags as FAIL if either percentage exceeds 5%
5. Emits `MetricNumeric` for both percentages per column and `MetricDetail` with classification
6. Emits one summary `MetricDetail` with the overall status

This is a **Spark DataFrame check** (SparkSession required) with **no external JDBC providers**. It is simpler than `DistributionCheck` — thresholds are hardcoded constants, not read from DB. This matches the story pattern where the epic does not specify a DB-backed config provider for tolerances.

### Spark Column Type Detection

Use `df.schema().fields()` to iterate. Check data type using:

```java
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.TimestampType;
import org.apache.spark.sql.types.DateType;

for (StructField field : df.schema().fields()) {
    if (field.dataType() instanceof TimestampType || field.dataType() instanceof DateType) {
        // analyze this column
    }
}
```

Both `TimestampType` and `DateType` are singletons — use `instanceof` check, not `equals`.

### Spark Filter + Count Pattern

```java
import static org.apache.spark.sql.functions.*;
import java.sql.Timestamp;
import java.time.LocalDate;

// Future threshold: allow up to 1 day tolerance
Timestamp futureThreshold = Timestamp.valueOf(partitionDate.plusDays(DEFAULT_FUTURE_TOLERANCE_DAYS).atStartOfDay());

// Stale threshold: 10 years before partition date
Timestamp staleThreshold  = Timestamp.valueOf(partitionDate.minusYears(DEFAULT_MAX_AGE_YEARS).atStartOfDay());

// Cast DateType columns to timestamp for uniform comparison
Column tsCol = col(columnName).cast(DataTypes.TimestampType);

long futureCount   = df.filter(tsCol.gt(lit(futureThreshold))).count();
long staleCount    = df.filter(tsCol.lt(lit(staleThreshold))).count();
long nonNullCount  = df.filter(col(columnName).isNotNull()).count();

double futurePct = nonNullCount == 0 ? 0.0 : (double) futureCount / nonNullCount;
double stalePct  = nonNullCount == 0 ? 0.0 : (double) staleCount  / nonNullCount;
```

**Note:** `lit(Timestamp)` is supported by Spark. `Timestamp.valueOf(localDateTime)` is the canonical conversion from `LocalDate` (use `.atStartOfDay()`).

**Static import required:** `import static org.apache.spark.sql.functions.*;`

### Metric Output Structure

For a column `event_ts` with no anomalies:
```
MetricNumeric(TIMESTAMP_SANITY, "future_pct.event_ts", 0.0)
MetricNumeric(TIMESTAMP_SANITY, "stale_pct.event_ts", 0.0)
MetricDetail(TIMESTAMP_SANITY, "timestamp_sanity.event_ts", {"status":"PASS","column":"event_ts","future_pct":0.0,"stale_pct":0.0,"total_rows":1000,"future_count":0,"stale_count":0})
```

For a column with >5% future timestamps:
```
MetricNumeric(TIMESTAMP_SANITY, "future_pct.event_ts", 0.08)
MetricDetail(TIMESTAMP_SANITY, "timestamp_sanity.event_ts", {"status":"FAIL","column":"event_ts","future_pct":0.08,"stale_pct":0.0,"reason":"future_timestamps_exceed_threshold","total_rows":1000,"future_count":80,"stale_count":0})
```

Summary detail (always emitted once per execution):
```
MetricDetail(TIMESTAMP_SANITY, "timestamp_sanity_summary", {"status":"FAIL","total_columns_analyzed":2,"failed_columns":1})
```

No timestamp columns:
```
MetricDetail(TIMESTAMP_SANITY, "timestamp_sanity_summary", {"status":"PASS","reason":"no_timestamp_columns","total_columns_analyzed":0,"failed_columns":0})
```

**IMPORTANT — `dq_metric_detail` uniqueness constraint:** The constraint is `UNIQUE(dq_run_id, check_type, detail_type, expiry_date)`. Detail type per column is `"timestamp_sanity.event_ts"` — unique per column per run. Summary uses `"timestamp_sanity_summary"` — always unique. No constraint violations.

**IMPORTANT — `dq_metric_numeric` uniqueness constraint:** The constraint is `UNIQUE(dq_run_id, check_type, metric_name, expiry_date)`. Metric names `"future_pct.event_ts"` and `"stale_pct.event_ts"` are unique per column. No constraint violations.

### Classification Logic

```java
private String classifyStatus(double futurePct, double stalePct) {
    if (futurePct > FUTURE_FAIL_THRESHOLD || stalePct > FUTURE_FAIL_THRESHOLD) {
        return STATUS_FAIL;
    }
    return STATUS_PASS;
}
```

No WARN status for this check — the epic only specifies PASS/FAIL based on the 5% threshold.

### Java Patterns — Follow Exactly

- **`import static org.apache.spark.sql.functions.*`** — required for `col`, `lit`, `count`
- **`private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`** — shared instance (thread-safe after construction)
- **No JDBC providers** — this check uses no external DB queries; thresholds are constants
- **No `try-with-resources` JDBC** — not applicable; this is a pure Spark check
- **Constructor validation** not needed — no-arg constructor, no injected dependencies
- **Full generic types**: `List<DqMetric>`, `Map<String, Object>`
- **Logger**: `private static final Logger LOG = LoggerFactory.getLogger(TimestampSanityCheck.class);`
- **Package**: `com.bank.dqs.checks`
- **Class**: `TimestampSanityCheck` (PascalCase), `CHECK_TYPE` (UPPER_SNAKE), `execute()` (camelCase)
- **`java.sql.Timestamp.valueOf(localDateTime)`** for Spark `lit()` comparison — NOT `java.time.Instant`
- **`DataTypes.TimestampType`** to cast `DateType` columns for uniform comparison

### DqsJob Registration — Exact Location

After `DistributionCheck` registration and before `DqsScoreCheck`:

```java
f.register(new DistributionCheck());        // Tier 2 — Epic 6, Story 6.4
f.register(new TimestampSanityCheck());     // Tier 2 — Epic 6, Story 6.5
// DqsScoreCheck is registered LAST — always runs after all other checks
f.register(new DqsScoreCheck(ctx -> accumulator));
```

### File Structure — Exact Locations

```
dqs-spark/
  src/main/java/com/bank/dqs/
    checks/
      TimestampSanityCheck.java     ← NEW file
    DqsJob.java                     ← MODIFY: add import + register TimestampSanityCheck
  src/test/java/com/bank/dqs/
    checks/
      TimestampSanityCheckTest.java ← NEW file
```

**Do NOT touch:** `CheckFactory.java`, `DqCheck.java`, `DistributionCheck.java`, `BreakingChangeCheck.java`, `ZeroRowCheck.java`, `SlaCountdownCheck.java`, `SchemaCheck.java`, any model files, writer, scanner, serve-layer files, or any dashboard/API components. **No DDL changes required** — no new columns needed.

### Test Pattern — SparkSession IS Required

`TimestampSanityCheck` calls `context.getDf().schema()` and Spark filter/count operations — SparkSession is required.

Follow **exact SparkSession lifecycle pattern from `ZeroRowCheckTest`**:

```java
class TimestampSanityCheckTest {

    private static SparkSession spark;
    private static final LocalDate PARTITION_DATE = LocalDate.of(2026, 4, 3);

    @BeforeAll
    static void initSpark() {
        spark = SparkSession.builder()
                .appName("TimestampSanityCheckTest")
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
}
```

For creating DataFrames with timestamp columns:
```java
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

StructType schema = new StructType()
    .add("id", DataTypes.LongType, false)
    .add("event_ts", DataTypes.TimestampType, true);

// Future timestamp (after 2026-04-04 threshold)
Timestamp futureTs = Timestamp.valueOf(LocalDateTime.of(2026, 5, 1, 0, 0));
// Normal timestamp
Timestamp normalTs = Timestamp.valueOf(LocalDateTime.of(2025, 6, 1, 0, 0));
// Very old timestamp (before 2016-04-03 threshold)
Timestamp staleTs  = Timestamp.valueOf(LocalDateTime.of(2000, 1, 1, 0, 0));

Dataset<Row> df = spark.createDataFrame(
    List.of(
        RowFactory.create(1L, futureTs),
        RowFactory.create(2L, normalTs),
        RowFactory.create(3L, futureTs),
        ...
    ),
    schema
);
```

For testing the >5% threshold:
- Use 10 rows total, with 6 having future timestamps → future_pct = 0.6 → FAIL
- Use 10 rows total, with 0 having future timestamps → future_pct = 0.0 → PASS

For testing no-timestamp-column case:
```java
StructType schema = new StructType()
    .add("id", DataTypes.LongType, false)
    .add("name", DataTypes.StringType, true);

Dataset<Row> df = spark.createDataFrame(
    List.of(RowFactory.create(1L, "Alice")),
    schema
);
```

For testing the graceful exception case, create a mock/wrapper DatasetContext that throws from getDf():
```java
// Simplest approach: pass a real context but call check with a broken df operation
// Better: create DataFrame that triggers exception in analyzeColumn by passing a non-existent column name
// Safest for unit test: the top-level try/catch in execute() catches the exception
```

### Anti-Patterns — NEVER Do These

- **NEVER store row values** — only percentages and counts are stored; timestamps themselves are never output to metrics
- **NEVER query raw tables in serve layer** — this check is Spark-only; the `v_*_active` rule applies only to the dqs-serve Python layer
- **NEVER add check-type-specific logic to serve/API/dashboard** — `TIMESTAMP_SANITY` check type is transparent to the serve layer
- **NEVER let exceptions propagate from `execute()`** — entire body wrapped in try/catch
- **NEVER create a second `CheckFactory` class** — register via `DqsJob.buildCheckFactory()`
- **NEVER hardcode `9999-12-31 23:59:59`** — this check doesn't use JDBC, so EXPIRY_SENTINEL is not needed here, but don't invent new patterns
- **NEVER bundle Spark JARs** — Spark is `provided` scope in pom.xml
- **NEVER use raw types** — `List<DqMetric>`, `Map<String, Object>`
- **NEVER call `getDf()` without null-checking context first** — guard: null context → null df → then schema/filter

### Previous Story Learnings (Stories 6.1–6.4)

From 6-4-distribution-checks-with-eventattribute-explosion completion:
- **Test suite is at 216 tests, 0 failures** — do not break regressions
- **`DqsJob.buildCheckFactory()` already has SlaCountdownCheck, ZeroRowCheck, BreakingChangeCheck, DistributionCheck registered** — add `TimestampSanityCheck` AFTER `DistributionCheck` and BEFORE `DqsScoreCheck`
- **ATDD tests may be pre-generated in RED phase with `@Disabled`** — if `TimestampSanityCheckTest.java` already exists with `@Disabled`, remove the annotations; do NOT create a duplicate file
- **`notRunDetail` returns a list with single item** — add to `metrics` list, then return
- **`errorDetail` returns single detail item** — consistent with all prior checks
- **SparkSession lifecycle**: `@BeforeAll initSpark()` + `@AfterAll stopSpark()` — required for all DataFrame tests
- **`private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`** — one per class, never instantiated inside methods

From 6-2-zero-row-check (most similar pattern — SparkSession, no JDBC):
- No external providers needed → single no-arg constructor
- `ObjectMapper` is thread-safe; one static instance is correct
- `df.filter(...).count()` is the correct Spark pattern for conditional row counting

From 6-1-sla-countdown-check (injected Clock pattern):
- This check does NOT need a Clock injection — it derives time boundaries from `context.getPartitionDate()` which is a `LocalDate`. This avoids non-determinism in tests without needing clock injection.

### Comparison With Previous Checks

| Check | DataFrame? | External Deps? | Stateful? | Complexity |
|---|---|---|---|---|
| SlaCountdownCheck | NO | JDBC SlaProvider | No (time-based) | Low |
| ZeroRowCheck | YES (`count()`) | None | No | Low |
| BreakingChangeCheck | YES (schema) | JDBC BaselineProvider | Schema baseline | Medium |
| DistributionCheck | YES (agg functions) | JDBC ExplodeConfigProvider + BaselineStatsProvider | Column stats baseline | High |
| **TimestampSanityCheck** | **YES (filter + count)** | **None** | **No** | **Low-Medium** |

`TimestampSanityCheck` is similar to `ZeroRowCheck` in that it requires SparkSession but has no JDBC providers. The difference is it iterates columns of specific types (TimestampType/DateType) and performs filter operations per column.

### Project Structure Notes

- New file `TimestampSanityCheck.java` → `dqs-spark/src/main/java/com/bank/dqs/checks/` alongside all existing check classes
- New test file `TimestampSanityCheckTest.java` → `dqs-spark/src/test/java/com/bank/dqs/checks/` — mirroring main source tree (Maven standard)
- Only `DqsJob.java` is modified (import + one line registration + Javadoc update)
- No schema DDL changes — thresholds are hardcoded constants (no new `check_config` columns needed)
- No changes to serve/API/dashboard — generic metric tables absorb the new check type automatically

### References

- Epic 6 Story 6.5 AC: `_bmad-output/planning-artifacts/epics/epic-6-tier-2-quality-checks-phase-2.md`
- PRD FR19 (Timestamp Sanity check): `_bmad-output/planning-artifacts/prd.md`
- DqCheck interface: `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- CheckFactory: `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- ZeroRowCheck (simplest DataFrame check, SparkSession required, no JDBC): `dqs-spark/src/main/java/com/bank/dqs/checks/ZeroRowCheck.java`
- ZeroRowCheckTest (SparkSession lifecycle to copy): `dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java`
- DistributionCheck (column-type iteration, Spark agg functions, per-column metrics): `dqs-spark/src/main/java/com/bank/dqs/checks/DistributionCheck.java`
- DistributionCheckTest (SparkSession lifecycle with explicit schema, RowFactory with typed values): `dqs-spark/src/test/java/com/bank/dqs/checks/DistributionCheckTest.java`
- DqsJob (register here): `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
- DatasetContext: `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- MetricDetail: `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- MetricNumeric: `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- DqsConstants (EXPIRY_SENTINEL — not used in this check but referenced for context): `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
- DDL schema (no changes needed): `dqs-serve/src/serve/schema/ddl.sql`
- Project Context — Java rules + anti-patterns: `_bmad-output/project-context.md`
- Architecture — Check extensibility: `_bmad-output/planning-artifacts/architecture.md`
- Previous stories: `_bmad-output/implementation-artifacts/6-1-sla-countdown-check.md`, `_bmad-output/implementation-artifacts/6-2-zero-row-check.md`, `_bmad-output/implementation-artifacts/6-3-breaking-change-check.md`, `_bmad-output/implementation-artifacts/6-4-distribution-checks-with-eventattribute-explosion.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation proceeded without issues.

### Completion Notes List

- Implemented `TimestampSanityCheck.java` — pure Spark DataFrame check, no JDBC providers. Iterates `TimestampType`/`DateType` columns, uses Spark filter+count for `future_pct` and `stale_pct` per column. Emits `MetricNumeric` for both percentages and `MetricDetail` with PASS/FAIL status (5% threshold), plus a summary `MetricDetail`.
- `TimestampColumnResult` inner record includes `futureCount`, `staleCount`, `nonNullCount` fields (beyond the story's minimum spec) to enable informative JSON payloads matching the Dev Notes metric output structure.
- Divide-by-zero guard: `nonNullCount == 0` → both pcts = 0.0, status=PASS.
- Registered in `DqsJob.buildCheckFactory()` after `DistributionCheck` and before `DqsScoreCheck`; added import and updated Javadoc.
- Removed all 11 `@Disabled` annotations from the ATDD test file (RED→GREEN phase).
- Full regression suite: **227 tests, 0 failures, 0 errors** (was 216 before this story; added 11 new tests).

### File List

- `dqs-spark/src/main/java/com/bank/dqs/checks/TimestampSanityCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` (MODIFIED — import + registration + Javadoc)
- `dqs-spark/src/test/java/com/bank/dqs/checks/TimestampSanityCheckTest.java` (MODIFIED — removed @Disabled annotations)

### Change Log

- 2026-04-04: Story 6-5 implemented — TimestampSanityCheck created, registered in DqsJob, 11 ATDD tests enabled and passing. Test suite grew from 216 to 227 tests, 0 failures.
- 2026-04-04: Code review complete — 4 patch findings fixed, 1 deferred. All 227 tests pass.

### Review Findings

- [x] [Review][Patch] Dead constant `DETAIL_TYPE_STATUS` never used — renamed to `DETAIL_TYPE_COLUMN` with accurate Javadoc [TimestampSanityCheck.java:59]
- [x] [Review][Patch] `metrics` not cleared before appending error detail on exception — added `metrics.clear()` to match `DistributionCheck` pattern and prevent partial column results mixing with error detail [TimestampSanityCheck.java:171]
- [x] [Review][Patch] `total_rows` payload field holds `nonNullCount` not actual row count — renamed to `non_null_rows` to accurately reflect the value [TimestampSanityCheck.java:279]
- [x] [Review][Patch] Compound FAIL (both future and stale thresholds exceeded) only reported first reason — added compound reason string when both thresholds exceeded [TimestampSanityCheck.java:272]
- [x] [Review][Patch] Missing null guard for `context.getPartitionDate()` returning null — added explicit guard returning NOT_RUN detail [TimestampSanityCheck.java:118]
- [x] [Review][Defer] Column names containing `.` or spaces create ambiguous metric name keys (e.g., `future_pct.event.ts`) — deferred, pre-existing pattern from DistributionCheck
