---
stepsCompleted: ['step-01-preflight-and-context', 'step-02-generation-mode', 'step-03-test-strategy', 'step-04-generate-tests', 'step-04c-aggregate', 'step-05-validate-and-complete']
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-03'
story_id: 3-2-spark-submit-runner-with-failure-isolation
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/3-2-spark-submit-runner-with-failure-isolation.md
  - _bmad-output/project-context.md
  - _bmad/tea/config.yaml
  - dqs-orchestrator/src/orchestrator/runner.py
  - dqs-orchestrator/src/orchestrator/models.py
  - dqs-orchestrator/src/orchestrator/cli.py
  - dqs-orchestrator/pyproject.toml
  - dqs-orchestrator/tests/test_runner.py
  - dqs-orchestrator/tests/test_cli.py
---

# ATDD Checklist: Story 3-2 — Spark-Submit Runner with Failure Isolation

## Step 1: Preflight & Context Summary

**Stack Detection:** `backend` (Python/pytest — `dqs-orchestrator/pyproject.toml`, no frontend indicators)

**TEA Config:**
- `test_stack_type: auto` → resolved to `backend`
- `tea_execution_mode: auto` → resolved to `sequential`
- `tea_use_playwright_utils: true` (API-only profile — no browser tests for backend)

**Story Status:** ready-for-dev

**Prerequisites satisfied:**
- [x] Story file present with clear acceptance criteria (3 ACs)
- [x] pytest available (`dqs-orchestrator/pyproject.toml`)
- [x] Existing test directory: `dqs-orchestrator/tests/`
- [x] `JobResult` dataclass exists in `models.py` (authoritative, do not modify)
- [x] `runner.py` placeholder with `NotImplementedError` — test target identified
- [x] Existing test baseline: 21 tests passing (17 `test_cli.py` + 2 placeholders + 1 `test_email.py` + 1 `test_db.py`)

**Current runner.py state:**
- `run_spark_job(parent_path, datasets)` — wrong signature, raises `NotImplementedError`
- `run_all_paths()` — does not exist

---

## Step 2: Generation Mode

**Mode selected:** AI Generation

**Reason:** Pure backend Python project. No browser interactions. ACs are clear subprocess-mocking unit test behaviours. `detected_stack = backend` → always use AI generation.

---

## Step 3: Test Strategy

### Acceptance Criteria → Test Scenarios Mapping

| AC | Test Scenario | Level | Priority |
|---|---|---|---|
| AC3 | `run_spark_job` returns `JobResult(success=True)` on exit code 0 | Unit | P0 |
| AC2 | `run_spark_job` returns `JobResult(success=False, error_message=...)` on non-zero exit | Unit | P0 |
| AC1 | `run_spark_job` builds correct command list (`--parent-path`, `--date`, spark flags, app_jar) | Unit | P0 |
| AC1 | `run_spark_job` passes `--datasets` args when datasets parameter is provided | Unit | P1 |
| AC1+AC2 | `run_all_paths` processes all 3 paths even when path 2 fails (core failure isolation) | Unit | P0 |
| AC1+AC2+AC3 | `run_all_paths` returns correct success/failure in results list | Unit | P0 |
| AC2 | `run_all_paths` continues processing after unexpected Python exception from one path | Unit | P0 |

**Total:** 7 test scenarios → 7 pytest test functions

### Test Level Rationale

- **Unit only** (no integration, no E2E): All behaviour is pure Python function logic with mocked subprocess.
  - `subprocess.run` mocked via `unittest.mock.patch` — no real Spark cluster needed.
  - `run_all_paths` exception isolation tested by patching `run_spark_job` itself.
- No Spark cluster, no Postgres, no network calls needed for this story.

---

## Step 4: TDD RED Phase — Test Generation

**Execution mode:** SEQUENTIAL (single backend agent, no subagents)

**Generated file:**
- `dqs-orchestrator/tests/test_runner.py` — 7 pytest unit tests

**TDD RED Phase Compliance:**
- All tests fail because:
  - `run_all_paths` does not exist in `orchestrator.runner` → `ImportError` at collection
  - `run_spark_job` has wrong signature (missing `partition_date`, `spark_config` params) → would fail even if ImportError were resolved
- Tests assert EXPECTED behavior — not placeholder `assert True`
- No `pytest.skip()` needed — imports fail naturally on RED phase

**Run result (RED phase):**
```
ERROR tests/test_runner.py
ImportError: cannot import name 'run_all_paths' from 'orchestrator.runner'
1 error during collection
```

**Regression check (21 existing tests must still pass):**
```
21 passed in 0.13s  (test_cli.py: 19, test_db.py: 1, test_email.py: 1)
```
Zero regressions confirmed.

---

## Step 4C: Aggregate

**Unit Tests:** 7 tests written — ALL FAILING (RED phase confirmed)
**E2E Tests:** N/A (backend stack, no E2E)
**Fixtures created:** None needed beyond `tmp_path` (not required) and `unittest.mock.patch`
**ATDD Checklist:** this document

---

## Step 5: Validation

### Checklist Verification

- [x] Prerequisites satisfied (story clear ACs, pytest available, `JobResult` dataclass exists)
- [x] Test file created: `dqs-orchestrator/tests/test_runner.py`
- [x] All 3 ACs have test coverage
- [x] Tests are designed to fail before implementation (RED phase — ImportError confirmed)
- [x] No orphaned browser sessions (N/A — backend only)
- [x] Temp artifacts stored in `_bmad-output/test-artifacts/` (this file)
- [x] No `print()` stubs in test code
- [x] Type hints on all test function signatures (`-> None`)
- [x] Existing 21 tests unaffected (21 still pass: `test_cli.py` + `test_db.py` + `test_email.py`)
- [x] Test uses `subprocess.run` patch at module level — matches `runner.py`'s import pattern
- [x] `run_all_paths` exception test patches `orchestrator.runner.run_spark_job` (correct path)

### Completion Summary

**Test files created:**
- `/home/sas/workspace/data-quality-service/dqs-orchestrator/tests/test_runner.py` (7 tests, replacing placeholder)

**Checklist output:**
- `/home/sas/workspace/data-quality-service/_bmad-output/test-artifacts/atdd-checklist-3-2-spark-submit-runner-with-failure-isolation.md`

**Key risks / assumptions:**
- `subprocess.run` is patched at top-level (`patch("subprocess.run", ...)`) — this works because `runner.py` calls `subprocess.run()` directly (not `from subprocess import run`).
- `run_all_paths` exception isolation test patches `orchestrator.runner.run_spark_job` — this is the correct patch target since `run_all_paths` calls the local module function.
- `test_main_exits_cleanly_with_valid_config_and_parent_paths` in `test_cli.py` will need updating by the dev agent to mock `run_all_paths` once it's wired into `main()` — the story Dev Notes warn about this.

**Next workflow:**
- `bmad-dev-story` — implement `run_spark_job()` and `run_all_paths()` in `runner.py`, wire `run_all_paths()` in `cli.py`, add `app_jar` to `orchestrator.yaml`
- After green phase: run `uv run pytest tests/test_runner.py -v` — all 7 tests must pass
- Full regression: `uv run pytest -v` — ~27 tests total, 0 failures

---

## TDD Red Phase Summary

```
TDD RED PHASE: Failing Tests Generated

Tests: 7 total — ALL FAILING (ImportError at collection)

Failure reason:
  ImportError: cannot import name 'run_all_paths' from 'orchestrator.runner'

Acceptance Criteria Coverage:
  AC1 (spark-submit invocation with correct args): 3 tests
  AC2 (failure isolation — one path failure doesn't halt others): 4 tests
  AC3 (exit code 0 → success): 2 tests
  (note: some tests cover multiple ACs)

Existing test baseline: 21 passing (UNAFFECTED)

Run command:
  cd dqs-orchestrator && uv run pytest tests/test_runner.py -v

Next steps:
  1. Implement run_spark_job() in runner.py (full signature + subprocess logic)
  2. Implement run_all_paths() in runner.py (per-path isolation loop)
  3. Wire run_all_paths() into cli.py main() (replace TODO comment)
  4. Add app_jar key to config/orchestrator.yaml
  5. Run tests → verify all 7 PASS (green phase)
  6. Run full regression: uv run pytest -v (~27 total, 0 failures)
```
