---
stepsCompleted:
  [
    'step-01-preflight-and-context',
    'step-02-generation-mode',
    'step-03-test-strategy',
    'step-04-generate-tests',
    'step-04c-aggregate',
    'step-05-validate-and-complete',
  ]
lastStep: 'step-05-validate-and-complete'
lastSaved: '2026-04-03'
workflowType: 'testarch-atdd'
inputDocuments:
  - '_bmad-output/implementation-artifacts/4-5-reference-data-resolution-caching.md'
  - '_bmad-output/project-context.md'
  - 'dqs-serve/tests/conftest.py'
  - 'dqs-serve/tests/test_routes/test_datasets.py'
  - 'dqs-serve/src/serve/main.py'
  - 'dqs-serve/src/serve/routes/datasets.py'
  - 'dqs-serve/src/serve/db/session.py'
  - '_bmad/tea/config.yaml'
---

# ATDD Checklist — Epic 4, Story 4.5: Reference Data Resolution & Caching

**Date:** 2026-04-03
**Author:** Sas
**Primary Test Level:** Unit + Integration (backend Python/pytest)
**Stack:** backend (Python 3.12, FastAPI 0.135, pytest)
**TDD Phase:** RED (all tests skipped — implementation pending)

---

## Story Summary

As a data steward, I want lookup codes resolved to LOB, owner, and classification names
in API responses, so that datasets are displayed with human-readable business context
instead of raw codes.

**As a** data steward
**I want** lookup codes resolved to LOB name, owner, and classification in API responses
**So that** datasets show human-readable business context instead of raw codes

---

## Acceptance Criteria

1. **AC1 [P0]:** Given the reference data service is configured — When the serve layer starts — Then it populates a LOB mapping cache from the lookup table
2. **AC2 [P0]:** Given the cache is populated — When an API response includes a lookup code — Then the code is resolved to LOB name, owner, and classification in the response
3. **AC3 [P0]:** Given the cache is older than 12 hours — When the refresh timer fires (twice daily) — Then the cache is refreshed from the source
4. **AC4 [P0]:** Given a lookup code with no mapping — When the API resolves it — Then LOB, owner, and classification fields return "N/A" (not null, not error)

---

## Test Strategy

### Stack Detection Result

- **Detected stack:** `backend`
- **Generation mode:** AI generation (backend project, no browser recording needed)
- **Test levels:** Unit + Integration (no E2E for pure backend)
- **Framework:** pytest with `@pytest.mark.skip` for RED phase (not `test.skip()` — that's JS/Playwright)

### AC → Test Level Mapping

| AC | Scenario | Level | Priority | Test File |
|---|---|---|---|---|
| AC1 | Cache populated on startup via lifespan | Integration | P0 | `test_reference_data.py` |
| AC1 | refresh() populates _cache from DB rows | Unit | P0 | `test_reference_data.py` |
| AC1 | refresh() queries v_lob_lookup_active (not raw table) | Unit | P0 | `test_reference_data.py` |
| AC1 | refresh() closes DB session after query | Unit | P1 | `test_reference_data.py` |
| AC1 | refresh() closes DB session on query error | Unit | P1 | `test_reference_data.py` |
| AC1 | refresh() updates _last_refresh timestamp | Unit | P1 | `test_reference_data.py` |
| AC2 | resolve() returns mapped LobMapping for known code | Unit | P0 | `test_reference_data.py` |
| AC2 | GET /api/datasets/{id} includes lob_name, owner, classification | Unit (route) | P0 | `test_datasets.py` |
| AC2 | GET /api/datasets/{id} integration with real DB | Integration | P0 | `test_reference_data.py` |
| AC2 | DatasetDetail Pydantic model has new fields | Unit | P0 | `test_datasets.py` |
| AC2 | get_reference_data_service dependency works | Unit | P1 | `test_reference_data.py` |
| AC3 | _maybe_refresh() triggers when cache > 12h old | Unit | P0 | `test_reference_data.py` |
| AC3 | _maybe_refresh() skips when cache < 12h old | Unit | P0 | `test_reference_data.py` |
| AC3 | _maybe_refresh() boundary: exactly 12h does NOT refresh | Unit | P1 | `test_reference_data.py` |
| AC4 | resolve(None) returns N/A LobMapping | Unit | P0 | `test_reference_data.py` |
| AC4 | resolve('UNKNOWN') returns N/A LobMapping | Unit | P0 | `test_reference_data.py` |
| AC4 | resolve() never raises exceptions | Unit | P0 | `test_reference_data.py` |
| AC4 | DatasetDetail fields never null | Unit (route) | P0 | `test_datasets.py` |

---

## Failing Tests Created (RED Phase)

### Service Unit Tests (22 tests — all skipped)

**File:** `dqs-serve/tests/test_services/test_reference_data.py`

#### TestReferenceDataServiceResolveNone
- **test_resolve_returns_na_for_none_code** [P0]
  - Status: RED — `ModuleNotFoundError: serve.services.reference_data` does not exist
  - Verifies: AC4 — resolve(None) returns LobMapping('N/A', 'N/A', 'N/A')

#### TestReferenceDataServiceResolveUnknown
- **test_resolve_returns_na_for_unknown_code** [P0]
  - Status: RED — module does not exist
  - Verifies: AC4 — unknown code returns N/A after empty cache refresh

- **test_resolve_does_not_raise_for_unknown_code** [P0]
  - Status: RED — module does not exist
  - Verifies: AC4 — service never raises HTTPException or KeyError

#### TestReferenceDataServiceResolveKnown
- **test_resolve_returns_cached_mapping** [P0]
  - Status: RED — module does not exist
  - Verifies: AC2 — resolve returns correct LobMapping from pre-populated _cache

#### TestReferenceDataServiceCacheTTL
- **test_maybe_refresh_triggers_on_stale_cache** [P0]
  - Status: RED — module does not exist
  - Verifies: AC3 — _maybe_refresh() calls refresh() when cache is 13h old

- **test_maybe_refresh_skips_on_fresh_cache** [P0]
  - Status: RED — module does not exist
  - Verifies: AC3 — _maybe_refresh() does NOT call refresh() when cache is 1h old

- **test_maybe_refresh_skips_on_exactly_12h_boundary** [P1]
  - Status: RED — module does not exist
  - Verifies: AC3 — boundary condition: exactly 12h is NOT stale (strictly greater than)

#### TestReferenceDataServiceRefresh
- **test_refresh_populates_cache_from_db** [P0]
  - Status: RED — module does not exist
  - Verifies: AC1 — refresh() correctly maps rows to LobMapping objects in _cache

- **test_refresh_updates_last_refresh_timestamp** [P1]
  - Status: RED — module does not exist
  - Verifies: AC1 — _last_refresh is updated to approximately now after refresh

- **test_refresh_closes_db_session** [P1]
  - Status: RED — module does not exist
  - Verifies: AC1 — db.close() called after query (no session leak)

- **test_refresh_closes_db_session_on_query_error** [P1]
  - Status: RED — module does not exist
  - Verifies: AC1 — db.close() called in finally block even when query raises

- **test_refresh_queries_active_view_not_raw_table** [P0]
  - Status: RED — module does not exist
  - Verifies: AC1 — SQL references v_lob_lookup_active, not raw lob_lookup table

#### TestLobMappingDataclass
- **test_lob_mapping_is_importable** [P0]
  - Status: RED — module does not exist
  - Verifies: AC2 — LobMapping has lob_name, owner, classification fields

- **test_lob_mapping_is_frozen** [P1]
  - Status: RED — module does not exist
  - Verifies: AC2 — LobMapping is immutable (frozen=True)

- **test_reference_data_service_is_importable** [P0]
  - Status: RED — module does not exist
  - Verifies: AC1 — ReferenceDataService class exists

#### TestDatasetDetailWithResolvedFields (route unit tests — these use TestClient)
- **test_dataset_detail_has_lob_name_field** [P0]
  - Status: RED — field not in DatasetDetail response
  - Verifies: AC2 — response includes 'lob_name' string key

- **test_dataset_detail_has_owner_field** [P0]
  - Status: RED — field not in DatasetDetail response
  - Verifies: AC2 — response includes 'owner' string key

- **test_dataset_detail_has_classification_field** [P0]
  - Status: RED — field not in DatasetDetail response
  - Verifies: AC2 — response includes 'classification' string key

- **test_dataset_detail_resolved_fields_are_snake_case** [P1]
  - Status: RED — fields not present
  - Verifies: AC2 — all three fields are snake_case per project-context.md

- **test_dataset_detail_resolved_fields_never_null** [P0]
  - Status: RED — fields not present
  - Verifies: AC4 — fields are never null (always str)

#### TestGetReferenceDataServiceDependency
- **test_dependencies_module_is_importable** [P1]
  - Status: RED — `serve.dependencies` does not exist
  - Verifies: AC2 — dependencies.py exports get_reference_data_service

- **test_get_reference_data_service_returns_service_from_app_state** [P1]
  - Status: RED — `serve.dependencies` does not exist
  - Verifies: AC2 — function returns request.app.state.reference_data

### Integration Tests (3 tests — all skipped)
- **test_refresh_reads_from_lob_lookup_view** [P0, integration]
  - Status: RED — lob_lookup table/view don't exist, service doesn't exist
  - Verifies: AC1 — real DB refresh populates cache with seeded LOBs

- **test_dataset_detail_includes_resolved_names** [P0, integration]
  - Status: RED — lob_lookup table/view/service not implemented
  - Verifies: AC2 — real GET /api/datasets/1 returns resolved lob_name='Retail Banking'

- **test_dataset_detail_returns_na_for_null_lookup_code** [P1, integration]
  - Status: RED — service not implemented
  - Verifies: AC4 — NULL lookup_code returns N/A fields

### Route Tests in test_datasets.py (11 tests — all skipped)

**File:** `dqs-serve/tests/test_routes/test_datasets.py`

#### TestDatasetDetailResolvedFields
- **test_dataset_detail_has_all_4_5_fields** [P0] — response missing lob_name/owner/classification
- **test_dataset_detail_lob_name_is_string** [P0] — field not present
- **test_dataset_detail_owner_is_string** [P0] — field not present
- **test_dataset_detail_classification_is_string** [P0] — field not present
- **test_dataset_detail_resolved_fields_not_null** [P0] — fields not present
- **test_dataset_detail_mock_returns_retail_banking_for_lob_retail** [P1] — mock not wired
- **test_dataset_detail_full_key_set_after_4_5** [P1] — new fields missing

#### TestDatasetDetailPydanticModelAfter45
- **test_dataset_detail_model_has_lob_name_field** [P0] — DatasetDetail missing field
- **test_dataset_detail_model_has_owner_field** [P0] — DatasetDetail missing field
- **test_dataset_detail_model_has_classification_field** [P0] — DatasetDetail missing field

#### TestLifespanAndServiceWiring
- **test_app_has_lifespan_that_sets_reference_data_state** [P0] — lifespan not implemented

---

## Fixture Needs

### Existing (already available in conftest.py)
- `client` — TestClient (unit tests, mock DB)
- `seeded_client` — TestClient with real Postgres + fixtures.sql seeded
- `db_conn` — raw psycopg2 connection for integration tests
- `override_db_dependency` — autouse mock DB for unit tests

### New (to add to conftest.py — Task 8)
- `mock_reference_data_service` — autouse fixture that sets `app.state.reference_data`
  to a MagicMock returning `LobMapping('Retail Banking', 'Jane Doe', 'Tier 1 Critical')`
  for unit tests (no `@pytest.mark.integration` marker)

### Fixture Data (to add to fixtures.sql — Task 2)
- `lob_lookup` rows: LOB_RETAIL, LOB_COMMERCIAL, LOB_LEGACY with sentinel expiry_date

---

## Schema Requirements (RED Phase Reminders)

The following schema changes must be made before integration tests can pass:

1. **ddl.sql** — Add `lob_lookup` table (Task 1)
2. **views.sql** — Add `v_lob_lookup_active` view (Task 1)
3. **fixtures.sql** — Add lob_lookup seed rows (Task 2)
4. **conftest.py cleanup block** — Add DROP TABLE/VIEW for lob_lookup (Task 1)

---

## Implementation Checklist (GREEN Phase)

Use this to track TDD green phase progress. Work one test at a time.

### P0 Tests — Implement First

- [ ] **Task 1**: Add `lob_lookup` table to ddl.sql + v_lob_lookup_active to views.sql
  - Makes green: `test_refresh_reads_from_lob_lookup_view` (after Tasks 2+3)
  - Run: `cd dqs-serve && uv run pytest -m integration tests/test_services/test_reference_data.py::TestReferenceDataServiceIntegration::test_refresh_reads_from_lob_lookup_view`

- [ ] **Task 2**: Add lob_lookup fixture rows to fixtures.sql
  - Run after Task 1 + Task 3

- [ ] **Task 3**: Implement `ReferenceDataService` in services/reference_data.py
  - Makes green: all `TestReferenceDataServiceRefresh`, `TestReferenceDataServiceResolveNone`,
    `TestReferenceDataServiceResolveUnknown`, `TestReferenceDataServiceResolveKnown`,
    `TestReferenceDataServiceCacheTTL`, `TestLobMappingDataclass` tests
  - Run: `cd dqs-serve && uv run pytest tests/test_services/test_reference_data.py`

- [ ] **Task 4**: Wire ReferenceDataService into main.py lifespan
  - Makes green: `TestLifespanAndServiceWiring::test_app_has_lifespan_that_sets_reference_data_state`
  - Run: `cd dqs-serve && uv run pytest tests/test_routes/test_datasets.py::TestLifespanAndServiceWiring`

- [ ] **Task 5**: Create dependencies.py with get_reference_data_service
  - Makes green: `TestGetReferenceDataServiceDependency` tests
  - Run: `cd dqs-serve && uv run pytest tests/test_services/test_reference_data.py::TestGetReferenceDataServiceDependency`

- [ ] **Task 6**: Add lob_name/owner/classification to DatasetDetail + inject ref_svc in route
  - Makes green: `TestDatasetDetailPydanticModelAfter45` tests
  - Run: `cd dqs-serve && uv run pytest tests/test_routes/test_datasets.py::TestDatasetDetailPydanticModelAfter45`

- [ ] **Task 7**: Already done (this ATDD step created the test_services tests)

- [ ] **Task 8**: Update conftest.py with mock_reference_data_service autouse fixture + lob_lookup cleanup
  - Makes green: all `TestDatasetDetailResolvedFields` and `TestDatasetDetailWithResolvedFields` tests
  - Run: `cd dqs-serve && uv run pytest tests/test_routes/test_datasets.py::TestDatasetDetailResolvedFields`

---

## Running Tests

```bash
# Run all Story 4.5 unit tests (no DB needed)
cd dqs-serve && uv run pytest tests/test_services/test_reference_data.py -v

# Run Story 4.5 integration tests (requires running Postgres)
cd dqs-serve && uv run pytest -m integration tests/test_services/test_reference_data.py -v

# Run Story 4.5 route unit tests in test_datasets.py
cd dqs-serve && uv run pytest tests/test_routes/test_datasets.py::TestDatasetDetailResolvedFields tests/test_routes/test_datasets.py::TestDatasetDetailPydanticModelAfter45 tests/test_routes/test_datasets.py::TestLifespanAndServiceWiring -v

# Run full test suite (excluding integration)
cd dqs-serve && uv run pytest tests/ -v

# Run full test suite including integration
cd dqs-serve && uv run pytest tests/ -v -m "integration or not integration"

# Verify existing tests still pass (regression check)
cd dqs-serve && uv run pytest tests/test_routes/ tests/test_schema/ -v
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) ✅

- ✅ 22 failing unit/integration tests created in `tests/test_services/test_reference_data.py`
- ✅ 11 failing route tests appended to `tests/test_routes/test_datasets.py`
- ✅ All tests marked `@pytest.mark.skip` (TDD red phase)
- ✅ All tests assert EXPECTED behavior from acceptance criteria
- ✅ All 33 tests collected and verified as SKIPPED (not failing with errors)
- ✅ Existing 36 passing tests in test_datasets.py remain unbroken
- ✅ Fixture needs documented (mock_reference_data_service for conftest.py)
- ✅ Schema requirements documented (lob_lookup table/view/fixtures)

**Verification:**

```
22 skipped (test_services) + 11 skipped (test_datasets) = 33 RED phase tests
0 tests unexpectedly failing (skip = intended)
36 existing tests passing (no regression)
```

---

### GREEN Phase (DEV Team — Next Steps)

1. Pick one failing test from implementation checklist (start with P0)
2. Read the test to understand expected behavior
3. Implement minimal code to make that specific test pass
4. Remove its `@pytest.mark.skip` and run: `uv run pytest <specific_test>`
5. Verify it passes (green)
6. Check off the task in implementation checklist
7. Move to the next test

**Key order:** Tasks 1 → 2 → 3 → 4 → 5 → 6 → 8 (Task 7 = this ATDD step, already done)

---

### REFACTOR Phase (After All Tests Pass)

1. Verify all 33 Story 4.5 tests pass plus all existing tests pass
2. Run `uv run ruff check --fix` (required per story dev notes)
3. Review code for quality (thread safety, error handling, logging)
4. Ensure tests still pass after any refactoring
5. Update story status to 'done' in sprint-status.yaml

---

## Anti-Patterns Documented

From project-context.md and story dev notes:

- **NEVER** query `lob_lookup` raw table — always `v_lob_lookup_active`
  - Verified by: `test_refresh_queries_active_view_not_raw_table`
- **NEVER** use `session.query()` — use SQLAlchemy 2.0 `db.execute(text(...))`
- **NEVER** raise `HTTPException` from `ReferenceDataService` — return N/A
  - Verified by: `test_resolve_does_not_raise_for_unknown_code`
- **NEVER** use `@app.on_event('startup')` — use `lifespan` context manager
  - Verified by: `test_app_has_lifespan_that_sets_reference_data_state`
- **NEVER** pass `ReferenceDataService` as a per-request `Depends()` constructor
  - It is a singleton on `app.state`, not created per request
- **NEVER** hardcode `'9999-12-31 23:59:59'` in service code — the view handles it
- **NEVER** add LOB name resolution to lobs.py/summary.py/search.py — only datasets.py

---

## Next Steps

1. Share this checklist with the dev workflow (see `_bmad-output/implementation-artifacts/4-5-reference-data-resolution-caching.md`)
2. Run failing tests to confirm RED phase: `cd dqs-serve && uv run pytest tests/test_services/ tests/test_routes/test_datasets.py -v`
3. Begin implementation with Task 1 (schema) per the implementation checklist above
4. Work one test at a time: red → green for each test
5. When all 33 Story 4.5 tests pass, run ruff and full test suite
6. Update `sprint-status.yaml` to mark story as done

---

## Knowledge Base References Applied

- **data-factories.md** — Factory patterns for Python test data
- **test-quality.md** — Test isolation, determinism, no hard waits
- **test-levels-framework.md** — Unit vs Integration level selection (backend stack)
- **component-tdd.md** — TDD red-green-refactor cycle
- **test-healing-patterns.md** — Resilient assertions with informative failure messages
- **api-testing-patterns.md** — FastAPI TestClient patterns, mock DB injection

---

**Generated by BMad TEA Agent** — 2026-04-03
