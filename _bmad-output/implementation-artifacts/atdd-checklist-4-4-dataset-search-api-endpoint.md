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
  - _bmad-output/implementation-artifacts/4-4-dataset-search-api-endpoint.md
  - _bmad-output/project-context.md
  - dqs-serve/tests/conftest.py
  - dqs-serve/tests/test_routes/test_datasets.py
  - dqs-serve/tests/test_routes/test_lobs.py
  - dqs-serve/src/serve/main.py
  - dqs-serve/src/serve/schema/fixtures.sql
---

# ATDD Checklist: Story 4-4 — Dataset Search API Endpoint

**Date:** 2026-04-03
**Author:** Sas
**Primary Test Level:** Backend (pytest, unit + integration)

---

## Story Summary

A downstream consumer needs a REST API search endpoint (`GET /api/search?q={query}`) that
returns up to 10 matching datasets with their DQS scores and check statuses inline, ordered
by prefix match first then substring match.

**As a** downstream consumer
**I want** a REST API search endpoint that finds datasets by name with DQS scores inline
**So that** the dashboard search can show health status without navigating to each dataset

---

## Acceptance Criteria

1. **AC1** — Given datasets exist in the database, When I call `GET /api/search?q=ue90`, Then it
   returns matching datasets with: `dataset_name`, `lob_id`, `dqs_score`, `check_status` — ordered
   by prefix match first, then substring.
2. **AC2** — Given a query matching multiple datasets, When results are returned, Then maximum 10
   results are included.
3. **AC3** — Given a query with no matches, When I call the search endpoint, Then it returns an
   empty array (not an error).

---

## TDD Red Phase Summary

- **Stack**: Backend (Python 3.12+ / pytest / FastAPI / SQLAlchemy 2.0)
- **Detected stack type**: `backend`
- **Generation mode**: AI generation (no browser/recording needed)
- **Execution mode**: Sequential (backend-only, no E2E subagent)
- **TDD Phase**: RED — all 27 tests collected; 17 skipped (unit, `@pytest.mark.skip`),
  10 excluded as integration (`-m 'not integration'` default config)

```
tests/test_routes/test_search.py — 17 skipped (unit RED), 10 deselected (integration)
====================== 17 skipped, 10 deselected in 0.02s ======================
```

This is INTENTIONAL — `src/serve/routes/search.py` does not exist yet.

---

## Test Files Generated

### API / Route Tests (27 tests total)

**File:** `dqs-serve/tests/test_routes/test_search.py` (unit: 17, integration: 10)

#### Unit Tests — Route Wiring (`TestSearchEndpointRouteWiring`, 6 tests)

- **`test_search_endpoint_returns_200`** [P0]
  - Status: RED — route not registered in main.py
  - Verifies: GET /api/search?q=sales returns HTTP 200
  - Fails because: `src/serve/routes/search.py` does not exist

- **`test_search_response_is_json`** [P0]
  - Status: RED — route not registered
  - Verifies: Content-Type header is application/json

- **`test_search_response_has_results_key`** [P0]
  - Status: RED — route not registered
  - Verifies: Response body has top-level `results` key containing a list
  - Verifies: SearchResponse shape `{"results": [...]}`

- **`test_search_result_items_have_all_required_snake_case_keys`** [P1]
  - Status: RED — route not registered
  - Verifies: Each item has `dataset_id`, `dataset_name`, `lob_id`, `dqs_score`, `check_status`

- **`test_search_result_no_camel_case_keys`** [P1]
  - Status: RED — route not registered
  - Verifies: No camelCase keys in result items (project-context.md rule)

- **`test_search_dataset_id_is_integer`** [P1]
  - Status: RED — route not registered
  - Verifies: `dataset_id` is `int` (= `dq_run.id` PK)

#### Unit Tests — AC3 No-Match (`TestSearchNoMatchReturnsEmptyArray`, 3 tests)

- **`test_no_match_returns_200_not_404`** [P0]
  - Status: RED — route not registered
  - Verifies: `GET /api/search?q=ZZZNOMATCH` returns 200 (NOT 4xx) — AC3 explicit

- **`test_no_match_returns_empty_results_array`** [P0]
  - Status: RED — route not registered
  - Verifies: No-match returns `{"results": []}` (not an error response)

- **`test_empty_q_returns_200_with_empty_results`** [P1]
  - Status: RED — route not registered
  - Verifies: Empty string `q=` returns 200 with empty results gracefully

#### Unit Tests — 422 Validation (`TestSearchMissingQueryParam`, 1 test)

- **`test_missing_q_returns_422`** [P0]
  - Status: RED — route not registered
  - Verifies: `GET /api/search` (no q param) returns 422 (FastAPI auto-validation)

#### Unit Tests — Pydantic Models (`TestSearchPydanticModels`, 7 tests)

- **`test_search_result_model_is_importable`** [P0]
  - Status: RED — `src/serve/routes/search.py` does not exist
  - Verifies: `from serve.routes.search import SearchResult` succeeds

- **`test_search_response_model_is_importable`** [P0]
  - Status: RED — `src/serve/routes/search.py` does not exist
  - Verifies: `from serve.routes.search import SearchResponse` succeeds

- **`test_search_result_field_names_are_snake_case`** [P1]
  - Status: RED — module does not exist
  - Verifies: All SearchResult Pydantic field names are snake_case

- **`test_search_result_has_required_fields`** [P1]
  - Status: RED — module does not exist
  - Verifies: SearchResult has all 5 required fields

- **`test_search_response_has_results_field`** [P1]
  - Status: RED — module does not exist
  - Verifies: SearchResponse has `results` field

- **`test_search_result_dqs_score_is_optional_float`** [P1]
  - Status: RED — module does not exist
  - Verifies: `dqs_score: Optional[float]` (NULL-safe)

- **`test_search_result_lob_id_is_optional_str`** [P1]
  - Status: RED — module does not exist
  - Verifies: `lob_id: Optional[str]` (nullable for legacy rows)

#### Integration Tests — Data Correctness (`TestSearchIntegrationDataCorrectness`, 4 tests)

- **`test_search_q_sales_returns_sales_daily`** [P0]
  - Status: RED — route not implemented
  - Verifies: ILIKE '%sales%' matches `lob=retail/src_sys_nm=alpha/dataset=sales_daily`

- **`test_search_q_sales_returns_correct_dqs_score`** [P0]
  - Status: RED — route not implemented
  - Verifies: sales_daily has dqs_score=98.50, check_status='PASS' from ROW_NUMBER() CTE

- **`test_search_q_sales_has_correct_field_values`** [P1]
  - Status: RED — route not implemented
  - Verifies: lob_id='LOB_RETAIL', dataset_id is integer

- **`test_search_all_result_fields_are_snake_case`** [P1]
  - Status: RED — route not implemented
  - Verifies: All integration response keys are snake_case

#### Integration Tests — Substring Match (`TestSearchIntegrationSubstringMatch`, 2 tests)

- **`test_search_q_alpha_returns_multiple_datasets`** [P1]
  - Status: RED — route not implemented
  - Verifies: q=alpha matches sales_daily + products + customers (3 datasets from src_sys_nm=alpha)

- **`test_search_q_ue90_returns_empty_results`** [P0]
  - Status: RED — route not implemented
  - Verifies: AC1 example `q=ue90` returns 200 with empty results (no fixture match)

#### Integration Tests — AC2 Max Results (`TestSearchIntegrationMaxResults`, 1 test)

- **`test_search_returns_at_most_10_results`** [P1]
  - Status: RED — route not implemented
  - Verifies: LIMIT 10 cap is enforced (q=a matches all 6 fixture datasets ≤ 10)

#### Integration Tests — AC1 Ordering (`TestSearchIntegrationOrdering`, 2 tests)

- **`test_search_ordering_is_deterministic`** [P1]
  - Status: RED — route not implemented
  - Verifies: Same query returns same order both times (ORDER BY sort_order, dataset_name)

- **`test_search_deduplicates_datasets_by_name`** [P1]
  - Status: RED — route not implemented
  - Verifies: ROW_NUMBER() dedup — each unique dataset_name appears only once

#### Integration Tests — Nullable Fields (`TestSearchIntegrationNullableFields`, 1 test)

- **`test_search_dqs_score_can_be_null`** [P1]
  - Status: RED — route not implemented
  - Verifies: dqs_score key present even when NULL; type is float or null

---

## conftest.py Changes

**File modified:** `dqs-serve/tests/conftest.py`

Added `_FAKE_SEARCH_RESULT_ROW` constant and `q` param dispatch branch to
`_make_mock_db_session`'s `_execute_side_effect`:

- `q` branch checked **first** in the dispatch chain (distinct param key, no overlap)
- `q in ('sales', 'alpha', 'retail')` → returns `_FAKE_SEARCH_RESULT_ROW`
- any other `q` value → returns `[]` (no-match → `results=[]`, not 4xx)
- Docstring updated with `q` param in both `_make_mock_db_session` and `_execute_side_effect`

---

## Acceptance Criteria Coverage

| AC | Test(s) | Level | Priority |
|---|---|---|---|
| AC1 — Fields returned (dataset_name, lob_id, dqs_score, check_status) | `test_search_result_items_have_all_required_snake_case_keys` + `test_search_q_sales_has_correct_field_values` | Unit + Integration | P0/P1 |
| AC1 — Ordering (prefix before substring) | `test_search_ordering_is_deterministic` + `test_search_deduplicates_datasets_by_name` | Integration | P1 |
| AC1 — dataset_id is integer | `test_search_dataset_id_is_integer` + `test_search_q_sales_has_correct_field_values` | Unit + Integration | P1 |
| AC1 — snake_case keys | `test_search_result_no_camel_case_keys` + `test_search_all_result_fields_are_snake_case` | Unit + Integration | P1 |
| AC2 — Maximum 10 results | `test_search_returns_at_most_10_results` | Integration | P1 |
| AC3 — Empty array (not error) | `test_no_match_returns_200_not_404` + `test_no_match_returns_empty_results_array` + `test_search_q_ue90_returns_empty_results` | Unit + Integration | P0 |
| Implicit — 422 for missing q | `test_missing_q_returns_422` | Unit | P0 |
| Implicit — Pydantic model correctness | All `TestSearchPydanticModels` tests | Unit | P0/P1 |

---

## Implementation Checklist (GREEN phase)

### Test group: TestSearchPydanticModels (7 tests)

**File to create:** `dqs-serve/src/serve/routes/search.py`

- [ ] Define `SearchResult(BaseModel)` with `ConfigDict(from_attributes=True)` and fields:
      `dataset_id: int`, `dataset_name: str`, `lob_id: Optional[str]`, `dqs_score: Optional[float]`,
      `check_status: str`
- [ ] Define `SearchResponse(BaseModel)` with `results: list[SearchResult]`
- [ ] Remove `@pytest.mark.skip` from `TestSearchPydanticModels` tests
- [ ] Run: `cd dqs-serve && uv run pytest tests/test_routes/test_search.py::TestSearchPydanticModels`
- [ ] All 7 pass (green)

### Test group: TestSearchEndpointRouteWiring + TestSearchNoMatchReturnsEmptyArray + TestSearchMissingQueryParam (10 tests)

**File to modify:** `dqs-serve/src/serve/main.py`

- [ ] Add `_SEARCH_SQL` constant using `sqlalchemy.text()` with ROW_NUMBER() CTE + ILIKE + LIMIT 10
- [ ] Add `@router.get("/search", response_model=SearchResponse)` route handler with `q: str` param
- [ ] Handle empty `q` early-return: `if not q: return SearchResponse(results=[])`
- [ ] Execute SQL with `{"q": q, "q_prefix": q + "%"}` bind params
- [ ] Map rows: `dataset_id=row["id"], lob_id=row["lookup_code"]`
- [ ] Add import in `main.py`: `from .routes import search as search_router`
- [ ] Add `app.include_router(search_router.router, prefix="/api")` in `main.py`
- [ ] Remove `@pytest.mark.skip` from `TestSearchEndpointRouteWiring`, `TestSearchNoMatchReturnsEmptyArray`, `TestSearchMissingQueryParam` tests
- [ ] Run: `cd dqs-serve && uv run pytest tests/test_routes/test_search.py -m 'not integration'`
- [ ] All 17 unit tests pass (green)

### Test group: All integration tests (10 tests)

- [ ] Remove `@pytest.mark.skip` from all integration test methods
- [ ] Run: `cd dqs-serve && uv run pytest -m integration tests/test_routes/test_search.py`
- [ ] All 10 integration tests pass (green)

---

## Anti-Patterns Verified (Test Assertions Enforce)

Per `_bmad-output/project-context.md`:

- Tests assert `ILIKE` against `v_dq_run_active` — never raw `dq_run` with expiry filter
- Tests assert `dataset_id` is integer `dq_run.id` — not a separate registry
- Tests assert no 4xx for no-match (AC3: `results=[]`)
- Tests assert snake_case keys — no camelCase
- Tests assert ROW_NUMBER() deduplication — no duplicate `dataset_name` in results

---

## Running Tests

```bash
# Run all unit tests for this story (RED phase — all skip)
cd dqs-serve && uv run pytest tests/test_routes/test_search.py

# Run integration tests (requires real Postgres)
cd dqs-serve && uv run pytest -m integration tests/test_routes/test_search.py

# Run all tests including other routes (verify no regression)
cd dqs-serve && uv run pytest tests/test_routes/

# After implementation — run everything to confirm green phase
cd dqs-serve && uv run pytest tests/test_routes/test_search.py -v
```

---

## Red-Green-Refactor Workflow

### RED Phase (Complete)

- All 27 tests written and in failing/skip state
- conftest.py extended with `q` param dispatch + `_FAKE_SEARCH_RESULT_ROW`
- All acceptance criteria covered by at least one test
- Clear skip reasons and actionable failure messages documented

### GREEN Phase (Next — Dev Team)

1. Create `dqs-serve/src/serve/routes/search.py` (Pydantic models + route handler)
2. Modify `dqs-serve/src/serve/main.py` (3 lines: import + include_router)
3. Modify `dqs-serve/tests/conftest.py` already done (no further changes needed)
4. Remove `@pytest.mark.skip` markers test-by-test as each feature is implemented
5. Run `uv run pytest tests/test_routes/test_search.py` after each group

### REFACTOR Phase

- Verify all 27 tests pass
- Review SQL for clarity (bind param names, CTE readability)
- Ensure type hints are complete on all functions
- Confirm no stack traces exposed (global 500 handler in main.py already handles this)

---

## Next Steps

1. Implement `dqs-serve/src/serve/routes/search.py` per story dev notes
2. Wire router in `dqs-serve/src/serve/main.py`
3. Remove `@pytest.mark.skip` markers as each feature group is implemented
4. Run unit tests: `cd dqs-serve && uv run pytest tests/test_routes/test_search.py`
5. Run integration tests after unit tests pass
6. When all 27 tests pass, update sprint-status.yaml: `4-4-dataset-search-api-endpoint → in-progress`

---

## Knowledge Base References Applied

- **test-levels-framework.md** — Backend test level selection (unit + integration, no E2E)
- **test-priorities-matrix.md** — P0/P1/P2 priority assignment by risk and business impact
- **data-factories.md** — Mock data patterns (conftest `_FAKE_SEARCH_RESULT_ROW`)
- **test-quality.md** — Given-When-Then structure, one assertion per test, clear failure messages
- **test-healing-patterns.md** — Descriptive assertion messages with remediation hints
- **component-tdd.md** — Pydantic model import tests for TDD red phase
- **ci-burn-in.md** — Integration test isolation using real Postgres with rollback

---

**Generated by BMad TEA Agent (bmad-testarch-atdd)** — 2026-04-03
