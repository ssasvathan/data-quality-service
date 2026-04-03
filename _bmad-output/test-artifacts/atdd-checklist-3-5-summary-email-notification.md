---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: '2026-04-03'
story_id: 3-5-summary-email-notification
tdd_phase: RED
total_tests: 15
tests_passing: 0
tests_failing: 15
baseline_tests_passing: 65
inputDocuments:
  - _bmad-output/implementation-artifacts/3-5-summary-email-notification.md
  - _bmad/tea/config.yaml
  - dqs-orchestrator/tests/test_db.py
  - dqs-orchestrator/tests/test_cli.py
  - dqs-orchestrator/tests/test_email.py
  - dqs-orchestrator/src/orchestrator/email.py
  - dqs-orchestrator/src/orchestrator/models.py
  - dqs-orchestrator/src/orchestrator/db.py
---

# ATDD Checklist: Story 3-5 Summary Email Notification

## Step 1: Preflight & Context

**Stack Detection:** `backend` (Python/pyproject.toml, no frontend indicators)

**Prerequisites:**
- Story file: `_bmad-output/implementation-artifacts/3-5-summary-email-notification.md` — status: `ready-for-dev`
- Test framework: pytest (confirmed via `dqs-orchestrator/pyproject.toml`)
- Test directory: `dqs-orchestrator/tests/`
- Baseline test count: 65 tests passing (66 - 1 placeholder replaced)

**Story context loaded:**
- 3 acceptance criteria
- Tasks 7, 8, 9 explicitly specify 15 new test functions
- Pure Python backend — no browser/E2E tests required

## Step 2: Generation Mode

Mode selected: **AI generation** (backend project, no browser recording needed)

## Step 3: Test Strategy

### Acceptance Criteria → Test Scenarios

| AC | Scenario | Level | Priority |
|----|----------|-------|----------|
| AC1 | `compose_summary_email` includes run_id in body | Unit | P0 |
| AC1 | `compose_summary_email` includes pass/fail counts | Unit | P0 |
| AC1 | `compose_summary_email` includes check-type failure grouping | Unit | P0 |
| AC1 | `compose_summary_email` includes rerun commands for failed datasets | Unit | P0 |
| AC1 | `compose_summary_email` includes dashboard link | Unit | P0 |
| AC1 | `compose_summary_email` subject: PASSED/FAILED(N) based on failed_datasets | Unit | P0 |
| AC1 | `query_run_summary` returns orchestration run data (run_id, parent_path, times, counts) | Unit | P0 |
| AC1 | `query_run_summary` returns failed dataset names list | Unit | P0 |
| AC1 | `query_run_summary` returns check-type failure counts dict | Unit | P0 |
| AC2 | `send_summary_email` calls `smtplib.SMTP.sendmail` with correct from/to | Unit | P0 |
| AC2 | `main()` calls `send_summary_email` after finalize loop | Integration | P0 |
| AC3 | `send_summary_email` does not re-raise `SMTPException` | Unit | P0 |
| AC3 | `send_summary_email` does not re-raise `OSError` | Unit | P0 |
| AC3 | `main()` email exception does not affect exit code | Integration | P0 |
| AC3 | `main()` skips email when smtp_host missing from config | Integration | P0 |

### TDD Red Phase Mechanism (Python)
- Tests import functions that do not exist yet → `ImportError` (TDD red)
- Tests call `compose_summary_email` with wrong old signature → `NotImplementedError` (TDD red)
- Tests mock `orchestrator.cli.query_run_summary` which is not imported in cli.py yet → `AttributeError` (TDD red)

## Step 4: Failing Tests Generated

### test_email.py — 9 tests (TDD RED)

All 9 tests fail with `ImportError` because:
- `RunSummary` not yet in `orchestrator.models` (Tasks 6 required first)
- `send_summary_email` not yet in `orchestrator.email` (Task 3 required)
- `compose_summary_email` new signature not yet implemented (Task 1 required)

| Test | AC | Failure Reason |
|------|----|----------------|
| `test_compose_summary_email_includes_run_id` | AC1 | `ImportError: RunSummary` |
| `test_compose_summary_email_includes_pass_fail_counts` | AC1 | `ImportError: RunSummary` |
| `test_compose_summary_email_includes_check_type_failures` | AC1 | `ImportError: RunSummary` |
| `test_compose_summary_email_includes_rerun_commands` | AC1 | `ImportError: RunSummary` |
| `test_compose_summary_email_includes_dashboard_link` | AC1 | `ImportError: RunSummary` |
| `test_compose_summary_email_subject_passes_status` | AC1 | `ImportError: RunSummary` |
| `test_send_summary_email_calls_smtp` | AC2 | `ImportError: send_summary_email` |
| `test_send_summary_email_non_fatal_on_smtp_exception` | AC3 | `ImportError: send_summary_email` |
| `test_send_summary_email_non_fatal_on_connection_refused` | AC3 | `ImportError: send_summary_email` |

### test_db.py additions — 3 tests (TDD RED)

All 3 tests fail with `ImportError: query_run_summary` because Task 2 not implemented.

| Test | AC | Failure Reason |
|------|----|----------------|
| `test_query_run_summary_returns_orchestration_run_data` | AC1 | `ImportError: query_run_summary` |
| `test_query_run_summary_returns_failed_datasets` | AC1 | `ImportError: query_run_summary` |
| `test_query_run_summary_returns_check_type_failure_counts` | AC1 | `ImportError: query_run_summary` |

### test_cli.py additions — 3 tests (TDD RED)

All 3 tests fail with `AttributeError` because cli.py does not import email functions yet.

| Test | AC | Failure Reason |
|------|----|----------------|
| `test_main_calls_send_summary_email_after_finalize` | AC1+AC2 | `AttributeError: query_run_summary` not in cli |
| `test_main_email_error_does_not_affect_exit_code` | AC3 | `AttributeError: query_run_summary` not in cli |
| `test_main_skips_email_when_no_smtp_config` | AC3 | `AttributeError: send_summary_email` not in cli |

## Step 4C: Aggregate

**Execution mode:** Sequential (backend-only project)

**TDD Red Phase Validation:**
- All 15 new tests FAIL because implementations do not exist yet
- 65 baseline tests PASS (no regressions)
- Tests assert EXPECTED behavior (not placeholder assertions)

**Summary:**
```
TDD RED PHASE: Failing Tests Generated

Total Tests: 15 (all failing — TDD red phase)
  - test_email.py:   9 tests (RED)
  - test_db.py:      3 tests (RED)
  - test_cli.py:     3 tests (RED)

Baseline Tests: 65 PASSING (no regressions)

All tests will FAIL until:
  Task 6: RunSummary added to models.py
  Task 1: compose_summary_email implemented in email.py
  Task 3: send_summary_email added to email.py
  Task 2: query_run_summary added to db.py
  Task 4: Email block wired in cli.py main()
```

## Step 5: Validation & Completion

**Checklist validation:**
- [x] Prerequisites satisfied (story ready-for-dev, pytest configured)
- [x] Test files created/updated correctly
- [x] Tests designed to fail before implementation (TDD red phase confirmed)
- [x] Acceptance criteria covered (all 3 ACs have test coverage)
- [x] No placeholder assertions (`assert True` / `expect(true).toBe(true)`)
- [x] Tests follow existing codebase patterns (`make_mock_conn`, `_write_minimal_config`)
- [x] No orphaned browsers or temp artifacts

**Completion Summary:**

Test files created/modified:
- `dqs-orchestrator/tests/test_email.py` — replaced 1 placeholder with 9 new tests
- `dqs-orchestrator/tests/test_db.py` — appended 3 new tests at end
- `dqs-orchestrator/tests/test_cli.py` — appended 3 new tests at end

Key risks / assumptions:
- CLI tests (`test_main_calls_send_summary_email_after_finalize` etc.) depend on functions being
  imported at **module level** in `cli.py` — the mock targets `orchestrator.cli.query_run_summary`
  etc. only work if the imports are at module level, not inside the function body.
- `compose_summary_email` must return `(subject, body)` tuple (per Task 4 update to Task 1 signature).

**Next recommended workflow:** `bmad-dev-story` → implement story 3-5 (Tasks 1-10)

## Green Phase Instructions

After implementing the feature (Tasks 1-10):

1. Run `uv run pytest -v` from `dqs-orchestrator/`
2. All 15 new tests should PASS
3. All 65 baseline tests must continue to PASS
4. Total passing: 81 tests (66 baseline + 15 new - note: 1 placeholder removed)
5. Commit passing tests
