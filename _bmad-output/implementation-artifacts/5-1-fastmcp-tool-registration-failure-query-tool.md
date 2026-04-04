# Story 5.1: FastMCP Tool Registration & Failure Query Tool

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **LLM consumer**,
I want an MCP tool that answers "What failed last night?",
so that I can get a quick failure summary without opening the dashboard.

## Acceptance Criteria

1. **Given** FastMCP is configured in dqs-serve **When** the serve layer starts **Then** MCP tools are registered and discoverable by LLM clients
2. **Given** an LLM consumer asks "What failed last night?" **When** the MCP failure query tool executes **Then** it calls the same API endpoints the dashboard uses **And** returns a formatted text response: count of failures, dataset names, check types that failed, and LOB grouping
3. **Given** no failures occurred in the last run **When** the failure query tool executes **Then** it returns "All datasets passed in the latest run ({timestamp}). {N} datasets processed."

## Tasks / Subtasks

- [x] Task 1: Create `dqs-serve/src/serve/mcp/` package with `__init__.py` and `tools.py` (AC: 1)
  - [x] Create `dqs-serve/src/serve/mcp/__init__.py` (empty module marker)
  - [x] Create `dqs-serve/src/serve/mcp/tools.py` with `FastMCP("dqs")` instance
  - [x] Register `query_failures` tool using `@mcp.tool` decorator with type hints on all parameters
  - [x] Tool function must have a docstring describing its purpose (used as MCP tool description)
- [x] Task 2: Implement `query_failures` tool logic (AC: 2, 3)
  - [x] Accept optional `time_range` parameter (default "latest" for most recent run)
  - [x] Query `v_dq_run_active` for failure rows — use `sqlalchemy.text()` and `db.execute()` (SQLAlchemy 2.0 style)
  - [x] Query active-record views ONLY — NEVER query `dq_run` or `dq_metric_numeric` directly
  - [x] Extract: count of failures, dataset names, check types from `dq_metric_numeric`, LOB grouping via `lookup_code`
  - [x] Format as structured text response (not JSON — MCP responses are text for LLM consumption)
  - [x] When failures exist: return formatted summary with count, LOB grouping, dataset names, failing check types
  - [x] When no failures: return "All datasets passed in the latest run ({timestamp}). {N} datasets processed."
- [x] Task 3: Inject DB session via FastMCP `Depends` (AC: 1, 2)
  - [x] Import `fastmcp.dependencies.Depends` (NOT `fastapi.Depends`)
  - [x] Use `db: Session = Depends(get_db)` in tool function signature
  - [x] Exclude `db` from tool schema using `exclude_args=["db"]` in `@mcp.tool` decorator
- [x] Task 4: Mount FastMCP onto the FastAPI app in `main.py` (AC: 1)
  - [x] Import the `mcp` instance from `serve.mcp.tools`
  - [x] Mount via `app.mount("/mcp", mcp.http_app(path="/mcp"))` after existing router includes
  - [x] Do NOT replace or modify existing FastAPI routes — the MCP layer is additive
- [x] Task 5: Write pytest tests in `dqs-serve/tests/test_mcp/` (AC: 1, 2, 3)
  - [x] Create `dqs-serve/tests/test_mcp/__init__.py` (empty)
  - [x] Create `dqs-serve/tests/test_mcp/test_tools.py`
  - [x] Unit tests (no `@pytest.mark.integration`): verify tool is registered and discoverable
  - [x] Unit tests: verify tool returns correct text format when failures exist (mock DB)
  - [x] Unit tests: verify "All datasets passed" response when no failures (mock DB)
  - [x] Integration tests (`@pytest.mark.integration`): use `seeded_client` fixture against real Postgres
  - [x] Remove all stale `# RED PHASE:` / `# THIS TEST WILL FAIL` comments before story is done

## Dev Notes

### Critical: MCP layer is ADDITIVE on top of existing FastAPI app

The `dqs-serve` FastAPI app is fully functional with 7 API endpoints. Epic 5 adds the MCP layer **without touching any existing routes or models**. The architecture is clear:

```
MCP tools wrap the same API endpoint logic — same data, different interface.
```

From `_bmad-output/planning-artifacts/architecture.md#API & Communication Patterns`:
- MCP layer: FastMCP 3.2 wrapping same API endpoints
- `dqs-serve MCP depends on API endpoints existing` — they all exist (done in Epic 4)

### FastMCP 3.2.0 — Correct Integration Pattern

FastMCP version installed: **3.2.0** (confirmed via `dqs-serve/pyproject.toml` and `uv run python -c "import fastmcp; print(fastmcp.__version__)"`).

**Tool registration:**
```python
from fastmcp import FastMCP

mcp = FastMCP("dqs")

@mcp.tool
def query_failures(db: Session = Depends(get_db)) -> str:
    """Answer 'What failed last night?' — returns failure summary from latest DQS run."""
    ...
```

**Dependency injection in tools** — use `fastmcp.dependencies.Depends`, NOT `fastapi.Depends`:
```python
from fastmcp.dependencies import Depends  # NOT from fastapi import Depends
from sqlalchemy.orm import Session
from ..db.session import get_db

@mcp.tool(exclude_args=["db"])   # exclude 'db' from tool schema shown to LLMs
def query_failures(db: Session = Depends(get_db)) -> str:
    ...
```

**Mounting FastMCP to FastAPI** — use `mcp.http_app()`:
```python
# In src/serve/main.py, after router includes:
from .mcp.tools import mcp

app.mount("/mcp", mcp.http_app(path="/mcp"))
```

This creates a Starlette sub-application at `/mcp`. LLM clients connect to the MCP server at `http://host:8000/mcp`.

**IMPORTANT:** `mcp.http_app()` is the correct mounting approach for FastMCP 3.x. The older `mcp.asgi_app()` method does not exist in 3.2.0.

### Database Pattern — Identical to Existing Routes

Copy the DB session pattern from existing route files verbatim:

```python
from ..db.session import get_db   # relative import, same package
from fastmcp.dependencies import Depends  # FastMCP's Depends
from sqlalchemy import text
from sqlalchemy.orm import Session
```

**Query patterns** (same as `src/serve/routes/summary.py`):
- Use `db.execute(text("...")).mappings().all()` — SQLAlchemy 2.0 style
- NEVER use `session.query()` — legacy style banned per project-context.md
- NEVER query `dq_run` directly — always `v_dq_run_active`
- NEVER query `dq_metric_numeric` directly — always `v_dq_metric_numeric_active`

### SQL for `query_failures` Tool

The tool finds failures from the latest run and groups by LOB:

```sql
-- Step 1: Find the latest partition_date (most recent run)
SELECT MAX(partition_date) AS latest_date FROM v_dq_run_active

-- Step 2: Get all runs from that latest date with FAIL status
-- Grouped by LOB (lookup_code) for the response format
SELECT
    dataset_name,
    lookup_code,
    check_status,
    dqs_score,
    partition_date
FROM v_dq_run_active
WHERE partition_date = :latest_date
  AND check_status = 'FAIL'
ORDER BY lookup_code, dataset_name

-- Step 3: Get total dataset count for the latest run (for "N datasets processed")
SELECT COUNT(DISTINCT dataset_name) AS total_count
FROM v_dq_run_active
WHERE partition_date = :latest_date

-- Step 4 (optional): Get check types for failed datasets
-- Join v_dq_metric_numeric_active to see which check_type rows exist for failed runs
SELECT DISTINCT r.dataset_name, m.check_type
FROM v_dq_run_active r
JOIN v_dq_metric_numeric_active m ON m.run_id = r.id
WHERE r.partition_date = :latest_date
  AND r.check_status = 'FAIL'
ORDER BY r.dataset_name, m.check_type
```

**Alternative simpler approach** — call existing service logic directly in the tool function instead of raw SQL. The tool can call the same functions used by `GET /api/summary` by importing from `routes/summary.py`. This avoids SQL duplication. However, to keep the MCP module independent, inline SQL is preferred.

### Response Format (MCP tools return TEXT, not JSON)

LLM-optimized text responses — structured but not JSON blobs:

**Failure case:**
```
DQS Failure Summary — Latest Run (2026-04-03)
3 datasets failed out of 24 processed.

LOB_RETAIL (2 failures):
  - lob=retail/src_sys_nm=alpha/dataset=customers_daily [FRESHNESS, VOLUME]
  - lob=retail/src_sys_nm=beta/dataset=transactions [SCHEMA]

LOB_COMMERCIAL (1 failure):
  - lob=commercial/src_sys_nm=gamma/dataset=accounts [FRESHNESS]
```

**No-failure case (AC3 verbatim):**
```
All datasets passed in the latest run (2026-04-03). 24 datasets processed.
```

**No data case:**
```
No DQS run data available.
```

### Directory Structure — Files to Create/Modify

```
dqs-serve/
  src/serve/
    mcp/
      __init__.py       ← NEW: empty module marker
      tools.py          ← NEW: FastMCP instance + query_failures tool
    main.py             ← MODIFY: import mcp, add app.mount("/mcp", ...)
  tests/
    test_mcp/
      __init__.py       ← NEW: empty
      test_tools.py     ← NEW: unit + integration tests for MCP tools
```

**Do NOT touch:** Any existing route files (`routes/summary.py`, `routes/lobs.py`, `routes/datasets.py`, `routes/search.py`), existing services, `db/engine.py`, `db/models.py`, `db/session.py`, `conftest.py` (unless adding new mock support for MCP).

**Do NOT create:** New top-level directories outside `src/serve/` or `tests/`.

### Test Strategy

**Unit tests** (default suite, no marker) — mock DB via `override_db_dependency` autouse fixture already in `conftest.py`:
- `test_query_failures_tool_is_registered()` — verify tool appears in `asyncio.run(mcp.list_tools())`
- `test_query_failures_returns_text_format_on_failures()` — mock DB returns failure rows, assert formatted text
- `test_query_failures_returns_all_passed_message_when_no_failures()` — mock returns only PASS rows, assert AC3 text
- `test_query_failures_returns_no_data_message_when_db_empty()` — mock returns empty, assert no-data message

**Integration tests** (`@pytest.mark.integration`) — use `seeded_client` from existing `conftest.py`:
- `test_mcp_endpoint_is_accessible()` — `GET /mcp` returns 200 or correct MCP response
- `test_query_failures_with_real_data()` — call the tool against seeded fixtures, verify output

**Mocking FastMCP tools in unit tests** — note that `override_db_dependency` in conftest overrides `get_db` for FastAPI's `Depends`. For FastMCP's `Depends(get_db)`, you may need to add an additional override. Check if `fastmcp.dependencies.Depends` respects the same override mechanism as FastAPI's, or test the tool function directly by calling it with a mock DB session.

**Direct function testing pattern** (recommended for MCP tools):
```python
import asyncio
from unittest.mock import MagicMock

def test_query_failures_formats_correctly():
    mock_db = MagicMock()
    # Simulate latest_date query, failures query, etc.
    mock_db.execute.return_value.mappings.return_value.first.return_value = {"latest_date": datetime.date(2026, 4, 3)}
    # ... etc
    result = asyncio.run(query_failures(db=mock_db))  # if async, else: result = query_failures(db=mock_db)
    assert "failed" in result
```

### Conftest: ReferenceDataService Mock Already Set Up

The existing `conftest.py` `mock_reference_data_service` autouse fixture sets `app.state.reference_data`. The MCP tools should NOT need `ReferenceDataService` for Story 5.1 — the failure query tool uses `lookup_code` raw values, not resolved LOB names. If LOB name resolution is wanted, it can be added via `app.state.reference_data` access, but this is NOT required for Story 5.1 acceptance criteria.

### Python Rules — Epic 5 Returns to Pure Python

**Epic 4 Retro Action Items (must follow):**
1. Python resource management: psycopg2 connections use `try/finally: conn.close()` or context manager. Not applicable to SQLAlchemy's `get_db()` generator (which already uses `try/finally`), but applies to any direct `psycopg2.connect()` usage in tests.
2. `ruff`/`isort` not yet enforced but use consistent import ordering.
3. Remove ALL stale `# RED PHASE:` / `# THIS TEST WILL FAIL` comments before story is complete.
4. TypeScript floating Promise `void`-prefix rule does NOT apply here (Python only story).

**Python-specific rules from `project-context.md`:**
- Type hints on all functions (parameters AND return types) — required
- Pydantic 2 models if needed for structured output (not required for MCP text responses)
- SQLAlchemy 2.0 style — `Session`, `select()`, `text()` — never `session.query()`
- Relative imports within `serve` package: `from ..db.session import get_db`
- File naming: `snake_case.py` — confirmed: `tools.py` is correct
- Class naming: `PascalCase` — no classes needed for simple tool functions

### Anti-Patterns to NEVER Do

- **NEVER query `dq_run` raw table** — always `v_dq_run_active`
- **NEVER query `dq_metric_numeric` raw table** — always `v_dq_metric_numeric_active`
- **NEVER use `fastapi.Depends`** in FastMCP tools — use `fastmcp.dependencies.Depends`
- **NEVER return JSON blobs from MCP tools** — return human-readable text for LLM consumption
- **NEVER add FastMCP as a new top-level app** — mount it onto the existing FastAPI `app`
- **NEVER touch existing routes or models** — the MCP layer is purely additive
- **NEVER hardcode `9999-12-31 23:59:59`** — use `v_*_active` views (already filtering on sentinel)
- **NEVER return stack traces** — existing `@app.exception_handler(Exception)` in `main.py` handles this
- **NEVER use `session.query()` style** — SQLAlchemy 2.0 only
- **NEVER add check-type-specific logic** — only Spark knows check types; MCP returns what the API returns

### Architecture Boundaries Reminder

```
LLM clients → POST /mcp (FastMCP ASGI) → query_failures() → v_dq_run_active (Postgres)
Dashboard   → GET /api/summary (FastAPI) → routes/summary.py → v_dq_run_active (Postgres)
```

Both paths hit the same underlying data. "MCP tools wrap the same API endpoint logic — same data, different interface." [Source: architecture.md#API & Communication Patterns]

### Existing Patterns to Reuse (Not Reinvent)

| Pattern | Location | Use in Story 5.1 |
|---------|----------|-----------------|
| `@mcp.tool` registration | New in this story | Create for first time |
| `from ..db.session import get_db` | All route files | Copy verbatim |
| `db.execute(text(...)).mappings().all()` | `routes/summary.py` | Copy query pattern |
| `v_dq_run_active` view usage | All route files | Use same view |
| `_LATEST_PER_DATASET_SQL` CTE pattern | `routes/summary.py` | Reuse or adapt SQL |
| `override_db_dependency` fixture | `tests/conftest.py` | Autouse — no action needed |
| `mock_reference_data_service` | `tests/conftest.py` | Autouse — no action needed |
| Test file placement | `tests/test_routes/` | New: `tests/test_mcp/` |

### Project Structure Notes

- The `mcp/` package is already specified in the architecture: `dqs-serve/src/serve/mcp/tools.py` [Source: architecture.md#dqs-serve directory structure]
- This story creates the `mcp/` package for the FIRST time — it does not yet exist
- No shared config files — `mcp/tools.py` uses `src/serve/db/session.py` via relative imports
- The MCP server runs as part of the same `dqs-serve` process — not a separate service

### References

- Epic 5 Story 5.1 AC: `_bmad-output/planning-artifacts/epics/epic-5-mcp-llm-integration.md`
- Architecture — MCP layer: `_bmad-output/planning-artifacts/architecture.md#API & Communication Patterns`
- Architecture — dqs-serve structure: `_bmad-output/planning-artifacts/architecture.md#dqs-serve`
- Project Context — Python rules: `_bmad-output/project-context.md#Language-Specific Rules`
- Project Context — FastAPI rules: `_bmad-output/project-context.md#Framework-Specific Rules`
- Project Context — Anti-patterns: `_bmad-output/project-context.md#Anti-Patterns`
- Epic 4 Retro (Python lessons): `_bmad-output/implementation-artifacts/epic-4-retro-2026-04-04.md`
- Existing main.py: `dqs-serve/src/serve/main.py`
- DB session: `dqs-serve/src/serve/db/session.py`
- DB engine: `dqs-serve/src/serve/db/engine.py`
- Reference data deps: `dqs-serve/src/serve/dependencies.py`
- Test fixtures (conftest): `dqs-serve/tests/conftest.py`
- Active-record views DDL: `dqs-serve/src/serve/schema/views.sql`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

None — implementation completed cleanly on first pass.

### Completion Notes List

- Created `dqs-serve/src/serve/mcp/__init__.py` and `dqs-serve/src/serve/mcp/tools.py` with FastMCP 3.2.0 instance `mcp = FastMCP("dqs")`.
- Implemented `query_failures` tool with 4-query pattern against `v_dq_run_active` and `v_dq_metric_numeric_active` (active-record views only).
- Used `fastmcp.dependencies.Depends` (not `fastapi.Depends`) and `exclude_args=["db"]` as specified.
- Mounted MCP sub-app at `/mcp` via `app.mount("/mcp", mcp.http_app(path="/mcp"))` in `main.py` — additive, no existing routes modified.
- Removed all `@pytest.mark.skip` / RED PHASE markers from 15 unit tests; integration tests retain their markers (require real Postgres).
- All 15 unit tests pass; full suite 125 passed, 0 failures, 0 regressions.
- Note: FastMCP 3.2.0 emits a deprecation warning for `exclude_args` (deprecated in 2.14); the parameter is still functional and is required by story spec. No action needed — it continues to work correctly.

### File List

dqs-serve/src/serve/mcp/__init__.py
dqs-serve/src/serve/mcp/tools.py
dqs-serve/src/serve/main.py
dqs-serve/tests/test_mcp/__init__.py
dqs-serve/tests/test_mcp/test_tools.py

## Change Log

- 2026-04-04: Implemented Story 5.1 — created MCP package with FastMCP 3.2.0, registered `query_failures` tool, mounted at `/mcp` in main.py, removed RED PHASE skip markers. 15 unit tests pass, 125 total suite passing.
