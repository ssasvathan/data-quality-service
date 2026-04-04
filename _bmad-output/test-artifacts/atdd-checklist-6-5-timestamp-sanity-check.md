---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: 2026-04-04
inputDocuments:
  - _bmad-output/implementation-artifacts/6-5-timestamp-sanity-check.md
  - _bmad/tea/config.yaml
  - dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/DistributionCheckTest.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
---

# ATDD Checklist: Story 6-5 — Timestamp Sanity Check

## Story Summary

**As a** data steward,
**I want** the Timestamp Sanity check to detect out-of-range or future-dated timestamps,
**So that** time-based anomalies in source data are flagged.

**Stack detected:** backend (Java/Maven/Spark — pure Spark job layer, no new endpoints or UI)
**Test framework:** JUnit 5 + Apache Spark (local[1])
**TDD Phase:** RED — failing tests generated, `TimestampSanityCheck.java` not yet implemented

---

## TDD Red Phase Status

**CURRENT STATE: RED**

- [x] Failing tests generated
- [ ] `TimestampSanityCheck.java` implemented (GREEN phase — DEV responsibility)
- [ ] `@Disabled` removed from all test methods (GREEN phase)
- [ ] All tests passing (GREEN phase confirmed)

All 11 test methods are annotated with `@Disabled("ATDD RED PHASE — TimestampSanityCheck.java not yet implemented")`.
The class will not compile until `TimestampSanityCheck.java` is created in `dqs-spark/src/main/java/com/bank/dqs/checks/`.

---

## Acceptance Criteria Coverage

| AC | Description | Test Method | Priority | Status |
|---|---|---|---|---|
| AC1 | >5% future-dated in a column → MetricNumeric with future_pct + MetricDetail status=FAIL | `executeFlagsColumnAsFailWhenFuturePctExceedsThreshold` | P0 | RED |
| AC2 | ≤5% future-dated → MetricNumeric + MetricDetail status=PASS | `executePassesColumnWhenFuturePctBelowThreshold` | P0 | RED |
| AC3 | >5% beyond max-age threshold → stale_pct MetricNumeric + MetricDetail status=FAIL | `executeFlagsColumnAsFailWhenStalePctExceedsThreshold` | P0 | RED |
| AC4 | No timestamp/date columns → MetricDetail status=PASS reason=no_timestamp_columns, no further metrics | `executePassesWhenNoTimestampColumns` | P0 | RED |
| AC5 | Null context → MetricDetail status=NOT_RUN, no exception | `executeReturnsNotRunWhenContextIsNull` | P0 | RED |
| AC5 | Null DataFrame → MetricDetail status=NOT_RUN, no exception | `executeReturnsNotRunWhenDataFrameIsNull` | P0 | RED |
| AC6 | implements DqCheck, registered in CheckFactory, zero changes to serve/API/dashboard | `getCheckTypeReturnsTimestampSanity` | P1 | RED |
| Edge | All-null timestamp column → future_pct=0.0, stale_pct=0.0, PASS (divide-by-zero guard) | `executeHandlesAllNullTimestampColumn` | P1 | RED |
| Additional | Both MetricNumeric entries (future_pct.col + stale_pct.col) emitted per column | `executeWritesMetricNumericForFutureAndStalePct` | P0 | RED |
| Additional | One FAIL column + one PASS column → summary MetricDetail status=FAIL | `executeSummaryDetailReflectsOverallStatus` | P1 | RED |
| Additional | DataFrame.schema() throws → errorDetail returned, no exception propagation | `executeHandlesExceptionGracefully` | P1 | RED |

**All 6 acceptance criteria covered (plus 5 additional boundary/error path tests). 11 test methods total.**

---

## Test Files Created

| File | Tests | Phase |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/checks/TimestampSanityCheckTest.java` | 11 | RED |

### Test Method Summary

```
TimestampSanityCheckTest (11 tests, all @Disabled — RED phase)
├── [P0] executeFlagsColumnAsFailWhenFuturePctExceedsThreshold
├── [P0] executePassesColumnWhenFuturePctBelowThreshold
├── [P0] executeFlagsColumnAsFailWhenStalePctExceedsThreshold
├── [P0] executePassesWhenNoTimestampColumns
├── [P0] executeReturnsNotRunWhenContextIsNull
├── [P0] executeReturnsNotRunWhenDataFrameIsNull
├── [P0] executeWritesMetricNumericForFutureAndStalePct
├── [P1] executeHandlesAllNullTimestampColumn
├── [P1] executeSummaryDetailReflectsOverallStatus
├── [P1] getCheckTypeReturnsTimestampSanity
└── [P1] executeHandlesExceptionGracefully
```

---

## Test Design Decisions

### Level: Unit (JUnit 5 + SparkSession)

TimestampSanityCheck uses Spark schema inspection (`df.schema().fields()`) and Spark filter/count
operations — SparkSession is mandatory. Follows the exact SparkSession lifecycle from
`ZeroRowCheckTest` and `DistributionCheckTest`:
- `@BeforeAll static void initSpark()` — creates `local[1]` SparkSession
- `@AfterAll static void stopSpark()` — stops session to free resources

No constructor injection needed — `TimestampSanityCheck` has no-arg constructor, no external JDBC
providers. Thresholds are compile-time constants (`DEFAULT_FUTURE_TOLERANCE_DAYS = 1`,
`DEFAULT_MAX_AGE_YEARS = 10`, `FUTURE_FAIL_THRESHOLD = 0.05`).

### Why NOT E2E or API Tests

AC6 states: "requires zero changes to serve/API/dashboard." There are no new API endpoints,
no new UI components, and no new MCP tools. The entire story impact is confined to the Spark
job layer. E2E and API test generation was skipped — no applicable scenarios (consistent with
stories 6.1, 6.2, 6.3, and 6.4).

### TDD Red Phase Mechanism (Java)

In JUnit 5, `@Disabled` is the Java equivalent of Playwright's `test.skip()`:
- Tests declare correct expected behavior (real assertions, not placeholders)
- `@Disabled("ATDD RED PHASE — TimestampSanityCheck.java not yet implemented")` marks them as
  intentionally failing during RED phase
- Once `TimestampSanityCheck.java` is implemented, remove `@Disabled` to enter GREEN phase
- The class itself will not compile until `TimestampSanityCheck.java` is created (compiler
  catches `TimestampSanityCheck`, `TimestampSanityCheck.CHECK_TYPE`, and
  `TimestampSanityCheck.DETAIL_TYPE_SUMMARY` as unknown symbols)

### Partition Date and Threshold Rationale

- `PARTITION_DATE = LocalDate.of(2026, 4, 3)` — matches other story 6.x tests for consistency
- Future threshold: `2026-04-04` (partition + 1 day) — values after this date are flagged
- Stale threshold: `2016-04-03` (partition - 10 years) — values before this date are flagged
- Test timestamps:
  - `FUTURE_TS = 2026-05-01` — clearly after future threshold
  - `NORMAL_TS = 2025-06-01` — within acceptable range (between stale and future thresholds)
  - `STALE_TS = 2000-01-01` — clearly before stale threshold (26 years before partition date)

### 5% Threshold Coverage

Two test patterns cover the boundary:
- `6/10 rows = 0.60` (60%) — clearly exceeds 5% → FAIL (used for both future and stale tests)
- `0/10 rows = 0.0` (0%) — clearly below 5% → PASS

This matches the story's explicit test data guidance:
> Use 10 rows total, with 6 having future timestamps → future_pct = 0.6 → FAIL
> Use 10 rows total, with 0 having future timestamps → future_pct = 0.0 → PASS

### Divide-by-Zero Guard Coverage

`executeHandlesAllNullTimestampColumn` tests the case where `nonNullCount == 0`, which would
cause division-by-zero without the guard. The check must return 0.0 for both percentages when
all values in the column are null.

### Summary Detail Coverage

`executeSummaryDetailReflectsOverallStatus` uses a two-column DataFrame:
- `event_ts`: 6 future rows → FAIL
- `created_ts`: 0 future rows → PASS

The summary `MetricDetail(TIMESTAMP_SANITY, "timestamp_sanity_summary", ...)` must reflect
overall FAIL because at least one column is FAIL.

### Exception Handling Coverage

`executeHandlesExceptionGracefully` uses Mockito to mock the DataFrame and throw from
`schema()`, testing the outermost try/catch in `execute()`. The check must return an error
detail without propagating the exception.

### Data Sensitivity Boundary

Tests verify:
- Metric names contain column names (structural metadata): `"future_pct.event_ts"`, `"stale_pct.event_ts"`
- Metric values contain aggregate percentages (0.6, 0.0) — not raw timestamp values
- Detail payloads contain `status`, `column`, `future_pct`, `stale_pct` fields — statistical metadata
- No PII, PCI, or raw timestamp string values appear in any test assertion

### Metric Naming Convention (from Dev Notes)

Per the story specification:
- `METRIC_FUTURE_PCT = "future_pct"` → `"future_pct.event_ts"` (appended with column name)
- `METRIC_STALE_PCT = "stale_pct"` → `"stale_pct.event_ts"` (appended with column name)
- Column detail type: `"timestamp_sanity.event_ts"` → unique per column per run
- Summary detail type: `"timestamp_sanity_summary"` → `TimestampSanityCheck.DETAIL_TYPE_SUMMARY`

---

## Data Infrastructure

No separate data factories or external fixtures required. TimestampSanityCheck:
- Uses `spark.createDataFrame(List.of(RowFactory.create(...)), schema)` with explicit `StructType`
- Timestamp values created via `Timestamp.valueOf(LocalDateTime.of(...))` — deterministic
- `DatasetContext` constructed directly (canonical values matching ZeroRowCheckTest)
- Static constants `FUTURE_TS`, `NORMAL_TS`, `STALE_TS` shared across test methods

No PII risk: TimestampSanityCheck stores only aggregate percentages and counts, never source
timestamp values.

---

## Implementation Checklist (DEV GREEN Phase)

After completing tests (RED phase done by TEA), DEV must:

### Task 1: Create `TimestampSanityCheck.java`

**File:** `dqs-spark/src/main/java/com/bank/dqs/checks/TimestampSanityCheck.java`
**Package:** `com.bank.dqs.checks`

Required implementation (from story Dev Notes):
- [ ] `public final class TimestampSanityCheck implements DqCheck`
- [ ] `public static final String CHECK_TYPE = "TIMESTAMP_SANITY"`
- [ ] `public static final String METRIC_FUTURE_PCT = "future_pct"`
- [ ] `public static final String METRIC_STALE_PCT = "stale_pct"`
- [ ] `public static final String DETAIL_TYPE_STATUS = "timestamp_sanity_status"`
- [ ] `public static final String DETAIL_TYPE_SUMMARY = "timestamp_sanity_summary"`
- [ ] `static final int DEFAULT_FUTURE_TOLERANCE_DAYS = 1`
- [ ] `static final int DEFAULT_MAX_AGE_YEARS = 10`
- [ ] `static final double FUTURE_FAIL_THRESHOLD = 0.05`
- [ ] Inner record `TimestampColumnResult`: `String columnName`, `double futurePct`, `double stalePct`, `String status`
- [ ] No-arg constructor `public TimestampSanityCheck()`
- [ ] `getCheckType()` returns `CHECK_TYPE`
- [ ] `execute(DatasetContext context)`:
  - Entire body wrapped in try/catch (catch Exception → errorDetail list)
  - Guard null context → notRunDetail(REASON_MISSING_CONTEXT) list
  - Guard null df → notRunDetail(REASON_MISSING_DATAFRAME) list
  - Collect timestamp/date columns via `df.schema().fields()` with `instanceof TimestampType || instanceof DateType`
  - If no timestamp/date columns → return `[summaryDetail(STATUS_PASS, REASON_NO_TIMESTAMP_COLUMNS, 0, 0)]`
  - For each column: call `analyzeColumn(df, colName, partitionDate)`, emit MetricNumeric + MetricDetail
  - Emit `MetricNumeric(CHECK_TYPE, "future_pct." + colName, result.futurePct())`
  - Emit `MetricNumeric(CHECK_TYPE, "stale_pct." + colName, result.stalePct())`
  - Emit `MetricDetail(CHECK_TYPE, "timestamp_sanity." + colName, columnStatusPayload(result))`
  - Determine overall status: FAIL if any column is FAIL, else PASS
  - Emit final `MetricDetail(CHECK_TYPE, DETAIL_TYPE_SUMMARY, summaryPayload(...))`
- [ ] `analyzeColumn(Dataset<Row> df, String columnName, LocalDate partitionDate)` → `TimestampColumnResult`:
  - `futureThreshold = Timestamp.valueOf(partitionDate.plusDays(DEFAULT_FUTURE_TOLERANCE_DAYS).atStartOfDay())`
  - `staleThreshold = Timestamp.valueOf(partitionDate.minusYears(DEFAULT_MAX_AGE_YEARS).atStartOfDay())`
  - `Column tsCol = col(columnName).cast(DataTypes.TimestampType)`
  - `long futureCount = df.filter(tsCol.gt(lit(futureThreshold))).count()`
  - `long staleCount = df.filter(tsCol.lt(lit(staleThreshold))).count()`
  - `long nonNullCount = df.filter(col(columnName).isNotNull()).count()`
  - `double futurePct = nonNullCount == 0 ? 0.0 : (double) futureCount / nonNullCount`
  - `double stalePct = nonNullCount == 0 ? 0.0 : (double) staleCount / nonNullCount`
  - Classify: `futurePct > FUTURE_FAIL_THRESHOLD || stalePct > FUTURE_FAIL_THRESHOLD` → FAIL else PASS
- [ ] Private helpers: `notRunDetail(String reason)`, `errorDetail(Exception e)`, `summaryDetail(...)`, `columnStatusPayload(...)`, `toJson(Map<String, Object>)`
- [ ] `import static org.apache.spark.sql.functions.*` — required for `col`, `lit`
- [ ] `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`
- [ ] `private static final Logger LOG = LoggerFactory.getLogger(TimestampSanityCheck.class)`

### Task 2: Register in `DqsJob.buildCheckFactory()`

**File:** `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`

- [ ] Add `f.register(new TimestampSanityCheck());` AFTER `DistributionCheck` and BEFORE `DqsScoreCheck`
- [ ] Add import: `import com.bank.dqs.checks.TimestampSanityCheck;`
- [ ] Update Javadoc comment listing Tier 2 checks in `buildCheckFactory()`

### Task 3: Remove `@Disabled` from All Tests

After implementing and verifying:
- [ ] Remove `@Disabled(...)` from all 11 test methods in `TimestampSanityCheckTest.java`
- [ ] Run: `cd dqs-spark && mvn test -Dtest=TimestampSanityCheckTest`
- [ ] Confirm: 11 tests pass, 0 failures

### Task 4: Verify Regression Suite

- [ ] Run full test suite: `cd dqs-spark && mvn test`
- [ ] Confirm: all 216+ tests pass, 0 failures (test suite was at 216 after story 6.4)
- [ ] Confirm: `TimestampSanityCheckTest` adds 11 new tests to the total

---

## Red-Green-Refactor Workflow

```
RED (TEA — complete):
  TimestampSanityCheckTest.java written with @Disabled
  Tests assert expected behavior but class doesn't exist yet
  Compilation fails: "Cannot find symbol: class TimestampSanityCheck"

GREEN (DEV — next):
  Implement TimestampSanityCheck.java per Task 1
  Register in DqsJob per Task 2
  Remove @Disabled from TimestampSanityCheckTest.java
  Run: mvn test -Dtest=TimestampSanityCheckTest → all 11 pass

REFACTOR (DEV — as needed):
  Review Spark filter order (null guard before filter operations)
  Verify divide-by-zero guard: nonNullCount == 0 → 0.0 for both percentages
  Confirm exception caught at outermost try/catch level
  Verify Timestamp.valueOf(localDate.atStartOfDay()) — not java.time.Instant
  Confirm DataTypes.TimestampType cast applied to DateType columns for uniform comparison
  Verify DETAIL_TYPE_SUMMARY = "timestamp_sanity_summary" is used (not per-column variant)
  Confirm metric names follow "future_pct.<colName>" and "stale_pct.<colName>" pattern
  Verify uniqueness: one summary MetricDetail per execution, one column MetricDetail per column per run
```

---

## Execution Commands

```bash
# Run TimestampSanityCheckTest only (after implementing TimestampSanityCheck.java)
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest=TimestampSanityCheckTest

# Run full Spark checks test suite
mvn test -pl dqs-spark

# Run with verbose output
mvn test -Dtest=TimestampSanityCheckTest -pl dqs-spark -e

# RED phase confirmation (before TimestampSanityCheck.java exists):
# Expected: BUILD FAILURE — compilation error "cannot find symbol: class TimestampSanityCheck"
```

---

## Anti-Patterns Avoided

- Guard order: `null context` checked BEFORE `null df` (dev notes anti-pattern rule)
- No `expect(true).toBe(true)` or `assertTrue(true)` placeholder assertions — all assertions test real behavior
- No exception propagation from `execute()` — AC5 and exception tests confirm no exception leaks
- No hardcoded `9999-12-31 23:59:59` — this check uses no JDBC
- No raw types in `List`, `Map` declarations
- SparkSession uses `local[1]` — standard for unit tests
- Timestamp values stored as column names/percentages only — no raw row data in assertions
- Uniform timestamp constants (`FUTURE_TS`, `NORMAL_TS`, `STALE_TS`) for deterministic filter results
- `Timestamp.valueOf(LocalDateTime)` used — not `java.time.Instant` (dev notes requirement)

---

## Knowledge Fragments Applied

| Fragment | Applied |
|---|---|
| `test-quality.md` | Deterministic tests, explicit assertions, stable data, no conditionals |
| `test-levels-framework.md` | Unit level for pure Spark logic, no E2E for backend-only stories |
| `test-healing-patterns.md` | Guard order, null handling, outermost try/catch |
| `data-factories.md` | Spark `createDataFrame(RowFactory, StructType)` with static constant timestamps |
| `test-priorities-matrix.md` | P0 for all AC-mapped tests; P1 for boundary/error paths |

---

## Output Summary

- **Story ID:** 6-5-timestamp-sanity-check
- **Primary test level:** Unit (JUnit 5 + SparkSession local[1])
- **Tests generated:** 11 (7×P0, 4×P1, all @Disabled for RED phase)
- **Test file:** `dqs-spark/src/test/java/com/bank/dqs/checks/TimestampSanityCheckTest.java`
- **Checklist:** `_bmad-output/test-artifacts/atdd-checklist-6-5-timestamp-sanity-check.md`
- **Data factories:** None (Spark createDataFrame + RowFactory with static Timestamp constants)
- **Fixtures:** None (SparkSession @BeforeAll/@AfterAll)
- **Mock requirements:** Mockito for exception test only (`Dataset<Row>` mocked to throw from `schema()`)
- **API/E2E tests:** None (no new endpoints/UI per AC6)
- **Implementation tasks:** 4 (Create class, Register in DqsJob, Remove @Disabled, Verify regression)
- **Next step:** DEV agent implements `TimestampSanityCheck.java` using `bmad-dev-story` skill
