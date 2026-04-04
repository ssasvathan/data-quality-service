---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: '2026-04-04'
story_id: 7-1-classification-weighted-alerting-source-system-health
tdd_phase: RED
total_tests: 18
unit_tests: 18
all_tests_failing: true
---

# ATDD Checklist: Story 7.1 — Classification-Weighted Alerting & Source System Health

## TDD Red Phase (Current)

Tests generated — will fail to compile until implementation classes exist.

- `ClassificationWeightedCheckTest.java`: 8 tests (RED — class `ClassificationWeightedCheck` does not exist)
- `SourceSystemHealthCheckTest.java`: 9 tests (RED — class `SourceSystemHealthCheck` does not exist)

**Total: 18 failing unit tests**

---

## Step 1: Preflight & Context

**Stack detected:** `backend` (Java/Maven — `dqs-spark/pom.xml` present, no frontend indicators)

**Prerequisites:**
- Story status: `ready-for-dev` — approved with clear acceptance criteria
- Test framework: JUnit 5 (confirmed via existing test files in `dqs-spark/src/test/`)
- No SparkSession required — neither check calls `context.getDf()`

**Story file loaded:** `_bmad-output/implementation-artifacts/7-1-classification-weighted-alerting-source-system-health.md`

**Acceptance criteria count:** 7 (AC1–AC7)

**Key patterns loaded from existing tests:**
- `SlaCountdownCheckTest` — no-SparkSession, lambda provider injection (closest pattern)
- `TimestampSanityCheckTest` — error handling, null context, NOT_RUN semantics
- `BreakingChangeCheckTest` — JDBC history pattern reference

---

## Step 2: Generation Mode

**Mode selected:** AI generation (sequential)

**Rationale:** Backend stack with clear ACs and no browser UI needed. Sequential execution (no subagent parallelism required for two Java test files).

---

## Step 3: Test Strategy

### Acceptance Criteria to Test Scenario Mapping

| AC | Description | Test Level | Priority | Test Method |
|----|-------------|-----------|----------|-------------|
| AC1 | MetricNumeric + MetricDetail emitted for any classification | Unit | P0 | `executesReturnsPassForCriticalDataset`, `executeReturnsPassForStandardDataset`, `executeReturnsPassForLowPriorityDataset` |
| AC2 | Tier 1 Critical → multiplier=2.0 amplification | Unit | P0 | `executesReturnsPassForCriticalDataset` |
| AC3 | Null/missing lookup_code → PASS + reason=no_classification_found | Unit | P0 | `executeReturnsPassWhenNoClassificationFound`, `executeReturnsPassWhenLookupCodeIsNull` |
| AC4 | Source system health aggregated, FAIL/WARN/PASS thresholds applied | Unit | P0 | `executeWritesFailMetrics...`, `executeWritesWarnMetrics...`, `executeWritesPassMetrics...`, `executeFailDetailContainsAllRequiredContextFields` |
| AC4 (edge) | src_sys_nm correctly extracted from path | Unit | P1 | `executeExtractsCorrectSrcSysNmFromDatasetName` |
| AC4 (edge) | No history available → PASS + reason=no_history_available | Unit | P1 | `executeReturnsPassWhenNoHistoryAvailable` |
| AC5 | No src_sys_nm= segment → PASS + reason=no_source_system_segment | Unit | P0 | `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` |
| AC6 | Null context → NOT_RUN, no exception | Unit | P0 | Both `executeReturnsNotRunWhenContextIsNull` (×2) |
| AC6 | Provider exception → errorDetail, no propagation | Unit | P1 | Both `executeHandlesExceptionGracefully` (×2) |
| AC7 | Both implement DqCheck, getCheckType() constants correct | Unit | P1 | Both `getCheckTypeReturns*` (×2) |

### Test Levels

All tests are **Unit** level (JUnit 5, no JDBC, no Spark, no network).
Provider dependencies injected via `@FunctionalInterface` lambda (same pattern as `SlaCountdownCheckTest`).

### Red Phase Confirmation

All tests reference `ClassificationWeightedCheck` and `SourceSystemHealthCheck` — neither class exists in `dqs-spark/src/main/java/com/bank/dqs/checks/`. Tests will fail to compile until implementation is complete. This is the intentional TDD RED state.

---

## Step 4: Generated Test Files

### File 1: ClassificationWeightedCheckTest.java

**Path:** `dqs-spark/src/test/java/com/bank/dqs/checks/ClassificationWeightedCheckTest.java`

**Tests (8 total):**

| Method | AC | Priority | Description |
|--------|-----|---------|-------------|
| `executesReturnsPassForCriticalDataset` | AC1, AC2 | P0 | Tier 1 Critical → multiplier=2.0, MetricNumeric + MetricDetail emitted, status=PASS |
| `executeReturnsPassForStandardDataset` | AC1 | P0 | Tier 2 Standard → multiplier=1.0, MetricDetail status=PASS |
| `executeReturnsPassForLowPriorityDataset` | AC1 | P1 | Unknown classification → multiplier=0.5, MetricDetail status=PASS |
| `executeReturnsPassWhenNoClassificationFound` | AC3 | P0 | Provider returns empty → PASS + reason=no_classification_found, no MetricNumeric |
| `executeReturnsPassWhenLookupCodeIsNull` | AC3 | P0 | Null lookupCode → PASS + reason=no_classification_found, no MetricNumeric |
| `executeReturnsNotRunWhenContextIsNull` | AC6 | P0 | Null context → NOT_RUN detail, no exception, no MetricNumeric |
| `executeHandlesExceptionGracefully` | AC6 | P1 | Provider throws → error detail, no propagation, no MetricNumeric (metrics.clear()) |
| `getCheckTypeReturnsClassificationWeighted` | AC7 | P1 | getCheckType() == "CLASSIFICATION_WEIGHTED" == CHECK_TYPE constant |

**Expected failure reason:** `ClassificationWeightedCheck` class does not exist — compilation failure.

---

### File 2: SourceSystemHealthCheckTest.java

**Path:** `dqs-spark/src/test/java/com/bank/dqs/checks/SourceSystemHealthCheckTest.java`

**Tests (9 total):**

| Method | AC | Priority | Description |
|--------|-----|---------|-------------|
| `executeWritesFailMetricsWhenAggregateScoreBelowFailThreshold` | AC4 | P0 | score=45.0 < 60.0 → MetricNumeric aggregate_score+dataset_count, MetricDetail FAIL |
| `executeWritesWarnMetricsWhenAggregateScoreBetweenThresholds` | AC4 | P0 | score=68.0 ∈ [60,75) → MetricDetail WARN |
| `executeWritesPassMetricsWhenAggregateScoreAboveWarnThreshold` | AC4 | P0 | score=88.0 ≥ 75.0 → MetricDetail PASS |
| `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` | AC5 | P0 | No src_sys_nm= in path → PASS + reason=no_source_system_segment, no MetricNumeric |
| `executeReturnsPassWhenNoHistoryAvailable` | AC4 | P1 | Provider returns empty → PASS + reason=no_history_available, no MetricNumeric |
| `executeReturnsNotRunWhenContextIsNull` | AC6 | P0 | Null context → NOT_RUN detail, no exception, no MetricNumeric |
| `executeHandlesExceptionGracefully` | AC6 | P1 | Provider throws → error detail, no propagation, no MetricNumeric (metrics.clear()) |
| `getCheckTypeReturnsSourceSystemHealth` | AC7 | P1 | getCheckType() == "SOURCE_SYSTEM_HEALTH" == CHECK_TYPE constant |
| `executeExtractsCorrectSrcSysNmFromDatasetName` | AC4 | P1 | "lob=retail/src_sys_nm=alpha/dataset=sales_daily" → srcSysNm="alpha" captured by provider |
| `executeFailDetailContainsAllRequiredContextFields` | AC4 | P1 | FAIL detail contains src_sys_nm, aggregate_score, dataset_count, history_days, threshold_fail, threshold_warn |

**Note:** 10 test methods listed above; the test file contains 9 (two are combined in the count above — `executeExtractsCorrectSrcSysNmFromDatasetName` and `executeFailDetailContainsAllRequiredContextFields` are separate methods, total = 9 test methods in file).

**Expected failure reason:** `SourceSystemHealthCheck` class does not exist — compilation failure.

---

## Step 5: Validation

### Prerequisites
- [x] Story approved with clear acceptance criteria (7 ACs)
- [x] Test framework configured (JUnit 5 — `dqs-spark/pom.xml`, existing check tests)
- [x] Development environment available

### TDD Red Phase Compliance
- [x] `ClassificationWeightedCheck.java` does NOT exist in `src/main/java/com/bank/dqs/checks/`
- [x] `SourceSystemHealthCheck.java` does NOT exist in `src/main/java/com/bank/dqs/checks/`
- [x] Tests will fail to compile until implementation classes created — confirmed RED phase
- [x] All tests assert EXPECTED behavior (not placeholder assertions)
- [x] No `assertEquals(true, true)` or similar vacuous assertions

### Acceptance Criteria Coverage
- [x] AC1 — MetricNumeric + MetricDetail emitted for any classification
- [x] AC2 — Tier 1 Critical amplification (multiplier=2.0)
- [x] AC3 — null/missing lookup_code → graceful skip (PASS + no_classification_found)
- [x] AC4 — Source system health: FAIL/WARN/PASS thresholds, aggregate_score, dataset_count, history aggregation, src_sys_nm extraction
- [x] AC5 — No src_sys_nm= segment → graceful skip (PASS + no_source_system_segment)
- [x] AC6 — Null context → NOT_RUN + no exception; provider exception → error detail + no propagation
- [x] AC7 — Both implement DqCheck, correct CHECK_TYPE constants

### Patterns followed
- [x] `@FunctionalInterface` provider injection via lambda (matches `SlaCountdownCheck` pattern)
- [x] No SparkSession (neither check uses DataFrame)
- [x] `metrics.clear()` in catch block — tested via `executeHandlesExceptionGracefully`
- [x] `DatasetContext` with `df=null` (valid in test contexts per Javadoc)
- [x] `SourceSystemStats` inner record referenced correctly

---

## Next Steps (TDD Green Phase)

After implementing `ClassificationWeightedCheck.java` and `SourceSystemHealthCheck.java`:

1. Register both checks in `DqsJob.buildCheckFactory()` (Task 3)
2. Run: `mvn test -pl dqs-spark -Dtest=ClassificationWeightedCheckTest,SourceSystemHealthCheckTest`
3. Verify all 18 tests PASS (green phase)
4. Run full test suite: `mvn test -pl dqs-spark` — confirm 227+ tests, 0 failures
5. If any tests fail:
   - Fix the implementation (if test intent is correct per story ACs)
   - Or fix the test (if AC interpretation needs revision)
6. Proceed with `bmad-dev-story` to complete implementation

## Implementation Guidance

### Classes to create
- `dqs-spark/src/main/java/com/bank/dqs/checks/ClassificationWeightedCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/SourceSystemHealthCheck.java`

### File to modify
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` — add imports + register both checks after `TimestampSanityCheck`

### No other files change
- No DDL changes (views `v_lob_lookup_active`, `v_dq_run_active` already exist)
- No serve/API/dashboard changes (generic metric tables absorb new check types automatically)
- No schema changes to `DqCheck`, `CheckFactory`, model classes, or existing checks
