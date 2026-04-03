# Story 3.2: Spark-Submit Runner with Failure Isolation

Status: done

## Story

As a **platform operator**,
I want the orchestrator to invoke spark-submit for each parent path with per-parent-path failure isolation,
So that one failed path does not halt processing of other paths.

## Acceptance Criteria

1. **Given** 3 parent paths configured **When** the orchestrator runs **Then** it invokes spark-submit once per parent path, passing the path and any runtime parameters.

2. **Given** spark-submit for parent path 2 fails with a non-zero exit code **When** the orchestrator processes all paths **Then** paths 1 and 3 are still processed successfully **And** the failure for path 2 is captured with the exit code and stderr output.

3. **Given** spark-submit for a path returns exit code 0 **When** the orchestrator checks the result **Then** it records the path as successful.

## Tasks / Subtasks

- [x] Task 1: Implement `run_spark_job()` in `runner.py` — replaces the `NotImplementedError` placeholder (AC: #1, #2, #3)
  - [x] Build the spark-submit command list from the `spark` config section (submit_path, master, deploy_mode, driver_memory, executor_memory, app_jar) plus `--parent-path` and `--date`
  - [x] Accept optional `datasets: list[str] | None` parameter and pass `--datasets` if provided (for Story 3.4 rerun support — wired but not yet used)
  - [x] Use `subprocess.run()` with `capture_output=True` and `text=True` — do NOT use `shell=True`
  - [x] Return a `JobResult` dataclass (already in `models.py`) with `success=True/False`, `error_message` from stderr on failure
  - [x] Signature: `run_spark_job(parent_path: str, partition_date: date, spark_config: dict, datasets: list[str] | None = None) -> JobResult`

- [x] Task 2: Implement `run_all_paths()` in `runner.py` — per-path failure isolation loop (AC: #1, #2)
  - [x] Iterate over all parent paths, calling `run_spark_job()` for each
  - [x] Wrap each call in try/except to catch unexpected Python errors (beyond non-zero exit code)
  - [x] Collect and return all `JobResult` objects regardless of individual failures
  - [x] Log `INFO` before each spark-submit invocation (path, date)
  - [x] Log `INFO` after each result: `SUCCESS path=...` or `FAILURE path=... exit_code=... stderr=...`
  - [x] Signature: `run_all_paths(parent_paths: list[str], partition_date: date, spark_config: dict, datasets: list[str] | None = None) -> list[JobResult]`

- [x] Task 3: Wire `run_all_paths()` into `cli.main()` — replace the `# TODO(story-3-2)` stub (AC: #1)
  - [x] Import `run_all_paths` from `orchestrator.runner`
  - [x] Extract `spark_config` from the loaded `orchestrator_config["spark"]` section
  - [x] Call `run_all_paths(parent_paths, partition_date, spark_config, args.datasets)`
  - [x] Log a final summary: total paths, succeeded count, failed count
  - [x] Exit with code 1 if any paths failed, code 0 if all succeeded

- [x] Task 4: Write `tests/test_runner.py` — replace placeholder with real pytest unit tests (AC: #1, #2, #3)
  - [x] Replace `test_placeholder()` with real tests using `unittest.mock.patch`
  - [x] Test: `run_spark_job` returns `JobResult(success=True)` when mocked subprocess exits 0
  - [x] Test: `run_spark_job` returns `JobResult(success=False, error_message=<stderr>)` when exit code non-zero
  - [x] Test: `run_spark_job` builds correct command list (includes `--parent-path`, `--date`, spark flags)
  - [x] Test: `run_spark_job` passes `--datasets` args when `datasets` is provided
  - [x] Test: `run_all_paths` processes all 3 paths even when path 2 fails (isolation test — core AC #2)
  - [x] Test: `run_all_paths` returns correct success/failure in results list
  - [x] Test: `run_all_paths` handles unexpected exception from `run_spark_job` for one path (continues to others)

## Dev Notes

### Existing Code — What to Extend, Not Replace

| File | Current State | This Story's Action |
|---|---|---|
| `dqs-orchestrator/src/orchestrator/runner.py` | Placeholder `run_spark_job()` raising `NotImplementedError` | REPLACE: full implementation |
| `dqs-orchestrator/src/orchestrator/cli.py` | Has `# TODO(story-3-2)` comment as handoff point | EXTEND: wire `run_all_paths()` call |
| `dqs-orchestrator/src/orchestrator/models.py` | `JobResult` dataclass already exists and is correct | DO NOT TOUCH |
| `dqs-orchestrator/src/orchestrator/db.py` | Placeholder | DO NOT TOUCH — Story 3.3 |
| `dqs-orchestrator/src/orchestrator/email.py` | Placeholder | DO NOT TOUCH — Story 3.5 |
| `dqs-orchestrator/tests/test_runner.py` | `test_placeholder()` only | REPLACE placeholder with real tests |
| `dqs-orchestrator/tests/test_cli.py` | 17 tests all passing (Story 3.1) | DO NOT MODIFY |

**Critical:** The `# TODO(story-3-2)` comment in `cli.py` (line 96) is the exact handoff point. Replace that single comment with the `run_all_paths()` call and summary log.

### `JobResult` Dataclass — Already Exists in `models.py`

```python
@dataclass
class JobResult:
    parent_path: str
    success: bool
    failed_datasets: list[str] = field(default_factory=list)
    error_message: Optional[str] = None
```

Use this **exactly as-is**. Do NOT add new fields or modify `models.py`. For Story 3.2, `failed_datasets` will always be an empty list (dataset-level failure tracking is Story 3.3+).

### Exact `runner.py` Design (Authoritative)

```python
"""Spark submit runner — invokes spark-submit per parent path with per-path failure isolation."""
import logging
import subprocess
from datetime import date

from orchestrator.models import JobResult

logger = logging.getLogger(__name__)


def run_spark_job(
    parent_path: str,
    partition_date: date,
    spark_config: dict,
    datasets: list[str] | None = None,
) -> JobResult:
    """Submit a Spark DQ job for the given parent path.

    Builds and runs the spark-submit command. Returns JobResult with success status.
    Caller provides per-path isolation via run_all_paths().
    """
    submit_path = spark_config.get("submit_path", "spark-submit")
    app_jar = spark_config.get("app_jar", "dqs-spark.jar")

    cmd = [
        submit_path,
        "--master", spark_config.get("master", "yarn"),
        "--deploy-mode", spark_config.get("deploy_mode", "cluster"),
        "--driver-memory", spark_config.get("driver_memory", "2g"),
        "--executor-memory", spark_config.get("executor_memory", "4g"),
        app_jar,
        "--parent-path", parent_path,
        "--date", partition_date.strftime("%Y%m%d"),
    ]

    if datasets:
        cmd.extend(["--datasets"] + datasets)

    logger.info("spark-submit starting: path=%s date=%s", parent_path, partition_date)

    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode == 0:
        logger.info("spark-submit SUCCESS: path=%s", parent_path)
        return JobResult(parent_path=parent_path, success=True)
    else:
        logger.error(
            "spark-submit FAILURE: path=%s exit_code=%d stderr=%s",
            parent_path, result.returncode, result.stderr,
        )
        return JobResult(
            parent_path=parent_path,
            success=False,
            error_message=f"exit_code={result.returncode} stderr={result.stderr}",
        )


def run_all_paths(
    parent_paths: list[str],
    partition_date: date,
    spark_config: dict,
    datasets: list[str] | None = None,
) -> list[JobResult]:
    """Run spark-submit for each parent path with per-path failure isolation.

    Failed paths are recorded but do not halt processing of remaining paths.
    """
    results: list[JobResult] = []

    for path in parent_paths:
        try:
            job_result = run_spark_job(path, partition_date, spark_config, datasets)
        except Exception as exc:  # noqa: BLE001
            logger.error("Unexpected error running spark job for path=%s: %s", path, exc)
            job_result = JobResult(
                parent_path=path,
                success=False,
                error_message=f"Unexpected error: {exc}",
            )
        results.append(job_result)

    return results
```

**Critical design notes:**
- `subprocess.run()` with `capture_output=True, text=True` — no `shell=True` ever (security risk, also harder to test)
- `app_jar` comes from `spark_config.get("app_jar", "dqs-spark.jar")` — must be in `orchestrator.yaml` for real deployments
- `datasets` extends the command with `--datasets ds1 ds2` as separate args — consistent with argparse `nargs="*"` on the Spark side
- The outer `try/except Exception` in `run_all_paths` catches unexpected Python errors (not subprocess failures — those are handled by returncode check inside `run_spark_job`). This is the second layer of isolation.
- `logger.error` for failures — per project-context.md ERROR level means "needs attention"

### Exact `cli.py` Change — Replace the TODO Stub

Replace the single TODO comment in `cli.py` (currently the last body line of `main()`):

```python
# TODO(story-3-2): invoke runner.run_spark_job() for each parent path
```

With:

```python
from orchestrator.runner import run_all_paths  # add to top-level imports

# Inside main(), after the for path in parent_paths loop:
spark_config = orchestrator_config.get("spark", {})
results = run_all_paths(parent_paths, partition_date, spark_config, args.datasets)

succeeded = sum(1 for r in results if r.success)
failed = len(results) - succeeded
logger.info(
    "DQS Orchestrator complete: total_paths=%d succeeded=%d failed=%d",
    len(results), succeeded, failed,
)

if failed > 0:
    sys.exit(1)
```

**Move the import** to the top of `cli.py` with the other imports (not inline inside `main()`).

### `orchestrator.yaml` — `app_jar` Key Must Be Added

The current `orchestrator.yaml` is missing the `app_jar` key under `spark`. The developer must add it:

```yaml
spark:
  submit_path: spark-submit
  master: yarn
  deploy_mode: cluster
  driver_memory: 2g
  executor_memory: 4g
  app_jar: dqs-spark.jar   # ← ADD THIS LINE
```

This is required for `run_spark_job` to know which JAR to invoke. If absent, it defaults to `"dqs-spark.jar"` via `spark_config.get("app_jar", "dqs-spark.jar")`.

### DqsJob.java CLI Arguments Reference

The Spark job accepts exactly these CLI args (from `DqsJob.java` argparse):
- `--parent-path <path>` (required)
- `--date <yyyyMMdd>` (optional, defaults to today)
- `--datasets` is NOT yet supported in DqsJob.java (Story 3.4 adds it) — pass it through anyway so the flag is ready; DqsJob silently ignores unknown flags in its current parser design

The orchestrator constructs: `spark-submit [spark-flags] <app_jar> --parent-path <path> --date <date>`

### Subprocess Mocking Pattern for Tests

```python
from unittest.mock import patch, MagicMock
from datetime import date
from orchestrator.runner import run_spark_job, run_all_paths
from orchestrator.models import JobResult

SPARK_CONFIG = {
    "submit_path": "spark-submit",
    "master": "yarn",
    "deploy_mode": "cluster",
    "driver_memory": "2g",
    "executor_memory": "4g",
    "app_jar": "dqs-spark.jar",
}


def make_completed_process(returncode: int, stderr: str = "") -> MagicMock:
    mock = MagicMock()
    mock.returncode = returncode
    mock.stderr = stderr
    return mock


def test_run_spark_job_returns_success_on_exit_code_0() -> None:
    with patch("subprocess.run", return_value=make_completed_process(0)):
        result = run_spark_job("/data/finance/loans", date(2026, 3, 25), SPARK_CONFIG)
    assert result.success is True
    assert result.parent_path == "/data/finance/loans"


def test_run_spark_job_returns_failure_on_nonzero_exit() -> None:
    with patch("subprocess.run", return_value=make_completed_process(1, stderr="OOM")):
        result = run_spark_job("/data/finance/loans", date(2026, 3, 25), SPARK_CONFIG)
    assert result.success is False
    assert "exit_code=1" in result.error_message
    assert "OOM" in result.error_message


def test_run_spark_job_builds_correct_command() -> None:
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job("/data/risk/credit", date(2026, 3, 25), SPARK_CONFIG)
    cmd = mock_run.call_args[0][0]
    assert "--parent-path" in cmd
    assert "/data/risk/credit" in cmd
    assert "--date" in cmd
    assert "20260325" in cmd
    assert "dqs-spark.jar" in cmd


def test_run_spark_job_passes_datasets_when_provided() -> None:
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job("/data/risk/credit", date(2026, 3, 25), SPARK_CONFIG, datasets=["ue90-omni"])
    cmd = mock_run.call_args[0][0]
    assert "--datasets" in cmd
    assert "ue90-omni" in cmd


def test_run_all_paths_processes_all_paths_when_middle_fails() -> None:
    """Core AC2: failure isolation — path 2 fails, paths 1 and 3 still run."""
    side_effects = [
        make_completed_process(0),   # path 1: success
        make_completed_process(1, stderr="YARN error"),  # path 2: failure
        make_completed_process(0),   # path 3: success
    ]
    with patch("subprocess.run", side_effect=side_effects):
        results = run_all_paths(
            ["/data/finance/loans", "/data/finance/deposits", "/data/risk/credit"],
            date(2026, 3, 25),
            SPARK_CONFIG,
        )
    assert len(results) == 3
    assert results[0].success is True
    assert results[1].success is False
    assert results[2].success is True


def test_run_all_paths_continues_after_unexpected_exception() -> None:
    """Isolation holds even when run_spark_job raises a Python exception."""
    with patch(
        "orchestrator.runner.run_spark_job",
        side_effect=[
            JobResult("/data/finance/loans", success=True),
            RuntimeError("unexpected"),
            JobResult("/data/risk/credit", success=True),
        ],
    ):
        results = run_all_paths(
            ["/data/finance/loans", "/data/finance/deposits", "/data/risk/credit"],
            date(2026, 3, 25),
            SPARK_CONFIG,
        )
    assert len(results) == 3
    assert results[1].success is False
    assert "Unexpected error" in results[1].error_message
```

### File Structure Requirements

```
dqs-orchestrator/
  src/orchestrator/
    runner.py        ← REPLACE placeholder — implement run_spark_job() and run_all_paths()
    cli.py           ← EXTEND — wire run_all_paths() call, replace # TODO(story-3-2) comment
    models.py        ← DO NOT TOUCH
    db.py            ← DO NOT TOUCH
    email.py         ← DO NOT TOUCH
  config/
    orchestrator.yaml ← EXTEND — add app_jar key under spark section
  tests/
    test_runner.py   ← REPLACE placeholder test with ~7 real tests
    test_cli.py      ← DO NOT TOUCH — 17 tests must continue to pass
    test_db.py       ← DO NOT TOUCH
    test_email.py    ← DO NOT TOUCH
```

### Python Conventions (dqs-orchestrator)

Per `project-context.md`:
- Type hints on **all** function parameters and return types
- `snake_case` for functions, variables, module names
- Use `list[str] | None` union syntax (Python 3.12+, `pyproject.toml` requires `>=3.12`)
- Module-level `logger = logging.getLogger(__name__)` — already in `runner.py` placeholder, keep it
- No `print()` in production code — use `logger`

### Dependencies — No New Packages Needed

`subprocess` is Python stdlib. `pyproject.toml` already has all needed dependencies:

```toml
dependencies = [
    "psycopg2-binary>=2.9.11",
    "pyyaml>=6.0.3",
]
```

Do NOT run `uv add` for anything.

### Test Run Commands

```bash
# Story 3.2 tests only
cd /home/sas/workspace/data-quality-service/dqs-orchestrator
uv run pytest tests/test_runner.py -v

# Full regression (must pass — includes 17 test_cli.py + 1 test_email.py + 1 test_db.py)
cd /home/sas/workspace/data-quality-service/dqs-orchestrator
uv run pytest -v
```

Current test baseline: 20 tests passing (17 `test_cli.py` + 1 each placeholder in `test_runner.py`, `test_db.py`, `test_email.py`). After this story: `test_runner.py` placeholder is replaced, so net test count = 20 - 1 + ~7 new = ~26 passing.

### Logging Pattern

Per `project-context.md`, use structured console logging with `run_id` where available (Story 3.3 wires `run_id`; for now it's absent from logs but reserved for future):

| Event | Level | Message Pattern |
|---|---|---|
| Before spark-submit | INFO | `spark-submit starting: path=%s date=%s` |
| Success | INFO | `spark-submit SUCCESS: path=%s` |
| Non-zero exit | ERROR | `spark-submit FAILURE: path=%s exit_code=%d stderr=%s` |
| Unexpected exception | ERROR | `Unexpected error running spark job for path=%s: %s` |
| Final summary | INFO | `DQS Orchestrator complete: total_paths=%d succeeded=%d failed=%d` |

### Architecture Constraints

- `dqs-orchestrator` runs **outside** docker-compose — it's a scheduling server CLI, not a service.
- `subprocess.run()` with `capture_output=True, text=True, shell=False` — never `shell=True` (security, testability).
- This story does NOT write to `dq_orchestration_run` — that is Story 3.3. The `db.py` placeholder is untouched.
- This story does NOT send email — that is Story 3.5. The `email.py` placeholder is untouched.
- The `--datasets` arg passed through to spark-submit is a Story 3.4 concern for DqsJob — it's wired in this story's command builder but DqsJob will silently ignore it until Story 3.4 extends DqsJob's arg parser.
- `BatchWriter.orchestration_run_id = NULL` (set in Spark) remains NULL until Story 3.3 wires the run ID from the orchestrator.

### Cross-Story Dependency Map

| Story | Depends on 3.2 |
|---|---|
| 3.3 (Run Tracking) | `run_all_paths()` return value (`list[JobResult]`) feeds failed/succeeded counts for `dq_orchestration_run` record |
| 3.4 (Rerun Management) | `--datasets` passthrough already wired; Story 3.4 activates it in DqsJob |
| 3.5 (Email) | `list[JobResult]` provides per-path failure data for email composition |

### Previous Story Intelligence (Story 3.1)

From Story 3.1 completion notes and implementation:

- **`pyproject.toml` src layout is confirmed working** — `pythonpath = ["src"]` in `[tool.pytest.ini_options]` makes `from orchestrator.X import Y` work in tests. No changes needed.
- **`parse_args()` already returns `args.datasets`** — it's `list[str] | None`. Pass it directly to `run_all_paths()`.
- **`args.rerun` is parsed but Story 3.2 does not act on it** — `args.rerun` is available in `main()` but rerun logic belongs to Story 3.4. Do not add rerun handling here.
- **`cli.py` `main()` loads `orchestrator_config["spark"]` already** — the `orchestrator_config` dict is already loaded in `main()`. Extract `spark_config = orchestrator_config.get("spark", {})` — no re-read needed.
- **`partition_date` is already a `date` object** in `main()` — pass it directly to `run_all_paths()`.
- **`test_cli.py`'s `test_main_exits_cleanly...` test mocks the runner** — when you wire `run_all_paths()` into `main()`, this test may need updating to mock `orchestrator.runner.run_all_paths`. Check the test to ensure it still passes.

### Epic 2 Retrospective Lessons Applied

1. **No stale TDD RED PHASE comments in test file** — write tests clean, no "will fail" annotations.
2. **Type hints on all function signatures** — `def run_spark_job(parent_path: str, ...) -> JobResult:`.
3. **Per-path isolation is the story's core contribution** — the try/except in `run_all_paths` is load-bearing logic, not defensive boilerplate.
4. **Zero regressions rule** — run the full suite before marking done. The existing 17 `test_cli.py` tests must all still pass.

### Git Intelligence

Commit pattern established in Epic 3:
```
bmad(epic-3/3-2-spark-submit-runner-with-failure-isolation): complete workflow and quality gates
```

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Patch target for `run_all_paths` in test_cli.py must be `orchestrator.cli.run_all_paths` (not `orchestrator.runner.run_all_paths`) because cli.py imports it at module level via `from orchestrator.runner import run_all_paths`.

### Completion Notes List

- Implemented `run_spark_job()` in `runner.py`: builds spark-submit command list from config, uses `subprocess.run(capture_output=True, text=True)` (no `shell=True`), returns `JobResult` with success/failure and stderr-based error_message.
- Implemented `run_all_paths()` in `runner.py`: iterates all parent paths with try/except for per-path isolation, collects all `JobResult` objects regardless of failures.
- Wired `run_all_paths()` into `cli.main()`: replaced `# TODO(story-3-2)` comment with call to `run_all_paths`, added final summary log and `sys.exit(1)` if any paths failed.
- Added `app_jar: dqs-spark.jar` to `config/orchestrator.yaml` under `spark` section.
- All 7 ATDD tests in `test_runner.py` pass. Full suite: 28 tests pass (19 test_cli.py + 1 test_db.py + 1 test_email.py + 7 test_runner.py).
- Updated 2 tests in `test_cli.py` to mock `orchestrator.cli.run_all_paths` so `main()` integration tests continue to pass without requiring a real spark-submit binary — as explicitly anticipated in story Dev Notes.

### File List

- `dqs-orchestrator/src/orchestrator/runner.py` — replaced placeholder with full implementation of `run_spark_job()` and `run_all_paths()`
- `dqs-orchestrator/src/orchestrator/cli.py` — added `from orchestrator.runner import run_all_paths` import; replaced `# TODO(story-3-2)` with `run_all_paths()` call and summary log
- `dqs-orchestrator/config/orchestrator.yaml` — added `app_jar: dqs-spark.jar` under `spark` section
- `dqs-orchestrator/tests/test_runner.py` — replaced placeholder test with 7 ATDD tests covering all ACs (pre-existing file, tests were already written for TDD red phase)
- `dqs-orchestrator/tests/test_cli.py` — updated 2 `main()` integration tests to mock `orchestrator.cli.run_all_paths` (required by story Dev Notes to prevent regression)

### Review Findings

_to be filled by code review agent_

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
- 2026-04-03: Implementation complete. Implemented runner.py (run_spark_job + run_all_paths), wired into cli.py, added app_jar to orchestrator.yaml. All 28 tests pass. Status set to review.
