# Story 1.5: Implement Orchestration Schema

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform operator**,
I want the `dq_orchestration_run` table with temporal pattern,
So that every orchestration run is tracked with start/end times, dataset counts, and status for audit and debugging.

## Acceptance Criteria

1. **Given** the Postgres database is running **When** the DDL script is executed **Then** `dq_orchestration_run` exists with columns: `id` (SERIAL PK), `parent_path` (TEXT NOT NULL), `run_status` (TEXT NOT NULL), `start_time` (TIMESTAMP), `end_time` (TIMESTAMP), `total_datasets` (INT), `passed_datasets` (INT), `failed_datasets` (INT), `error_summary` (TEXT), `create_date` (TIMESTAMP NOT NULL DEFAULT NOW()), `expiry_date` (TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59') — following the temporal pattern
2. **And** `dq_run.orchestration_run_id` references `dq_orchestration_run.id` via a named FK constraint `fk_dq_run_orchestration_run`
3. **And** the table supports tracking multiple parent paths per orchestration run (one row per parent_path per run)

## Tasks / Subtasks

- [x] Task 1: Add `dq_orchestration_run` DDL to `dqs-serve/src/serve/schema/ddl.sql` (AC: #1, #3)
  - [x] Append `CREATE TABLE dq_orchestration_run` BEFORE the existing `dq_run` table — see Dev Notes for ordering rationale
  - [x] Add `id SERIAL PRIMARY KEY`
  - [x] Add `parent_path TEXT NOT NULL`
  - [x] Add `run_status TEXT NOT NULL`
  - [x] Add `start_time TIMESTAMP`
  - [x] Add `end_time TIMESTAMP`
  - [x] Add `total_datasets INTEGER`
  - [x] Add `passed_datasets INTEGER`
  - [x] Add `failed_datasets INTEGER`
  - [x] Add `error_summary TEXT`
  - [x] Add `create_date TIMESTAMP NOT NULL DEFAULT NOW()`
  - [x] Add `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`
  - [x] Add unique constraint: `CONSTRAINT uq_dq_orchestration_run_parent_path_expiry_date UNIQUE (parent_path, expiry_date)`

- [x] Task 2: Add `ALTER TABLE` FK constraint to `dqs-serve/src/serve/schema/ddl.sql` (AC: #2)
  - [x] Append AFTER the `dq_run` table CREATE statement (and its index):
    ```sql
    ALTER TABLE dq_run
        ADD CONSTRAINT fk_dq_run_orchestration_run
        FOREIGN KEY (orchestration_run_id) REFERENCES dq_orchestration_run(id);
    ```
  - [x] This resolves the FK deferral from story 1-2

- [x] Task 3: Write integration tests in `dqs-serve/tests/test_schema/test_orchestration_schema.py` (AC: #1–3)
  - [x] Test: `dq_orchestration_run` table exists in public schema
  - [x] Test: `dq_orchestration_run` has all required columns with correct types (parametrized)
  - [x] Test: `dq_orchestration_run` unique constraint exists with exact name `uq_dq_orchestration_run_parent_path_expiry_date`
  - [x] Test: `dq_orchestration_run` unique constraint covers exactly `(parent_path, expiry_date)`
  - [x] Test: inserting two active `dq_orchestration_run` rows with same `parent_path` is rejected with UniqueViolation
  - [x] Test: `dq_run.orchestration_run_id` FK constraint `fk_dq_run_orchestration_run` exists in `information_schema.referential_constraints`
  - [x] Test: inserting a `dq_run` row with a non-existent `orchestration_run_id` raises `ForeignKeyViolation`
  - [x] Test: inserting a `dq_run` row with `orchestration_run_id = NULL` succeeds (FK is nullable — not all runs have an orchestration parent)
  - [x] Mark all tests in this file `@pytest.mark.integration`

## Dev Notes

### CRITICAL: DDL File Structure — `dq_orchestration_run` Must Be Created BEFORE `dq_run`

`dq_run` references `dq_orchestration_run.id` via a FK constraint. In SQL, the referenced table must exist before the referencing ALTER TABLE executes. The correct final structure of `ddl.sql` is:

```sql
-- dqs-serve owns all schema DDL
-- Temporal pattern: create_date + expiry_date as TIMESTAMP, sentinel '9999-12-31 23:59:59'
-- Composite unique constraints enforce no duplicate active records (same natural key + expiry_date)

CREATE TABLE dq_orchestration_run ( ... );   ← NEW — must come FIRST (referenced by dq_run)

CREATE TABLE dq_run ( ... );                 ← KEEP UNCHANGED (has orchestration_run_id INTEGER)
CREATE INDEX idx_dq_run_dataset_name_partition_date ...;  ← KEEP UNCHANGED
ALTER TABLE dq_run ADD CONSTRAINT fk_dq_run_orchestration_run ...;  ← NEW — added after dq_run

CREATE TABLE dq_metric_numeric ( ... );      ← KEEP UNCHANGED
CREATE TABLE dq_metric_detail ( ... );       ← KEEP UNCHANGED
CREATE TABLE check_config ( ... );           ← KEEP UNCHANGED
CREATE TABLE dataset_enrichment ( ... );     ← KEEP UNCHANGED
```

The `dq_run` table already has `orchestration_run_id INTEGER` (nullable) — that column was added in story 1-2 with no FK (the referenced table did not yet exist). This story adds the FK via ALTER TABLE, not by modifying the CREATE TABLE statement.

### Exact DDL to Produce

**Step 1 — Insert `dq_orchestration_run` CREATE TABLE at the TOP of ddl.sql (before `dq_run`):**

```sql
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
    expiry_date      TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dq_orchestration_run_parent_path_expiry_date
        UNIQUE (parent_path, expiry_date)
);
```

**Step 2 — Append `ALTER TABLE` after `dq_run`'s CREATE TABLE + index (right after the `idx_dq_run_dataset_name_partition_date` CREATE INDEX line):**

```sql
ALTER TABLE dq_run
    ADD CONSTRAINT fk_dq_run_orchestration_run
    FOREIGN KEY (orchestration_run_id) REFERENCES dq_orchestration_run(id);
```

### Type Decisions for `dq_orchestration_run`

- `parent_path TEXT NOT NULL` — the HDFS parent path that was processed (e.g., `/data/consumer/sales`). NOT NULL because every orchestration run is triggered by a specific parent path. One row per parent-path invocation.
- `run_status TEXT NOT NULL` — status string: `RUNNING`, `SUCCESS`, `FAILED`. NOT NULL with a check status always required.
- `start_time TIMESTAMP` — nullable: set when orchestration starts, not known at row insert time if row is pre-created; allows partial updates as run progresses
- `end_time TIMESTAMP` — nullable: not known until run completes
- `total_datasets INTEGER`, `passed_datasets INTEGER`, `failed_datasets INTEGER` — all nullable: counts only known after run completes
- `error_summary TEXT` — nullable: only set when there are failures
- `TIMESTAMP` (not TIMESTAMPTZ) — Eastern time natively per project-context.md; consistent with all other DQS tables
- No `ON DELETE CASCADE` — temporal pattern forbids hard deletes
- Unique constraint on `(parent_path, expiry_date)` — same as all other temporal tables: enforces one active row per parent path. Multiple completed rows (expired) are allowed for history.

### FK Constraint on `dq_run.orchestration_run_id`

From story 1-2 dev notes: "No FK on `orchestration_run_id` in this story — `dq_orchestration_run` table created in story 1-5." This story completes that deferral.

The FK is **nullable** — `orchestration_run_id INTEGER` in `dq_run` has no NOT NULL constraint (story 1-2 DDL). This is intentional: during development/testing, `dq_run` rows may be inserted without an orchestration parent. The FK constraint only enforces referential integrity when the value is non-NULL.

Named constraint follows architecture naming convention: `fk_{table}_{referenced_table}` → `fk_dq_run_orchestration_run`.

### Naming Convention Cross-Check

Per architecture database naming rules:
- Table: `dq_orchestration_run` (snake_case, singular, `dq_` prefix for run-data tables)
- Unique constraint: `uq_dq_orchestration_run_parent_path_expiry_date`
- FK constraint: `fk_dq_run_orchestration_run`
- Future index (story 1-6): `idx_dq_orchestration_run_parent_path`
- Future view (story 1-6): `v_dq_orchestration_run_active`
- **Do NOT add indexes** in this story — that is story 1-6
- **Do NOT add views** — that is story 1-6

### What NOT to Do in This Story

- **Do NOT modify** the `dq_run` CREATE TABLE statement — the `orchestration_run_id INTEGER` column is already there; only add the ALTER TABLE FK
- **Do NOT modify** any other existing tables or their statements
- **Do NOT add views** — story 1-6
- **Do NOT add ORM models** in `db/models.py` — ORM models come after all schema stories
- **Do NOT add indexes** on the new table — story 1-6
- **Do NOT hardcode `'9999-12-31 23:59:59'`** in any Python test code — import `EXPIRY_SENTINEL` from `serve.db.models`
- **Do NOT use `ON DELETE CASCADE`** — temporal pattern forbids hard deletes
- **Do NOT make `start_time`, `end_time`, `total_datasets`, `passed_datasets`, `failed_datasets`, or `error_summary` NOT NULL** — these are populated as the run progresses

### conftest.py Compatibility — No Changes Required

The existing `conftest.py` `db_conn` fixture loads the **entire** `ddl.sql` file and rolls back after each test. Because `dq_orchestration_run` will be prepended before `dq_run`, and the FK ALTER TABLE will follow `dq_run`, the full script will execute cleanly in a single transaction. All existing tests from stories 1-2, 1-3, and 1-4 remain unaffected.

**Do NOT modify `conftest.py`** — it works as-is.

### Testing Strategy

Per `project-context.md`:
> **Test against real Postgres** — temporal pattern and active-record views need real DB validation

- Tests live in `dqs-serve/tests/test_schema/test_orchestration_schema.py` — new file
- `dqs-serve/tests/test_schema/__init__.py` already exists — do NOT recreate it
- All tests in this file are `@pytest.mark.integration` — excluded from default suite by `addopts = -m "not integration"` in `dqs-serve/pyproject.toml`
- Use the `db_conn` fixture from `conftest.py` — do NOT create a second DB fixture
- Import `EXPIRY_SENTINEL` from `serve.db.models` for sentinel value assertions — **never hardcode `'9999-12-31 23:59:59'`**
- Run integration tests with: `cd dqs-serve && uv run pytest -m integration -v`

**Test pattern for column parametrization (adapt from story 1-4):**
```python
@pytest.mark.parametrize(
    "column_name,expected_data_type,is_nullable",
    [
        ("id",              "integer",                    "NO"),
        ("parent_path",     "text",                       "NO"),
        ("run_status",      "text",                       "NO"),
        ("start_time",      "timestamp without time zone","YES"),
        ("end_time",        "timestamp without time zone","YES"),
        ("total_datasets",  "integer",                    "YES"),
        ("passed_datasets", "integer",                    "YES"),
        ("failed_datasets", "integer",                    "YES"),
        ("error_summary",   "text",                       "YES"),
        ("create_date",     "timestamp without time zone","NO"),
        ("expiry_date",     "timestamp without time zone","NO"),
    ],
)
def test_dq_orchestration_run_column_exists_with_correct_type(
    self, db_conn, column_name, expected_data_type, is_nullable
):
    cur = db_conn.cursor()
    cur.execute(
        """
        SELECT data_type, is_nullable
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'dq_orchestration_run'
          AND column_name = %s
        """,
        (column_name,),
    )
    row = cur.fetchone()
    assert row is not None, f"Column '{column_name}' not found in dq_orchestration_run"
    assert row[0] == expected_data_type
    assert row[1] == is_nullable
```

**Test pattern for unique constraint violation (adapt from story 1-4):**
```python
import psycopg2

def test_dq_orchestration_run_rejects_duplicate_active_parent_path(self, db_conn):
    cur = db_conn.cursor()
    # Insert first active row — should succeed
    cur.execute(
        "INSERT INTO dq_orchestration_run (parent_path, run_status) VALUES ('/data/consumer/sales', 'RUNNING')"
    )
    # Insert duplicate active row with same parent_path — must raise UniqueViolation
    with pytest.raises(psycopg2.errors.UniqueViolation):
        cur.execute(
            "INSERT INTO dq_orchestration_run (parent_path, run_status) VALUES ('/data/consumer/sales', 'SUCCESS')"
        )
```

**Test pattern for FK constraint existence:**
```python
def test_dq_run_fk_orchestration_run_exists(self, db_conn):
    cur = db_conn.cursor()
    cur.execute(
        """
        SELECT constraint_name
        FROM information_schema.referential_constraints
        WHERE constraint_schema = 'public'
          AND constraint_name = 'fk_dq_run_orchestration_run'
        """
    )
    row = cur.fetchone()
    assert row is not None, "FK constraint 'fk_dq_run_orchestration_run' not found"
```

**Test pattern for nullable FK (dq_run accepts NULL orchestration_run_id):**
```python
def test_dq_run_accepts_null_orchestration_run_id(self, db_conn):
    cur = db_conn.cursor()
    cur.execute(
        """
        INSERT INTO dq_run (dataset_name, partition_date, check_status)
        VALUES ('sales_daily', '2026-01-01', 'PASS')
        """
    )
    # No error raised — orchestration_run_id defaults to NULL, FK allows NULL
```

**Test pattern for FK enforcement (dq_run rejects non-existent orchestration_run_id):**
```python
def test_dq_run_rejects_invalid_orchestration_run_id(self, db_conn):
    cur = db_conn.cursor()
    with pytest.raises(psycopg2.errors.ForeignKeyViolation):
        cur.execute(
            """
            INSERT INTO dq_run (dataset_name, partition_date, check_status, orchestration_run_id)
            VALUES ('sales_daily', '2026-01-01', 'PASS', 99999)
            """
        )
```

### Semantic Role of `dq_orchestration_run` (Context for Correct Implementation)

Per architecture data boundaries:
- **Write side:** `dqs-orchestrator` (psycopg2) — creates a row when an orchestration run starts, updates it when complete
- **Read side:** `dqs-serve` (SQLAlchemy via active-record views) — may surface orchestration run status in API responses
- **Purpose:** Tracks one row per `parent_path` invocation — the orchestrator manages 5-10 `spark-submit` calls (one per parent path); each gets its own `dq_orchestration_run` row
- **FK relationship:** `dq_run.orchestration_run_id` → `dq_orchestration_run.id` — every individual dataset run can be linked back to its orchestration batch

From architecture FR mapping:
> FR26-30 (run tracking, rerun, errors, counts) → `dqs-orchestrator/db.py`

The orchestrator is the sole writer to this table. The serve layer reads it (story 4-x).

### Previous Story Intelligence (Stories 1-2 through 1-4 Learnings)

From story 1-4 completion notes and review:
- `ddl.sql` is at `dqs-serve/src/serve/schema/ddl.sql` — confirmed path
- `models.py` already has `EXPIRY_SENTINEL: str = "9999-12-31 23:59:59"` at `dqs-serve/src/serve/db/models.py` — import it, never redefine, never hardcode
- `conftest.py` `db_conn` fixture: uses `with conn.cursor() as cur:` for setup block, then `yield conn` — do NOT modify
- `pyproject.toml` in `dqs-serve/` has `addopts = -m "not integration"` under `[tool.pytest.ini_options]` — NOT a separate `pytest.ini` file
- `dqs-serve` uses `uv` — no new packages needed for this story (psycopg2 already installed)
- `tests/test_schema/__init__.py` already exists — do NOT create it again
- Total test count after story 1-4: 90 tests (4 non-integration + 86 integration); ensure zero regressions

**Critical review findings from stories 1-3 and 1-4 — do NOT repeat:**
- **NEVER hardcode `'9999-12-31 23:59:59'`** in Python test code — use `EXPIRY_SENTINEL` constant imported from `serve.db.models`. This was flagged in BOTH story 1-3 and 1-4 reviews.
- Remove any stale TDD RED PHASE comments from test files before marking story done

### Project Structure Notes

Files to create/modify:
- `dqs-serve/src/serve/schema/ddl.sql` — INSERT `dq_orchestration_run` CREATE TABLE before `dq_run`, then APPEND `ALTER TABLE fk` after `dq_run` index
- `dqs-serve/tests/test_schema/test_orchestration_schema.py` — new test file

Files that must NOT be modified:
- `dqs-serve/src/serve/schema/views.sql` — unchanged until story 1-6
- `dqs-serve/src/serve/db/models.py` — ORM models deferred until after all schema stories
- `dqs-serve/tests/conftest.py` — works as-is
- `dqs-serve/tests/test_schema/__init__.py` — already exists, do NOT recreate

### Git Intelligence

Established commit pattern (stories 1-1 through 1-4):
- Commit message format: `bmad(epic-1/1-X): complete workflow and quality gates`
- DDL changes and test files committed together in a single commit per story

### References

- Epic 1, Story 1.5 full AC: `_bmad-output/planning-artifacts/epics/epic-1-project-foundation-data-model.md`
- FK deferral from story 1-2: `_bmad-output/implementation-artifacts/1-2-implement-core-schema-with-temporal-pattern.md` (Dev Notes: "No FK on `orchestration_run_id`")
- Temporal pattern (CRITICAL): `_bmad-output/project-context.md` → Temporal Data Pattern (ALL COMPONENTS)
- Anti-patterns (hardcoding sentinel, no raw tables): `_bmad-output/project-context.md` → Critical Don't-Miss Rules
- Database naming conventions: `_bmad-output/planning-artifacts/architecture.md` → Naming Patterns
- Schema ownership rule (DDL owned by dqs-serve): `_bmad-output/project-context.md` → Development Workflow Rules
- dqs-serve testing rules (real Postgres, test location): `_bmad-output/project-context.md` → Testing Rules
- Orchestrator write boundary: `_bmad-output/planning-artifacts/architecture.md` → Data Boundaries table (`dq_orchestration_run` write side = dqs-orchestrator)
- Story 1-4 completion notes (conftest pattern, EXPIRY_SENTINEL, no hardcoding): `_bmad-output/implementation-artifacts/1-4-implement-configuration-enrichment-schema.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_none_

### Completion Notes List

- Prepended `CREATE TABLE dq_orchestration_run` to `ddl.sql` before `dq_run`, following the FK ordering requirement (referenced table must exist before ALTER TABLE runs).
- Appended `ALTER TABLE dq_run ADD CONSTRAINT fk_dq_run_orchestration_run FOREIGN KEY (orchestration_run_id) REFERENCES dq_orchestration_run(id)` after the `idx_dq_run_dataset_name_partition_date` index, resolving the FK deferral from story 1-2.
- Integration test file `test_orchestration_schema.py` was already authored and complete — all 25 new integration tests pass (table existence, PK, 11 parametrized columns, defaults, unique constraint name/columns/enforcement, functional inserts, FK existence, FK references, FK enforcement, nullable FK acceptance, valid FK acceptance).
- Full test suite: 115 tests (4 non-integration + 111 integration), 0 failures, 0 regressions.
- `EXPIRY_SENTINEL` imported from `serve.db.models` — no hardcoded sentinel values in Python.
- `conftest.py` unchanged — DDL runs cleanly in single transaction due to correct table creation order.

### File List

- `dqs-serve/src/serve/schema/ddl.sql` (modified)
- `dqs-serve/tests/test_schema/test_orchestration_schema.py` (pre-existing, validated passing)

### Review Findings

- [x] [Review][Defer] `import psycopg2 # noqa: PLC0415` inside test methods [test_orchestration_schema.py:252,392] — deferred, pre-existing project-wide pattern (identical in stories 1-2, 1-3, 1-4)
- [x] [Review][Defer] `from serve.db.models import EXPIRY_SENTINEL # noqa: PLC0415` inside test method [test_orchestration_schema.py:142] — deferred, pre-existing project-wide pattern
- [x] [Review][Dismiss] `'9999-12-31 23:59:59'` in Python test file [test_orchestration_schema.py:141] — appears only in docstring text, not in any assertion or SQL parameter; actual assertion uses `EXPIRY_SENTINEL` constant

**Review verdict: CLEAN** — 0 critical, 0 high, 0 medium, 0 low. 2 deferred (pre-existing patterns), 1 dismissed. Status set to `done`.

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
- 2026-04-03: Implementation complete. Prepended `dq_orchestration_run` CREATE TABLE and appended FK ALTER TABLE to `ddl.sql`. All 115 tests pass. Status set to review.
- 2026-04-03: Code review complete. All layers passed (Blind Hunter, Edge Case Hunter, Acceptance Auditor). Zero fixable findings. Status set to done.
