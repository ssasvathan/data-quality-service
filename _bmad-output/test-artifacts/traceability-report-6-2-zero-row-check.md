---
stepsCompleted:
  - step-01-load-context
  - step-02-discover-tests
  - step-03-map-criteria
  - step-04-analyze-gaps
  - step-05-gate-decision
lastStep: step-05-gate-decision
lastSaved: '2026-04-04'
story: 6-2-zero-row-check
gateDecision: PASS
---

# Traceability Report — Story 6.2: Zero-Row Check

## Gate Decision: PASS

**Rationale:** P0 coverage is 100%, P1 coverage is 100% (target: 90%), and overall coverage is
100% (minimum: 80%). All 4 acceptance criteria are fully covered by 7 dedicated unit tests.
No critical gaps identified. Happy-path, boundary, null-guard, and exception-path scenarios are
all covered. `ZeroRowCheck` is registered in `DqsJob.buildCheckFactory()` and the full regression
suite passes (190 tests, 0 failures).

**Decision Date:** 2026-04-04

---

## Coverage Summary

| Metric | Count | Percentage |
|---|---|---|
| Total Requirements | 7 | — |
| Fully Covered | 7 | 100% |
| Partially Covered | 0 | 0% |
| Uncovered | 0 | 0% |
| **Overall Coverage** | **7/7** | **100%** |

### Priority Coverage

| Priority | Total | Covered | % | Status |
|---|---|---|---|---|
| P0 | 4 | 4 | 100% | MET |
| P1 | 3 | 3 | 100% | MET |
| P2 | 0 | — | N/A | N/A |
| P3 | 0 | — | N/A | N/A |

---

## Gate Criteria

| Gate Criterion | Required | Actual | Status |
|---|---|---|---|
| P0 coverage | 100% | 100% | MET |
| P1 coverage (PASS target) | 90% | 100% | MET |
| Overall coverage | ≥80% | 100% | MET |
| Critical gaps (P0 uncovered) | 0 | 0 | MET |

---

## Traceability Matrix

### AC1: Zero rows → FAIL, MetricNumeric(ZERO_ROW, row_count, 0.0) + status detail with status=FAIL

| Req ID | Scenario | Test(s) | Coverage | Priority | Level |
|---|---|---|---|---|---|
| AC1-R1 | Empty DataFrame → MetricNumeric with check_type=ZERO_ROW, metric_value=0.0 | `ZeroRowCheckTest::executeReturnsFailAndZeroRowCountWhenDataFrameIsEmpty` | FULL | P0 | Unit |
| AC1-R2 | Empty DataFrame → MetricDetail with status=FAIL, reason=zero_rows, row_count=0 | `ZeroRowCheckTest::executeReturnsFailAndZeroRowCountWhenDataFrameIsEmpty` | FULL | P0 | Unit |

### AC2: N rows → PASS, MetricNumeric(ZERO_ROW, row_count, N.0) + status detail with status=PASS

| Req ID | Scenario | Test(s) | Coverage | Priority | Level |
|---|---|---|---|---|---|
| AC2-R1 | DataFrame with 5 rows → MetricNumeric value=5.0, status=PASS, reason=has_rows | `ZeroRowCheckTest::executeReturnsPassAndRowCountWhenDataFrameHasRows` | FULL | P0 | Unit |
| AC2-R2 | Boundary: exactly 1 row → PASS, row_count=1.0 (not-zero = not-empty) | `ZeroRowCheckTest::executeReturnsSingleRowHandledCorrectly` | FULL | P1 | Unit |

### AC3: Null context or null DataFrame → NOT_RUN detail, no exception propagation

| Req ID | Scenario | Test(s) | Coverage | Priority | Level |
|---|---|---|---|---|---|
| AC3-R1 | Null context → MetricDetail with status=NOT_RUN, reason=missing_context, no exception | `ZeroRowCheckTest::executeReturnsNotRunWhenContextIsNull` | FULL | P0 | Unit |
| AC3-R2 | Context with null df → MetricDetail with status=NOT_RUN, reason=missing_dataframe, no exception | `ZeroRowCheckTest::executeReturnsNotRunWhenDataFrameIsNull` | FULL | P0 | Unit |
| AC3-R3 | Exception path: DataFrame.count() throws → errorDetail with status=NOT_RUN, reason=execution_error, error_type captured | `ZeroRowCheckTest::executeReturnsErrorDetailWhenDataFrameCountThrows` | FULL | P1 | Unit |

### AC4: Implements DqCheck, registered in CheckFactory, zero changes to serve/API/dashboard

| Req ID | Scenario | Test(s) | Coverage | Priority | Level |
|---|---|---|---|---|---|
| AC4-R1 | getCheckType() returns canonical "ZERO_ROW" string and matches CHECK_TYPE constant | `ZeroRowCheckTest::getCheckTypeReturnsZeroRow` | FULL | P1 | Unit |
| AC4-R2 | ZeroRowCheck registered in DqsJob.buildCheckFactory() after SlaCountdownCheck | `DqsJob.java` lines 316 (import + registration verified) | FULL | P1 | Static |

---

## Gap Analysis

### Critical Gaps (P0 Uncovered): 0

None.

### High Gaps (P1 Uncovered): 0

None.

### Coverage Heuristics

| Heuristic | Finding | Risk |
|---|---|---|
| Endpoints without tests | N/A — AC4 explicitly states zero changes to serve/API/dashboard. No new HTTP endpoints. | None |
| Auth/authz negative-path gaps | N/A — no auth layer in Spark checks | None |
| Happy-path-only criteria | 0 — null-context, null-df, and exception-thrown paths all have dedicated tests covering not-run and error cases | None |
| DataFrame exception injection | Covered by `executeReturnsErrorDetailWhenDataFrameCountThrows` using Mockito mock of Dataset.count() throwing RuntimeException | P=1, I=1, Score=1 → DOCUMENT |
| No MetricNumeric in NOT_RUN paths | Verified: all three NOT_RUN tests assert `numericCount == 0` | Covered |

---

## Test Inventory

**Test file:** `dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java`

### Story 6.2 Unit Tests (7)

| Class | Method | AC | Priority | Status |
|---|---|---|---|---|
| ZeroRowCheckTest | `executeReturnsFailAndZeroRowCountWhenDataFrameIsEmpty` | AC1 | P0 | PASS |
| ZeroRowCheckTest | `executeReturnsPassAndRowCountWhenDataFrameHasRows` | AC2 | P0 | PASS |
| ZeroRowCheckTest | `executeReturnsSingleRowHandledCorrectly` | AC2 boundary | P1 | PASS |
| ZeroRowCheckTest | `executeReturnsNotRunWhenContextIsNull` | AC3 | P0 | PASS |
| ZeroRowCheckTest | `executeReturnsNotRunWhenDataFrameIsNull` | AC3 | P0 | PASS |
| ZeroRowCheckTest | `getCheckTypeReturnsZeroRow` | AC4 | P1 | PASS |
| ZeroRowCheckTest | `executeReturnsErrorDetailWhenDataFrameCountThrows` | AC3 exception path | P1 | PASS |

**Test infrastructure:** SparkSession `local[1]` via `@BeforeAll`/`@AfterAll` — matches `VolumeCheckTest` pattern.

**Regression suite:** 190 tests total, 0 failures (up from 184 before story 6.1; 6 new tests added by this story).

---

## Coverage Heuristics Summary

| Category | Gaps |
|---|---|
| Endpoints without tests | 0 |
| Auth negative-path gaps | 0 |
| Happy-path-only criteria | 0 |

---

## Recommendations

1. **LOW (advisory):** The ATDD checklist generated for this story noted 6 tests at RED phase. The implementation activates a 7th test (`executeReturnsErrorDetailWhenDataFrameCountThrows`) for the exception path using Mockito mocking. This extra test strengthens coverage — no action required.
2. **LOW:** Run `/bmad:tea:test-review` to assess test quality if desired.

---

## Implementation Files

| File | Status |
|---|---|
| `dqs-spark/src/main/java/com/bank/dqs/checks/ZeroRowCheck.java` | NEW — implements `DqCheck`, no-arg constructor, full constants, null guards, try/catch isolation, four private helpers |
| `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` | MODIFIED — `import com.bank.dqs.checks.ZeroRowCheck` added + `f.register(new ZeroRowCheck())` after `SlaCountdownCheck` |
| `dqs-spark/src/test/java/com/bank/dqs/checks/ZeroRowCheckTest.java` | MODIFIED — `@Disabled` annotations removed, all 7 tests active and passing |

---

## GATE DECISION: PASS

**P0 Coverage:** 4/4 (100%) — Required: 100% — **MET**

**P1 Coverage:** 3/3 (100%) — PASS target: 90%, minimum: 80% — **MET**

**Overall Coverage:** 7/7 (100%) — Minimum: 80% — **MET**

**Critical Gaps:** 0

**Release Status:** PASS — Release approved, coverage meets standards. Story 6.2 is complete.
