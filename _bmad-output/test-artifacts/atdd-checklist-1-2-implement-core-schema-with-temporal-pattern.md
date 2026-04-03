---
stepsCompleted: ['step-01-preflight-and-context', 'step-02-generation-mode', 'step-03-test-strategy', 'step-04-generate-tests', 'step-04c-aggregate', 'step-05-validate-and-complete']
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-03'
workflowType: 'testarch-atdd'
inputDocuments:
  - _bmad-output/implementation-artifacts/1-2-implement-core-schema-with-temporal-pattern.md
  - _bmad-output/planning-artifacts/epics/epic-1-project-foundation-data-model.md
  - _bmad-output/project-context.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad/tea/config.yaml
---

# ATDD Checklist - Epic 1, Story 1.2: Implement Core Schema with Temporal Pattern

**Date:** 2026-04-03
**Author:** BMad TEA Agent
**Primary Test Level:** Structural (no DB) + Integration (real Postgres)
**TDD Phase:** RED — structural tests fail, integration tests deselected until DB available

---

## Story Summary

As a platform operator, I want the core `dq_run` table with the temporal pattern
(create_date/expiry_date as TIMESTAMP, sentinel `9999-12-31 23:59:59`, composite unique
constraint on natural keys + expiry_date), so that every quality run is auditable
with no hard deletes from the start.

---

## Stack Detection

- **Detected stack:** `backend` (Python/FastAPI, pyproject.toml, no playwright.config.ts)
- **Test framework:** pytest (dqs-serve)
- **Generation mode:** AI generation (no browser recording needed for backend)
- **E2E tests:** N/A — pure backend/schema story

---

## Acceptance Criteria

1. **Given** the Postgres database is running **When** the DDL script is executed **Then** the `dq_run` table exists with columns: `id` (serial PK), `dataset_name`, `partition_date`, `lookup_code`, `check_status`, `dqs_score`, `rerun_number`, `orchestration_run_id`, `error_message`, `create_date` (TIMESTAMP), `expiry_date` (TIMESTAMP DEFAULT `'9999-12-31 23:59:59'`)
2. **And** a composite unique constraint exists on `(dataset_name, partition_date, rerun_number, expiry_date)`
3. **And** the `EXPIRY_SENTINEL` constant is defined in dqs-serve Python (`serve.db.models`)
4. **And** inserting two active records with the same natural key is rejected by the DB constraint

---

## Test Strategy

| AC | Test Class | Test Level | Requires DB | Priority |
|----|-----------|-----------|-------------|----------|
| AC3 | `TestExpirySentinelConstant` | Structural (no DB) | No | P1 |
| AC1 | `TestDqRunTableExists` | Integration (real Postgres) | Yes | P0 |
| AC2 + AC4 | `TestDqRunUniqueConstraint` | Integration (real Postgres) | Yes | P0 |

---

## Failing Tests Created (RED Phase)

### File: `dqs-serve/tests/test_schema/test_dq_run_schema.py`

#### TestExpirySentinelConstant (4 tests) — AC3, structural, no DB required

- **[P1]** `test_expiry_sentinel_constant_exists_in_models` — RED: `EXPIRY_SENTINEL` not in `models.py`
- **[P1]** `test_expiry_sentinel_has_correct_value` — RED: constant doesn't exist yet
- **[P1]** `test_expiry_sentinel_is_string_type` — RED: constant doesn't exist yet
- **[P1]** `test_no_hardcoded_sentinel_in_python_sources` — PASSES (no sentinel hardcoded yet — correct)

#### TestDqRunTableExists (9 tests) — AC1, `@pytest.mark.integration`, requires Postgres

- **[P0]** `test_dq_run_table_exists` — RED: ddl.sql is placeholder, table doesn't exist
- **[P0]** `test_dq_run_has_id_serial_pk` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[dataset_name-text-NO]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[partition_date-date-NO]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[lookup_code-text-YES]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[check_status-text-NO]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[dqs_score-numeric-YES]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[rerun_number-integer-NO]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[orchestration_run_id-integer-YES]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[error_message-text-YES]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[create_date-timestamp without time zone-NO]` — RED: table doesn't exist
- **[P0]** `test_dq_run_column_exists_with_correct_type[expiry_date-timestamp without time zone-NO]` — RED: table doesn't exist
- **[P0]** `test_dq_run_expiry_date_default_is_sentinel` — RED: table doesn't exist
- **[P0]** `test_dq_run_create_date_default_is_now` — RED: table doesn't exist
- **[P0]** `test_dq_run_rerun_number_default_is_zero` — RED: table doesn't exist

#### TestDqRunUniqueConstraint (5 tests) — AC2+AC4, `@pytest.mark.integration`, requires Postgres

- **[P0]** `test_unique_constraint_exists_with_correct_name` — RED: table/constraint doesn't exist
- **[P0]** `test_unique_constraint_covers_correct_columns` — RED: table/constraint doesn't exist
- **[P0]** `test_two_active_records_same_natural_key_rejected` — RED: table doesn't exist
- **[P0]** `test_active_and_expired_records_same_natural_key_allowed` — RED: table doesn't exist
- **[P1]** `test_rerun_number_differentiates_reruns` — RED: table doesn't exist

---

## Test Execution Evidence

### Default Suite (non-integration, TDD RED Phase Verification)

**Command:** `cd dqs-serve && uv run pytest tests/test_schema/ -q -rs -m "not integration"`

**Results:**
```
3 failed, 1 passed, 20 deselected in 0.23s
```

**Summary:**
- Structural FAILS: 3 (EXPIRY_SENTINEL constant not defined — correct RED phase)
- Structural PASS: 1 (`test_no_hardcoded_sentinel_in_python_sources` — no hardcoded sentinel exists yet, which is correct)
- Integration DESELECTED: 20 (require Postgres, deselected per pytest.ini `addopts = -m 'not integration'`)
- Status: RED phase verified

### Integration Suite (requires Postgres)

**Command:** `cd dqs-serve && uv run pytest tests/test_schema/ -q -rs -m "integration"`

**Expected results (pre-implementation):**
```
20 failed — table 'dq_run' does not exist
```

---

## Infrastructure Created

### New Files

| File | Purpose |
|------|---------|
| `dqs-serve/tests/test_schema/__init__.py` | Package init for pytest discovery |
| `dqs-serve/tests/test_schema/test_dq_run_schema.py` | ATDD tests for story 1-2 |
| `dqs-serve/conftest.py` | Root conftest — adds `src/` to sys.path |

### Modified Files

| File | Change |
|------|--------|
| `dqs-serve/tests/conftest.py` | Added `db_conn` fixture for integration tests |
| `dqs-serve/pyproject.toml` | Added dev deps (pytest, pytest-asyncio, httpx) + pytest config section |

---

## Data Factories / Fixtures

The `db_conn` fixture in `dqs-serve/tests/conftest.py`:
- Connects to Postgres via `DATABASE_URL` env var (default: `postgresql://postgres:localdev@localhost:5432/postgres`)
- Executes `ddl.sql` inside a transaction
- Yields the connection for the test
- Rolls back on teardown (clean state guaranteed)

No external data factories needed — tests use inline INSERT statements.

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

- 23 total tests written (3 structural failing, 1 structural passing guard, 20 integration deselected)
- Structural tests fail with clear assertion messages pointing to missing code
- Integration tests deselected from default suite per project-context.md rules
- `db_conn` fixture ready for integration tests

### GREEN Phase (DEV Agent — Next Steps)

1. **Task 1** — Write `dq_run` DDL in `dqs-serve/src/serve/schema/ddl.sql`
   - Creates the table and constraint per the exact schema in Dev Notes
   - Run: `uv run pytest tests/test_schema/ -m integration -v` to verify

2. **Task 2** — Add `EXPIRY_SENTINEL` to `dqs-serve/src/serve/db/models.py`
   - Run: `uv run pytest tests/test_schema/ -m "not integration" -v` to verify

3. **Task 3** — Create `dqs-spark/src/main/java/com/bank/dqs/model/DqsConstants.java`
   - No automated pytest test for Java (verified manually or with JUnit)

4. Run full green verification:
   ```
   # Structural tests (fast, no DB):
   cd dqs-serve && uv run pytest tests/test_schema/ -m "not integration" -v

   # Integration tests (requires docker-compose up postgres):
   cd dqs-serve && uv run pytest tests/test_schema/ -m integration -v
   ```

---

## Key Risks and Assumptions

- **DDL transaction rollback:** Postgres rolls back DDL (CREATE TABLE) inside an explicit transaction with `autocommit=False`. This is used in the `db_conn` fixture for test isolation. If this assumption breaks in a future Postgres version, tests would need `DROP TABLE` cleanup instead.
- **`db_conn` fixture scope:** Tests are isolated via `ROLLBACK` — each test gets a fresh schema. Multiple tests can modify the table within a test method safely.
- **Java DqsConstants.java:** Not tested by pytest. Structural verification (file exists, constant value correct) is left to the dev agent's implementation validation.
- **`httpx` added as dev dep:** Required by `fastapi[standard]` for `TestClient` — without it, the conftest import of `TestClient` would fail.

---

**Generated by BMad TEA Agent (bmad-testarch-atdd)** — 2026-04-03
