# Story 5.2: MCP Trending & Comparison Tools

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **LLM consumer**,
I want MCP tools for trending queries and LOB comparisons,
so that I can get trend analysis and quality comparisons conversationally.

## Acceptance Criteria

1. **Given** an LLM consumer asks "Show trending for dataset ue90-omni-transactions" **When** the MCP trending tool executes **Then** it returns a formatted summary: current DQS score, trend direction (improving/degrading/stable), score history over the requested period, and flagged checks
2. **Given** an LLM consumer asks "Which LOB has worst quality?" **When** the MCP comparison tool executes **Then** it returns LOBs ranked by DQS score (ascending), with dataset counts and top failing check types per LOB
3. **Given** an LLM consumer queries a dataset that doesn't exist **When** the MCP tool executes **Then** it returns a clear message: "No dataset found matching '{query}'."

## Tasks / Subtasks

- [x] Task 1: Implement `query_dataset_trend` tool in `dqs-serve/src/serve/mcp/tools.py` (AC: 1, 3)
  - [x] Add tool using `@mcp.tool(exclude_args=["db"])` decorator on the **existing `mcp` instance** — do NOT create a new `FastMCP` instance
  - [x] Accept `dataset_name` (str, required) and `time_range` (str, default `"7d"`) parameters
  - [x] Step 1: Search for dataset by partial name using ILIKE (reuse `_SEARCH_SQL` pattern from `routes/search.py`) — limit to top match
  - [x] Step 2: If no match → return `"No dataset found matching '{dataset_name}'."`
  - [x] Step 3: Get latest run for matched dataset from `v_dq_run_active` (current score, status, partition_date)
  - [x] Step 4: Get trend history using `_TREND_SQL` pattern from `routes/datasets.py` (`days_back = days - 1`)
  - [x] Step 5: Compute trend direction from score history: improving (last > first + threshold), degrading (last < first - threshold), stable otherwise
  - [x] Step 6: Get failing check types for latest run from `v_dq_metric_numeric_active` (same join as `_CHECK_TYPES_SQL` in existing `tools.py`)
  - [x] Return LLM-optimised plain text response (not JSON) — see format spec in Dev Notes
  - [x] Tool must have a docstring (used as MCP description for LLM discovery)
- [x] Task 2: Implement `compare_lob_quality` tool in `dqs-serve/src/serve/mcp/tools.py` (AC: 2)
  - [x] Add tool using `@mcp.tool(exclude_args=["db"])` decorator on the **existing `mcp` instance**
  - [x] No user parameters needed (always returns all LOBs ranked)
  - [x] Step 1: Fetch latest run per dataset from `v_dq_run_active` using `ROW_NUMBER() OVER (PARTITION BY dataset_name ORDER BY partition_date DESC)` CTE (same `_LOBS_LATEST_SQL` pattern from `routes/lobs.py`)
  - [x] Step 2: Group by `lookup_code`, compute aggregate score (avg of latest scores per dataset), dataset count, FAIL count
  - [x] Step 3: Get top failing check types per LOB from `v_dq_metric_numeric_active` using batched ANY(:run_ids) query (same pattern as `_METRIC_CHECK_TYPES_BATCH_SQL` in `routes/lobs.py`)
  - [x] Step 4: Rank LOBs by aggregate score ascending (worst first)
  - [x] Return LLM-optimised plain text response — see format spec in Dev Notes
  - [x] Tool must have a docstring
  - [x] If no LOB data exists → return `"No LOB quality data available."`
- [x] Task 3: Write pytest tests in `dqs-serve/tests/test_mcp/test_tools.py` (AC: 1, 2, 3)
  - [x] Append new test classes to the **existing** `test_tools.py` — do NOT create a new file
  - [x] Unit tests for `query_dataset_trend`: tool is registered in `mcp.list_tools()`, returns str, correct format with trend history, "not found" message for unknown dataset, no-JSON response
  - [x] Unit tests for `compare_lob_quality`: tool is registered, returns str, LOBs ranked ascending by score, includes dataset counts, no-JSON response
  - [x] Integration tests (`@pytest.mark.integration`): call tools against seeded DB, verify str output
  - [x] Remove ALL `# RED PHASE:` / `# THIS TEST WILL FAIL` comments before story is done

## Dev Notes

### Critical: Add Tools to the EXISTING `mcp` Instance — Never Create a Second One

The `mcp` FastMCP instance is already created in `dqs-serve/src/serve/mcp/tools.py`:

```python
mcp: FastMCP = FastMCP("dqs")
```

Story 5.2 MUST add new tools to **this same instance**. Do NOT:
- Create a new `mcp2 = FastMCP(...)` anywhere
- Create a separate `tools_v2.py` file
- Import a second FastMCP instance

Just add the two new tool functions decorated with `@mcp.tool(exclude_args=["db"])` below the existing `query_failures` function in the same `tools.py` file.

### FastMCP 3.2.0 — Same Patterns as Story 5.1

FastMCP version: **3.2.0** (confirmed in `dqs-serve/pyproject.toml`).

**Deprecation note (from Story 5.1 completion):** FastMCP 3.2.0 emits a deprecation warning for `exclude_args` (deprecated in 2.14). The parameter is still functional — continue using it. Do NOT remove it; it keeps internal DB dependencies hidden from LLM tool schemas.

**Tool registration pattern** (copy from existing `query_failures`):
```python
@mcp.tool(exclude_args=["db"])
def query_dataset_trend(
    dataset_name: str,
    time_range: str = "7d",
    db: Session = Depends(get_db),
) -> str:
    """Show DQS score trend for a dataset. Use for queries like 'Show trending for dataset X'.
    ...
    """
    ...

@mcp.tool(exclude_args=["db"])
def compare_lob_quality(
    db: Session = Depends(get_db),
) -> str:
    """Compare data quality across all LOBs ranked by DQS score.
    ...
    """
    ...
```

**CRITICAL import:** `from fastmcp.dependencies import Depends` — NOT `from fastapi import Depends`. Same as Story 5.1. Anti-pattern test in test_tools.py already validates this for the whole module.

### SQL Patterns — Reuse Existing SQL, Never Reinvent

All SQL needed already exists in the route files. Copy the patterns exactly (adapt parameters only):

**For `query_dataset_trend` — dataset search + trend:**

```python
# Step 1: Search by partial name (ILIKE) — from routes/search.py _SEARCH_SQL
# Use the same ROW_NUMBER() CTE + ILIKE pattern but take the TOP 1 match
_TREND_DATASET_SEARCH_SQL = text("""
    WITH ranked AS (
        SELECT
            id,
            dataset_name,
            lookup_code,
            check_status,
            dqs_score,
            partition_date,
            ROW_NUMBER() OVER (
                PARTITION BY dataset_name
                ORDER BY partition_date DESC
            ) AS rn
        FROM v_dq_run_active
        WHERE dataset_name ILIKE '%' || :q || '%'
    )
    SELECT id, dataset_name, lookup_code, check_status, dqs_score, partition_date
    FROM ranked
    WHERE rn = 1
    ORDER BY dataset_name
    LIMIT 1
""")

# Step 4: Trend history — from routes/datasets.py _TREND_SQL (identical pattern)
_TREND_HISTORY_SQL = text("""
    WITH ds_max AS (
        SELECT MAX(partition_date) AS max_date
        FROM v_dq_run_active
        WHERE dataset_name = :dataset_name
    )
    SELECT
        DATE(v.partition_date) AS date,
        AVG(v.dqs_score) AS dqs_score
    FROM v_dq_run_active v, ds_max m
    WHERE v.dataset_name = :dataset_name
      AND v.partition_date >= m.max_date - CAST(:days_back AS INTEGER) * INTERVAL '1 day'
    GROUP BY DATE(v.partition_date)
    ORDER BY date ASC
""")

# Step 6: Failing check types for latest run — from existing tools.py _CHECK_TYPES_SQL
# Already defined as _CHECK_TYPES_SQL — reuse it by passing run_id as :latest_date param
# or query differently: join by run_id directly
_FAILING_CHECKS_FOR_RUN_SQL = text("""
    SELECT DISTINCT m.check_type
    FROM v_dq_metric_numeric_active m
    WHERE m.dq_run_id = :run_id
    ORDER BY m.check_type
""")
```

**For `compare_lob_quality` — LOB ranking:**

```python
# From routes/lobs.py _LOBS_LATEST_SQL — identical (with NULL lookup_code exclusion)
_LOB_LATEST_PER_DATASET_SQL = text("""
    WITH ranked AS (
        SELECT
            id AS run_id,
            dataset_name,
            lookup_code,
            check_status,
            dqs_score,
            ROW_NUMBER() OVER (
                PARTITION BY dataset_name
                ORDER BY partition_date DESC
            ) AS rn
        FROM v_dq_run_active
        WHERE lookup_code IS NOT NULL
    )
    SELECT run_id, dataset_name, lookup_code, check_status, dqs_score
    FROM ranked
    WHERE rn = 1
""")

# From routes/lobs.py _METRIC_CHECK_TYPES_BATCH_SQL — identical
_LOB_CHECK_TYPES_BATCH_SQL = text("""
    SELECT DISTINCT dq_run_id, check_type
    FROM v_dq_metric_numeric_active
    WHERE dq_run_id = ANY(:run_ids)
    AND dq_run_id IN (
        SELECT dq_run_id FROM v_dq_metric_numeric_active
        WHERE dq_run_id = ANY(:run_ids)
        -- Only FAIL runs contribute to "top failing check types"
    )
""")
```

**IMPORTANT:** Use `db.execute(text(...)).mappings().all()` — SQLAlchemy 2.0 style. Never `session.query()`.

### Time Range — Reuse `_TIME_RANGE_DAYS` Logic

`routes/datasets.py` and `routes/lobs.py` both define `_TIME_RANGE_DAYS = {"7d": 7, "30d": 30, "90d": 90}`.
Do NOT import from those modules (MCP tools module is independent). Define or inline the mapping:

```python
_TIME_RANGE_DAYS: dict[str, int] = {"7d": 7, "30d": 30, "90d": 90}

def _parse_time_range(time_range: str) -> int:
    return _TIME_RANGE_DAYS.get(time_range, 7)
```

`days_back = days - 1` — the trend window is `[max_date - days_back, max_date]` inclusive.

### Trend Direction Computation

Compute trend direction from the score history list (oldest to newest from `_TREND_HISTORY_SQL`):

```python
def _compute_trend_direction(scores: list[float]) -> str:
    """Compute trend direction from chronological score history."""
    if len(scores) < 2:
        return "stable"
    delta = scores[-1] - scores[0]
    if delta > 2.0:    # threshold: 2-point improvement
        return "improving"
    if delta < -2.0:   # threshold: 2-point degradation
        return "degrading"
    return "stable"
```

Threshold of 2.0 points is appropriate given DQS scores are 0-100 floats.

### Response Format (Text Only — Never JSON)

**`query_dataset_trend` success response:**
```
DQS Trend — dataset: lob=omni/src_sys_nm=ue90/dataset=transactions (30d)
Current Score: 87.50 | Status: PASS | Date: 2026-04-03
Trend: degrading

Score History (oldest → newest):
  2026-03-04: 95.20
  2026-03-11: 91.50
  2026-03-18: 89.00
  2026-04-03: 87.50

Flagged Checks: FRESHNESS, VOLUME
```

**`query_dataset_trend` not-found response (AC3):**
```
No dataset found matching 'ue90-omni-transactions'.
```

**`compare_lob_quality` response:**
```
LOB Quality Comparison — All LOBs (worst to best)

1. LOB_COMMERCIAL — Score: 62.30 | 8 datasets | 3 failing
   Top failing checks: FRESHNESS, VOLUME
2. LOB_RETAIL — Score: 81.50 | 15 datasets | 1 failing
   Top failing checks: SCHEMA
3. LOB_MARKETS — Score: 96.20 | 5 datasets | 0 failing
```

**`compare_lob_quality` no-data response:**
```
No LOB quality data available.
```

### "Top Failing Check Types" for `compare_lob_quality`

Only fetch check types for FAIL runs, grouped by LOB. Use the `_METRIC_CHECK_TYPES_BATCH_SQL` batched pattern from `routes/lobs.py` but filter to FAIL run_ids only:

```python
# Collect only run_ids where check_status == 'FAIL'
fail_run_ids = [row["run_id"] for row in lob_dataset_rows if row["check_status"] == "FAIL"]
# Query check types for those failed runs
```

If no FAIL runs for a LOB, show no check types (or omit the "Top failing checks" line).

### "Flagged Checks" for `query_dataset_trend`

Flagged checks = check types present in `v_dq_metric_numeric_active` for the latest run_id of the matched dataset. Use `_FAILING_CHECKS_FOR_RUN_SQL` with `run_id = matched_run["id"]`. Return ALL check types present (regardless of pass/fail) as flags, or only FAIL check types if the run status is FAIL.

**Simplest correct approach:** If `check_status == 'PASS'`, flagged checks = empty (show "None"). If `check_status != 'PASS'`, return all check types from `v_dq_metric_numeric_active` for that run_id.

### Error Handling — Same Pattern as `query_failures`

Wrap each DB call in `try/except Exception:` and return `"Error querying DQS data. Please try again later."` on failure. Log with `logger.error(..., exc_info=True)`. Do NOT let exceptions propagate — MCP tools must always return a str.

### Files to Modify (Only One File Changes)

```
dqs-serve/
  src/serve/
    mcp/
      tools.py          ← MODIFY: add two new tool functions below query_failures
  tests/
    test_mcp/
      test_tools.py     ← MODIFY: append new test classes for the two new tools
```

**Do NOT touch:** `main.py` (MCP is already mounted), `__init__.py`, any route files, services, conftest.py, schema files.

**Do NOT create:** New Python files, new test files, new directories.

### Test Strategy — Append to Existing `test_tools.py`

Add new test classes at the bottom of `dqs-serve/tests/test_mcp/test_tools.py`. Follow the exact same pattern as the existing `TestMcpToolRegistration` and `TestQueryFailuresResponseFormat` classes.

**New test classes to add:**

```python
class TestQueryDatasetTrendRegistration:
    def test_query_dataset_trend_tool_is_registered(self) -> None: ...
    def test_query_dataset_trend_tool_has_docstring(self) -> None: ...
    def test_query_dataset_trend_excludes_db_from_schema(self) -> None: ...

class TestQueryDatasetTrendResponseFormat:
    def test_returns_str(self) -> None: ...
    def test_returns_trend_summary_with_score_and_direction(self) -> None: ...
    def test_returns_not_found_message_for_unknown_dataset(self) -> None: ...
    def test_does_not_return_json(self) -> None: ...

class TestCompareLobQualityRegistration:
    def test_compare_lob_quality_tool_is_registered(self) -> None: ...
    def test_compare_lob_quality_has_docstring(self) -> None: ...

class TestCompareLobQualityResponseFormat:
    def test_returns_str(self) -> None: ...
    def test_lobs_ranked_ascending_by_score(self) -> None: ...
    def test_includes_dataset_counts(self) -> None: ...
    def test_does_not_return_json(self) -> None: ...

class TestTrendingComparisonIntegration:
    @pytest.mark.integration
    def test_query_dataset_trend_with_seeded_data(self, seeded_client) -> None: ...
    @pytest.mark.integration
    def test_compare_lob_quality_with_seeded_data(self, seeded_client) -> None: ...
```

**Mock DB helper pattern** (following the same structure as `_make_mock_db_with_failures` in the existing test file):

Create `_make_mock_db_for_trend()` and `_make_mock_db_for_lob_comparison()` helper functions that dispatch on SQL text content (same `str(query).lower()` pattern). Key SQL signatures to match:
- `"ilike"` → dataset search query → return one fake dataset row or empty
- `"ds_max"` → trend history query → return fake trend points
- `"dq_run_id = :run_id"` → flagged checks query → return check type rows
- `"row_number()"` → LOB latest-per-dataset query → return fake LOB rows
- `"any(:run_ids)"` → batched check types query → return check type rows

### Direct Function Testing Pattern (Same as Story 5.1)

Test the tool functions directly (bypassing FastMCP's call machinery) by importing and calling with mock DB:

```python
from serve.mcp.tools import query_dataset_trend, compare_lob_quality

result = query_dataset_trend(dataset_name="omni-transactions", db=mock_db)
assert isinstance(result, str)
assert "Score" in result

result = compare_lob_quality(db=mock_db)
assert isinstance(result, str)
```

**Note on `override_db_dependency`:** The autouse `override_db_dependency` fixture in `conftest.py` overrides FastAPI's `get_db`. For direct function calls with a passed-in `db=mock_db`, the fixture override is irrelevant — just pass the mock directly.

### Python Rules — From Project Context + Epic 5 Lessons

- **Type hints** on ALL function parameters and return types — required
- **SQLAlchemy 2.0 style** — `db.execute(text(...)).mappings().all()` — never `session.query()`
- **Relative imports** within `serve` package: `from ..db.session import get_db` (already imported)
- **File naming**: `snake_case.py` — already correct
- **Logging**: include `logger.debug("tool_name called with ...")` at entry, `logger.error(..., exc_info=True)` on DB errors
- **Remove ALL stale** `# RED PHASE:` / `# THIS TEST WILL FAIL` comments before story is complete

### Anti-Patterns to NEVER Do

- **NEVER create a second `FastMCP` instance** — add tools to the existing `mcp` in `tools.py`
- **NEVER query `dq_run` raw table** — always `v_dq_run_active`
- **NEVER query `dq_metric_numeric` raw table** — always `v_dq_metric_numeric_active`
- **NEVER use `fastapi.Depends`** — use `fastmcp.dependencies.Depends`
- **NEVER return JSON** from MCP tools — return human-readable text
- **NEVER touch existing routes or models** — MCP layer is purely additive
- **NEVER hardcode `9999-12-31 23:59:59`** — use `v_*_active` views (already filtering)
- **NEVER use `session.query()` style** — SQLAlchemy 2.0 only
- **NEVER import from route files** — tools.py is independent; duplicate SQL as module-level `text()` constants

### Architecture Boundaries

```
LLM clients → POST /mcp (FastMCP ASGI) → query_dataset_trend() → v_dq_run_active (Postgres)
LLM clients → POST /mcp (FastMCP ASGI) → compare_lob_quality() → v_dq_run_active (Postgres)
Dashboard   → GET /api/datasets/{id}/trend (FastAPI) → routes/datasets.py → v_dq_run_active
Dashboard   → GET /api/lobs (FastAPI) → routes/lobs.py → v_dq_run_active
```

MCP tools query the same views as the dashboard routes — "same data, different interface."

### Existing Patterns to Reuse (Not Reinvent)

| Pattern | Location | Use in Story 5.2 |
|---------|----------|------------------|
| `@mcp.tool(exclude_args=["db"])` | `tools.py` query_failures | Copy verbatim for new tools |
| `from fastmcp.dependencies import Depends` | `tools.py` (already imported) | Already present, reuse |
| `db.execute(text(...)).mappings().all()` | All route files | Copy query pattern |
| `ROW_NUMBER() OVER PARTITION BY` CTE | `routes/lobs.py`, `routes/summary.py` | Reuse for LOB aggregation |
| `_TREND_SQL` with ds_max CTE | `routes/datasets.py` | Copy and adapt |
| `_SEARCH_SQL` ILIKE + ROW_NUMBER | `routes/search.py` | Adapt for dataset lookup |
| `_METRIC_CHECK_TYPES_BATCH_SQL` ANY(:run_ids) | `routes/lobs.py` | Reuse for check type lookup |
| `_TIME_RANGE_DAYS` + `_parse_time_range` | `routes/datasets.py` | Duplicate in tools.py |
| `try/except Exception` per DB call | `tools.py` query_failures | Copy error handling pattern |
| `_make_mock_db_with_failures` helper | `tests/test_mcp/test_tools.py` | Follow same pattern for new helpers |
| `asyncio.run(mcp.list_tools())` | `tests/test_mcp/test_tools.py` | Use same for new tool registration tests |

### References

- Epic 5 Story 5.2 AC: `_bmad-output/planning-artifacts/epics/epic-5-mcp-llm-integration.md`
- Story 5.1 (previous, done): `_bmad-output/implementation-artifacts/5-1-fastmcp-tool-registration-failure-query-tool.md`
- Existing MCP tools: `dqs-serve/src/serve/mcp/tools.py`
- Existing MCP tests: `dqs-serve/tests/test_mcp/test_tools.py`
- Dataset trend route: `dqs-serve/src/serve/routes/datasets.py` (see `_TREND_SQL`, `_parse_time_range`)
- LOB comparison route: `dqs-serve/src/serve/routes/lobs.py` (see `_LOBS_LATEST_SQL`, `_METRIC_CHECK_TYPES_BATCH_SQL`)
- Search route: `dqs-serve/src/serve/routes/search.py` (see `_SEARCH_SQL` ILIKE pattern)
- Architecture — MCP layer: `_bmad-output/planning-artifacts/architecture.md#API & Communication Patterns`
- Project Context — Python rules: `_bmad-output/project-context.md#Language-Specific Rules`
- Project Context — Anti-patterns: `_bmad-output/project-context.md#Anti-Patterns`
- Test fixtures (conftest): `dqs-serve/tests/conftest.py`
- Active-record views DDL: `dqs-serve/src/serve/schema/views.sql`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

No debug issues encountered. Implementation followed Dev Notes patterns exactly.

### Completion Notes List

- Implemented `query_dataset_trend` tool on the existing `mcp` instance in `tools.py` — not a new FastMCP instance.
- Implemented `compare_lob_quality` tool on the same `mcp` instance.
- Added helper functions: `_parse_time_range`, `_compute_trend_direction` (module-level, not imported from routes).
- Added SQL constants: `_TREND_DATASET_SEARCH_SQL` (ILIKE + ROW_NUMBER CTE), `_TREND_HISTORY_SQL` (ds_max CTE), `_FAILING_CHECKS_FOR_RUN_SQL`, `_LOB_LATEST_PER_DATASET_SQL`, `_LOB_CHECK_TYPES_BATCH_SQL`.
- All tools return LLM-optimised plain text — never JSON.
- Both tools use `fastmcp.dependencies.Depends` (not fastapi.Depends).
- Both tools use `exclude_args=["db"]` to hide DB dependency from tool schema.
- `query_dataset_trend` returns AC3 not-found message when ILIKE search yields no result.
- `compare_lob_quality` returns no-data message when ROW_NUMBER query returns empty.
- Flagged checks are only populated when `check_status != 'PASS'`.
- Top failing checks per LOB are only fetched for FAIL run_ids via batched ANY(:run_ids) query.
- Test results: 38 unit tests pass (15 story 5.1 + 23 story 5.2), 6 integration tests deselected (require Postgres), 148 total unit tests pass across full suite — no regressions.

### File List

- `dqs-serve/src/serve/mcp/tools.py` (modified: added 2 tools + helper functions + SQL constants)

### Review Findings

- [x] [Review][Patch] Invalid `time_range` values rendered as-is in response header [tools.py:359] — Fixed: added `_normalize_time_range()` helper; unknown values default to `"7d"` in display.
- [x] [Review][Patch] Missing `test_compare_lob_quality_excludes_db_from_schema` test from story spec [test_tools.py:TestCompareLobQualityRegistration] — Fixed: test added to registration class.
- [x] [Review][Defer] Pre-existing `_CHECK_TYPES_SQL` in tools.py uses `m.run_id` instead of `m.dq_run_id` [tools.py:91] — deferred, pre-existing Story 5.1 bug

### Change Log

- 2026-04-04: Implemented `query_dataset_trend` and `compare_lob_quality` MCP tools; all 38 unit tests pass, no regressions.
- 2026-04-04: Code review complete — 2 low patches applied (time_range normalisation, missing schema exclusion test); 1 pre-existing defer noted; 149 tests pass.
