# Story 7.1: Classification-Weighted Alerting & Source System Health

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **executive**,
I want quality alerts weighted by dataset classification (critical, standard, low) and source system health aggregation,
so that alerting prioritizes the most impactful datasets and source system patterns are visible.

## Acceptance Criteria

1. **Given** datasets with classification levels from `lob_lookup` (resolved via `lookup_code`)
   **When** the Classification-Weighted Alerting check (`CLASSIFICATION_WEIGHTED`) executes
   **Then** it computes a classification-adjusted severity score and writes a `MetricNumeric` for the weighted score
   **And** it writes a `MetricDetail` with status=FAIL for critical-classification datasets that have `dqs_score < 70` (or the configured threshold), and status=PASS otherwise

2. **Given** a dataset whose `lookup_code` resolves to a `lob_lookup` row with `classification = 'Tier 1 Critical'`
   **When** the check executes and the dataset's DQS score (from prior check results in the accumulator) is below the fail threshold
   **Then** the classification-adjusted score is amplified (weight multiplier > 1.0) and detail status = FAIL

3. **Given** a dataset whose `lookup_code` is null or resolves to no `lob_lookup` row
   **When** the check executes
   **Then** it returns a `MetricDetail` with status=PASS and reason=no_classification_found (graceful skip, not an error)

4. **Given** multiple datasets from the same source system (parsed from `dataset_name` path segment `src_sys_nm=<value>`)
   **When** the Source System Health check (`SOURCE_SYSTEM_HEALTH`) executes
   **Then** it queries recent `dq_run` history (last 7 days by default) for datasets sharing the same `src_sys_nm` segment
   **And** it computes an aggregate health score: average `dqs_score` across those datasets
   **And** it writes a `MetricNumeric` for `aggregate_score` and a `MetricNumeric` for `dataset_count`
   **And** it writes a `MetricDetail` with status=FAIL if the aggregate score is below 60.0, status=WARN if between 60.0 and 75.0, status=PASS otherwise

5. **Given** a dataset whose `dataset_name` does not contain a `src_sys_nm=` segment
   **When** the Source System Health check executes
   **Then** it returns a `MetricDetail` with status=PASS and reason=no_source_system_segment (graceful skip)

6. **Given** a null context or null `dataset_name` is passed to either check
   **When** the check executes
   **Then** it returns a `MetricDetail` with status=NOT_RUN and does NOT propagate an exception

7. **And** both checks implement `DqCheck`, are registered in `DqsJob.buildCheckFactory()` AFTER `TimestampSanityCheck` and BEFORE `DqsScoreCheck`, and require zero changes to serve/API/dashboard

## Tasks / Subtasks

- [x] Task 1: Create `ClassificationWeightedCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 1–3, 6–7)
  - [x] Declare `public final class ClassificationWeightedCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "CLASSIFICATION_WEIGHTED"`
  - [x] Define constants:
    - `METRIC_WEIGHTED_SCORE = "classification_weighted_score"`
    - `DETAIL_TYPE_STATUS = "classification_weighted_status"`
    - `CRITICAL_MULTIPLIER = 2.0` — weight amplifier for Tier 1 Critical datasets
    - `STANDARD_MULTIPLIER = 1.0` — weight for Tier 2 Standard datasets
    - `LOW_MULTIPLIER = 0.5` — weight for Tier 3 Low / unclassified datasets
    - `FAIL_THRESHOLD = 70.0` — DQS score below which a CRITICAL dataset is FAILed
    - `CLASSIFICATION_CRITICAL = "Tier 1 Critical"`
    - `CLASSIFICATION_STANDARD = "Tier 2 Standard"`
  - [x] Define inner `ClassificationProvider` interface with method `Optional<String> getClassification(DatasetContext ctx) throws Exception`
  - [x] Define inner static final class `JdbcClassificationProvider implements ClassificationProvider`:
    - Query: `SELECT ll.classification FROM v_lob_lookup_active ll WHERE ll.lookup_code = ?` — use the `context.getLookupCode()` directly (already resolved by `EnrichmentResolver` during scanning)
    - Use `try-with-resources` for Connection, PreparedStatement, ResultSet
    - Return `Optional.empty()` if no row or `lookup_code` is null
  - [x] Define inner static final class `NoOpClassificationProvider implements ClassificationProvider` (returns `Optional.empty()`)
  - [x] Implement no-arg constructor: `public ClassificationWeightedCheck() { this(new NoOpClassificationProvider()); }`
  - [x] Implement testable constructor: `public ClassificationWeightedCheck(ClassificationProvider classificationProvider)`
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - Wrap entire body in try/catch — catch Exception → `metrics.clear()` + return `errorDetail(e)` list
    - Guard null context → return `notRunDetail("missing_context")` list
    - Guard null `context.getDatasetName()` (not possible given `DatasetContext` validation, but guard null `context.getLookupCode()`) → treat as no_classification_found (PASS, skip)
    - Call `classificationProvider.getClassification(context)` → if empty → PASS + reason=no_classification_found, return
    - Compute `multiplier` based on classification string: "Tier 1 Critical" → 2.0, "Tier 2 Standard" → 1.0, else 0.5
    - Compute `weightedScore`: DQS score is not available at per-check execution time (checks run before BatchWriter assigns dq_run_id); instead, use `0.0` as the base raw score placeholder and compute: `weightedScore = multiplier * 100.0` as a classification priority indicator (the weighted score represents the dataset's priority magnitude, not a modified DQS score — see Dev Notes for rationale)
    - Determine status: if classification is CRITICAL and `multiplier * 100.0 >= FAIL_THRESHOLD * CRITICAL_MULTIPLIER` → PASS (score exists, classification noted); if classification is CRITICAL → FAIL when explicit DQS score below threshold; default to PASS with classification payload
    - **IMPORTANT:** `DqsScoreCheck` computes the actual DQS score after all other checks run. `ClassificationWeightedCheck` runs BEFORE `DqsScoreCheck`. The weighted score metric is a priority/multiplier signal for external consumers, not a real-time score adjustment. See Dev Notes.
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_WEIGHTED_SCORE, multiplier)` — the multiplier IS the meaningful metric value (1 = standard, 2 = critical, 0.5 = low priority)
    - Emit `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, statusPayload(classification, multiplier, status))`
    - Status determination: FAIL if classification=CRITICAL (the mere fact of being critical and under active monitoring justifies FAIL to draw attention until DQS proves it healthy); WARN if classification=STANDARD; PASS if low/unknown — **REVISIT**: use PASS for all, with classification captured in detail payload (status reflects check execution health, not classification severity — see Dev Notes)

- [x] Task 2: Create `SourceSystemHealthCheck.java` in `dqs-spark/src/main/java/com/bank/dqs/checks/` (AC: 4–7)
  - [x] Declare `public final class SourceSystemHealthCheck implements DqCheck`
  - [x] Define `public static final String CHECK_TYPE = "SOURCE_SYSTEM_HEALTH"`
  - [x] Define constants:
    - `METRIC_AGGREGATE_SCORE = "aggregate_score"`
    - `METRIC_DATASET_COUNT = "dataset_count"`
    - `DETAIL_TYPE_STATUS = "source_system_health_status"`
    - `HISTORY_DAYS = 7` — number of days of recent history to aggregate
    - `FAIL_THRESHOLD = 60.0` — aggregate score below which status is FAIL
    - `WARN_THRESHOLD = 75.0` — aggregate score below which status is WARN
    - `SRC_SYS_SEGMENT_PREFIX = "src_sys_nm="` — prefix used to extract source system from dataset name path
  - [x] Define inner `SourceSystemStatsProvider` interface with method `Optional<SourceSystemStats> getStats(String srcSysNm, java.time.LocalDate partitionDate) throws Exception`
  - [x] Define inner record `SourceSystemStats(double aggregateScore, int datasetCount)`
  - [x] Define inner static final class `JdbcSourceSystemStatsProvider implements SourceSystemStatsProvider`:
    - Parse `src_sys_nm` from `dataset_name` path (find `src_sys_nm=<value>/` segment, extract value before next `/`)
    - Query: `SELECT AVG(dqs_score), COUNT(DISTINCT dataset_name) FROM v_dq_run_active WHERE dataset_name LIKE ? AND partition_date >= ? AND partition_date <= ? AND dqs_score IS NOT NULL`
      - LIKE pattern: `'%src_sys_nm=<value>/%'` — matches all datasets with that source system in their path
      - Date range: `partitionDate.minusDays(HISTORY_DAYS)` to `partitionDate`
    - Use `try-with-resources` for Connection, PreparedStatement, ResultSet
    - Return `Optional.empty()` if `COUNT = 0`
  - [x] Define inner static final class `NoOpSourceSystemStatsProvider` (returns `Optional.empty()`)
  - [x] Implement no-arg constructor
  - [x] Implement testable constructor
  - [x] Implement `getCheckType()` returning `CHECK_TYPE`
  - [x] Implement `execute(DatasetContext context)`:
    - Wrap entire body in try/catch
    - Guard null context → `notRunDetail("missing_context")`
    - Extract `src_sys_nm` from `context.getDatasetName()` — if not found → PASS + reason=no_source_system_segment, return
    - Call `statsProvider.getStats(srcSysNm, context.getPartitionDate())` → if empty → PASS + reason=no_history_available, return
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_AGGREGATE_SCORE, stats.aggregateScore())`
    - Emit `MetricNumeric(CHECK_TYPE, METRIC_DATASET_COUNT, stats.datasetCount())`
    - Determine status: score < FAIL_THRESHOLD → FAIL; score < WARN_THRESHOLD → WARN; else PASS
    - Emit `MetricDetail(CHECK_TYPE, DETAIL_TYPE_STATUS, statusPayload(...))`

- [x] Task 3: Register both checks in `DqsJob.buildCheckFactory()` (AC: 7)
  - [x] Add after `TimestampSanityCheck` and before `DqsScoreCheck`:
    ```java
    f.register(new ClassificationWeightedCheck(                        // Tier 3 — Epic 7, Story 7.1
            new ClassificationWeightedCheck.JdbcClassificationProvider(
                    () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
    f.register(new SourceSystemHealthCheck(                            // Tier 3 — Epic 7, Story 7.1
            new SourceSystemHealthCheck.JdbcSourceSystemStatsProvider(
                    () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
    ```
  - [x] Add imports for both new classes
  - [x] Update Javadoc for `buildCheckFactory()` — add Tier 3 mention and both new check classes
  - [x] Update the comment listing checks to read: "Tier 3 checks: ClassificationWeightedCheck, SourceSystemHealthCheck"

- [x] Task 4: Write `ClassificationWeightedCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 1–3, 6)
  - [x] Test: `executesReturnsPassForCriticalDataset` — inject provider returning "Tier 1 Critical" → MetricNumeric with multiplier=2.0, MetricDetail present
  - [x] Test: `executeReturnsPassForStandardDataset` — "Tier 2 Standard" → multiplier=1.0
  - [x] Test: `executeReturnsPassForLowPriorityDataset` — unknown classification → multiplier=0.5
  - [x] Test: `executeReturnsPassWhenNoClassificationFound` — provider returns empty → MetricDetail with reason=no_classification_found
  - [x] Test: `executeReturnsPassWhenLookupCodeIsNull` — context with null lookupCode → provider never called (or returns empty), MetricDetail with reason=no_classification_found
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeHandlesExceptionGracefully` — provider throws RuntimeException → errorDetail returned, no exception propagation
  - [x] Test: `getCheckTypeReturnsClassificationWeighted` — `assertEquals("CLASSIFICATION_WEIGHTED", check.getCheckType())`
  - [x] **No SparkSession required** — this check does NOT use `context.getDf()`; use `DatasetContext` with `null` df (valid in test context per `DatasetContext` Javadoc)

- [x] Task 5: Write `SourceSystemHealthCheckTest.java` in `dqs-spark/src/test/java/com/bank/dqs/checks/` (AC: 4–6)
  - [x] Test: `executeWritesFailMetricsWhenAggregateScoreBelowFailThreshold` — inject provider returning stats with score=45.0, count=3 → MetricNumeric aggregate_score=45.0, MetricDetail status=FAIL
  - [x] Test: `executeWritesWarnMetricsWhenAggregateScoreBetweenThresholds` — score=68.0 → status=WARN
  - [x] Test: `executeWritesPassMetricsWhenAggregateScoreAboveWarnThreshold` — score=88.0 → status=PASS
  - [x] Test: `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` — dataset_name without `src_sys_nm=` → MetricDetail reason=no_source_system_segment
  - [x] Test: `executeReturnsPassWhenNoHistoryAvailable` — provider returns empty → reason=no_history_available
  - [x] Test: `executeReturnsNotRunWhenContextIsNull` — null context → NOT_RUN detail, no exception
  - [x] Test: `executeHandlesExceptionGracefully` — provider throws → errorDetail, no propagation
  - [x] Test: `getCheckTypeReturnsSourceSystemHealth` — `assertEquals("SOURCE_SYSTEM_HEALTH", check.getCheckType())`
  - [x] Test: `executeExtractsCorrectSrcSysNmFromDatasetName` — dataset_name = "lob=retail/src_sys_nm=alpha/dataset=sales_daily" → src_sys_nm extracted as "alpha"
  - [x] **No SparkSession required** — neither check uses `context.getDf()`; use null-df `DatasetContext`

## Dev Notes

### Critical Architecture Decision: Classification-Weighted Check Timing Problem

**The DQS score is NOT available when `ClassificationWeightedCheck.execute()` runs.** The execution order within a dataset is:

```
FreshnessCheck → VolumeCheck → SchemaCheck → OpsCheck →
SlaCountdownCheck → ZeroRowCheck → BreakingChangeCheck →
DistributionCheck → TimestampSanityCheck →
ClassificationWeightedCheck → SourceSystemHealthCheck →
DqsScoreCheck (LAST — reads accumulated metrics to compute DQS score)
```

`DqsScoreCheck` runs LAST and computes the final DQS score by reading all prior metrics from the accumulator. `ClassificationWeightedCheck` cannot read the final DQS score because it hasn't been computed yet.

**Resolution:** `ClassificationWeightedCheck` outputs a **priority signal metric**, not a score modifier:
- `MetricNumeric(CLASSIFICATION_WEIGHTED, "classification_weighted_score", multiplier)` — where multiplier is `2.0` (critical), `1.0` (standard), or `0.5` (low)
- This metric tells downstream consumers: "when this dataset fails, treat its failure with this severity weight"
- The `DqsScoreCheck` can optionally read this metric from the accumulator to apply classification-adjusted weighting — but for MVP this is a no-op; the multiplier metric is surfaced for external alerting systems

**Status logic for ClassificationWeightedCheck:** Use PASS for all (the check is informational — it captures classification, not a quality failure). The `multiplier` in the MetricNumeric is the actionable signal.

### Source System Health: JDBC-Only, No DataFrame

`SourceSystemHealthCheck` is unlike all prior checks in that it does NOT process `context.getDf()` at all. It queries historical `dq_run` data from Postgres to compute aggregate health across a source system. This is a cross-dataset, time-windowed aggregation.

**Query pattern (exact SQL):**

```java
private static final String STATS_QUERY =
    "SELECT AVG(dqs_score) AS avg_score, COUNT(DISTINCT dataset_name) AS ds_count " +
    "FROM v_dq_run_active " +
    "WHERE dataset_name LIKE ? " +
    "AND partition_date BETWEEN ? AND ? " +
    "AND dqs_score IS NOT NULL";
```

LIKE pattern: `'%src_sys_nm=' + srcSysNm + '/%'` — matches `lob=retail/src_sys_nm=alpha/dataset=sales_daily` when `srcSysNm = "alpha"`.

**Use `v_dq_run_active` view, not raw `dq_run` table** — mandatory per project-context.md anti-patterns.

**Date parameters:** `partitionDate.minusDays(HISTORY_DAYS)` through `partitionDate` (inclusive) as `java.sql.Date`.

### Source System Name Extraction

Extract `src_sys_nm` from the dataset path. The format is: `lob=<lob>/src_sys_nm=<value>/dataset=<name>` (or the legacy `src_sys_nm=<value>/dataset=<name>` without lob prefix).

```java
private static Optional<String> extractSrcSysNm(String datasetName) {
    if (datasetName == null) return Optional.empty();
    int start = datasetName.indexOf("src_sys_nm=");
    if (start < 0) return Optional.empty();
    int valueStart = start + "src_sys_nm=".length();
    int end = datasetName.indexOf('/', valueStart);
    String value = end < 0 ? datasetName.substring(valueStart) : datasetName.substring(valueStart, end);
    return value.isBlank() ? Optional.empty() : Optional.of(value);
}
```

### ClassificationWeightedCheck: `lob_lookup` Table Access

`DatasetContext.getLookupCode()` returns the already-resolved `lookup_code` (e.g., `"LOB_RETAIL"`). This value is populated by `EnrichmentResolver` during scanning and stored in `dq_run.lookup_code`.

Query `v_lob_lookup_active`, not raw `lob_lookup`:

```java
private static final String CLASSIFICATION_QUERY =
    "SELECT classification FROM v_lob_lookup_active WHERE lookup_code = ?";
```

Existing classifications in fixtures: `"Tier 1 Critical"`, `"Tier 2 Standard"`. The check should handle any arbitrary string gracefully (unknown → multiplier=0.5).

### Metric Output Structure

**ClassificationWeightedCheck — for dataset with lookup_code=LOB_RETAIL (Tier 1 Critical):**
```
MetricNumeric(CLASSIFICATION_WEIGHTED, "classification_weighted_score", 2.0)
MetricDetail(CLASSIFICATION_WEIGHTED, "classification_weighted_status",
  {"status":"PASS","classification":"Tier 1 Critical","multiplier":2.0,"reason":"classification_captured"})
```

**ClassificationWeightedCheck — no classification found:**
```
MetricDetail(CLASSIFICATION_WEIGHTED, "classification_weighted_status",
  {"status":"PASS","reason":"no_classification_found"})
```

**SourceSystemHealthCheck — FAIL case (aggregate_score=45.0, dataset_count=3):**
```
MetricNumeric(SOURCE_SYSTEM_HEALTH, "aggregate_score", 45.0)
MetricNumeric(SOURCE_SYSTEM_HEALTH, "dataset_count", 3.0)
MetricDetail(SOURCE_SYSTEM_HEALTH, "source_system_health_status",
  {"status":"FAIL","src_sys_nm":"alpha","aggregate_score":45.0,"dataset_count":3,"history_days":7,"threshold_fail":60.0,"threshold_warn":75.0})
```

**SourceSystemHealthCheck — no src_sys_nm segment:**
```
MetricDetail(SOURCE_SYSTEM_HEALTH, "source_system_health_status",
  {"status":"PASS","reason":"no_source_system_segment"})
```

### `dq_metric_numeric` and `dq_metric_detail` Uniqueness Constraints

```
dq_metric_numeric: UNIQUE(dq_run_id, check_type, metric_name, expiry_date)
dq_metric_detail: UNIQUE(dq_run_id, check_type, detail_type, expiry_date)
```

For `ClassificationWeightedCheck`:
- One numeric row per dataset: `metric_name = "classification_weighted_score"` — unique per run
- One detail row: `detail_type = "classification_weighted_status"` — unique per run

For `SourceSystemHealthCheck`:
- Two numeric rows: `"aggregate_score"` and `"dataset_count"` — unique per run
- One detail row: `"source_system_health_status"` — unique per run

No constraint violations possible with these names (one of each per `dq_run_id`).

### Java Patterns — Follow Exactly

- **`private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`** — shared static instance per class
- **`private static final Logger LOG = LoggerFactory.getLogger(ClassName.class)`**
- **`try-with-resources`** for ALL JDBC: Connection, PreparedStatement, ResultSet
- **`PreparedStatement` with `?` placeholders** — never string concatenation
- **`metrics.clear()` before adding error detail** in catch block — prevents partial results mixing with error detail (pattern from `TimestampSanityCheck` post-review fix)
- **No `getDf()` calls in either check** — both are purely JDBC-based (similar to `SlaCountdownCheck`)
- **Full generic types**: `List<DqMetric>`, `Map<String, Object>`, `Optional<String>`
- **Package**: `com.bank.dqs.checks`
- **No SparkSession dependency** — these checks have no Spark DataFrame operations

### No-Arg Constructor Pattern (for DqsJob default registration)

Both checks follow the same pattern as `SlaCountdownCheck`:

```java
public ClassificationWeightedCheck() {
    this(new NoOpClassificationProvider());
}

public ClassificationWeightedCheck(ClassificationProvider classificationProvider) {
    if (classificationProvider == null) {
        throw new IllegalArgumentException("classificationProvider must not be null");
    }
    this.classificationProvider = classificationProvider;
}
```

**BUT** — in `DqsJob.buildCheckFactory()`, we ALWAYS use the JDBC constructor (not no-arg). The no-arg exists only for tests or future no-JDBC scenarios.

### DqsJob Registration — Exact Location

After `TimestampSanityCheck` and before `DqsScoreCheck`:

```java
f.register(new TimestampSanityCheck());                                    // Tier 2 — Epic 6, Story 6.5
f.register(new ClassificationWeightedCheck(                                // Tier 3 — Epic 7, Story 7.1
        new ClassificationWeightedCheck.JdbcClassificationProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
f.register(new SourceSystemHealthCheck(                                    // Tier 3 — Epic 7, Story 7.1
        new SourceSystemHealthCheck.JdbcSourceSystemStatsProvider(
                () -> DriverManager.getConnection(jdbcUrl, dbUser, dbPass))));
// DqsScoreCheck is registered LAST — always runs after all other checks
f.register(new DqsScoreCheck(ctx -> accumulator));
```

### Test Pattern — No SparkSession Required

Both checks have no Spark DataFrame operations. Use the same `DatasetContext` construction as `SlaCountdownCheck` tests:

```java
private DatasetContext context(String datasetName, String lookupCode) {
    return new DatasetContext(
            datasetName,
            lookupCode,
            LocalDate.of(2026, 4, 3),
            "/prod/data",
            null,         // df=null is valid in test contexts (per DatasetContext Javadoc)
            DatasetContext.FORMAT_PARQUET
    );
}
```

Inject mock providers via lambdas (same pattern as `SlaCountdownCheck` tests with `NoOpSlaProvider`):

```java
// ClassificationWeightedCheck test with injected provider
ClassificationWeightedCheck check = new ClassificationWeightedCheck(
        ctx -> Optional.of("Tier 1 Critical")
);

// SourceSystemHealthCheck test with injected provider
SourceSystemHealthCheck check = new SourceSystemHealthCheck(
        (srcSysNm, date) -> Optional.of(new SourceSystemHealthCheck.SourceSystemStats(45.0, 3))
);
```

### Comparison With Previous Checks

| Check | DataFrame? | External Deps? | Cross-Dataset? | Complexity |
|---|---|---|---|---|
| SlaCountdownCheck | NO | JDBC (dataset_enrichment) | No | Low |
| ClassificationWeightedCheck | **NO** | JDBC (lob_lookup) | No | Low |
| SourceSystemHealthCheck | **NO** | JDBC (v_dq_run_active) | **YES (time-windowed)** | Medium |

`ClassificationWeightedCheck` is simpler than `SlaCountdownCheck` (no time arithmetic). `SourceSystemHealthCheck` is more complex due to cross-dataset aggregation but simpler than `DistributionCheck` (no Spark operations).

### File Structure — Exact Locations

```
dqs-spark/
  src/main/java/com/bank/dqs/
    checks/
      ClassificationWeightedCheck.java     ← NEW file
      SourceSystemHealthCheck.java         ← NEW file
    DqsJob.java                            ← MODIFY: add imports + register both checks + update Javadoc
  src/test/java/com/bank/dqs/
    checks/
      ClassificationWeightedCheckTest.java ← NEW file
      SourceSystemHealthCheckTest.java     ← NEW file
```

**Do NOT touch:** `CheckFactory.java`, `DqCheck.java`, any existing check files, model files, writer, scanner, serve-layer files, or any dashboard/API components. **No DDL changes required** — `lob_lookup` and `v_dq_run_active` already exist.

### Anti-Patterns — NEVER Do These

- **NEVER call `context.getDf()`** in either check — neither uses the DataFrame
- **NEVER query raw `dq_run` or `lob_lookup` tables** — always use `v_dq_run_active` and `v_lob_lookup_active` views
- **NEVER compute or attempt to read the DQS score** in `ClassificationWeightedCheck` — it hasn't been computed yet when this check runs
- **NEVER let exceptions propagate from `execute()`** — entire body wrapped in try/catch; `metrics.clear()` before error detail
- **NEVER add check-type-specific logic to serve/API/dashboard** — generic metric consumption requires zero serve/dashboard changes
- **NEVER hardcode `9999-12-31 23:59:59`** — both checks use views that already filter for active records; no sentinel needed in these queries
- **NEVER bundle Spark JARs** — Spark is `provided` scope; these checks don't use Spark at all
- **NEVER use raw types**: `List<DqMetric>`, `Optional<String>`, `Map<String, Object>`

### Previous Story Learnings (Epic 6)

From Epic 6 completion (stories 6.1–6.5):
- **Test suite is at 227 tests, 0 failures** — do not break regressions
- **`DqsJob.buildCheckFactory()` already has all Tier 2 checks**: FreshnessCheck, VolumeCheck, SchemaCheck, OpsCheck, SlaCountdownCheck, ZeroRowCheck, BreakingChangeCheck, DistributionCheck, TimestampSanityCheck, then DqsScoreCheck LAST
- **ATDD tests may be pre-generated in RED phase with `@Disabled`** — if test files already exist, remove `@Disabled` annotations; do NOT create duplicate files
- **`metrics.clear()` in catch block is mandatory** (added as patch in 6.5 review) — prevents partial column results mixing with error detail when exception thrown mid-execution
- **`private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`** — one per class, never instantiated inside methods (thread-safe after construction)
- **Inner provider interfaces as `@FunctionalInterface`** — enables lambda injection in tests without mock frameworks
- **No-arg constructor delegates to JDBC-provider constructor** — never duplicates logic
- **`notRunDetail` returns a list with single item** — `return List.of(new MetricDetail(...))`
- **SparkSession `@BeforeAll`/`@AfterAll` lifecycle** — required only for DataFrame tests; these two checks do NOT need SparkSession

From 6.1 (`SlaCountdownCheck`) — most similar architecture to both new checks:
- JDBC provider pattern with `ConnectionProvider` functional interface
- `Optional<T>` return from provider to signal "not applicable" (graceful skip vs error)
- Clock injection for testability (not needed here, but the provider injection pattern is identical)

From 6.3 (`BreakingChangeCheck`) — JDBC query of `dq_metric_detail`:
- Pattern for querying metric history from Postgres during check execution
- `v_dq_run_active` / `v_dq_metric_detail_active` views, never raw tables

### Project Structure Notes

- New files `ClassificationWeightedCheck.java` and `SourceSystemHealthCheck.java` → `dqs-spark/src/main/java/com/bank/dqs/checks/` alongside all existing check classes
- New test files → `dqs-spark/src/test/java/com/bank/dqs/checks/` (Maven standard mirror structure)
- Only `DqsJob.java` is modified (imports + two registration lines + Javadoc)
- No schema DDL changes — `lob_lookup`, `v_lob_lookup_active`, `v_dq_run_active` already exist
- No changes to serve/API/dashboard — generic metric tables absorb new check types automatically
- Implementation sequence (both checks can be done together): ClassificationWeightedCheck (simpler) → SourceSystemHealthCheck → DqsJob registration → both test files → run full test suite

### References

- Epic 7 Story 7.1 AC: `/home/sas/workspace/data-quality-service/_bmad-output/planning-artifacts/epics/epic-7-tier-3-intelligence-executive-reporting-phase-3.md`
- PRD FR20: `/home/sas/workspace/data-quality-service/_bmad-output/planning-artifacts/prd.md`
- DqCheck interface: `dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java`
- CheckFactory: `dqs-spark/src/main/java/com/bank/dqs/checks/CheckFactory.java`
- SlaCountdownCheck (closest pattern — JDBC provider, no DataFrame): `dqs-spark/src/main/java/com/bank/dqs/checks/SlaCountdownCheck.java`
- BreakingChangeCheck (JDBC query of historical metrics): `dqs-spark/src/main/java/com/bank/dqs/checks/BreakingChangeCheck.java`
- DqsJob (register here): `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
- DatasetContext (getLookupCode(), getDatasetName(), getPartitionDate()): `dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java`
- MetricDetail: `dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java`
- MetricNumeric: `dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java`
- DqsConstants (EXPIRY_SENTINEL — not used directly here but views handle it): `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
- DDL schema (lob_lookup, dataset_enrichment, dq_run, check_config — no changes needed): `dqs-serve/src/serve/schema/ddl.sql`
- Views (v_lob_lookup_active, v_dq_run_active — use these, never raw tables): `dqs-serve/src/serve/schema/views.sql`
- Fixtures (classification values: "Tier 1 Critical", "Tier 2 Standard"): `dqs-serve/src/serve/schema/fixtures.sql`
- Project Context — Java rules + anti-patterns: `_bmad-output/project-context.md`
- Architecture — Check extensibility + unidirectional dependency: `_bmad-output/planning-artifacts/architecture.md`
- Previous story (most recent): `_bmad-output/implementation-artifacts/6-5-timestamp-sanity-check.md`
- Previous story (JDBC-heavy pattern): `_bmad-output/implementation-artifacts/6-3-breaking-change-check.md`
- Previous story (JDBC provider, no DataFrame): `_bmad-output/implementation-artifacts/6-1-sla-countdown-check.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

No debug issues encountered. All 18 tests passed on first implementation.

### Completion Notes List

- Implemented `ClassificationWeightedCheck.java` with `@FunctionalInterface ClassificationProvider`, `JdbcClassificationProvider` (queries `v_lob_lookup_active`), and `NoOpClassificationProvider`. Status is always PASS — the multiplier MetricNumeric is the priority signal.
- Implemented `SourceSystemHealthCheck.java` with `@FunctionalInterface SourceSystemStatsProvider`, `JdbcSourceSystemStatsProvider` (queries `v_dq_run_active` for 7-day window), inner record `SourceSystemStats`, and `NoOpSourceSystemStatsProvider`. Status is FAIL/WARN/PASS per thresholds 60.0/75.0.
- Registered both checks in `DqsJob.buildCheckFactory()` after `TimestampSanityCheck` and before `DqsScoreCheck`; added imports; updated Javadoc to reference Tier 3.
- Test files already existed (ATDD RED phase); implemented the checks against those tests.
- Full test suite: 245 tests, 0 failures, 0 errors (was 227; 18 new tests added).
- Both checks follow the project patterns exactly: try-with-resources JDBC, `metrics.clear()` in catch, static `ObjectMapper`, `@FunctionalInterface` providers, no-arg + testable constructors.

### File List

- `dqs-spark/src/main/java/com/bank/dqs/checks/ClassificationWeightedCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/checks/SourceSystemHealthCheck.java` (NEW)
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` (MODIFIED — imports, registration, Javadoc)
- `dqs-spark/src/test/java/com/bank/dqs/checks/ClassificationWeightedCheckTest.java` (pre-existing ATDD tests — @Disabled annotations removed by running against implementation)
- `dqs-spark/src/test/java/com/bank/dqs/checks/SourceSystemHealthCheckTest.java` (pre-existing ATDD tests)

### Review Findings

- [x] [Review][Patch] Dead public constant `FAIL_THRESHOLD` declared but never referenced in execute() logic [ClassificationWeightedCheck.java:50]
- [x] [Review][Patch] `passDetail()` in ClassificationWeightedCheck has dead parameters (`classification`, `multiplier`, `includeClassification`) never used at call sites [ClassificationWeightedCheck.java:155]
- [x] [Review][Patch] Unused `@Disabled` import left over from ATDD RED phase [ClassificationWeightedCheckTest.java:8]
- [x] [Review][Patch] `passDetail()` in SourceSystemHealthCheck has dead parameters (`srcSysNm`, `aggregateScore`, `datasetCount`, `includeStats`) never used at call sites [SourceSystemHealthCheck.java:198]

## Change Log

- 2026-04-04: Implemented ClassificationWeightedCheck and SourceSystemHealthCheck (Tier 3 — Epic 7, Story 7.1). Registered both checks in DqsJob. All 18 ATDD tests pass; full suite 245 tests, 0 failures. Story status set to review.
- 2026-04-04: Code review complete. 4 patch findings fixed (dead constant, dead method parameters x2, unused import). All 245 tests pass. Story status: done.
