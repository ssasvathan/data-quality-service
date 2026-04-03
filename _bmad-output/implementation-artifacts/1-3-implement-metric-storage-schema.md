# Story 1.3: Implement Metric Storage Schema

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform operator**,
I want metric storage tables (`dq_metric_numeric` and `dq_metric_detail`) with the temporal pattern,
So that quality check results have structured, auditable storage for both numeric values and structured text.

## Acceptance Criteria

1. **Given** the `dq_run` table exists **When** the DDL script is executed **Then** `dq_metric_numeric` exists with columns: `id` (SERIAL PK), `dq_run_id` (INT FK → dq_run.id), `check_type` (TEXT NOT NULL), `metric_name` (TEXT NOT NULL), `metric_value` (NUMERIC), `create_date` (TIMESTAMP NOT NULL DEFAULT NOW()), `expiry_date` (TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59') — following the temporal pattern
2. **And** `dq_metric_detail` exists with columns: `id` (SERIAL PK), `dq_run_id` (INT FK → dq_run.id), `check_type` (TEXT NOT NULL), `detail_type` (TEXT NOT NULL), `detail_value` (JSONB), `create_date` (TIMESTAMP NOT NULL DEFAULT NOW()), `expiry_date` (TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59') — following the temporal pattern
3. **And** `dq_metric_numeric` has a composite unique constraint on `(dq_run_id, check_type, metric_name, expiry_date)`
4. **And** `dq_metric_detail` has a composite unique constraint on `(dq_run_id, check_type, detail_type, expiry_date)`
5. **And** both tables have foreign key constraints referencing `dq_run.id`

## Tasks / Subtasks

- [x] Task 1: Add `dq_metric_numeric` DDL to `dqs-serve/src/serve/schema/ddl.sql` (AC: #1, #3, #5)
  - [x] Append `CREATE TABLE dq_metric_numeric` after the existing `dq_run` DDL — do NOT modify existing statements
  - [x] Add `id SERIAL PRIMARY KEY`
  - [x] Add `dq_run_id INTEGER NOT NULL`
  - [x] Add `check_type TEXT NOT NULL`
  - [x] Add `metric_name TEXT NOT NULL`
  - [x] Add `metric_value NUMERIC` (nullable — a metric may not have a value yet in some edge cases)
  - [x] Add `create_date TIMESTAMP NOT NULL DEFAULT NOW()`
  - [x] Add `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`
  - [x] Add FK: `CONSTRAINT fk_dq_metric_numeric_dq_run FOREIGN KEY (dq_run_id) REFERENCES dq_run(id)`
  - [x] Add unique constraint: `CONSTRAINT uq_dq_metric_numeric_dq_run_id_check_type_metric_name_expiry_date UNIQUE (dq_run_id, check_type, metric_name, expiry_date)`

- [x] Task 2: Add `dq_metric_detail` DDL to `dqs-serve/src/serve/schema/ddl.sql` (AC: #2, #4, #5)
  - [x] Append `CREATE TABLE dq_metric_detail` after `dq_metric_numeric`
  - [x] Add `id SERIAL PRIMARY KEY`
  - [x] Add `dq_run_id INTEGER NOT NULL`
  - [x] Add `check_type TEXT NOT NULL`
  - [x] Add `detail_type TEXT NOT NULL`
  - [x] Add `detail_value JSONB` (nullable — stores schema structures, field name lists, not source data values)
  - [x] Add `create_date TIMESTAMP NOT NULL DEFAULT NOW()`
  - [x] Add `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`
  - [x] Add FK: `CONSTRAINT fk_dq_metric_detail_dq_run FOREIGN KEY (dq_run_id) REFERENCES dq_run(id)`
  - [x] Add unique constraint: `CONSTRAINT uq_dq_metric_detail_dq_run_id_check_type_detail_type_expiry_date UNIQUE (dq_run_id, check_type, detail_type, expiry_date)`

- [x] Task 3: Write integration tests in `dqs-serve/tests/test_schema/test_metric_schema.py` (AC: #1–5)
  - [x] Test: `dq_metric_numeric` table exists in public schema
  - [x] Test: `dq_metric_numeric` has all required columns with correct types (parametrized)
  - [x] Test: `dq_metric_numeric` unique constraint exists with exact name
  - [x] Test: `dq_metric_numeric` unique constraint covers exactly `(dq_run_id, check_type, metric_name, expiry_date)`
  - [x] Test: `dq_metric_numeric` FK to `dq_run.id` exists (named `fk_dq_metric_numeric_dq_run`)
  - [x] Test: inserting two active `dq_metric_numeric` rows with same `(dq_run_id, check_type, metric_name)` is rejected
  - [x] Test: `dq_metric_detail` table exists in public schema
  - [x] Test: `dq_metric_detail` has all required columns with correct types (parametrized)
  - [x] Test: `dq_metric_detail` unique constraint exists with exact name
  - [x] Test: `dq_metric_detail` unique constraint covers exactly `(dq_run_id, check_type, detail_type, expiry_date)`
  - [x] Test: `dq_metric_detail` FK to `dq_run.id` exists (named `fk_dq_metric_detail_dq_run`)
  - [x] Test: inserting two active `dq_metric_detail` rows with same `(dq_run_id, check_type, detail_type)` is rejected
  - [x] Mark all tests in this file `@pytest.mark.integration`

## Dev Notes

### Critical: Append-Only DDL — Do NOT Modify Existing Statements

`dqs-serve/src/serve/schema/ddl.sql` already contains the `dq_run` table and its index (from story 1-2). Append the two new `CREATE TABLE` statements at the end. The existing content must remain byte-for-byte identical:

```sql
-- dqs-serve owns all schema DDL
-- Temporal pattern: create_date + expiry_date as TIMESTAMP, sentinel '9999-12-31 23:59:59'
-- Composite unique constraints enforce no duplicate active records (same natural key + expiry_date)

CREATE TABLE dq_run ( ... );      ← KEEP UNCHANGED
CREATE INDEX idx_dq_run_...;      ← KEEP UNCHANGED

-- Append here:
CREATE TABLE dq_metric_numeric ( ... );
CREATE TABLE dq_metric_detail ( ... );
```

### Exact DDL to Produce

```sql
CREATE TABLE dq_metric_numeric (
    id           SERIAL PRIMARY KEY,
    dq_run_id    INTEGER NOT NULL,
    check_type   TEXT NOT NULL,
    metric_name  TEXT NOT NULL,
    metric_value NUMERIC,
    create_date  TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT fk_dq_metric_numeric_dq_run
        FOREIGN KEY (dq_run_id) REFERENCES dq_run(id),
    CONSTRAINT uq_dq_metric_numeric_dq_run_id_check_type_metric_name_expiry_date
        UNIQUE (dq_run_id, check_type, metric_name, expiry_date)
);

CREATE TABLE dq_metric_detail (
    id           SERIAL PRIMARY KEY,
    dq_run_id    INTEGER NOT NULL,
    check_type   TEXT NOT NULL,
    detail_type  TEXT NOT NULL,
    detail_value JSONB,
    create_date  TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date  TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT fk_dq_metric_detail_dq_run
        FOREIGN KEY (dq_run_id) REFERENCES dq_run(id),
    CONSTRAINT uq_dq_metric_detail_dq_run_id_check_type_detail_type_expiry_date
        UNIQUE (dq_run_id, check_type, detail_type, expiry_date)
);
```

Key type decisions:
- `dq_run_id` is `INTEGER` (not BIGSERIAL) — matches `dq_run.id` which is SERIAL (integer)
- `metric_value NUMERIC` — nullable (Spark may emit a metric without a numeric value in rare error cases)
- `detail_value JSONB` — Spark stores structured output (field names, schema diffs, JSON objects), never raw PII/PCI. JSONB enables efficient Postgres JSON operators in future queries
- `TIMESTAMP` (not TIMESTAMPTZ) — Eastern time natively per project-context.md. Consistent with `dq_run`
- No `ON DELETE CASCADE` on FKs — DQS tables use soft-delete (temporal pattern), never hard deletes

### Naming Convention Cross-Check

Per architecture database naming rules:
- Tables: `dq_metric_numeric`, `dq_metric_detail` (snake_case, singular) ✓
- FK name: `fk_{child_table}_{parent_table}` → `fk_dq_metric_numeric_dq_run`, `fk_dq_metric_detail_dq_run`
- Unique constraint: `uq_{table}_{columns}` → full column names, underscore-joined
- Future index (story 1-6): `idx_dq_metric_numeric_dq_run_id`, `idx_dq_metric_detail_dq_run_id`
- Do NOT add indexes in this story — that is story 1-6

### What NOT to Do in This Story

- **Do NOT modify** the existing `dq_run` table or its index in `ddl.sql`
- **Do NOT add views** — that is story 1-6
- **Do NOT add config/enrichment tables** (`check_config`, `dataset_enrichment`) — story 1-4
- **Do NOT add `dq_orchestration_run` table** — story 1-5
- **Do NOT add ORM models** in `db/models.py` — ORM models come after all schema stories
- **Do NOT add indexes** on the new tables — story 1-6
- **Do NOT hardcode `'9999-12-31 23:59:59'`** in any Python test code — the DDL DEFAULT is the only acceptable inline occurrence; test code must import `EXPIRY_SENTINEL` from `serve.db.models`
- **Do NOT use `ON DELETE CASCADE`** — the temporal pattern forbids hard deletes; soft-delete via expiry_date update is the only supported pattern

### conftest.py Compatibility — No Changes Required

The existing `conftest.py` `db_conn` fixture loads the **entire** `ddl.sql` file and rolls back after each test. Because the new tables are appended to `ddl.sql`, the `db_conn` fixture will automatically create `dq_metric_numeric` and `dq_metric_detail` in every test that uses it — including the existing `test_dq_run_schema.py` tests (those tests only insert into `dq_run` and are unaffected by additional tables in the same transaction).

**Do NOT modify `conftest.py`** — it works as-is.

### Testing Strategy

Per `project-context.md`:
> **Test against real Postgres** — temporal pattern and active-record views need real DB validation

- Tests live in `dqs-serve/tests/test_schema/test_metric_schema.py` (new file in existing subdirectory)
- `dqs-serve/tests/test_schema/__init__.py` already exists (created in story 1-2) — do NOT recreate it
- All tests in this file are `@pytest.mark.integration` — deselected from default suite by `addopts = -m "not integration"` in `pyproject.toml`
- Use `DATABASE_URL` env var (default: `postgresql://postgres:localdev@localhost:5432/postgres`)
- Use the `db_conn` fixture from `conftest.py` — do NOT create a second DB fixture
- Import `EXPIRY_SENTINEL` from `serve.db.models` for the sentinel value in test assertions

**Test pattern for FK constraint existence** (query `information_schema.referential_constraints`):
```python
cur.execute(
    """
    SELECT tc.constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.referential_constraints rc
        ON tc.constraint_name = rc.constraint_name
    WHERE tc.table_schema = 'public'
    AND tc.table_name = %s
    AND tc.constraint_type = 'FOREIGN KEY'
    AND tc.constraint_name = %s
    """,
    (table_name, fk_name),
)
row = cur.fetchone()
assert row is not None, f"FK constraint '{fk_name}' not found on {table_name}"
```

**Test pattern for unique constraint violation:**
```python
import psycopg2
# First insert a dq_run row to get a valid dq_run_id (FK constraint must be satisfied)
cur.execute(
    "INSERT INTO dq_run (dataset_name, partition_date, check_status) VALUES ('test_ds', '2024-01-01', 'PASS') RETURNING id"
)
run_id = cur.fetchone()[0]
# Insert first metric row — should succeed
cur.execute(
    "INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value) VALUES (%s, 'freshness', 'staleness_hours', 1.5)",
    (run_id,),
)
# Insert duplicate active row — must raise UniqueViolation
with pytest.raises(psycopg2.errors.UniqueViolation):
    cur.execute(
        "INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value) VALUES (%s, 'freshness', 'staleness_hours', 2.0)",
        (run_id,),
    )
```

### Previous Story Intelligence (Story 1-2 Learnings)

From story 1-2 completion notes and review:
- `ddl.sql` starts at `dqs-serve/src/serve/schema/ddl.sql` — confirmed path, file is not empty now
- `models.py` already has `EXPIRY_SENTINEL: str = "9999-12-31 23:59:59"` — import it, don't redefine
- `conftest.py` `db_conn` fixture: cursor is closed before `yield` (review patch from 1-2). Pattern to follow: use `with conn.cursor() as cur:` block for setup, then `yield conn`
- `pyproject.toml` in `dqs-serve/` has `addopts = -m "not integration"` under `[tool.pytest.ini_options]` — NOT a separate `pytest.ini` file
- `dqs-serve` uses `uv` — no new packages needed for this story (psycopg2 already installed)
- `tests/test_schema/__init__.py` already exists — do NOT create it again
- Full test suite: 4 structural + integration tests. Ensure zero regressions in existing tests after DDL append

### Data Sensitivity Boundary (CRITICAL)

From `project-context.md`:
> Detail metrics may contain field names and schema structures but **never source data values**. Architectural rule enforced via code review.

`dq_metric_detail.detail_value JSONB` stores things like:
- Schema drift: `{"expected_fields": ["id", "amount"], "actual_fields": ["id", "amt"]}`
- OPS check: `{"missing_ops_fields": ["source_event_timestamp"]}`
- Distribution check: field name → statistical summary (NOT actual values)

**Never store raw row data or PII/PCI values.** This is enforced at the `DqCheck` interface level in dqs-spark, not in the DDL itself. The DDL does not and cannot enforce this — it is a design rule.

### Spark Writer Contract (Context for Schema Shape)

dqs-spark `BatchWriter.java` will write to these tables via JDBC batch inserts. The natural key choices for unique constraints are driven by how Spark groups output:
- Per dataset per check: one numeric metric row per `(dq_run_id, check_type, metric_name)` — e.g., `('freshness', 'staleness_hours', 24.5)`
- Per dataset per check: one detail row per `(dq_run_id, check_type, detail_type)` — e.g., `('schema', 'drift_summary', {...})`

The unique constraint prevents duplicate writes on Spark job retry within the same active record window.

### Project Structure Notes

- `dqs-serve/src/serve/schema/ddl.sql` — append-only; the canonical DDL file
- `dqs-serve/tests/test_schema/test_metric_schema.py` — new file; `__init__.py` already present
- No other files need modification in this story
- Do NOT touch `views.sql` — it remains as-is until story 1-6

### References

- Epic 1, Story 1.3 full AC: [Source: _bmad-output/planning-artifacts/epics/epic-1-project-foundation-data-model.md#Story 1.3]
- Temporal pattern (CRITICAL): [Source: _bmad-output/project-context.md#Temporal Data Pattern (ALL COMPONENTS)]
- Anti-patterns (hardcoding sentinel, no raw tables in serve): [Source: _bmad-output/project-context.md#Critical Don't-Miss Rules]
- Database naming conventions (FK naming, unique constraint naming, index naming): [Source: _bmad-output/planning-artifacts/architecture.md#Naming Patterns]
- Schema ownership rule (DDL owned by dqs-serve): [Source: _bmad-output/project-context.md#Development Workflow Rules]
- dqs-serve testing rules (real Postgres, test location): [Source: _bmad-output/project-context.md#Testing Rules]
- Data sensitivity boundary (detail metrics — field names only, never source values): [Source: _bmad-output/project-context.md#Framework-Specific Rules → Spark]
- Spark writer contract (BatchWriter.java): [Source: _bmad-output/planning-artifacts/architecture.md#Structure Patterns → dqs-spark]
- Story 1-2 completion notes (conftest cursor pattern, pyproject.toml location): [Source: _bmad-output/implementation-artifacts/1-2-implement-core-schema-with-temporal-pattern.md#Completion Notes List]
- Story 1-2 review findings (cursor close before yield, pyproject.toml not pytest.ini): [Source: _bmad-output/implementation-artifacts/1-2-implement-core-schema-with-temporal-pattern.md#Review Findings]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation was straightforward. One environment issue resolved: leftover committed tables from a prior session caused `DuplicateTable` errors on conftest DDL execution. Resolved by dropping all orphaned tables from the Postgres instance before running integration tests (this is a DB state issue, not a code issue).

### Completion Notes List

- Appended `CREATE TABLE dq_metric_numeric` and `CREATE TABLE dq_metric_detail` to `dqs-serve/src/serve/schema/ddl.sql` without modifying any existing DDL.
- Both tables follow the temporal pattern: `create_date TIMESTAMP NOT NULL DEFAULT NOW()`, `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`.
- FK constraints named `fk_dq_metric_numeric_dq_run` and `fk_dq_metric_detail_dq_run` reference `dq_run(id)` with no `ON DELETE CASCADE` (temporal pattern enforces soft-delete).
- Composite unique constraints use full column names as prescribed:
  - `uq_dq_metric_numeric_dq_run_id_check_type_metric_name_expiry_date`
  - `uq_dq_metric_detail_dq_run_id_check_type_detail_type_expiry_date`
- `metric_value NUMERIC` and `detail_value JSONB` are nullable per story spec.
- ATDD test file `test_metric_schema.py` was pre-written (TDD red phase). All 34 integration tests now pass.
- Full test suite: 4 non-integration + 54 integration = 58 total; 0 regressions.
- No changes to `models.py`, `conftest.py`, `pyproject.toml`, or any other file.

### File List

- `dqs-serve/src/serve/schema/ddl.sql` — modified (appended dq_metric_numeric and dq_metric_detail CREATE TABLE statements)
- `dqs-serve/tests/test_schema/test_metric_schema.py` — pre-existing ATDD file (not modified; tests were already written in red phase)

## Review Findings

- [x] [Review][Patch] Hardcoded sentinel string `'9999-12-31 23:59:59'` in Python test assertions violates project rule — replace with `EXPIRY_SENTINEL` [dqs-serve/tests/test_schema/test_metric_schema.py:152, 299] — FIXED
- [x] [Review][Patch] Stale TDD RED PHASE comment at top of test file claims tests fail by design — tests now pass, comment is misleading [dqs-serve/tests/test_schema/test_metric_schema.py:7-13] — FIXED
- [x] [Review][Defer] No indexes on FK columns `dq_run_id` in `dq_metric_numeric` and `dq_metric_detail` — deferred, pre-existing (story spec: indexes deferred to story 1-6)
- [x] [Review][Defer] `id SERIAL` verification in tests checks `data_type = 'integer'` only, not sequence default — deferred, pre-existing (AC only requires integer PK, not sequence verification)

## Change Log

- 2026-04-03: Appended `CREATE TABLE dq_metric_numeric` and `CREATE TABLE dq_metric_detail` to `ddl.sql`. All 5 ACs satisfied. 34 integration tests pass, 0 regressions. Story status updated to "review".
- 2026-04-03: Code review complete. 2 patch findings fixed (hardcoded sentinel in test assertions, stale TDD comment). 2 findings deferred to future stories. Story status updated to "done".
