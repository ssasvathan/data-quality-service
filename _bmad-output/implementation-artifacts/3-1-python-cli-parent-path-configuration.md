# Story 3.1: Python CLI & Parent Path Configuration

Status: done

## Story

As a **platform operator**,
I want a Python CLI that reads parent paths from a YAML config file and accepts runtime parameters (date, rerun flags, dataset filter),
So that I can trigger orchestration runs with flexible configuration.

## Acceptance Criteria

1. **Given** a `parent_paths.yaml` config file with 2 parent paths configured **When** I run `python -m orchestrator.cli` **Then** it reads the config and identifies the parent paths to process.

2. **Given** CLI arguments `--date 20260325 --datasets ue90-omni-transactions` **When** the CLI parses arguments **Then** it passes the date and dataset filter to the runner.

3. **Given** a missing or malformed config file **When** the CLI starts **Then** it exits with a clear error message indicating the config problem.

## Tasks / Subtasks

- [x] Task 1: Implement `load_parent_paths(config_path: str) -> list[str]` in `cli.py` (AC: #1, #3)
  - [x] Use `yaml.safe_load()` to read `parent_paths.yaml`
  - [x] Extract the `parent_paths[*].path` list from the YAML structure
  - [x] Raise `SystemExit` with a clear error message if file is missing (`FileNotFoundError`) or malformed (missing `parent_paths` key, `yaml.YAMLError`)
  - [x] Return a plain `list[str]` of path strings

- [x] Task 2: Extend `parse_args()` in `cli.py` to include `--date` and `--rerun` arguments (AC: #2)
  - [x] Add `--date` argument: `type=str`, format `yyyyMMdd` (e.g., `20260325`); optional, defaults to `None` (caller resolves to today)
  - [x] Add `--rerun` flag: `action="store_true"`, default `False`
  - [x] Keep existing `--config` (path to `orchestrator.yaml`) and `--datasets` (nargs="*") arguments unchanged
  - [x] `--config` should default to `"config/orchestrator.yaml"` (unchanged from placeholder)

- [x] Task 3: Implement `main()` in `cli.py` — wiring config load to runner call (AC: #1, #2, #3)
  - [x] Parse args via `parse_args()`
  - [x] Load orchestrator config from `args.config` using `yaml.safe_load()`; extract `spark` section for runner
  - [x] Determine `parent_paths_config_path`: read from orchestrator config key `parent_paths_config` or default to `"config/parent_paths.yaml"`
  - [x] Call `load_parent_paths(parent_paths_config_path)` to get list of parent path strings
  - [x] Resolve partition date: if `args.date` is provided, parse via `datetime.strptime(args.date, "%Y%m%d").date()`; else default to `date.today()`
  - [x] Log `INFO` with run parameters: date, number of parent paths loaded, datasets filter, rerun flag
  - [x] For each parent path, log the paths at `DEBUG` level
  - [x] Replace the existing `print(...)` stub — `main()` must be real wiring, not a placeholder

- [x] Task 4: Write `tests/test_cli.py` with pytest unit tests (AC: #1, #2, #3)
  - [x] Test: `load_parent_paths` returns correct list from valid YAML file (use `tmp_path` fixture)
  - [x] Test: `load_parent_paths` raises `SystemExit` when file is missing
  - [x] Test: `load_parent_paths` raises `SystemExit` when YAML is malformed (missing `parent_paths` key)
  - [x] Test: `parse_args` parses `--date 20260325` correctly
  - [x] Test: `parse_args` parses `--datasets ds1 ds2` as a list
  - [x] Test: `parse_args` parses `--rerun` flag as `True`
  - [x] Test: `parse_args` defaults: `date=None`, `rerun=False`, `datasets=None`
  - [x] Test: `main()` exits cleanly with a valid config and `parent_paths.yaml` (mock runner call — don't invoke spark-submit in unit tests)

## Dev Notes

### Existing Code — What to Extend, Not Replace

The `dqs-orchestrator` component already has a working placeholder structure. This story **replaces the placeholder implementations** with real code:

| File | Current State | This Story's Action |
|---|---|---|
| `dqs-orchestrator/src/orchestrator/cli.py` | Placeholder `main()` with `print()` stub; `parse_args()` with `--config` + `--datasets` only | EXTEND: add `--date`, `--rerun`; implement `load_parent_paths()`; wire `main()` |
| `dqs-orchestrator/src/orchestrator/runner.py` | Placeholder `run_spark_job()` that raises `NotImplementedError` | DO NOT TOUCH — Story 3.2 implements this |
| `dqs-orchestrator/src/orchestrator/db.py` | Placeholder `get_connection()` using `DATABASE_URL` env var | DO NOT TOUCH — Story 3.3 expands this |
| `dqs-orchestrator/src/orchestrator/email.py` | Placeholder `compose_summary_email()` raising `NotImplementedError` | DO NOT TOUCH — Story 3.5 implements this |
| `dqs-orchestrator/src/orchestrator/models.py` | `JobResult` dataclass exists | DO NOT TOUCH — already correct for this story |
| `dqs-orchestrator/tests/test_runner.py` | `test_placeholder()` only | DO NOT TOUCH in this story |
| `dqs-orchestrator/tests/test_db.py` | `test_placeholder()` only | DO NOT TOUCH in this story |

**Do NOT** replace the existing placeholder tests — add `tests/test_cli.py` as a new file.

### Config File Structure — Existing YAML Files

`config/parent_paths.yaml` (already exists at `dqs-orchestrator/config/parent_paths.yaml`):

```yaml
# Parent paths for DQ job execution
# One spark-submit per entry; failed paths don't halt others

parent_paths:
  - path: /data/finance/loans
    description: "Finance loan dataset parent path"
  - path: /data/finance/deposits
    description: "Finance deposits dataset parent path"
  - path: /data/risk/credit
    description: "Risk credit dataset parent path"
```

`config/orchestrator.yaml` (already exists at `dqs-orchestrator/config/orchestrator.yaml`):

```yaml
database:
  url: postgresql://postgres:localdev@localhost:5432/postgres

spark:
  submit_path: spark-submit
  master: yarn
  deploy_mode: cluster
  driver_memory: 2g
  executor_memory: 4g

email:
  smtp_host: localhost
  smtp_port: 25
  from_address: dqs-alerts@example.com
  to_addresses:
    - data-engineering@example.com
```

`load_parent_paths()` reads ONLY `parent_paths.yaml` (not `orchestrator.yaml`). `main()` reads both.

### Exact `cli.py` Design (Authoritative)

The final `cli.py` must look structurally like this:

```python
"""CLI entry point for dqs-orchestrator."""
import argparse
import logging
import sys
from datetime import date, datetime
from typing import Optional

import yaml

logger = logging.getLogger(__name__)


def load_parent_paths(config_path: str) -> list[str]:
    """Load parent paths from YAML config file.
    
    Returns list of path strings.
    Exits with clear error if file is missing or malformed.
    """
    try:
        with open(config_path) as f:
            config = yaml.safe_load(f)
    except FileNotFoundError:
        sys.exit(f"ERROR: Parent paths config not found: {config_path}")
    except yaml.YAMLError as e:
        sys.exit(f"ERROR: Failed to parse parent paths config {config_path}: {e}")
    
    if not config or "parent_paths" not in config:
        sys.exit(f"ERROR: 'parent_paths' key missing in config: {config_path}")
    
    return [entry["path"] for entry in config["parent_paths"]]


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments."""
    parser = argparse.ArgumentParser(description="DQS Orchestrator — runs Spark DQ jobs")
    parser.add_argument("--config", default="config/orchestrator.yaml", help="Path to orchestrator config")
    parser.add_argument("--date", default=None, help="Partition date in yyyyMMdd format (default: today)")
    parser.add_argument("--datasets", nargs="*", help="Optional dataset filter for rerun")
    parser.add_argument("--rerun", action="store_true", default=False, help="Rerun mode: expire previous results")
    return parser.parse_args()


def main() -> None:
    """Entry point — reads config, loads parent paths, triggers spark-submit per path."""
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    args = parse_args()
    
    # Load orchestrator config
    try:
        with open(args.config) as f:
            orchestrator_config = yaml.safe_load(f)
    except FileNotFoundError:
        sys.exit(f"ERROR: Orchestrator config not found: {args.config}")
    except yaml.YAMLError as e:
        sys.exit(f"ERROR: Failed to parse orchestrator config: {e}")
    
    # Resolve parent paths config path
    parent_paths_config = orchestrator_config.get("parent_paths_config", "config/parent_paths.yaml")
    parent_paths = load_parent_paths(parent_paths_config)
    
    # Resolve partition date
    if args.date is not None:
        try:
            partition_date = datetime.strptime(args.date, "%Y%m%d").date()
        except ValueError:
            sys.exit(f"ERROR: Invalid date format '{args.date}' — expected yyyyMMdd")
    else:
        partition_date = date.today()
    
    logger.info(
        "DQS Orchestrator starting: date=%s, parent_paths=%d, datasets=%s, rerun=%s",
        partition_date, len(parent_paths), args.datasets, args.rerun,
    )
    for path in parent_paths:
        logger.debug("Parent path: %s", path)
    
    # TODO(story-3-2): invoke runner.run_spark_job() for each parent path


if __name__ == "__main__":
    main()
```

**Critical design notes:**
- `load_parent_paths()` is a standalone function (not method) — easy to unit test with `tmp_path`
- `sys.exit()` with a string produces a non-zero exit code AND prints to stderr — correct for CLI error handling
- `logging.basicConfig` in `main()`, not at module level — avoids polluting library imports
- `parent_paths_config` key in `orchestrator.yaml` is optional — default to `"config/parent_paths.yaml"` if not set
- The `# TODO(story-3-2)` comment is the handoff point for the next story

### File Structure Requirements

```
dqs-orchestrator/
  src/orchestrator/
    cli.py          ← MODIFY (replace placeholder; add load_parent_paths, extend parse_args, wire main)
  tests/
    test_cli.py     ← NEW
```

Do NOT create new top-level directories. Do NOT modify `runner.py`, `db.py`, `email.py`, `models.py`.

### Python Conventions (dqs-orchestrator)

Per `project-context.md`:
- Type hints on all function parameters and return types — no bare `def f(x):` without annotations
- `snake_case` for all functions, variables, module names
- `UPPER_SNAKE` for module-level constants (none needed in this story)
- Use `Optional[str]` from `typing` if targeting Python 3.9 compatibility, or `str | None` if Python 3.10+
- The `pyproject.toml` requires `>=3.12` — use `str | None` union syntax is fine
- Environment variable pattern: `os.getenv("KEY", "default")` — but this story does NOT need DB config; that is story 3.3

### Dependencies Already Available

`pyproject.toml` already declares:
```toml
dependencies = [
    "psycopg2-binary>=2.9.11",
    "pyyaml>=6.0.3",
]
```

`yaml` (pyyaml) is already installed. `argparse`, `sys`, `datetime`, `logging` are stdlib. **Do NOT add new dependencies.**

### Testing Approach — Mock the Runner

In `test_cli.py`, `main()` has a TODO stub for the runner — it does not call `runner.run_spark_job()` yet (that is Story 3.2). So testing `main()` end-to-end only needs to verify:
1. Config files are read correctly
2. Parent paths are loaded
3. Date is parsed
4. No crash on valid inputs

Use `tmp_path` (pytest fixture) to create temporary YAML files in tests — do NOT depend on the real `config/` files from the repo.

Example test pattern for `load_parent_paths`:

```python
import pytest
from orchestrator.cli import load_parent_paths

def test_load_parent_paths_returns_paths(tmp_path):
    config_file = tmp_path / "parent_paths.yaml"
    config_file.write_text(
        "parent_paths:\n  - path: /data/finance/loans\n  - path: /data/risk/credit\n"
    )
    result = load_parent_paths(str(config_file))
    assert result == ["/data/finance/loans", "/data/risk/credit"]

def test_load_parent_paths_exits_on_missing_file(tmp_path):
    with pytest.raises(SystemExit):
        load_parent_paths(str(tmp_path / "nonexistent.yaml"))

def test_load_parent_paths_exits_on_missing_key(tmp_path):
    config_file = tmp_path / "bad.yaml"
    config_file.write_text("other_key: value\n")
    with pytest.raises(SystemExit):
        load_parent_paths(str(config_file))
```

### Test Run Commands

```bash
# Run this story's tests only
cd /home/sas/workspace/data-quality-service/dqs-orchestrator
uv run pytest tests/test_cli.py -v

# Full regression (must pass before marking done — includes existing placeholder tests)
cd /home/sas/workspace/data-quality-service/dqs-orchestrator
uv run pytest -v
```

Current baseline: 3 placeholder tests pass (1 each in `test_runner.py`, `test_db.py`, `test_email.py`). Zero regressions after this story is required.

### Logging Pattern

Per `project-context.md`, Python components use structured console logging. Use the module-level `logger = logging.getLogger(__name__)` pattern. Log levels:
- `ERROR` — config file missing/malformed → but this story uses `sys.exit()` for fatal errors, not `logger.error()`
- `INFO` — run starting with parameters (date, path count, datasets, rerun)
- `DEBUG` — each individual parent path name

**Do NOT use `print()` in production code** — the existing placeholder `print(...)` in `main()` must be replaced with proper logging.

### Architecture Constraints

- `dqs-orchestrator` is **outside** docker-compose — it runs on a scheduling server. No containerization.
- `dqs-orchestrator` communicates with Postgres via `psycopg2` (not SQLAlchemy). DB config in story 3.3.
- This story does NOT write to `dq_orchestration_run` — that is Story 3.3.
- The orchestrator's `config/` directory is local to the component — per architecture, no shared config files across components.
- `parent_paths.yaml` is **separate** from `orchestrator.yaml` — two distinct config files with different responsibilities.
- `--datasets` filter is passed through to spark-submit in Story 3.2 and to rerun logic in Story 3.4. This story just parses and holds the value.

### Cross-Story Dependencies

| Story | Dependency from 3.1 |
|---|---|
| 3.2 (Spark-Submit Runner) | `cli.main()` has `# TODO(story-3-2)` comment as the handoff point; `parse_args()` returns `args.date`, `args.datasets`, `args.rerun` |
| 3.3 (Run Tracking) | `cli.main()` can be extended to create `dq_orchestration_run` records; `args.date` available |
| 3.4 (Rerun Management) | `args.rerun` flag and `args.datasets` filter available from `parse_args()` |

### DqsJob CLI Pattern Reference

`DqsJob.java` (Spark job entry point) accepts `--parent-path` and `--date 20260325`. The orchestrator CLI uses the same `--date yyyyMMdd` format for consistency. The orchestrator passes `--date` through to each spark-submit invocation in Story 3.2.

### Previous Story Intelligence

From Story 2.10 completion notes and Epic 2 retrospective:
- Epic 2 work was entirely in `dqs-spark/` (Java). This is the **first real Python story** for `dqs-orchestrator`.
- Python uses `uv` for dependency management — **never use pip** directly.
- Testing pattern changes: from JUnit/H2 to pytest/tmp_path. No real Postgres needed for this story (no DB operations).
- Per Epic 2 retro action item: `_bmad-output/dev-checklist.md` was planned but **NOT yet created** (verify before dev-story runs). Reference the checklist if it exists.
- The `models.py` `JobResult` dataclass already exists and is the right shape for Story 3.2's runner — do not duplicate it.

### Epic 2 Retrospective Lessons Applied to This Story

1. **Constructor validation rule**: `load_parent_paths` validates its input state (missing key) — matches the constructor validation pattern from Java stories.
2. **No stale TDD comments**: When writing `test_cli.py`, do not leave "RED PHASE" or "TODO implement" comments in tests.
3. **Type hints on all code**: All functions must have parameter and return type annotations.
4. **Per-item failure isolation is Story 3.2's job** — this story only loads the list. Do not add per-path try/catch here.

### Git Intelligence

Established commit pattern (Epics 1 and 2):
```
bmad(epic-3/3-1-python-cli-parent-path-configuration): complete workflow and quality gates
```

Recent commits live entirely in `dqs-spark/` (Java). This story begins the `dqs-orchestrator/` Python work.

### References

- Story ACs: `_bmad-output/planning-artifacts/epics/epic-3-orchestration-run-management-notifications.md` (Story 3.1)
- Existing orchestrator files: `dqs-orchestrator/src/orchestrator/cli.py`, `runner.py`, `db.py`, `email.py`, `models.py`
- Config files: `dqs-orchestrator/config/parent_paths.yaml`, `dqs-orchestrator/config/orchestrator.yaml`
- Architecture (orchestrator design, config ownership): `_bmad-output/planning-artifacts/architecture.md` (§Orchestrator architecture, §Structure Patterns)
- Project guardrails: `_bmad-output/project-context.md` (Python rules, Orchestrator rules, logging levels)
- DqsJob CLI reference (--date format): `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java`
- Dependency declaration: `dqs-orchestrator/pyproject.toml`
- Epic 2 retrospective (Python testing approach change): `_bmad-output/implementation-artifacts/epic-2-retro-2026-04-03.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_none_

### Completion Notes List

- Implemented `load_parent_paths(config_path: str) -> list[str]` with `yaml.safe_load()`, `FileNotFoundError` and `yaml.YAMLError` handling, and missing `parent_paths` key guard — all using `sys.exit()` for CLI-style error reporting.
- Extended `parse_args()` with `--date` (str, default None), `--rerun` (store_true, default False), keeping `--config` and `--datasets` unchanged.
- Implemented `main()`: loads orchestrator config, resolves `parent_paths_config` key (falling back to `"config/parent_paths.yaml"`), calls `load_parent_paths()`, parses partition date (or defaults to `date.today()`), logs INFO run parameters and DEBUG per-path. Replaced `print()` stub with proper logging. Left `# TODO(story-3-2)` comment as runner handoff.
- Fixed `pyproject.toml` to add `[build-system]` (hatchling), `[tool.hatch.build.targets.wheel]` packages source, and `[tool.pytest.ini_options]` with `pythonpath = ["src"]` so `orchestrator` package is importable during test runs.
- All 17 new ATDD tests pass; 3 existing placeholder tests continue to pass (20/20 total, zero regressions).

### File List

- `dqs-orchestrator/src/orchestrator/cli.py` (modified — replaced placeholder with full implementation)
- `dqs-orchestrator/pyproject.toml` (modified — added build-system, hatch wheel config, pytest pythonpath)
- `dqs-orchestrator/tests/test_cli.py` (pre-existing ATDD file, 17 tests now pass)

### Review Findings

_to be filled by code review agent_

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
- 2026-04-03: Implementation complete. cli.py fully implemented (load_parent_paths, extended parse_args, wired main). pyproject.toml updated with src layout and pytest config. All 17 ATDD tests pass, 20/20 total. Status set to review.
