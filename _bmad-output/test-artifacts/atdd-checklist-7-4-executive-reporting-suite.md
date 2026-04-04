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
inputDocuments:
  - _bmad-output/implementation-artifacts/7-4-executive-reporting-suite.md
  - _bmad/tea/config.yaml
  - dqs-serve/tests/test_routes/test_summary.py
  - dqs-serve/tests/conftest.py
  - dqs-dashboard/tests/pages/SummaryPage.test.tsx
  - _bmad-output/project-context.md
---

# ATDD Checklist: Story 7.4 — Executive Reporting Suite

## TDD Red Phase (Current)

All failing tests generated. Tests assert EXPECTED behavior and will fail until implementation is complete.

- **Backend Tests (pytest)**: 17 tests (unit + integration) — all fail until `routes/executive.py` exists
- **Frontend Tests (Vitest)**: 23 tests (all marked `it.skip`) — all fail until `ExecReportPage.tsx` exists

## Stack Detection

- **Detected stack**: `fullstack`
- **Backend**: Python/FastAPI (dqs-serve) — pytest
- **Frontend**: React/TypeScript (dqs-dashboard) — Vitest + RTL
- **Generation mode**: AI Generation (backend: API tests; frontend: component tests)

## Test Files Generated

| File | Type | Tests | TDD Phase |
|------|------|-------|-----------|
| `dqs-serve/tests/test_routes/test_executive.py` | Backend pytest | 17 | RED |
| `dqs-dashboard/tests/pages/ExecReportPage.test.tsx` | Frontend Vitest | 23 | RED |

## Acceptance Criteria Coverage

| AC | Description | Tests | Priority |
|----|-------------|-------|----------|
| AC1 | Cross-LOB quality scorecards with monthly trends | `test_returns_200_with_empty_db`, `test_response_has_expected_keys`, `renders_lob_monthly_scorecard` | P0 |
| AC2 | Deeper aggregation (monthly rollups) on existing data model | `test_lob_monthly_scores_covers_last_3_months`, `test_lob_monthly_scores_month_format_is_yyyy_mm` | P0 |
| AC3 | time_range=90d powers quarterly trend via existing endpoints | `test_lob_monthly_scores_covers_last_3_months` | P0 |
| AC4 | `v_dq_run_active` GROUP BY DATE_TRUNC monthly rollup | `test_lob_monthly_scores_uses_active_view`, `test_no_raw_tables_queried` | P0 |
| AC5 | ExecReportPage at /exec with 3 tables | `renders_lob_monthly_scorecard`, `renders_improvement_summary_with_delta`, `renders_source_system_accountability_table`, `positive_delta_renders_with_green_indicator`, `negative_delta_renders_with_red_indicator` | P0 |
| AC6 | GET /api/executive/report response shape + v_dq_run_active only | `test_response_has_expected_keys`, `test_lob_monthly_score_keys`, `test_source_system_score_keys`, `test_improvement_summary_keys`, `test_no_raw_tables_queried`, `test_source_system_extraction_correct`, `test_improvement_delta_computed_correctly`, `test_source_system_status_counts_correct` | P0 |
| AC7 | Loading state: Skeleton elements, no spinners | `renders_skeleton_while_loading`, `does_not_render_spinner_during_loading` | P0 |
| AC8 | Error state: "Failed to load executive report" + Retry button, never full-page crash | `renders_error_message_on_failure`, `renders_retry_button_on_error`, `retry_button_calls_refetch` | P0 |

## Test Strategy

### Backend (pytest) — `dqs-serve/tests/test_routes/test_executive.py`

**Class `TestExecutiveReportRouteWiring` (Unit, no DB):**
- `test_returns_200_with_empty_db` [P0] — route registered, returns 200
- `test_response_has_expected_keys` [P0] — lob_monthly_scores, source_system_scores, improvement_summary present
- `test_response_top_level_fields_are_lists` [P0] — all three fields are lists (never null)
- `test_response_is_json` [P0] — Content-Type: application/json
- `test_lob_monthly_score_keys` [P1] — lob_id, month, avg_score keys
- `test_source_system_score_keys` [P1] — src_sys_nm, dataset_count, avg_score, healthy_count, critical_count
- `test_improvement_summary_keys` [P1] — lob_id, baseline_score, current_score, delta
- `test_no_camel_case_keys_in_response` [P1] — snake_case only
- `test_pydantic_models_are_importable` [P0] — structural import check
- `test_pydantic_models_use_from_attributes_config` [P1] — ConfigDict(from_attributes=True)
- `test_router_is_importable_from_executive_module` [P0] — APIRouter named 'router'
- `test_executive_router_is_registered_in_main` [P0] — route reachable in FastAPI app

**Class `TestExecutiveReportIntegration` (Integration, real Postgres):**
- `test_lob_monthly_scores_uses_active_view` [P0] — expired records excluded
- `test_source_system_extraction_correct` [P0] — SPLIT_PART extracts 'alpha' from dataset_name
- `test_source_system_excludes_datasets_without_src_sys_nm` [P1] — WHERE filter correct
- `test_improvement_delta_computed_correctly` [P0] — delta = current - baseline
- `test_lob_monthly_scores_covers_last_3_months` [P0] — 3-month window correct
- `test_no_raw_tables_queried` [P0] — source file inspection
- `test_source_system_status_counts_correct` [P1] — healthy_count=PASS, critical_count=FAIL
- `test_lob_monthly_scores_month_format_is_yyyy_mm` [P1] — TO_CHAR format

### Frontend (Vitest) — `dqs-dashboard/tests/pages/ExecReportPage.test.tsx`

**Loading state (AC7):**
- `renders_skeleton_while_loading` [P0]
- `does_not_render_spinner_during_loading` [P0]
- `does_not_render_section_headings_during_loading` [P1]

**Error state (AC8):**
- `renders_error_message_on_failure` [P0]
- `renders_retry_button_on_error` [P0]
- `retry_button_calls_refetch` [P0]
- `does_not_render_table_rows_when_isError_is_true` [P1]

**LOB Monthly Scorecard (AC5):**
- `renders_lob_monthly_scorecard` [P0]
- `scorecard_shows_avg_scores_in_cells` [P0]
- `scorecard_shows_em_dash_for_null_scores` [P1]
- `scorecard_column_headers_are_month_labels` [P1]

**Improvement Summary (AC5):**
- `renders_improvement_summary_with_delta` [P0]
- `positive_delta_renders_with_green_indicator` [P0]
- `negative_delta_renders_with_red_indicator` [P0]
- `null_delta_shows_na` [P1]
- `improvement_summary_shows_lob_names` [P1]

**Source System Accountability (AC5):**
- `renders_source_system_accountability_table` [P0]
- `source_system_names_visible` [P0]
- `source_system_counts_visible` [P1]

**Rendering stability:**
- `renders_without_crashing_in_loading_state` [P0]
- `renders_without_crashing_with_full_data` [P0]
- `renders_without_crashing_in_error_state` [P0]
- `renders_without_crashing_with_empty_arrays` [P0]

**Pattern compliance:**
- `does_not_show_progressbar_spinner_when_isFetching` [P1]

## Red Phase TDD Validation

- [x] All backend tests: raise ImportError or AssertionError until `routes/executive.py` is created
- [x] All frontend tests: marked `it.skip()` — will be unskipped after implementation
- [x] No placeholder assertions (`expect(true).toBe(true)`)
- [x] All tests assert EXPECTED behavior per acceptance criteria
- [x] Anti-patterns guarded: no raw `dq_run` queries, no spinners, no `any` types
- [x] No temp artifacts left in random locations — all outputs in `_bmad-output/test-artifacts/`
- [x] No browser sessions to clean up (backend project uses AI generation only)

## Next Steps (TDD Green Phase)

After implementing the feature:

1. **Backend**: Run `cd dqs-serve && uv run pytest tests/test_routes/test_executive.py` — verify unit tests PASS
2. **Backend Integration**: Run `cd dqs-serve && uv run pytest -m integration tests/test_routes/test_executive.py` — verify integration tests PASS
3. **Frontend**: Remove `it.skip` from all tests in `ExecReportPage.test.tsx` (convert to `it`)
4. **Frontend**: Run `cd dqs-dashboard && npm test` — verify all tests PASS
5. If any tests fail: fix implementation (feature bug) or fix test (test bug)
6. Commit passing tests

## Implementation Guidance

**Files to create (new):**
- `dqs-serve/src/serve/routes/executive.py` — FastAPI router with 3 SQL queries
- `dqs-dashboard/src/pages/ExecReportPage.tsx` — React page with 3 MUI Tables

**Files to modify (existing):**
- `dqs-serve/src/serve/main.py` — add `include_router(executive_router.router, prefix='/api')`
- `dqs-dashboard/src/api/types.ts` — add 4 interfaces
- `dqs-dashboard/src/api/queries.ts` — add `useExecutiveReport()` hook
- `dqs-dashboard/src/App.tsx` — add `/exec` route
- `dqs-dashboard/src/layouts/AppLayout.tsx` — add "Executive Report" nav link

**Key constraints (from project-context.md):**
- ONLY query `v_dq_run_active` — NEVER raw `dq_run`
- Pydantic 2: `ConfigDict(from_attributes=True)` on ALL models
- SQLAlchemy 2.0: `db.execute(text(...)).mappings().all()`
- TypeScript: strict mode, NO `any` types, define all types in `api/types.ts`
- Loading: `Skeleton` elements only — NEVER spinners
- Error state: component-level — NEVER full-page crash

## Key Risks and Assumptions

1. **Mock DB returns empty lists for executive queries**: The conftest.py mock does not know about executive-specific params. Unit tests verify route wiring and shape only; data correctness is integration-test territory.
2. **Month format assumption**: Tests verify `YYYY-MM` format (7 chars with '-' separator). If a different format is chosen, update `test_lob_monthly_scores_month_format_is_yyyy_mm`.
3. **Delta sign**: Tests verify presence of delta value and positive/negative indicators. Exact formatting (▲/▼ prefix) is asserted loosely with regex to allow minor UI variations.
4. **Integration test data isolation**: Integration tests seed their own data and rely on `seeded_client` + `db_conn` fixtures for transaction rollback isolation.
