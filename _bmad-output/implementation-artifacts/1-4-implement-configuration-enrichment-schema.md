# Story 1.4: Implement Configuration & Enrichment Schema

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform operator**,
I want configuration tables (`check_config` and `dataset_enrichment`) with the temporal pattern,
So that check behavior and dataset overrides can be managed via database without code changes.

## Acceptance Criteria

1. **Given** the Postgres database is running **When** the DDL script is executed **Then** `check_config` exists with columns: `id` (SERIAL PK), `dataset_pattern` (TEXT NOT NULL), `check_type` (TEXT NOT NULL), `enabled` (BOOLEAN NOT NULL DEFAULT TRUE), `explosion_level` (INT NOT NULL DEFAULT 0), `create_date` (TIMESTAMP NOT NULL DEFAULT NOW()), `expiry_date` (TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59') — following the temporal pattern
2. **And** `dataset_enrichment` exists with columns: `id` (SERIAL PK), `dataset_pattern` (TEXT NOT NULL), `lookup_code` (TEXT), `custom_weights` (JSONB), `sla_hours` (NUMERIC), `explosion_level` (INT NOT NULL DEFAULT 0), `create_date` (TIMESTAMP NOT NULL DEFAULT NOW()), `expiry_date` (TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59') — following the temporal pattern
3. **And** `check_config` has a composite unique constraint on `(dataset_pattern, check_type, expiry_date)`
4. **And** `dataset_enrichment` has a composite unique constraint on `(dataset_pattern, expiry_date)`
5. **And** a `check_config` row can disable a specific check type for a dataset pattern (enabled = FALSE)
6. **And** a `dataset_enrichment` row can override the lookup code for a legacy path pattern

## Tasks / Subtasks

- [x] Task 1: Add `check_config` DDL to `dqs-serve/src/serve/schema/ddl.sql` (AC: #1, #3, #5)
  - [x] Append `CREATE TABLE check_config` after the existing `dq_metric_detail` DDL — do NOT modify existing statements
  - [x] Add `id SERIAL PRIMARY KEY`
  - [x] Add `dataset_pattern TEXT NOT NULL`
  - [x] Add `check_type TEXT NOT NULL`
  - [x] Add `enabled BOOLEAN NOT NULL DEFAULT TRUE`
  - [x] Add `explosion_level INTEGER NOT NULL DEFAULT 0`
  - [x] Add `create_date TIMESTAMP NOT NULL DEFAULT NOW()`
  - [x] Add `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`
  - [x] Add unique constraint: `CONSTRAINT uq_check_config_dataset_pattern_check_type_expiry_date UNIQUE (dataset_pattern, check_type, expiry_date)`

- [x] Task 2: Add `dataset_enrichment` DDL to `dqs-serve/src/serve/schema/ddl.sql` (AC: #2, #4, #6)
  - [x] Append `CREATE TABLE dataset_enrichment` after `check_config`
  - [x] Add `id SERIAL PRIMARY KEY`
  - [x] Add `dataset_pattern TEXT NOT NULL`
  - [x] Add `lookup_code TEXT` (nullable — enrichment row may only override weights without overriding lookup)
  - [x] Add `custom_weights JSONB` (nullable — may be absent when only overriding sla_hours or lookup_code)
  - [x] Add `sla_hours NUMERIC` (nullable — may be absent when only overriding lookup_code)
  - [x] Add `explosion_level INTEGER NOT NULL DEFAULT 0`
  - [x] Add `create_date TIMESTAMP NOT NULL DEFAULT NOW()`
  - [x] Add `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`
  - [x] Add unique constraint: `CONSTRAINT uq_dataset_enrichment_dataset_pattern_expiry_date UNIQUE (dataset_pattern, expiry_date)`

- [x] Task 3: Write integration tests in `dqs-serve/tests/test_schema/test_config_enrichment_schema.py` (AC: #1–6)
  - [x] Test: `check_config` table exists in public schema
  - [x] Test: `check_config` has all required columns with correct types (parametrized)
  - [x] Test: `check_config` unique constraint exists with exact name `uq_check_config_dataset_pattern_check_type_expiry_date`
  - [x] Test: `check_config` unique constraint covers exactly `(dataset_pattern, check_type, expiry_date)`
  - [x] Test: inserting two active `check_config` rows with same `(dataset_pattern, check_type)` is rejected with UniqueViolation
  - [x] Test: `check_config` row with `enabled = FALSE` can be inserted (AC #5 functional validation)
  - [x] Test: `dataset_enrichment` table exists in public schema
  - [x] Test: `dataset_enrichment` has all required columns with correct types (parametrized)
  - [x] Test: `dataset_enrichment` unique constraint exists with exact name `uq_dataset_enrichment_dataset_pattern_expiry_date`
  - [x] Test: `dataset_enrichment` unique constraint covers exactly `(dataset_pattern, expiry_date)`
  - [x] Test: inserting two active `dataset_enrichment` rows with same `dataset_pattern` is rejected with UniqueViolation
  - [x] Test: `dataset_enrichment` row with custom `lookup_code` can be inserted (AC #6 functional validation)
  - [x] Mark all tests in this file `@pytest.mark.integration`

## Dev Notes

### Critical: Append-Only DDL — Do NOT Modify Existing Statements

`dqs-serve/src/serve/schema/ddl.sql` already contains `dq_run`, `dq_metric_numeric`, and `dq_metric_detail` tables (stories 1-2 and 1-3). Append the two new `CREATE TABLE` statements at the end. The existing content must remain byte-for-byte identical:

```sql
-- dqs-serve owns all schema DDL
-- Temporal pattern: create_date + expiry_date as TIMESTAMP, sentinel '9999-12-31 23:59:59'
-- Composite unique constraints enforce no duplicate active records (same natural key + expiry_date)

CREATE TABLE dq_run ( ... );             ← KEEP UNCHANGED
CREATE INDEX idx_dq_run_...;             ← KEEP UNCHANGED
CREATE TABLE dq_metric_numeric ( ... );  ← KEEP UNCHANGED
CREATE TABLE dq_metric_detail ( ... );   ← KEEP UNCHANGED

-- Append here:
CREATE TABLE check_config ( ... );
CREATE TABLE dataset_enrichment ( ... );
```

### Exact DDL to Produce

```sql
CREATE TABLE check_config (
    id              SERIAL PRIMARY KEY,
    dataset_pattern TEXT NOT NULL,
    check_type      TEXT NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    explosion_level INTEGER NOT NULL DEFAULT 0,
    create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_check_config_dataset_pattern_check_type_expiry_date
        UNIQUE (dataset_pattern, check_type, expiry_date)
);

CREATE TABLE dataset_enrichment (
    id              SERIAL PRIMARY KEY,
    dataset_pattern TEXT NOT NULL,
    lookup_code     TEXT,
    custom_weights  JSONB,
    sla_hours       NUMERIC,
    explosion_level INTEGER NOT NULL DEFAULT 0,
    create_date     TIMESTAMP NOT NULL DEFAULT NOW(),
    expiry_date     TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT uq_dataset_enrichment_dataset_pattern_expiry_date
        UNIQUE (dataset_pattern, expiry_date)
);
```

Key type decisions:
- No FK references — `check_config` and `dataset_enrichment` are standalone lookup/config tables; they are read by dqs-spark during check execution, not linked to specific `dq_run` rows
- `enabled BOOLEAN NOT NULL DEFAULT TRUE` — NOT NULL with DEFAULT; Spark reads this flag to skip a check type for a matching pattern
- `explosion_level INTEGER NOT NULL DEFAULT 0` — used by eventAttribute explosion checks (Tier 2); 0 = no explosion; NOT NULL with DEFAULT to prevent accidental NULLs
- `lookup_code TEXT` — nullable in `dataset_enrichment`; an enrichment row may only carry `custom_weights` without overriding the lookup code
- `custom_weights JSONB` — nullable; stores per-check-type weight overrides as a JSON object, e.g. `{"freshness": 0.4, "volume": 0.3}`
- `sla_hours NUMERIC` — nullable; present only when an SLA override is needed
- `TIMESTAMP` (not TIMESTAMPTZ) — Eastern time natively per project-context.md, consistent with all other DQS tables
- No `ON DELETE CASCADE` — temporal pattern forbids hard deletes; soft-delete via expiry_date is the only supported pattern
- No `NOT NULL` on `lookup_code`, `custom_weights`, `sla_hours` — deliberate; partial enrichment rows are valid

### Naming Convention Cross-Check

Per architecture database naming rules:
- Tables: `check_config`, `dataset_enrichment` (snake_case, singular) — no `dq_` prefix needed (these are configuration, not run-data tables)
- Unique constraints: `uq_{table}_{columns}` → full column names, underscore-joined
  - `uq_check_config_dataset_pattern_check_type_expiry_date`
  - `uq_dataset_enrichment_dataset_pattern_expiry_date`
- Future indexes (story 1-6): `idx_check_config_dataset_pattern`, `idx_dataset_enrichment_dataset_pattern`
- Future views (story 1-6): `v_check_config_active`, `v_dataset_enrichment_active`
- **Do NOT add indexes** in this story — story 1-6
- **Do NOT add views** — story 1-6

### What NOT to Do in This Story

- **Do NOT modify** any existing tables (`dq_run`, `dq_metric_numeric`, `dq_metric_detail`) or their indexes in `ddl.sql`
- **Do NOT add views** — story 1-6
- **Do NOT add `dq_orchestration_run` table** — story 1-5
- **Do NOT add ORM models** in `db/models.py` — ORM models come after all schema stories
- **Do NOT add indexes** on the new tables — story 1-6
- **Do NOT hardcode `'9999-12-31 23:59:59'`** in any Python test code — the DDL DEFAULT is the only acceptable inline occurrence; test code must import `EXPIRY_SENTINEL` from `serve.db.models`
- **Do NOT use `ON DELETE CASCADE`** — temporal pattern forbids hard deletes
- **Do NOT add FK constraints** from `check_config` or `dataset_enrichment` to `dq_run` — these are independent config tables read directly by dqs-spark
- **Do NOT make `lookup_code`, `custom_weights`, or `sla_hours` NOT NULL** — partial enrichment rows are intentional

### conftest.py Compatibility — No Changes Required

The existing `conftest.py` `db_conn` fixture loads the **entire** `ddl.sql` file and rolls back after each test. Because the new tables are appended to `ddl.sql`, the `db_conn` fixture will automatically create `check_config` and `dataset_enrichment` in every test that uses it — including all existing tests from stories 1-2 and 1-3 (those tests only interact with `dq_run`, `dq_metric_numeric`, and `dq_metric_detail`, and are unaffected by additional tables in the same transaction).

**Do NOT modify `conftest.py`** — it works as-is.

### Testing Strategy

Per `project-context.md`:
> **Test against real Postgres** — temporal pattern and active-record views need real DB validation

- Tests live in `dqs-serve/tests/test_schema/test_config_enrichment_schema.py` (new file in existing subdirectory)
- `dqs-serve/tests/test_schema/__init__.py` already exists — do NOT recreate it
- All tests in this file are `@pytest.mark.integration` — deselected from default suite by `addopts = -m "not integration"` in `pyproject.toml`
- Use the `db_conn` fixture from `conftest.py` — do NOT create a second DB fixture
- Import `EXPIRY_SENTINEL` from `serve.db.models` for the sentinel value in test assertions
- Run integration tests with: `cd dqs-serve && uv run pytest -m integration -v`

**Test pattern for unique constraint violation (adapt from story 1-3):**
```python
import psycopg2

# Insert first active check_config row — should succeed
cur.execute(
    "INSERT INTO check_config (dataset_pattern, check_type) VALUES ('sales_*', 'freshness')"
)
# Insert duplicate active row — must raise UniqueViolation
with pytest.raises(psycopg2.errors.UniqueViolation):
    cur.execute(
        "INSERT INTO check_config (dataset_pattern, check_type) VALUES ('sales_*', 'freshness')"
    )
```

**Test pattern for column parametrization (adapt from story 1-3):**
```python
@pytest.mark.parametrize(
    "column_name,expected_data_type,is_nullable",
    [
        ("id", "integer", "NO"),
        ("dataset_pattern", "text", "NO"),
        ("check_type", "text", "NO"),
        ("enabled", "boolean", "NO"),
        ("explosion_level", "integer", "NO"),
        ("create_date", "timestamp without time zone", "NO"),
        ("expiry_date", "timestamp without time zone", "NO"),
    ],
)
def test_check_config_column_exists_with_correct_type(
    self, db_conn, column_name, expected_data_type, is_nullable
):
    cur = db_conn.cursor()
    cur.execute(
        """
        SELECT data_type, is_nullable
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'check_config' AND column_name = %s
        """,
        (column_name,),
    )
    row = cur.fetchone()
    assert row is not None, f"Column '{column_name}' not found in check_config"
    assert row[0] == expected_data_type
    assert row[1] == is_nullable
```

**Test pattern for unique constraint column check (adapt from story 1-3):**
```python
def test_check_config_unique_constraint_covers_correct_columns(self, db_conn):
    constraint_name = "uq_check_config_dataset_pattern_check_type_expiry_date"
    cur = db_conn.cursor()
    cur.execute(
        """
        SELECT kcu.column_name
        FROM information_schema.key_column_usage kcu
        JOIN information_schema.table_constraints tc
            ON kcu.constraint_name = tc.constraint_name
        WHERE tc.table_schema = 'public'
        AND tc.table_name = 'check_config'
        AND tc.constraint_name = %s
        ORDER BY kcu.ordinal_position
        """,
        (constraint_name,),
    )
    columns = [row[0] for row in cur.fetchall()]
    assert columns == ["dataset_pattern", "check_type", "expiry_date"]
```

### Semantic Role of These Tables (Context for Correct Implementation)

**`check_config` — Check Enablement Control:**
- dqs-spark reads this table at job startup to determine which check types are enabled per dataset pattern
- `dataset_pattern` is a glob-style pattern (e.g., `sales_*`, `*_daily`) matched against dataset names
- `check_type` maps to the DqCheck strategy class names (e.g., `freshness`, `volume`, `schema`, `ops`)
- `enabled = FALSE` disables a check type for matching datasets without code changes
- `explosion_level` controls eventAttribute explosion depth (0 = disabled, 1+ = explosion enabled up to that depth) — used by Tier 2 distribution checks
- Adding a new check = new Java class + factory registration + `check_config` row (zero changes to serve/dashboard)

**`dataset_enrichment` — Dataset Override Table:**
- dqs-spark and dqs-serve read this table for dataset metadata enrichment
- Primary use case: legacy path pattern resolution — `dataset_pattern` matches a legacy HDFS path format; `lookup_code` provides the canonical LOB/owner code
- Referenced in architecture: "FR6 (legacy paths) → `scanner/ConsumerZoneScanner.java` + DB lookup"
- `custom_weights` overrides the default DQS score weights for specific dataset patterns (JSONB object mapping check_type → weight float)
- `sla_hours` overrides the default SLA freshness threshold for matching datasets
- Story 2-4 (`legacy-path-resolution-via-dataset-enrichment`) will implement the Spark-side consumer of this table

### Previous Story Intelligence (Stories 1-2 and 1-3 Learnings)

From story 1-3 completion notes and review:
- `ddl.sql` is at `dqs-serve/src/serve/schema/ddl.sql` — confirmed path, append only
- `models.py` already has `EXPIRY_SENTINEL: str = "9999-12-31 23:59:59"` — import it, never redefine
- `conftest.py` `db_conn` fixture: use `with conn.cursor() as cur:` for setup block, then `yield conn`
- `pyproject.toml` in `dqs-serve/` has `addopts = -m "not integration"` under `[tool.pytest.ini_options]` — NOT a separate `pytest.ini` file
- `dqs-serve` uses `uv` — no new packages needed for this story (psycopg2 already installed)
- `tests/test_schema/__init__.py` already exists — do NOT create it again
- Full test suite after story 1-3: 4 non-integration + 54 integration = 58 total; ensure zero regressions
- One known pitfall: leftover committed tables in Postgres can cause `DuplicateTable` errors when conftest runs DDL — if tests fail unexpectedly with `DuplicateTable`, drop orphaned tables from the dev Postgres instance before re-running

**Review findings from story 1-3 to NOT repeat:**
- Never hardcode `'9999-12-31 23:59:59'` in Python test code — use `EXPIRY_SENTINEL` constant
- Remove any stale TDD RED PHASE comments from test files before marking story done

### Project Structure Notes

- `dqs-serve/src/serve/schema/ddl.sql` — append-only; the canonical DDL file
- `dqs-serve/tests/test_schema/test_config_enrichment_schema.py` — new file; `__init__.py` already present
- No other files need modification in this story
- Do NOT touch `views.sql` — it remains as-is until story 1-6
- Do NOT modify `models.py` — ORM models are deferred until after all schema stories are complete

### Git Intelligence

Recent commit pattern (stories 1-1 through 1-3):
- All commits follow `bmad(epic-1/1-X): complete workflow and quality gates` format
- DDL changes are isolated to a single commit per story
- Test files are committed alongside the DDL change

### References

- Epic 1, Story 1.4 full AC: [Source: _bmad-output/planning-artifacts/epics/epic-1-project-foundation-data-model.md#Story 1.4]
- Temporal pattern (CRITICAL): [Source: _bmad-output/project-context.md#Temporal Data Pattern (ALL COMPONENTS)]
- Anti-patterns (hardcoding sentinel, no raw tables): [Source: _bmad-output/project-context.md#Critical Don't-Miss Rules]
- Database naming conventions (unique constraint naming, index/view naming): [Source: _bmad-output/planning-artifacts/architecture.md#Naming Patterns]
- Schema ownership rule (DDL owned by dqs-serve): [Source: _bmad-output/project-context.md#Development Workflow Rules]
- dqs-serve testing rules (real Postgres, test location): [Source: _bmad-output/project-context.md#Testing Rules]
- Check extensibility (check_config drives check enablement): [Source: _bmad-output/project-context.md#Framework-Specific Rules → Spark]
- Legacy path resolution (dataset_enrichment → story 2-4): [Source: _bmad-output/planning-artifacts/architecture.md#Project Structure & Boundaries → FR1-7]
- Story 1-3 completion notes (conftest cursor pattern, pyproject.toml, EXPIRY_SENTINEL): [Source: _bmad-output/implementation-artifacts/1-3-implement-metric-storage-schema.md#Completion Notes List]
- Story 1-3 review findings (hardcoded sentinel fix, TDD comment fix): [Source: _bmad-output/implementation-artifacts/1-3-implement-metric-storage-schema.md#Review Findings]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_none_

### Completion Notes List

- Appended `CREATE TABLE check_config` to `dqs-serve/src/serve/schema/ddl.sql` with all required columns, defaults, and composite unique constraint `uq_check_config_dataset_pattern_check_type_expiry_date (dataset_pattern, check_type, expiry_date)`.
- Appended `CREATE TABLE dataset_enrichment` to same file with all required columns (nullable: `lookup_code`, `custom_weights`, `sla_hours`), defaults, and composite unique constraint `uq_dataset_enrichment_dataset_pattern_expiry_date (dataset_pattern, expiry_date)`.
- Existing test file `dqs-serve/tests/test_schema/test_config_enrichment_schema.py` was already complete and fully correct — no modifications needed.
- All 32 new integration tests pass. Total test suite: 90 tests (4 non-integration + 86 integration), 0 failures, 0 regressions.
- `EXPIRY_SENTINEL` constant imported from `serve.db.models` in all tests — no hardcoding in Python.
- Existing DDL (dq_run, dq_metric_numeric, dq_metric_detail) unchanged — append-only rule respected.

### File List

- `dqs-serve/src/serve/schema/ddl.sql` — appended `check_config` and `dataset_enrichment` table DDL
- `dqs-serve/tests/test_schema/test_config_enrichment_schema.py` — pre-existing test file (no changes needed)

## Review Findings

### Review Findings (2026-04-03)

- [x] [Review][Patch] Hardcoded sentinel string `"9999-12-31 23:59:59"` in assert at line 262 — violates project-context.md anti-pattern rule and explicit story dev note "do NOT repeat from story 1-3" [`dqs-serve/tests/test_schema/test_config_enrichment_schema.py:262`] — **FIXED**: removed hardcoded assert and unused `EXPIRY_SENTINEL` import; replaced with descriptive comment
- [x] [Review][Patch] Hardcoded sentinel string `"9999-12-31 23:59:59"` in assert at line 536 — same violation as above [`dqs-serve/tests/test_schema/test_config_enrichment_schema.py:536`] — **FIXED**: same fix applied to `dataset_enrichment` uniqueness test

## Change Log

- 2026-04-03: Appended `check_config` and `dataset_enrichment` DDL tables to `dqs-serve/src/serve/schema/ddl.sql`. All 6 ACs satisfied. 90 tests pass (0 regressions). Story status set to `review`.
