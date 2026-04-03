# Story 1.7: Create Test Data Fixtures

Status: done
<!-- Code review complete: 3 Low patches applied, 0 unresolved findings -->
<!-- Quality gates: ruff clean, 64 acceptance tests passed, 0 skipped (2026-04-03) -->

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **developer**,
I want comprehensive test data fixtures with 3-5 mock source systems, mixed anomalies, legacy paths, and mocked historical baselines,
so that all components can be developed and tested against realistic data.

## Acceptance Criteria

1. **Given** the complete schema is deployed **When** the test fixture SQL is loaded **Then** at least 3 mock source systems exist covering both Avro and Parquet formats with different path structures
2. **And** test data includes mixed anomalies within otherwise healthy datasets: stale partitions, zero rows, schema drift, and high nulls
3. **And** at least one legacy-format path (e.g., `src_sys_nm=omni`) exercises the `dataset_enrichment` lookup table resolution
4. **And** `eventAttribute` test values cover all JSON literal types: strings, numbers, booleans, arrays, objects, and nested combinations
5. **And** historical baseline data spanning at least 7 days is mocked in `dq_metric_numeric` for statistical checks (single date on disk, mocked history in DB)
6. **And** test data populates `check_config` and `dataset_enrichment` with sample configuration rows

## Tasks / Subtasks

- [x] Task 1: Create `dqs-serve/src/serve/schema/fixtures.sql` (AC: #1–6)
  - [x] Insert `dq_orchestration_run` rows (2 runs: one complete, one with errors)
  - [x] Insert `dq_run` rows for at least 3 source systems with Avro and Parquet formats (AC: #1)
  - [x] Include anomaly rows: stale partition, zero-row dataset, schema-drift dataset, high-null dataset (AC: #2)
  - [x] Include at least one legacy `src_sys_nm=omni` path that maps via `dataset_enrichment` (AC: #3)
  - [x] Insert `dq_metric_numeric` rows covering all check types + 7-day historical baseline (AC: #4, #5)
  - [x] Insert `dq_metric_detail` rows with `eventAttribute` JSONB values covering all JSON literal types (AC: #4)
  - [x] Insert `check_config` rows (multiple dataset patterns, check types, enabled/disabled) (AC: #6)
  - [x] Insert `dataset_enrichment` rows (including legacy-path override row) (AC: #3, #6)
  - [x] All rows use sentinel `'9999-12-31 23:59:59'` for `expiry_date` (active records)
  - [x] All `dq_metric_numeric` and `dq_metric_detail` rows reference valid `dq_run.id` via FK

- [x] Task 2: Verify `fixtures.sql` loads cleanly against the schema (AC: all)
  - [x] The file must run error-free after `ddl.sql` + `views.sql` (no FK violations, no unique constraint violations)
  - [x] Confirm active-record views return all inserted rows (expiry_date = sentinel)
  - [x] Confirm legacy `dataset_enrichment` row pattern matches the `src_sys_nm=omni` `dq_run` dataset_name

- [x] Task 3: Write integration tests in `dqs-serve/tests/test_schema/test_fixtures.py` (AC: #1–6)
  - [x] Load `fixtures.sql` using the `db_conn` fixture (execute after ddl.sql + views.sql)
  - [x] Test: at least 3 distinct source systems appear in `v_dq_run_active` (distinct `dataset_name` prefixes)
  - [x] Test: both Avro and Parquet formats are represented in metric detail rows
  - [x] Test: anomaly rows exist — stale partition, zero rows, schema drift, high nulls
  - [x] Test: at least one `v_dq_run_active` row has a `dataset_name` matching the legacy path pattern (`src_sys_nm=omni`)
  - [x] Test: `v_dataset_enrichment_active` has a row with `dataset_pattern` matching the legacy path
  - [x] Test: `v_dq_metric_detail_active` contains JSONB `detail_value` for all JSON literal types (string, number, boolean, array, object, nested)
  - [x] Test: `v_dq_metric_numeric_active` contains at least 7 distinct `partition_date` values for the same `check_type` + `metric_name`
  - [x] Test: `v_check_config_active` has rows with both enabled=true and enabled=false
  - [x] Mark all tests `@pytest.mark.integration`
  - [x] Do NOT create `tests/test_schema/__init__.py` — already exists

## Dev Notes

### CRITICAL: New File Location

The fixtures file is a **NEW** file at exactly this path:

```
dqs-serve/src/serve/schema/fixtures.sql
```

This is the only file created in `dqs-serve/src/serve/schema/`. The schema directory already contains:
- `dqs-serve/src/serve/schema/ddl.sql` — DO NOT MODIFY
- `dqs-serve/src/serve/schema/views.sql` — DO NOT MODIFY

### CRITICAL: `fixtures.sql` Must Load After `ddl.sql` + `views.sql`

Execution order (mandatory):
1. `ddl.sql` — creates all tables and indexes
2. `views.sql` — creates all `v_*_active` views
3. `fixtures.sql` — inserts test data

`fixtures.sql` uses INSERT statements referencing tables created by `ddl.sql`. Views are optional for loading but must be consistent (no residual FK violations). FK constraint: `dq_run.orchestration_run_id` → `dq_orchestration_run.id` and `dq_metric_numeric.dq_run_id` / `dq_metric_detail.dq_run_id` → `dq_run.id`.

### Data Design: 3 Mock Source Systems

Design at least 3 source systems with distinct path structures and mixed health profiles:

| Source System | Format | Path Style | Notes |
|---|---|---|---|
| `alpha` | Parquet | `lob=retail/src_sys_nm=alpha/dataset=sales_daily` | Healthy baseline |
| `beta` | Avro | `lob=commercial/src_sys_nm=beta/dataset=transactions` | Mixed anomalies |
| `omni` (legacy) | Parquet | `src_sys_nm=omni/dataset=customer_profile` | Legacy path — no `lob=` prefix, exercises `dataset_enrichment` |

A 4th optional source system (e.g., `gamma`, Avro) improves coverage. All dataset names must be stable string literals that can be pattern-matched (e.g., `LIKE 'lob=retail/%'`).

### CRITICAL: dataset_enrichment Row for Legacy Path

The legacy `omni` path uses the old Hive-style path without a `lob=` prefix. In production, the HDFS scanner calls `dataset_enrichment` to resolve the `lookup_code`. The fixture must include:

1. A `dq_run` row with `dataset_name = 'src_sys_nm=omni/dataset=customer_profile'` (no `lob=` prefix)
2. A `dataset_enrichment` row with `dataset_pattern = 'src_sys_nm=omni/%'` and a valid `lookup_code` (e.g., `'LOB_LEGACY'`)

This proves the enrichment lookup path is exercised by test data. The `dataset_enrichment.explosion_level` can default to `0`.

### CRITICAL: eventAttribute JSON Literal Type Coverage

`dq_metric_detail.detail_value` is JSONB. Insert rows covering all 6 literal types:

```sql
-- String
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
VALUES (1, 'SCHEMA', 'eventAttribute', '"transaction_id"', '9999-12-31 23:59:59');

-- Number
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
VALUES (1, 'SCHEMA', 'eventAttribute_count', '42', '9999-12-31 23:59:59');

-- Boolean
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
VALUES (1, 'SCHEMA', 'eventAttribute_nullable', 'true', '9999-12-31 23:59:59');

-- Array
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
VALUES (1, 'SCHEMA', 'eventAttribute_list', '["id","name","amount"]', '9999-12-31 23:59:59');

-- Object
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
VALUES (1, 'SCHEMA', 'eventAttribute_meta', '{"type":"string","nullable":false}', '9999-12-31 23:59:59');

-- Nested (object containing array)
INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value, expiry_date)
VALUES (1, 'SCHEMA', 'eventAttribute_schema', '{"fields":[{"name":"id","type":"long"},{"name":"amount","type":"double"}],"version":2}', '9999-12-31 23:59:59');
```

The `detail_type` values shown above are examples. Use meaningful names. The `dq_run_id` must reference a valid `dq_run.id` (use a healthy run). Note: the unique constraint is `(dq_run_id, check_type, detail_type, expiry_date)` so each `detail_type` per `(dq_run_id, check_type)` must be unique.

### CRITICAL: 7-Day Historical Baseline in `dq_metric_numeric`

The statistical checks (volume anomaly detection) need historical baselines. Since there is only one actual date on disk, mock the 7-day history directly in the DB with different `partition_date` values for the same dataset. Use the most recent 7 calendar days before `2026-04-03` (the project date):

```sql
-- 7-day history for volume check on sales_daily (dq_run_id maps to 7 dq_run rows across 7 dates)
-- Each day: a dq_run row with partition_date from 2026-03-27 through 2026-04-02
-- Then for each dq_run, insert a metric_numeric row for check_type='VOLUME', metric_name='row_count'
```

Pattern:
- Insert 7 `dq_run` rows: `dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'`, partition_dates `2026-03-27` through `2026-04-02`, `check_status = 'PASS'`
- For each, insert a `dq_metric_numeric` row: `check_type = 'VOLUME'`, `metric_name = 'row_count'`, metric_value varying between ~95000 and ~110000 (realistic daily variation)
- Also insert one anomaly run (the stale/zero-row dataset) with its own metric_numeric row

This satisfies AC #5: historical baseline spanning 7 days in `dq_metric_numeric`.

### Anomaly Row Design (AC #2)

Include distinct `dq_run` rows for each anomaly type:

| Anomaly | `dataset_name` | `check_status` | Key metric |
|---|---|---|---|
| Stale partition | `lob=commercial/src_sys_nm=beta/dataset=transactions` | `WARN` | `FRESHNESS` metric_name=`hours_since_update`, value=`72` |
| Zero rows | `lob=commercial/src_sys_nm=beta/dataset=payments` | `FAIL` | `VOLUME` metric_name=`row_count`, value=`0` |
| Schema drift | `lob=retail/src_sys_nm=alpha/dataset=products` | `FAIL` | `SCHEMA` metric_name=`missing_columns`, value=`3` |
| High nulls | `lob=retail/src_sys_nm=alpha/dataset=customers` | `WARN` | `OPS` metric_name=`null_rate`, value=`0.87` |

### check_config Row Design (AC #6)

Include rows that test:
- A wildcard pattern: `dataset_pattern = 'lob=retail/%'`, `check_type = 'FRESHNESS'`, `enabled = TRUE`
- A specific disable: `dataset_pattern = 'lob=commercial/src_sys_nm=beta/dataset=payments'`, `check_type = 'VOLUME'`, `enabled = FALSE`
- A row with `explosion_level = 2` (non-zero): `dataset_pattern = 'lob=retail/src_sys_nm=alpha/%'`, `check_type = 'OPS'`, `enabled = TRUE`, `explosion_level = 2`

### dataset_enrichment Row Design (AC #3, #6)

Include:
- Legacy path: `dataset_pattern = 'src_sys_nm=omni/%'`, `lookup_code = 'LOB_LEGACY'`, `sla_hours = 24`
- A row with `custom_weights` JSONB: `dataset_pattern = 'lob=commercial/%'`, `custom_weights = '{"FRESHNESS":0.4,"VOLUME":0.3,"SCHEMA":0.2,"OPS":0.1}'`
- Note: `custom_weights` is a JSONB object — this doubles as a JSONB value test

### Temporal Pattern in fixtures.sql

ALL rows must have:
- `expiry_date = '9999-12-31 23:59:59'` (the sentinel — active records)
- `create_date` defaults to `NOW()` if not specified (column default)

Do NOT use `EXPIRY_SENTINEL` Python constant in SQL — the literal string is correct in SQL files (same pattern as `ddl.sql` and `views.sql`).

### conftest.py: Loading fixtures.sql in Tests

The `db_conn` fixture in `tests/conftest.py` currently loads only `ddl.sql` + `views.sql`. The new test file `test_fixtures.py` must load `fixtures.sql` AFTER `db_conn` setup OR use a new fixture. Recommended: create a `db_conn_with_fixtures` fixture in `test_fixtures.py` itself (to avoid changing conftest.py and breaking existing tests):

```python
# tests/test_schema/test_fixtures.py

import pathlib
import psycopg2
import pytest
from tests.conftest import DATABASE_URL

_DDL_PATH = pathlib.Path(__file__).parent.parent.parent / "src" / "serve" / "schema" / "ddl.sql"
_VIEWS_PATH = pathlib.Path(__file__).parent.parent.parent / "src" / "serve" / "schema" / "views.sql"
_FIXTURES_PATH = pathlib.Path(__file__).parent.parent.parent / "src" / "serve" / "schema" / "fixtures.sql"


@pytest.fixture
def db_with_fixtures():
    """Real Postgres connection with full schema (DDL + views + fixtures) loaded."""
    conn = psycopg2.connect(DATABASE_URL)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            cur.execute(_DDL_PATH.read_text())
            cur.execute(_VIEWS_PATH.read_text())
            cur.execute(_FIXTURES_PATH.read_text())
        yield conn
    finally:
        conn.rollback()
        conn.close()
```

**Do NOT modify `tests/conftest.py`** — the `db_conn` fixture is used by 139 existing tests and must remain unchanged. The local `db_with_fixtures` fixture is local to `test_fixtures.py`.

### SQL File Style Rules (Match Existing Files)

Look at `ddl.sql` and `views.sql` for style conventions:
- File header comment block describing purpose and key constraint
- SQL keywords: UPPERCASE (`INSERT INTO`, `VALUES`, `ON CONFLICT`, `WHERE`)
- Table/column names: lowercase snake_case
- Logical grouping with section comments (e.g., `-- dq_orchestration_run rows`, `-- check_config rows`)
- Use `'9999-12-31 23:59:59'` for sentinel (no variable substitution in SQL)

### Unique Constraint Awareness

Unique constraints that must not be violated in the fixture:
- `dq_orchestration_run`: `UNIQUE (parent_path, expiry_date)` → one active row per `parent_path`
- `dq_run`: `UNIQUE (dataset_name, partition_date, rerun_number, expiry_date)` → one active row per `(dataset_name, partition_date, rerun_number)`
- `dq_metric_numeric`: `UNIQUE (dq_run_id, check_type, metric_name, expiry_date)` → one active metric per `(dq_run_id, check_type, metric_name)`
- `dq_metric_detail`: `UNIQUE (dq_run_id, check_type, detail_type, expiry_date)` → one active detail per `(dq_run_id, check_type, detail_type)`
- `check_config`: `UNIQUE (dataset_pattern, check_type, expiry_date)` → one active config per `(dataset_pattern, check_type)`
- `dataset_enrichment`: `UNIQUE (dataset_pattern, expiry_date)` → one active enrichment per `dataset_pattern`

The 7-day historical baseline for `sales_daily` requires 7 separate `dq_run` rows (different `partition_date`) — each with `rerun_number = 0` (different partition_date makes each unique).

### ID Reference Strategy

Since `id` columns are `SERIAL`, insert `dq_orchestration_run` rows first, then `dq_run` rows referencing them. For `dq_metric_numeric` and `dq_metric_detail`, reference `dq_run.id`. Use a `RETURNING id` pattern if the SQL file needs to capture IDs, OR use `INSERT ... SELECT` with a subquery to look up the FK:

```sql
-- Example: insert metric referencing a dq_run by its unique natural key
INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date)
SELECT id, 'VOLUME', 'row_count', 105000, '9999-12-31 23:59:59'
FROM dq_run
WHERE dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
  AND partition_date = '2026-04-02'
  AND rerun_number = 0
  AND expiry_date = '9999-12-31 23:59:59';
```

This approach is robust and avoids hardcoding serial IDs that may differ across environments.

### Testing Strategy

Per `project-context.md`:
> **Test against real Postgres** — temporal pattern and active-record views need real DB validation

New test file: `dqs-serve/tests/test_schema/test_fixtures.py`

Key tests (use `db_with_fixtures` fixture):

```python
@pytest.mark.integration
class TestFixturesSourceSystems:
    def test_at_least_3_source_systems(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        cur.execute("SELECT COUNT(DISTINCT dataset_name) FROM v_dq_run_active")
        assert cur.fetchone()[0] >= 3

    def test_both_avro_and_parquet_covered(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        # Verify detail rows exist for both formats (via check_type or dataset_name patterns)
        cur.execute(
            "SELECT COUNT(*) FROM v_dq_run_active WHERE dataset_name LIKE 'lob=retail/%'"
        )
        assert cur.fetchone()[0] >= 1
        cur.execute(
            "SELECT COUNT(*) FROM v_dq_run_active WHERE dataset_name LIKE 'lob=commercial/%'"
        )
        assert cur.fetchone()[0] >= 1


@pytest.mark.integration
class TestFixturesAnomalies:
    def test_zero_row_anomaly_present(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        cur.execute(
            """
            SELECT COUNT(*) FROM v_dq_metric_numeric_active
            WHERE check_type = 'VOLUME' AND metric_name = 'row_count' AND metric_value = 0
            """
        )
        assert cur.fetchone()[0] >= 1

    def test_stale_partition_present(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        cur.execute(
            """
            SELECT COUNT(*) FROM v_dq_run_active WHERE check_status = 'WARN'
            """
        )
        assert cur.fetchone()[0] >= 1


@pytest.mark.integration
class TestFixturesLegacyPath:
    def test_legacy_path_in_dq_run(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        cur.execute(
            "SELECT COUNT(*) FROM v_dq_run_active WHERE dataset_name LIKE 'src_sys_nm=omni/%'"
        )
        assert cur.fetchone()[0] >= 1

    def test_legacy_path_in_dataset_enrichment(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        cur.execute(
            "SELECT COUNT(*) FROM v_dataset_enrichment_active WHERE dataset_pattern LIKE 'src_sys_nm=omni/%'"
        )
        assert cur.fetchone()[0] >= 1


@pytest.mark.integration
class TestFixturesEventAttributeTypes:
    def test_all_json_literal_types_present(self, db_with_fixtures):
        """Verify JSONB values of all 6 types are present in metric_detail."""
        cur = db_with_fixtures.cursor()
        cur.execute(
            """
            SELECT detail_value FROM v_dq_metric_detail_active
            WHERE check_type = 'SCHEMA' AND detail_type LIKE 'eventAttribute%'
            """
        )
        rows = [r[0] for r in cur.fetchall()]
        import json
        types_seen = set()
        for v in rows:
            if isinstance(v, str):
                types_seen.add("string")
            elif isinstance(v, bool):
                types_seen.add("boolean")
            elif isinstance(v, (int, float)):
                types_seen.add("number")
            elif isinstance(v, list):
                types_seen.add("array")
            elif isinstance(v, dict):
                types_seen.add("object")
                # check for nested
                if any(isinstance(val, (list, dict)) for val in v.values()):
                    types_seen.add("nested")
        expected = {"string", "number", "boolean", "array", "object", "nested"}
        assert expected.issubset(types_seen), f"Missing JSON types: {expected - types_seen}"


@pytest.mark.integration
class TestFixturesHistoricalBaseline:
    def test_seven_day_history_in_metric_numeric(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        cur.execute(
            """
            SELECT COUNT(DISTINCT r.partition_date)
            FROM v_dq_metric_numeric_active m
            JOIN v_dq_run_active r ON r.id = m.dq_run_id
            WHERE m.check_type = 'VOLUME' AND m.metric_name = 'row_count'
              AND r.dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
            """
        )
        assert cur.fetchone()[0] >= 7


@pytest.mark.integration
class TestFixturesCheckConfig:
    def test_enabled_and_disabled_rows(self, db_with_fixtures):
        cur = db_with_fixtures.cursor()
        cur.execute("SELECT COUNT(*) FROM v_check_config_active WHERE enabled = TRUE")
        assert cur.fetchone()[0] >= 1
        cur.execute("SELECT COUNT(*) FROM v_check_config_active WHERE enabled = FALSE")
        assert cur.fetchone()[0] >= 1
```

**Run tests:**
```bash
cd dqs-serve && uv run pytest tests/test_schema/test_fixtures.py -m integration -v
```

### What NOT to Do in This Story

- **Do NOT modify `ddl.sql` or `views.sql`** — those are owned by previous stories
- **Do NOT modify `tests/conftest.py`** — 139 existing tests depend on it; use local `db_with_fixtures` fixture
- **Do NOT add ORM models** in `db/models.py` — ORM models are deferred past all schema stories
- **Do NOT create `tests/test_schema/__init__.py`** — already exists
- **Do NOT hardcode serial IDs** (e.g., `dq_run_id = 1`) — use subqueries to look up FKs by natural keys
- **Do NOT use Python `EXPIRY_SENTINEL` constant in SQL** — the literal `'9999-12-31 23:59:59'` is correct in SQL files
- **Do NOT violate unique constraints** — each `(dataset_name, partition_date, rerun_number)` must be unique for active rows
- **Do NOT store raw field values** (data sensitivity boundary: detail metrics may contain field names, never source data values)

### Files to Create

- `dqs-serve/src/serve/schema/fixtures.sql` — NEW test data fixture file
- `dqs-serve/tests/test_schema/test_fixtures.py` — NEW integration test file

### Files That Must NOT Be Modified

- `dqs-serve/src/serve/schema/ddl.sql` — owned by stories 1-2 through 1-6
- `dqs-serve/src/serve/schema/views.sql` — owned by story 1-6
- `dqs-serve/tests/conftest.py` — DO NOT CHANGE (use local fixture in test file)
- `dqs-serve/src/serve/db/models.py` — ORM models deferred
- Any existing test files — zero regressions required (139 tests must still pass)

### Previous Story Intelligence (Story 1-6 Learnings)

From story 1-6 completion notes (status: done):
- Total test count after story 1-6: **139 tests** (135 integration + 4 non-integration) — ensure zero regressions
- `EXPIRY_SENTINEL` is at `serve.db.models.EXPIRY_SENTINEL` — use in Python assertions, never hardcode
- `conftest.py` `db_conn` fixture: runs `ddl.sql` then `views.sql` in a single transaction, rolled back on teardown — THIS IS THE PATTERN to replicate in `db_with_fixtures`
- `uv` for dependency management — no new packages needed (psycopg2 already present)
- All indexes now use `CREATE INDEX IF NOT EXISTS` — idempotency pattern; fixtures.sql uses INSERT (not idempotent by design, rollback handles cleanup)
- Commit message pattern: `bmad(epic-1/1-X): complete workflow and quality gates`
- Review finding: "Remove stale TDD RED PHASE comments before marking done"

### Project Context Rules (Must Follow)

From `project-context.md`:
- `EXPIRY_SENTINEL` constant = `'9999-12-31 23:59:59'` — use constant in Python, literal in SQL
- Active-record views (`v_*_active`) are the ONLY way serve-layer code accesses data; queries in tests should use them
- Postgres schema DDL is owned by `dqs-serve` — all DDL/fixtures in `dqs-serve/src/serve/schema/`
- Test against real Postgres — temporal pattern and active-record views need real DB validation
- Database naming: tables snake_case singular, columns snake_case
- Data sensitivity: detail metrics contain field names, **never source data values**

### References

- Story 1.7 AC: `_bmad-output/planning-artifacts/epics/epic-1-project-foundation-data-model.md` (Story 1.7)
- Architecture FR53-57 (Test Data): `_bmad-output/planning-artifacts/architecture.md` § Requirements Overview table (Test Data row)
- Temporal pattern and EXPIRY_SENTINEL: `_bmad-output/project-context.md` § Temporal Data Pattern
- Anti-patterns: `_bmad-output/project-context.md` § Critical Don't-Miss Rules
- Database naming conventions: `_bmad-output/planning-artifacts/architecture.md` § Naming Patterns
- Schema ownership (DDL owned by dqs-serve): `_bmad-output/project-context.md` § Development Workflow Rules
- dqs-serve testing rules (real Postgres, test location): `_bmad-output/project-context.md` § Testing Rules
- Existing ddl.sql (tables + constraints): `dqs-serve/src/serve/schema/ddl.sql`
- Existing views.sql (active-record views): `dqs-serve/src/serve/schema/views.sql`
- conftest.py pattern (db_conn fixture): `dqs-serve/tests/conftest.py`
- Story 1-6 completion notes (test count, EXPIRY_SENTINEL location, conftest pattern): `_bmad-output/implementation-artifacts/1-6-implement-active-record-views-indexing.md`

### Review Findings

- [x] [Review][Patch] test_wildcard_pattern_check_config_exists checks for '/' not '%' in pattern [dqs-serve/tests/test_schema/test_fixtures.py:562–570] — Fixed
- [x] [Review][Patch] dq_orchestration_run retail/alpha stats inaccurate: passed_datasets=3,failed_datasets=0 but 2026-04-02 has products=FAIL, customers=WARN [dqs-serve/src/serve/schema/fixtures.sql:15] — Fixed
- [x] [Review][Patch] Cursor objects never closed in test methods — no context manager used [dqs-serve/tests/test_schema/test_fixtures.py, all test methods] — Fixed

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

_none_

### Completion Notes List

_to be filled by dev agent_

### File List

_to be filled by dev agent_

## Change Log

- 2026-04-03: Story created. Status set to ready-for-dev.
