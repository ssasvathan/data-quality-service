# Story 3.4: Rerun Management with Metric Expiration

Status: done

## Story

As a **platform operator**,
I want to rerun specific datasets with incrementing rerun_number and automatic expiration of previous metrics,
So that I can fix failures without losing audit trail of the original run.

## Acceptance Criteria

1. **Given** a dataset that was previously processed with `rerun_number=1` **When** a rerun is triggered via `--datasets ue90-omni-transactions --rerun` **Then** the orchestrator passes a `--datasets` filter to spark-submit so only the specified datasets are processed **And** the new `dq_run` record has `rerun_number=2`.

2. **Given** a rerun is triggered for a dataset **When** the Spark job creates new results **Then** the previous `dq_run`'s `expiry_date` is set to the current timestamp (expired) **And** all associated `dq_metric_numeric` and `dq_metric_detail` records from the previous run are also expired **And** active-record views only return the new rerun's data.

3. **Given** a rerun with `rerun_number=3` **When** I query the database directly (not via views) **Then** I can see all 3 historical run records with their respective `expiry_dates` for full audit trail.

## Tasks / Subtasks

- [x] Task 1: Add `expire_previous_run()` function to `db.py` in dqs-orchestrator (AC: #2)
  - [x] Define `expire_previous_run(conn: Any, dataset_name: str, partition_date: date, current_run_expiry: datetime) -> int | None` — returns the `rerun_number` of the expired run (or `None` if no active run found)
  - [x] SQL: UPDATE `dq_run` SET `expiry_date = current_run_expiry` WHERE `dataset_name = %s AND partition_date = %s AND expiry_date = EXPIRY_SENTINEL` — use `RETURNING id, rerun_number`
  - [x] SQL: UPDATE `dq_metric_numeric` SET `expiry_date = current_run_expiry` WHERE `dq_run_id = <returned_id>` AND `expiry_date = EXPIRY_SENTINEL`
  - [x] SQL: UPDATE `dq_metric_detail` SET `expiry_date = current_run_expiry` WHERE `dq_run_id = <returned_id>` AND `expiry_date = EXPIRY_SENTINEL`
  - [x] All three UPDATEs must execute within one transaction (`conn.commit()` after all three)
  - [x] Use `EXPIRY_SENTINEL` constant — never hardcode `9999-12-31 23:59:59`
  - [x] If no active run found for the dataset, return `None` without error (first-time run, not an error)

- [x] Task 2: Add `get_next_rerun_number()` helper to `db.py` (AC: #1)
  - [x] Define `get_next_rerun_number(conn: Any, dataset_name: str, partition_date: date) -> int` — queries MAX(rerun_number) across ALL runs (including expired) for this dataset+date and returns MAX + 1
  - [x] SQL: `SELECT COALESCE(MAX(rerun_number), -1) + 1 FROM dq_run WHERE dataset_name = %s AND partition_date = %s`
  - [x] Returns `0` when no previous runs exist (first-time run; `COALESCE(-1+1 = 0)`)
  - [x] Returns `1` when one run exists with `rerun_number=0` (first rerun)
  - [x] This function is called by `BatchWriter` via a new `--rerun-number` CLI arg — see Task 4

- [x] Task 3: Wire rerun logic into `cli.py` `main()` when `--rerun` flag is set (AC: #1, #2)
  - [x] Import `expire_previous_run` from `orchestrator.db`
  - [x] After `run_all_paths()` returns but ONLY when `args.rerun is True` AND `args.datasets is not None`:
    - For each dataset in `args.datasets`, call `expire_previous_run(conn, dataset, partition_date, datetime.now())` — store the returned `rerun_number`
  - [x] Compute `next_rerun_number = (rerun_number or 0) + 1` per dataset
  - [x] Pass the `rerun_number` per dataset to `run_all_paths()` as a new `rerun_numbers: dict[str, int] | None` param (see Task 3b)
  - **IMPORTANT:** The orchestrator must expire BEFORE calling `run_all_paths()` so the Spark job writes the new run as `rerun_number = N` (not a conflicting duplicate). Execution order:
    1. `expire_previous_run()` for each dataset when `--rerun` is set
    2. `run_all_paths()` with `rerun_numbers` dict passed through
  - [x] DB errors in `expire_previous_run` are non-fatal — log at ERROR level and continue with `rerun_number = 0` (safe default)

- [x] Task 3b: Extend `runner.py` to pass `--rerun-number` per dataset to spark-submit (AC: #1)
  - [x] Add optional `rerun_number: int | None = None` parameter to `run_spark_job()`
  - [x] When `rerun_number is not None`, append `["--rerun-number", str(rerun_number)]` to the spark-submit command
  - [x] Add optional `rerun_numbers: dict[str, int] | None = None` to `run_all_paths()` — look up per path or per dataset
  - **Note:** `rerun_number` applies at the dataset level, not the parent-path level. Since orchestrator currently passes `--datasets` to filter to specific datasets within a parent path, the `rerun_number` applies to all datasets in that `--datasets` filter. Pass `rerun_number` as a single int to `run_spark_job()` — the Spark side applies it to each matching dataset.

- [x] Task 4: Extend `BatchWriter.java` to accept and use `rerunNumber` (AC: #1)
  - [x] Remove the `TODO(story-3-4)` placeholder at line 157: `ps.setInt(6, 0);`
  - [x] Extend `write(DatasetContext ctx, List<DqMetric> metrics, Long orchestrationRunId)` — add `int rerunNumber` as 4th parameter: `write(DatasetContext ctx, List<DqMetric> metrics, Long orchestrationRunId, int rerunNumber)`
  - [x] In `insertDqRun()`, replace `ps.setInt(6, 0)` with `ps.setInt(6, rerunNumber)`
  - [x] Update `DqsJob.java` `main()` to parse `--rerun-number` CLI arg and pass it to `writer.write()`
  - [x] Update all `BatchWriterTest.java` call sites from `writer.write(ctx, metrics, null)` to `writer.write(ctx, metrics, null, 0)` (add `0` as fourth arg — keeps existing tests green)
  - **UNIQUE CONSTRAINT IMPACT:** `dq_run` has `UNIQUE(dataset_name, partition_date, rerun_number, expiry_date)`. The orchestrator MUST expire the previous run (Task 1) before the Spark job inserts `rerun_number=N`. If the orchestrator fails to expire first, the Spark INSERT will fail with a unique constraint violation.

- [x] Task 5: Extend `DqsJob.java` to parse `--rerun-number` CLI arg (AC: #1)
  - [x] Add `--rerun-number` to the `parseArgs()` switch block
  - [x] Add `int rerunNumber` (default `0`) field to `DqsJobArgs` record: `record DqsJobArgs(String parentPath, LocalDate partitionDate, Long orchestrationRunId, int rerunNumber)`
  - [x] In `main()`, pass `jobArgs.rerunNumber()` to `writer.write(ctx, datasetMetrics, jobArgs.orchestrationRunId(), jobArgs.rerunNumber())`
  - [x] Update `DqsJobArgParserTest.java` to cover: happy path `--rerun-number 2`, default value `0`, invalid value throws `IllegalArgumentException`, dangling flag throws `IllegalArgumentException`

- [x] Task 6: Write `tests/test_db.py` additions — add rerun tests (AC: #1, #2, #3)
  - [x] Add `test_expire_previous_run_updates_expiry_date_on_dq_run()` — mock returns a `dq_run.id` row, verify UPDATE executed with correct params
  - [x] Add `test_expire_previous_run_cascades_to_metric_numeric()` — mock returns a `dq_run.id`, verify `dq_metric_numeric` UPDATE is also executed
  - [x] Add `test_expire_previous_run_cascades_to_metric_detail()` — verify `dq_metric_detail` UPDATE is also executed
  - [x] Add `test_expire_previous_run_returns_none_when_no_active_run()` — mock cursor returns no rows, verify returns `None` without raising
  - [x] Add `test_get_next_rerun_number_returns_zero_when_no_history()` — mock returns `0`, verify result is `0`
  - [x] Add `test_get_next_rerun_number_returns_one_when_first_run_exists()` — mock returns `1`, verify result is `1`

- [x] Task 7: Write `tests/test_cli.py` additions (AC: #1, #2)
  - [x] Add `test_main_calls_expire_previous_run_when_rerun_flag_set()` — verifies `expire_previous_run` is called for each dataset in `args.datasets` when `--rerun` is set
  - [x] Add `test_main_does_not_call_expire_previous_run_when_rerun_not_set()` — verifies no expiration call on normal (non-rerun) runs
  - [x] Add `test_main_expire_error_is_non_fatal()` — `expire_previous_run` raises, spark-submit still proceeds

- [x] Task 8: Full regression — all 49 Python tests + new tests must pass; all 171 Java tests + new tests must pass
  - [x] `uv run pytest -v` from `dqs-orchestrator/` — 66 tests pass (49 baseline + 17 new)
  - [x] `mvn test` from `dqs-spark/` — 177 tests pass (171 baseline + 6 new)

## Dev Notes

### DB Schema Reference (Authoritative — `dqs-serve/src/serve/schema/ddl.sql`)

```sql
CREATE TABLE dq_run (
    id                   SERIAL PRIMARY KEY,
    dataset_name         TEXT NOT NULL,
    partition_date       DATE NOT NULL,
    lookup_code          TEXT,
    check_status         TEXT NOT NULL,
    dqs_score            NUMERIC(5,2),
    rerun_number         INTEGER NOT NULL DEFAULT 0,
    orchestration_run_id INTEGER,
    error_message        TEXT,
    create_date          TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date          TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dq_run_dataset_name_partition_date_rerun_number_expiry_date
        UNIQUE (dataset_name, partition_date, rerun_number, expiry_date)
);
```

Key facts:
- `rerun_number` defaults to `0` — first run is always `rerun_number=0`
- UNIQUE constraint is on `(dataset_name, partition_date, rerun_number, expiry_date)` — NOT just `(dataset_name, partition_date, expiry_date)`
- This means the orchestrator MUST expire the old run BEFORE Spark inserts the new one, because:
  - Old run has `expiry_date = EXPIRY_SENTINEL` and `rerun_number = 0`
  - New run will have `expiry_date = EXPIRY_SENTINEL` and `rerun_number = 1`
  - They are different `rerun_number` values, so technically no UNIQUE violation…
  - BUT the ACTIVE-RECORD VIEWS filter on `expiry_date = EXPIRY_SENTINEL` — if old run is not expired, BOTH old and new run appear in `v_dq_run_active` — which violates the "only show current run" intent
  - Therefore: ALWAYS expire before inserting the new run

```sql
CREATE TABLE dq_metric_numeric (
    id           SERIAL PRIMARY KEY,
    dq_run_id    INTEGER NOT NULL,
    ...
    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dq_metric_numeric_dq_run_id_check_type_metric_name_expiry_date
        UNIQUE (dq_run_id, check_type, metric_name, expiry_date)
);

CREATE TABLE dq_metric_detail (
    id           SERIAL PRIMARY KEY,
    dq_run_id    INTEGER NOT NULL,
    ...
    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dq_metric_detail_dq_run_id_check_type_detail_type_expiry_date
        UNIQUE (dq_run_id, check_type, detail_type, expiry_date)
);
```

Metrics are expired by `dq_run_id` — cascade-expire all metrics for the old run.

### Active-Record Views (from `dqs-serve/src/serve/schema/views.sql`)

```sql
CREATE OR REPLACE VIEW v_dq_run_active AS
    SELECT * FROM dq_run WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_metric_numeric_active AS
    SELECT * FROM dq_metric_numeric WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_metric_detail_active AS
    SELECT * FROM dq_metric_detail WHERE expiry_date = '9999-12-31 23:59:59';
```

After a rerun, `v_dq_run_active` should show ONLY the new `rerun_number=N` row. The old row (now expired) is visible in the raw `dq_run` table for audit trail.

### `db.py` New Functions (Authoritative Design)

```python
from datetime import date, datetime

def expire_previous_run(
    conn: Any,
    dataset_name: str,
    partition_date: date,
    current_run_expiry: datetime,
) -> int | None:
    """Expire the active dq_run for this dataset+date and cascade to metrics.

    Sets expiry_date = current_run_expiry on:
    1. The dq_run row (must have expiry_date = EXPIRY_SENTINEL)
    2. All dq_metric_numeric rows for that dq_run_id
    3. All dq_metric_detail rows for that dq_run_id

    Returns the rerun_number of the expired run (for computing next rerun_number),
    or None if no active run exists (first-time run — not an error).

    All three UPDATEs execute in one transaction (conn.commit() after all three).
    """
    sql_expire_run = """
        UPDATE dq_run
        SET expiry_date = %s
        WHERE dataset_name = %s
          AND partition_date = %s
          AND expiry_date = %s
        RETURNING id, rerun_number
    """
    sql_expire_numeric = """
        UPDATE dq_metric_numeric
        SET expiry_date = %s
        WHERE dq_run_id = %s
          AND expiry_date = %s
    """
    sql_expire_detail = """
        UPDATE dq_metric_detail
        SET expiry_date = %s
        WHERE dq_run_id = %s
          AND expiry_date = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql_expire_run, (current_run_expiry, dataset_name, partition_date, EXPIRY_SENTINEL))
        row = cur.fetchone()
        if row is None:
            conn.commit()
            logger.info(
                "expire_previous_run: no active run found for dataset=%s date=%s — skipping",
                dataset_name, partition_date,
            )
            return None
        run_id, rerun_number = row[0], row[1]

        cur.execute(sql_expire_numeric, (current_run_expiry, run_id, EXPIRY_SENTINEL))
        cur.execute(sql_expire_detail, (current_run_expiry, run_id, EXPIRY_SENTINEL))
        conn.commit()

    logger.info(
        "expire_previous_run: expired dq_run id=%d rerun_number=%d for dataset=%s date=%s",
        run_id, rerun_number, dataset_name, partition_date,
    )
    return rerun_number


def get_next_rerun_number(conn: Any, dataset_name: str, partition_date: date) -> int:
    """Return the next rerun_number for this dataset+date across all history.

    Queries MAX(rerun_number) across ALL dq_run rows (including expired).
    Returns MAX + 1. Returns 0 if no history exists.
    """
    sql = """
        SELECT COALESCE(MAX(rerun_number), -1) + 1
        FROM dq_run
        WHERE dataset_name = %s
          AND partition_date = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (dataset_name, partition_date))
        row = cur.fetchone()
        return row[0] if row else 0
```

**Why COALESCE(MAX, -1) + 1:** When no rows exist, `MAX` returns `NULL`. `COALESCE(NULL, -1)` → `-1`. `-1 + 1 = 0`. So first-time insert gets `rerun_number=0`. After one run exists with `rerun_number=0`: `COALESCE(0, -1) = 0`. `0 + 1 = 1`. Correct.

### `cli.py` Rerun Wiring (Authoritative Pattern)

Insert this block between the `spark_config` extraction and the orchestration_run_id creation block:

```python
from orchestrator.db import expire_previous_run  # add to existing import

# Rerun management: expire previous metrics before spark-submit (non-fatal on error)
rerun_numbers: dict[str, int] = {}  # dataset_name -> next rerun_number
if args.rerun and args.datasets:
    expiry_ts = datetime.now()
    for dataset in args.datasets:
        try:
            conn = get_connection(db_url)
            try:
                prev_rerun = expire_previous_run(conn, dataset, partition_date, expiry_ts)
                rerun_numbers[dataset] = (prev_rerun or 0) + 1
            finally:
                conn.close()
        except Exception as exc:  # noqa: BLE001
            logger.error(
                "Failed to expire previous run for dataset=%s: %s — using rerun_number=0",
                dataset, exc,
            )
            rerun_numbers[dataset] = 0  # safe default: may cause duplicate if old run not expired
```

Pass `rerun_numbers` to `run_all_paths()`:
```python
results = run_all_paths(
    parent_paths,
    partition_date,
    spark_config,
    args.datasets,
    orchestration_run_ids=orchestration_run_ids if orchestration_run_ids else None,
    rerun_numbers=rerun_numbers if rerun_numbers else None,
)
```

**Critical: rerun_numbers has dataset_name keys, not parent_path keys.** The `run_all_paths()` function receives a single `rerun_number` for the entire spark-submit invocation (since `--datasets` filters apply to ALL matched datasets in the parent path). Because `--datasets` is a list of datasets, and all are being rerun together with the same `--rerun-number`, determine the `rerun_number` for the spark-submit call as the MAX across all specified datasets in `rerun_numbers`, or pass the first dataset's `rerun_number` if all are the same.

**Simpler approach:** Since the rerun scenario is `--datasets ue90-omni-transactions --rerun` (typically one dataset at a time), pass the single `rerun_number` via `runner.py`'s new `rerun_number: int | None` parameter. Use `max(rerun_numbers.values()) if rerun_numbers else None` as the single value.

### `runner.py` Extended Signatures

```python
def run_spark_job(
    parent_path: str,
    partition_date: date,
    spark_config: dict[str, Any],
    datasets: list[str] | None = None,
    orchestration_run_id: int | None = None,
    rerun_number: int | None = None,  # NEW in story 3-4
) -> JobResult:
    ...
    if rerun_number is not None:
        cmd.extend(["--rerun-number", str(rerun_number)])
```

```python
def run_all_paths(
    parent_paths: list[str],
    partition_date: date,
    spark_config: dict[str, Any],
    datasets: list[str] | None = None,
    orchestration_run_ids: dict[str, int] | None = None,
    rerun_numbers: dict[str, int] | None = None,  # NEW in story 3-4
) -> list[JobResult]:
    ...
    for path in parent_paths:
        # rerun_number applies per spark-submit call (all datasets in that call share same rerun_number)
        rn = max(rerun_numbers.values()) if rerun_numbers else None
        job_result = run_spark_job(
            path, partition_date, spark_config, datasets,
            orchestration_run_id=orch_id,
            rerun_number=rn,
        )
```

### `BatchWriter.java` Updated Signature

Current (after Story 3-3):
```java
public long write(DatasetContext ctx, List<DqMetric> metrics, Long orchestrationRunId) throws SQLException
```

New (Story 3-4):
```java
public long write(DatasetContext ctx, List<DqMetric> metrics, Long orchestrationRunId, int rerunNumber) throws SQLException
```

Replace the TODO at line 157:
```java
// TODO(story-3-4): increment rerun_number for reruns
ps.setInt(6, 0);
```
With:
```java
ps.setInt(6, rerunNumber);
```

**All existing `BatchWriterTest.java` `writer.write(ctx, metrics, null)` calls must be updated to `writer.write(ctx, metrics, null, 0)`.**

### `DqsJobArgs` Record Update

Current (after Story 3-3):
```java
record DqsJobArgs(String parentPath, LocalDate partitionDate, Long orchestrationRunId) {}
```

New (Story 3-4):
```java
record DqsJobArgs(String parentPath, LocalDate partitionDate, Long orchestrationRunId, int rerunNumber) {}
```

Default `rerunNumber = 0` when `--rerun-number` not provided. Set in the `parseArgs()` switch: initialize `int rerunNumber = 0;` before the loop.

### `DqsJob.main()` — passing rerunNumber

```java
long runId = writer.write(ctx, datasetMetrics, jobArgs.orchestrationRunId(), jobArgs.rerunNumber());
```

### `DqsJobArgParserTest.java` New Tests

Add four tests:
1. `parseArgsExtractsRerunNumber()` — `--rerun-number 2` → `result.rerunNumber() == 2`
2. `parseArgsDefaultsRerunNumberToZeroWhenAbsent()` — no flag → `result.rerunNumber() == 0`
3. `parseArgsThrowsOnInvalidRerunNumberValue()` — `--rerun-number not-a-number` → `IllegalArgumentException`
4. `parseArgsThrowsOnDanglingRerunNumberFlag()` — `--rerun-number` with no value → `IllegalArgumentException`

### Existing File State — What to Touch

| File | Current State | This Story's Action |
|---|---|---|
| `dqs-orchestrator/src/orchestrator/db.py` | `get_connection()`, `EXPIRY_SENTINEL`, `create_orchestration_run()`, `finalize_orchestration_run()` | EXTEND: add `expire_previous_run()`, `get_next_rerun_number()` |
| `dqs-orchestrator/src/orchestrator/cli.py` | Full Story 3-3 impl | EXTEND: add rerun-management block before `run_all_paths()`; pass `rerun_numbers` to `run_all_paths()` |
| `dqs-orchestrator/src/orchestrator/runner.py` | Full Story 3-3 impl | EXTEND: add `rerun_number` to `run_spark_job()`, `rerun_numbers` to `run_all_paths()` |
| `dqs-orchestrator/src/orchestrator/models.py` | `JobResult` dataclass | DO NOT TOUCH |
| `dqs-orchestrator/tests/test_db.py` | 9 real tests from Story 3-3 | EXTEND: add ~6 tests for `expire_previous_run()` and `get_next_rerun_number()` |
| `dqs-orchestrator/tests/test_cli.py` | 19 tests from Story 3-1 + 8 tests from Story 3-3 | EXTEND: add ~3 tests for rerun wiring |
| `dqs-orchestrator/tests/test_runner.py` | 13 tests from Story 3-2+3-3 | EXTEND: add ~2 tests for `rerun_number` passthrough |
| `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` | Parses `--orchestration-run-id`, passes to `writer.write()` | EXTEND: parse `--rerun-number`, pass to `writer.write()` |
| `dqs-spark/src/main/java/com/bank/dqs/writer/BatchWriter.java` | `write(ctx, metrics, orchestrationRunId)`, `ps.setInt(6, 0)` TODO | EXTEND: add `rerunNumber` param, replace TODO with `ps.setInt(6, rerunNumber)` |
| `dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java` | All calls use `writer.write(ctx, metrics, null)` | UPDATE: add `0` as 4th arg to all `write()` calls |
| `dqs-spark/src/test/java/com/bank/dqs/DqsJobArgParserTest.java` | 13 tests covering `--parent-path`, `--date`, `--orchestration-run-id` | EXTEND: add 4 tests for `--rerun-number` |

**DO NOT TOUCH:**
- `dqs-orchestrator/src/orchestrator/email.py` — Story 3.5
- `dqs-orchestrator/tests/test_email.py` — Story 3.5
- `dqs-serve/src/serve/schema/ddl.sql` — schema already has all required columns and constraints
- `dqs-serve/src/serve/schema/views.sql` — active-record views already defined correctly

### Temporal Pattern Compliance Checklist

Per `project-context.md` § Temporal Data Pattern (ALL COMPONENTS):

- Use `EXPIRY_SENTINEL` constant (`"9999-12-31 23:59:59"`) in all SQL — never hardcode
- Soft-delete = UPDATE `expiry_date` FROM sentinel TO current timestamp
- After soft-delete, the new `rerun_number=N` INSERT gets `expiry_date = EXPIRY_SENTINEL` (via DB DEFAULT — no explicit INSERT needed)
- Active-record views (`v_dq_run_active`, `v_dq_metric_numeric_active`, `v_dq_metric_detail_active`) filter on sentinel — they automatically exclude expired rows

### Unique Constraint Enforcement Logic

The UNIQUE constraint on `dq_run` is `(dataset_name, partition_date, rerun_number, expiry_date)`.

Example multi-run scenario for dataset `ue90-omni-transactions` on `2026-03-25`:

| run | rerun_number | expiry_date | visible in v_dq_run_active |
|-----|-------------|-------------|--------------------------|
| Initial run | 0 | `9999-12-31 23:59:59` | YES |
| After 1st rerun: initial row | 0 | `2026-03-25 10:05:00` | NO (expired) |
| After 1st rerun: new row | 1 | `9999-12-31 23:59:59` | YES |
| After 2nd rerun: row with rn=1 | 1 | `2026-03-25 11:00:00` | NO (expired) |
| After 2nd rerun: new row | 2 | `9999-12-31 23:59:59` | YES |

The key insight: the old `rerun_number=0` row has its `expiry_date` changed from sentinel to current timestamp. The new `rerun_number=1` row has `expiry_date = sentinel`. These are different `rerun_number` values, so no UNIQUE violation. But still expire first to keep `v_dq_run_active` clean (only one active row per dataset).

### Python Conventions (dqs-orchestrator)

- Type hints on all function parameters and return types
- `snake_case` functions, variables, module names
- `list[str] | None` and `int | None` union syntax (Python 3.12+)
- Module-level `logger = logging.getLogger(__name__)` — already in `db.py`
- No `print()` — use `logger`
- Relative imports: `from orchestrator.db import expire_previous_run, get_next_rerun_number`
- Mock target in `test_cli.py` for new functions: `orchestrator.cli.expire_previous_run` (because cli.py imports at module level)

### Java Conventions (dqs-spark)

- `rerunNumber` as `int` (not `Integer`) — primitive, default `0`, no null allowed in `DqsJobArgs`
- `ps.setInt(6, rerunNumber)` — positional 6 is `rerun_number` in the INSERT SQL (already defined in `BatchWriter.java`)
- Use `throws SQLException` on method signatures — no catch-and-swallow
- Keep `TODO(story-3-4)` comment removal clean — replace the entire `// TODO(story-3-4)` + `ps.setInt(6, 0)` block with just `ps.setInt(6, rerunNumber)`

### Logging Pattern

| Event | Level | Message Pattern |
|---|---|---|
| No active run found (first run) | INFO | `expire_previous_run: no active run found for dataset=%s date=%s — skipping` |
| Expired previous run | INFO | `expire_previous_run: expired dq_run id=%d rerun_number=%d for dataset=%s date=%s` |
| DB expire failure | ERROR | `Failed to expire previous run for dataset=%s: %s — using rerun_number=0` |

### Cross-Story Dependencies

| Story | Relationship to 3.4 |
|---|---|
| 3.3 (Run Tracking) | Provides `EXPIRY_SENTINEL`, `get_connection()`, `db.py` infrastructure — this story extends it |
| 3.5 (Email) | Queries `dq_orchestration_run` for run summary — the email may reference `rerun_number` from `dq_run` for context |
| 4.x (Dashboard) | `v_dq_run_active` must return only the latest rerun per dataset — this story ensures that |

### Test Baseline and Expected Count

Current baseline: **49 Python tests** (19 `test_cli.py` + 13 `test_runner.py` + 9 `test_db.py` + 7 `test_db.py` ATDD + 1 `test_email.py`) — all passing.

After this story: approximately **~62 Python tests** (49 + ~6 `test_db.py` + ~3 `test_cli.py` + ~2 `test_runner.py` + ~2 extra).

Java: **171 tests** currently. After: approximately **~179** (171 + 4 `DqsJobArgParserTest` + some `BatchWriterTest` updates — not new tests, just updating call args).

### Test Commands

```bash
# Python tests — from dqs-orchestrator/
cd /home/sas/workspace/data-quality-service/dqs-orchestrator
uv run pytest -v

# Java tests — from dqs-spark/
cd /home/sas/workspace/data-quality-service/dqs-spark
mvn test

# DB tests only (fastest check while developing db.py):
cd /home/sas/workspace/data-quality-service/dqs-orchestrator
uv run pytest tests/test_db.py -v
```

### Previous Story Intelligence (Stories 3.1, 3.2, 3.3)

- **`test_cli.py` mock target is `orchestrator.cli.<function>`** — not `orchestrator.db.<function>` — because cli.py imports functions at module level. Any new DB functions imported into `cli.py` (`expire_previous_run`) must be mocked at `orchestrator.cli.expire_previous_run` in tests.
- **`subprocess.run()` must be `shell=False`** — already enforced, do not change.
- **DB errors are non-fatal** — wrap all DB calls in `try/except Exception` and log at ERROR level. spark-submit must proceed regardless.
- **`args.rerun` was intentionally left unused in Story 3.3** — this story activates it.
- **`args.datasets` is `None` when not provided** — always guard: `if args.rerun and args.datasets`.
- **`BatchWriterTest.java` was last updated in Story 3-3** — all `writer.write()` calls already have `null` as 3rd arg (`orchestrationRunId`). Adding 4th arg `0` (`rerunNumber`) is a purely additive call-site change.
- **`get_connection()` accepts optional `dsn: str | None = None`** — introduced in Story 3.3. Use `get_connection(db_url)` pattern from `cli.py`.
- **Connection management pattern from Story 3.3:** Use explicit `conn = get_connection(db_url)` + `try/finally: conn.close()` pattern (NOT `with conn:` context manager) — psycopg2 connections support `with` for transaction management but NOT auto-close.
- **`JobResult.failed_datasets`** is `list[str]` — Story 3.3 confirmed it is NOT populated from Spark exit code yet. Do not rely on its contents for rerun decision.

### File Structure

```
dqs-orchestrator/
  src/orchestrator/
    db.py           ← EXTEND: add expire_previous_run(), get_next_rerun_number()
    runner.py       ← EXTEND: add rerun_number param to run_spark_job(), run_all_paths()
    cli.py          ← EXTEND: add rerun management block; pass rerun_numbers to run_all_paths()
    models.py       ← DO NOT TOUCH
    email.py        ← DO NOT TOUCH
  tests/
    test_db.py      ← EXTEND: add ~6 tests for expire_previous_run() and get_next_rerun_number()
    test_runner.py  ← EXTEND: add ~2 tests for rerun_number passthrough in spark command
    test_cli.py     ← EXTEND: add ~3 tests for rerun wiring in main()
    test_email.py   ← DO NOT TOUCH

dqs-spark/
  src/main/java/com/bank/dqs/
    DqsJob.java                    ← EXTEND: parse --rerun-number, pass to writer.write()
    writer/BatchWriter.java        ← EXTEND: add rerunNumber param, replace TODO with ps.setInt(6, rerunNumber)
  src/test/java/com/bank/dqs/
    DqsJobArgParserTest.java       ← EXTEND: add 4 tests for --rerun-number
    writer/BatchWriterTest.java    ← UPDATE: add 0 as 4th arg to all writer.write(ctx, metrics, null) calls
```

### Project Context Reference

- [Source: `_bmad-output/project-context.md` § Temporal Data Pattern] — `EXPIRY_SENTINEL` constant, soft-delete pattern, active-record views
- [Source: `_bmad-output/project-context.md` § Python rules] — type hints, `snake_case`, psycopg2 for orchestrator, non-fatal DB errors
- [Source: `_bmad-output/project-context.md` § Anti-Patterns] — never hardcode sentinel, never let DB errors crash the orchestration run
- [Source: `dqs-serve/src/serve/schema/ddl.sql`] — authoritative `dq_run`, `dq_metric_numeric`, `dq_metric_detail` schemas with UNIQUE constraints
- [Source: `dqs-serve/src/serve/schema/views.sql`] — active-record view definitions — rerun must maintain view correctness
- [Source: `dqs-orchestrator/src/orchestrator/db.py`] — `EXPIRY_SENTINEL`, `get_connection()`, `create_orchestration_run()`, `finalize_orchestration_run()` patterns to follow
- [Source: `dqs-orchestrator/src/orchestrator/cli.py` lines 107-148] — existing connection management pattern (`get_connection(db_url)` + `try/finally conn.close()`)
- [Source: `dqs-spark/src/main/java/com/bank/dqs/writer/BatchWriter.java` line 157] — `TODO(story-3-4): increment rerun_number for reruns` to remove

## Dev Agent Record

### Implementation Plan

Followed TDD red-green-refactor cycle across all 8 tasks:

1. Extended `db.py` with `expire_previous_run()` and `get_next_rerun_number()` using EXPIRY_SENTINEL and psycopg2 cursor context managers. Handled "no rows" case by checking `not row or len(row) < 2` to account for mock patterns in tests.
2. Extended `runner.py` `run_spark_job()` with optional `rerun_number: int | None = None` parameter; `run_all_paths()` with `rerun_numbers: dict[str, int] | None = None`; uses `max(rerun_numbers.values())` as single spark-submit value.
3. Extended `cli.py` `main()` with rerun management block between orchestration run creation and `run_all_paths()`; imports `expire_previous_run` at module level for mock compatibility.
4. Updated `BatchWriter.java` signature to `write(..., int rerunNumber)`, replaced `TODO(story-3-4)` with `ps.setInt(6, rerunNumber)`.
5. Extended `DqsJob.java`: added `rerunNumber` to `DqsJobArgs` record, added `--rerun-number` case to `parseArgs()` switch, wired `jobArgs.rerunNumber()` to `writer.write()`.
6. Updated 3 pre-existing `test_runner.py` tests to include `rerun_number=None` in expected call assertions (signature change made it a required kwarg in the actual calls).

### Completion Notes

- All 66 Python tests pass (49 baseline + 9 new `test_db.py` + 4 new `test_cli.py` + 4 new `test_runner.py`)
- All 177 Java tests pass (171 baseline + 4 new `DqsJobArgParserTest` + 2 new `BatchWriterTest`)
- `expire_previous_run()` uses 3-UPDATE atomic transaction per temporal pattern spec
- `get_next_rerun_number()` queries all history (no expiry_date filter) using `COALESCE(MAX,-1)+1`
- Non-fatal DB error handling in `cli.py` per project anti-patterns rule

## File List

- `dqs-orchestrator/src/orchestrator/db.py` — added `expire_previous_run()`, `get_next_rerun_number()`; added `date` import
- `dqs-orchestrator/src/orchestrator/runner.py` — added `rerun_number` param to `run_spark_job()`; added `rerun_numbers` param to `run_all_paths()`
- `dqs-orchestrator/src/orchestrator/cli.py` — imported `expire_previous_run`; added rerun management block; passed `rerun_numbers` to `run_all_paths()`
- `dqs-spark/src/main/java/com/bank/dqs/writer/BatchWriter.java` — updated `write()` and `insertDqRun()` signatures to include `int rerunNumber`; replaced `TODO(story-3-4)` with `ps.setInt(6, rerunNumber)`
- `dqs-spark/src/main/java/com/bank/dqs/DqsJob.java` — updated `DqsJobArgs` record to include `int rerunNumber`; added `--rerun-number` case in `parseArgs()`; wired `jobArgs.rerunNumber()` to `writer.write()`
- `dqs-spark/src/test/java/com/bank/dqs/writer/BatchWriterTest.java` — updated all `writer.write(ctx, metrics, null)` call sites to `writer.write(ctx, metrics, null, 0)`
- `dqs-orchestrator/tests/test_runner.py` — updated 3 pre-existing call assertion tests to include `rerun_number=None` kwarg

## Change Log

- 2026-04-03: Story created — rerun management with metric expiration. Extends db.py, cli.py, runner.py, BatchWriter.java, DqsJob.java per rerun requirements.
- 2026-04-03: Story implemented and all tests passing — moved to review status.
