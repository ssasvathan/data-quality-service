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
story: 5-2-mcp-trending-comparison-tools
tdd_phase: RED
inputDocuments:
  - _bmad-output/implementation-artifacts/5-2-mcp-trending-comparison-tools.md
  - dqs-serve/tests/test_mcp/test_tools.py
  - dqs-serve/src/serve/mcp/tools.py
  - dqs-serve/tests/conftest.py
  - _bmad-output/project-context.md
---

# ATDD Checklist: Story 5.2 — MCP Trending & Comparison Tools

## TDD Red Phase (Current)

All failing tests generated and appended to existing test file.

- Unit Tests: 23 tests (all failing — RED PHASE)
- Integration Tests: 3 tests (excluded from default run, marked @pytest.mark.integration)
- Existing Story 5.1 Tests: 15 tests (all PASSING — unaffected)

## Stack Detection

- Detected stack: `backend`
- Generation mode: AI Generation (no browser recording needed)
- Execution mode: Sequential

## Prerequisites Satisfied

- [x] Story approved with clear acceptance criteria (3 ACs)
- [x] Test framework configured: `pytest` + `conftest.py` in `dqs-serve/tests/`
- [x] Existing test file exists: `dqs-serve/tests/test_mcp/test_tools.py`
- [x] Existing implementation exists: `dqs-serve/src/serve/mcp/tools.py`

## Step 1 — Preflight Context

**Story ACs:**
1. AC1: `query_dataset_trend` returns current DQS score, trend direction (improving/degrading/stable), score history, and flagged checks
2. AC2: `compare_lob_quality` returns LOBs ranked ascending by DQS score, with dataset counts and top failing check types
3. AC3: Unknown dataset → `"No dataset found matching '{query}'."`

**Framework config:**
- `test_stack_type`: backend (auto-detected via `pyproject.toml`)
- `tea_use_playwright_utils`: disabled (backend stack, no browser)
- `tea_use_pactjs_utils`: disabled
- `tea_pact_mcp`: none

**Knowledge fragments loaded (core + backend):**
- `data-factories.md` — mock DB helpers follow factory pattern
- `test-quality.md` — isolation rules, no hard waits, explicit assertions
- `test-healing-patterns.md` — SQL dispatch pattern for query matching
- `test-levels-framework.md` — unit for logic, integration for DB
- `test-priorities-matrix.md` — P0/P1/P2 assignment

## Step 2 — Generation Mode

**Mode selected:** AI Generation (backend project, acceptance criteria clear, no UI browser recording needed).

## Step 3 — Test Strategy

### Acceptance Criteria → Test Scenarios

| AC | Scenario | Level | Priority |
|----|----------|-------|----------|
| AC1 | `query_dataset_trend` registered in mcp.list_tools() | Unit | P0 |
| AC1 | `query_dataset_trend` has docstring description | Unit | P1 |
| AC1 | `query_dataset_trend` excludes db from schema | Unit | P1 |
| AC2 | `compare_lob_quality` registered in mcp.list_tools() | Unit | P0 |
| AC2 | `compare_lob_quality` has docstring description | Unit | P1 |
| AC1 | Returns str (not JSON, not dict) | Unit | P0 |
| AC1 | Response includes current score and trend direction | Unit | P0 |
| AC1 | Trend direction = 'degrading' when scores decline > 2pts | Unit | P1 |
| AC1 | Flagged checks present when run status is FAIL | Unit | P1 |
| AC3 | Returns not-found message for unknown dataset | Unit | P0 |
| AC1 | Response is not a JSON string | Unit | P1 |
| AC1 | No flagged checks when status is PASS | Unit | P2 |
| AC1 | time_range parameter accepted without error | Unit | P2 |
| AC2 | Returns str (not JSON, not dict) | Unit | P0 |
| AC2 | LOBs ranked ascending by aggregate score | Unit | P0 |
| AC2 | Dataset counts per LOB present | Unit | P0 |
| AC2 | Aggregate score per LOB present | Unit | P0 |
| AC2 | Response is not a JSON string | Unit | P1 |
| AC2 | No-data message when DB empty | Unit | P1 |
| AC2 | Top failing check types per LOB included | Unit | P1 |
| AC1 | Active-record views used (module source check) | Unit | P1 |
| AC1 | query_dataset_trend function exists and callable | Unit | P0 |
| AC2 | compare_lob_quality function exists and callable | Unit | P0 |
| AC1 | Integration: query_dataset_trend against real seeded DB | Integration | P1 |
| AC2 | Integration: compare_lob_quality against real seeded DB | Integration | P1 |
| AC3 | Integration: not-found message with real DB | Integration | P1 |

### Test Level Selection Rationale

**Unit tests chosen for:** Tool registration, response format, business logic (trend direction computation), SQL pattern compliance. These are fast, isolated, mock-DB tests that don't require Postgres.

**Integration tests chosen for:** End-to-end validation that SQL queries run against real Postgres views without errors. These are excluded from default pytest run via `addopts = -m 'not integration'`.

**No E2E/browser tests:** Backend stack, no UI components involved in this story.

## Step 4 — Generated Tests

### New Mock DB Helpers (appended to test_tools.py)

| Helper | Purpose |
|--------|---------|
| `_make_mock_db_for_trend()` | Simulates matched dataset + trend history + flagged checks |
| `_make_mock_db_for_trend_not_found()` | Simulates ILIKE returning no match (AC3) |
| `_make_mock_db_for_lob_comparison()` | Simulates LOB dataset rows + batch check types |
| `_make_mock_db_for_lob_comparison_empty()` | Simulates empty LOB data |

**SQL dispatch signatures (matching pattern from existing `_make_mock_db_with_failures`):**
- `"ilike"` → dataset search query (AC1 ILIKE pattern)
- `"ds_max"` → trend history query (AC1 `_TREND_HISTORY_SQL` CTE)
- `"dq_run_id = :run_id"` → flagged checks for latest run (AC1 `_FAILING_CHECKS_FOR_RUN_SQL`)
- `"row_number()"` → LOB latest-per-dataset CTE (AC2 `_LOB_LATEST_PER_DATASET_SQL`)
- `"any(:run_ids)"` → batched check types (AC2 `_LOB_CHECK_TYPES_BATCH_SQL`)

### New Test Classes (appended to test_tools.py)

| Class | Tests | ACs |
|-------|-------|-----|
| `TestQueryDatasetTrendRegistration` | 3 | AC1 |
| `TestCompareLobQualityRegistration` | 2 | AC2 |
| `TestQueryDatasetTrendResponseFormat` | 8 | AC1, AC3 |
| `TestCompareLobQualityResponseFormat` | 6 | AC2 |
| `TestQueryDatasetTrendQueryPatterns` | 3 | AC1, AC2 |
| `TestTrendingComparisonIntegration` | 3 | AC1, AC2, AC3 |

**Total new tests: 25** (23 unit + 2 integration in default run exclusion)

## Step 4C — Aggregation Results

**TDD Red Phase Validation:**

```
✅ All 23 unit tests FAIL before implementation (correct RED phase)
✅ All failing with ImportError or AssertionError (not AttributeError/TypeError)
✅ Failure messages are actionable (include implementation guidance)
✅ Existing 15 Story 5.1 tests continue to PASS (no regression)
✅ 1 structural test passes (test_query_dataset_trend_uses_active_record_views_only)
   — intentional: validates anti-pattern guard on existing module
```

**Test run output:**
- 16 passed (15 Story 5.1 + 1 structural guard)
- 22 failed (all Story 5.2 unit tests = correct RED phase)
- 6 deselected (integration tests excluded by default)

## Step 5 — Validation & Completion

### Prerequisites Check

- [x] Story acceptance criteria: 3 ACs, all mapped to test scenarios
- [x] Test file created: APPENDED (not replaced) to `dqs-serve/tests/test_mcp/test_tools.py`
- [x] Tests designed to fail before implementation (TDD red phase verified)
- [x] No orphaned browser sessions (backend project, no browser automation)
- [x] Temp artifacts: checklist saved to `_bmad-output/test-artifacts/` (not random locations)
- [x] No `# RED PHASE:` / `# THIS TEST WILL FAIL` comments added (per story task 3 requirement)

### Failure Mode Analysis

All new tests fail with one of two expected reasons:
1. `ImportError: cannot import name 'query_dataset_trend' from 'serve.mcp.tools'` — function not implemented yet
2. `AssertionError: Tool 'query_dataset_trend' not found in registered tools: ['query_failures']` — tool not decorated yet

These are clean, actionable failures pointing directly at what needs to be implemented.

### Key Risks / Assumptions

1. **Mock DB SQL dispatch** — The `_make_mock_db_for_trend()` helper dispatches on lowercase SQL text. If the implementation uses slightly different SQL text (e.g., `"ds_max"` CTE name changed), the mock will fall through to the empty-result branch. The dispatch strings match the SQL constants specified in story dev notes exactly.

2. **LOB aggregate score computation** — Tests assert `"62.30"` for LOB_COMMERCIAL. The mock provides two rows each with `dqs_score=62.30`, so AVG = 62.30. If implementation uses a different aggregation (e.g., MIN or weighted), the score assertion may need updating.

3. **Trend direction threshold** — Tests assert `"degrading"` for scores 95.20→87.50 (delta=-7.70). This relies on the `_compute_trend_direction` threshold of 2.0 points specified in dev notes. If threshold changes, update `test_returns_trend_direction_degrading_for_decreasing_scores`.

4. **Integration test fixture content** — Integration tests use broad/non-existent dataset names to test AC3. The `"ZZZZNOTEXIST_9999_NEVERREAL"` query is unlikely to match any fixture data. If fixtures.sql contains a matching row, update the test.

### Next Steps (TDD Green Phase)

After implementing the two new tool functions in `dqs-serve/src/serve/mcp/tools.py`:

1. Run unit tests: `cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py`
2. Verify all 25 new tests pass (green phase)
3. Run integration tests: `uv run pytest -m integration tests/test_mcp/test_tools.py`
4. Remove any `# RED PHASE:` comments if any were added during implementation (none in these tests)
5. Commit passing tests

### Implementation Guidance (from test failures)

Functions to implement in `dqs-serve/src/serve/mcp/tools.py`:

```python
@mcp.tool(exclude_args=["db"])
def query_dataset_trend(
    dataset_name: str,
    time_range: str = "7d",
    db: Session = Depends(get_db),
) -> str:
    """Show DQS score trend for a dataset. ..."""
    ...

@mcp.tool(exclude_args=["db"])
def compare_lob_quality(
    db: Session = Depends(get_db),
) -> str:
    """Compare data quality across all LOBs ranked by DQS score. ..."""
    ...
```

See story dev notes for full SQL patterns and response format specifications.

## Generated Files

- `dqs-serve/tests/test_mcp/test_tools.py` — MODIFIED (appended Story 5.2 tests)
- `_bmad-output/test-artifacts/atdd-checklist-5-2-mcp-trending-comparison-tools.md` — this file
