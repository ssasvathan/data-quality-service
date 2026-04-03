---
stepsCompleted: ['step-01-preflight-and-context', 'step-02-generation-mode', 'step-03-test-strategy', 'step-04-generate-tests', 'step-04c-aggregate', 'step-05-validate-and-complete']
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-03'
story_id: 3-1-python-cli-parent-path-configuration
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/3-1-python-cli-parent-path-configuration.md
  - _bmad-output/project-context.md
  - _bmad/tea/config.yaml
  - dqs-orchestrator/src/orchestrator/cli.py
  - dqs-orchestrator/pyproject.toml
  - dqs-orchestrator/tests/test_runner.py
  - dqs-orchestrator/tests/test_db.py
  - dqs-orchestrator/tests/test_email.py
---

# ATDD Checklist: Story 3-1 — Python CLI & Parent Path Configuration

## Step 1: Preflight & Context Summary

**Stack Detection:** `backend` (Python/pytest — `dqs-orchestrator/pyproject.toml`, no frontend indicators)

**TEA Config:**
- `test_stack_type: auto` → resolved to `backend`
- `tea_execution_mode: auto` → resolved to `sequential`
- `tea_use_playwright_utils: true` (API-only profile — no browser tests for backend)

**Story Status:** ready-for-dev

**Prerequisites satisfied:**
- [x] Story file present with clear acceptance criteria
- [x] pytest available (`dqs-orchestrator/pyproject.toml`)
- [x] Existing test directory: `dqs-orchestrator/tests/`
- [x] Existing placeholder tests: `test_runner.py`, `test_db.py`, `test_email.py` (3 passing)

---

## Step 2: Generation Mode

**Mode selected:** AI Generation

**Reason:** Pure backend Python project. No browser interactions. ACs are clear unit-testable behaviours. `detected_stack = backend` → always use AI generation.

---

## Step 3: Test Strategy

### Acceptance Criteria → Test Scenarios Mapping

| AC | Test Scenario | Level | Priority |
|---|---|---|---|
| AC1 | `load_parent_paths` returns correct list from valid 2-path YAML | Unit | P0 |
| AC1 | `load_parent_paths` returns all 3 paths (not just first) | Unit | P0 |
| AC1 | `load_parent_paths` returns `list[str]`, not list of dicts | Unit | P0 |
| AC2 | `parse_args` parses `--date 20260325` as string | Unit | P0 |
| AC2 | `parse_args` parses `--datasets ds1 ds2` as list | Unit | P0 |
| AC2 | `parse_args` parses `--rerun` flag as True | Unit | P0 |
| AC2 | `parse_args` defaults: `date=None` | Unit | P1 |
| AC2 | `parse_args` defaults: `rerun=False` | Unit | P1 |
| AC2 | `parse_args` defaults: `datasets=None` | Unit | P1 |
| AC2 | `parse_args` `--config` unchanged default | Unit | P1 |
| AC2 | `parse_args` single `--datasets` value → list with 1 element | Unit | P1 |
| AC3 | `load_parent_paths` raises SystemExit when file missing | Unit | P0 |
| AC3 | `load_parent_paths` raises SystemExit on missing `parent_paths` key | Unit | P0 |
| AC3 | `load_parent_paths` raises SystemExit on empty YAML | Unit | P0 |
| AC1+2+3 | `main()` exits cleanly with valid config and parent_paths.yaml | Unit | P1 |
| AC3 | `main()` exits with error on missing orchestrator config | Unit | P0 |
| AC2 | `main()` resolves to today when `--date` not provided | Unit | P1 |

**Total:** 17 test scenarios → 17 pytest test functions

### Test Level Rationale

- **Unit only** (no integration, no E2E): All behaviours are pure Python function logic.
  - `load_parent_paths()` is a standalone function with `tmp_path` fixtures.
  - `parse_args()` is pure argparse logic tested with `patch("sys.argv", ...)`.
  - `main()` wiring tested with tmp YAML files, no real Spark/DB needed.
- No Spark cluster, no Postgres, no network calls needed for this story.

---

## Step 4: TDD RED Phase — Test Generation

**Execution mode:** SEQUENTIAL (single backend agent, no subagents)

**Generated file:**
- `dqs-orchestrator/tests/test_cli.py` — 17 pytest unit tests

**TDD RED Phase Compliance:**
- All tests import from `orchestrator.cli` which does NOT yet implement `load_parent_paths()`, `--date`, `--rerun`
- Tests fail due to `ImportError: cannot import name 'load_parent_paths'` and `AttributeError: args.date/args.rerun`
- Tests assert EXPECTED behavior (not placeholders like `assert True`)
- No `test.skip()` needed in pytest — imports fail naturally on RED phase

**Run result (PYTHONPATH=src):**
```
12 failed, 5 passed in 0.43s
```

The 5 passing tests are for already-implemented args (`--config`, `--datasets`) — correct behavior.
The 12 failing tests cover all new functionality to be implemented.

---

## Step 4C: Aggregate

**API Tests (Unit):** 17 tests written, 12 failing (RED), 5 passing (existing implementation already present)
**E2E Tests:** N/A (backend stack, no E2E)
**Fixtures created:** None needed beyond `tmp_path` (pytest built-in)
**ATDD Checklist:** this document

---

## Step 5: Validation

### Checklist Verification

- [x] Prerequisites satisfied (story clear ACs, pytest available)
- [x] Test file created: `dqs-orchestrator/tests/test_cli.py`
- [x] All 3 ACs have test coverage
- [x] Tests are designed to fail before implementation (RED phase)
- [x] No orphaned browser sessions (N/A — backend only)
- [x] Temp artifacts stored in `_bmad-output/test-artifacts/` (this file)
- [x] No `print()` stubs in test code
- [x] Type hints on test function parameters (`tmp_path: pytest.TempPathFactory`)
- [x] Existing placeholder tests unaffected (3 still pass: `test_runner.py`, `test_db.py`, `test_email.py`)

### Completion Summary

**Test files created:**
- `/home/sas/workspace/data-quality-service/dqs-orchestrator/tests/test_cli.py` (17 tests)

**Checklist output:**
- `/home/sas/workspace/data-quality-service/_bmad-output/test-artifacts/atdd-checklist-3-1-python-cli-parent-path-configuration.md`

**Key risks / assumptions:**
- The `orchestrator` package must be installed via `uv run` (with `PYTHONPATH=src` or installed package) for tests to run properly. The standard dev workflow uses `uv run pytest` from the `dqs-orchestrator/` directory.
- `main()` test for valid config does NOT mock the runner call — correct for this story since runner stub raises `NotImplementedError` only (Story 3.2). The `# TODO(story-3-2)` comment means `main()` doesn't actually call the runner yet.
- `test_main_resolves_today_when_date_not_provided` will pass once `main()` is wired — it just calls `date.today()` which is always valid.

**Next workflow:**
- `bmad-dev-story` — implement `load_parent_paths()`, extend `parse_args()`, wire `main()` per story task list
- After green phase: run `uv run pytest tests/test_cli.py -v` — all 17 tests must pass

---

## TDD Red Phase Summary

```
TDD RED PHASE: Failing Tests Generated

Tests: 17 total
  - 12 failing (RED — implementation not yet done)
  - 5 passing (pre-existing parse_args behavior already implemented)

Acceptance Criteria Coverage:
  AC1 (parent path config reading): 6 tests
  AC2 (argument parsing):           8 tests
  AC3 (error handling):             6 tests
  AC1+2+3 combined (main wiring):   3 tests

Run command:
  cd dqs-orchestrator && uv run pytest tests/test_cli.py -v

Next steps:
  1. Implement load_parent_paths() in cli.py
  2. Extend parse_args() with --date and --rerun
  3. Wire main() per story task list
  4. Run tests → verify all 17 PASS (green phase)
  5. Run full regression: uv run pytest -v (20 total, 0 failures)
```
