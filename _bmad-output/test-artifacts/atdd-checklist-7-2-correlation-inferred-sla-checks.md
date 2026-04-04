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
story_id: 7-2-correlation-inferred-sla-checks
tdd_phase: RED
total_tests: 19
unit_tests: 19
all_tests_failing: true
---

# ATDD Checklist: Story 7.2 — Correlation & Inferred SLA Checks

## TDD Red Phase (Current)

Tests generated — will fail to compile until implementation classes exist.

- `CorrelationCheckTest.java`: 9 tests (RED — class `CorrelationCheck` does not exist)
- `InferredSlaCheckTest.java`: 10 tests (RED — class `InferredSlaCheck` does not exist)

**Total: 19 failing unit tests**

---

## Step 1: Preflight & Context

**Stack detected:** `backend` (Java/Maven — `dqs-spark/pom.xml` present, no frontend indicators)

**Prerequisites:**
- Story status: `ready-for-dev` — approved with clear acceptance criteria
- Test framework: JUnit 5 (confirmed via existing test files in `dqs-spark/src/test/`)
- No SparkSession required — neither check calls `context.getDf()`

**Story file loaded:** `_bmad-output/implementation-artifacts/7-2-correlation-inferred-sla-checks.md`

**Acceptance criteria count:** 7 (AC1–AC7)

**Key patterns loaded from existing tests:**
- `SourceSystemHealthCheckTest` — src_sys_nm extraction, empty provider, context field assertions (CorrelationCheck mirrors this pattern closely)
- `ClassificationWeightedCheckTest` — lambda provider injection, null context, exception handling
- `SlaCountdownCheckTest` — explicit SLA check, empty list return pattern (InferredSlaCheck defers to SlaCountdownCheck)

---

## Step 2: Generation Mode

**Mode selected:** AI generation (sequential)

**Rationale:** Backend stack with clear ACs and no browser UI needed. Sequential execution (no subagent parallelism required for two Java test files).

---

## Step 3: Test Strategy

### Acceptance Criteria to Test Scenario Mapping

**CorrelationCheck (9 tests):**

| AC | Description | Test Level | Priority | Test Method |
|----|-------------|-----------|----------|-------------|
| AC1 | ratio=0.6 >= 0.50 → FAIL + MetricNumeric correlated_dataset_count + correlation_ratio | Unit | P0 | `executeWritesFailMetricsWhenMajorityOfSourceSystemDegraded` |
| AC1 | ratio=0.33 ∈ [0.25, 0.50) → WARN | Unit | P0 | `executeWritesWarnMetricsWhenMinorityDegraded` |
| AC1 | ratio=0.10 < 0.25 → PASS | Unit | P0 | `executeWritesPassMetricsWhenNoDegradationCorrelation` |
| AC2 | No src_sys_nm= segment → PASS + reason=no_source_system_segment | Unit | P0 | `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` |
| AC1 (edge) | Provider returns empty (no current-day data) → PASS + reason=no_current_day_data | Unit | P1 | `executeReturnsPassWhenNoCurrentDayData` |
| AC6 | Null context → NOT_RUN, no exception | Unit | P0 | `executeReturnsNotRunWhenContextIsNull` |
| AC6 | Provider throws → errorDetail, no propagation | Unit | P1 | `executeHandlesExceptionGracefully` |
| AC7 | getCheckType() == "CORRELATION" | Unit | P1 | `getCheckTypeReturnsCorrelation` |
| AC1 (edge) | FAIL payload contains all required context fields | Unit | P1 | `executeFailDetailContainsAllRequiredContextFields` |

**InferredSlaCheck (10 tests):**

| AC | Description | Test Level | Priority | Test Method |
|----|-------------|-----------|----------|-------------|
| AC3 | mean=10.0, stddev=2.0 → inferredSlaHours=14.0; current=16.0 → deviation=+2.0 → FAIL | Unit | P0 | `executeWritesFailMetricsWhenCurrentDeliveryLaterThanInferredWindow` |
| AC3 | current just below inferred but within 20% → WARN | Unit | P0 | `executeWritesWarnMetricsWhenApproachingInferredWindow` |
| AC3 | current well within window → PASS | Unit | P0 | `executeWritesPassMetricsWhenDeliveryWellWithinInferredWindow` |
| AC4 | hasExplicitSla=true → empty list (SlaCountdownCheck handles this) | Unit | P0 | `executeReturnsEmptyWhenExplicitSlaIsConfigured` |
| AC5 | < 7 data points → PASS + reason=insufficient_history | Unit | P0 | `executeReturnsPassWhenInsufficientHistory` |
| AC6 | Null context → NOT_RUN, no exception | Unit | P0 | `executeReturnsNotRunWhenContextIsNull` |
| AC6 | Provider throws → errorDetail, no propagation | Unit | P1 | `executeHandlesExceptionGracefully` |
| AC7 | getCheckType() == "INFERRED_SLA" | Unit | P1 | `getCheckTypeReturnsInferredSla` |
| AC3 (edge) | PASS detail contains required statistics fields | Unit | P1 | `executePassDetailContainsRequiredStatisticsFields` |
| AC3 (edge) | MetricNumeric values mathematically correct for given history | Unit | P1 | `executeEmitsCorrectMetricNumericsForInferredWindow` |

### Test Levels

All tests are **Unit** level (JUnit 5, no JDBC, no Spark, no network).
Provider dependencies injected via `@FunctionalInterface` lambda (same pattern as `SourceSystemHealthCheckTest`).

### Red Phase Confirmation

All tests reference `CorrelationCheck` and `InferredSlaCheck` — neither class exists in
`dqs-spark/src/main/java/com/bank/dqs/checks/`. Tests will fail to compile until implementation
is complete. This is the intentional TDD RED state.

---

## Step 4: Generated Test Files

### File 1: CorrelationCheckTest.java

**Path:** `dqs-spark/src/test/java/com/bank/dqs/checks/CorrelationCheckTest.java`

**Tests (9 total):**

| Method | AC | Priority | Description |
|--------|-----|---------|-------------|
| `executeWritesFailMetricsWhenMajorityOfSourceSystemDegraded` | AC1 | P0 | ratio=0.60 ≥ 0.50 → MetricNumeric correlated_dataset_count=3 + correlation_ratio=0.60, MetricDetail FAIL |
| `executeWritesWarnMetricsWhenMinorityDegraded` | AC1 | P0 | ratio=0.33 ∈ [0.25, 0.50) → MetricDetail WARN |
| `executeWritesPassMetricsWhenNoDegradationCorrelation` | AC1 | P0 | ratio=0.10 < 0.25 → MetricDetail PASS |
| `executeReturnsPassWhenNoSourceSystemSegmentInDatasetName` | AC2 | P0 | No src_sys_nm= in path → PASS + reason=no_source_system_segment, no MetricNumeric |
| `executeReturnsPassWhenNoCurrentDayData` | AC1 | P1 | Provider returns empty → PASS + reason=no_current_day_data, no MetricNumeric |
| `executeReturnsNotRunWhenContextIsNull` | AC6 | P0 | Null context → NOT_RUN detail, no exception, no MetricNumeric |
| `executeHandlesExceptionGracefully` | AC6 | P1 | Provider throws → error detail, no propagation, no MetricNumeric (metrics.clear()) |
| `getCheckTypeReturnsCorrelation` | AC7 | P1 | getCheckType() == "CORRELATION" == CHECK_TYPE constant |
| `executeFailDetailContainsAllRequiredContextFields` | AC1 | P1 | FAIL detail contains src_sys_nm, correlated_dataset_count, total_dataset_count, correlation_ratio, history_days, threshold_fail, threshold_warn |

**Expected failure reason:** `CorrelationCheck` class does not exist — compilation failure.

---

### File 2: InferredSlaCheckTest.java

**Path:** `dqs-spark/src/test/java/com/bank/dqs/checks/InferredSlaCheckTest.java`

**Tests (10 total):**

| Method | AC | Priority | Description |
|--------|-----|---------|-------------|
| `executeWritesFailMetricsWhenCurrentDeliveryLaterThanInferredWindow` | AC3 | P0 | mean=10.0, stddev=2.0 → inferred=14.0; current=16.0 → deviation=+2.0 > 0 → FAIL |
| `executeWritesWarnMetricsWhenApproachingInferredWindow` | AC3 | P0 | current=12.0, inferred=14.0, deviation=-2.0 > -2.8 (20% threshold) → WARN |
| `executeWritesPassMetricsWhenDeliveryWellWithinInferredWindow` | AC3 | P0 | current=5.0, inferred=14.0, deviation=-9.0 < -2.8 → PASS |
| `executeReturnsEmptyWhenExplicitSlaIsConfigured` | AC4 | P0 | hasExplicitSla=true → empty list (SlaCountdownCheck handles this dataset) |
| `executeReturnsPassWhenInsufficientHistory` | AC5 | P0 | 4 data points (< 7 minimum) → PASS + reason=insufficient_history, no MetricNumeric |
| `executeReturnsNotRunWhenContextIsNull` | AC6 | P0 | Null context → NOT_RUN detail, no exception, no MetricNumeric |
| `executeHandlesExceptionGracefully` | AC6 | P1 | Provider throws → error detail, no propagation, no MetricNumeric (metrics.clear()) |
| `getCheckTypeReturnsInferredSla` | AC7 | P1 | getCheckType() == "INFERRED_SLA" == CHECK_TYPE constant |
| `executePassDetailContainsRequiredStatisticsFields` | AC3 | P1 | PASS detail contains current_hours, inferred_sla_hours, deviation_from_inferred, mean_hours, stddev_hours, data_points |
| `executeEmitsCorrectMetricNumericsForInferredWindow` | AC3 | P1 | MetricNumeric inferred_sla_hours ≈ 14.0, deviation_from_inferred > 0.0, status=FAIL confirmed |

**Expected failure reason:** `InferredSlaCheck` class does not exist — compilation failure.

---

## Step 5: Validation

### Prerequisites
- [x] Story approved with clear acceptance criteria (7 ACs)
- [x] Test framework configured (JUnit 5 — `dqs-spark/pom.xml`, existing check tests)
- [x] Development environment available

### TDD Red Phase Compliance
- [x] `CorrelationCheck.java` does NOT exist in `src/main/java/com/bank/dqs/checks/`
- [x] `InferredSlaCheck.java` does NOT exist in `src/main/java/com/bank/dqs/checks/`
- [x] Tests will fail to compile until implementation classes created — confirmed RED phase
- [x] All tests assert EXPECTED behavior (not placeholder assertions)
- [x] No `assertEquals(true, true)` or similar vacuous assertions
- [x] No orphaned browser sessions (N/A — backend only)
- [x] Checklist stored in `_bmad-output/test-artifacts/` not a random location

### Acceptance Criteria Coverage
- [x] AC1 — CorrelationCheck: FAIL (ratio ≥ 0.50), WARN (ratio ≥ 0.25), PASS; MetricNumeric correlated_dataset_count + correlation_ratio; MetricDetail with full context fields
- [x] AC2 — CorrelationCheck: no src_sys_nm= → graceful skip (PASS + no_source_system_segment)
- [x] AC3 — InferredSlaCheck: FAIL (deviation > 0), WARN (approaching 20% zone), PASS; MetricNumeric inferred_sla_hours + deviation_from_inferred; MetricDetail with statistics fields
- [x] AC4 — InferredSlaCheck: explicit SLA → empty list (SlaCountdownCheck deference)
- [x] AC5 — InferredSlaCheck: < 7 data points → graceful skip (PASS + insufficient_history)
- [x] AC6 — Both: null context → NOT_RUN + no exception; provider exception → error detail + no propagation
- [x] AC7 — Both implement DqCheck, correct CHECK_TYPE constants; require zero changes to serve/API/dashboard

### Patterns followed
- [x] `@FunctionalInterface` provider injection via lambda (matches `SourceSystemHealthCheck` pattern)
- [x] No SparkSession (neither check uses DataFrame)
- [x] `metrics.clear()` in catch block — tested via `executeHandlesExceptionGracefully` in both files
- [x] `DatasetContext` with `df=null` (valid in test contexts per Javadoc)
- [x] `CorrelationStats` and `SlaHistory` inner records referenced correctly
- [x] History values chosen to produce exact mean=10.0, stddev≈2.0 for deterministic assertions
- [x] WARN threshold direction verified: `deviationFromInferred > -inferredSlaHours * 0.20`

---

## Next Steps (TDD Green Phase)

After implementing `CorrelationCheck.java` and `InferredSlaCheck.java`:

1. Register both checks in `DqsJob.buildCheckFactory()` (Task 3):
   - AFTER `SourceSystemHealthCheck`, BEFORE `DqsScoreCheck`
2. Run: `mvn test -pl dqs-spark -Dtest=CorrelationCheckTest,InferredSlaCheckTest`
3. Verify all 19 tests PASS (green phase)
4. Run full test suite: `mvn test -pl dqs-spark` — confirm 0 failures
5. If any tests fail:
   - Fix the implementation (if test intent is correct per story ACs)
   - Or fix the test (if AC interpretation needs revision)
6. Proceed with `bmad-dev-story` to complete implementation

## Implementation Guidance

### Classes to create
- `dqs-spark/src/main/java/com/bank/dqs/checks/CorrelationCheck.java`
- `dqs-spark/src/main/java/com/bank/dqs/checks/InferredSlaCheck.java`

### File to modify
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` — add imports + register both checks after `SourceSystemHealthCheck`

### No other files change
- No DDL changes (`v_dq_run_active`, `v_dq_metric_numeric_active`, `v_dataset_enrichment_active` already exist)
- No serve/API/dashboard changes (generic metric tables absorb new check types automatically)
- No schema changes to `DqCheck`, `CheckFactory`, model classes, or existing checks

### Key implementation notes
- `CorrelationCheck` uses a correlated subquery — each dataset reports the source system picture from its own perspective
- `InferredSlaCheck` uses population stddev (not sample stddev) — `sqrt(sumSqDiff / n)`, not `sqrt(sumSqDiff / (n-1))`
- `SlaHistory.hasExplicitSla` is queried first — short-circuit to empty list before expensive history query if true
- Both checks delegate to `NoOpXxxProvider` (no-arg constructor) for non-JDBC contexts; DqsJob always uses the JDBC constructor
