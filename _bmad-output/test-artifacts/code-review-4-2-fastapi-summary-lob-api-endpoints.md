# Code Review Report: Story 4-2 — FastAPI Summary & LOB API Endpoints

**Date:** 2026-04-03
**Reviewer:** Senior Developer Agent
**Story:** `4-2-fastapi-summary-lob-api-endpoints`
**Status:** All findings resolved. Story marked `done`.

---

## Summary

14 findings (4 High, 6 Medium, 3 Low) plus 1 skipped test were identified and resolved. All 20 unit tests pass with 0 skipped and 0 failures. `ruff check` reports no errors.

---

## Findings and Resolutions

### High Priority

**[High-1] N+1 query in `get_summary`**
- **File:** `dqs-serve/src/serve/routes/summary.py`
- **Problem:** `_LOB_TREND_SQL` was executed once per LOB inside the `lob_rows` loop, producing N database round-trips for N LOBs.
- **Fix:** Replaced `_LOB_TREND_SQL` (per-LOB) with `_ALL_LOBS_TREND_SQL` — a single batched query using a `GROUP BY lookup_code` CTE that retrieves trends for all LOBs in one round-trip. Results are collected into a `lob_trends` dict keyed by `lookup_code` before the loop.

**[High-2] N+1 query in `get_lob_datasets`**
- **File:** `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** `_DATASET_TREND_SQL` and `_METRIC_CHECK_TYPES_FOR_RUN_SQL` were executed once per dataset inside the dataset loop.
- **Fix:** Replaced both with batched queries:
  - `_DATASET_TREND_BATCH_SQL` uses `WHERE dataset_name = ANY(:dataset_names)` with a CTE to fetch trends for all datasets in one query.
  - `_METRIC_CHECK_TYPES_BATCH_SQL` uses `WHERE dq_run_id = ANY(:run_ids)` to fetch all metric check types in one query.
  Both results are pre-aggregated into maps (`trend_map`, `check_types_map`) before the loop.

**[High-3] `dataset_id` naming mismatch**
- **File:** `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** The variable `dataset_id` held `dq_run.id` (the run PK), not a dataset identifier. This caused semantic confusion.
- **Fix:** Renamed the SQL column alias from `id AS dataset_id` to `id AS run_id`. Updated all references in the route function (`row["dataset_id"]` → `row["run_id"]`, loop variable `dataset_id` → `run_id`). The Pydantic response field `DatasetInLob.dataset_id` retains the original name (it represents the run PK as exposed to API consumers). Updated the conftest mock's `_FAKE_DATASET_ROW` to use `run_id` key.

**[High-4] `_LOB_TREND_SQL` hardcodes 7-day window**
- **File:** `dqs-serve/src/serve/routes/summary.py`
- **Problem:** `INTERVAL '6 days'` was baked into the SQL string with no parameter or constant.
- **Fix:** Introduced `_SUMMARY_TREND_DAYS = 7` constant at module level. The new batched trend query computes `days_back = _SUMMARY_TREND_DAYS - 1` in Python and passes it as a bound parameter `CAST(:days_back AS INTEGER) * INTERVAL '1 day'`. A docstring comment explains this is intentionally fixed (no `time_range` param for the summary endpoint per AC).

---

### Medium Priority

**[Medium-1] Deferred imports**
- **Files:** `dqs-serve/src/serve/routes/summary.py`, `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** `from collections import defaultdict` was inside function bodies.
- **Fix:** Moved to module top-level imports in both files.

**[Medium-2] SQL arithmetic in parameter**
- **File:** `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** `_DATASET_TREND_SQL` used `CAST(:days - 1 AS INTEGER)` — arithmetic on a bound parameter inside SQL.
- **Fix:** In the new batched query, `days_back = days - 1` is computed in Python and bound as `:days_back`. SQL receives the pre-computed integer value.

**[Medium-3] `get_db()` missing rollback**
- **File:** `dqs-serve/src/serve/db/session.py`
- **Problem:** On exception, the session was closed without rolling back, potentially leaving a transaction in a dirty state.
- **Fix:** Added `except Exception: db.rollback(); raise` block before the `finally: db.close()`.

**[Medium-4] `LobListResponse` dead code**
- **File:** `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** `LobListResponse` class was defined but never used (the `GET /api/lobs` endpoint uses `list[LobDetail]` directly as its response model).
- **Fix:** Removed the `LobListResponse` class entirely.

**[Medium-5] No `lob_id` validation**
- **File:** `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** The `lob_id` path parameter had no format validation, accepting any string.
- **Fix:** Added `Annotated[str, Field(pattern=r"^[A-Z0-9_]+$")]` to the `lob_id` parameter in `get_lob_datasets()`. Also added `from typing import Annotated` and `from pydantic import Field` to module imports.

**[Medium-6] `health_check()` return type**
- **File:** `dqs-serve/src/serve/main.py`
- **Problem:** Return type was `-> dict:` (unparameterised).
- **Fix:** Changed to `-> dict[str, str]:`.

---

### Low Priority

**[Low-1] `internal_server_error_handler` catches `HTTPException`**
- **File:** `dqs-serve/src/serve/main.py`
- **Problem:** The generic `Exception` handler would intercept `HTTPException` instances (e.g. 404s) and convert them to 500 responses.
- **Fix:** Added `if isinstance(exc, HTTPException): raise exc` at the top of the handler so FastAPI's own `HTTPException` handler takes over for intentional HTTP errors.

**[Low-2] Add module-level loggers**
- **Files:** `dqs-serve/src/serve/routes/summary.py`, `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** No logger was present in either route module.
- **Fix:** Added `import logging` and `logger = logging.getLogger(__name__)` at module level in both files.

**[Low-3] Fix correlated subquery performance**
- **Files:** `dqs-serve/src/serve/routes/summary.py`, `dqs-serve/src/serve/routes/lobs.py`
- **Problem:** `_LATEST_PER_DATASET_SQL`, `_LOBS_LATEST_SQL`, and `_DATASET_LATEST_FOR_LOB_SQL` all used `WHERE partition_date = (SELECT MAX(...) WHERE ...)` correlated subqueries, which re-execute the subquery for every row.
- **Fix:** Replaced all three with a `ROW_NUMBER() OVER (PARTITION BY dataset_name ORDER BY partition_date DESC)` CTE pattern. The outer query filters `WHERE rn = 1` to get the latest row per dataset without a correlated subquery.

---

### Skipped Test Fix

**Test:** `test_summary_lob_items_have_snake_case_keys` in `test_summary.py`
- **File:** `dqs-serve/tests/test_routes/test_summary.py`
- **Problem:** The test used `if lobs:` to guard the key validation loop, silently passing with no assertions when the mock returned an empty list.
- **Fix:** Replaced the conditional guard with:
  ```python
  assert lobs != [], "lobs list must not be empty — mock DB session must return at least one LOB row."
  ```
  The loop then always runs. The conftest mock was updated to return `_FAKE_SUMMARY_TREND_ROW` (which includes a `lookup_code` field) for the batched summary trend query, ensuring the summary response always populates the `lobs` list.

---

### Conftest Mock Updates

- **File:** `dqs-serve/tests/conftest.py`
- `_FAKE_DATASET_ROW` key renamed from `dataset_id` to `run_id` (tracks High-3 rename).
- `_FAKE_TREND_ROW` replaced with `_FAKE_SUMMARY_TREND_ROW` (adds `lookup_code` field for batched trend response).
- `_execute_side_effect` dispatch logic updated:
  - `run_ids` param → batched metric check types query (was `run_id`)
  - `dataset_names` param → batched dataset trend query (was `dataset_name`)
  - `days_back` param → batched summary LOB trend query (was `avg_score` string check)

---

### Additional Lint Fixes

- **File:** `dqs-serve/tests/test_routes/test_lobs.py`
  - Renamed ambiguous loop variable `l` to `lob` in five `next(...)` comprehensions (E741).
- **File:** `dqs-serve/tests/test_routes/test_summary.py`
  - Removed unused imports `MagicMock` and `patch` (F401, auto-fixed by ruff).
- **Files:** `dqs-serve/tests/test_routes/test_lobs.py`, `test_summary.py`
  - Sorted deferred import blocks within test methods (I001, auto-fixed by ruff).

---

## Test Results

```
collected 57 items / 37 deselected (integration) / 20 selected
tests/test_routes/test_lobs.py    ............ (12 passed)
tests/test_routes/test_summary.py ........ (8 passed)
====================== 20 passed, 0 skipped, 0 failed ======================
```

## Lint Results

```
ruff check dqs-serve/
All checks passed!
```
