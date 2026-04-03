---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: '2026-04-03'
inputDocuments:
  - _bmad-output/implementation-artifacts/1-4-implement-configuration-enrichment-schema.md
  - _bmad-output/project-context.md
  - dqs-serve/tests/conftest.py
  - dqs-serve/tests/test_schema/test_metric_schema.py
  - dqs-serve/src/serve/db/models.py
  - dqs-serve/pyproject.toml
---

# ATDD Checklist: Story 1-4 — Implement Configuration & Enrichment Schema

## Step 1: Preflight & Context

- **Stack detected:** `backend` (Python/pytest, pyproject.toml present, no frontend indicators)
- **Test framework:** pytest with real Postgres (`conftest.py` `db_conn` fixture)
- **Execution mode:** sequential (single-agent, no subagent dispatch needed for backend)
- **Marker strategy:** `@pytest.mark.integration` — deselected from default suite by `addopts = -m 'not integration'` in `pyproject.toml`

## Step 2: Generation Mode

- **Mode selected:** AI Generation (backend stack — no browser recording)
- **Reason:** Pure DB schema story; acceptance criteria are fully specified; no UI components involved

## Step 3: Test Strategy

### Acceptance Criteria → Test Scenarios

| AC | Scenario | Level | Priority |
|----|----------|-------|----------|
| AC1 | `check_config` table exists in public schema | Integration | P0 |
| AC1 | `check_config.id` is SERIAL PRIMARY KEY | Integration | P0 |
| AC1 | All 7 `check_config` columns exist with correct types (parametrized) | Integration | P0 |
| AC1 | `expiry_date` DEFAULT = EXPIRY_SENTINEL | Integration | P0 |
| AC1 | `create_date` DEFAULT = NOW() | Integration | P0 |
| AC3 | `uq_check_config_dataset_pattern_check_type_expiry_date` exists | Integration | P0 |
| AC3 | Unique constraint covers exactly (dataset_pattern, check_type, expiry_date) | Integration | P0 |
| AC3 | Two active rows with same (dataset_pattern, check_type) raise UniqueViolation | Integration | P1 |
| AC5 | Row with `enabled = FALSE` can be inserted | Integration | P1 |
| AC2 | `dataset_enrichment` table exists in public schema | Integration | P0 |
| AC2 | `dataset_enrichment.id` is SERIAL PRIMARY KEY | Integration | P0 |
| AC2 | All 8 `dataset_enrichment` columns with correct types (parametrized) | Integration | P0 |
| AC2 | `expiry_date` DEFAULT = EXPIRY_SENTINEL | Integration | P0 |
| AC2 | `create_date` DEFAULT = NOW() | Integration | P0 |
| AC4 | `uq_dataset_enrichment_dataset_pattern_expiry_date` exists | Integration | P0 |
| AC4 | Unique constraint covers exactly (dataset_pattern, expiry_date) | Integration | P0 |
| AC4 | Two active rows with same `dataset_pattern` raise UniqueViolation | Integration | P1 |
| AC6 | Row with custom `lookup_code` can be inserted | Integration | P1 |
| AC2/AC6 | Partial row with only `custom_weights` (lookup_code=NULL, sla_hours=NULL) accepted | Integration | P1 |

**TDD Red Phase:** All tests will fail with `psycopg2.errors.UndefinedTable` until the DDL is appended to `ddl.sql`.

## Step 4: Test Generation (Sequential — Backend)

- **Worker A (API/DB tests):** Generated directly — no API endpoints in this story; all tests are DB integration tests against real Postgres
- **Worker B (E2E tests):** Skipped — `detected_stack = backend`; no browser-based testing needed

## Step 4C: Aggregation

### TDD Red Phase Compliance

- All tests use `@pytest.mark.integration` — deselected from default suite (NOT skipped)
- Tests will FAIL in red phase because `check_config` and `dataset_enrichment` tables don't exist in `ddl.sql` yet
- No `@pytest.mark.skip` used anywhere
- No hardcoded `'9999-12-31 23:59:59'` — all uses of sentinel import `EXPIRY_SENTINEL` from `serve.db.models`
- Import order correct: stdlib (`__future__`, `typing`) → third-party (`pytest`) → local (`psycopg2` in TYPE_CHECKING, inline imports with `# noqa`)

### Generated Files

- `dqs-serve/tests/test_schema/test_config_enrichment_schema.py` — 32 collected tests (including parametrize expansions)

### Test Count Breakdown

| Class | Tests (base) | Parametrize expansions | Total collected |
|-------|-------------|------------------------|-----------------|
| `TestCheckConfigTableExists` | 4 base + 1 parametrized×7 | 7 | 11 |
| `TestCheckConfigUniqueConstraint` | 3 | 0 | 3 |
| `TestCheckConfigFunctional` | 1 | 0 | 1 |
| `TestDatasetEnrichmentTableExists` | 4 base + 1 parametrized×8 | 8 | 12 |
| `TestDatasetEnrichmentUniqueConstraint` | 3 | 0 | 3 |
| `TestDatasetEnrichmentFunctional` | 2 | 0 | 2 |
| **Total** | | | **32** |

## Step 5: Validate & Complete

### Checklist

- [x] Test file created at correct path: `dqs-serve/tests/test_schema/test_config_enrichment_schema.py`
- [x] `__init__.py` already present — NOT recreated
- [x] All tests marked `@pytest.mark.integration`
- [x] No `@pytest.mark.skip` used
- [x] `from __future__ import annotations` present
- [x] `TYPE_CHECKING` guard for psycopg2 type hints
- [x] Import order: stdlib → third-party → local
- [x] `EXPIRY_SENTINEL` imported from `serve.db.models` — never hardcoded
- [x] Uses `db_conn` fixture from `conftest.py` — no new DB fixture created
- [x] `conftest.py` NOT modified
- [x] `pyproject.toml` NOT modified
- [x] All 6 acceptance criteria covered by at least one test
- [x] Story status updated to `in-progress`

### Acceptance Criteria Coverage

| AC | Covered By |
|----|-----------|
| AC1 (`check_config` table + columns) | `TestCheckConfigTableExists` (11 tests) |
| AC2 (`dataset_enrichment` table + columns) | `TestDatasetEnrichmentTableExists` (12 tests) |
| AC3 (`check_config` unique constraint) | `TestCheckConfigUniqueConstraint` (3 tests) |
| AC4 (`dataset_enrichment` unique constraint) | `TestDatasetEnrichmentUniqueConstraint` (3 tests) |
| AC5 (`enabled = FALSE` row) | `TestCheckConfigFunctional` (1 test) |
| AC6 (`lookup_code` override row) | `TestDatasetEnrichmentFunctional` (2 tests) |

### Next Steps (TDD Green Phase)

1. Append `CREATE TABLE check_config (...)` and `CREATE TABLE dataset_enrichment (...)` to `dqs-serve/src/serve/schema/ddl.sql`
2. Run: `cd dqs-serve && uv run pytest -m integration -v tests/test_schema/test_config_enrichment_schema.py`
3. All 32 tests should PASS (green phase)
4. Verify zero regressions: `uv run pytest -m integration -v` (full integration suite = 54 + 32 = 86 tests)
5. Commit DDL + test file together per project git pattern
