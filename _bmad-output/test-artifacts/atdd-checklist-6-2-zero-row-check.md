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
  - _bmad-output/implementation-artifacts/6-2-zero-row-check.md
  - _bmad/tea/config.yaml
  - dqs-spark/src/test/java/com/bank/dqs/checks/VolumeCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/SlaCountdownCheckTest.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricNumeric.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
---

# ATDD Checklist: Story 6-2 — Zero-Row Check

## Story Summary

**As a** data steward,
**I want** the Zero-Row check to detect datasets with no records,
**So that** empty deliveries are caught immediately.

**Stack detected:** fullstack (Java/Spark backend — this story is backend-only)
**Test framework:** JUnit 5 + Apache Spark (local[1])
**TDD Phase:** RED — failing tests generated, `ZeroRowCheck.java` not yet implemented

---

## TDD Red Phase Status

**CURRENT STATE: RED**

- [x] Failing tests generated
- [ ] `ZeroRowCheck.java` implemented (GREEN phase — DEV responsibility)
- [ ] `@Disabled` removed from all test methods (GREEN phase)
- [ ] All tests passing (GREEN phase confirmed)

All 6 test methods are annotated with `@Disabled("ATDD RED PHASE — ZeroRowCheck.java not yet implemented")`.
The class will not compile until `ZeroRowCheck.java` is created in `dqs-spark/src/main/java/com/bank/dqs/checks/`.

---

## Acceptance Criteria Coverage

| AC | Description | Test Method | Priority | Status |
|---|---|---|---|---|
| AC1 | Zero rows → FAIL, row_count=0.0 | `executeReturnsFailAndZeroRowCountWhenDataFrameIsEmpty` | P0 | RED |
| AC2 | N rows → PASS, row_count=N.0 | `executeReturnsPassAndRowCountWhenDataFrameHasRows` | P0 | RED |
| AC2 boundary | Exactly 1 row → PASS (boundary) | `executeReturnsSingleRowHandledCorrectly` | P1 | RED |
| AC3 | Null context → NOT_RUN, no exception | `executeReturnsNotRunWhenContextIsNull` | P0 | RED |
| AC3 | Null df → NOT_RUN, no exception | `executeReturnsNotRunWhenDataFrameIsNull` | P0 | RED |
| AC4 | getCheckType() returns "ZERO_ROW" | `getCheckTypeReturnsZeroRow` | P1 | RED |

**All 4 acceptance criteria covered. 6 test methods total.**

---

## Test Files Created

| File | Tests | Phase |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java` | 6 | RED |

### Test Method Summary

```
ZeroRowCheckTest (6 tests, all @Disabled — RED phase)
├── [P0] executeReturnsFailAndZeroRowCountWhenDataFrameIsEmpty
├── [P0] executeReturnsPassAndRowCountWhenDataFrameHasRows
├── [P1] executeReturnsSingleRowHandledCorrectly
├── [P0] executeReturnsNotRunWhenContextIsNull
├── [P0] executeReturnsNotRunWhenDataFrameIsNull
└── [P1] getCheckTypeReturnsZeroRow
```

---

## Test Design Decisions

### Level: Unit (JUnit 5 + SparkSession)

ZeroRowCheck is pure DataFrame logic — no JDBC, no external dependencies. This makes it ideal for unit testing. SparkSession is required because `context.getDf().count()` calls the Spark execution engine.

Unlike `SlaCountdownCheckTest` (no SparkSession needed — time-based only), `ZeroRowCheckTest` follows the `VolumeCheckTest` pattern with `@BeforeAll`/`@AfterAll` SparkSession lifecycle.

### Why NOT E2E or API Tests

AC4 states: "requires zero changes to serve/API/dashboard." There are no new API endpoints, no new UI components, and no new MCP tools. The entire story impact is confined to the Spark job layer. E2E and API test generation was skipped — no applicable scenarios.

### TDD Red Phase Mechanism (Java)

In JUnit 5, `@Disabled` is the equivalent of Playwright's `test.skip()`:
- Tests are declared with correct expected behavior
- `@Disabled` marks them as intentionally failing (RED phase)
- Once `ZeroRowCheck.java` is implemented, remove `@Disabled` to enter GREEN phase
- Tests assert real behavior — not placeholder assertions

### Assertions Follow Existing Patterns

All assertions follow the established pattern from `VolumeCheckTest` and `SlaCountdownCheckTest`:
- `findNumericMetric()` — extracts `MetricNumeric` by `metricName`
- `findStatusDetail()` — extracts `MetricDetail` by `ZeroRowCheck.DETAIL_TYPE_STATUS`
- String-contains assertions on `detail.getDetailValue()` for JSON payload fields

---

## Data Infrastructure

No data factories or external fixtures required. ZeroRowCheck:
- Uses `spark.range(N).toDF("id")` for rows-present DataFrames
- Uses `spark.range(0).toDF("id")` for empty DataFrame
- Uses `null` for null DataFrame guard test
- `DatasetContext` constructed directly (no factory — no faker needed; values are fixed)

No PII risk: `df.count()` returns only a Long. No cell values are accessed.

---

## Implementation Checklist (DEV GREEN Phase)

After completing tests (RED phase done by TEA), DEV must:

### Task 1: Create `ZeroRowCheck.java`

**File:** `dqs-spark/src/main/java/com/bank/dqs/checks/ZeroRowCheck.java`
**Package:** `com.bank.dqs.checks`

Required implementation:
- [ ] `public final class ZeroRowCheck implements DqCheck`
- [ ] `public static final String CHECK_TYPE = "ZERO_ROW"`
- [ ] `static final String METRIC_ROW_COUNT = "row_count"`
- [ ] `static final String DETAIL_TYPE_STATUS = "zero_row_status"`
- [ ] Status constants: `STATUS_PASS`, `STATUS_FAIL`, `STATUS_NOT_RUN`
- [ ] Reason constants: `REASON_HAS_ROWS`, `REASON_ZERO_ROWS`, `REASON_MISSING_CONTEXT`, `REASON_MISSING_DATAFRAME`, `REASON_EXECUTION_ERROR`
- [ ] `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()`
- [ ] No-arg constructor: `public ZeroRowCheck() {}`
- [ ] `getCheckType()` returns `CHECK_TYPE`
- [ ] `execute(DatasetContext context)`:
  - Wrapped in try/catch (entire body)
  - Guard: null context → `notRunDetail(REASON_MISSING_CONTEXT)`
  - Guard: null df → `notRunDetail(REASON_MISSING_DATAFRAME)`
  - `long rowCount = context.getDf().count()`
  - `rowCount == 0` → FAIL + REASON_ZERO_ROWS; else PASS + REASON_HAS_ROWS
  - Returns `[MetricNumeric(CHECK_TYPE, METRIC_ROW_COUNT, (double) rowCount), statusDetail(...)]`
- [ ] Private helper `notRunDetail(String reason)` — returns `List<DqMetric>` with one detail
- [ ] Private helper `statusDetail(String status, String reason, long rowCount)` — JSON `{status, reason, row_count}`
- [ ] Private helper `errorDetail(Exception e)` — JSON `{status=NOT_RUN, reason=execution_error, row_count=0, error_type}`
- [ ] Private helper `toJson(Map<String, Object>)` — catch `JsonProcessingException`, return fallback

### Task 2: Register in `DqsJob.buildCheckFactory()`

**File:** `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`

- [ ] Add `f.register(new ZeroRowCheck())` AFTER `SlaCountdownCheck` and BEFORE `DqsScoreCheck`
- [ ] Add import: `import com.bank.dqs.checks.ZeroRowCheck;`

### Task 3: Remove `@Disabled` from All Tests

After implementing and verifying:
- [ ] Remove `@Disabled(...)` from all 6 test methods in `ZeroRowCheckTest.java`
- [ ] Run: `cd dqs-spark && mvn test -pl . -Dtest=ZeroRowCheckTest`
- [ ] Confirm: 6 tests pass, 0 failures

### Task 4: Verify Regression Suite

- [ ] Run full test suite: `cd dqs-spark && mvn test`
- [ ] Confirm: all 184+ tests pass, 0 failures
- [ ] Confirm: `ZeroRowCheckTest` adds 6 new tests to the total

---

## Red-Green-Refactor Workflow

```
RED (TEA — complete):
  ZeroRowCheckTest.java written with @Disabled
  Tests assert expected behavior but class doesn't exist yet
  Compilation fails: "Cannot find symbol: class ZeroRowCheck"

GREEN (DEV — next):
  Implement ZeroRowCheck.java per Task 1
  Register in DqsJob per Task 2
  Remove @Disabled from ZeroRowCheckTest.java
  Run: mvn test -Dtest=ZeroRowCheckTest → all 6 pass

REFACTOR (DEV — as needed):
  Review for code quality (helper deduplication, constant reuse)
  Ensure Jackson ObjectMapper is static final
  Verify null guard order: context → df → count
  Check exception is caught at outermost try/catch level
```

---

## Execution Commands

```bash
# Run ZeroRowCheckTest only (after implementing ZeroRowCheck.java)
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest=ZeroRowCheckTest

# Run full checks test suite
mvn test -pl dqs-spark

# Run with verbose output
mvn test -Dtest=ZeroRowCheckTest -pl dqs-spark -e

# RED phase confirmation (before ZeroRowCheck.java exists):
# Expected: BUILD FAILURE — compilation error "cannot find symbol: class ZeroRowCheck"
```

---

## Anti-Patterns Avoided

- Guard order: `null context` checked BEFORE `null df` (as specified in dev notes)
- No `expect(true).toBe(true)` placeholder assertions — all assertions test real behavior
- No exception propagation from `execute()` — AC3 tests confirm no exception leaks
- No hardcoded `9999-12-31 23:59:59` (not applicable, but project rule noted)
- No raw types in `List`, `Map` declarations
- SparkSession uses `local[1]` — standard for unit tests, not bundled Spark JARs

---

## Knowledge Fragments Applied

| Fragment | Applied |
|---|---|
| `test-quality.md` | Deterministic tests, explicit assertions, no conditionals |
| `test-levels-framework.md` | Unit level for pure logic, no E2E for backend-only stories |
| `test-healing-patterns.md` | Guard order, null handling, try/catch patterns |
| `data-factories.md` | Spark `range()` as DataFrame factory; `DatasetContext` helper method |

---

## Output Summary

- **Story ID:** 6-2-zero-row-check
- **Primary test level:** Unit (JUnit 5 + SparkSession)
- **Tests generated:** 6 (all P0/P1, all @Disabled for RED phase)
- **Test file:** `dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java`
- **Checklist:** `_bmad-output/test-artifacts/atdd-checklist-6-2-zero-row-check.md`
- **Data factories:** None (Spark range() used directly)
- **Fixtures:** None (SparkSession @BeforeAll/@AfterAll)
- **Mock requirements:** None (pure DataFrame, no JDBC)
- **API/E2E tests:** None (no new endpoints/UI per AC4)
- **Implementation tasks:** 4 (Create class, Register, Remove @Disabled, Verify regression)
- **Next step:** DEV agent implements `ZeroRowCheck.java` using `bmad-dev-story` skill
