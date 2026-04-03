# Story 3.3: Orchestration Run Tracking & Error Capture

Status: done

## Story

As a **platform operator**,
I want every orchestration run tracked in `dq_orchestration_run` with dataset counts, status, and error summaries,
So that I have a complete audit trail of every run for debugging and compliance.

## Acceptance Criteria

1. **Given** an orchestration run starts **When** the CLI begins processing **Then** it creates a `dq_orchestration_run` record with `start_time` and `run_status='running'` for each parent path.

2. **Given** all parent paths have been processed **When** the orchestrator finalizes the run **Then** it updates the `dq_orchestration_run` record with `end_time`, `total_datasets`, `passed_datasets`, `failed_datasets`, and `run_status='completed'` or `run_status='completed_with_errors'` **And** `error_summary` contains a text summary of any failures.

3. **Given** individual `dq_run` records created by Spark **When** the orchestrator links them **Then** each `dq_run.orchestration_run_id` references the correct `dq_orchestration_run.id`.

## Tasks / Subtasks

- [x] Task 1: Implement DB functions in `db.py` — create and finalize orchestration run records (AC: #1, #2)
  - [x] Define `EXPIRY_SENTINEL = "9999-12-31 23:59:59"` constant in `db.py`
  - [x] Implement `create_orchestration_run(conn, parent_path: str, start_time: datetime) -> int` — INSERTs row with `run_status='running'`, returns the auto-generated `id`
  - [x] Implement `finalize_orchestration_run(conn, run_id: int, end_time: datetime, total_datasets: int, passed_datasets: int, failed_datasets: int, error_summary: str | None) -> None` — UPDATEs the row for `run_status`, `end_time`, `total_datasets`, `passed_datasets`, `failed_datasets`, `error_summary`
  - [x] Use `run_status='completed'` when `failed_datasets == 0`, `run_status='completed_with_errors'` otherwise
  - [x] Use `conn.cursor()` + `cursor.execute()` + `conn.commit()` — `get_connection()` already exists in `db.py`

- [x] Task 2: Update `runner.py` to accept and pass `orchestration_run_id` to spark-submit (AC: #3)
  - [x] Add optional `orchestration_run_id: int | None = None` parameter to `run_spark_job()`
  - [x] When `orchestration_run_id` is not None, append `["--orchestration-run-id", str(orchestration_run_id)]` to the spark-submit command
  - [x] Update `run_all_paths()` to accept and thread through `orchestration_run_ids: dict[str, int] | None = None`
  - [x] In `run_all_paths()`, look up `orchestration_run_id = orchestration_run_ids.get(path)` when dict is provided

- [x] Task 3: Wire run tracking into `cli.main()` (AC: #1, #2, #3)
  - [x] Import `get_connection` from `orchestrator.db`
  - [x] Import `create_orchestration_run`, `finalize_orchestration_run` from `orchestrator.db`
  - [x] Load `database.url` from `orchestrator_config` (key: `orchestrator_config["database"]["url"]`), fall back to env `DATABASE_URL`
  - [x] Before `run_all_paths()`, for each parent path: open a connection, call `create_orchestration_run()`, record the returned `run_id`, close connection
  - [x] Pass the mapping of `{parent_path: run_id}` as `orchestration_run_ids` to `run_all_paths()`
  - [x] After `run_all_paths()`, for each `JobResult`, compute `total_datasets`, `passed_datasets`, `failed_datasets` from the result, call `finalize_orchestration_run()` with appropriate status
  - [x] DB errors during create/finalize must be logged at ERROR level but must NOT prevent spark-submit from running or cause `sys.exit(1)` beyond what the spark results determine

- [x] Task 4: Update `JobResult` in `models.py` to support dataset counts (AC: #2)
  - [x] The existing `failed_datasets: list[str]` field in `JobResult` already carries per-path failure list from Spark stderr
  - [x] No new fields needed — `total_datasets` is queried from Postgres after Spark completes (or derived from counts); for Story 3.3, set `total_datasets = len(job_result.failed_datasets) + passed_count` where `passed_count` is queried OR default to 0/None if not available
  - [x] For MVP simplicity: `total_datasets = None`, `passed_datasets = None`, set only `failed_datasets = len(job_result.failed_datasets)` from `JobResult.failed_datasets` length

- [x] Task 5: Write `tests/test_db.py` — replace placeholder with real psycopg2-mocked unit tests (AC: #1, #2)
  - [x] Replace `test_placeholder()` with real tests using `unittest.mock.MagicMock` for psycopg2 connection/cursor
  - [x] Test: `create_orchestration_run` executes correct INSERT SQL and returns the auto-generated ID
  - [x] Test: `finalize_orchestration_run` executes correct UPDATE SQL with `run_status='completed'` when no failures
  - [x] Test: `finalize_orchestration_run` executes correct UPDATE SQL with `run_status='completed_with_errors'` when `failed_datasets > 0`
  - [x] Test: `get_connection()` uses `DATABASE_URL` env var when set
  - [x] Test: `create_orchestration_run` uses `EXPIRY_SENTINEL` constant — NOT the hardcoded string

## Dev Notes

### Database Schema — `dq_orchestration_run` (authoritative from `dqs-serve/src/serve/schema/ddl.sql`)

```sql
CREATE TABLE dq_orchestration_run (
    id               SERIAL PRIMARY KEY,
    parent_path      TEXT NOT NULL,
    run_status       TEXT NOT NULL,      -- 'running', 'completed', 'completed_with_errors'
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    total_datasets   INTEGER,
    passed_datasets  INTEGER,
    failed_datasets  INTEGER,
    error_summary    TEXT,
    create_date      TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date      TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dq_orchestration_run_parent_path_expiry_date
        UNIQUE (parent_path, expiry_date)
);
```

**One row per parent path per run.** The `UNIQUE(parent_path, expiry_date)` constraint means each active (non-expired) run for a parent path must be unique. This supports per-path tracking across all parent paths.

### `dq_run.orchestration_run_id` FK (from `ddl.sql`)

```sql
ALTER TABLE dq_run
    ADD CONSTRAINT fk_dq_run_orchestration_run
    FOREIGN KEY (orchestration_run_id) REFERENCES dq_orchestration_run(id);
```

`dq_run.orchestration_run_id` is currently `NULL` (set in `BatchWriter.java`, line ~159):
```java
// TODO(epic-3): set orchestration_run_id once assigned by orchestrator
ps.setNull(7, Types.INTEGER);
```

This story wires the `orchestration_run_id` from the orchestrator to Spark via CLI arg `--orchestration-run-id`. **DqsJob.java must also be updated** to parse `--orchestration-run-id` and pass it to `BatchWriter`. This is the cross-component change.

### Existing File State — What to Touch

| File | Current State | This Story's Action |
|---|---|---|
| `dqs-orchestrator/src/orchestrator/db.py` | `get_connection()` placeholder only | EXTEND: add `EXPIRY_SENTINEL`, `create_orchestration_run()`, `finalize_orchestration_run()` |
| `dqs-orchestrator/src/orchestrator/models.py` | `JobResult` dataclass with `failed_datasets: list[str]` | DO NOT CHANGE — use `failed_datasets` field as-is for error count |
| `dqs-orchestrator/src/orchestrator/runner.py` | Full impl from Story 3.2 | EXTEND: add `orchestration_run_id` param to `run_spark_job()` and `run_all_paths()` |
| `dqs-orchestrator/src/orchestrator/cli.py` | Full impl from Story 3.2 | EXTEND: wire `create_orchestration_run()` and `finalize_orchestration_run()` around `run_all_paths()` |
| `dqs-orchestrator/tests/test_db.py` | `test_placeholder()` only | REPLACE with ~5 real tests |
| `dqs-orchestrator/tests/test_runner.py` | 7 tests from Story 3.2 | EXTEND: add tests for `orchestration_run_id` passthrough |
| `dqs-orchestrator/tests/test_cli.py` | 19 tests from Story 3.2 | MAY NEED UPDATE if `main()` signature changes for DB wiring |
| `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` | Ignores unknown CLI args | EXTEND: parse `--orchestration-run-id` and pass to `BatchWriter` |
| `dqs-spark/src/main/java/com/bank/dqs/writer/BatchWriter.java` | `ps.setNull(7, Types.INTEGER)` for orchestration_run_id | EXTEND: accept `Long orchestrationRunId` in `write()` signature, set if non-null |

**DO NOT TOUCH:**
- `dqs-orchestrator/src/orchestrator/email.py` — Story 3.5
- `dqs-serve/src/serve/schema/ddl.sql` — schema already has all required columns

### `db.py` Implementation (Authoritative Design)

```python
"""Database helpers for dqs-orchestrator.

Uses psycopg2-binary (not SQLAlchemy) for lightweight CLI usage.
"""
import logging
import os
from datetime import datetime
from typing import Any

import psycopg2

logger = logging.getLogger(__name__)

# Temporal pattern: active records use this sentinel as expiry_date
# NEVER hardcode '9999-12-31 23:59:59' inline — always use this constant
# See project-context.md § Temporal Data Pattern (ALL COMPONENTS)
EXPIRY_SENTINEL = "9999-12-31 23:59:59"


def get_connection() -> Any:
    """Return a psycopg2 connection using environment or config defaults."""
    dsn: str = os.getenv("DATABASE_URL", "postgresql://postgres:localdev@localhost:5432/postgres")
    return psycopg2.connect(dsn)


def create_orchestration_run(conn: Any, parent_path: str, start_time: datetime) -> int:
    """Insert a new dq_orchestration_run row with run_status='running'.

    Returns the auto-generated id.
    """
    sql = """
        INSERT INTO dq_orchestration_run
            (parent_path, run_status, start_time, expiry_date)
        VALUES (%s, %s, %s, %s)
        RETURNING id
    """
    with conn.cursor() as cur:
        cur.execute(sql, (parent_path, "running", start_time, EXPIRY_SENTINEL))
        row = cur.fetchone()
        conn.commit()
        run_id: int = row[0]
        logger.info("Created orchestration_run: id=%d parent_path=%s", run_id, parent_path)
        return run_id


def finalize_orchestration_run(
    conn: Any,
    run_id: int,
    end_time: datetime,
    total_datasets: int | None,
    passed_datasets: int | None,
    failed_datasets: int,
    error_summary: str | None,
) -> None:
    """Update dq_orchestration_run row with final status, counts, and error_summary.

    Sets run_status='completed' if failed_datasets==0, else 'completed_with_errors'.
    """
    run_status = "completed" if failed_datasets == 0 else "completed_with_errors"
    sql = """
        UPDATE dq_orchestration_run
        SET end_time = %s,
            total_datasets = %s,
            passed_datasets = %s,
            failed_datasets = %s,
            run_status = %s,
            error_summary = %s
        WHERE id = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (
            end_time, total_datasets, passed_datasets,
            failed_datasets, run_status, error_summary, run_id,
        ))
        conn.commit()
    logger.info(
        "Finalized orchestration_run: id=%d status=%s failed=%d",
        run_id, run_status, failed_datasets,
    )
```

**Critical:** `EXPIRY_SENTINEL` is defined here in Python. The Java side uses `DqsConstants.EXPIRY_SENTINEL`. Both must be identical per `project-context.md`. Never hardcode the string inline.

### `runner.py` Signature Changes (Additive Only)

Extend `run_spark_job()` signature — current:
```python
def run_spark_job(
    parent_path: str,
    partition_date: date,
    spark_config: dict[str, Any],
    datasets: list[str] | None = None,
) -> JobResult:
```

New signature (add optional `orchestration_run_id`):
```python
def run_spark_job(
    parent_path: str,
    partition_date: date,
    spark_config: dict[str, Any],
    datasets: list[str] | None = None,
    orchestration_run_id: int | None = None,
) -> JobResult:
```

In the command builder, after the `--datasets` block:
```python
if orchestration_run_id is not None:
    cmd.extend(["--orchestration-run-id", str(orchestration_run_id)])
```

Extend `run_all_paths()`:
```python
def run_all_paths(
    parent_paths: list[str],
    partition_date: date,
    spark_config: dict[str, Any],
    datasets: list[str] | None = None,
    orchestration_run_ids: dict[str, int] | None = None,
) -> list[JobResult]:
```

Inside the loop:
```python
orch_id = orchestration_run_ids.get(path) if orchestration_run_ids else None
job_result = run_spark_job(path, partition_date, spark_config, datasets, orchestration_run_id=orch_id)
```

### `cli.py` Run Tracking Wiring (Authoritative Pattern)

The new code inserts **before** and **after** the `run_all_paths()` call that already exists in `main()`:

```python
from datetime import datetime
from orchestrator.db import create_orchestration_run, finalize_orchestration_run, get_connection

# --- In main(), before run_all_paths() ---

# Create one orchestration run record per parent path
orchestration_run_ids: dict[str, int] = {}
for path in parent_paths:
    try:
        with get_connection() as conn:
            run_id = create_orchestration_run(conn, path, datetime.now())
            orchestration_run_ids[path] = run_id
    except Exception as exc:  # noqa: BLE001
        logger.error("Failed to create orchestration_run for path=%s: %s", path, exc)
        # Do NOT block spark-submit — continue without run_id

results = run_all_paths(
    parent_paths, partition_date, spark_config, args.datasets,
    orchestration_run_ids=orchestration_run_ids if orchestration_run_ids else None,
)

# --- After run_all_paths() ---

# Finalize each orchestration run record
for result in results:
    run_id = orchestration_run_ids.get(result.parent_path)
    if run_id is None:
        continue  # run record was not created — skip finalize
    failed_count = len(result.failed_datasets)
    error_summary = result.error_message if not result.success else None
    try:
        with get_connection() as conn:
            finalize_orchestration_run(
                conn,
                run_id,
                datetime.now(),
                total_datasets=None,    # not available from spark exit code alone
                passed_datasets=None,   # not available from spark exit code alone
                failed_datasets=failed_count,
                error_summary=error_summary,
            )
    except Exception as exc:  # noqa: BLE001
        logger.error("Failed to finalize orchestration_run id=%d: %s", run_id, exc)
```

**Critical notes:**
- `get_connection()` returns a psycopg2 connection — use `with conn:` as context manager (psycopg2 connections support `__enter__`/`__exit__` for transaction handling, but NOT auto-close). Call `conn.close()` explicitly, or use psycopg2's `with psycopg2.connect(...) as conn:` pattern.
- DB failures are non-fatal — `logger.error()` and continue. Never let DB errors halt spark-submit.
- `total_datasets` and `passed_datasets` are `None` in this story since Spark doesn't surface per-dataset counts via exit code. Story 3.5 may query them post-run.

### DqsJob.java and BatchWriter.java Changes

**DqsJob.java — parse `--orchestration-run-id` CLI arg:**

Add to `parseArgs()` switch block (alongside `--parent-path` and `--date`):
```java
case "--orchestration-run-id" -> {
    if (i + 1 >= args.length) {
        throw new IllegalArgumentException(
                "--orchestration-run-id flag requires a value but none was provided");
    }
    orchestrationRunId = Long.parseLong(args[++i]);
}
```

The `DqsJobArgs` record needs a new field:
```java
record DqsJobArgs(String parentPath, LocalDate partitionDate, Long orchestrationRunId) {}
```
`orchestrationRunId` defaults to `null` if not provided.

**BatchWriter.java — accept `orchestrationRunId` in `write()` signature:**

Current:
```java
public long write(DatasetContext ctx, List<DqMetric> metrics) throws SQLException
```

New:
```java
public long write(DatasetContext ctx, List<DqMetric> metrics, Long orchestrationRunId) throws SQLException
```

In `insertDqRun()`, replace:
```java
// TODO(epic-3): set orchestration_run_id once assigned by orchestrator
ps.setNull(7, Types.INTEGER);
```

With:
```java
if (orchestrationRunId != null) {
    ps.setLong(7, orchestrationRunId);
} else {
    ps.setNull(7, Types.INTEGER);
}
```

**DqsJob.java main() — pass orchestrationRunId to writer.write():**
```java
long runId = writer.write(ctx, datasetMetrics, jobArgs.orchestrationRunId());
```

### `test_db.py` Pattern (Authoritative Mock Design)

```python
from unittest.mock import MagicMock, patch
from datetime import datetime
from orchestrator.db import (
    create_orchestration_run,
    finalize_orchestration_run,
    EXPIRY_SENTINEL,
)


def make_mock_conn(fetchone_return=None):
    """Helper: returns a mock psycopg2 connection with cursor context manager."""
    mock_cursor = MagicMock()
    mock_cursor.fetchone.return_value = fetchone_return or [42]
    mock_cursor.__enter__ = lambda s: mock_cursor
    mock_cursor.__exit__ = MagicMock(return_value=False)
    mock_conn = MagicMock()
    mock_conn.cursor.return_value = mock_cursor
    return mock_conn, mock_cursor


def test_create_orchestration_run_returns_id() -> None:
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[42])
    result = create_orchestration_run(mock_conn, "/data/finance/loans", datetime(2026, 3, 25, 8, 0))
    assert result == 42
    mock_cursor.execute.assert_called_once()
    mock_conn.commit.assert_called_once()


def test_create_orchestration_run_uses_expiry_sentinel() -> None:
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[1])
    create_orchestration_run(mock_conn, "/data/finance/loans", datetime.now())
    call_args = mock_cursor.execute.call_args[0][1]  # (sql, params) — params is second arg
    assert EXPIRY_SENTINEL in call_args


def test_finalize_orchestration_run_completed_on_zero_failures() -> None:
    mock_conn, mock_cursor = make_mock_conn()
    finalize_orchestration_run(mock_conn, 42, datetime.now(), None, None, 0, None)
    call_args = mock_cursor.execute.call_args[0][1]
    assert "completed" in call_args


def test_finalize_orchestration_run_completed_with_errors_on_failures() -> None:
    mock_conn, mock_cursor = make_mock_conn()
    finalize_orchestration_run(mock_conn, 42, datetime.now(), 10, 8, 2, "path /x failed")
    call_args = mock_cursor.execute.call_args[0][1]
    assert "completed_with_errors" in call_args


def test_get_connection_uses_database_url_env(monkeypatch) -> None:
    monkeypatch.setenv("DATABASE_URL", "postgresql://user:pass@myhost:5432/mydb")
    with patch("psycopg2.connect") as mock_connect:
        mock_connect.return_value = MagicMock()
        from orchestrator.db import get_connection
        get_connection()
        mock_connect.assert_called_once_with("postgresql://user:pass@myhost:5432/mydb")
```

### Python Conventions (dqs-orchestrator)

Per `project-context.md`:
- Type hints on all function parameters and return types
- `snake_case` functions, variables, module names
- `list[str] | None` and `int | None` union syntax (Python 3.12+)
- Module-level `logger = logging.getLogger(__name__)` — already in `db.py`, keep it
- No `print()` — use `logger`
- Relative imports within the package: `from orchestrator.db import ...`

### `orchestrator.yaml` — `database.url` Config Key

The `orchestrator.yaml` already has the `database` section:
```yaml
database:
  url: postgresql://postgres:localdev@localhost:5432/postgres
```

In `cli.main()`, extract: `orchestrator_config.get("database", {}).get("url")` and fall back to `os.getenv("DATABASE_URL", ...)`. Pass this URL to `get_connection()` or set `DATABASE_URL` env var before calling it. The simplest approach is to call `get_connection()` directly since it reads `DATABASE_URL` env or the default — but for config-file-driven URL, temporarily set env or update `get_connection()` signature to accept optional dsn.

**Recommended**: Update `get_connection()` to accept an optional `dsn: str | None = None` parameter:
```python
def get_connection(dsn: str | None = None) -> Any:
    resolved_dsn = dsn or os.getenv("DATABASE_URL", "postgresql://postgres:localdev@localhost:5432/postgres")
    return psycopg2.connect(resolved_dsn)
```

Then in `cli.main()`:
```python
db_url = orchestrator_config.get("database", {}).get("url")  # None if key missing — get_connection falls back
# Pass db_url to create_orchestration_run / finalize_orchestration_run calls via get_connection(db_url)
```

### Dependencies — No New Packages Needed

`psycopg2-binary` already in `pyproject.toml`. No `uv add` needed.

### Test Run Commands

```bash
# db tests only
cd /home/sas/workspace/data-quality-service/dqs-orchestrator
uv run pytest tests/test_db.py -v

# runner tests (should still pass — additive changes only)
uv run pytest tests/test_runner.py -v

# full regression (must pass)
uv run pytest -v
```

Current test baseline (post-Story 3.2): 28 tests passing (19 `test_cli.py` + 7 `test_runner.py` + 1 `test_db.py` placeholder + 1 `test_email.py` placeholder). After this story: `test_db.py` placeholder replaced with ~5 real tests, `test_runner.py` gains ~2 more for orchestration_run_id passthrough. Expected ~35 tests passing.

### Java Test Impact — BatchWriter

`BatchWriter.write()` signature change from `write(ctx, metrics)` to `write(ctx, metrics, orchestrationRunId)` will break existing `BatchWriterTest.java` tests. You must update those test calls to pass `null` as the third argument.

Check: `dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java`

### Logging Pattern

| Event | Level | Message Pattern |
|---|---|---|
| Created run record | INFO | `Created orchestration_run: id=%d parent_path=%s` |
| DB create failure | ERROR | `Failed to create orchestration_run for path=%s: %s` |
| Finalized run record | INFO | `Finalized orchestration_run: id=%d status=%s failed=%d` |
| DB finalize failure | ERROR | `Failed to finalize orchestration_run id=%d: %s` |

### Architecture Constraints

- `dqs-orchestrator` uses **psycopg2** (not SQLAlchemy) — per project-context.md and `pyproject.toml`
- `dq_orchestration_run` write boundary: **only dqs-orchestrator writes this table** — Spark does not write it
- `dq_run.orchestration_run_id` write: **only dqs-spark (BatchWriter)** writes this FK column — wired via CLI arg
- The temporal pattern (EXPIRY_SENTINEL) applies to `dq_orchestration_run` — the INSERT must include `expiry_date = EXPIRY_SENTINEL`
- DB errors are non-fatal for orchestration — spark-submit must proceed even if DB write fails

### Cross-Story Dependency Map

| Story | Relationship to 3.3 |
|---|---|
| 3.2 (runner.py) | Provides `run_all_paths()` and `JobResult` — this story extends both |
| 3.4 (Rerun Management) | Reads `orchestration_run_id` from `dq_run` — needs Story 3.3 to wire the FK |
| 3.5 (Email) | Queries `dq_orchestration_run` for run summary — depends on Story 3.3 data |

### Previous Story Intelligence (Story 3.2)

From Story 3.2 completion notes:
- **`test_cli.py` mock target is `orchestrator.cli.run_all_paths`** (not `orchestrator.runner.run_all_paths`) because cli.py imports it at module level. Any new DB functions imported into `cli.py` must be mocked at `orchestrator.cli.<function_name>` in tests.
- **`subprocess.run()` must be `shell=False`** — never `shell=True`. Already enforced.
- **Full regression required** before marking done — 28 tests currently passing, zero regressions allowed.
- **`args.rerun` is still unused** in Story 3.3 — do not add rerun logic here; Story 3.4 owns that.
- **`JobResult.failed_datasets`** is `list[str]` — populated from Spark stderr parsing (not yet implemented). For Story 3.3, `len(result.failed_datasets)` may be 0 even on failure. Use `0 if result.success else len(result.failed_datasets)` — the `error_message` field carries the failure detail for `error_summary`.

### File Structure

```
dqs-orchestrator/
  src/orchestrator/
    db.py           ← EXTEND: add EXPIRY_SENTINEL, create_orchestration_run(), finalize_orchestration_run()
    runner.py       ← EXTEND: add orchestration_run_id param to run_spark_job() and run_all_paths()
    cli.py          ← EXTEND: wire DB create/finalize calls around run_all_paths()
    models.py       ← DO NOT TOUCH
    email.py        ← DO NOT TOUCH
  tests/
    test_db.py      ← REPLACE placeholder with ~5 real tests
    test_runner.py  ← EXTEND: add ~2 tests for orchestration_run_id passthrough
    test_cli.py     ← MAY UPDATE: mock new DB imports if main() tests break
    test_email.py   ← DO NOT TOUCH

dqs-spark/
  src/main/java/com/bank/dqs/
    DqsJob.java                    ← EXTEND: parse --orchestration-run-id arg, pass to writer.write()
    writer/BatchWriter.java        ← EXTEND: add orchestrationRunId param to write(), set/null in insertDqRun()
  src/test/java/com/bank/dqs/
    writer/BatchWriterTest.java    ← UPDATE: add null as third arg to all writer.write() calls
```

### Project Context Reference

- [Source: `_bmad-output/project-context.md` § Temporal Data Pattern] — EXPIRY_SENTINEL must be a constant, never hardcoded inline
- [Source: `_bmad-output/project-context.md` § Python rules] — type hints, snake_case, psycopg2 for orchestrator
- [Source: `_bmad-output/project-context.md` § Anti-Patterns] — never let DB errors crash the orchestration run
- [Source: `dqs-serve/src/serve/schema/ddl.sql`] — authoritative `dq_orchestration_run` schema
- [Source: `dqs-spark/src/main/java/com/bank/dqs/writer/BatchWriter.java` line ~159] — `TODO(epic-3)` for orchestration_run_id
- [Source: `dqs-orchestrator/src/orchestrator/db.py`] — existing `get_connection()` to extend

### Review Findings

- [x] [Review][Patch] Connection leak in create block — if `create_orchestration_run()` raises after `get_connection()` succeeds, `conn.close()` is skipped [cli.py:109-116]
- [x] [Review][Patch] Connection leak in finalize block — if `finalize_orchestration_run()` raises, `conn.close()` at line 141 is never called [cli.py:130-143]
- [x] [Review][Patch] `NumberFormatException` not wrapped for `--orchestration-run-id` in `DqsJob.java` — `Long.parseLong()` throws bare `NumberFormatException` with no user-friendly message; inconsistent with `--date` handling [DqsJob.java:126]
- [x] [Review][Patch] Missing tests for `--orchestration-run-id` arg parsing in `DqsJobArgParserTest.java` — new arg has zero test coverage for happy path, invalid value, and dangling flag cases [DqsJobArgParserTest.java]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation proceeded cleanly with no major issues. One pre-existing Story 3.2 test (`test_run_all_paths_passes_datasets_to_each_run_spark_job_call`) required updating to include `orchestration_run_id=None` in expected call assertions after `run_all_paths()` was extended.

### Completion Notes List

- Implemented `EXPIRY_SENTINEL`, `create_orchestration_run()`, and `finalize_orchestration_run()` in `db.py`. Extended `get_connection()` to accept optional `dsn` parameter for config-file-driven URL.
- Extended `run_spark_job()` with `orchestration_run_id: int | None = None` and `run_all_paths()` with `orchestration_run_ids: dict[str, int] | None = None` in `runner.py`. `--orchestration-run-id` is appended to spark-submit command when provided.
- Wired `create_orchestration_run()` and `finalize_orchestration_run()` around `run_all_paths()` in `cli.main()`. DB errors are caught and logged at ERROR level without blocking spark-submit. DB URL read from `orchestrator_config["database"]["url"]` with fallback.
- `models.py` not modified — `failed_datasets: list[str]` field sufficient for `len()` count.
- `DqsJob.java`: Added `orchestrationRunId: Long` to `DqsJobArgs` record; added `--orchestration-run-id` case to `parseArgs()` switch; passed `jobArgs.orchestrationRunId()` to `writer.write()`.
- `BatchWriter.java`: Extended `write()` and `insertDqRun()` to accept `Long orchestrationRunId`; replaced `TODO(epic-3) ps.setNull(7, Types.INTEGER)` with conditional `ps.setLong()` / `ps.setNull()`.
- `BatchWriterTest.java`: Updated all `writer.write(ctx, metrics)` calls to `writer.write(ctx, metrics, null)` — 10 call sites updated.
- All 49 Python tests pass (19 new ATDD tests + 30 pre-existing). All 167 Java tests pass. Zero regressions.

### File List

- dqs-orchestrator/src/orchestrator/db.py
- dqs-orchestrator/src/orchestrator/runner.py
- dqs-orchestrator/src/orchestrator/cli.py
- dqs-orchestrator/tests/test_db.py (pre-written ATDD tests — already existed, now passing)
- dqs-orchestrator/tests/test_runner.py (updated call assertion for `orchestration_run_id=None`)
- dqs-spark/src/main/java/com/bank/dqs/DqsJob.java
- dqs-spark/src/main/java/com/bank/dqs/writer/BatchWriter.java
- dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java

## Change Log

- 2026-04-03: Implemented Story 3.3 — orchestration run tracking and error capture. Added `EXPIRY_SENTINEL`, `create_orchestration_run()`, `finalize_orchestration_run()` to `db.py`; extended `runner.py` and `cli.py` with orchestration run ID threading; updated `DqsJob.java` to parse `--orchestration-run-id` and pass to `BatchWriter`; updated `BatchWriter.java` `write()` signature to accept `orchestrationRunId`; updated `BatchWriterTest.java` call sites. 49 Python + 167 Java tests passing, zero regressions.
