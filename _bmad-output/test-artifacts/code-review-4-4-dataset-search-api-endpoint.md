# Code Review Report: Story 4-4 — Dataset Search API Endpoint

**Date:** 2026-04-03
**Reviewer:** Senior Developer Agent (claude-sonnet-4-6)
**Story:** `4-4-dataset-search-api-endpoint`
**Status:** All findings resolved. Story marked `done`.

---

## Summary

4 findings (1 Medium, 3 Low severity) were identified and resolved. All 73 unit tests pass with 0 skipped and 0 failures. `ruff check` reports no errors.

The implementation is clean and correct. It follows all established patterns from `datasets.py` and `lobs.py`, correctly queries `v_dq_run_active` via ROW_NUMBER() CTE, uses ILIKE for case-insensitive matching, implements prefix-first ordering, caps results at LIMIT 10, returns empty array (not 404) for no-match, and exposes all required snake_case fields.

---

## Review Layers

- **Blind Hunter** (adversarial general): 3 ruff lint issues (F401, I001 x2, F541) + stale docstring
- **Edge Case Hunter** (boundary conditions): No actionable findings — `if not q: return SearchResponse(results=[])` correctly handles empty string; bind params `{"q": q, "q_prefix": q + "%"}` prevent SQL injection
- **Acceptance Auditor** (AC compliance): All 3 acceptance criteria satisfied; stale TDD RED PHASE docstring identified

---

## Findings and Resolutions

### Medium Priority

**[Med-1] Stale TDD RED PHASE docstring and dead `if TYPE_CHECKING: pass` block**
- **File:** `dqs-serve/tests/test_routes/test_search.py:1-30`
- **Problem:** The module docstring claimed "TDD RED PHASE: tests will fail because the endpoint does not exist yet" even though the implementation was complete and all tests passing. Additionally, after removing the unused `psycopg2.extensions` import (ruff F401), the `if TYPE_CHECKING:` block became empty dead code. Both were misleading and noise.
- **Fix:** Removed the stale TDD RED PHASE header paragraphs and the entire dead `if TYPE_CHECKING: pass` block. The module docstring now accurately reflects the implemented state.
- **Resolution:** Fixed.

### Low Priority

**[Low-1] Ruff F401: unused `psycopg2.extensions` import under TYPE_CHECKING block**
- **File:** `dqs-serve/tests/test_routes/test_search.py:30`
- **Problem:** `import psycopg2.extensions` was present inside `if TYPE_CHECKING:` but was not referenced anywhere in the file (the `seeded_client` fixture type hint in the integration test methods used `TestClient` from fastapi, not a psycopg2 type). Ruff F401.
- **Fix:** Removed by `ruff check --fix`.
- **Resolution:** Fixed.

**[Low-2] Ruff I001: import block unsorted/unformatted (2 occurrences)**
- **File:** `dqs-serve/tests/test_routes/test_search.py:346, 368`
- **Problem:** Two inline import blocks (`import inspect` + `from serve.routes.search import SearchResult`) inside test methods were flagged as unsorted per isort rules.
- **Fix:** Removed by `ruff check --fix`.
- **Resolution:** Fixed.

**[Low-3] Ruff F541: f-string without any placeholders**
- **File:** `dqs-serve/tests/test_routes/test_search.py:580`
- **Problem:** An assertion message used an f-string prefix (`f"GET /api/search?q=a returned no results..."`) but contained no format placeholders. Ruff F541.
- **Fix:** Removed extraneous `f` prefix by `ruff check --fix`.
- **Resolution:** Fixed.

---

## Architecture Compliance

The following critical rules from `project-context.md` were verified:

| Rule | Status |
|---|---|
| All queries use `v_dq_run_active` view (never raw `dq_run` table) | PASS |
| No hardcoded sentinel timestamp `9999-12-31 23:59:59` | PASS |
| No check-type-specific business logic in API layer | PASS |
| SQLAlchemy 2.0 style (`db.execute(text(...)).mappings().all()`) | PASS |
| All response fields are snake_case (`dataset_id`, `dataset_name`, `lob_id`, `dqs_score`, `check_status`) | PASS |
| No stack traces returned from endpoint (global 500 handler covers it) | PASS |
| Type hints on all function parameters and return types | PASS |
| No new top-level directories invented | PASS |
| Relative imports within package | PASS |
| Returns empty results (not 404) for no-match queries — AC3 | PASS |

---

## SQL Pattern Compliance

| Pattern | search.py | Reference (datasets.py / lobs.py) |
|---|---|---|
| Module docstring referencing active-record views | PASS | PASS |
| SQL constants as module-level `text()` objects | PASS | PASS |
| ROW_NUMBER() CTE for latest-run-per-dataset dedup | PASS | PASS |
| Bind parameters used (no string interpolation) | PASS | PASS |
| ILIKE with `'%' \|\| :q \|\| '%'` pattern | PASS | — |
| Prefix-first ordering via CASE WHEN sort_order | PASS | — |
| LIMIT 10 cap | PASS | — |
| `Pydantic BaseModel` + `ConfigDict(from_attributes=True)` | PASS | PASS |
| `router = APIRouter()` and `logger = logging.getLogger(__name__)` | PASS | PASS |
| Route handler using `Depends(get_db)` | PASS | PASS |

---

## Edge Cases Verified

| Scenario | Handling |
|---|---|
| `q` is empty string | `if not q: return SearchResponse(results=[])` before DB call |
| `q` parameter absent | FastAPI auto-validates — returns 422 Unprocessable Entity |
| No datasets match ILIKE | SQL returns empty rows; `SearchResponse(results=[])` returned (never 404) |
| `dqs_score` is NULL in DB | `Optional[float]` on `SearchResult.dqs_score` — serialized as JSON `null` |
| `lob_id` (`lookup_code`) is NULL for legacy rows | `Optional[str]` on `SearchResult.lob_id` — serialized as JSON `null` |
| Multiple historical runs for same dataset_name | ROW_NUMBER() PARTITION BY dataset_name returns only latest run |
| More than 10 matching datasets | LIMIT 10 in SQL caps results |
| Case-insensitive search (`q=Sales` vs `sales_daily`) | ILIKE handles case-insensitive match |
| Prefix match ordering | CASE WHEN dataset_name ILIKE :q_prefix THEN 0 ELSE 1 puts prefix results first |

---

## Acceptance Criteria Verification

| AC | Criteria | Status |
|---|---|---|
| AC1 | `GET /api/search?q=ue90` returns `dataset_name`, `lob_id`, `dqs_score`, `check_status` ordered prefix-first | PASS |
| AC2 | Maximum 10 results returned | PASS — LIMIT 10 in SQL |
| AC3 | No-match returns `{"results": []}` not a 4xx error | PASS — never raises HTTPException |

---

## Test Results

```
collected 155 items / 82 deselected (integration) / 73 selected
tests/test_routes/test_datasets.py  36 passed
tests/test_routes/test_lobs.py      8 passed
tests/test_routes/test_search.py    17 passed
tests/test_routes/test_summary.py   12 passed
====================== 73 passed, 0 skipped, 0 failed ======================
```

Integration tests (82) are excluded from the default suite per `pyproject.toml: addopts = -m 'not integration'`.

---

## Lint Results

```
uv run ruff check
All checks passed!
```

---

## Files Changed

| File | Change |
|---|---|
| `dqs-serve/src/serve/routes/search.py` | NEW — `GET /api/search` endpoint + `SearchResult` and `SearchResponse` Pydantic models + ROW_NUMBER() CTE SQL |
| `dqs-serve/src/serve/main.py` | MODIFIED (dev) — added `search_router` import and `include_router`; also added `datasets_router` (included in this diff) |
| `dqs-serve/tests/test_routes/test_search.py` | NEW (dev) — 17 unit tests + 13 integration tests; MODIFIED (review) — removed stale TDD docstring, dead TYPE_CHECKING block, fixed 4 ruff issues |
| `dqs-serve/tests/conftest.py` | MODIFIED (dev) — extended `_make_mock_db_session` with `q` param dispatch branch and `_FAKE_SEARCH_RESULT_ROW`; also added dataset/trend/orchestration fake rows |
