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
workflowType: testarch-atdd
inputDocuments:
  - _bmad-output/implementation-artifacts/4-2-fastapi-summary-lob-api-endpoints.md
  - _bmad-output/project-context.md
  - dqs-serve/src/serve/schema/ddl.sql
  - dqs-serve/src/serve/schema/views.sql
  - dqs-serve/src/serve/schema/fixtures.sql
  - dqs-serve/tests/conftest.py
  - dqs-serve/src/serve/main.py
  - dqs-serve/src/serve/db/models.py
  - dqs-serve/src/serve/db/engine.py
  - dqs-serve/pyproject.toml
tdd_phase: RED
story_id: 4-2-fastapi-summary-lob-api-endpoints
---

# ATDD Checklist — Epic 4, Story 4.2: FastAPI Summary & LOB API Endpoints

**Date:** 2026-04-03
**Author:** Sas
**Primary Test Level:** Backend (pytest, unit + integration)
**TDD Phase:** RED (all tests skipped — feature not yet implemented)

---

## Story Summary

Data stewards need REST API endpoints to power the quality dashboard.
This story adds `GET /api/summary`, `GET /api/lobs`, and `GET /api/lobs/{lob_id}/datasets`
to the dqs-serve FastAPI application. The endpoints aggregate DQS scores, dataset counts,
and per-check statuses from the `v_dq_run_active` and `v_dq_metric_numeric_active` views.

**As a** data steward
**I want** REST API endpoints for summary and LOB-level data
**So that** the dashboard can display overall quality status and LOB drill-down

---

## Acceptance Criteria

1. **AC1** — `GET /api/summary` returns aggregated LOB data: total datasets, healthy/degraded/critical counts, per-LOB aggregate DQS scores with trend data
2. **AC2** — Response uses snake_case JSON field names
3. **AC3** — `GET /api/lobs` returns all LOBs with aggregate scores, dataset counts, and status distributions
4. **AC4** — `GET /api/lobs/{lob_id}/datasets` returns all datasets within that LOB with DQS scores, trend sparkline data, and per-check status chips (freshness, volume, schema)
5. **AC5** — Results support a `time_range` query parameter (7d, 30d, 90d)
6. **AC6** — All queries use active-record views (`v_*_active`), never raw tables

---

## Failing Tests Created (RED Phase)

### Unit Tests (20 tests — no DB required, skip markers active)

**File:** `dqs-serve/tests/test_routes/test_summary.py`

- `TestSummaryEndpointRouteWiring::test_summary_endpoint_returns_200`
  - **Status:** RED — Route `/api/summary` not registered
  - **Verifies:** AC1 — endpoint returns HTTP 200

- `TestSummaryEndpointRouteWiring::test_summary_response_is_json`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC2 — Content-Type is application/json

- `TestSummaryEndpointRouteWiring::test_summary_response_has_snake_case_top_level_keys`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC2 — top-level JSON keys are snake_case (total_datasets, healthy_count, etc.)

- `TestSummaryEndpointRouteWiring::test_summary_lob_items_have_snake_case_keys`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC2 — LOB items in the `lobs` array have snake_case keys

- `TestSummaryEndpointRouteWiring::test_summary_lob_trend_is_list_of_floats`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC1 — `trend` field in each LOB item is list[float]

- `TestSummaryEndpointRouteWiring::test_summary_counts_are_non_negative_integers`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC1 — count fields are non-negative integers

- `TestSummaryEndpointRouteWiring::test_summary_pydantic_models_are_importable`
  - **Status:** RED — `src/serve/routes/summary.py` does not exist
  - **Verifies:** AC2 — `LobSummaryItem` and `SummaryResponse` Pydantic models exist

- `TestSummaryEndpointRouteWiring::test_summary_response_model_field_names_are_snake_case`
  - **Status:** RED — Module does not exist
  - **Verifies:** AC2 — Pydantic model field names are snake_case

**File:** `dqs-serve/tests/test_routes/test_lobs.py`

- `TestLobsListEndpointRouteWiring::test_lobs_endpoint_returns_200`
  - **Status:** RED — Route `/api/lobs` not registered
  - **Verifies:** AC3 — endpoint returns HTTP 200

- `TestLobsListEndpointRouteWiring::test_lobs_response_is_list`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC3 — response body is a JSON array

- `TestLobsListEndpointRouteWiring::test_lobs_items_have_snake_case_keys`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC2/AC3 — LOB items have snake_case keys

- `TestLobsListEndpointRouteWiring::test_lobs_pydantic_models_importable`
  - **Status:** RED — Module does not exist
  - **Verifies:** AC3 — `LobDetail` Pydantic model exists

- `TestLobsListEndpointRouteWiring::test_lob_detail_field_names_are_snake_case`
  - **Status:** RED — Module does not exist
  - **Verifies:** AC2 — `LobDetail` fields are snake_case

- `TestLobDatasetsEndpointRouteWiring::test_lob_datasets_endpoint_returns_200_for_valid_lob`
  - **Status:** RED — Route `/api/lobs/{lob_id}/datasets` not registered
  - **Verifies:** AC4 — endpoint is registered (not 404 from missing route)

- `TestLobDatasetsEndpointRouteWiring::test_lob_datasets_pydantic_models_importable`
  - **Status:** RED — Module does not exist
  - **Verifies:** AC4 — `DatasetInLob` and `LobDatasetsResponse` models exist

- `TestLobDatasetsEndpointRouteWiring::test_dataset_in_lob_field_names_are_snake_case`
  - **Status:** RED — Module does not exist
  - **Verifies:** AC2 — `DatasetInLob` fields are snake_case

- `TestLobDatasetsEndpointRouteWiring::test_lob_datasets_response_has_lob_id_and_datasets_keys`
  - **Status:** RED — Module does not exist
  - **Verifies:** AC4 — `LobDatasetsResponse` has `lob_id` and `datasets` fields

- `TestLobDatasetsNotFoundError::test_unknown_lob_returns_404`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC4 — 404 returned for unknown lob_id

- `TestLobDatasetsNotFoundError::test_unknown_lob_404_response_has_correct_error_format`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC4 — 404 body has `{detail: {detail: 'LOB not found', error_code: 'NOT_FOUND'}}`

- `TestLobDatasetsNotFoundError::test_unknown_lob_error_response_has_no_stack_trace`
  - **Status:** RED — Route not implemented
  - **Verifies:** AC4 — No stack traces in error responses (project-context.md rule)

### Integration Tests (37 tests — require Postgres, @pytest.mark.integration)

**File:** `dqs-serve/tests/test_routes/test_summary.py`

- `TestSummaryEndpointDataCorrectness` (9 tests)
  - Fixture data counts: total=6, healthy=2, degraded=2, critical=2
  - LOB_RETAIL: count=3, score=71.83, healthy=1/degraded=1/critical=1, trend list
  - Expired record exclusion (AC6 validation)
  - New active record reflection (AC6 view coverage)

- `TestSummaryEndpointViewUsage` (2 tests)
  - Expired record excluded from count
  - New active record reflected in count

**File:** `dqs-serve/tests/test_routes/test_lobs.py`

- `TestLobsListDataCorrectness` (6 tests)
  - All 3 LOBs present; per-LOB: dataset_count, aggregate_score, status distribution
  - LOB_COMMERCIAL score=30.00, LOB_LEGACY count=1 score=95.00

- `TestLobDatasetsDataCorrectness` (9 tests)
  - lob_id echoed in response, 3 datasets for LOB_RETAIL
  - sales_daily: PASS 98.50; products: FAIL 45.00; schema_status set
  - customers: WARN, volume_status=None (no VOLUME metric)
  - freshness_status derived from metric presence
  - trend is list[float], per-check statuses in valid set

- `TestLobDatasetsTimeRange` (5 tests)
  - 7d/30d/90d accepted; default=7d; sales_daily trend has 7 points (7d window)
  - Trend ordered oldest-to-newest

- `TestLobEndpointsViewUsage` (3 tests)
  - Expired LOBs excluded from /api/lobs list
  - Expired datasets excluded from /api/lobs/{lob_id}/datasets
  - NONEXISTENT lob returns 404 with seeded real DB

---

## Fixtures Created / Modified

### `dqs-serve/tests/conftest.py` — MODIFIED

Added `seeded_client` fixture:
- Builds on `db_conn` (DDL + views in transaction)
- Seeds `fixtures.sql` and commits so FastAPI's OWN session can read data
- Yields `TestClient(app)` for route testing
- Teardown via `db_conn` rollback removes DDL (drops tables)

### Existing Fixtures Reused

- `db_conn` — psycopg2 connection, DDL + views in transaction, rollback on teardown
- `client` — bare TestClient (no DB)
- `fixtures.sql` — 6 datasets across 3 LOBs (LOB_RETAIL/COMMERCIAL/LEGACY)

---

## Implementation Checklist

### Make unit tests pass (no DB required):

- [ ] Create `dqs-serve/src/serve/routes/__init__.py` (empty)
- [ ] Create `dqs-serve/src/serve/routes/summary.py` with:
  - [ ] `LobSummaryItem` Pydantic model (snake_case fields, trend: list[float])
  - [ ] `SummaryResponse` Pydantic model (snake_case fields)
  - [ ] `router = APIRouter()` and `GET /summary` endpoint skeleton
- [ ] Create `dqs-serve/src/serve/routes/lobs.py` with:
  - [ ] `LobDetail` Pydantic model
  - [ ] `DatasetInLob` Pydantic model (including freshness/volume/schema_status: Optional[str])
  - [ ] `LobDatasetsResponse` Pydantic model (lob_id + datasets)
  - [ ] `router = APIRouter()` with `GET /lobs` and `GET /lobs/{lob_id}/datasets` skeletons
- [ ] Create `dqs-serve/src/serve/db/session.py` with `get_db()` generator
- [ ] Modify `dqs-serve/src/serve/main.py`:
  - [ ] Include routers with `prefix="/api"`
  - [ ] Add custom 500 handler (no stack trace leaks)

### Make integration tests pass (real Postgres required):

- [ ] Implement `GET /api/summary` query logic:
  - [ ] Query `v_dq_run_active`, latest partition_date per dataset
  - [ ] Compute total_datasets (COUNT DISTINCT dataset_name)
  - [ ] Compute healthy/degraded/critical counts by check_status
  - [ ] Per-LOB aggregation (aggregate_score, status distribution)
  - [ ] Per-LOB 7d trend (daily AVG dqs_score from v_dq_run_active)
- [ ] Implement `GET /api/lobs` query logic:
  - [ ] DISTINCT lookup_codes → per-LOB aggregates
  - [ ] Return list[LobDetail]
- [ ] Implement `GET /api/lobs/{lob_id}/datasets` query logic:
  - [ ] Filter by lookup_code, latest run per dataset
  - [ ] Accept `time_range` param (7d default, 30d, 90d)
  - [ ] Sparkline trend via daily AVG within time_range window
  - [ ] Per-check statuses from `v_dq_metric_numeric_active` join
  - [ ] 404 with `{"detail": "LOB not found", "error_code": "NOT_FOUND"}` for unknown LOB

### Remove skip markers after each test passes:

1. Remove `@pytest.mark.skip` from structural/import tests first
2. Then remove from unit route tests (need route registered)
3. Then remove from integration data-correctness tests (need real query logic)
4. Run `uv run pytest -m integration` to verify green

---

## Running Tests

```bash
# Default suite (unit tests only, no Postgres required)
cd dqs-serve && uv run pytest tests/test_routes/test_summary.py tests/test_routes/test_lobs.py -v

# Integration suite (requires Postgres on localhost:5432)
cd dqs-serve && uv run pytest -m integration tests/test_routes/ -v

# All tests for the story
cd dqs-serve && uv run pytest tests/test_routes/ -v

# Run with DATABASE_URL override
DATABASE_URL=postgresql://postgres:localdev@localhost:5433/postgres uv run pytest -m integration tests/test_routes/ -v
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete)

- [x] 20 unit tests generated with `@pytest.mark.skip` (TDD red phase)
- [x] 37 integration tests generated with `@pytest.mark.skip` + `@pytest.mark.integration`
- [x] All tests assert EXPECTED behavior from acceptance criteria
- [x] `seeded_client` fixture added to `conftest.py`
- [x] Clear failure messages in each assert pointing to implementation steps
- [x] No stack trace assertions, no placeholder `assert True`

### GREEN Phase (DEV Team — Next Steps)

1. Create Pydantic models in `src/serve/routes/summary.py` and `src/serve/routes/lobs.py`
2. Remove `@pytest.mark.skip` from import/structural tests → verify they pass
3. Implement route skeletons in `main.py` → remove skip from route-wiring tests
4. Implement query logic → remove skip from integration tests
5. Run `uv run pytest -m integration` → verify all 37 integration tests pass

### REFACTOR Phase

- Extract common query helpers if routes share SQL patterns
- Consider `services/` layer extraction (deferred to Story 4.5 per dev notes)
- Verify no raw table queries (`dq_run`, `dq_metric_numeric`) in production code

---

## Critical Anti-Patterns Avoided (project-context.md)

- Tests verify `v_*_active` view usage (AC6) — raw table queries will fail the expired-record tests
- No `9999-12-31 23:59:59` hardcoded in test assertions — tests use DB operations
- No `useEffect`/`fetch` patterns (not applicable — Python backend)
- No `any` types (Python type hints enforced throughout)
- Error format tests verify `{"detail": ..., "error_code": "NOT_FOUND"}` — no stack traces

---

## Test Execution Evidence

**Initial Test Run (RED Phase Verification):**

```bash
cd dqs-serve && uv run pytest tests/test_routes/ -v
```

**Results:**
```
collected 57 items / 37 deselected / 20 selected
20 skipped (RED phase, all @pytest.mark.skip)
```

- Total unit tests: 20 (all SKIP — expected)
- Total integration tests: 37 (deselected by default, all SKIP when run)
- Status: RED phase confirmed

---

**Generated by BMad TEA Agent (bmad-testarch-atdd)** — 2026-04-03
