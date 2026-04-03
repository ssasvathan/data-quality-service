---
stepsCompleted: ['step-01-preflight-and-context', 'step-02-generation-mode', 'step-03-test-strategy', 'step-04-generate-tests', 'step-04c-aggregate', 'step-05-validate-and-complete']
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-03'
story_id: '3-3-orchestration-run-tracking-error-capture'
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/3-3-orchestration-run-tracking-error-capture.md
  - dqs-orchestrator/src/orchestrator/db.py
  - dqs-orchestrator/src/orchestrator/runner.py
  - dqs-orchestrator/src/orchestrator/cli.py
  - dqs-orchestrator/src/orchestrator/models.py
  - dqs-orchestrator/tests/test_db.py (pre-existing)
  - dqs-orchestrator/tests/test_runner.py (pre-existing)
  - dqs-orchestrator/tests/test_cli.py (pre-existing)
  - _bmad/tea/config.yaml
---

# ATDD Checklist: Story 3-3 — Orchestration Run Tracking & Error Capture

## TDD Red Phase (Current Status)

**19 failing tests generated — all fail as expected (TDD red phase)**

| Test File | New Tests | Failure Reason |
|---|---|---|
| `dqs-orchestrator/tests/test_db.py` | 9 (replaced placeholder + 8 new) | `ImportError: cannot import name 'create_orchestration_run'` / `finalize_orchestration_run` from `orchestrator.db` |
| `dqs-orchestrator/tests/test_runner.py` | 4 | `TypeError: run_all_paths() got an unexpected keyword argument 'orchestration_run_ids'` |
| `dqs-orchestrator/tests/test_cli.py` | 6 | `AttributeError: module 'orchestrator.cli' does not have attribute 'create_orchestration_run'` |

**Pre-existing tests: 30 PASS (zero regressions)**

## Stack Detection

- Detected stack: `backend`
- Generation mode: AI Generation (no browser recording)
- Execution mode: Sequential (single agent)
- Framework: pytest + unittest.mock

## Acceptance Criteria Coverage

### AC1: Orchestration run created with start_time and run_status='running'

| Test | File | Priority | Status |
|---|---|---|---|
| `test_create_orchestration_run_returns_id` | test_db.py | P0 | RED - ImportError |
| `test_create_orchestration_run_inserts_running_status` | test_db.py | P0 | RED - ImportError |
| `test_create_orchestration_run_uses_expiry_sentinel` | test_db.py | P0 | RED - ImportError |
| `test_create_orchestration_run_includes_parent_path_and_start_time` | test_db.py | P1 | RED - ImportError |
| `test_get_connection_uses_database_url_env` | test_db.py | P1 | PASS (get_connection already exists) |
| `test_main_calls_create_orchestration_run_before_run_all_paths` | test_cli.py | P0 | RED - AttributeError |

### AC2: Orchestration run finalized with end_time, counts, and correct run_status

| Test | File | Priority | Status |
|---|---|---|---|
| `test_finalize_orchestration_run_sets_completed_on_zero_failures` | test_db.py | P0 | RED - ImportError |
| `test_finalize_orchestration_run_sets_completed_with_errors_on_failures` | test_db.py | P0 | RED - ImportError |
| `test_finalize_orchestration_run_includes_error_summary` | test_db.py | P1 | RED - ImportError |
| `test_finalize_orchestration_run_commits_transaction` | test_db.py | P1 | RED - ImportError |
| `test_finalize_orchestration_run_uses_correct_run_id` | test_db.py | P1 | RED - ImportError |
| `test_main_calls_finalize_orchestration_run_after_run_all_paths` | test_cli.py | P0 | RED - AttributeError |
| `test_main_finalizes_with_completed_with_errors_on_failure` | test_cli.py | P0 | RED - AttributeError |

### AC3: dq_run.orchestration_run_id linked via --orchestration-run-id CLI arg

| Test | File | Priority | Status |
|---|---|---|---|
| `test_run_spark_job_appends_orchestration_run_id_to_command` | test_runner.py | P0 | RED - TypeError |
| `test_run_spark_job_omits_orchestration_run_id_when_none` | test_runner.py | P0 | RED - TypeError |
| `test_run_all_paths_threads_orchestration_run_ids_to_each_path` | test_runner.py | P0 | RED - TypeError |
| `test_run_all_paths_passes_none_run_id_for_path_not_in_dict` | test_runner.py | P1 | RED - TypeError |
| `test_main_passes_orchestration_run_ids_to_run_all_paths` | test_cli.py | P0 | RED - AttributeError |

### Safety: DB errors are non-fatal

| Test | File | Priority | Status |
|---|---|---|---|
| `test_main_continues_spark_submit_when_create_orchestration_run_raises` | test_cli.py | P0 | RED - AttributeError |
| `test_main_does_not_exit_1_when_finalize_orchestration_run_raises` | test_cli.py | P0 | RED - AttributeError |

## Generated Test Files

| File | Action | Tests Added |
|---|---|---|
| `dqs-orchestrator/tests/test_db.py` | REPLACED placeholder, added 9 real tests | 9 |
| `dqs-orchestrator/tests/test_runner.py` | EXTENDED with 4 Story 3-3 AC3 tests | 4 |
| `dqs-orchestrator/tests/test_cli.py` | EXTENDED with 6 Story 3-3 integration tests | 6 |

## Test Run Summary

```
FAILED (19):
  test_db.py        - 9 tests (ImportError: create_orchestration_run / finalize_orchestration_run not in db.py)
  test_runner.py    - 4 tests (TypeError: orchestration_run_ids param not on run_all_paths / run_spark_job)
  test_cli.py       - 6 tests (AttributeError: create_orchestration_run not in orchestrator.cli module)

PASSED (30):
  test_cli.py       - 19 pre-existing Story 3-1 tests (no regression)
  test_runner.py    - 9 pre-existing Story 3-2 tests (no regression)
  test_db.py        - 1 test (test_get_connection_uses_database_url_env — get_connection already exists)
  test_email.py     - 1 placeholder test
```

## Next Steps (TDD Green Phase)

After implementing the feature per the story tasks:

1. **Task 1** (db.py): Add `EXPIRY_SENTINEL`, `create_orchestration_run()`, `finalize_orchestration_run()` → fixes `test_db.py` (9 tests)
2. **Task 2** (runner.py): Add `orchestration_run_id` to `run_spark_job()` and `orchestration_run_ids` to `run_all_paths()` → fixes `test_runner.py` (4 tests)
3. **Task 3** (cli.py): Import and wire DB functions around `run_all_paths()` → fixes `test_cli.py` (6 tests)

Then run: `cd dqs-orchestrator && uv run pytest -v`

Expected: 49 tests passing (30 existing + 19 new), zero failures.

## Quality Gates

- [x] All new tests assert EXPECTED behavior (not placeholder assertions)
- [x] All new tests fail for the RIGHT reason (missing implementation, not test bugs)
- [x] Zero regressions in pre-existing 30 tests
- [x] Mock targets use `orchestrator.cli.*` prefix per Story 3.2 pattern
- [x] DB error safety tests use try/except pattern per story spec
- [x] EXPIRY_SENTINEL test verifies constant usage (not hardcoded string)
- [x] Python type hints in test helpers (return type annotations)

## Assumptions and Notes

- `test_get_connection_uses_database_url_env` is in green already because `get_connection()` exists — this is correct, it tests existing behavior
- The `test_main_finalizes_with_completed_with_errors_on_failure` test uses `pytest.raises(SystemExit)` because `main()` calls `sys.exit(1)` when spark failures occur — the finalize assertions are checked before the exit
- DB safety tests (`test_main_continues_spark_submit_*` and `test_main_does_not_exit_1_*`) currently fail with AttributeError rather than the safety assertion — once implementation exists, these tests will verify the non-fatal behavior
- Java tests (BatchWriterTest.java) are out of scope for this ATDD run — story notes that existing `writer.write()` calls will need `null` as 3rd arg after signature change
