---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: '2026-04-04'
workflowType: testarch-atdd
inputDocuments:
  - _bmad-output/implementation-artifacts/5-1-fastmcp-tool-registration-failure-query-tool.md
  - _bmad-output/project-context.md
  - dqs-serve/src/serve/main.py
  - dqs-serve/src/serve/db/session.py
  - dqs-serve/tests/conftest.py
  - dqs-serve/pyproject.toml
  - dqs-serve/src/serve/routes/summary.py
tdd_phase: RED
story_id: 5-1-fastmcp-tool-registration-failure-query-tool
---

# ATDD Checklist — Epic 5, Story 5.1: FastMCP Tool Registration & Failure Query Tool

**Date:** 2026-04-04
**Author:** Sas
**Primary Test Level:** Backend (pytest, unit + integration)
**TDD Phase:** RED (all tests skipped — feature not yet implemented)

---

## Story Summary

LLM consumers need a way to ask "What failed last night?" and get a structured
failure summary without opening the dashboard. This story creates the MCP layer
on top of the existing FastAPI `dqs-serve` application using FastMCP 3.2.0.
The `query_failures` tool queries the same `v_dq_run_active` view as the dashboard
and returns a formatted text response suitable for LLM consumption.

**As an** LLM consumer
**I want** an MCP tool that answers "What failed last night?"
**So that** I can get a quick failure summary without opening the dashboard

---

## Acceptance Criteria

1. **AC1** — Given FastMCP is configured in dqs-serve, When the serve layer starts, Then MCP tools are registered and discoverable by LLM clients
2. **AC2** — Given an LLM consumer asks "What failed last night?", When the MCP failure query tool executes, Then it calls the same API endpoints the dashboard uses AND returns a formatted text response: count of failures, dataset names, check types that failed, and LOB grouping
3. **AC3** — Given no failures occurred in the last run, When the failure query tool executes, Then it returns "All datasets passed in the latest run ({timestamp}). {N} datasets processed."

---

## Stack Detection

**Detected stack:** `backend` (Python/FastAPI, no frontend framework)
**Test framework:** pytest (pyproject.toml)
**Execution mode:** sequential (AI generation)
**Playwright Utils:** disabled (backend-only)
**Pact.js Utils:** disabled (no contract testing configured)

---

## Test Strategy

### Test Level Selection

| AC | Scenario | Level | Priority |
|----|----------|-------|----------|
| AC1 | `serve.mcp.tools` module is importable without errors | Unit | P0 |
| AC1 | `mcp` object is a `FastMCP` instance | Unit | P0 |
| AC1 | `query_failures` tool appears in `mcp.list_tools()` | Unit | P0 |
| AC1 | `query_failures` tool has a docstring (MCP description) | Unit | P1 |
| AC1 | `db` parameter excluded from tool schema via `exclude_args=["db"]` | Unit | P1 |
| AC1 | MCP mounted at `/mcp` in FastAPI app | Unit | P0 |
| AC1 | Existing routes still present after MCP mount (additive only) | Unit | P1 |
| AC2 | `query_failures` returns `str` (not JSON, not dict) | Unit | P0 |
| AC2 | Failure summary includes count, dataset names, check types, LOB grouping | Unit | P0 |
| AC2 | Per-LOB failure counts are correct | Unit | P0 |
| AC2 | Response date/timestamp context present | Unit | P0 |
| AC2 | Response is NOT a JSON blob | Unit | P1 |
| AC3 | "All datasets passed..." message when no FAIL rows | Unit | P0 |
| AC3 | AC3 message includes run date and total dataset count | Unit | P0 |
| AC2/3 | "No DQS run data available." when DB is empty | Unit | P1 |
| AC2 | `fastmcp.dependencies.Depends` used (not `fastapi.Depends`) | Unit | P1 |
| AC2 | SQL queries use `v_dq_run_active`, not raw `dq_run` table | Unit | P1 |
| AC1 | MCP HTTP endpoint responds (not 404) | Integration | P0 |
| AC2 | `query_failures` returns str against real seeded Postgres | Integration | P1 |
| AC3 | Response format matches AC2 or AC3 depending on fixture data | Integration | P1 |

### TDD Red Phase Confirmation

All tests use `@pytest.mark.skip(reason="RED PHASE: ...")` to mark them as intentionally failing.
Tests assert EXPECTED behavior that will only pass after implementation.

---

## Failing Tests Created (RED Phase)

### Unit Tests (15 tests — no DB required, all @pytest.mark.skip active)

**File:** `dqs-serve/tests/test_mcp/test_tools.py`

#### TestMcpToolRegistration (5 tests)

- `test_mcp_module_is_importable`
  - **Priority:** P0
  - **Status:** RED — `serve.mcp` module does not exist
  - **Verifies:** AC1 — `serve.mcp.tools` is importable

- `test_mcp_instance_is_fastmcp`
  - **Priority:** P0
  - **Status:** RED — module not created
  - **Verifies:** AC1 — `mcp = FastMCP("dqs")` exists in `tools.py`

- `test_query_failures_tool_is_registered`
  - **Priority:** P0
  - **Status:** RED — `@mcp.tool` decorator not applied
  - **Verifies:** AC1 — `query_failures` appears in `asyncio.run(mcp.list_tools())`

- `test_query_failures_tool_has_docstring_description`
  - **Priority:** P1
  - **Status:** RED — function not created
  - **Verifies:** AC1 — tool has non-empty description (from docstring)

- `test_query_failures_tool_excludes_db_from_schema`
  - **Priority:** P1
  - **Status:** RED — decorator not applied
  - **Verifies:** AC1 — `db` not in `inputSchema.properties` (via `exclude_args=["db"]`)

#### TestMcpMainAppMount (2 tests)

- `test_mcp_is_mounted_on_fastapi_app`
  - **Priority:** P0
  - **Status:** RED — `app.mount("/mcp", ...)` not in `main.py`
  - **Verifies:** AC1 — `/mcp` route present in `app.routes`

- `test_fastmcp_import_does_not_break_fastapi_import`
  - **Priority:** P1
  - **Status:** RED — `serve.mcp` module missing
  - **Verifies:** AC1 — MCP mount is additive; existing routes survive

#### TestQueryFailuresResponseFormat (6 tests)

- `test_query_failures_returns_str`
  - **Priority:** P0
  - **Status:** RED — function not implemented
  - **Verifies:** AC2 — return type is `str`, not `dict` or JSON string

- `test_query_failures_returns_formatted_summary_when_failures_exist`
  - **Priority:** P0
  - **Status:** RED — function not implemented
  - **Verifies:** AC2 — failure count (3), total (24), LOB_RETAIL, LOB_COMMERCIAL,
    dataset names (customers_daily, transactions, accounts), check types (FRESHNESS, SCHEMA, VOLUME),
    run date (2026-04-03)

- `test_query_failures_lob_grouping_correct_counts`
  - **Priority:** P0
  - **Status:** RED — function not implemented
  - **Verifies:** AC2 — LOB_RETAIL section shows "2", LOB_COMMERCIAL section shows "1"

- `test_query_failures_returns_all_passed_message_when_no_failures`
  - **Priority:** P0
  - **Status:** RED — function not implemented
  - **Verifies:** AC3 — "All datasets passed in the latest run (2026-04-03). 24 datasets processed."

- `test_query_failures_returns_no_data_message_when_db_empty`
  - **Priority:** P1
  - **Status:** RED — function not implemented
  - **Verifies:** AC2/3 edge case — "No DQS run data available." when MAX(partition_date) is NULL

- `test_query_failures_does_not_return_json`
  - **Priority:** P1
  - **Status:** RED — function not implemented
  - **Verifies:** Anti-pattern guard — response does not start with `{` or `[`

#### TestQueryFailuresQueryPatterns (2 tests)

- `test_query_failures_does_not_import_fastapi_depends`
  - **Priority:** P1
  - **Status:** RED — module not implemented
  - **Verifies:** Anti-pattern guard — `from fastapi import Depends` absent from `tools.py`

- `test_query_failures_uses_active_record_views_only`
  - **Priority:** P1
  - **Status:** RED — module not implemented
  - **Verifies:** Anti-pattern guard — `v_dq_run_active` present, `FROM dq_run` absent

### Integration Tests (3 tests — @pytest.mark.integration, excluded from default suite)

**File:** `dqs-serve/tests/test_mcp/test_tools.py`

Note: Integration tests also have `@pytest.mark.skip` for RED phase on top of `@pytest.mark.integration`.

#### TestMcpEndpointAccessibility (1 test)

- `test_mcp_http_endpoint_responds`
  - **Priority:** P0
  - **Status:** RED — MCP not mounted
  - **Verifies:** AC1 — `GET /mcp` does not return 404; MCP ASGI is reachable

#### TestQueryFailuresIntegration (2 tests)

- `test_query_failures_with_seeded_data_returns_str`
  - **Priority:** P1
  - **Status:** RED — function not implemented
  - **Verifies:** AC2 — real SQLAlchemy session executes without errors, returns str

- `test_query_failures_with_seeded_pass_data_returns_all_passed`
  - **Priority:** P1
  - **Status:** RED — function not implemented
  - **Verifies:** AC2/3 — result matches either AC2 or AC3 format depending on fixture data

---

## Mock Strategy

### `_make_mock_db_with_failures()`

Simulates four sequential `db.execute()` calls:
1. `SELECT MAX(partition_date)` → returns `{"latest_date": date(2026, 4, 3)}`
2. `SELECT ... WHERE check_status = 'FAIL'` → 3 rows (LOB_RETAIL x2, LOB_COMMERCIAL x1)
3. `SELECT COUNT(DISTINCT dataset_name)` → `{"total_count": 24}`
4. Check types join → 4 rows (FRESHNESS, VOLUME, SCHEMA, FRESHNESS)

### `_make_mock_db_no_failures()`

Same as above but query 2 returns empty list (all datasets PASS).

### `_make_mock_db_empty()`

All `db.execute()` calls return `first()=None` and `all()=[]`.
Simulates fresh DB with no run data at all.

---

## Implementation Checklist

### To make `TestMcpToolRegistration` tests pass:

- [ ] Create `dqs-serve/src/serve/mcp/__init__.py` (empty)
- [ ] Create `dqs-serve/src/serve/mcp/tools.py` with:
  ```python
  from fastmcp import FastMCP
  from fastmcp.dependencies import Depends
  from sqlalchemy.orm import Session
  from ..db.session import get_db

  mcp = FastMCP("dqs")

  @mcp.tool(exclude_args=["db"])
  def query_failures(db: Session = Depends(get_db)) -> str:
      """Answer 'What failed last night?' — returns failure summary from latest DQS run."""
      ...
  ```
- [ ] Verify `asyncio.run(mcp.list_tools())` includes `query_failures`
- [ ] Run: `cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py::TestMcpToolRegistration -v`

### To make `TestMcpMainAppMount` tests pass:

- [ ] In `dqs-serve/src/serve/main.py`, after existing router includes:
  ```python
  from .mcp.tools import mcp
  app.mount("/mcp", mcp.http_app(path="/mcp"))
  ```
- [ ] Do NOT remove existing routes
- [ ] Run: `cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py::TestMcpMainAppMount -v`

### To make `TestQueryFailuresResponseFormat` tests pass:

- [ ] Implement `query_failures()` body in `tools.py`:
  - Query 1: `SELECT MAX(partition_date) AS latest_date FROM v_dq_run_active`
  - Handle NULL result → return `"No DQS run data available."`
  - Query 2: `SELECT dataset_name, lookup_code, check_status, dqs_score, partition_date FROM v_dq_run_active WHERE partition_date = :latest_date AND check_status = 'FAIL' ORDER BY lookup_code, dataset_name`
  - Query 3: `SELECT COUNT(DISTINCT dataset_name) AS total_count FROM v_dq_run_active WHERE partition_date = :latest_date`
  - Query 4: `SELECT DISTINCT r.dataset_name, m.check_type FROM v_dq_run_active r JOIN v_dq_metric_numeric_active m ON m.run_id = r.id WHERE r.partition_date = :latest_date AND r.check_status = 'FAIL' ORDER BY r.dataset_name, m.check_type`
  - When `failure_rows` is empty: return `f"All datasets passed in the latest run ({latest_date}). {total_count} datasets processed."`
  - When failures exist: format as:
    ```
    DQS Failure Summary — Latest Run ({latest_date})
    {N} datasets failed out of {total_count} processed.

    {LOB_CODE} ({n} failures):
      - {dataset_name} [{CHECK_TYPE, ...}]
    ```
- [ ] Run: `cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py::TestQueryFailuresResponseFormat -v`

### To make `TestQueryFailuresQueryPatterns` tests pass:

- [ ] Use `from fastmcp.dependencies import Depends` (NOT `from fastapi import Depends`)
- [ ] Use `v_dq_run_active` and `v_dq_metric_numeric_active` views only
- [ ] Never query `dq_run` or `dq_metric_numeric` raw tables
- [ ] Run: `cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py::TestQueryFailuresQueryPatterns -v`

### To make integration tests pass:

- [ ] Ensure a real Postgres instance is running with seeded data
- [ ] Remove `@pytest.mark.skip` from integration tests (after unit tests pass)
- [ ] Run: `cd dqs-serve && uv run pytest -m integration tests/test_mcp/test_tools.py -v`

---

## Running Tests

```bash
# Run all unit tests for this story (default suite — no DB required)
cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py -v

# Run a specific test class
cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py::TestMcpToolRegistration -v

# Run integration tests (requires real Postgres)
cd dqs-serve && uv run pytest -m integration tests/test_mcp/test_tools.py -v

# Run full test suite to verify existing tests still pass
cd dqs-serve && uv run pytest -v

# Confirm RED phase (all tests skip)
cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py -v 2>&1 | grep -E "SKIP|PASS|FAIL"
```

---

## Anti-Patterns Guarded by Tests

| Anti-Pattern | Guarding Test |
|---|---|
| `from fastapi import Depends` in MCP tools | `test_query_failures_does_not_import_fastapi_depends` |
| Querying raw `dq_run` table | `test_query_failures_uses_active_record_views_only` |
| Returning JSON blob from MCP tool | `test_query_failures_does_not_return_json` |
| MCP replacing existing FastAPI routes | `test_fastmcp_import_does_not_break_fastapi_import` |
| Missing tool registration | `test_query_failures_tool_is_registered` |
| `db` parameter exposed in LLM tool schema | `test_query_failures_tool_excludes_db_from_schema` |

---

## Red-Green-Refactor Workflow

### RED Phase (Complete) — 2026-04-04

**TEA Agent Responsibilities:**

- All tests written and skipping (15 unit + 3 integration)
- Mock helpers created for DB simulation
- Anti-pattern guards included
- Implementation checklist with exact code snippets

**Verification:**

```
$ cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py -v
...
15 skipped, 3 deselected in 0.02s
```

All 15 tests skip cleanly. RED phase verified.

---

### GREEN Phase (DEV Agent — Next Steps)

1. Create `dqs-serve/src/serve/mcp/__init__.py`
2. Create `dqs-serve/src/serve/mcp/tools.py` with FastMCP instance + `query_failures` tool
3. Mount MCP in `main.py`
4. Remove `@pytest.mark.skip` from tests one by one as each task completes
5. Run `uv run pytest tests/test_mcp/test_tools.py -v` → verify tests turn GREEN
6. Remove `@pytest.mark.skip` from integration tests and run with `-m integration`
7. Remove ALL `# RED PHASE:` comments from test file before story is done

---

### REFACTOR Phase (After All Tests Pass)

1. Review `tools.py` for SQL duplication with `routes/summary.py`
2. Extract shared SQL constants if beneficial (keep MCP module independent per story notes)
3. Verify `uv run pytest` (full suite) still passes — no regressions
4. Run `ruff check` and `isort` for import ordering

---

## Knowledge Base References Applied

- **test-levels-framework.md** — backend-only: unit + integration, no E2E
- **test-priorities-matrix.md** — P0 for AC coverage, P1 for anti-pattern guards
- **data-factories.md** — mock DB helpers (`_make_mock_db_*`) as factory functions
- **test-quality.md** — clear failure messages, one assertion per concern
- **api-testing-patterns.md** — direct function testing pattern (recommended for MCP tools per story notes)

---

## Notes

- The 3 integration tests are excluded by `addopts = -m 'not integration'` in `pyproject.toml`. They also carry `@pytest.mark.skip` for the RED phase. To run them: `uv run pytest -m integration tests/test_mcp/test_tools.py`.
- The `_make_mock_db_*` helpers use a call counter to simulate sequential SQL queries in `query_failures()`. The order matters: (1) MAX date, (2) FAIL rows, (3) COUNT distinct, (4) check types.
- Integration tests call `query_failures(db=db)` directly (not via HTTP), which is the recommended pattern for MCP tool testing per story dev notes.
- `fastmcp.dependencies.Depends` is validated via source inspection (`inspect.getsource`) — this verifies the correct import without running the function against a real FastMCP DI container.
- The `@pytest.mark.skip` + `@pytest.mark.integration` combination on integration tests means they are doubly excluded: by the default `-m 'not integration'` AND by the skip marker.

---

**Generated by BMad TEA Agent (bmad-testarch-atdd)** — 2026-04-04
