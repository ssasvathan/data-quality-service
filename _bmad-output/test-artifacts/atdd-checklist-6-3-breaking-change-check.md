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
  - _bmad-output/implementation-artifacts/6-3-breaking-change-check.md
  - _bmad/tea/config.yaml
  - dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java
  - dqs-spark/src/test/java/com/bank/dqs/checks/SchemaCheckTest.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/SchemaCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/checks/ZeroRowCheck.java
  - dqs-spark/src/main/java/com/bank/dqs/model/DatasetContext.java
  - dqs-spark/src/main/java/com/bank/dqs/model/MetricDetail.java
---

# ATDD Checklist: Story 6-3 — Breaking Change Check

## Story Summary

**As a** data steward,
**I want** the Breaking Change check to detect schema changes that remove or rename fields,
**So that** destructive schema modifications are flagged separately from additive drift.

**Stack detected:** backend (Java/Maven/Spark — pure Spark job layer, no new endpoints or UI)
**Test framework:** JUnit 5 + Apache Spark (local[1])
**TDD Phase:** RED — failing tests generated, `BreakingChangeCheck.java` not yet implemented

---

## TDD Red Phase Status

**CURRENT STATE: RED**

- [x] Failing tests generated
- [ ] `BreakingChangeCheck.java` implemented (GREEN phase — DEV responsibility)
- [ ] `@Disabled` removed from all test methods (GREEN phase)
- [ ] All tests passing (GREEN phase confirmed)

All 9 test methods are annotated with `@Disabled("ATDD RED PHASE — BreakingChangeCheck.java not yet implemented")`.
The class will not compile until `BreakingChangeCheck.java` is created in `dqs-spark/src/main/java/com/bank/dqs/checks/`.

---

## Acceptance Criteria Coverage

| AC | Description | Test Method | Priority | Status |
|---|---|---|---|---|
| AC1 | Fields removed → FAIL, lists removed fields | `executeReturnsFailWhenFieldsRemoved` | P0 | RED |
| AC2 | Multiple fields removed (rename) → FAIL, all removed fields listed | `executeReturnsFailWhenMultipleFieldsRemoved` | P0 | RED |
| AC3 | Only fields added → PASS (additive not breaking) | `executeReturnsPassWhenOnlyFieldsAdded` | P0 | RED |
| AC3 boundary | Identical schema → PASS | `executeReturnsPassWhenSchemaUnchanged` | P1 | RED |
| AC4 | First run (no baseline) → PASS, reason=baseline_unavailable | `executeReturnsPassWhenBaselineUnavailable` | P0 | RED |
| AC5 | Null context → NOT_RUN, no exception | `executeReturnsNotRunWhenContextIsNull` | P0 | RED |
| AC5 | Null df → NOT_RUN, no exception | `executeReturnsNotRunWhenDataFrameIsNull` | P0 | RED |
| AC6 | getCheckType() returns "BREAKING_CHANGE" | `getCheckTypeReturnsBreakingChange` | P1 | RED |
| AC5+ | BaselineProvider throws → errorDetail, no propagation | `executeHandlesBaselineProviderExceptionGracefully` | P1 | RED |

**All 5 acceptance criteria covered (plus 4 additional boundary/error path tests). 9 test methods total.**

---

## Test Files Created

| File | Tests | Phase |
|---|---|---|
| `dqs-spark/src/test/java/com/bank/dqs/checks/BreakingChangeCheckTest.java` | 9 | RED |

### Test Method Summary

```
BreakingChangeCheckTest (9 tests, all @Disabled — RED phase)
├── [P0] executeReturnsFailWhenFieldsRemoved
├── [P0] executeReturnsFailWhenMultipleFieldsRemoved
├── [P0] executeReturnsPassWhenOnlyFieldsAdded
├── [P1] executeReturnsPassWhenSchemaUnchanged
├── [P0] executeReturnsPassWhenBaselineUnavailable
├── [P0] executeReturnsNotRunWhenContextIsNull
├── [P0] executeReturnsNotRunWhenDataFrameIsNull
├── [P1] getCheckTypeReturnsBreakingChange
└── [P1] executeHandlesBaselineProviderExceptionGracefully
```

---

## Test Design Decisions

### Level: Unit (JUnit 5 + SparkSession)

BreakingChangeCheck inspects `context.getDf().schema()` (Spark StructType) — SparkSession is mandatory to create DataFrames with explicit schemas. Unlike SlaCountdownCheck (time-based, no Spark), BreakingChangeCheck follows the ZeroRowCheckTest/SchemaCheckTest pattern with `@BeforeAll`/`@AfterAll` SparkSession lifecycle.

The `SchemaBaselineProvider` is injected via constructor, enabling lambda-based mock baselines without JDBC. Real `JdbcSchemaBaselineProvider` is not tested here — it reads from `dq_metric_detail` and is integration-tested only by running the Spark job end-to-end.

### Why NOT E2E or API Tests

AC6 states: "requires zero changes to serve/API/dashboard." There are no new API endpoints, no new UI components, and no new MCP tools. The entire story impact is confined to the Spark job layer. E2E and API test generation was skipped — no applicable scenarios (consistent with stories 6.1 and 6.2).

### TDD Red Phase Mechanism (Java)

In JUnit 5, `@Disabled` is the equivalent of Playwright's `test.skip()`:
- Tests are declared with correct expected behavior
- `@Disabled` marks them as intentionally failing (RED phase)
- Once `BreakingChangeCheck.java` is implemented, remove `@Disabled` to enter GREEN phase
- Tests assert real behavior — not placeholder assertions

### Baseline Pattern: Constructor-Injected Provider

`BreakingChangeCheck(SchemaBaselineProvider)` mirrors the pattern from `SchemaCheck(BaselineProvider)`. Tests use lambda injection:
```java
BreakingChangeCheck check = new BreakingChangeCheck(ctx -> Optional.of(snapshot));
```
This eliminates JDBC from unit tests while fully testing the check logic.

### Assertions Follow Established Patterns

All assertions follow the pattern established by `ZeroRowCheckTest` and `SchemaCheckTest`:
- `findStatusDetail()` — extracts `MetricDetail` by `BreakingChangeCheck.DETAIL_TYPE_STATUS`
- `findOptionalFieldsDetail()` — optionally finds `MetricDetail` by `DETAIL_TYPE_FIELDS`
- String-contains assertions on `detail.getDetailValue()` for JSON payload fields
- Guard-order tests: `null context` checked before `null df` (consistent with ZeroRowCheck pattern)

### Fields Detail Emission Rules

Consistent with SchemaCheck's diffDetail (only emitted when `hasDrift()`):
- **FAIL**: both `breaking_change_status` AND `breaking_change_fields` details emitted
- **PASS**: only `breaking_change_status` detail emitted — no `breaking_change_fields`
- **NOT_RUN**: only `breaking_change_status` detail emitted

Tests for PASS cases assert `assertFalse(fieldsDetailOpt.isPresent())` to enforce this contract.

---

## Data Infrastructure

No data factories or external fixtures required. BreakingChangeCheck:
- Uses `spark.createDataFrame(List.of(RowFactory.create(...)), schema)` with explicit `StructType`
- Baseline schemas are extracted as `df.schema().json()` and wrapped in `SchemaSnapshot`
- Lambda `ctx -> Optional.of(snapshot)` injects baseline without JDBC
- Lambda `ctx -> Optional.empty()` simulates first-run / no baseline
- Lambda `ctx -> { throw new RuntimeException(...); }` simulates JDBC failure
- `DatasetContext` constructed directly (no faker needed; values are structural, not data-sensitive)

No PII risk: `BreakingChangeCheck` inspects only field paths (structural metadata), never row values.

---

## Implementation Checklist (DEV GREEN Phase)

After completing tests (RED phase done by TEA), DEV must:

### Task 1: Create `BreakingChangeCheck.java`

**File:** `dqs-spark/src/main/java/com/bank/dqs/checks/BreakingChangeCheck.java`
**Package:** `com.bank.dqs.checks`

Required implementation:
- [ ] `public final class BreakingChangeCheck implements DqCheck`
- [ ] `public static final String CHECK_TYPE = "BREAKING_CHANGE"`
- [ ] `static final String DETAIL_TYPE_STATUS = "breaking_change_status"`
- [ ] `static final String DETAIL_TYPE_FIELDS = "breaking_change_fields"`
- [ ] Status constants: `STATUS_PASS`, `STATUS_FAIL`, `STATUS_NOT_RUN`
- [ ] Reason constants: `REASON_NO_BREAKING_CHANGES`, `REASON_BREAKING_CHANGES_DETECTED`, `REASON_BASELINE_UNAVAILABLE`, `REASON_MISSING_CONTEXT`, `REASON_MISSING_DATAFRAME`, `REASON_EXECUTION_ERROR`
- [ ] `private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()` (thread-safe static)
- [ ] Inner functional interface `SchemaBaselineProvider`: `Optional<SchemaSnapshot> getBaseline(DatasetContext ctx) throws Exception`
- [ ] Inner value class `SchemaSnapshot`: `String schemaJson()` (stores Spark schema JSON from prior run)
- [ ] Inner `JdbcSchemaBaselineProvider implements SchemaBaselineProvider` — queries `dq_metric_detail` for last SCHEMA check hash+JSON row, extracts `schema_json` field
- [ ] Inner `ConnectionProvider` functional interface (same pattern as `SchemaCheck.ConnectionProvider`)
- [ ] Inner `NoOpSchemaBaselineProvider implements SchemaBaselineProvider` — returns `Optional.empty()`
- [ ] No-arg constructor: `this(new NoOpSchemaBaselineProvider())`
- [ ] 1-arg constructor: `BreakingChangeCheck(SchemaBaselineProvider)` with null validation
- [ ] `getCheckType()` returns `CHECK_TYPE`
- [ ] `execute(DatasetContext)`:
  - Entire body wrapped in try/catch (catch Exception → errorDetail)
  - Guard null context → notRunDetail(REASON_MISSING_CONTEXT)
  - Guard null df → notRunDetail(REASON_MISSING_DATAFRAME)
  - `extractFlatFieldNames(df.schema())` for current fields
  - `baselineProvider.getBaseline(context)` → if empty, passDetail(REASON_BASELINE_UNAVAILABLE, emptyList)
  - Parse baseline JSON: `DataType.fromJson(snapshot.schemaJson())`
  - Compute removedFields = baselineFields minus currentFields
  - If empty → `[statusDetail(PASS, REASON_NO_BREAKING_CHANGES, emptyList)]`
  - Else → `[statusDetail(FAIL, REASON_BREAKING_CHANGES_DETECTED, removedFields), fieldsDetail(removedFields)]`
- [ ] `extractFlatFieldNames(StructType)` — dot-path names recursively (nested structs use `.`, array element structs use `[]`)
- [ ] Private helpers: `statusDetail`, `passDetail`, `fieldsDetail`, `notRunDetail`, `errorDetail`, `toJson`
- [ ] `statusDetail` payload: `{"status":..., "reason":..., "removed_count":N}`
- [ ] `fieldsDetail` payload: `{"removed_fields":[...]}` — alphabetically sorted

### Task 2: Register in `DqsJob.buildCheckFactory()`

**File:** `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`

- [ ] Add `f.register(new BreakingChangeCheck())` AFTER `ZeroRowCheck` and BEFORE `DqsScoreCheck`
- [ ] Add import: `import com.bank.dqs.checks.BreakingChangeCheck;`
- [ ] Fix malformed single-slash comments on lines 315 and 318: change `/ TODO` → `// TODO` and `/ Lambda` → `// Lambda`

### Task 3: Remove `@Disabled` from All Tests

After implementing and verifying:
- [ ] Remove `@Disabled(...)` from all 9 test methods in `BreakingChangeCheckTest.java`
- [ ] Run: `cd dqs-spark && mvn test -Dtest=BreakingChangeCheckTest`
- [ ] Confirm: 9 tests pass, 0 failures

### Task 4: Verify Regression Suite

- [ ] Run full test suite: `cd dqs-spark && mvn test`
- [ ] Confirm: all 190+ tests pass, 0 failures
- [ ] Confirm: `BreakingChangeCheckTest` adds 9 new tests to the total

---

## Red-Green-Refactor Workflow

```
RED (TEA — complete):
  BreakingChangeCheckTest.java written with @Disabled
  Tests assert expected behavior but class doesn't exist yet
  Compilation fails: "Cannot find symbol: class BreakingChangeCheck"

GREEN (DEV — next):
  Implement BreakingChangeCheck.java per Task 1
  Register in DqsJob per Task 2 (and fix malformed comments)
  Remove @Disabled from BreakingChangeCheckTest.java
  Run: mvn test -Dtest=BreakingChangeCheckTest → all 9 pass

REFACTOR (DEV — as needed):
  Review for code quality (helper deduplication, constant reuse)
  Ensure Jackson ObjectMapper is static final
  Verify null guard order: context → df → schema()
  Check exception is caught at outermost try/catch level
  Verify removed_fields list is sorted alphabetically
  Verify JdbcSchemaBaselineProvider uses EXPIRY_SENTINEL, not hardcoded date
```

---

## Execution Commands

```bash
# Run BreakingChangeCheckTest only (after implementing BreakingChangeCheck.java)
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test -Dtest=BreakingChangeCheckTest

# Run full checks test suite
mvn test -pl dqs-spark

# Run with verbose output
mvn test -Dtest=BreakingChangeCheckTest -pl dqs-spark -e

# RED phase confirmation (before BreakingChangeCheck.java exists):
# Expected: BUILD FAILURE — compilation error "cannot find symbol: class BreakingChangeCheck"
```

---

## Anti-Patterns Avoided

- Guard order: `null context` checked BEFORE `null df` (dev notes anti-pattern rule)
- No `expect(true).toBe(true)` placeholder assertions — all assertions test real behavior
- No exception propagation from `execute()` — AC5 tests confirm no exception leaks
- No hardcoded `9999-12-31 23:59:59` (JdbcSchemaBaselineProvider must use `DqsConstants.EXPIRY_SENTINEL`)
- No raw types in `List`, `Map`, `Optional` declarations
- SparkSession uses `local[1]` — standard for unit tests, not bundled Spark JARs
- `findOptionalFieldsDetail()` asserts absence of fields detail on PASS — enforces emit-only-on-failure contract
- Tests use `StructType.add(...)` with explicit `DataTypes` — same pattern as `SchemaCheckTest`

---

## Knowledge Fragments Applied

| Fragment | Applied |
|---|---|
| `test-quality.md` | Deterministic tests, explicit assertions, no conditionals |
| `test-levels-framework.md` | Unit level for pure logic, no E2E for backend-only stories |
| `test-healing-patterns.md` | Guard order, null handling, try/catch patterns |
| `data-factories.md` | Spark `createDataFrame(RowFactory, StructType)` as DataFrame factory |

---

## Output Summary

- **Story ID:** 6-3-breaking-change-check
- **Primary test level:** Unit (JUnit 5 + SparkSession)
- **Tests generated:** 9 (5×P0, 4×P1, all @Disabled for RED phase)
- **Test file:** `dqs-spark/src/test/java/com/bank/dqs/checks/BreakingChangeCheckTest.java`
- **Checklist:** `_bmad-output/test-artifacts/atdd-checklist-6-3-breaking-change-check.md`
- **Data factories:** None (Spark createDataFrame + RowFactory used directly)
- **Fixtures:** None (SparkSession @BeforeAll/@AfterAll)
- **Mock requirements:** Lambda-injected `SchemaBaselineProvider` (no Mockito needed)
- **API/E2E tests:** None (no new endpoints/UI per AC6)
- **Implementation tasks:** 4 (Create class, Register + fix comments, Remove @Disabled, Verify regression)
- **Next step:** DEV agent implements `BreakingChangeCheck.java` using `bmad-dev-story` skill
