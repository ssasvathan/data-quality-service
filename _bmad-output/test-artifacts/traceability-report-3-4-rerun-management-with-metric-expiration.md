---
stepsCompleted:
  - step-01-load-context
  - step-02-discover-tests
  - step-03-map-criteria
  - step-04-analyze-gaps
  - step-05-gate-decision
lastStep: step-05-gate-decision
lastSaved: '2026-04-03'
story_id: 3-4-rerun-management-with-metric-expiration
---

# Traceability Report: Story 3-4 — Rerun Management with Metric Expiration

## Gate Decision: PASS

**Rationale:** P0 coverage is 100%, P1 coverage is 100% (target: 90%), and overall coverage is 100% (minimum: 80%). All 3 acceptance criteria plus safety scenarios are fully covered by 23 new unit tests across 5 test files (17 Python + 6 Java). No coverage gaps identified. All tests confirmed passing in TDD GREEN phase per story Task 8 verification.

---

## Step 1: Context Summary

### Story
As a **platform operator**, I want to rerun specific datasets with incrementing rerun_number and automatic expiration of previous metrics, so that I can fix failures without losing audit trail of the original run.

### Acceptance Criteria

| AC | Description |
|----|-------------|
| AC1 | Rerun triggers `--datasets` filter + incremented `rerun_number` in new `dq_run` record |
| AC2 | Previous `dq_run.expiry_date` set to current timestamp; metrics cascaded; views show only new run |
| AC3 | Raw table retains all historical rows with respective `expiry_dates` (full audit trail) |

### Input Documents
- Story file: `_bmad-output/implementation-artifacts/3-4-rerun-management-with-metric-expiration.md`
- ATDD checklist: `_bmad-output/test-artifacts/atdd-checklist-3-4-rerun-management-with-metric-expiration.md`
- Test files: `dqs-orchestrator/tests/test_db.py`, `test_cli.py`, `test_runner.py`
- Java test files: `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java`, `BatchWriterTest.java`

### Knowledge Base Applied
- `test-priorities-matrix.md` — P0/P1 classification criteria
- `risk-governance.md` — Gate decision rules (score 1-9, FAIL/CONCERNS/PASS thresholds)
- `probability-impact.md` — Risk scoring framework
- `test-quality.md` — Definition of Done criteria
- `selective-testing.md` — Test execution strategy

---

## Step 2: Test Discovery & Catalog

### Test Level Classification

| File | Level | Story 3-4 Tests | Total Tests in File |
|------|-------|-----------------|---------------------|
| `dqs-orchestrator/tests/test_db.py` | Unit (Python) | 9 new | ~17 |
| `dqs-orchestrator/tests/test_cli.py` | Unit (Python) | 4 new | ~30 |
| `dqs-orchestrator/tests/test_runner.py` | Unit (Python) | 4 new | ~15 |
| `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` | Unit (Java) | 4 new | ~15 |
| `dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java` | Unit (Java) | 2 new + 11 updated call sites | ~16 |

**Total new Story 3-4 tests: 23** (17 Python + 6 Java)

### Story 3-4 Test Inventory

#### Python — `test_db.py` (9 tests)
| Test Name | AC | Priority |
|-----------|----|----------|
| `test_expire_previous_run_updates_expiry_date_on_dq_run` | AC2 | P0 |
| `test_expire_previous_run_cascades_to_metric_numeric` | AC2 | P0 |
| `test_expire_previous_run_cascades_to_metric_detail` | AC2 | P0 |
| `test_expire_previous_run_commits_all_three_updates_in_one_transaction` | AC2 | P0 |
| `test_expire_previous_run_returns_none_when_no_active_run` | AC2 | P1 |
| `test_expire_previous_run_uses_expiry_sentinel_constant_in_where_clause` | AC2 | P0 |
| `test_get_next_rerun_number_returns_zero_when_no_history` | AC1, AC3 | P0 |
| `test_get_next_rerun_number_returns_one_when_first_run_exists` | AC1 | P0 |
| `test_get_next_rerun_number_queries_all_history_including_expired` | AC1, AC3 | P0 |

#### Python — `test_cli.py` (4 tests)
| Test Name | AC | Priority |
|-----------|----|----------|
| `test_main_calls_expire_previous_run_when_rerun_flag_set` | AC1, AC2 | P0 |
| `test_main_does_not_call_expire_previous_run_when_rerun_not_set` | AC1 | P0 |
| `test_main_expire_error_is_non_fatal_spark_submit_still_proceeds` | Safety | P0 |
| `test_main_passes_rerun_numbers_to_run_all_paths` | AC1 | P0 |

#### Python — `test_runner.py` (4 tests)
| Test Name | AC | Priority |
|-----------|----|----------|
| `test_run_spark_job_appends_rerun_number_to_command` | AC1 | P0 |
| `test_run_spark_job_omits_rerun_number_when_none` | AC1 | P1 |
| `test_run_all_paths_threads_rerun_numbers_to_spark_job` | AC1 | P0 |
| `test_run_all_paths_passes_none_rerun_number_when_rerun_numbers_is_none` | AC1 | P1 |

#### Java — `DqsJobArgParserTest.java` (4 tests)
| Test Name | AC | Priority |
|-----------|----|----------|
| `parseArgsExtractsRerunNumber` | AC1 | P0 |
| `parseArgsDefaultsRerunNumberToZeroWhenAbsent` | AC1 | P1 |
| `parseArgsThrowsOnInvalidRerunNumberValue` | AC1 | P1 |
| `parseArgsThrowsOnDanglingRerunNumberFlag` | AC1 | P1 |

#### Java — `BatchWriterTest.java` (2 new tests + 11 call sites updated)
| Test Name | AC | Priority |
|-----------|----|----------|
| `writeStoresRerunNumberInDqRun` | AC1 | P0 |
| `writeStoresZeroRerunNumberForFirstRun` | AC1 | P1 |

### Coverage Heuristics Inventory

- **API endpoint coverage:** N/A — pure backend project; no HTTP endpoints. DB functions are the "API". All relevant DB operations (`expire_previous_run`, `get_next_rerun_number`, `insertDqRun`) are directly tested.
- **Authentication/authorization coverage:** N/A — no auth/authz requirements in this story.
- **Error-path coverage:** Covered — non-fatal error handling tested for `expire_previous_run` raising an exception (safety test). "No active run" edge case covered. Invalid CLI args (non-integer `--rerun-number`, dangling flag) covered in Java.
- **Happy-path-only blind spots:** None detected — both happy paths (rerun triggered, rerun not triggered) and error paths (DB error non-fatal, no active run to expire, first-time run) are covered.

---

## Step 3: Traceability Matrix

### Acceptance Criteria to Tests Mapping

| AC | Criterion Description | Tests | Coverage Status | Priority |
|----|-----------------------|-------|-----------------|----------|
| AC1 | `get_next_rerun_number()` returns incremented value across all history | `test_get_next_rerun_number_returns_zero_when_no_history`, `test_get_next_rerun_number_returns_one_when_first_run_exists`, `test_get_next_rerun_number_queries_all_history_including_expired` (Python); `parseArgsExtractsRerunNumber`, `parseArgsDefaultsRerunNumberToZeroWhenAbsent` (Java); `writeStoresRerunNumberInDqRun`, `writeStoresZeroRerunNumberForFirstRun` (Java) | FULL | P0 |
| AC1 | `run_spark_job()` passes `--rerun-number` to spark-submit | `test_run_spark_job_appends_rerun_number_to_command`, `test_run_spark_job_omits_rerun_number_when_none`, `test_run_all_paths_threads_rerun_numbers_to_spark_job`, `test_run_all_paths_passes_none_rerun_number_when_rerun_numbers_is_none` | FULL | P0 |
| AC1 | `main()` wires rerun management into CLI | `test_main_calls_expire_previous_run_when_rerun_flag_set`, `test_main_does_not_call_expire_previous_run_when_rerun_not_set`, `test_main_passes_rerun_numbers_to_run_all_paths` | FULL | P0 |
| AC2 | `expire_previous_run()` expires `dq_run` and cascades to metric tables in one transaction | `test_expire_previous_run_updates_expiry_date_on_dq_run`, `test_expire_previous_run_cascades_to_metric_numeric`, `test_expire_previous_run_cascades_to_metric_detail`, `test_expire_previous_run_commits_all_three_updates_in_one_transaction`, `test_expire_previous_run_uses_expiry_sentinel_constant_in_where_clause` | FULL | P0 |
| AC2 | `expire_previous_run()` handles no-active-run gracefully | `test_expire_previous_run_returns_none_when_no_active_run` | FULL | P1 |
| AC2 | Active views show only new rerun data | Covered implicitly by `test_expire_previous_run_updates_expiry_date_on_dq_run` — expiry_date is set on previous run, so active views (WHERE expiry_date = EXPIRY_SENTINEL) exclude it. No E2E test required (backend stack, view logic is in SQL schema verified by Story 1-6). | FULL | P0 |
| AC3 | Raw table retains all historical rows (audit trail) | `test_get_next_rerun_number_queries_all_history_including_expired` — verifies no `expiry_date` filter on `get_next_rerun_number()` query, proving all history is preserved and accessible | FULL | P0 |
| Safety | DB errors in `expire_previous_run` are non-fatal | `test_main_expire_error_is_non_fatal_spark_submit_still_proceeds` | FULL | P0 |
| Safety | `main()` does NOT call `expire_previous_run` on normal runs | `test_main_does_not_call_expire_previous_run_when_rerun_not_set` | FULL | P0 |

### Coverage Validation

- All P0 criteria: FULL coverage — 100%
- All P1 criteria: FULL coverage — 100%
- All P2/P3 criteria: None present in this story
- Error-path criteria: All covered (non-fatal exceptions, no-active-run edge case, invalid args)
- EXPIRY_SENTINEL pattern: Explicitly tested — `test_expire_previous_run_uses_expiry_sentinel_constant_in_where_clause` and `test_create_orchestration_run_uses_expiry_sentinel` (from Story 3-3, still passing)
- No duplicate coverage without justification

---

## Step 4: Gap Analysis & Coverage Statistics

### Coverage Statistics

| Metric | Value |
|--------|-------|
| Total Acceptance Criteria | 3 (AC1, AC2, AC3) |
| Fully Covered | 3 (100%) |
| Partially Covered | 0 |
| Uncovered | 0 |
| Overall Coverage | 100% |

### Priority Breakdown

| Priority | Total | Covered | Percentage |
|----------|-------|---------|------------|
| P0 | 16 test scenarios | 16 | 100% |
| P1 | 7 test scenarios | 7 | 100% |
| P2 | 0 | 0 | 100% (N/A) |
| P3 | 0 | 0 | 100% (N/A) |

### Gap Analysis

**Critical Gaps (P0):** 0

**High Gaps (P1):** 0

**Medium Gaps (P2):** 0

**Low Gaps (P3):** 0

**Partial Coverage Items:** 0

**Unit-Only Coverage Items:** 23 (all tests are unit tests — this is correct and appropriate for a pure backend project per ATDD checklist and stack detection)

### Coverage Heuristic Checks

| Heuristic | Gaps Found | Severity |
|-----------|-----------|---------|
| Endpoints without direct tests | 0 | None |
| Auth/authz negative-path gaps | 0 | None (no auth in story) |
| Happy-path-only criteria | 0 | None |

### Risk Assessment

| Risk | Probability | Impact | Score | Action |
|------|------------|--------|-------|--------|
| Ordering constraint: `expire_previous_run()` must precede `run_all_paths()` | 2 (possible) | 3 (critical — UNIQUE constraint violation in DB) | 6 | MITIGATE |
| UNIQUE constraint on `(dataset_name, partition_date, rerun_number, expiry_date)` | 1 (unlikely — orchestrator enforces ordering) | 3 (critical) | 3 | DOCUMENT |
| Multi-dataset rerun uses `max(rerun_numbers.values())` | 1 (unlikely) | 2 (degraded) | 2 | DOCUMENT |

**Mitigation for Risk Score 6 (ordering constraint):** Tested via `test_main_calls_expire_previous_run_when_rerun_flag_set` which verifies `expire_previous_run` is called before `run_all_paths`. Additionally `test_main_passes_rerun_numbers_to_run_all_paths` verifies the rerun_numbers are correctly threaded through, confirming expire→spark order. Risk is MITIGATED by test coverage.

### Regression Coverage

| Baseline | Tests |
|----------|-------|
| Python regression (Story 3-3 + earlier) | 49 tests — all pass per Task 8 |
| Java regression (Stories 2-9, 2-10, earlier) | 171 tests — all pass per Task 8 |
| New Story 3-4 tests | 23 tests — all pass (TDD GREEN) |
| **Total Python** | **66 tests pass** |
| **Total Java** | **177 tests pass** |

### Recommendations

1. **NONE — PASS:** All acceptance criteria are fully covered with unit tests at both Python and Java layers. No gaps require action.
2. The ordering constraint risk (P×I = 6) is mitigated by existing test coverage. No additional tests needed.
3. Consider adding integration/E2E test (with live PostgreSQL) in a future sprint if the UNIQUE constraint enforcement becomes a reliability concern. This is not required for the current gate.

---

## Step 5: Gate Decision

### Gate Criteria Evaluation

| Criterion | Required | Actual | Status |
|-----------|----------|--------|--------|
| P0 Coverage | 100% | 100% | MET |
| P1 Coverage (PASS target) | 90% | 100% | MET |
| P1 Coverage (minimum) | 80% | 100% | MET |
| Overall Coverage (minimum) | 80% | 100% | MET |
| Critical Gaps (P0 uncovered) | 0 | 0 | MET |
| Test Quality (passing) | All pass | 66 Python + 177 Java = 243 total pass | MET |

### Gate Decision: PASS

**Rationale:** P0 coverage is 100%, P1 coverage is 100% (target: 90%), and overall coverage is 100% (minimum: 80%). All 3 acceptance criteria are fully covered by 23 new unit tests (17 Python + 6 Java). No coverage gaps exist. All 243 tests pass (66 Python + 177 Java). Ordering constraint risk is mitigated by test coverage. Story 3-4 is approved for release.

### Decision Date: 2026-04-03

### Uncovered Requirements: None

### Full Traceability Matrix Summary

| Requirement | Tests | Coverage | Priority | Gate Contribution |
|-------------|-------|----------|----------|-------------------|
| AC1: rerun_number increments | 9 Python + 6 Java = 15 tests | FULL | P0 | PASS |
| AC2: expiry cascade (dq_run + metrics + transaction) | 6 Python tests | FULL | P0 | PASS |
| AC2: graceful no-active-run | 1 Python test | FULL | P1 | PASS |
| AC2: active views isolation | Covered by expire logic tests | FULL | P0 | PASS |
| AC3: audit trail (raw table all history) | 1 Python test | FULL | P0 | PASS |
| Safety: expire non-fatal | 2 Python tests | FULL | P0 | PASS |

---

## GATE: PASS — Release approved, coverage meets standards

All acceptance criteria covered. 243 tests pass. Story 3-4 quality gate: **PASS**.
