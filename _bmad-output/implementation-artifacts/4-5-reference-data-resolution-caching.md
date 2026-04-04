# Story 4.5: Reference Data Resolution & Caching

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **data steward**,
I want lookup codes resolved to LOB, owner, and classification names in API responses,
so that datasets are displayed with human-readable business context instead of raw codes.

## Acceptance Criteria

1. **Given** the reference data service is configured **When** the serve layer starts **Then** it populates a LOB mapping cache from the lookup table
2. **Given** the cache is populated **When** an API response includes a lookup code **Then** the code is resolved to LOB name, owner, and classification in the response
3. **Given** the cache is older than 12 hours **When** the refresh timer fires (twice daily) **Then** the cache is refreshed from the source
4. **Given** a lookup code with no mapping **When** the API resolves it **Then** LOB, owner, and classification fields return "N/A" (not null, not error)

## Tasks / Subtasks

- [x] Task 1: Add `lob_lookup` table to schema DDL and views (AC: 1, 2, 4)
  - [x] Add `CREATE TABLE lob_lookup` to `dqs-serve/src/serve/schema/ddl.sql` with temporal pattern columns (`create_date`, `expiry_date = '9999-12-31 23:59:59'`)
  - [x] Columns: `id SERIAL PRIMARY KEY`, `lookup_code TEXT NOT NULL`, `lob_name TEXT NOT NULL`, `owner TEXT NOT NULL`, `classification TEXT NOT NULL`, `create_date TIMESTAMP NOT NULL DEFAULT NOW()`, `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`
  - [x] Add unique constraint: `CONSTRAINT uq_lob_lookup_lookup_code_expiry_date UNIQUE (lookup_code, expiry_date)`
  - [x] Add index: `CREATE INDEX IF NOT EXISTS idx_lob_lookup_lookup_code ON lob_lookup (lookup_code)`
  - [x] Add `CREATE OR REPLACE VIEW v_lob_lookup_active AS SELECT * FROM lob_lookup WHERE expiry_date = '9999-12-31 23:59:59';` to `dqs-serve/src/serve/schema/views.sql`
  - [x] Add `DROP TABLE IF EXISTS lob_lookup CASCADE;` and `DROP VIEW IF EXISTS v_lob_lookup_active CASCADE;` to the cleanup block in `dqs-serve/tests/conftest.py` `db_conn` fixture (maintains clean test state)

- [x] Task 2: Add fixture rows for `lob_lookup` to `fixtures.sql` (AC: 1, 2, 4)
  - [x] Insert active rows for `LOB_RETAIL`, `LOB_COMMERCIAL`, `LOB_LEGACY` matching existing `dq_run.lookup_code` values in fixtures
  - [x] Rows must carry `expiry_date = '9999-12-31 23:59:59'` (temporal pattern)
  - [x] Example row: `('LOB_RETAIL', 'Retail Banking', 'Jane Doe', 'Tier 1 Critical', '9999-12-31 23:59:59')`

- [x] Task 3: Implement `ReferenceDataService` in `dqs-serve/src/serve/services/reference_data.py` (AC: 1, 2, 3, 4)
  - [x] Create `dqs-serve/src/serve/services/__init__.py` (empty)
  - [x] Create `dqs-serve/src/serve/services/reference_data.py` (new file)
  - [x] Define `LobMapping` dataclass with fields: `lob_name: str`, `owner: str`, `classification: str`
  - [x] Define `ReferenceDataService` class with:
    - [x] `__init__(self, db_factory: Callable[[], Session])` — takes a callable that returns a fresh DB session
    - [x] `_cache: dict[str, LobMapping]` — internal dict mapping `lookup_code` → `LobMapping`
    - [x] `_last_refresh: datetime.datetime` — tracks last cache refresh time
    - [x] `_lock: threading.Lock` — protects concurrent reads during refresh
    - [x] `refresh(self) -> None` — loads all rows from `v_lob_lookup_active` into `_cache`, updates `_last_refresh`
    - [x] `resolve(self, lookup_code: str | None) -> LobMapping` — returns mapping for code, or `LobMapping("N/A", "N/A", "N/A")` if code is None or not found
    - [x] `_maybe_refresh(self) -> None` — refreshes cache if `_last_refresh` is more than 12 hours ago; called inside `resolve()`
  - [x] SQL: `SELECT lookup_code, lob_name, owner, classification FROM v_lob_lookup_active`
  - [x] Use SQLAlchemy 2.0 style: `db.execute(text(...)).mappings().all()`
  - [x] `refresh()` opens its own session using `db_factory()`, closes it when done
  - [x] Log at INFO when cache is refreshed; log at WARN when lookup_code is unresolved

- [x] Task 4: Wire `ReferenceDataService` into FastAPI lifespan in `main.py` (AC: 1, 3)
  - [x] Add FastAPI `lifespan` context manager (replaces `@app.on_event("startup")` pattern — FastAPI 0.95+ preferred)
  - [x] In `lifespan` startup: create `ReferenceDataService(db_factory=SessionLocal)`, call `service.refresh()`, store as `app.state.reference_data`
  - [x] Import `SessionLocal` from `..db.engine` in `main.py`
  - [x] `ReferenceDataService` instance accessible via `request.app.state.reference_data` in route handlers
  - [x] Do NOT pass `ReferenceDataService` as a FastAPI `Depends()` — it is a singleton, not per-request

- [x] Task 5: Expose `get_reference_data_service` FastAPI dependency in `main.py` (AC: 2)
  - [x] Add `get_reference_data_service(request: Request) -> ReferenceDataService` function to `main.py`
  - [x] Returns `request.app.state.reference_data`
  - [x] This enables `Depends(get_reference_data_service)` in route handlers

- [x] Task 6: Enrich `GET /api/datasets/{dataset_id}` response with resolved names (AC: 2, 4)
  - [x] In `datasets.py`, add `lob_name: str`, `owner: str`, `classification: str` fields to `DatasetDetail` Pydantic model
  - [x] Inject `ref_svc: ReferenceDataService = Depends(get_reference_data_service)` into `get_dataset_detail` route
  - [x] Call `ref_svc.resolve(row["lookup_code"])` to get mapping; set `lob_name`, `owner`, `classification` on the response
  - [x] Import `get_reference_data_service` from `..main` — OR extract to a shared `dependencies.py` module to avoid circular import (see Dev Notes)

- [x] Task 7: Write pytest tests for `ReferenceDataService` (AC: 1, 2, 3, 4)
  - [x] Create `dqs-serve/tests/test_services/__init__.py` (empty)
  - [x] Create `dqs-serve/tests/test_services/test_reference_data.py`
  - [x] Unit tests (no `@pytest.mark.integration` marker):
    - [x] `test_resolve_returns_na_for_none_code` — `resolve(None)` → `LobMapping("N/A", "N/A", "N/A")`
    - [x] `test_resolve_returns_na_for_unknown_code` — `resolve("UNKNOWN_CODE")` with empty cache → returns N/A
    - [x] `test_resolve_returns_cached_mapping` — pre-populate `_cache`, `resolve("LOB_RETAIL")` → correct mapping
    - [x] `test_maybe_refresh_triggers_on_stale_cache` — set `_last_refresh` to 13 hours ago, mock `refresh()`, confirm it is called
    - [x] `test_maybe_refresh_skips_on_fresh_cache` — set `_last_refresh` to 1 hour ago, confirm `refresh()` is NOT called
    - [x] `test_refresh_populates_cache_from_db` — mock `db_factory()` returning fake rows, call `refresh()`, verify cache populated correctly
  - [x] Integration tests (`@pytest.mark.integration`):
    - [x] `test_refresh_reads_from_lob_lookup_view` — with `seeded_client` fixture, verify that `ReferenceDataService(SessionLocal).refresh()` populates cache with `LOB_RETAIL`, `LOB_COMMERCIAL`, `LOB_LEGACY`
    - [x] `test_dataset_detail_includes_resolved_names` — `GET /api/datasets/{dataset_id}` with seeded data → response includes `lob_name`, `owner`, `classification` (not "N/A" for known LOBs)

- [x] Task 8: Update `conftest.py` mock for route tests that now return resolved fields (AC: 2, 4)
  - [x] Extend `_make_mock_db_session` to handle `lob_lookup` queries (no params, returns fake lob_lookup rows) — OR mock `ReferenceDataService` at the app level in unit tests
  - [x] For unit tests: override `app.state.reference_data` with a mock `ReferenceDataService` whose `resolve()` returns `LobMapping("Retail Banking", "Jane Doe", "Tier 1 Critical")` for "LOB_RETAIL" and `LobMapping("N/A", "N/A", "N/A")` for unknown codes
  - [x] Add autouse fixture or extend `override_db_dependency` to inject mock `ReferenceDataService` for unit tests
  - [x] Ensure existing tests for `GET /api/datasets/9` still pass after `lob_name`/`owner`/`classification` fields added

## Dev Notes

### Architecture: `services/` Directory Already Exists

`dqs-serve/src/serve/services/` exists but is empty (no `__init__.py` or Python files). This story creates:
- `services/__init__.py` (empty)
- `services/reference_data.py` (the implementation)

This matches the architecture spec in `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`:
```
dqs-serve/src/serve/
  services/
    reference_data.py   ← CREATE in 4.5
    score_service.py    ← future story
```

### Schema: New `lob_lookup` Table Required

No lookup table exists yet. The DDL schema (`dqs-serve/src/serve/schema/ddl.sql`) must be extended. The `dq_run.lookup_code` column is the FK into this table (soft reference, not a DB-level FK constraint — resilient to missing entries). The view `v_lob_lookup_active` follows the same temporal pattern as all other `v_*_active` views.

**IMPORTANT**: The `lob_lookup` table uses the same temporal pattern as all other tables:
- `create_date TIMESTAMP NOT NULL DEFAULT NOW()`
- `expiry_date TIMESTAMP NOT NULL DEFAULT '9999-12-31 23:59:59'`
- Active-record view: `v_lob_lookup_active`

**Do NOT hardcode** `'9999-12-31 23:59:59'` in service code — the view already filters. Reference `EXPIRY_SENTINEL` constant only if manipulating expiry values in Python; the view handles it otherwise.

### Circular Import Prevention

`datasets.py` needs `get_reference_data_service`, which is defined in `main.py`. To avoid circular imports (`main.py` imports `datasets.py`, `datasets.py` imports `main.py`), extract the dependency function to a separate module:

**Option A (recommended):** Create `dqs-serve/src/serve/dependencies.py`:
```python
from fastapi import Request
from .services.reference_data import ReferenceDataService

def get_reference_data_service(request: Request) -> ReferenceDataService:
    return request.app.state.reference_data
```

Then import in `datasets.py`:
```python
from ..dependencies import get_reference_data_service
```

**Option B:** Define `get_reference_data_service` in `main.py` and use `from __future__ import annotations` — but this is fragile.

Use **Option A** — a `dependencies.py` module is clean and prevents any circular import path.

### FastAPI Lifespan Pattern (FastAPI 0.135)

FastAPI 0.95+ recommends `lifespan` over `@app.on_event("startup")`. The correct pattern:

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from .db.engine import SessionLocal
from .services.reference_data import ReferenceDataService

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    svc = ReferenceDataService(db_factory=SessionLocal)
    svc.refresh()
    app.state.reference_data = svc
    yield
    # Shutdown (nothing to clean up)

app = FastAPI(title="Data Quality Service", lifespan=lifespan)
```

`SessionLocal` is the callable — it constructs a new session when called. `ReferenceDataService.__init__` stores it as `self._db_factory`. `refresh()` calls `self._db_factory()` to get a session, executes the query, closes the session.

### ReferenceDataService Design

```python
import datetime
import logging
import threading
from dataclasses import dataclass
from typing import Callable, Optional

from sqlalchemy import text
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

_CACHE_TTL_HOURS = 12

_LOB_LOOKUP_SQL = text(
    "SELECT lookup_code, lob_name, owner, classification FROM v_lob_lookup_active"
)

@dataclass(frozen=True)
class LobMapping:
    lob_name: str
    owner: str
    classification: str

_NA_MAPPING = LobMapping(lob_name="N/A", owner="N/A", classification="N/A")


class ReferenceDataService:
    def __init__(self, db_factory: Callable[[], Session]) -> None:
        self._db_factory = db_factory
        self._cache: dict[str, LobMapping] = {}
        self._last_refresh: datetime.datetime = datetime.datetime.min
        self._lock = threading.Lock()

    def refresh(self) -> None:
        db = self._db_factory()
        try:
            rows = db.execute(_LOB_LOOKUP_SQL).mappings().all()
            new_cache = {
                row["lookup_code"]: LobMapping(
                    lob_name=row["lob_name"],
                    owner=row["owner"],
                    classification=row["classification"],
                )
                for row in rows
            }
        finally:
            db.close()
        with self._lock:
            self._cache = new_cache
            self._last_refresh = datetime.datetime.now()
        logger.info("Reference data cache refreshed: %d LOB mappings loaded", len(new_cache))

    def resolve(self, lookup_code: Optional[str]) -> LobMapping:
        self._maybe_refresh()
        if not lookup_code:
            return _NA_MAPPING
        with self._lock:
            result = self._cache.get(lookup_code)
        if result is None:
            logger.warning("Unresolved lookup_code: %r — returning N/A", lookup_code)
            return _NA_MAPPING
        return result

    def _maybe_refresh(self) -> None:
        with self._lock:
            age = datetime.datetime.now() - self._last_refresh
        if age.total_seconds() > _CACHE_TTL_HOURS * 3600:
            self.refresh()
```

### `DatasetDetail` Model Extension

Add three new fields at the end of `DatasetDetail` (preserving existing field order):
```python
class DatasetDetail(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    # ... existing fields ...
    lob_name: str       # resolved from lookup_code via ReferenceDataService
    owner: str          # resolved from lookup_code via ReferenceDataService
    classification: str # resolved from lookup_code via ReferenceDataService
```

These are always `str` (never `None`) because `resolve()` always returns "N/A" as fallback — never null.

### Dependency Injection in Route Handler

In `datasets.py`, `get_dataset_detail()` signature becomes:
```python
from ..dependencies import get_reference_data_service
from ..services.reference_data import ReferenceDataService

@router.get("/datasets/{dataset_id}", response_model=DatasetDetail)
def get_dataset_detail(
    dataset_id: int,
    db: Session = Depends(get_db),
    ref_svc: ReferenceDataService = Depends(get_reference_data_service),
) -> DatasetDetail:
    ...
    mapping = ref_svc.resolve(row["lookup_code"])
    return DatasetDetail(
        ...
        lob_name=mapping.lob_name,
        owner=mapping.owner,
        classification=mapping.classification,
    )
```

**Do NOT inject `ReferenceDataService` into every route** — only routes that need resolved names. For 4.5 scope: `GET /api/datasets/{dataset_id}` only. Other routes (`/api/lobs`, `/api/summary`, `/api/search`) continue returning `lob_id` (raw `lookup_code`) without resolution — they don't require human-readable names per their AC.

### Testing: Mock `ReferenceDataService` for Unit Tests

Unit tests for `test_datasets.py` will fail after `DatasetDetail` gains `lob_name`/`owner`/`classification` fields unless `app.state.reference_data` is populated. The `override_db_dependency` fixture injects the mock DB, but `app.state.reference_data` must also be mocked.

Add a session-scoped fixture in `conftest.py`:
```python
from unittest.mock import MagicMock
from serve.services.reference_data import LobMapping, ReferenceDataService

@pytest.fixture(autouse=True)
def mock_reference_data_service(request: pytest.FixtureRequest):
    """Auto-use: inject a mock ReferenceDataService for unit tests."""
    if request.node.get_closest_marker("integration") is None:
        mock_svc = MagicMock(spec=ReferenceDataService)
        mock_svc.resolve.return_value = LobMapping(
            lob_name="Retail Banking",
            owner="Jane Doe",
            classification="Tier 1 Critical",
        )
        app.state.reference_data = mock_svc
        yield
        # Clean up: remove app.state.reference_data after unit test
        try:
            del app.state.reference_data
        except AttributeError:
            pass
    else:
        yield
```

Import `app` from `serve.main` at the top of `conftest.py` (already imported via `TestClient(app)`).

### Fixture Data for `lob_lookup`

Add to `fixtures.sql` after `dataset_enrichment` rows:
```sql
-- lob_lookup rows — reference data for lookup_code resolution (4.5)
INSERT INTO lob_lookup (lookup_code, lob_name, owner, classification, expiry_date)
VALUES
    ('LOB_RETAIL',     'Retail Banking',     'Jane Doe',    'Tier 1 Critical', '9999-12-31 23:59:59'),
    ('LOB_COMMERCIAL', 'Commercial Banking', 'John Smith',  'Tier 1 Critical', '9999-12-31 23:59:59'),
    ('LOB_LEGACY',     'Legacy Systems',     'Alice Brown', 'Tier 2 Standard', '9999-12-31 23:59:59');
```

These match the `lookup_code` values in existing `dq_run` fixture rows.

### Active-Record Views — Critical Rule

`v_lob_lookup_active` must be added to `views.sql`. The service MUST query this view — never `lob_lookup` directly.

```sql
-- CORRECT
SELECT lookup_code, lob_name, owner, classification FROM v_lob_lookup_active

-- NEVER DO THIS
SELECT lookup_code, lob_name, owner, classification FROM lob_lookup
WHERE expiry_date = '9999-12-31 23:59:59'
```

### conftest.py Cleanup Block

In `tests/conftest.py`, the `db_conn` fixture's cleanup block must DROP `lob_lookup` table and `v_lob_lookup_active` view before running DDL. Add to the existing `cur.execute("""DROP TABLE IF EXISTS ...""")` block:
```sql
DROP TABLE IF EXISTS lob_lookup CASCADE;
DROP VIEW IF EXISTS v_lob_lookup_active CASCADE;
```

Add these before the DDL execution lines. The order matters: drop views before tables.

### Directory Structure — All Files to Create/Modify

```
dqs-serve/
  src/serve/
    dependencies.py               ← NEW: get_reference_data_service() dependency
    services/
      __init__.py                 ← NEW: empty
      reference_data.py           ← NEW: ReferenceDataService + LobMapping
    routes/
      datasets.py                 ← MODIFY: add lob_name/owner/classification to DatasetDetail + inject ref_svc
    main.py                       ← MODIFY: add lifespan, import SessionLocal, register ReferenceDataService
    schema/
      ddl.sql                     ← MODIFY: add lob_lookup table
      views.sql                   ← MODIFY: add v_lob_lookup_active view
      fixtures.sql                ← MODIFY: add lob_lookup rows
  tests/
    conftest.py                   ← MODIFY: add mock_reference_data_service autouse fixture + lob_lookup cleanup
    test_services/
      __init__.py                 ← NEW: empty
      test_reference_data.py      ← NEW: unit + integration tests
```

**Do NOT modify:** `routes/summary.py`, `routes/lobs.py`, `routes/search.py` (they return `lob_id` only, not resolved names). `db/engine.py`, `db/session.py`, `db/models.py`, `db/views.py` — no changes needed.

### Error Response Format

No new error codes for this story. The service itself never raises HTTP errors — it always returns `LobMapping("N/A", "N/A", "N/A")` for unknown codes. If the DB is unavailable at startup (lifespan), let the exception propagate — FastAPI will fail to start (correct behavior).

### Testing Standards

Per `project-context.md` testing rules for dqs-serve:
- Tests in `tests/test_services/` — new directory
- Unit tests (no marker) mock DB and `ReferenceDataService`
- Integration tests (`@pytest.mark.integration`) use `seeded_client` with real Postgres

**Run unit tests:** `cd dqs-serve && uv run pytest tests/test_services/test_reference_data.py`

**Run integration tests:** `cd dqs-serve && uv run pytest -m integration tests/test_services/test_reference_data.py`

**Run full suite:** `cd dqs-serve && uv run pytest tests/`

### Architecture Anti-Patterns to Avoid

From `_bmad-output/project-context.md`:
- **NEVER query `lob_lookup` directly** — always `v_lob_lookup_active`
- **NEVER hardcode `'9999-12-31 23:59:59'`** in service code — views handle this
- **NEVER use `session.query()` style** — SQLAlchemy 2.0 uses `db.execute(text(...))`
- **NEVER return stack traces** — global 500 handler in `main.py` covers this
- **NEVER raise HTTPException from `ReferenceDataService`** — it returns "N/A", not errors
- **NEVER use `@app.on_event("startup")`** — deprecated; use `lifespan` context manager
- **NEVER pass `ReferenceDataService` as a per-request `Depends()` constructor** — it is a singleton on `app.state`, not created per request
- **NEVER add LOB name resolution to `routes/lobs.py`** — `lob_id` (lookup_code) is the API identifier for LOBs; resolved names belong only in `DatasetDetail`

### Previous Story Intelligence (4.4)

From Story 4.4 completion notes:
1. **`services/` directory already exists** as empty directory — just needs `__init__.py` and `reference_data.py`
2. **`test_services/` already exists** as empty directory — just needs `__init__.py` and `test_reference_data.py`
3. **`conftest.py` `_execute_side_effect` pattern** is well-established — extend carefully, do NOT break existing dispatch logic
4. **`autouse=True` fixtures** work well for this test suite — `mock_reference_data_service` follows the `override_db_dependency` pattern
5. **Route handler injection** via `Depends()` is the established pattern — use it for `ref_svc` in `datasets.py`
6. **Ruff** is enforced in this project — ensure imports are sorted, no unused imports, f-strings have placeholders. Run `uv run ruff check --fix` before finalizing

### References

- Epic 4 Story 4.5 AC: `_bmad-output/planning-artifacts/epics/epic-4-quality-dashboard-drill-down-reporting.md#Story 4.5`
- Architecture — reference data: `_bmad-output/planning-artifacts/architecture.md#Cross-Cutting Concerns Identified`
- Architecture — dqs-serve structure: `_bmad-output/planning-artifacts/architecture.md#Structure Patterns`
- PRD — FR46-48: `_bmad-output/planning-artifacts/prd.md#Reference Data Resolution`
- Project Context — Python/FastAPI rules: `_bmad-output/project-context.md`
- Project Context — Anti-patterns: `_bmad-output/project-context.md#Anti-Patterns`
- DB Schema (current): `dqs-serve/src/serve/schema/ddl.sql`
- Active-record views (current): `dqs-serve/src/serve/schema/views.sql`
- Fixtures (current): `dqs-serve/src/serve/schema/fixtures.sql`
- Existing conftest: `dqs-serve/tests/conftest.py`
- Pattern reference (datasets.py): `dqs-serve/src/serve/routes/datasets.py`
- Pattern reference (lobs.py): `dqs-serve/src/serve/routes/lobs.py`
- DB session dep: `dqs-serve/src/serve/db/session.py`
- DB engine (SessionLocal): `dqs-serve/src/serve/db/engine.py`
- Main app (to modify): `dqs-serve/src/serve/main.py`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation completed without major blockers.

### Completion Notes List

- Implemented `lob_lookup` table with temporal pattern in `ddl.sql` and `v_lob_lookup_active` view in `views.sql`
- Added lob_lookup fixture rows for `LOB_RETAIL`, `LOB_COMMERCIAL`, `LOB_LEGACY` to `fixtures.sql`
- Created `services/__init__.py` (empty) and `services/reference_data.py` with `LobMapping` frozen dataclass and `ReferenceDataService` class
- `ReferenceDataService` uses threading.Lock for cache safety, 12h TTL with 1-second grace period to avoid boundary timing races
- Created `dependencies.py` with `get_reference_data_service` to prevent circular imports (Option A from Dev Notes)
- Updated `main.py` with FastAPI `lifespan` context manager using `asynccontextmanager` — replaces deprecated `@app.on_event("startup")`
- Updated `DatasetDetail` Pydantic model with `lob_name`, `owner`, `classification` fields (all `str`, never `Optional`)
- Updated `get_dataset` route handler to inject `ref_svc: ReferenceDataService = Depends(get_reference_data_service)` and call `ref_svc.resolve(row["lookup_code"])`
- Added `mock_reference_data_service` autouse fixture to `conftest.py` with `patch("serve.main.SessionLocal")` to prevent lifespan DB calls in unit tests
- Removed ALL `@pytest.mark.skip` decorators from `test_reference_data.py` (24 removed) and `test_datasets.py` (11 removed)
- Applied ruff auto-fixes to test files; manually fixed unused `client` variable in lifespan test
- All 110 unit tests pass with 0 skipped, 0 failed

### File List

- `dqs-serve/src/serve/schema/ddl.sql` (modified — added lob_lookup table + index)
- `dqs-serve/src/serve/schema/views.sql` (modified — added v_lob_lookup_active view)
- `dqs-serve/src/serve/schema/fixtures.sql` (modified — added lob_lookup rows)
- `dqs-serve/src/serve/services/__init__.py` (created — empty)
- `dqs-serve/src/serve/services/reference_data.py` (created — LobMapping + ReferenceDataService)
- `dqs-serve/src/serve/dependencies.py` (created — get_reference_data_service dependency)
- `dqs-serve/src/serve/main.py` (modified — lifespan context manager + ReferenceDataService wiring)
- `dqs-serve/src/serve/routes/datasets.py` (modified — DatasetDetail fields + ref_svc injection)
- `dqs-serve/tests/conftest.py` (modified — lob_lookup cleanup + mock_reference_data_service autouse fixture)
- `dqs-serve/tests/test_services/__init__.py` (already existed — empty, confirmed)
- `dqs-serve/tests/test_services/test_reference_data.py` (modified — removed all @pytest.mark.skip decorators + ruff fixes)
- `dqs-serve/tests/test_routes/test_datasets.py` (modified — removed all @pytest.mark.skip decorators + ruff fixes)

## Change Log

- 2026-04-03: Story 4.5 implemented — reference data resolution and caching complete. lob_lookup table, v_lob_lookup_active view, ReferenceDataService (12h TTL cache), FastAPI lifespan wiring, DatasetDetail enrichment with lob_name/owner/classification, dependencies.py to avoid circular imports, conftest.py mock_reference_data_service autouse fixture. All 110 unit tests pass (0 skipped).
