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
  - _bmad-output/implementation-artifacts/6-4-distribution-checks-with-eventattribute-explosion.md
  - _bmad/tea/config.yaml
  - dqs-spark/src/test/java/com/bank/dqs/checks/BreakingChangeCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/BreakingChangeCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/DqCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
---

# ATDD Checklist: Story 6-4 — Distribution Checks with eventAttribute Explosion

## Story Summary

**As a** data steward,
**I want** Distribution checks to detect statistical distribution shifts across columns,
including recursively exploded eventAttribute JSON keys with configurable explosion depth,
**So that** subtle data quality degradation is caught through statistical analysis.

**Stack detected:** backend (Java/Maven/Spark — pure Spark job layer, no new endpoints or UI)
**Test framework:** JUnit 5 + Apache Spark (local[1])
**TDD Phase:** RED — failing tests generated, `DistributionCheck.java` not yet implemented

---

## TDD Red Phase Status

**CURRENT STATE: RED**

- [x] Failing tests generated
- [ ] `DistributionCheck.java` implemented (GREEN phase — DEV responsibility)
- [ ] `@Disabled` removed from all test methods (GREEN phase)
- [ ] All tests passing (GREEN phase confirmed)

All 11 test methods are annotated with `@Disabled("ATDD RED PHASE — DistributionCheck.java not yet implemented")`.
The class will not compile until `DistributionCheck.java` is created in `dqs-spark/src/main/java/com/bank/dqs/checks/`.

---

## Acceptance Criteria Coverage

| AC | Description | Test Method | Priority | Status |
|---|---|---|---|---|
| AC1 | Numeric columns → MetricNumeric per stat (mean, stddev, p50, p95, count) | `executeComputesMeanAndStddevForNumericColumns` | P0 | RED |
| AC1 | Shift z>3.0 → FAIL detail with distribution_shift_detected | `executeDetectsDistributionShiftWhenMeanExceedsBaseline` | P0 | RED |
| AC1 | Shift 2.0<z<3.0 → WARN detail | `executeEmitsWarnWhenMeanShiftIsModerate` | P0 | RED |
| AC2 | explosion_level=ALL → recursive nested key explosion, key paths in metric names | `executeExplodesAllNestedEventAttributeKeys` | P0 | RED |
| AC3 | explosion_level=TOP_LEVEL → only first-level keys analyzed, nested excluded | `executeExplodesTopLevelEventAttributeKeys` | P0 | RED |
| AC4 | explosion_level=OFF → eventAttribute skipped, regular numerics processed | `executeSkipsEventAttributeWhenExplosionLevelIsOff` | P0 | RED |
| AC5 | First run (no baseline) → MetricNumeric + PASS baseline_unavailable per column | `executeReturnsPassWithBaselineUnavailableForFirstRun` | P0 | RED |
| AC6 | Null context → NOT_RUN, no exception | `executeReturnsNotRunWhenContextIsNull` | P0 | RED |
| AC6 | Null df → NOT_RUN, no exception | `executeReturnsNotRunWhenDataFrameIsNull` | P0 | RED |
| AC7 | getCheckType() returns "DISTRIBUTION" | `getCheckTypeReturnsDistribution` | P1 | RED |
| AC6+ | ExplodeConfigProvider throws → errorDetail, no propagation | `executeHandlesExplodeConfigProviderExceptionGracefully` | P1 | RED |
| Edge | Empty dataset (0 rows) → no exception, metrics default gracefully | `executeHandlesEmptyDatasetGracefully` | P1 | RED |

**All 7 acceptance criteria covered (plus 5 additional boundary/error path tests). 12 test methods total.**

---

## Test Files Created

| File | Tests | Phase |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/checks/DistributionCheckTest.java` | 12 | RED |

### Test Method Summary

```
DistributionCheckTest (12 tests, all @Disabled — RED phase)
├── [P0] executeComputesMeanAndStddevForNumericColumns
├── [P0] executeDetectsDistributionShiftWhenMeanExceedsBaseline
├── [P0] executeEmitsWarnWhenMeanShiftIsModerate
├── [P0] executeReturnsPassWithBaselineUnavailableForFirstRun
├── [P0] executeSkipsEventAttributeWhenExplosionLevelIsOff
├── [P0] executeExplodesTopLevelEventAttributeKeys
├── [P0] executeExplodesAllNestedEventAttributeKeys
├── [P0] executeReturnsNotRunWhenContextIsNull
├── [P0] executeReturnsNotRunWhenDataFrameIsNull
├── [P1] getCheckTypeReturnsDistribution
├── [P1] executeHandlesExplodeConfigProviderExceptionGracefully
└── [P1] executeHandlesEmptyDatasetGracefully
```

---

## Test Design Decisions

### Level: Unit (JUnit 5 + SparkSession)

DistributionCheck uses Spark aggregate functions (`mean`, `stddev_pop`, `percentile_approx`,
`count`) and schema inspection — SparkSession is mandatory. Follows the exact SparkSession
lifecycle from `BreakingChangeCheckTest`/`ZeroRowCheckTest`:
- `@BeforeAll static void initSpark()` — creates `local[1]` SparkSession
- `@AfterAll static void stopSpark()` — stops session to free resources

Both providers are injected via constructor lambdas, eliminating JDBC from unit tests:
- `ExplodeConfigProvider` as `ctx -> DistributionCheck.ExplosionLevel.OFF`
- `BaselineStatsProvider` as `ctx -> Optional.empty()` or `ctx -> Optional.of(baselineMap)`

### Why NOT E2E or API Tests

AC7 states: "requires zero changes to serve/API/dashboard." There are no new API endpoints,
no new UI components, and no new MCP tools. The entire story impact is confined to the Spark
job layer. E2E and API test generation was skipped — no applicable scenarios (consistent with
stories 6.1, 6.2, and 6.3).

### TDD Red Phase Mechanism (Java)

In JUnit 5, `@Disabled` is the Java equivalent of Playwright's `test.skip()`:
- Tests declare correct expected behavior (real assertions, not placeholders)
- `@Disabled("ATDD RED PHASE — DistributionCheck.java not yet implemented")` marks them as
  intentionally failing during RED phase
- Once `DistributionCheck.java` is implemented, remove `@Disabled` to enter GREEN phase
- The class itself will not compile until `DistributionCheck.java` is created (compiler
  catches `DistributionCheck`, `DistributionCheck.ExplosionLevel`,
  `DistributionCheck.ColumnStats`, and `DistributionCheck.CHECK_TYPE` as unknown symbols)

### Two-Provider Constructor Injection Pattern

`DistributionCheck(ExplodeConfigProvider, BaselineStatsProvider)` mirrors the single-provider
pattern from `BreakingChangeCheck(SchemaBaselineProvider)`, extended to two providers:

```java
// No explosion, no baseline (basic numeric test)
DistributionCheck check = new DistributionCheck(
    ctx -> DistributionCheck.ExplosionLevel.OFF,
    ctx -> Optional.empty()
);

// With baseline for z-score tests
DistributionCheck.ColumnStats baseline = new DistributionCheck.ColumnStats(100.0, 5.0, 99.0, 120.0, 30L);
DistributionCheck checkWithBaseline = new DistributionCheck(
    ctx -> DistributionCheck.ExplosionLevel.OFF,
    ctx -> Optional.of(Map.of("amount", baseline))
);

// Simulating ExplodeConfigProvider failure
DistributionCheck checkWithThrow = new DistributionCheck(
    ctx -> { throw new RuntimeException("DB failure"); },
    ctx -> Optional.empty()
);
```

### Z-Score Threshold Coverage

Three test cases cover the three z-score outcome regions:
- `z > 3.0` → FAIL (shift test uses mean=130, baseline mean=100, stddev=5 → z=6.0)
- `2.0 < z < 3.0` → WARN (moderate shift uses mean=112, baseline mean=100, stddev=5 → z=2.4)
- No shift (`z <= 2.0`) → PASS (first-run tests with no baseline → baseline_unavailable PASS)

### eventAttribute Explosion Coverage

Three tests cover the full explosion dial:
- `OFF` (level 0): `executeSkipsEventAttributeWhenExplosionLevelIsOff` — eventAttribute absent from metrics
- `TOP_LEVEL` (level 1): `executeExplodesTopLevelEventAttributeKeys` — depth-1 keys only
- `ALL` (level 2): `executeExplodesAllNestedEventAttributeKeys` — all nested numeric leaf paths

### Data Sensitivity Boundary

Tests verify that metric names contain key paths (structural metadata) but never row values:
- Metric names contain `"eventAttribute.amount.mean"` — key path structure
- Metric values contain aggregate statistics (150.0 = mean) — not raw row data
- `getDetailValue()` payloads contain `z_score`, `current_mean`, `baseline_mean` — statistical metadata
- No PII, PCI, or raw event payload strings appear in any test assertion on `detailValue`

### Metric Naming Convention

Established from Dev Notes:
- Regular numeric columns: `{columnName}.{stat}` → `amount.mean`, `amount.stddev`
- exploded eventAttribute keys: `eventAttribute.{jsonPath}.{stat}` → `eventAttribute.amount.mean`
- Nested paths: `eventAttribute.details.price.mean`
- Column status detail type: `{columnName}.status` or `eventAttribute.{jsonPath}.status`
- Summary detail type: `distribution_summary` (always emitted once per execution)

---

## Data Infrastructure

No separate data factories or external fixtures required. DistributionCheck:
- Uses `spark.createDataFrame(List.of(RowFactory.create(...)), schema)` with explicit `StructType`
- For eventAttribute tests: `RowFactory.create(id, "{\"amount\": 100.0}")` — JSON string in-row
- Baseline `ColumnStats` constructed directly: `new DistributionCheck.ColumnStats(mean, stddev, p50, p95, count)`
- `DatasetContext` constructed directly (canonical values matching other check tests)

No PII risk: DistributionCheck stores only aggregate statistics and structural key paths, never source data values.

---

## Implementation Checklist (DEV GREEN Phase)

After completing tests (RED phase done by TEA), DEV must:

### Task 1: Create `DistributionCheck.java`

**File:** `dqs-spark/src/main/java/com/bank/dqs/checks/DistributionCheck.java`
**Package:** `com.bank.dqs.checks`

Required implementation (from Dev Notes):
- [ ] `public final class DistributionCheck implements DqCheck`
- [ ] `public static final String CHECK_TYPE = "DISTRIBUTION"`
- [ ] Value record `ExplodedColumn`: `String columnName`, `String virtualPath`
- [ ] Enum `ExplosionLevel`: `OFF(0)`, `TOP_LEVEL(1)`, `ALL(2)` with `fromInt(int)` factory
- [ ] Functional interface `ExplodeConfigProvider`: `ExplosionLevel getExplosionLevel(DatasetContext ctx) throws Exception`
- [ ] Functional interface `ConnectionProvider`: `Connection getConnection() throws SQLException`
- [ ] Inner `JdbcExplodeConfigProvider`: queries `check_config` for `explosion_level` integer
- [ ] Inner `NoOpExplodeConfigProvider`: returns `ExplosionLevel.OFF`
- [ ] Functional interface `BaselineStatsProvider`: `Optional<Map<String, ColumnStats>> getBaseline(DatasetContext ctx) throws Exception`
- [ ] Value class `ColumnStats`: `double mean`, `double stddev`, `double p50`, `double p95`, `long sampleCount`
- [ ] Inner `JdbcBaselineStatsProvider`: queries `dq_metric_numeric` for prior run stats
- [ ] Inner `NoOpBaselineStatsProvider`: returns `Optional.empty()`
- [ ] No-arg constructor: `this(new NoOpExplodeConfigProvider(), new NoOpBaselineStatsProvider())`
- [ ] 2-arg constructor: `DistributionCheck(ExplodeConfigProvider, BaselineStatsProvider)` with null validation
- [ ] `getCheckType()` returns `CHECK_TYPE`
- [ ] `execute(DatasetContext)`:
  - Entire body wrapped in try/catch (catch Exception → errorDetail list)
  - Guard null context → notRunDetail(REASON_MISSING_CONTEXT) list
  - Guard null df → notRunDetail(REASON_MISSING_DATAFRAME) list
  - Determine ExplosionLevel via `explodeConfigProvider.getExplosionLevel(context)`
  - Collect numeric columns: `collectNumericColumns(df, explosionLevel)`
  - If no columns → return `[summaryDetail(PASS, REASON_NO_NUMERIC_COLUMNS, 0, 0)]`
  - Fetch baseline: `baselineProvider.getBaseline(context)`
  - For each column: compute stats (single-pass Spark agg), emit MetricNumeric per stat
  - Compare to baseline using z-score; emit MetricDetail per column
  - Emit final `distribution_summary` MetricDetail
- [ ] `collectNumericColumns(Dataset<Row> df, ExplosionLevel level)` → `List<ExplodedColumn>`
- [ ] `explodeEventAttribute(Dataset<Row> df, String colName, ExplosionLevel level)` → `List<String>`
- [ ] `computeColumnStats(Dataset<Row> df, String colExpr)` → `ColumnStats` (single-pass agg)
- [ ] `classifyShift(double currentMean, ColumnStats baseline)` → STATUS_PASS/WARN/FAIL (z-score)
- [ ] Private helpers: `notRunDetail(reason)`, `errorDetail(e)`, `summaryDetail(...)`, `columnStatusDetail(...)`, `toJson(map)`
- [ ] `import static org.apache.spark.sql.functions.*` — required for `mean`, `stddev_pop`, etc.
- [ ] `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`
- [ ] `private static final Logger LOG = LoggerFactory.getLogger(DistributionCheck.class)`

### Task 2: Register in `DqsJob.buildCheckFactory()`

**File:** `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`

- [ ] Add `f.register(new DistributionCheck());` AFTER `BreakingChangeCheck` and BEFORE `DqsScoreCheck`
- [ ] Add import: `import com.bank.dqs.checks.DistributionCheck;`
- [ ] Update Javadoc comment listing Tier 2 checks in `buildCheckFactory()`

### Task 3: Remove `@Disabled` from All Tests

After implementing and verifying:
- [ ] Remove `@Disabled(...)` from all 12 test methods in `DistributionCheckTest.java`
- [ ] Run: `cd dqs-spark && mvn test -Dtest=DistributionCheckTest`
- [ ] Confirm: 12 tests pass, 0 failures

### Task 4: Verify Regression Suite

- [ ] Run full test suite: `cd dqs-spark && mvn test`
- [ ] Confirm: all 204+ tests pass, 0 failures (test suite was at 204 after story 6.3)
- [ ] Confirm: `DistributionCheckTest` adds 12 new tests to the total

---

## Red-Green-Refactor Workflow

```
RED (TEA — complete):
  DistributionCheckTest.java written with @Disabled
  Tests assert expected behavior but class doesn't exist yet
  Compilation fails: "Cannot find symbol: class DistributionCheck"

GREEN (DEV — next):
  Implement DistributionCheck.java per Task 1
  Register in DqsJob per Task 2
  Remove @Disabled from DistributionCheckTest.java
  Run: mvn test -Dtest=DistributionCheckTest → all 12 pass

REFACTOR (DEV — as needed):
  Review Spark aggregation single-pass efficiency (one agg() call per column)
  Verify null guard order: context → df → schema()/agg()
  Confirm exception caught at outermost try/catch level
  Verify eventAttribute explosion uses get_json_object (not row value materialization)
  Confirm key paths stored in metric names, raw values NEVER in payloads
  Verify DqsConstants.EXPIRY_SENTINEL used in JDBC (never hardcoded date)
  Confirm ColumnStats is a value class (record or similar immutable)
```

---

## Execution Commands

```bash
# Run DistributionCheckTest only (after implementing DistributionCheck.java)
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest=DistributionCheckTest

# Run full Spark checks test suite
mvn test -pl dqs-spark

# Run with verbose output
mvn test -Dtest=DistributionCheckTest -pl dqs-spark -e

# RED phase confirmation (before DistributionCheck.java exists):
# Expected: BUILD FAILURE — compilation error "cannot find symbol: class DistributionCheck"
```

---

## Anti-Patterns Avoided

- Guard order: `null context` checked BEFORE `null df` (dev notes anti-pattern rule)
- No `expect(true).toBe(true)` or `assertTrue(true)` placeholder assertions — all assertions test real behavior
- No exception propagation from `execute()` — AC6 tests confirm no exception leaks
- No hardcoded `9999-12-31 23:59:59` in provider — must use `DqsConstants.EXPIRY_SENTINEL`
- No raw types in `List`, `Map`, `Optional` declarations
- SparkSession uses `local[1]` — standard for unit tests, not bundled Spark JARs
- eventAttribute key paths verified in metric NAMES only — no row value assertions
- `DistributionCheck.ColumnStats` used directly with known values for deterministic z-score calculation
- Tests use uniform `mean=X` rows to produce stable Spark aggregate values (no statistical flakiness)

---

## Knowledge Fragments Applied

| Fragment | Applied |
|---|---|
| `test-quality.md` | Deterministic tests, explicit assertions, stable data, no conditionals |
| `test-levels-framework.md` | Unit level for pure logic + Spark, no E2E for backend-only stories |
| `test-healing-patterns.md` | Guard order, null handling, outermost try/catch, provider injection |
| `data-factories.md` | Spark `createDataFrame(RowFactory, StructType)` + uniform values for stable means |
| `test-priorities-matrix.md` | P0 for all AC-mapped tests; P1 for boundary/error paths |

---

## Output Summary

- **Story ID:** 6-4-distribution-checks-with-eventattribute-explosion
- **Primary test level:** Unit (JUnit 5 + SparkSession local[1])
- **Tests generated:** 12 (9×P0, 3×P1, all @Disabled for RED phase)
- **Test file:** `dqs-spark/src/test/java/com/bank/dqs/checks/DistributionCheckTest.java`
- **Checklist:** `_bmad-output/test-artifacts/atdd-checklist-6-4-distribution-checks-with-eventattribute-explosion.md`
- **Data factories:** None (Spark createDataFrame + RowFactory with uniform values for stable aggregations)
- **Fixtures:** None (SparkSession @BeforeAll/@AfterAll)
- **Mock requirements:** Lambda-injected `ExplodeConfigProvider` + `BaselineStatsProvider` (no Mockito needed)
- **API/E2E tests:** None (no new endpoints/UI per AC7)
- **Implementation tasks:** 4 (Create class, Register in DqsJob, Remove @Disabled, Verify regression)
- **Next step:** DEV agent implements `DistributionCheck.java` using `bmad-dev-story` skill
