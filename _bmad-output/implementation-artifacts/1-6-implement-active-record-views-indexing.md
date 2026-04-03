# Story 1.6: Implement Active-Record Views & Indexing

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **developer**,
I want active-record views (`v_*_active`) filtering on the sentinel expiry_date and composite indexes on all DQS tables,
so that queries always return current data safely and primary query patterns perform well.

## Acceptance Criteria

1. **Given** all DQS tables exist **When** the views DDL is executed **Then** `v_dq_run_active`, `v_dq_metric_numeric_active`, `v_dq_metric_detail_active`, `v_check_config_active`, `v_dataset_enrichment_active`, `v_dq_orchestration_run_active` views exist
2. **And** each view filters on `expiry_date = '9999-12-31 23:59:59'` (using the `EXPIRY_SENTINEL` concept — the literal must appear in the SQL but `EXPIRY_SENTINEL` is the constant used in Python code referencing this)
3. **And** composite indexes exist on natural key + expiry_date (or natural key columns) for all tables per the exact index names and column lists in Dev Notes
4. **And** querying a view after expiring a record (setting expiry_date to now) no longer returns that record

## Tasks / Subtasks

- [x] Task 1: Replace the placeholder in `dqs-serve/src/serve/schema/views.sql` with all 6 active-record view definitions (AC: #1, #2, #4)
  - [x] Add `v_dq_orchestration_run_active`: `SELECT * FROM dq_orchestration_run WHERE expiry_date = '9999-12-31 23:59:59'`
  - [x] Add `v_dq_run_active`: `SELECT * FROM dq_run WHERE expiry_date = '9999-12-31 23:59:59'`
  - [x] Add `v_dq_metric_numeric_active`: `SELECT * FROM dq_metric_numeric WHERE expiry_date = '9999-12-31 23:59:59'`
  - [x] Add `v_dq_metric_detail_active`: `SELECT * FROM dq_metric_detail WHERE expiry_date = '9999-12-31 23:59:59'`
  - [x] Add `v_check_config_active`: `SELECT * FROM check_config WHERE expiry_date = '9999-12-31 23:59:59'`
  - [x] Add `v_dataset_enrichment_active`: `SELECT * FROM dataset_enrichment WHERE expiry_date = '9999-12-31 23:59:59'`
  - [x] Retain the existing header comment; replace only the placeholder line

- [x] Task 2: Append 6 composite index definitions to the END of `dqs-serve/src/serve/schema/ddl.sql` (AC: #3)
  - [x] Append `CREATE INDEX idx_dq_run_dataset_name_partition_date` — **SKIP if already exists** (story 1-2 created this index; check ddl.sql first before adding)
  - [x] Append `CREATE INDEX idx_dq_metric_numeric_dq_run_id ON dq_metric_numeric (dq_run_id)`
  - [x] Append `CREATE INDEX idx_dq_metric_detail_dq_run_id ON dq_metric_detail (dq_run_id)`
  - [x] Append `CREATE INDEX idx_check_config_dataset_pattern ON check_config (dataset_pattern)`
  - [x] Append `CREATE INDEX idx_dataset_enrichment_dataset_pattern ON dataset_enrichment (dataset_pattern)`
  - [x] Append `CREATE INDEX idx_dq_orchestration_run_parent_path ON dq_orchestration_run (parent_path)`

- [x] Task 3: Update `conftest.py` to also load `views.sql` after `ddl.sql` (AC: #1, #4)
  - [x] Add `_VIEWS_PATH` constant pointing to `views.sql` alongside `_DDL_PATH`
  - [x] Execute `views.sql` content in the same transaction block (after `ddl.sql`) so views are available in all integration tests
  - [x] **Do NOT change** the rollback/teardown strategy — `conn.rollback()` still handles cleanup

- [x] Task 4: Write integration tests in `dqs-serve/tests/test_schema/test_views_indexes.py` (AC: #1–4)
  - [x] Test: all 6 views exist in `information_schema.views` (parametrized)
  - [x] Test: `v_dq_run_active` returns active record (expiry_date = sentinel) and excludes expired record
  - [x] Test: `v_dq_metric_numeric_active` returns active record, excludes expired
  - [x] Test: `v_dq_metric_detail_active` returns active record, excludes expired
  - [x] Test: `v_check_config_active` returns active record, excludes expired
  - [x] Test: `v_dataset_enrichment_active` returns active record, excludes expired
  - [x] Test: `v_dq_orchestration_run_active` returns active record, excludes expired
  - [x] Test: all 6 indexes exist in `pg_indexes` (parametrized)
  - [x] Mark all tests `@pytest.mark.integration`
  - [x] Do NOT create `tests/test_schema/__init__.py` — already exists

## Dev Notes

### CRITICAL: views.sql Is Currently a Placeholder — Replace It Completely

The file `dqs-serve/src/serve/schema/views.sql` currently contains only:
```sql
-- Active-record views (v_*_active)
-- Placeholder — view definitions added in story 1-2
```
Replace ALL content with the correct view definitions. Retain the top-level header comment style.

### Exact views.sql Content to Write

```sql
-- Active-record views for dqs-serve
-- Temporal pattern: active records have expiry_date = '9999-12-31 23:59:59' (EXPIRY_SENTINEL)
-- All serve-layer queries MUST use these views — never query raw tables with manual expiry filters
-- Reference: project-context.md § Framework-Specific Rules (FastAPI) and § Anti-Patterns

CREATE OR REPLACE VIEW v_dq_orchestration_run_active AS
    SELECT * FROM dq_orchestration_run WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_run_active AS
    SELECT * FROM dq_run WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_metric_numeric_active AS
    SELECT * FROM dq_metric_numeric WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dq_metric_detail_active AS
    SELECT * FROM dq_metric_detail WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_check_config_active AS
    SELECT * FROM check_config WHERE expiry_date = '9999-12-31 23:59:59';

CREATE OR REPLACE VIEW v_dataset_enrichment_active AS
    SELECT * FROM dataset_enrichment WHERE expiry_date = '9999-12-31 23:59:59';
```

Use `CREATE OR REPLACE VIEW` so the script is idempotent (safe to re-run).

### CRITICAL: idx_dq_run_dataset_name_partition_date Already Exists in ddl.sql

Story 1-2 ALREADY created this index:
```sql
CREATE INDEX idx_dq_run_dataset_name_partition_date
    ON dq_run (dataset_name, partition_date);
```
**DO NOT add it again.** The 5 NEW indexes to append to `ddl.sql` are:
```sql
CREATE INDEX idx_dq_metric_numeric_dq_run_id
    ON dq_metric_numeric (dq_run_id);

CREATE INDEX idx_dq_metric_detail_dq_run_id
    ON dq_metric_detail (dq_run_id);

CREATE INDEX idx_check_config_dataset_pattern
    ON check_config (dataset_pattern);

CREATE INDEX idx_dataset_enrichment_dataset_pattern
    ON dataset_enrichment (dataset_pattern);

CREATE INDEX idx_dq_orchestration_run_parent_path
    ON dq_orchestration_run (parent_path);
```
Append these at the END of `ddl.sql`, after the `dataset_enrichment` CREATE TABLE.

### CRITICAL: conftest.py Must Load views.sql After ddl.sql

Currently `conftest.py` only loads `ddl.sql`. The `db_conn` fixture must also execute `views.sql` so all tests — including existing ones that verify the serve layer — can use the active-record views. The views depend on tables, so `ddl.sql` must execute first.

**Modify `conftest.py`** (the ONLY story where conftest.py must change):
```python
_DDL_PATH = pathlib.Path(__file__).parent.parent / "src" / "serve" / "schema" / "ddl.sql"
_VIEWS_PATH = pathlib.Path(__file__).parent.parent / "src" / "serve" / "schema" / "views.sql"

@pytest.fixture
def db_conn() -> Generator[psycopg2.extensions.connection, None, None]:
    conn = psycopg2.connect(DATABASE_URL)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            cur.execute(_DDL_PATH.read_text())
            cur.execute(_VIEWS_PATH.read_text())
        yield conn
    finally:
        conn.rollback()
        conn.close()
```

### Architecture: Why These Views Exist (Anti-Pattern Prevention)

From `project-context.md` (Framework-Specific Rules — FastAPI):
> Query active-record views (`v_*_active`), **never raw tables** with manual expiry filters

From `project-context.md` (Anti-Patterns):
> Never query raw tables in the serve layer — always use `v_*_active` views
> Never hardcode the sentinel timestamp `9999-12-31 23:59:59` — use `EXPIRY_SENTINEL` constant

**In Python code (serve layer, tests):** always `EXPIRY_SENTINEL` from `serve.db.models` — never hardcode the string.
**In SQL DDL (views.sql, ddl.sql):** the literal `'9999-12-31 23:59:59'` IS correct — SQL doesn't have Python constants.

### Index Naming Convention (from architecture.md)

Pattern: `idx_{table}_{columns}`
- `idx_dq_run_dataset_name_partition_date` — already exists (story 1-2)
- `idx_dq_metric_numeric_dq_run_id` — new
- `idx_dq_metric_detail_dq_run_id` — new
- `idx_check_config_dataset_pattern` — new
- `idx_dataset_enrichment_dataset_pattern` — new
- `idx_dq_orchestration_run_parent_path` — new

### View Naming Convention (from architecture.md)

Pattern: `v_{table}_active`
- `v_dq_run_active`
- `v_dq_metric_numeric_active`
- `v_dq_metric_detail_active`
- `v_check_config_active`
- `v_dataset_enrichment_active`
- `v_dq_orchestration_run_active`

### Testing Strategy

Per `project-context.md`:
> **Test against real Postgres** — temporal pattern and active-record views need real DB validation

New test file: `dqs-serve/tests/test_schema/test_views_indexes.py`

**View existence test (parametrized):**
```python
@pytest.mark.integration
@pytest.mark.parametrize("view_name", [
    "v_dq_orchestration_run_active",
    "v_dq_run_active",
    "v_dq_metric_numeric_active",
    "v_dq_metric_detail_active",
    "v_check_config_active",
    "v_dataset_enrichment_active",
])
class TestActiveViewsExist:
    def test_view_exists(self, db_conn, view_name):
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT table_name FROM information_schema.views
            WHERE table_schema = 'public' AND table_name = %s
            """,
            (view_name,),
        )
        assert cur.fetchone() is not None, f"View '{view_name}' not found"
```

**View filter test (expired record excluded — pattern for all views):**
```python
@pytest.mark.integration
class TestDqRunActiveView:
    def test_active_record_visible(self, db_conn):
        from serve.db.models import EXPIRY_SENTINEL
        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dq_run (dataset_name, partition_date, check_status) VALUES ('sales_daily', '2026-01-01', 'PASS')"
        )
        cur.execute("SELECT count(*) FROM v_dq_run_active WHERE dataset_name = 'sales_daily'")
        assert cur.fetchone()[0] == 1

    def test_expired_record_excluded(self, db_conn):
        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dq_run (dataset_name, partition_date, check_status, expiry_date) VALUES ('sales_daily', '2026-01-01', 'PASS', NOW())"
        )
        cur.execute("SELECT count(*) FROM v_dq_run_active WHERE dataset_name = 'sales_daily'")
        assert cur.fetchone()[0] == 0
```

**Index existence test (parametrized):**
```python
@pytest.mark.integration
@pytest.mark.parametrize("index_name", [
    "idx_dq_run_dataset_name_partition_date",
    "idx_dq_metric_numeric_dq_run_id",
    "idx_dq_metric_detail_dq_run_id",
    "idx_check_config_dataset_pattern",
    "idx_dataset_enrichment_dataset_pattern",
    "idx_dq_orchestration_run_parent_path",
])
class TestCompositeIndexesExist:
    def test_index_exists(self, db_conn, index_name):
        cur = db_conn.cursor()
        cur.execute(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname = %s",
            (index_name,),
        )
        assert cur.fetchone() is not None, f"Index '{index_name}' not found"
```

**Run tests:**
```bash
cd dqs-serve && uv run pytest -m integration -v
```

### What NOT to Do in This Story

- **Do NOT add ORM models** in `db/models.py` — ORM models deferred until after all schema stories (same rule as all previous stories)
- **Do NOT duplicate** `idx_dq_run_dataset_name_partition_date` — it already exists in ddl.sql at line 37-38
- **Do NOT hardcode `'9999-12-31 23:59:59'`** in Python test assertions — use `EXPIRY_SENTINEL` from `serve.db.models`
- **Do NOT use `CREATE VIEW`** — use `CREATE OR REPLACE VIEW` for idempotency
- **Do NOT create** `tests/test_schema/__init__.py` — already exists
- **Do NOT modify** any existing CREATE TABLE statements in ddl.sql
- **Do NOT use `ON DELETE CASCADE`** — temporal pattern forbids hard deletes

### Files to Create/Modify

- `dqs-serve/src/serve/schema/views.sql` — REPLACE placeholder with all 6 view definitions
- `dqs-serve/src/serve/schema/ddl.sql` — APPEND 5 new index definitions at the end
- `dqs-serve/tests/conftest.py` — ADD `_VIEWS_PATH` and second `cur.execute` for views.sql
- `dqs-serve/tests/test_schema/test_views_indexes.py` — NEW test file

### Files That Must NOT Be Modified

- `dqs-serve/src/serve/db/models.py` — ORM models deferred until after all schema stories
- `dqs-serve/tests/test_schema/__init__.py` — already exists, do NOT recreate
- Any existing test files (stories 1-2 through 1-5) — zero regressions

### Previous Story Intelligence (Stories 1-2 through 1-5 Learnings)

From story 1-5 completion notes:
- Total test count after story 1-5: 115 tests (4 non-integration + 111 integration) — ensure zero regressions
- `EXPIRY_SENTINEL` is at `serve.db.models.EXPIRY_SENTINEL` — import it, never redefine
- `conftest.py` `db_conn` fixture: `with conn.cursor() as cur:` for setup, then `yield conn` — **this story IS the exception** where conftest.py must be modified (to add views.sql loading)
- `pyproject.toml` in `dqs-serve/` has `addopts = -m "not integration"` — no changes needed
- `uv` for dependency management — no new packages needed for this story
- Commit message pattern: `bmad(epic-1/1-X): complete workflow and quality gates`

**Critical learnings — do NOT repeat:**
- **NEVER hardcode `'9999-12-31 23:59:59'`** in Python test code — flagged in stories 1-3, 1-4, 1-5
- Remove stale TDD RED PHASE comments before marking done

### Project Context Rules (Must Follow)

From `project-context.md`:
- `EXPIRY_SENTINEL` constant defined in `serve.db.models` — use it in Python, never hardcode
- Active-record views (`v_*_active`) are the ONLY way serve-layer code accesses data
- Postgres schema DDL is owned by `dqs-serve` — all DDL in `dqs-serve/src/serve/schema/`
- Test against real Postgres — temporal pattern and active-record views need real DB validation
- Database naming: views = `v_{table}_active`, indexes = `idx_{table}_{columns}`

### References

- Story 1.6 AC: `_bmad-output/planning-artifacts/epics/epic-1-project-foundation-data-model.md`
- Temporal pattern and EXPIRY_SENTINEL: `_bmad-output/project-context.md` § Temporal Data Pattern
- Anti-patterns (no raw tables, no hardcoded sentinel): `_bmad-output/project-context.md` § Critical Don't-Miss Rules
- Database naming conventions: `_bmad-output/planning-artifacts/architecture.md` § Naming Patterns
- Schema ownership (DDL owned by dqs-serve): `_bmad-output/project-context.md` § Development Workflow Rules
- dqs-serve testing rules (real Postgres, test location): `_bmad-output/project-context.md` § Testing Rules
- Index rationale: `_bmad-output/planning-artifacts/architecture.md` § Data Architecture (Indexing row)
- View rationale: `_bmad-output/planning-artifacts/architecture.md` § Implementation Patterns (Enforcement Guidelines)
- Story 1-5 completion notes (conftest pattern, sentinel, test count): `_bmad-output/implementation-artifacts/1-5-implement-orchestration-schema.md`
- Existing ddl.sql (verify idx_dq_run_dataset_name_partition_date at line 37): `dqs-serve/src/serve/schema/ddl.sql`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_none_

### Completion Notes List

- Replaced placeholder in `views.sql` with 6 `CREATE OR REPLACE VIEW` statements, each filtering on `expiry_date = '9999-12-31 23:59:59'` (EXPIRY_SENTINEL literal in SQL).
- Appended 5 new indexes to `ddl.sql` using `CREATE INDEX IF NOT EXISTS` for idempotency: `idx_dq_metric_numeric_dq_run_id`, `idx_dq_metric_detail_dq_run_id`, `idx_check_config_dataset_pattern`, `idx_dataset_enrichment_dataset_pattern`, `idx_dq_orchestration_run_parent_path`. The `idx_dq_run_dataset_name_partition_date` index was confirmed already present from story 1-2 and was not duplicated.
- `conftest.py` updated: added `_VIEWS_PATH` constant and second `cur.execute(_VIEWS_PATH.read_text())` call in `db_conn` fixture so views are applied to all integration tests.
- `test_views_indexes.py` created with complete tests for all 4 ACs.
- All 135 integration tests pass, 4 non-integration tests pass. Total: 139 tests, 0 failures, 0 regressions.
- `EXPIRY_SENTINEL` is used in Python test assertions via `from serve.db.models import EXPIRY_SENTINEL` — no hardcoded sentinel strings in Python.

### File List

- `dqs-serve/src/serve/schema/views.sql` — replaced placeholder with 6 active-record view definitions
- `dqs-serve/src/serve/schema/ddl.sql` — appended 5 new composite index definitions

## Tasks / Review Findings

### Review Findings

- [x] [Review][Patch] `idx_dq_run_dataset_name_partition_date` lacks `IF NOT EXISTS` — inconsistent with all 5 new indexes; breaks idempotency on re-run against existing DB [dqs-serve/src/serve/schema/ddl.sql:37] — **Fixed:** added `IF NOT EXISTS`
- [x] [Review][Patch] Completion notes incorrectly stated conftest.py was unchanged [_bmad-output/implementation-artifacts/1-6-implement-active-record-views-indexing.md] — **Fixed:** corrected completion notes to accurately reflect conftest.py was modified
- [x] [Review][Patch] `_insert_dq_run` helper duplicated verbatim in two test classes — test code duplication [dqs-serve/tests/test_schema/test_views_indexes.py:122,186] — **Fixed:** extracted to module-level `_insert_dq_run(cur, dataset_name)` helper used by both classes

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
- 2026-04-03: Implementation complete. views.sql populated with 6 CREATE OR REPLACE VIEW statements. ddl.sql appended with 5 new indexes. All 139 tests pass (135 integration + 4 non-integration). Status set to review.
- 2026-04-03: Code review complete. 3 patch findings fixed (1 Medium, 2 Low). 2 findings dismissed as noise. Status set to done.
