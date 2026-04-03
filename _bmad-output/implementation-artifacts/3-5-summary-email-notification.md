# Story 3.5: Summary Email Notification

Status: done
ATDD: 15 failing tests generated (2026-04-03) — TDD red phase complete

## Story

As a **platform operator**,
I want a summary email sent via SMTP after each orchestration run containing run details, pass/fail counts, top failures, and a dashboard link,
So that the SRE team is notified of overnight results without checking the dashboard manually.

## Acceptance Criteria

1. **Given** an orchestration run has completed **When** the orchestrator composes the summary email **Then** it queries Postgres for the run summary: run ID, start/end time, total/passed/failed dataset counts **And** it includes top failures grouped by check type (e.g., "3 volume failures, 2 schema drift") **And** it includes a dashboard link to the summary view **And** it includes actionable rerun commands for failed datasets (e.g., `python -m orchestrator.cli --datasets <name> --rerun`).

2. **Given** the SMTP server is configured **When** the email is sent **Then** it is delivered to the configured SRE distribution list.

3. **Given** the SMTP server is unreachable **When** the email send fails **Then** the error is logged but does not cause the orchestration run to be marked as failed.

## Tasks / Subtasks

- [x] Task 1: Implement `compose_summary_email()` in `email.py` (AC: #1)
  - [x] Replace the `NotImplementedError` placeholder in `dqs-orchestrator/src/orchestrator/email.py`
  - [x] Accept a structured `RunSummary` dataclass (define in `models.py`) — do NOT accept raw `list[dict]`
  - [x] `RunSummary` fields: `run_id: int`, `parent_path: str`, `start_time: datetime`, `end_time: datetime | None`, `total_datasets: int | None`, `passed_datasets: int | None`, `failed_datasets: int`, `error_summary: str | None`, `check_type_failures: dict[str, int]`, `failed_dataset_names: list[str]`, `dashboard_url: str`
  - [x] Email subject: `DQS Run Summary — {parent_path} — {date} — {status}` where status is `PASSED` or `FAILED ({n} failures)`
  - [x] Email body (plain text): run ID, start/end time, total/passed/failed counts, top check type failures (sorted by count desc), rerun commands for each failed dataset, dashboard link
  - [x] Return the composed email body as a plain-text `str`
  - [x] `compose_summary_email` must be a pure function — no side effects, no I/O, fully unit-testable

- [x] Task 2: Add `query_run_summary()` to `db.py` (AC: #1)
  - [x] Define `query_run_summary(conn: Any, orchestration_run_id: int, partition_date: date) -> dict[str, Any]`
  - [x] Query 1: fetch `dq_orchestration_run` row by `id = orchestration_run_id` — columns: `id`, `parent_path`, `run_status`, `start_time`, `end_time`, `total_datasets`, `passed_datasets`, `failed_datasets`, `error_summary`
  - [x] Query 2: fetch failed datasets — `SELECT dataset_name, error_message FROM dq_run WHERE orchestration_run_id = %s AND check_status != 'passed' AND expiry_date = %s` using `EXPIRY_SENTINEL`
  - [x] Query 3: fetch check-type failure counts — `SELECT check_type, COUNT(*) FROM dq_metric_detail WHERE dq_run_id IN (SELECT id FROM dq_run WHERE orchestration_run_id = %s AND check_status != 'passed') AND expiry_date = %s GROUP BY check_type` using `EXPIRY_SENTINEL`
  - [x] Return a dict with keys matching `RunSummary` field names so caller can unpack with `RunSummary(**result)`
  - [x] If orchestration_run_id is not found, return a minimal dict with safe defaults (do not raise — treat as non-fatal)

- [x] Task 3: Add `send_summary_email()` to `email.py` (AC: #2, #3)
  - [x] Define `send_summary_email(subject: str, body: str, smtp_config: dict[str, Any]) -> None`
  - [x] Use Python's stdlib `smtplib.SMTP` — no additional dependencies (psycopg2-binary and pyyaml are already in pyproject.toml; do NOT add third-party email libraries)
  - [x] Use `email.mime.text.MIMEText` and `email.mime.multipart.MIMEMultipart` from stdlib
  - [x] SMTP config keys: `smtp_host` (str), `smtp_port` (int, default 25), `from_address` (str), `to_addresses` (list[str])
  - [x] No SMTP authentication for MVP — plain SMTP on port 25 (internal relay)
  - [x] On `smtplib.SMTPException` or `OSError` (connection refused, host unreachable): log at ERROR level, do NOT re-raise (AC #3: non-fatal)
  - [x] Log at INFO level on successful send: `"Summary email sent to {len(to_addresses)} recipients"`

- [x] Task 4: Wire email into `cli.py` `main()` after finalization (AC: #1, #2, #3)
  - [x] Import `compose_summary_email`, `send_summary_email` from `orchestrator.email`
  - [x] Import `query_run_summary` from `orchestrator.db`
  - [x] Import `RunSummary` from `orchestrator.models`
  - [x] After all `finalize_orchestration_run()` calls complete, for each path that has a `run_id`:
    - Call `query_run_summary(conn, run_id, partition_date)` — non-fatal on error, log and skip
    - Construct `RunSummary(**summary_dict)` with `dashboard_url` from `orchestrator_config.get("email", {}).get("dashboard_url", "")`
    - Call `compose_summary_email(run_summary)` — returns subject + body
    - Call `send_summary_email(subject, body, smtp_config)` — non-fatal on SMTP error (already handled inside)
  - [x] `compose_summary_email` returns a tuple `(subject: str, body: str)` — update Task 1 signature accordingly
  - [x] Email is skipped (not an error) when: `email` key missing from orchestrator config, `smtp_host` is missing/empty, or `to_addresses` is empty
  - [x] Any exception in the email block must not change `sys.exit(1)` logic — email errors never cause non-zero exit

- [x] Task 5: Add `dashboard_url` to `orchestrator.yaml` config (AC: #1)
  - [x] Add `dashboard_url: http://localhost:5173/summary` under the `email:` block in `dqs-orchestrator/config/orchestrator.yaml`
  - [x] This is a dev default — production operators override it in their deployment config

- [x] Task 6: Update `models.py` — add `RunSummary` dataclass (AC: #1)
  - [x] Add `RunSummary` dataclass to `dqs-orchestrator/src/orchestrator/models.py`
  - [x] Fields as specified in Task 1 with appropriate type hints and `field(default_factory=list)` / `field(default_factory=dict)` for mutable defaults
  - [x] Keep existing `JobResult` dataclass unchanged

- [x] Task 7: Implement `tests/test_email.py` — replace placeholder (AC: #1, #2, #3)
  - [x] Remove the `test_placeholder()` stub
  - [x] `test_compose_summary_email_includes_run_id()` — verify run ID appears in email body
  - [x] `test_compose_summary_email_includes_pass_fail_counts()` — verify total/passed/failed counts in body
  - [x] `test_compose_summary_email_includes_check_type_failures()` — verify check type failure grouping in body (e.g., "3 volume", "2 schema")
  - [x] `test_compose_summary_email_includes_rerun_commands()` — verify `python -m orchestrator.cli --datasets <name> --rerun` appears for each failed dataset
  - [x] `test_compose_summary_email_includes_dashboard_link()` — verify dashboard URL appears in body
  - [x] `test_compose_summary_email_subject_passes_status()` — verify subject contains "PASSED" when `failed_datasets=0`, "FAILED (N failures)" otherwise
  - [x] `test_send_summary_email_calls_smtp()` — mock `smtplib.SMTP`, verify `sendmail()` called with correct from/to addresses
  - [x] `test_send_summary_email_non_fatal_on_smtp_exception()` — mock `smtplib.SMTP` to raise `smtplib.SMTPException`, verify no exception propagates
  - [x] `test_send_summary_email_non_fatal_on_connection_refused()` — mock to raise `OSError`, verify no exception propagates

- [x] Task 8: Add `test_db.py` additions — `query_run_summary` tests (AC: #1)
  - [x] `test_query_run_summary_returns_orchestration_run_data()` — mock cursor returns a `dq_orchestration_run` row, verify fields in returned dict
  - [x] `test_query_run_summary_returns_failed_datasets()` — mock returns `dq_run` rows with failed check_status, verify `failed_dataset_names` in result
  - [x] `test_query_run_summary_returns_check_type_failure_counts()` — mock returns grouped counts, verify `check_type_failures` dict

- [x] Task 9: Add `test_cli.py` additions — email integration tests (AC: #1, #2, #3)
  - [x] `test_main_calls_send_summary_email_after_finalize()` — mock `query_run_summary` + `compose_summary_email` + `send_summary_email`, verify `send_summary_email` called once per run_id
  - [x] `test_main_email_error_does_not_affect_exit_code()` — mock `send_summary_email` to raise, verify `main()` still exits 0 on success run
  - [x] `test_main_skips_email_when_no_smtp_config()` — config without `email` key, verify `send_summary_email` not called

- [x] Task 10: Full regression — all Python tests must pass
  - [x] `uv run pytest -v` from `dqs-orchestrator/` — 66 baseline tests + new tests must all pass
  - [x] Confirm test count is 66 baseline + new (from previous story) + story 3-5 additions

## Dev Notes

### File Locations (Authoritative)

```
dqs-orchestrator/
  src/orchestrator/
    email.py      ← Task 1, Task 3 (replace placeholder, add send_summary_email)
    db.py         ← Task 2 (add query_run_summary)
    models.py     ← Task 6 (add RunSummary dataclass)
    cli.py        ← Task 4 (wire email after finalization loop)
  config/
    orchestrator.yaml  ← Task 5 (add dashboard_url)
  tests/
    test_email.py  ← Task 7 (replace placeholder with full test suite)
    test_db.py     ← Task 8 (add query_run_summary tests)
    test_cli.py    ← Task 9 (add email wiring tests)
```

**No new files needed.** All code goes into existing files. Do NOT create new modules.

### `email.py` — Authoritative Design

The current placeholder is:

```python
"""Summary email composer — placeholder for story 1-3+.

Categorizes parent path failures vs dataset failures with rerun commands.
"""
from typing import Any


def compose_summary_email(results: list[dict[str, Any]]) -> str:
    """Compose a summary email body from job results."""
    # TODO: implement email composition with failure categorization
    raise NotImplementedError("email.compose_summary_email not yet implemented")
```

Replace with the full implementation. The new signature is:

```python
from orchestrator.models import RunSummary

def compose_summary_email(run_summary: RunSummary) -> tuple[str, str]:
    """Compose subject and body for summary email.

    Returns (subject, body) tuple — pure function, no I/O.
    """
```

Email body format (plain text):

```
DQS Orchestration Run Summary
==============================
Run ID      : {run_id}
Parent Path : {parent_path}
Date        : {partition_date}
Start Time  : {start_time}
End Time    : {end_time or 'N/A'}

Results
-------
Total Datasets  : {total_datasets or 'N/A'}
Passed Datasets : {passed_datasets or 'N/A'}
Failed Datasets : {failed_datasets}

Top Failures by Check Type
--------------------------
{check_type}: {count} failure(s)
...

Failed Datasets — Rerun Commands
---------------------------------
python -m orchestrator.cli --datasets {name} --rerun
...

Dashboard
---------
{dashboard_url}
```

### `send_summary_email()` — Authoritative Design

```python
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

def send_summary_email(subject: str, body: str, smtp_config: dict[str, Any]) -> None:
    """Send summary email via SMTP. Non-fatal on all errors."""
    smtp_host = smtp_config.get("smtp_host", "")
    smtp_port = int(smtp_config.get("smtp_port", 25))
    from_addr = smtp_config.get("from_address", "")
    to_addrs = smtp_config.get("to_addresses", [])

    if not smtp_host or not to_addrs:
        logger.warning("SMTP not configured — skipping summary email")
        return

    try:
        msg = MIMEMultipart()
        msg["Subject"] = subject
        msg["From"] = from_addr
        msg["To"] = ", ".join(to_addrs)
        msg.attach(MIMEText(body, "plain"))

        with smtplib.SMTP(smtp_host, smtp_port) as smtp:
            smtp.sendmail(from_addr, to_addrs, msg.as_string())
        logger.info("Summary email sent to %d recipients", len(to_addrs))
    except (smtplib.SMTPException, OSError) as exc:
        logger.error("Failed to send summary email: %s", exc)
```

**Key design decisions:**
- `smtplib` is Python stdlib — zero new dependencies
- `with smtplib.SMTP(...) as smtp:` handles `smtp.quit()` automatically
- Both `smtplib.SMTPException` and `OSError` caught — `OSError` covers connection refused, host unreachable
- Non-fatal by design: error logged at ERROR but never re-raised (AC #3)

### `query_run_summary()` — Authoritative Design

```python
def query_run_summary(
    conn: Any,
    orchestration_run_id: int,
    partition_date: date,
) -> dict[str, Any]:
    """Query Postgres for run summary data for email composition.

    Returns dict with keys matching RunSummary fields.
    Returns safe defaults if orchestration_run_id not found.
    """
```

Three separate queries:

**Query 1 — orchestration run row:**
```sql
SELECT id, parent_path, run_status, start_time, end_time,
       total_datasets, passed_datasets, failed_datasets, error_summary
FROM dq_orchestration_run
WHERE id = %s
```

**Query 2 — failed dataset names:**
```sql
SELECT dataset_name
FROM dq_run
WHERE orchestration_run_id = %s
  AND check_status != 'passed'
  AND expiry_date = %s
```
Use `EXPIRY_SENTINEL` — only query active records.

**Query 3 — check type failure counts:**
```sql
SELECT d.check_type, COUNT(*) as failure_count
FROM dq_metric_detail d
JOIN dq_run r ON r.id = d.dq_run_id
WHERE r.orchestration_run_id = %s
  AND r.check_status != 'passed'
  AND d.expiry_date = %s
GROUP BY d.check_type
ORDER BY failure_count DESC
```
Use `EXPIRY_SENTINEL` for `d.expiry_date`.

**Note on check_type grouping:** `dq_metric_detail` has `check_type` column. The AC requires failures "grouped by check type (e.g., '3 volume failures, 2 schema drift')". `dq_metric_detail.check_type` values match the check names used in Spark (e.g., `freshness`, `volume`, `schema`, `ops`).

### `RunSummary` Dataclass (models.py)

```python
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional

@dataclass
class RunSummary:
    """Summary data for composing the post-run email notification."""
    run_id: int
    parent_path: str
    start_time: datetime
    end_time: Optional[datetime]
    failed_datasets: int
    dashboard_url: str
    total_datasets: Optional[int] = None
    passed_datasets: Optional[int] = None
    error_summary: Optional[str] = None
    check_type_failures: dict[str, int] = field(default_factory=dict)
    failed_dataset_names: list[str] = field(default_factory=list)
```

### `cli.py` Email Wiring (Authoritative Pattern)

Insert this block AFTER the finalization loop and BEFORE the `succeeded`/`failed` count logging:

```python
# Send summary email per path (non-fatal — email errors never affect exit code)
email_config = orchestrator_config.get("email", {})
if email_config.get("smtp_host") and email_config.get("to_addresses"):
    for result in results:
        run_id = orchestration_run_ids.get(result.parent_path)
        if run_id is None:
            continue
        try:
            conn = get_connection(db_url)
            try:
                summary_dict = query_run_summary(conn, run_id, partition_date)
            finally:
                conn.close()
            summary_dict["dashboard_url"] = email_config.get("dashboard_url", "")
            run_summary = RunSummary(**summary_dict)
            subject, body = compose_summary_email(run_summary)
            send_summary_email(subject, body, email_config)
        except Exception as exc:  # noqa: BLE001
            logger.error("Failed to send summary email for path=%s: %s", result.parent_path, exc)
```

**Critical constraints:**
- Email block is entirely non-fatal — all exceptions caught with `BLE001` suppression
- Email block runs AFTER `finalize_orchestration_run()` for ALL paths (not interleaved)
- `sys.exit(1)` logic is based on `failed` count, computed AFTER the email block — email errors cannot affect it
- Skip email silently when `smtp_host` or `to_addresses` is missing from config

### `orchestrator.yaml` Addition

```yaml
email:
  smtp_host: localhost
  smtp_port: 25
  from_address: dqs-alerts@example.com
  to_addresses:
    - data-engineering@example.com
  dashboard_url: http://localhost:5173/summary  # ← ADD THIS LINE
```

### DB Schema Reference (Unchanged from Story 3-4)

```sql
-- dq_orchestration_run — queried by query_run_summary()
CREATE TABLE dq_orchestration_run (
    id               SERIAL PRIMARY KEY,
    parent_path      TEXT NOT NULL,
    run_status       TEXT NOT NULL,
    start_time       TIMESTAMP,
    end_time         TIMESTAMP,
    total_datasets   INTEGER,
    passed_datasets  INTEGER,
    failed_datasets  INTEGER,
    error_summary    TEXT,
    create_date      TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date      TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'
);

-- dq_run — used to find failed datasets
-- check_status values: 'passed', 'failed', 'error'
-- orchestration_run_id FK back to dq_orchestration_run.id

-- dq_metric_detail — has check_type column for failure grouping
-- check_type values (from Spark checks): 'freshness', 'volume', 'schema', 'ops', 'dqs_score'
```

### Test Patterns — Consistency with Existing Tests

Follow patterns from `tests/test_db.py`:

```python
def make_mock_conn(fetchone_return=None):
    """Return a mock psycopg2 connection with cursor context manager support."""
    mock_cursor = MagicMock()
    mock_cursor.fetchone.return_value = fetchone_return if fetchone_return is not None else [42]
    mock_cursor.__enter__ = lambda s: mock_cursor
    mock_cursor.__exit__ = MagicMock(return_value=False)
    mock_conn = MagicMock()
    mock_conn.cursor.return_value = mock_cursor
    return mock_conn, mock_cursor
```

For `query_run_summary` tests, you need `mock_cursor.fetchall.return_value` for the list-returning queries.

For `send_summary_email` tests, patch `smtplib.SMTP`:

```python
from unittest.mock import MagicMock, patch

def test_send_summary_email_calls_smtp():
    from orchestrator.email import send_summary_email
    with patch("orchestrator.email.smtplib.SMTP") as mock_smtp_cls:
        mock_smtp = MagicMock()
        mock_smtp_cls.return_value.__enter__ = lambda s: mock_smtp
        mock_smtp_cls.return_value.__exit__ = MagicMock(return_value=False)
        send_summary_email("Subject", "Body", {
            "smtp_host": "localhost", "smtp_port": 25,
            "from_address": "from@example.com", "to_addresses": ["to@example.com"],
        })
        mock_smtp.sendmail.assert_called_once()
```

### SMTP Import Collision Warning

Python's stdlib `email` package and the orchestrator module `orchestrator.email` both use the name `email`. In `email.py`:

```python
import smtplib
from email.mime.multipart import MIMEMultipart  # stdlib email package
from email.mime.text import MIMEText
```

In `cli.py`, when importing from the orchestrator module:

```python
from orchestrator.email import compose_summary_email, send_summary_email
```

**This works correctly** because `from orchestrator.email import ...` uses the full package path. Inside `email.py` itself, `from email.mime.text import MIMEText` resolves to the stdlib because relative imports in `email.py` do NOT shadow the stdlib (Python resolves absolute imports by default). Do NOT add `from __future__ import annotations` — this is Python 3.12+ which has no conflict.

### Previous Story Baseline (Story 3-4 Complete)

From story 3-4, the baseline is **66 Python tests passing**. The test distribution as of story 3-4:
- `tests/test_cli.py` — ~33 tests (including rerun tests)
- `tests/test_db.py` — ~22 tests (including expire/rerun tests)
- `tests/test_runner.py` — ~10 tests
- `tests/test_email.py` — 1 placeholder test

This story replaces the 1 placeholder with ~9 new email tests, adds ~3 db tests, and ~3 cli tests — expect ~78+ tests total after story 3-5.

### Non-Goals (Do NOT implement in this story)

- HTML email formatting — plain text only for MVP
- SMTP authentication (TLS, STARTTLS, username/password) — not required for internal relay
- Email templates or Jinja2 — plain string formatting only
- Retry logic for failed SMTP sends — non-fatal single attempt only
- Per-dataset granular check results in email — top-level failure grouping by check type only
- Any changes to dqs-spark, dqs-serve, dqs-dashboard — this story is orchestrator-only

### EXPIRY_SENTINEL Usage

In `query_run_summary()`, use the constant from `db.py`:

```python
from orchestrator.db import EXPIRY_SENTINEL

# In SQL parameters:
cur.execute(sql_failed_datasets, (orchestration_run_id, EXPIRY_SENTINEL))
```

Never hardcode `'9999-12-31 23:59:59'` inline — always use `EXPIRY_SENTINEL`.

### Logging Levels

Follow the established pattern from `cli.py` and `db.py`:
- `ERROR` — email send failure, DB query failure in summary fetch
- `WARNING` — SMTP not configured, skipping email
- `INFO` — successful email send with recipient count
- `DEBUG` — (not required for this story)

Always include relevant context: `path=%s`, `run_id=%d` in log messages.

## Dev Agent Record

### Implementation Plan

Implemented all tasks in a single session following the red-green-refactor cycle. All 15 ATDD failing tests were pre-written; implementation drove them green.

### Completion Notes

- Task 6 (models.py): Added `RunSummary` dataclass with all required fields, mutable defaults using `field(default_factory=...)`, keeping `JobResult` unchanged.
- Task 1 (email.py compose): Replaced `NotImplementedError` placeholder with `compose_summary_email(run_summary: RunSummary) -> tuple[str, str]`. Pure function returning `(subject, body)`. Subject format: `DQS Run Summary — {path} — {date} — PASSED|FAILED (N failures)`. Body includes run ID, times, counts, sorted check-type failures, rerun commands per failed dataset, dashboard URL.
- Task 3 (email.py send): Added `send_summary_email()` using stdlib `smtplib` and `email.mime.*`. Non-fatal on `SMTPException` and `OSError`. Used `import smtplib` at module level to avoid import collision with stdlib `email` package.
- Task 2 (db.py): Added `query_run_summary()` with 3 queries: orchestration run row, failed dataset names (Query 2), check-type counts via JOIN (Query 3). Uses `EXPIRY_SENTINEL` for active-record filtering. Returns safe defaults if run_id not found.
- Task 5 (orchestrator.yaml): Added `dashboard_url: http://localhost:5173/summary` under `email:` block.
- Task 4 (cli.py): Added imports for `query_run_summary`, `compose_summary_email`, `send_summary_email`, `RunSummary`. Wired email block after finalization loop, guarded by `smtp_host` and `to_addresses` presence check. All exceptions in the block caught with `BLE001` suppression — email errors never affect `sys.exit(1)` logic.
- Tasks 7-9 (tests): Tests were pre-generated in TDD red phase. All 15 new tests passed immediately after implementation.
- Task 10: Full regression — 80 tests pass (66 baseline + 14 net new from story 3-5, replacing 1 placeholder with 9 email tests + 3 db tests + 3 cli tests).

## File List

- `dqs-orchestrator/src/orchestrator/models.py` — added `RunSummary` dataclass
- `dqs-orchestrator/src/orchestrator/email.py` — replaced placeholder; implemented `compose_summary_email()` and `send_summary_email()`
- `dqs-orchestrator/src/orchestrator/db.py` — added `query_run_summary()`
- `dqs-orchestrator/src/orchestrator/cli.py` — added imports; wired email block after finalization loop
- `dqs-orchestrator/config/orchestrator.yaml` — added `dashboard_url` under `email:` block

## Change Log

- 2026-04-03: Story 3-5 implemented — summary email notification feature complete. Added `RunSummary` dataclass, `compose_summary_email()`, `send_summary_email()`, `query_run_summary()`, wired email block into CLI. All 15 ATDD tests now pass; 80 total tests passing (0 regressions).
- 2026-04-03: Code review complete — 2 findings fixed, 0 deferred, 0 dismissed.

### Review Findings

- [x] [Review][Patch] Unsorted import block in cli.py [dqs-orchestrator/src/orchestrator/cli.py:2] — fixed by ruff --fix (isort I001)
- [x] [Review][Patch] Unsorted import block in test_email.py [dqs-orchestrator/tests/test_email.py:14] — fixed by ruff --fix (isort I001)
- [x] [Review][Patch] Type mismatch: RunSummary.start_time typed as non-optional datetime but query_run_summary safe-defaults path returns None [dqs-orchestrator/src/orchestrator/models.py:21] — fixed by changing start_time type to Optional[datetime]
