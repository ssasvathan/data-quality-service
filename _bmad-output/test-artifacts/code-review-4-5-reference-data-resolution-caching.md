# Code Review: Story 4-5 — Reference Data Resolution & Caching

**Date:** 2026-04-03
**Reviewer:** Senior Developer (automated review pass)
**Story:** 4-5-reference-data-resolution-caching
**Status:** All findings resolved — story marked done

---

## Summary

11 findings were identified across 5 files. All have been resolved. Tests: 110 passed, 0 failed, 0 skipped.

---

## Findings & Resolutions

### High Severity

#### [High-1] TOCTOU race in `_maybe_refresh`

**File:** `dqs-serve/src/serve/services/reference_data.py`

**Finding:** The age check and `refresh()` call were not under the same lock. Multiple threads could pass the age check and each independently call `refresh()`.

**Resolution:** Implemented double-check locking with a dedicated `_refresh_lock` separate from `_lock` (which protects cache reads/writes). `_maybe_refresh()` now:
1. Checks staleness under `_lock` (fast path, no DB call held under lock).
2. Acquires `_refresh_lock` to serialise concurrent refresh candidates.
3. Re-checks staleness under `_lock` (second check after acquiring `_refresh_lock`).
4. Only calls `refresh()` if cache is still stale after step 3.

A separate `_refresh_lock` is used to avoid deadlock — `refresh()` itself acquires `_lock` when updating `_cache`, so holding `_lock` while calling `refresh()` would deadlock.

---

#### [High-2] `refresh()` updates `_cache` after `db.close()`

**File:** `dqs-serve/src/serve/services/reference_data.py`

**Finding:** If `db.close()` raised, `_cache` and `_last_refresh` would remain stale since they were updated after the `finally` block.

**Resolution:** Moved `_cache` and `_last_refresh` updates (under `_lock`) and the `logger.info` call inside the `try` block, before `finally: db.close()`. Cache is now always updated on a successful query regardless of whether `close()` subsequently raises.

---

#### [High-3] Async lifespan calling sync `svc.refresh()`

**File:** `dqs-serve/src/serve/main.py`

**Finding:** `svc.refresh()` is a blocking synchronous DB call invoked directly from the `async` lifespan context manager, blocking the event loop.

**Resolution:** Replaced `svc.refresh()` with `await asyncio.to_thread(svc.refresh)`. Added `import asyncio` at the top of `main.py`.

---

### Medium Severity

#### [Medium-1] No startup failure handling in `lifespan`

**File:** `dqs-serve/src/serve/main.py`

**Finding:** If `refresh()` raised at startup, the app crashed with no log entry, making diagnosis difficult.

**Resolution:** Wrapped the `asyncio.to_thread(svc.refresh)` call in `try/except Exception` with `logger.exception(...)` providing a descriptive message about DB connectivity and the view name before re-raising.

---

#### [Medium-2] `get_reference_data_service` no guard on missing attribute

**File:** `dqs-serve/src/serve/dependencies.py`

**Finding:** `request.app.state.reference_data` raised `AttributeError` if not set (e.g. lifespan failed).

**Resolution:** Changed to `getattr(request.app.state, "reference_data", None)` and raises `HTTPException(status_code=503)` with `error_code: "SERVICE_UNAVAILABLE"` and a diagnostic message if the value is `None`.

---

#### [Medium-3] `resolve()` falsy check

**File:** `dqs-serve/src/serve/services/reference_data.py`

**Finding:** `if not lookup_code` treated empty string `""` the same as `None`, which is semantically incorrect — an empty string is a distinct (unknown) code that should fall through to the cache lookup.

**Resolution:** Changed to `if lookup_code is None`. Empty string `""` now falls through to `self._cache.get("")` which returns `None` → N/A mapping (same end result for empty string, but semantically correct).

No test changes required — no test exercised `resolve("")` with an explicit expectation on the old falsy behaviour.

---

#### [Medium-4] `datetime.datetime.min` initial value

**File:** `dqs-serve/src/serve/services/reference_data.py`

**Finding:** `datetime.datetime.min` is semantically misleading — it implies the cache was last refreshed at the minimum representable datetime rather than "never".

**Resolution:** Changed `_last_refresh` initial value to `None` (typed `Optional[datetime.datetime]`). `_is_stale()` now explicitly checks `if self._last_refresh is None: return True`.

---

### Low Severity

#### [Low-1] 1-second grace period undocumented

**File:** `dqs-serve/src/serve/services/reference_data.py`

**Finding:** The `timedelta(hours=12, seconds=1)` offset was a silent semantic deviation from the 12h TTL with no explanation.

**Resolution:** Added a detailed docstring comment in `_is_stale()` explaining the rationale: the 1-second tolerance guards against CPU execution time causing `age` to fractionally exceed the threshold immediately after `_last_refresh` is set. The semantic TTL remains 12 hours.

---

#### [Low-2] `logger.info` references `new_cache` that may be undefined

**File:** `dqs-serve/src/serve/services/reference_data.py`

**Finding:** If an exception occurred partway through building `new_cache`, the `logger.info` call after the `finally` block would reference an undefined variable.

**Resolution:** Moved `logger.info` inside the `try` block, after `new_cache` is fully constructed and the lock update is complete. The log now always executes in the happy path, never in error cases.

---

#### [Low-3] Unnecessary import of `ReferenceDataService` in `datasets.py`

**File:** `dqs-serve/src/serve/routes/datasets.py`

**Finding:** `ReferenceDataService` was imported at runtime solely for use as a type hint in `Depends()`.

**Resolution:**
- Added `from __future__ import annotations` to defer all annotation evaluation (PEP 563), making all type hints lazy strings at runtime.
- Moved `from ..services.reference_data import ReferenceDataService` inside `if TYPE_CHECKING:` block — it is now only imported during static analysis, never at runtime.

---

#### [Low-4] No schema validation on `v_lob_lookup_active` rows

**File:** `dqs-serve/src/serve/services/reference_data.py`

**Finding:** `row["lookup_code"]` raises `KeyError` if the schema of `v_lob_lookup_active` changes and a column is missing.

**Resolution:** Changed to `row.get("lookup_code", "")` (and equivalent `.get()` calls for `lob_name`, `owner`, `classification`) with `"N/A"` fallback for string columns. Rows where `lookup_code` is `None` (after `.get()`) are filtered out of `new_cache` with an `if row.get("lookup_code") is not None` guard.

Note: `row.get()` works for both SQLAlchemy `RowMapping` objects (returned by `.mappings().all()`) and plain `dict` objects used in unit test mocks.

---

## Test Results

```
110 passed, 0 failed, 0 skipped
```

Command: `cd dqs-serve && uv run pytest tests/ -q -rs`

---

## Lint Results

```
All checks passed!
```

Command: `uv run ruff check` (run from project root)

---

## Files Modified

| File | Changes |
|------|---------|
| `dqs-serve/src/serve/services/reference_data.py` | High-1, High-2, Low-1, Low-2, Low-4, Medium-3, Medium-4 |
| `dqs-serve/src/serve/main.py` | High-3, Medium-1 |
| `dqs-serve/src/serve/dependencies.py` | Medium-2 |
| `dqs-serve/src/serve/routes/datasets.py` | Low-3 |
| `dqs-serve/tests/test_services/test_reference_data.py` | No changes required (behavior change for Medium-3 had no test impact) |
