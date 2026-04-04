# Story 4.4: Dataset Search API Endpoint

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **downstream consumer**,
I want a REST API search endpoint that finds datasets by name with DQS scores inline,
so that the dashboard search can show health status without navigating to each dataset.

## Acceptance Criteria

1. **Given** datasets exist in the database **When** I call `GET /api/search?q=ue90` **Then** it returns matching datasets with: `dataset_name`, `lob_id`, `dqs_score`, `check_status` — ordered by prefix match first, then substring
2. **Given** a query matching multiple datasets **When** results are returned **Then** maximum 10 results are included
3. **Given** a query with no matches **When** I call the search endpoint **Then** it returns an empty array (not an error)

## Tasks / Subtasks

- [x] Task 1: Create Pydantic response models in new `dqs-serve/src/serve/routes/search.py` (AC: 1, 3)
  - [x] Create `dqs-serve/src/serve/routes/search.py` (new file)
  - [x] Define `SearchResult` model: `dataset_id`, `dataset_name`, `lob_id`, `dqs_score`, `check_status` — all snake_case
  - [x] Define `SearchResponse` model: `results: list[SearchResult]`
  - [x] All models inherit from `pydantic.BaseModel`; use `ConfigDict(from_attributes=True)` where appropriate
  - [x] `dqs_score` is `Optional[float]` — can be NULL in DB
  - [x] `lob_id` is `Optional[str]` — lookup_code can be NULL (legacy rows)

- [x] Task 2: Implement `GET /api/search` endpoint (AC: 1, 2, 3)
  - [x] Accept required query parameter `q: str` (minimum 1 character)
  - [x] Query `v_dq_run_active` for latest-run-per-dataset matching `dataset_name ILIKE '%' || :q || '%'` using ROW_NUMBER() CTE pattern
  - [x] Order results: prefix matches first (dataset_name ILIKE :q || '%'), then substring matches
  - [x] LIMIT 10 results total
  - [x] Return 200 with `SearchResponse` containing matched datasets (empty `results: []` when no matches — NOT 404)
  - [x] Return `dataset_id` = `dq_run.id` (consistent with existing `DatasetInLob.dataset_id` pattern)
  - [x] Handle empty `q` parameter gracefully: return empty results array

- [x] Task 3: Wire search router into `dqs-serve/src/serve/main.py` (AC: all)
  - [x] Add import: `from .routes import search as search_router`
  - [x] Add `app.include_router(search_router.router, prefix="/api")`
  - [x] Preserve all existing endpoints: `/health`, `/api/summary`, `/api/lobs`, `/api/lobs/{lob_id}/datasets`, `/api/datasets/{dataset_id}`, `/api/datasets/{dataset_id}/metrics`, `/api/datasets/{dataset_id}/trend`

- [x] Task 4: Write pytest tests (AC: all)
  - [x] Create `dqs-serve/tests/test_routes/test_search.py`
  - [x] `dqs-serve/tests/test_routes/__init__.py` already exists — do NOT recreate
  - [x] Unit tests (no `@pytest.mark.integration` marker) using `app.dependency_overrides` autouse fixture from `conftest.py`
  - [x] Integration tests marked `@pytest.mark.integration` using `seeded_client` fixture from `conftest.py`
  - [x] Test AC1: `GET /api/search?q=sales` returns 200 with `dataset_name`, `lob_id`, `dqs_score`, `check_status` fields (shape test)
  - [x] Test AC1: All response JSON fields are snake_case
  - [x] Test AC1: `dataset_id` field is present and is an integer
  - [x] Test AC1 (integration): prefix match `sales_daily` appears before substring match when both exist
  - [x] Test AC2: `GET /api/search?q=a` returns at most 10 results (integration: fixture has enough datasets to verify cap)
  - [x] Test AC3: `GET /api/search?q=ZZZNOMATCH` returns 200 with `{"results": []}` — NOT a 4xx error
  - [x] Test: missing `q` parameter returns 422 (FastAPI automatic validation)
  - [x] Test (integration): `GET /api/search?q=ue90` returns empty results (no `ue90` in fixtures — verifies no-match path)
  - [x] Test (integration): `GET /api/search?q=sales` returns `sales_daily` dataset with correct `dqs_score=98.50`
  - [x] Test (integration): `GET /api/search?q=alpha` returns datasets from `src_sys_nm=alpha` (substring match across LOBs)
  - [x] Extend `conftest.py` `_make_mock_db_session` `_execute_side_effect` to handle `q` param: return `_FAKE_SEARCH_RESULT_ROW` for known queries, empty list for unknown
  - [x] Update `conftest.py` docstring dispatch table to document `q` param handling

## Dev Notes

### search.py is a NEW route module — follow datasets.py / lobs.py pattern exactly

`dqs-serve/src/serve/routes/` already has `summary.py`, `lobs.py`, `datasets.py`. Create `search.py` with the same structure:
1. Module docstring referencing active-record views and project-context rules
2. `router = APIRouter()` and `logger = logging.getLogger(__name__)`
3. SQL constants defined at module level using `sqlalchemy.text()`
4. Pydantic models with `ConfigDict(from_attributes=True)`
5. Route handler using `Depends(get_db)` and SQLAlchemy 2.0 style (`db.execute(text(...)).mappings().all()`)

### `dataset_id` is `dq_run.id` — NOT a separate registry

No separate dataset table exists. `dataset_id` = `dq_run.id` — consistent with `DatasetInLob.dataset_id` in `lobs.py` and `DatasetDetail.dataset_id` in `datasets.py`. The dashboard search result navigates to `/dataset/{dataset_id}` using this same integer.

### Active-Record Views — CRITICAL

All queries MUST go through `v_dq_run_active`. NEVER query raw `dq_run` with manual expiry filters.

```sql
-- CORRECT
SELECT ... FROM v_dq_run_active WHERE dataset_name ILIKE ...

-- NEVER DO THIS (anti-pattern)
SELECT ... FROM dq_run WHERE expiry_date = '9999-12-31 23:59:59' AND ...
```

### Response Schema Specification

**`GET /api/search?q={query}`** — `SearchResponse`:
```json
{
  "results": [
    {
      "dataset_id": 7,
      "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
      "lob_id": "LOB_RETAIL",
      "dqs_score": 98.50,
      "check_status": "PASS"
    }
  ]
}
```

Notes:
- `dataset_id` = `dq_run.id` (PK of the latest run for this dataset)
- `lob_id` = `dq_run.lookup_code` (e.g., `"LOB_RETAIL"`) — can be `null` for legacy rows
- `dqs_score` = from `v_dq_run_active.dqs_score` — `Optional[float]` (NULL-safe)
- `check_status` = `dq_run.check_status` (PASS / WARN / FAIL)
- `results: []` when no match (NOT a 4xx error — AC3 is explicit)
- Maximum 10 results (LIMIT 10 in SQL — AC2)
- Results ordered: prefix matches first, then substring matches — see SQL below

### SQL Implementation — ROW_NUMBER() + ILIKE + Ordering

Use the ROW_NUMBER() CTE pattern established in `lobs.py` and `datasets.py` for latest-run-per-dataset. Then filter and order:

```sql
WITH ranked AS (
    SELECT
        id,
        dataset_name,
        lookup_code,
        check_status,
        dqs_score,
        ROW_NUMBER() OVER (
            PARTITION BY dataset_name
            ORDER BY partition_date DESC
        ) AS rn
    FROM v_dq_run_active
    WHERE dataset_name ILIKE '%' || :q || '%'
),
latest AS (
    SELECT id, dataset_name, lookup_code, check_status, dqs_score
    FROM ranked
    WHERE rn = 1
)
SELECT
    id,
    dataset_name,
    lookup_code,
    check_status,
    dqs_score,
    CASE
        WHEN dataset_name ILIKE :q_prefix THEN 0
        ELSE 1
    END AS sort_order
FROM latest
ORDER BY sort_order, dataset_name
LIMIT 10
```

Where `:q_prefix = q + '%'` (bind separately for prefix detection).

**Bind parameters:**
- `:q` = user query string (used inside `'%' || :q || '%'` for substring match)
- `:q_prefix` = `q + '%'` (bound in Python before executing, used for CASE sort)

Both bind with `{"q": q_value, "q_prefix": q_value + "%"}`.

**Why ILIKE:** Case-insensitive matching so `GET /api/search?q=Sales` matches `sales_daily`. Consistent with how dataset names are stored (lowercase Hive-style path segments).

**Why ROW_NUMBER():** Prevents duplicate dataset names when a dataset has multiple historical runs. Only the latest run (`partition_date DESC`) per dataset name is returned. Consistent with `datasets.py` and `lobs.py` patterns — DO NOT use correlated subquery or DISTINCT ON.

### Pydantic Models

```python
from pydantic import BaseModel, ConfigDict
from typing import Optional

class SearchResult(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    dataset_id: int
    dataset_name: str
    lob_id: Optional[str]       # lookup_code — nullable for legacy rows
    dqs_score: Optional[float]  # can be NULL in DB
    check_status: str

class SearchResponse(BaseModel):
    results: list[SearchResult]
```

- Use `Optional[str]` for `lob_id` (not `str`) — legacy datasets may have `NULL` lookup_code (e.g., fixture row `src_sys_nm=omni/dataset=customer_profile` has `lookup_code='LOB_LEGACY'` but other legacy cases may not)
- Use `Optional[float]` for `dqs_score` — matches pattern in `DatasetInLob` and `DatasetDetail`
- No `ConfigDict` required on `SearchResponse` itself (it doesn't map from DB rows directly)

### FastAPI Route Pattern

```python
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import text
from ..db.session import get_db

router = APIRouter()
logger = logging.getLogger(__name__)

@router.get("/search", response_model=SearchResponse)
def search_datasets(q: str, db: Session = Depends(get_db)) -> SearchResponse:
    """Search datasets by name with DQS score inline.
    
    Returns up to 10 matching datasets ordered by prefix match first.
    Returns empty results (not 404) when no datasets match.
    Queries v_dq_run_active — never raw dq_run table.
    """
    if not q:
        return SearchResponse(results=[])
    
    rows = db.execute(
        _SEARCH_SQL,
        {"q": q, "q_prefix": q + "%"},
    ).mappings().all()
    
    return SearchResponse(
        results=[
            SearchResult(
                dataset_id=row["id"],
                dataset_name=row["dataset_name"],
                lob_id=row["lookup_code"],
                dqs_score=row["dqs_score"],
                check_status=row["check_status"],
            )
            for row in rows
        ]
    )
```

**Note:** `q` declared as required query param (`q: str`) — FastAPI auto-generates 422 when absent. This is correct behavior (test it in the test suite).

### Directory Structure — Files to Create/Modify

```
dqs-serve/
  src/serve/
    routes/
      search.py       ← NEW: GET /api/search endpoint + Pydantic models
    main.py           ← MODIFY: add search_router import + include_router (3 lines)
  tests/
    test_routes/
      test_search.py  ← NEW: unit + integration tests for search endpoint
```

**Do NOT create:** any `services/` layer. Query goes directly in route handler using `sqlalchemy.text()`.

**Do NOT modify:** `schema/ddl.sql`, `schema/views.sql`, `schema/fixtures.sql`, `db/models.py`, `db/engine.py`, `db/session.py`, `routes/summary.py`, `routes/lobs.py`, `routes/datasets.py`. Only touch `main.py` (to wire the new router) and `tests/conftest.py` (to extend mock dispatch).

### conftest.py Mock Extension

The existing `_make_mock_db_session` in `tests/conftest.py` dispatches by param keys. Add `q` param handling:

```python
# In _execute_side_effect, add to the dispatch chain:
q = params.get("q") if params else None
q_prefix = params.get("q_prefix") if params else None

if q is not None:
    # Search query — return fake search result row for known queries, empty for unknown
    if q.lower() in ("sales", "alpha", "retail"):
        rows = [_FAKE_SEARCH_RESULT_ROW]
    else:
        rows = []  # no match → empty results array
```

Add to the fake row definitions:
```python
_FAKE_SEARCH_RESULT_ROW = {
    "id": 9,
    "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
    "lookup_code": "LOB_RETAIL",
    "dqs_score": 98.50,
    "check_status": "PASS",
}
```

The `q` branch should be checked BEFORE `dataset_id` and `lob_id` branches since it has its own distinct param key. The existing `days_back`, `dataset_id`, `dataset_name`, `lob_id`, `run_ids`, `dataset_names`, and `orchestration_run_id` branches are NOT affected — add `q` as a new branch.

Update the docstring's dispatch table in `_make_mock_db_session` and `_execute_side_effect` to document `q` param.

### Error Response Format

`GET /api/search` does NOT return 404 for no-match (AC3 is explicit: "returns an empty array, not an error"). The only non-200 response is 422 (FastAPI automatic validation) when `q` is missing.

For unexpected server errors, the global 500 handler in `main.py` already covers unhandled exceptions — consistent with all other routes.

### Testing Standards

Per `project-context.md` testing rules for dqs-serve:
- Tests in `tests/test_routes/` — extend existing directory (do NOT recreate `__init__.py`)
- Unit tests (no marker) use `app.dependency_overrides` via `override_db_dependency` autouse fixture — runs without Postgres
- Integration tests (`@pytest.mark.integration`) use `seeded_client` fixture — requires real Postgres

**Run unit tests:** `cd dqs-serve && uv run pytest tests/test_routes/test_search.py`

**Run integration tests:** `cd dqs-serve && uv run pytest -m integration tests/test_routes/test_search.py`

### Fixture Data Available for Integration Tests

From `dqs-serve/src/serve/schema/fixtures.sql` — datasets searchable by name:

| dataset_name | check_status | dqs_score | Searchable by |
|---|---|---|---|
| `lob=retail/src_sys_nm=alpha/dataset=sales_daily` | PASS | 98.50 | `sales`, `alpha`, `retail`, `daily` |
| `lob=retail/src_sys_nm=alpha/dataset=products` | FAIL | 45.00 | `products`, `alpha`, `retail` |
| `lob=retail/src_sys_nm=alpha/dataset=customers` | WARN | 72.00 | `customers`, `alpha`, `retail` |
| `lob=commercial/src_sys_nm=beta/dataset=transactions` | WARN | 60.00 | `transactions`, `beta`, `commercial` |
| `lob=commercial/src_sys_nm=beta/dataset=payments` | FAIL | 0.00 | `payments`, `beta`, `commercial` |
| `src_sys_nm=omni/dataset=customer_profile` | PASS | 95.00 | `omni`, `customer`, `profile` |

For AC2 (max 10 results): querying `q=a` (matches `sales_daily`, `alpha`, `alpha/products`, `alpha/customers`, `alpha/transactions`... etc.) would return > 10 raw matches, verifying the LIMIT 10 cap. The fixture has 12 dq_run rows (7 for `sales_daily` + 5 others), but the ROW_NUMBER() CTE deduplicates to 6 unique datasets. `q=a` matches all 6 unique datasets — test that result count is ≤ 10 (all 6 returned, confirming cap is not exceeded but the LIMIT logic is correct).

For AC1 ordering: `q=profile` would match only `customer_profile`. For ordering test, use `q=dataset` which matches all — prefix match test requires a query where some datasets START with the query string, which is not the case for these path-based names. Use a simpler approach: verify ordering is deterministic (ORDER BY sort_order, dataset_name).

**Note on `ue90`:** The acceptance criteria example `GET /api/search?q=ue90` returns empty results against these fixtures (no dataset contains "ue90"). This is intentional — tests the no-match empty array path (AC3).

### Architecture Anti-Patterns to Avoid

From `_bmad-output/project-context.md` — violations that would cause immediate failure:

- **NEVER query `dq_run` directly** — always `v_dq_run_active`
- **NEVER add check-type-specific logic** — API returns raw data from dq_run only (no metric joins needed for search)
- **NEVER return stack traces** — global 500 handler is already in `main.py`
- **NEVER use `session.query()` style** — SQLAlchemy 2.0 uses `db.execute(text(...))`
- **NEVER hardcode `'9999-12-31 23:59:59'`** — views already apply this filter
- **NEVER invent new top-level directories** — `routes/` directory already established
- **NEVER return 404 for no-match search** — return `{"results": []}` (AC3 is explicit)
- **NEVER use `any` TypeScript type** (not applicable here, but Python: use type hints on all functions)

### Previous Story Intelligence (4-3)

From Story 4.3 completion notes and debug log — directly actionable for 4.4:

1. **ROW_NUMBER() CTE replaces correlated subquery** — use same pattern for latest-run-per-dataset in search
2. **`app.dependency_overrides` autouse fixture** in `conftest.py` handles unit test DB mocking — extend `_make_mock_db_session` with `q` param branch, NOT by replacing existing logic
3. **`test_routes/__init__.py` already exists** — do NOT recreate
4. **Tests follow `test_<scope>.py` naming** — `test_search.py`
5. **Per-check status NOT derived in API** — search returns `check_status` from `dq_run` (overall status only), no metric table joins needed — simpler than datasets.py
6. **Mock dispatch guards** — add `.get()` checks for `q` in param dict before accessing, to avoid KeyError; use `if params and isinstance(params, dict):` guard (already in conftest.py)
7. **`seeded_client` commits before TestClient** — integration tests use `seeded_client` fixture; no changes needed to fixture itself
8. **`dataset_id` = `dq_run.id`** — same integer used as `DatasetInLob.dataset_id` in `lobs.py`; consistency is critical for dashboard navigation
9. **Mock dispatch priority** — `q` param should be checked first (or early) in `_execute_side_effect` since it does NOT overlap with any existing param keys (`lob_id`, `dataset_id`, `dataset_name`, `run_ids`, `dataset_names`, `days_back`, `orchestration_run_id`)

### Project Structure Notes

All changes are within `dqs-serve/` only. No changes to `dqs-dashboard/`, `dqs-spark/`, `dqs-orchestrator/`.

Exact files to create/modify:
- `dqs-serve/src/serve/routes/search.py` — NEW
- `dqs-serve/src/serve/main.py` — MODIFY (add 3 lines: import + include_router)
- `dqs-serve/tests/test_routes/test_search.py` — NEW
- `dqs-serve/tests/conftest.py` — MODIFY (extend `_make_mock_db_session` for `q` param)

Alignment with architecture `dqs-serve` structure (from `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`):
```
dqs-serve/src/serve/routes/
  summary.py     ← exists (4.2)
  lobs.py        ← exists (4.2)
  datasets.py    ← exists (4.3)
  search.py      ← CREATE in 4.4
```

### References

- Epic 4 Story 4.4 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md`
- Architecture — API Endpoints table: `_bmad-output/planning-artifacts/architecture.md#API Endpoints`
- Architecture — dqs-serve structure: `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`
- Architecture — Error format: `_bmad-output/planning-artifacts/architecture.md#API & Communication Patterns`
- Project Context — Python rules: `_bmad-output/project-context.md#Language-Specific Rules`
- Project Context — FastAPI rules: `_bmad-output/project-context.md#Framework-Specific Rules`
- Project Context — Anti-patterns: `_bmad-output/project-context.md#Anti-Patterns`
- DB Schema: `dqs-serve/src/serve/schema/ddl.sql`
- Active-record views: `dqs-serve/src/serve/schema/views.sql`
- Test fixtures data: `dqs-serve/src/serve/schema/fixtures.sql`
- Existing conftest: `dqs-serve/tests/conftest.py`
- Pattern reference (datasets.py): `dqs-serve/src/serve/routes/datasets.py`
- Pattern reference (lobs.py): `dqs-serve/src/serve/routes/lobs.py`
- Engine config: `dqs-serve/src/serve/db/engine.py`
- DB session dep: `dqs-serve/src/serve/db/session.py`
- Existing main.py (to modify): `dqs-serve/src/serve/main.py`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

No blockers encountered. conftest.py already contained the `_FAKE_SEARCH_RESULT_ROW` and `q` dispatch logic from prior story preparation — no conftest modifications needed.

### Completion Notes List

- Created `dqs-serve/src/serve/routes/search.py` with `SearchResult` and `SearchResponse` Pydantic models and `GET /api/search` endpoint using ROW_NUMBER() CTE pattern with ILIKE substring + prefix ordering and LIMIT 10.
- Modified `dqs-serve/src/serve/main.py` to import and include search router under `/api` prefix.
- Removed all `@pytest.mark.skip` decorators from `dqs-serve/tests/test_routes/test_search.py` (17 unit tests activated).
- All 77 unit tests pass (17 new + 60 existing), 0 failures, 0 skipped.
- AC1 satisfied: endpoint returns dataset_name, lob_id, dqs_score, check_status in snake_case.
- AC2 satisfied: LIMIT 10 in SQL query.
- AC3 satisfied: no-match returns `{"results": []}` — never raises HTTPException.

### File List

- `dqs-serve/src/serve/routes/search.py` (NEW)
- `dqs-serve/src/serve/main.py` (MODIFIED — added search router import + include_router)
- `dqs-serve/tests/test_routes/test_search.py` (MODIFIED — removed all skip markers)

### Review Findings

- [x] [Review][Patch] Ruff F401: unused `psycopg2.extensions` import under TYPE_CHECKING block [dqs-serve/tests/test_routes/test_search.py:30] — fixed by ruff --fix
- [x] [Review][Patch] Ruff I001: import block unsorted/unformatted (2 occurrences in inline imports) [dqs-serve/tests/test_routes/test_search.py:346,368] — fixed by ruff --fix
- [x] [Review][Patch] Ruff F541: f-string without any placeholders [dqs-serve/tests/test_routes/test_search.py:580] — fixed by ruff --fix
- [x] [Review][Patch] Stale TDD RED PHASE docstring and dead `if TYPE_CHECKING: pass` block in test module — implementation is done, docstring was misleading [dqs-serve/tests/test_routes/test_search.py:1-30] — removed stale header and dead import guard

## Change Log

- Implemented GET /api/search endpoint with ROW_NUMBER() CTE pattern, ILIKE substring + prefix ordering, LIMIT 10 (Date: 2026-04-03)
