---
stepsCompleted:
  - step-01-preflight-and-context
  - step-02-generation-mode
  - step-03-test-strategy
  - step-04-generate-tests
  - step-04c-aggregate
  - step-05-validate-and-complete
lastStep: step-05-validate-and-complete
lastSaved: 2026-04-03
inputDocuments:
  - _bmad-output/implementation-artifacts/4-3-fastapi-dataset-detail-metrics-api-endpoints.md
  - dqs-serve/tests/conftest.py
  - dqs-serve/tests/test_routes/test_lobs.py
  - dqs-serve/tests/test_routes/test_summary.py
  - dqs-serve/src/serve/schema/fixtures.sql
  - dqs-serve/src/serve/schema/views.sql
  - _bmad/tea/config.yaml
  - _bmad-output/project-context.md
---

# ATDD Checklist: Story 4.3 — FastAPI Dataset Detail & Metrics API Endpoints

## TDD Red Phase Summary

**Status:** RED PHASE COMPLETE — Failing tests generated

| Category | Count | Status |
|---|---|---|
| Unit tests (no DB, mock session) | 36 | All skipped (red phase) |
| Integration tests (real Postgres) | 35 | All skipped (red phase) |
| **Total** | **71** | **All use @pytest.mark.skip** |

## Stack Detection

- **Detected stack:** `backend` (Python 3.12+, FastAPI, pytest)
- **Generation mode:** AI generation (no browser recording — backend only)
- **E2E tests:** Not applicable (pure backend story, no UI components)
- **Execution mode:** Sequential (auto-resolved, backend stack)

## Acceptance Criteria Coverage

| AC | Description | Unit Tests | Integration Tests | Priority |
|---|---|---|---|---|
| AC1 | GET /api/datasets/{id} returns DatasetDetail | 6 | 12 | P0 |
| AC2 | GET /api/datasets/{id}/metrics returns grouped metrics | 7 | 8 | P0 |
| AC3 | GET /api/datasets/{id}/trend returns ordered trend | 9 | 8 | P0 |
| AC4 | All endpoints query v_*_active views | (covered via integration) | 4 | P0 |

### Detailed Acceptance Criteria Mapping

**AC1: GET /api/datasets/{dataset_id} returns full dataset metadata**

Unit tests (mock DB):
- [x] `TestDatasetDetailEndpointRouteWiring::test_dataset_detail_endpoint_returns_200` [P0]
- [x] `TestDatasetDetailEndpointRouteWiring::test_dataset_detail_response_is_json` [P0]
- [x] `TestDatasetDetailEndpointRouteWiring::test_dataset_detail_response_has_all_required_snake_case_keys` [P1]
- [x] `TestDatasetDetailEndpointRouteWiring::test_dataset_detail_no_camel_case_keys` [P1]
- [x] `TestDatasetDetailEndpointRouteWiring::test_dataset_detail_returns_404_for_unknown_id` [P0]
- [x] `TestDatasetDetailEndpointRouteWiring::test_dataset_detail_404_has_correct_error_body` [P0]
- [x] `TestDatasetDetailPydanticModels::test_dataset_detail_model_is_importable` [P0]
- [x] `TestDatasetDetailPydanticModels::test_dataset_detail_model_fields_are_snake_case` [P1]
- [x] `TestDatasetHelperFunctions::test_extract_source_system_from_standard_path` [P1]
- [x] `TestDatasetHelperFunctions::test_extract_source_system_from_legacy_path` [P1]
- [x] `TestDatasetHelperFunctions::test_extract_source_system_returns_unknown_when_segment_absent` [P1]
- [x] `TestDatasetHelperFunctions::test_compose_hdfs_path` [P1]
- [x] `TestDatasetHelperFunctions::test_parse_format_from_jsonb_string` [P2]
- [x] `TestDatasetHelperFunctions::test_parse_format_avro_jsonb_string` [P2]
- [x] `TestDatasetHelperFunctions::test_parse_format_none_returns_unknown` [P2]

Integration tests (real Postgres + seeded fixtures):
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_dataset_name` [P0]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_lob_id` [P0]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_source_system` [P1]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_format_from_jsonb` [P2]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_format_for_transactions` [P2]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_hdfs_path` [P1]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_row_count` [P0]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_previous_row_count` [P1]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_correct_check_status_and_dqs_score` [P0]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_returns_404_for_unknown_id_integration` [P0]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_parent_path_from_orchestration_run` [P1]
- [x] `TestDatasetDetailEndpointDataCorrectness::test_dataset_detail_legacy_path_source_system_extraction` [P1]

**AC2: GET /api/datasets/{dataset_id}/metrics returns all check results**

Unit tests:
- [x] `TestDatasetDetailPydanticModels::test_dataset_metrics_response_model_is_importable` [P0]
- [x] `TestDatasetDetailPydanticModels::test_numeric_metric_model_is_importable` [P0]
- [x] `TestDatasetDetailPydanticModels::test_detail_metric_model_is_importable` [P0]
- [x] `TestDatasetMetricsEndpointRouteWiring::test_dataset_metrics_endpoint_returns_200` [P0]
- [x] `TestDatasetMetricsEndpointRouteWiring::test_dataset_metrics_response_has_required_top_level_keys` [P1]
- [x] `TestDatasetMetricsEndpointRouteWiring::test_dataset_metrics_check_results_is_list` [P1]
- [x] `TestDatasetMetricsEndpointRouteWiring::test_dataset_metrics_dataset_id_echoed_in_response` [P1]
- [x] `TestDatasetMetricsEndpointRouteWiring::test_dataset_metrics_returns_404_for_unknown_id` [P0]
- [x] `TestDatasetMetricsEndpointRouteWiring::test_dataset_metrics_404_has_correct_error_body` [P0]
- [x] `TestDatasetMetricsEndpointRouteWiring::test_dataset_metrics_no_camel_case_keys` [P1]

Integration tests:
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_returns_check_results_list` [P0]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_volume_check_type_present_with_numeric_metrics` [P0]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_freshness_check_type_present` [P0]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_schema_check_type_present_with_detail_metrics` [P1]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_check_result_items_have_correct_keys` [P1]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_status_is_overall_run_status_not_per_check_inferred` [P1]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_metrics_grouped_by_check_type` [P0]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_returns_404_for_unknown_id_integration` [P0]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_payments_dataset_has_volume_metrics` [P0]
- [x] `TestDatasetMetricsEndpointDataCorrectness::test_metrics_transactions_has_schema_detail_with_format` [P1]

**AC3: GET /api/datasets/{dataset_id}/trend returns daily DQS scores**

Unit tests:
- [x] `TestDatasetDetailPydanticModels::test_dataset_trend_response_model_is_importable` [P0]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_endpoint_returns_200_default_time_range` [P0]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_accepts_7d_time_range` [P1]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_accepts_30d_time_range` [P1]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_accepts_90d_time_range` [P1]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_response_has_required_keys` [P1]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_time_range_echoed_in_response` [P2]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_trend_field_is_list` [P1]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_returns_404_for_unknown_id` [P0]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_404_has_correct_error_body` [P0]
- [x] `TestDatasetTrendEndpointRouteWiring::test_dataset_trend_default_time_range_is_7d` [P2]

Integration tests:
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_returns_7d_data_for_sales_daily` [P0]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_points_are_ordered_oldest_to_newest` [P1]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_points_have_correct_keys` [P1]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_dqs_scores_are_numeric` [P1]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_sales_daily_scores_are_98_50` [P1]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_time_range_30d_returns_fewer_points_than_7_fixture_rows` [P1]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_returns_404_for_unknown_id_integration` [P0]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_single_point_dataset_returns_one_element_list` [P2]
- [x] `TestDatasetTrendEndpointDataCorrectness::test_trend_time_range_echoed_in_response_integration` [P2]

**AC4: All endpoints query active-record views (v_*_active)**

Integration tests:
- [x] `TestActiveRecordViewUsage::test_dataset_detail_excludes_expired_records` [P0]
- [x] `TestActiveRecordViewUsage::test_dataset_metrics_excludes_expired_metric_rows` [P1]
- [x] `TestActiveRecordViewUsage::test_dataset_trend_excludes_expired_runs` [P1]
- [x] `TestActiveRecordViewUsage::test_datasets_router_registered_in_main` [P0]

## Generated Files

- `dqs-serve/tests/test_routes/test_datasets.py` — 71 failing acceptance tests (36 unit + 35 integration), all with `@pytest.mark.skip`
- `dqs-serve/tests/conftest.py` — Extended `_make_mock_db_session()` with `dataset_id` / `dataset_name` / `orchestration_run_id` dispatch logic

## Key Design Decisions

### TDD Red Phase Mechanism (Python/pytest)

Unlike TypeScript/Playwright (`test.skip()`), this project uses `@pytest.mark.skip` at the class level. The marker is applied as a class decorator so that:
- All tests in the class are skipped collectively
- The skip reason clearly states "TDD RED PHASE: route not implemented yet"
- Integration test classes also carry `@pytest.mark.integration` for suite separation

**To enter green phase:** Remove `@pytest.mark.skip(...)` decorators from each test class after implementing the feature.

### Anti-Pattern Guard: No Per-Check Status Inference

`TestDatasetMetricsEndpointDataCorrectness::test_metrics_status_is_overall_run_status_not_per_check_inferred` explicitly tests that `status` in each `CheckResult` equals the overall `dq_run.check_status`. This guards against the project-wide anti-pattern of inferring PASS/FAIL/WARN per check type from metric values (which is Spark's responsibility only).

### Mock DB Extension for Unit Tests

The `conftest.py` `_make_mock_db_session()` has been extended to dispatch on:
- `dataset_id` param: `9` → `_FAKE_DATASET_DETAIL_RUN_ROW` (known), `9999` → `[]` (404)
- `dataset_name` param + `days_back` → `_FAKE_TREND_ROW` (trend query)
- `dataset_name` param without `days_back` → `[]` (previous_row_count OFFSET 1)
- `orchestration_run_id` param → `_FAKE_ORCHESTRATION_RUN_ROW` (parent_path lookup)

### No E2E Tests

This story is backend-only (`detected_stack = backend`). No browser/UI tests are appropriate. The `dqs-dashboard` will have its own tests when Story 4.4+ implements the UI drill-down.

## Next Steps (TDD Green Phase)

After implementing `dqs-serve/src/serve/routes/datasets.py` and wiring into `main.py`:

1. Remove `@pytest.mark.skip(...)` from each test class in `test_datasets.py`
2. Run unit tests: `cd dqs-serve && uv run pytest tests/test_routes/test_datasets.py`
3. Verify all unit tests pass (green phase)
4. If unit tests pass, run integration tests: `cd dqs-serve && uv run pytest -m integration tests/test_routes/test_datasets.py`
5. Fix any failures — either implementation bug or test assumption error
6. Commit passing tests

## Implementation Guidance (for dev-story)

Endpoints to implement (in `dqs-serve/src/serve/routes/datasets.py`):
- `GET /api/datasets/{dataset_id}` → `DatasetDetail`
- `GET /api/datasets/{dataset_id}/metrics` → `DatasetMetricsResponse`
- `GET /api/datasets/{dataset_id}/trend` → `DatasetTrendResponse`

Helper functions to implement:
- `_extract_source_system(dataset_name: str) -> str`
- `_compose_hdfs_path(dataset_name: str, partition_date: datetime.date) -> str`
- `_parse_format(detail_value: Any) -> str`

Wire into `main.py`:
```python
from .routes import datasets as datasets_router
app.include_router(datasets_router.router, prefix="/api")
```

Extend `conftest.py` mock to handle `dataset_id` queries (done in this ATDD step).

## Validation Results

- [x] All test files created in correct locations
- [x] All unit tests skipped (36 skipped, 0 failed in default suite)
- [x] All integration tests excluded from default suite via `addopts = -m 'not integration'`
- [x] All tests assert EXPECTED behavior (no placeholder assertions)
- [x] Existing tests unaffected (20 existing unit tests still pass)
- [x] No CLI sessions to clean up (backend stack, no browser automation)
- [x] Checklist saved to `_bmad-output/test-artifacts/`
- [x] Anti-pattern guard for per-check status inference included
- [x] Expired-record view usage tests included (AC4)

## Performance

- **Execution Mode:** Sequential (backend, auto-resolved)
- **Unit test suite time:** ~0.05s (all skipped)
- **E2E generation:** N/A (backend stack)
