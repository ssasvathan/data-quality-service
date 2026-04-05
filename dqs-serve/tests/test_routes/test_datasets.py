"""Acceptance tests for GET /api/datasets/{dataset_id},
GET /api/datasets/{dataset_id}/metrics, and GET /api/datasets/{dataset_id}/trend endpoints.

TDD RED PHASE: All tests are marked @pytest.mark.skip because the endpoints
do not exist yet. Tests assert EXPECTED behavior per acceptance criteria.
Remove skip markers after implementation to enter green phase.

Test categories:
  - Unit tests (no DB, mock session): verify route wiring, Pydantic model shapes,
    helper functions, 404 error format, snake_case fields
  - Integration tests (real Postgres + seeded fixtures): verify data correctness,
    view usage, query correctness, grouped metrics, trend ordering

Run unit tests:     cd dqs-serve && uv run pytest tests/test_routes/test_datasets.py
Run integration:    cd dqs-serve && uv run pytest -m integration tests/test_routes/test_datasets.py
"""

from __future__ import annotations

import datetime
from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient

if TYPE_CHECKING:
    import psycopg2.extensions


# ---------------------------------------------------------------------------
# Shared constants
# ---------------------------------------------------------------------------

DATASET_DETAIL_ENDPOINT = "/api/datasets/{dataset_id}"
DATASET_METRICS_ENDPOINT = "/api/datasets/{dataset_id}/metrics"
DATASET_TREND_ENDPOINT = "/api/datasets/{dataset_id}/trend"

UNKNOWN_DATASET_ID = 9999  # No fixture row with this ID — triggers 404
KNOWN_DATASET_ID = 9        # From conftest _FAKE_DATASET_DETAIL_RUN_ROW (unit tests)

# Expected snake_case keys in DatasetDetail response
EXPECTED_DATASET_DETAIL_KEYS = {
    "dataset_id",
    "dataset_name",
    "lob_id",
    "source_system",
    "format",
    "hdfs_path",
    "parent_path",
    "partition_date",
    "row_count",
    "previous_row_count",
    "last_updated",
    "run_id",
    "rerun_number",
    "dqs_score",
    "check_status",
    "error_message",
}

# Expected snake_case keys in DatasetMetricsResponse (top-level)
EXPECTED_METRICS_TOP_KEYS = {
    "dataset_id",
    "check_results",
}

# Expected snake_case keys in each CheckResult item
EXPECTED_CHECK_RESULT_KEYS = {
    "check_type",
    "status",
    "numeric_metrics",
    "detail_metrics",
}

# Expected snake_case keys in DatasetTrendResponse (top-level)
EXPECTED_TREND_TOP_KEYS = {
    "dataset_id",
    "time_range",
    "trend",
}

# Expected snake_case keys in each TrendPoint item
EXPECTED_TREND_POINT_KEYS = {
    "date",
    "dqs_score",
}


# ---------------------------------------------------------------------------
# Unit tests — GET /api/datasets/{dataset_id}
# ---------------------------------------------------------------------------


class TestDatasetDetailEndpointRouteWiring:
    """AC1 [P0]: GET /api/datasets/{dataset_id} must be registered and return 200.

    TDD RED PHASE: endpoint does not exist — all tests skip.
    """

    def test_dataset_detail_endpoint_returns_200(self) -> None:
        """AC1 [P0]: GET /api/datasets/{dataset_id} must return HTTP 200.

        Fails until src/serve/routes/datasets.py is created and wired into main.py.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200, (
            f"GET /api/datasets/{KNOWN_DATASET_ID} returned {response.status_code}, expected 200. "
            "Route not registered — create src/serve/routes/datasets.py with APIRouter "
            "and include it in main.py with prefix='/api'."
        )

    def test_dataset_detail_response_is_json(self) -> None:
        """AC1 [P0]: Response Content-Type must be application/json."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert "application/json" in response.headers.get("content-type", ""), (
            "GET /api/datasets/{dataset_id} did not return JSON content-type"
        )

    def test_dataset_detail_response_has_all_required_snake_case_keys(self) -> None:
        """AC1 [P1]: Response must contain all required snake_case fields.

        Per project-context.md: all API responses use snake_case JSON field names.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        actual_keys = set(body.keys())
        assert actual_keys >= EXPECTED_DATASET_DETAIL_KEYS, (
            f"Missing snake_case keys in DatasetDetail response. "
            f"Expected (subset): {EXPECTED_DATASET_DETAIL_KEYS}, "
            f"Got: {actual_keys}. "
            "Check Pydantic model field names in src/serve/routes/datasets.py."
        )

    def test_dataset_detail_no_camel_case_keys(self) -> None:
        """AC1 [P1]: No camelCase keys must appear in the response.

        Per project-context.md: all API responses use snake_case JSON field names.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        camel_case_keys = [k for k in body if k != k.lower() and "_" not in k]
        assert camel_case_keys == [], (
            f"camelCase keys found in DatasetDetail response: {camel_case_keys}. "
            "Use snake_case field names in Pydantic models."
        )

    def test_dataset_detail_returns_404_for_unknown_id(self) -> None:
        """AC1 [P0]: GET /api/datasets/9999 must return HTTP 404.

        Per story dev notes: return 404 {"detail": "Dataset not found", "error_code": "NOT_FOUND"}
        when dataset_id does not exist in v_dq_run_active.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=UNKNOWN_DATASET_ID))
        assert response.status_code == 404, (
            f"GET /api/datasets/{UNKNOWN_DATASET_ID} returned {response.status_code}, expected 404. "
            "When dataset_id has no matching row in v_dq_run_active, return 404."
        )

    def test_dataset_detail_404_has_correct_error_body(self) -> None:
        """AC1 [P0]: 404 response must have correct error shape.

        Error format from story dev notes:
          {"detail": "Dataset not found", "error_code": "NOT_FOUND"}
        This is the FastAPI HTTPException pattern used in lobs.py.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=UNKNOWN_DATASET_ID))
        assert response.status_code == 404

        body = response.json()
        # FastAPI wraps HTTPException detail in outer "detail" key
        detail = body.get("detail", {})
        assert isinstance(detail, dict), (
            f"404 response detail must be a dict, got {type(detail)}. "
            "Use: raise HTTPException(status_code=404, detail={'detail': ..., 'error_code': ...})"
        )
        assert detail.get("detail") == "Dataset not found", (
            f"404 detail.detail={detail.get('detail')!r}, expected 'Dataset not found'"
        )
        assert detail.get("error_code") == "NOT_FOUND", (
            f"404 detail.error_code={detail.get('error_code')!r}, expected 'NOT_FOUND'"
        )


class TestDatasetDetailPydanticModels:
    """AC1 [P0]: Pydantic DatasetDetail model must be importable and well-formed."""

    def test_dataset_detail_model_is_importable(self) -> None:
        """AC1 [P0]: DatasetDetail Pydantic model must be importable.

        Fails until src/serve/routes/datasets.py defines the DatasetDetail class.
        """
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.datasets import DatasetDetail  # noqa: PLC0415

        assert issubclass(DatasetDetail, BaseModel), (
            "DatasetDetail must inherit from pydantic.BaseModel"
        )

    def test_dataset_detail_model_fields_are_snake_case(self) -> None:
        """AC1 [P1]: DatasetDetail Pydantic model fields must all be snake_case."""
        from serve.routes.datasets import DatasetDetail  # noqa: PLC0415

        for field_name in DatasetDetail.model_fields:
            assert field_name == field_name.lower(), (
                f"Field '{field_name}' in DatasetDetail is not snake_case. "
                "Per project-context.md: all API responses use snake_case JSON field names."
            )

    def test_dataset_metrics_response_model_is_importable(self) -> None:
        """AC2 [P0]: DatasetMetricsResponse, CheckResult models must be importable."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.datasets import (  # noqa: PLC0415
            CheckResult,
            DatasetMetricsResponse,
        )

        assert issubclass(DatasetMetricsResponse, BaseModel)
        assert issubclass(CheckResult, BaseModel)

    def test_dataset_trend_response_model_is_importable(self) -> None:
        """AC3 [P0]: DatasetTrendResponse, TrendPoint models must be importable."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.datasets import DatasetTrendResponse, TrendPoint  # noqa: PLC0415

        assert issubclass(DatasetTrendResponse, BaseModel)
        assert issubclass(TrendPoint, BaseModel)

    def test_numeric_metric_model_is_importable(self) -> None:
        """AC2 [P0]: NumericMetric Pydantic model must be importable."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.datasets import NumericMetric  # noqa: PLC0415

        assert issubclass(NumericMetric, BaseModel)

    def test_detail_metric_model_is_importable(self) -> None:
        """AC2 [P0]: DetailMetric Pydantic model must be importable."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.datasets import DetailMetric  # noqa: PLC0415

        assert issubclass(DetailMetric, BaseModel)


class TestDatasetHelperFunctions:
    """Unit tests for pure helper functions in datasets.py.

    These test the extraction/composition logic without any DB or HTTP call.
    Per test-levels-framework: pure functions belong at unit level.
    """

    def test_extract_source_system_from_standard_path(self) -> None:
        """AC1 [P1]: _extract_source_system parses src_sys_nm=<value> segment.

        Example: 'lob=retail/src_sys_nm=alpha/dataset=sales_daily' → 'alpha'
        """
        from serve.routes.datasets import _extract_source_system  # noqa: PLC0415

        result = _extract_source_system("lob=retail/src_sys_nm=alpha/dataset=sales_daily")
        assert result == "alpha", (
            f"Expected 'alpha', got {result!r}. "
            "Parse 'src_sys_nm=<value>' segment from path."
        )

    def test_extract_source_system_from_legacy_path(self) -> None:
        """AC1 [P1]: _extract_source_system handles legacy paths without lob= prefix.

        Example: 'src_sys_nm=omni/dataset=customer_profile' → 'omni'
        """
        from serve.routes.datasets import _extract_source_system  # noqa: PLC0415

        result = _extract_source_system("src_sys_nm=omni/dataset=customer_profile")
        assert result == "omni", (
            f"Expected 'omni', got {result!r}. "
            "Legacy paths have no lob= prefix — still parse src_sys_nm= segment."
        )

    def test_extract_source_system_returns_unknown_when_segment_absent(self) -> None:
        """AC1 [P1]: _extract_source_system returns 'unknown' when segment not found."""
        from serve.routes.datasets import _extract_source_system  # noqa: PLC0415

        result = _extract_source_system("dataset=mystery")
        assert result == "unknown", (
            f"Expected 'unknown', got {result!r}. "
            "When src_sys_nm= segment is absent, return 'unknown' as fallback."
        )

    def test_compose_hdfs_path(self) -> None:
        """AC1 [P1]: _compose_hdfs_path builds /prod/datalake/{dataset_name}/partition_date={YYYYMMDD}.

        Example:
          dataset_name='lob=retail/src_sys_nm=alpha/dataset=sales_daily'
          partition_date=2026-04-02
          → '/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402'
        """
        from serve.routes.datasets import _compose_hdfs_path  # noqa: PLC0415

        result = _compose_hdfs_path(
            "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
            datetime.date(2026, 4, 2),
        )
        expected = "/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402"
        assert result == expected, (
            f"Expected {expected!r}, got {result!r}. "
            "Compose path as /prod/datalake/{dataset_name}/partition_date={YYYYMMDD}."
        )

    def test_parse_format_from_jsonb_string(self) -> None:
        """AC1 [P2]: _parse_format parses JSONB string '\"parquet\"' → 'Parquet'.

        The eventAttribute_format detail_value stores a JSON string like '"parquet"'.
        After json.loads → 'parquet', capitalize → 'Parquet'.
        """
        from serve.routes.datasets import _parse_format  # noqa: PLC0415

        result = _parse_format('"parquet"')
        assert result == "Parquet", (
            f"Expected 'Parquet', got {result!r}. "
            "json.loads('\"parquet\"') → 'parquet', then capitalize."
        )

    def test_parse_format_avro_jsonb_string(self) -> None:
        """AC1 [P2]: _parse_format parses '\"avro\"' → 'Avro'."""
        from serve.routes.datasets import _parse_format  # noqa: PLC0415

        result = _parse_format('"avro"')
        assert result == "Avro", f"Expected 'Avro', got {result!r}."

    def test_parse_format_none_returns_unknown(self) -> None:
        """AC1 [P2]: _parse_format returns 'Unknown' when detail_value is None."""
        from serve.routes.datasets import _parse_format  # noqa: PLC0415

        result = _parse_format(None)
        assert result == "Unknown", (
            f"Expected 'Unknown', got {result!r}. "
            "When no eventAttribute_format row exists, return 'Unknown'."
        )


# ---------------------------------------------------------------------------
# Unit tests — GET /api/datasets/{dataset_id}/metrics
# ---------------------------------------------------------------------------


class TestDatasetMetricsEndpointRouteWiring:
    """AC2 [P0]: GET /api/datasets/{dataset_id}/metrics must be registered and return 200.

    TDD RED PHASE: endpoint does not exist — all tests skip.
    """

    def test_dataset_metrics_endpoint_returns_200(self) -> None:
        """AC2 [P0]: GET /api/datasets/{dataset_id}/metrics must return HTTP 200."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200, (
            f"GET /api/datasets/{KNOWN_DATASET_ID}/metrics returned {response.status_code}, "
            "expected 200. Route not registered in datasets.py."
        )

    def test_dataset_metrics_response_has_required_top_level_keys(self) -> None:
        """AC2 [P1]: Response must have dataset_id and check_results at top level."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        actual_keys = set(body.keys())
        assert actual_keys >= EXPECTED_METRICS_TOP_KEYS, (
            f"Missing keys in DatasetMetricsResponse. "
            f"Expected (subset): {EXPECTED_METRICS_TOP_KEYS}, Got: {actual_keys}"
        )

    def test_dataset_metrics_check_results_is_list(self) -> None:
        """AC2 [P1]: check_results must be a list."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        assert isinstance(body.get("check_results"), list), (
            "check_results must be a list of CheckResult objects."
        )

    def test_dataset_metrics_dataset_id_echoed_in_response(self) -> None:
        """AC2 [P1]: dataset_id in response must match path parameter."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        assert body.get("dataset_id") == KNOWN_DATASET_ID, (
            f"dataset_id in response={body.get('dataset_id')!r}, "
            f"expected {KNOWN_DATASET_ID}."
        )

    def test_dataset_metrics_returns_404_for_unknown_id(self) -> None:
        """AC2 [P0]: GET /api/datasets/9999/metrics must return HTTP 404."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=UNKNOWN_DATASET_ID))
        assert response.status_code == 404, (
            f"GET /api/datasets/{UNKNOWN_DATASET_ID}/metrics returned "
            f"{response.status_code}, expected 404."
        )

    def test_dataset_metrics_404_has_correct_error_body(self) -> None:
        """AC2 [P0]: 404 on metrics endpoint must have correct error shape."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=UNKNOWN_DATASET_ID))
        assert response.status_code == 404

        body = response.json()
        detail = body.get("detail", {})
        assert isinstance(detail, dict), (
            f"404 response detail must be a dict, got {type(detail)}"
        )
        assert detail.get("detail") == "Dataset not found", (
            f"404 detail.detail={detail.get('detail')!r}, expected 'Dataset not found'"
        )
        assert detail.get("error_code") == "NOT_FOUND", (
            f"404 detail.error_code={detail.get('error_code')!r}, expected 'NOT_FOUND'"
        )

    def test_dataset_metrics_no_camel_case_keys(self) -> None:
        """AC2 [P1]: All keys in metrics response must be snake_case."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        # Check top-level keys
        top_level_camel = [k for k in body if k != k.lower() and "_" not in k]
        assert top_level_camel == [], (
            f"camelCase keys found at top level: {top_level_camel}"
        )
        # Check check_results items
        for item in body.get("check_results", []):
            item_camel = [k for k in item if k != k.lower() and "_" not in k]
            assert item_camel == [], (
                f"camelCase keys found in check_results item: {item_camel}"
            )


# ---------------------------------------------------------------------------
# Unit tests — GET /api/datasets/{dataset_id}/trend
# ---------------------------------------------------------------------------


class TestDatasetTrendEndpointRouteWiring:
    """AC3 [P0]: GET /api/datasets/{dataset_id}/trend must be registered and return 200.

    TDD RED PHASE: endpoint does not exist — all tests skip.
    """

    def test_dataset_trend_endpoint_returns_200_default_time_range(self) -> None:
        """AC3 [P0]: GET /api/datasets/{dataset_id}/trend must return HTTP 200 (default 7d)."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200, (
            f"GET /api/datasets/{KNOWN_DATASET_ID}/trend returned {response.status_code}, "
            "expected 200. Route not registered in datasets.py."
        )

    def test_dataset_trend_accepts_7d_time_range(self) -> None:
        """AC3 [P1]: time_range=7d must be accepted (returns 200)."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID),
            params={"time_range": "7d"},
        )
        assert response.status_code == 200, (
            f"time_range=7d returned {response.status_code}, expected 200."
        )

    def test_dataset_trend_accepts_30d_time_range(self) -> None:
        """AC3 [P1]: time_range=30d must be accepted (returns 200)."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID),
            params={"time_range": "30d"},
        )
        assert response.status_code == 200, (
            f"time_range=30d returned {response.status_code}, expected 200."
        )

    def test_dataset_trend_accepts_90d_time_range(self) -> None:
        """AC3 [P1]: time_range=90d must be accepted (returns 200)."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID),
            params={"time_range": "90d"},
        )
        assert response.status_code == 200, (
            f"time_range=90d returned {response.status_code}, expected 200."
        )

    def test_dataset_trend_response_has_required_keys(self) -> None:
        """AC3 [P1]: Response must have dataset_id, time_range, and trend keys."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        actual_keys = set(body.keys())
        assert actual_keys >= EXPECTED_TREND_TOP_KEYS, (
            f"Missing keys in DatasetTrendResponse. "
            f"Expected (subset): {EXPECTED_TREND_TOP_KEYS}, Got: {actual_keys}"
        )

    def test_dataset_trend_time_range_echoed_in_response(self) -> None:
        """AC3 [P2]: time_range in response must match the query parameter sent."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID),
            params={"time_range": "30d"},
        )
        assert response.status_code == 200

        body = response.json()
        assert body.get("time_range") == "30d", (
            f"time_range in response={body.get('time_range')!r}, expected '30d'. "
            "Echo the time_range param back in the response for client-side cache-keying."
        )

    def test_dataset_trend_trend_field_is_list(self) -> None:
        """AC3 [P1]: trend field must be a list."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        assert isinstance(body.get("trend"), list), (
            f"trend must be a list, got {type(body.get('trend'))}"
        )

    def test_dataset_trend_returns_404_for_unknown_id(self) -> None:
        """AC3 [P0]: GET /api/datasets/9999/trend must return HTTP 404."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_TREND_ENDPOINT.format(dataset_id=UNKNOWN_DATASET_ID))
        assert response.status_code == 404, (
            f"GET /api/datasets/{UNKNOWN_DATASET_ID}/trend returned "
            f"{response.status_code}, expected 404."
        )

    def test_dataset_trend_404_has_correct_error_body(self) -> None:
        """AC3 [P0]: 404 on trend endpoint must have correct error shape."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_TREND_ENDPOINT.format(dataset_id=UNKNOWN_DATASET_ID))
        assert response.status_code == 404

        body = response.json()
        detail = body.get("detail", {})
        assert isinstance(detail, dict), (
            f"404 response detail must be a dict, got {type(detail)}"
        )
        assert detail.get("detail") == "Dataset not found", (
            f"404 detail.detail={detail.get('detail')!r}, expected 'Dataset not found'"
        )
        assert detail.get("error_code") == "NOT_FOUND", (
            f"404 detail.error_code={detail.get('error_code')!r}, expected 'NOT_FOUND'"
        )

    def test_dataset_trend_default_time_range_is_7d(self) -> None:
        """AC3 [P2]: Default time_range (no param) must be '7d' echoed in response."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        # No time_range param — should default to 7d
        response = client.get(DATASET_TREND_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        assert body.get("time_range") == "7d", (
            f"Default time_range={body.get('time_range')!r}, expected '7d'. "
            "Endpoint signature: def get_dataset_trend(..., time_range: str = '7d', ...)"
        )


# ---------------------------------------------------------------------------
# Integration tests — GET /api/datasets/{dataset_id}
# Data correctness against seeded fixtures from fixtures.sql
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDatasetDetailEndpointDataCorrectness:
    """AC1 [P0]: Data correctness for GET /api/datasets/{dataset_id} against fixtures.

    Fixture data (from fixtures.sql):
      sales_daily latest run (2026-04-02):
        dataset_name: 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
        lookup_code:  'LOB_RETAIL'
        check_status: 'PASS'
        dqs_score:    98.50
        rerun_number: 0
        partition_date: 2026-04-02
        orchestration_run_id: references retail/alpha orchestration run
        VOLUME row_count: 103876 (2026-04-02), previous: 96103 (2026-04-01)
        FRESHNESS hours_since_update: 2
        SCHEMA detail eventAttribute_format: '"parquet"' → 'Parquet'
    """

    def test_dataset_detail_returns_correct_dataset_name(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: dataset_name must match fixture value.

        Find the sales_daily latest run id from seeded DB to use as dataset_id.
        Uses sales_daily 2026-04-02 PASS run.
        """
        # First: find the run ID for sales_daily 2026-04-02
        # Use LOBs endpoint (4.2) to find dataset_id for sales_daily, OR
        # directly query: GET /api/lobs/LOB_RETAIL/datasets to find the run_id.
        # For this test, we query via the lobs endpoint to get the dataset_id.
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")),
            None,
        )
        assert sales_dataset is not None, (
            "sales_daily dataset not found in LOB_RETAIL datasets. "
            "Ensure fixtures.sql is seeded correctly."
        )
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["dataset_name"] == "lob=retail/src_sys_nm=alpha/dataset=sales_daily", (
            f"dataset_name={body['dataset_name']!r}, "
            "expected 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'."
        )

    def test_dataset_detail_returns_correct_lob_id(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: lob_id must be 'LOB_RETAIL' (from dq_run.lookup_code)."""
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["lob_id"] == "LOB_RETAIL", (
            f"lob_id={body['lob_id']!r}, expected 'LOB_RETAIL'. "
            "lob_id comes from dq_run.lookup_code."
        )

    def test_dataset_detail_returns_correct_source_system(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: source_system must be 'alpha' (parsed from dataset_name)."""
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["source_system"] == "alpha", (
            f"source_system={body['source_system']!r}, expected 'alpha'. "
            "Extract 'src_sys_nm=<value>' segment from dataset_name path."
        )

    def test_dataset_detail_returns_correct_format_from_jsonb(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P2]: format must be 'Parquet' for sales_daily (from eventAttribute_format JSONB).

        fixtures.sql does NOT have a format detail row for sales_daily (only for transactions=Avro).
        sales_daily has no eventAttribute_format row → format should be 'Unknown'.
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        # sales_daily has no eventAttribute_format row → fallback to 'Unknown'
        assert body["format"] == "Unknown", (
            f"format={body['format']!r}, expected 'Unknown' for sales_daily "
            "(no eventAttribute_format detail row in fixtures.sql for sales_daily). "
            "Fallback to 'Unknown' when no SCHEMA/eventAttribute_format detail row exists."
        )

    def test_dataset_detail_returns_correct_format_for_transactions(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P2]: format must be 'Avro' for transactions dataset.

        fixtures.sql has: eventAttribute_format = '"avro"' for transactions.
        After _parse_format: json.loads('"avro"') → 'avro', capitalize → 'Avro'.
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_COMMERCIAL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        tx_dataset = next(
            (d for d in datasets if "transactions" in d.get("dataset_name", "")), None
        )
        assert tx_dataset is not None, (
            "transactions dataset not found in LOB_COMMERCIAL. Check fixtures."
        )
        dataset_id = tx_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["format"] == "Avro", (
            f"format={body['format']!r}, expected 'Avro' for transactions dataset. "
            "fixtures.sql has eventAttribute_format='\"avro\"' for transactions."
        )

    def test_dataset_detail_returns_correct_hdfs_path(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: hdfs_path must be composed correctly from dataset_name + partition_date.

        Expected: '/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402'
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        expected_hdfs = (
            "/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily"
            "/partition_date=20260402"
        )
        assert body["hdfs_path"] == expected_hdfs, (
            f"hdfs_path={body['hdfs_path']!r}, expected {expected_hdfs!r}. "
            "Compose as /prod/datalake/{dataset_name}/partition_date={YYYYMMDD}."
        )

    def test_dataset_detail_returns_correct_row_count(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: row_count must be 103876 for sales_daily 2026-04-02.

        From fixtures.sql: VOLUME/row_count for sales_daily 2026-04-02 = 103876.
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["row_count"] == pytest.approx(103876, abs=1), (
            f"row_count={body['row_count']!r}, expected 103876. "
            "From v_dq_metric_numeric_active WHERE check_type='VOLUME' "
            "AND metric_name='row_count' AND dq_run_id=dataset_id."
        )

    def test_dataset_detail_returns_correct_previous_row_count(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: previous_row_count must be 96103 (2026-04-01 run for sales_daily).

        From fixtures.sql:
          partition_date=2026-04-01 → row_count=96103
          partition_date=2026-04-02 → row_count=103876 (latest, not previous)
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["previous_row_count"] == pytest.approx(96103, abs=1), (
            f"previous_row_count={body['previous_row_count']!r}, expected 96103. "
            "Query v_dq_metric_numeric_active for VOLUME/row_count ordered by partition_date DESC, "
            "LIMIT 1 OFFSET 1 (skip latest, take second-latest)."
        )

    def test_dataset_detail_returns_correct_check_status_and_dqs_score(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: check_status must be 'PASS' and dqs_score must be 98.50 for sales_daily."""
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["check_status"] == "PASS", (
            f"check_status={body['check_status']!r}, expected 'PASS'."
        )
        assert body["dqs_score"] == pytest.approx(98.50, abs=0.01), (
            f"dqs_score={body['dqs_score']!r}, expected 98.50."
        )

    def test_dataset_detail_returns_404_for_unknown_id_integration(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: GET /api/datasets/9999 must return 404 against real DB.

        id=9999 is not in fixtures.sql — the query to v_dq_run_active returns empty.
        """
        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=9999))
        assert response.status_code == 404, (
            f"GET /api/datasets/9999 returned {response.status_code}, expected 404. "
            "v_dq_run_active has no row with id=9999."
        )

    def test_dataset_detail_parent_path_from_orchestration_run(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: parent_path must come from v_dq_orchestration_run_active.

        sales_daily is linked via orchestration_run_id to the retail/alpha run.
        That run has parent_path='lob=retail/src_sys_nm=alpha'.
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["parent_path"] == "lob=retail/src_sys_nm=alpha", (
            f"parent_path={body['parent_path']!r}, "
            "expected 'lob=retail/src_sys_nm=alpha'. "
            "Query v_dq_orchestration_run_active via dq_run.orchestration_run_id."
        )

    def test_dataset_detail_legacy_path_source_system_extraction(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: source_system extracted correctly for legacy omni path.

        'src_sys_nm=omni/dataset=customer_profile' → source_system='omni'
        No orchestration_run_id for legacy row → parent_path=None.
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_LEGACY/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        legacy_dataset = next(
            (d for d in datasets if "customer_profile" in d.get("dataset_name", "")), None
        )
        assert legacy_dataset is not None, (
            "customer_profile not found in LOB_LEGACY datasets. Check fixtures."
        )
        dataset_id = legacy_dataset["dataset_id"]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert body["source_system"] == "omni", (
            f"source_system={body['source_system']!r}, expected 'omni'. "
            "Legacy path: 'src_sys_nm=omni/dataset=customer_profile'."
        )
        # Legacy row has no orchestration_run_id → parent_path must be null
        assert body.get("parent_path") is None, (
            f"parent_path={body.get('parent_path')!r}, expected None for legacy row. "
            "When orchestration_run_id is NULL, return None for parent_path."
        )


# ---------------------------------------------------------------------------
# Integration tests — GET /api/datasets/{dataset_id}/metrics
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDatasetMetricsEndpointDataCorrectness:
    """AC2 [P0]: Data correctness for GET /api/datasets/{dataset_id}/metrics.

    Fixture data:
      sales_daily 2026-04-02:
        VOLUME:   row_count=103876 (numeric)
        FRESHNESS: hours_since_update=2 (numeric)
        SCHEMA:   eventAttribute_field_name, eventAttribute_field_count,
                  eventAttribute_nullable, eventAttribute_field_list,
                  eventAttribute_field_meta, eventAttribute_schema (detail)
    """

    def _get_sales_daily_id(self, seeded_client: TestClient) -> int:
        """Helper: find the dataset_id for sales_daily 2026-04-02."""
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        return sales_dataset["dataset_id"]

    def test_metrics_returns_check_results_list(self, seeded_client: TestClient) -> None:
        """AC2 [P0]: check_results must be a list for sales_daily."""
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        assert isinstance(body["check_results"], list), (
            f"check_results must be a list, got {type(body['check_results'])}"
        )
        assert len(body["check_results"]) >= 1, (
            "check_results must be non-empty — sales_daily has VOLUME, FRESHNESS, SCHEMA metrics."
        )

    def test_metrics_volume_check_type_present_with_numeric_metrics(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P0]: VOLUME check_type must appear in check_results with row_count numeric metric.

        From fixtures: v_dq_metric_numeric_active has VOLUME/row_count=103876 for sales_daily.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        volume_group = next(
            (g for g in body["check_results"] if g["check_type"] == "VOLUME"), None
        )
        assert volume_group is not None, (
            "VOLUME check_type not found in check_results. "
            "Query v_dq_metric_numeric_active WHERE dq_run_id=dataset_id AND check_type='VOLUME'."
        )
        numeric_metrics = volume_group.get("numeric_metrics", [])
        row_count_metric = next(
            (m for m in numeric_metrics if m["metric_name"] == "row_count"), None
        )
        assert row_count_metric is not None, (
            "row_count metric not found in VOLUME check_results. "
            "fixtures.sql has VOLUME/row_count=103876 for sales_daily."
        )
        assert row_count_metric["metric_value"] == pytest.approx(103876, abs=1), (
            f"row_count metric_value={row_count_metric['metric_value']!r}, expected 103876."
        )

    def test_metrics_freshness_check_type_present(self, seeded_client: TestClient) -> None:
        """AC2 [P0]: FRESHNESS check_type must appear in check_results.

        From fixtures: v_dq_metric_numeric_active has FRESHNESS/hours_since_update=2.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        freshness_group = next(
            (g for g in body["check_results"] if g["check_type"] == "FRESHNESS"), None
        )
        assert freshness_group is not None, (
            "FRESHNESS check_type not found in check_results. "
            "fixtures.sql has FRESHNESS/hours_since_update=2 for sales_daily 2026-04-02."
        )
        numeric_metrics = freshness_group.get("numeric_metrics", [])
        freshness_metric = next(
            (m for m in numeric_metrics if m["metric_name"] == "hours_since_update"), None
        )
        assert freshness_metric is not None, (
            "hours_since_update not found in FRESHNESS numeric_metrics."
        )
        assert freshness_metric["metric_value"] == pytest.approx(2, abs=0.01), (
            f"hours_since_update={freshness_metric['metric_value']!r}, expected 2."
        )

    def test_metrics_schema_check_type_present_with_detail_metrics(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P1]: SCHEMA check_type must appear with detail_metrics.

        From fixtures: sales_daily has 6 SCHEMA detail rows (eventAttribute_* fields).
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        schema_group = next(
            (g for g in body["check_results"] if g["check_type"] == "SCHEMA"), None
        )
        assert schema_group is not None, (
            "SCHEMA check_type not found in check_results. "
            "fixtures.sql has 6 SCHEMA detail rows for sales_daily."
        )
        detail_metrics = schema_group.get("detail_metrics", [])
        assert len(detail_metrics) >= 1, (
            "SCHEMA detail_metrics is empty, expected at least 1 row. "
            "Query v_dq_metric_detail_active WHERE dq_run_id=dataset_id AND check_type='SCHEMA'."
        )

    def test_metrics_check_result_items_have_correct_keys(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P1]: Each CheckResult must have check_type, status, numeric_metrics, detail_metrics."""
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        for item in body["check_results"]:
            actual_keys = set(item.keys())
            assert actual_keys >= EXPECTED_CHECK_RESULT_KEYS, (
                f"CheckResult missing keys. Expected: {EXPECTED_CHECK_RESULT_KEYS}, Got: {actual_keys}. "
                "Define CheckResult with check_type, status, numeric_metrics, detail_metrics."
            )

    def test_metrics_status_is_overall_run_status_not_per_check_inferred(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P1]: status in each CheckResult must be the overall run check_status.

        CRITICAL: Do NOT infer per-check status from metric values.
        Per story dev notes and project-context.md anti-patterns:
        'Never add check-type-specific logic to serve/API/dashboard'

        For sales_daily PASS: all check_type groups must have status='PASS'.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        for item in body["check_results"]:
            assert item["status"] == "PASS", (
                f"check_type={item['check_type']!r} has status={item['status']!r}, "
                "expected 'PASS' (overall run status for sales_daily). "
                "ANTI-PATTERN: Do not infer status from metric values — use dq_run.check_status."
            )

    def test_metrics_metrics_grouped_by_check_type(self, seeded_client: TestClient) -> None:
        """AC2 [P0]: Metrics must be grouped by check_type, not flat.

        Each element of check_results must have exactly one check_type value,
        and the same check_type must not appear more than once in the list.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        check_types = [item["check_type"] for item in body["check_results"]]
        unique_check_types = set(check_types)
        assert len(check_types) == len(unique_check_types), (
            f"Duplicate check_types in check_results: {check_types}. "
            "Each check_type must appear exactly once — group metrics by check_type."
        )

    def test_metrics_returns_404_for_unknown_id_integration(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P0]: GET /api/datasets/9999/metrics must return 404 against real DB."""
        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=9999))
        assert response.status_code == 404, (
            f"GET /api/datasets/9999/metrics returned {response.status_code}, expected 404."
        )

    def test_metrics_payments_dataset_has_volume_metrics(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P0]: payments dataset metrics must include VOLUME/row_count=0.

        From fixtures: payments FAIL run has VOLUME/row_count=0.
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_COMMERCIAL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        payments_dataset = next(
            (d for d in datasets if "payments" in d.get("dataset_name", "")), None
        )
        assert payments_dataset is not None
        dataset_id = payments_dataset["dataset_id"]

        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        volume_group = next(
            (g for g in body["check_results"] if g["check_type"] == "VOLUME"), None
        )
        assert volume_group is not None, "VOLUME check_type not found for payments dataset."
        row_count_metric = next(
            (m for m in volume_group["numeric_metrics"] if m["metric_name"] == "row_count"),
            None,
        )
        assert row_count_metric is not None, "row_count not found in VOLUME metrics for payments."
        assert row_count_metric["metric_value"] == pytest.approx(0, abs=0.01), (
            f"row_count={row_count_metric['metric_value']!r}, expected 0 for payments FAIL run."
        )

    def test_metrics_transactions_has_schema_detail_with_format(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P1]: transactions dataset must have SCHEMA detail metric with eventAttribute_format='\"avro\"'.

        From fixtures: transactions has SCHEMA/eventAttribute_format='"avro"' detail row.
        The API returns raw JSONB value, not parsed — detail_value should contain '"avro"' (raw).
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_COMMERCIAL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        tx_dataset = next(
            (d for d in datasets if "transactions" in d.get("dataset_name", "")), None
        )
        assert tx_dataset is not None
        dataset_id = tx_dataset["dataset_id"]

        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        schema_group = next(
            (g for g in body["check_results"] if g["check_type"] == "SCHEMA"), None
        )
        assert schema_group is not None, "SCHEMA check_type not found for transactions."
        format_detail = next(
            (m for m in schema_group["detail_metrics"]
             if m["detail_type"] == "eventAttribute_format"),
            None,
        )
        assert format_detail is not None, (
            "eventAttribute_format detail_metric not found in SCHEMA group for transactions."
        )
        # detail_value is raw JSONB — psycopg2 returns jsonb as string '"avro"'
        # FastAPI serializes it as-is (string or dict/list depending on DB driver)
        assert format_detail["detail_value"] is not None, (
            "detail_value must not be None for eventAttribute_format."
        )


# ---------------------------------------------------------------------------
# Integration tests — GET /api/datasets/{dataset_id}/trend
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDatasetTrendEndpointDataCorrectness:
    """AC3 [P0]: Data correctness for GET /api/datasets/{dataset_id}/trend.

    Fixture data:
      sales_daily has 7 runs: 2026-03-27 through 2026-04-02 (inclusive), all dqs_score=98.50.
      This gives exactly 7 trend points for time_range=7d.
    """

    def _get_sales_daily_id(self, seeded_client: TestClient) -> int:
        """Helper: find the dataset_id for sales_daily latest run."""
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        return sales_dataset["dataset_id"]

    def test_trend_returns_7d_data_for_sales_daily(self, seeded_client: TestClient) -> None:
        """AC3 [P0]: sales_daily with time_range=7d must return 7 trend points.

        fixtures.sql has exactly 7 consecutive dates (2026-03-27 to 2026-04-02).
        The MAX(partition_date) anchor = 2026-04-02, window = 2026-04-02 - 6 days = 2026-03-27.
        All 7 dates fall within the window → 7 trend points.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id),
            params={"time_range": "7d"},
        )
        assert response.status_code == 200

        body = response.json()
        trend = body["trend"]
        assert isinstance(trend, list), f"trend must be a list, got {type(trend)}"
        assert len(trend) == 7, (
            f"trend has {len(trend)} points, expected 7. "
            "fixtures.sql has 7 daily runs for sales_daily (2026-03-27 to 2026-04-02). "
            "7d window: MAX(partition_date) - 6 days = 2026-03-27."
        )

    def test_trend_points_are_ordered_oldest_to_newest(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P1]: Trend points must be ordered oldest-to-newest (ASC by date).

        For sparkline rendering: chart expects chronological order.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id),
            params={"time_range": "7d"},
        )
        assert response.status_code == 200

        body = response.json()
        trend = body["trend"]
        assert len(trend) >= 2, "Need at least 2 points to verify ordering."

        dates = [point["date"] for point in trend]
        assert dates == sorted(dates), (
            f"Trend points not ordered oldest-to-newest. Dates: {dates}. "
            "SQL must ORDER BY date ASC."
        )

    def test_trend_points_have_correct_keys(self, seeded_client: TestClient) -> None:
        """AC3 [P1]: Each TrendPoint must have 'date' and 'dqs_score' keys."""
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        for point in body["trend"]:
            actual_keys = set(point.keys())
            assert actual_keys >= EXPECTED_TREND_POINT_KEYS, (
                f"TrendPoint missing keys. Expected: {EXPECTED_TREND_POINT_KEYS}, Got: {actual_keys}"
            )

    def test_trend_dqs_scores_are_numeric(self, seeded_client: TestClient) -> None:
        """AC3 [P1]: dqs_score in each trend point must be numeric (float or int)."""
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        for point in body["trend"]:
            score = point.get("dqs_score")
            if score is not None:
                assert isinstance(score, (int, float)), (
                    f"dqs_score must be numeric or null, got {type(score)}: {score!r}"
                )

    def test_trend_sales_daily_scores_are_98_50(self, seeded_client: TestClient) -> None:
        """AC3 [P1]: All trend dqs_score values for sales_daily must be ~98.50.

        fixtures.sql sets dqs_score=98.50 for all 7 sales_daily dates.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id),
            params={"time_range": "7d"},
        )
        assert response.status_code == 200

        body = response.json()
        for point in body["trend"]:
            assert point["dqs_score"] == pytest.approx(98.50, abs=0.01), (
                f"dqs_score={point['dqs_score']!r} for date={point['date']!r}, "
                "expected ~98.50. All sales_daily runs have dqs_score=98.50 in fixtures.sql."
            )

    def test_trend_time_range_30d_returns_fewer_points_than_7_fixture_rows(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P1]: time_range=30d still returns only the available fixture data points.

        fixtures.sql has 7 runs for sales_daily (2026-03-27 to 2026-04-02).
        30d window: MAX(partition_date) - 29 days = 2026-03-04.
        All 7 dates are within the 30d window → still 7 trend points.
        """
        dataset_id = self._get_sales_daily_id(seeded_client)
        response = seeded_client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id),
            params={"time_range": "30d"},
        )
        assert response.status_code == 200

        body = response.json()
        trend = body["trend"]
        assert len(trend) == 7, (
            f"time_range=30d returned {len(trend)} points, expected 7. "
            "All 7 fixture dates (2026-03-27 to 2026-04-02) fall within 30d window."
        )

    def test_trend_returns_404_for_unknown_id_integration(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P0]: GET /api/datasets/9999/trend must return 404 against real DB."""
        response = seeded_client.get(DATASET_TREND_ENDPOINT.format(dataset_id=9999))
        assert response.status_code == 404, (
            f"GET /api/datasets/9999/trend returned {response.status_code}, expected 404."
        )

    def test_trend_single_point_dataset_returns_one_element_list(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P2]: Single-run datasets must return a 1-element trend list.

        payments, products, customers, transactions, customer_profile each have 1 run.
        7d trend for any of these should return exactly 1 point.
        """
        lobs_response = seeded_client.get("/api/lobs/LOB_COMMERCIAL/datasets")
        assert lobs_response.status_code == 200
        datasets = lobs_response.json()
        payments_dataset = next(
            (d for d in datasets if "payments" in d.get("dataset_name", "")), None
        )
        assert payments_dataset is not None
        dataset_id = payments_dataset["dataset_id"]

        response = seeded_client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id),
            params={"time_range": "7d"},
        )
        assert response.status_code == 200

        body = response.json()
        trend = body["trend"]
        assert len(trend) == 1, (
            f"payments dataset (1 run) returned {len(trend)} trend points, expected 1. "
            "Single-point datasets should return 1-element list, not empty list."
        )

    def test_trend_time_range_echoed_in_response_integration(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P2]: time_range in response must match parameter (integration)."""
        dataset_id = self._get_sales_daily_id(seeded_client)
        for time_range in ("7d", "30d", "90d"):
            response = seeded_client.get(
                DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id),
                params={"time_range": time_range},
            )
            assert response.status_code == 200
            body = response.json()
            assert body["time_range"] == time_range, (
                f"time_range in response={body['time_range']!r}, expected {time_range!r}."
            )


# ---------------------------------------------------------------------------
# Integration tests — AC4: Active-record view usage
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestActiveRecordViewUsage:
    """AC4 [P0]: All endpoints must query v_*_active views, never raw tables.

    TDD RED PHASE: endpoints do not exist — all tests skip.

    These tests verify that:
    1. Expired records are invisible to all three endpoints
    2. New active records are immediately visible

    If any endpoint queries raw tables (with or without expiry filter),
    these tests will reveal the bug by showing expired data or missing data.
    """

    def test_dataset_detail_excludes_expired_records(
        self,
        db_conn: "psycopg2.extensions.connection",
        seeded_client: TestClient,
    ) -> None:
        """AC4 [P0]: GET /api/datasets/{id} must not find expired runs.

        Insert an expired dq_run record with id=8888 and verify that
        GET /api/datasets/8888 returns 404 (expired = invisible).

        If the endpoint queries raw dq_run instead of v_dq_run_active,
        it will return 200 instead of 404 — revealing the anti-pattern.
        """
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score,
                     rerun_number, expiry_date)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=expired_detail',
                     '2026-04-02', 'LOB_RETAIL', 'PASS', 90.00, 0, '2026-01-01 00:00:00')
                RETURNING id
                """
            )
            row = cur.fetchone()
        db_conn.commit()
        expired_id = row[0]

        response = seeded_client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=expired_id))
        assert response.status_code == 404, (
            f"GET /api/datasets/{expired_id} returned {response.status_code} for an expired run, "
            "expected 404. The endpoint must query v_dq_run_active (not raw dq_run). "
            "ANTI-PATTERN: never query raw tables in the serve layer."
        )

    def test_dataset_metrics_excludes_expired_metric_rows(
        self,
        db_conn: "psycopg2.extensions.connection",
        seeded_client: TestClient,
    ) -> None:
        """AC4 [P1]: GET /api/datasets/{id}/metrics must not return expired metric rows.

        For a known dataset, insert an expired numeric metric row.
        Verify it does NOT appear in the metrics response.
        """
        # Get a known dataset_id (sales_daily)
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets_data = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets_data if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id = sales_dataset["dataset_id"]

        # Insert expired metric row (metric_name="expired_metric")
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_metric_numeric
                    (dq_run_id, check_type, metric_name, metric_value, expiry_date)
                VALUES
                    (%s, 'VOLUME', 'expired_metric', 999999, '2026-01-01 00:00:00')
                """,
                (dataset_id,),
            )
        db_conn.commit()

        response = seeded_client.get(DATASET_METRICS_ENDPOINT.format(dataset_id=dataset_id))
        assert response.status_code == 200

        body = response.json()
        volume_group = next(
            (g for g in body["check_results"] if g["check_type"] == "VOLUME"), None
        )
        if volume_group:
            metric_names = [m["metric_name"] for m in volume_group["numeric_metrics"]]
            assert "expired_metric" not in metric_names, (
                f"Expired metric 'expired_metric' appeared in metrics response: {metric_names}. "
                "Query v_dq_metric_numeric_active (not raw dq_metric_numeric). "
                "ANTI-PATTERN: never query raw tables in the serve layer."
            )

    def test_dataset_trend_excludes_expired_runs(
        self,
        db_conn: "psycopg2.extensions.connection",
        seeded_client: TestClient,
    ) -> None:
        """AC4 [P1]: GET /api/datasets/{id}/trend must not include expired run data.

        For sales_daily, insert an expired run with a distinct dqs_score.
        Verify it does NOT appear in the trend response.
        """
        dataset_id_from_lobs = None
        lobs_response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert lobs_response.status_code == 200
        datasets_data = lobs_response.json()
        sales_dataset = next(
            (d for d in datasets_data if "sales_daily" in d.get("dataset_name", "")), None
        )
        assert sales_dataset is not None
        dataset_id_from_lobs = sales_dataset["dataset_id"]

        # Insert expired run with unique dqs_score=11.11
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score,
                     rerun_number, expiry_date)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=sales_daily',
                     '2026-04-03', 'LOB_RETAIL', 'PASS', 11.11, 0, '2026-01-01 00:00:00')
                """
            )
        db_conn.commit()

        response = seeded_client.get(
            DATASET_TREND_ENDPOINT.format(dataset_id=dataset_id_from_lobs),
            params={"time_range": "7d"},
        )
        assert response.status_code == 200

        body = response.json()
        scores = [p["dqs_score"] for p in body["trend"]]
        assert 11.11 not in scores, (
            f"Expired run with dqs_score=11.11 appeared in trend: {scores}. "
            "Query v_dq_run_active (not raw dq_run). "
            "ANTI-PATTERN: never query raw tables in the serve layer."
        )

    def test_datasets_router_registered_in_main(self) -> None:
        """AC4 [P0]: datasets router must be registered in main.py with prefix='/api'.

        Verify by checking that all three endpoint paths are reachable.
        A 405 (Method Not Allowed) or 422 (Validation Error) means the route IS registered
        but has different constraints — only 404 or connection error means NOT registered.

        This test uses a non-existent dataset_id so no DB is needed.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)

        for endpoint_template in (
            DATASET_DETAIL_ENDPOINT,
            DATASET_METRICS_ENDPOINT,
            DATASET_TREND_ENDPOINT,
        ):
            url = endpoint_template.format(dataset_id=KNOWN_DATASET_ID)
            response = client.get(url)
            assert response.status_code != 404 or "Not Found" not in str(response.json()), (
                f"GET {url} returned 404 with standard 'Not Found' — route not registered. "
                "Add datasets_router to main.py with prefix='/api'."
            )


# ---------------------------------------------------------------------------
# Reference Data Resolution & Caching — route-level acceptance tests
#
# TDD RED PHASE: All tests in this section are marked @pytest.mark.skip because
# the ReferenceDataService, lob_lookup table/view, and DatasetDetail field
# extensions do not exist yet.
# Remove skip markers after completing implementation tasks.
# ---------------------------------------------------------------------------

# New keys added to DatasetDetail for reference data resolution
EXPECTED_RESOLVED_FIELDS = {"lob_name", "owner", "classification"}

# Full expected key set (base fields + resolved reference data fields)
EXPECTED_DATASET_DETAIL_KEYS_4_5 = EXPECTED_DATASET_DETAIL_KEYS | EXPECTED_RESOLVED_FIELDS


class TestDatasetDetailResolvedFields:
    """AC2 [P0]: DatasetDetail response must include lob_name, owner, classification.

    TDD RED PHASE: DatasetDetail model does not have these fields yet.
    Remove @pytest.mark.skip after implementing reference data resolution.
    """

    def test_dataset_detail_has_all_4_5_fields(self, client: TestClient) -> None:
        """AC2 [P0]: GET /api/datasets/{dataset_id} must include resolved LOB fields.

        After implementation, DatasetDetail gains three new snake_case
        string fields from ReferenceDataService.resolve(lookup_code):
          - lob_name: str      (e.g. 'Retail Banking')
          - owner: str         (e.g. 'Jane Doe')
          - classification: str (e.g. 'Tier 1 Critical')

        Fails until:
          1. DatasetDetail Pydantic model gains these three fields.
          2. Route handler injects ref_svc = Depends(get_reference_data_service).
          3. conftest.py mock_reference_data_service fixture sets app.state.reference_data.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        actual_keys = set(body.keys())
        missing_fields = EXPECTED_RESOLVED_FIELDS - actual_keys
        assert not missing_fields, (
            f"DatasetDetail response missing resolved reference data fields: {missing_fields}. "
            f"Got keys: {actual_keys}. "
            "Add lob_name, owner, classification to DatasetDetail Pydantic model "
            "and populate via ReferenceDataService.resolve()."
        )

    def test_dataset_detail_lob_name_is_string(self, client: TestClient) -> None:
        """AC2 [P0]: 'lob_name' field must be a str in the response.

        resolve() always returns a str ('N/A' as fallback) — never None.
        DatasetDetail model must declare 'lob_name: str', not 'Optional[str]'.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        assert "lob_name" in body, (
            "DatasetDetail response missing 'lob_name' field"
        )
        assert isinstance(body["lob_name"], str), (
            f"'lob_name' must be a str, got {type(body['lob_name']).__name__} "
            f"value={body['lob_name']!r}. ReferenceDataService.resolve() always returns str."
        )

    def test_dataset_detail_owner_is_string(self, client: TestClient) -> None:
        """AC2 [P0]: 'owner' field must be a str in the response."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        assert "owner" in body, "DatasetDetail response missing 'owner' field"
        assert isinstance(body["owner"], str), (
            f"'owner' must be a str, got {type(body['owner']).__name__} "
            f"value={body['owner']!r}."
        )

    def test_dataset_detail_classification_is_string(self, client: TestClient) -> None:
        """AC2 [P0]: 'classification' field must be a str in the response."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        assert "classification" in body, (
            "DatasetDetail response missing 'classification' field"
        )
        assert isinstance(body["classification"], str), (
            f"'classification' must be a str, got {type(body['classification']).__name__} "
            f"value={body['classification']!r}."
        )

    def test_dataset_detail_resolved_fields_not_null(self, client: TestClient) -> None:
        """AC4 [P0]: lob_name, owner, classification must never be null.

        AC4 states: for unknown codes, return 'N/A' — not null, not error.
        The mock ReferenceDataService (from conftest fixture) returns
        LobMapping('Retail Banking', 'Jane Doe', 'Tier 1 Critical') for unit tests.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        for field in ("lob_name", "owner", "classification"):
            val = body.get(field)
            assert val is not None, (
                f"'{field}' is null in DatasetDetail response. "
                "ReferenceDataService.resolve() must return 'N/A' for unknowns, never null. "
                "DatasetDetail model must declare these as 'str', not 'Optional[str]'."
            )

    def test_dataset_detail_mock_returns_retail_banking_for_lob_retail(
        self, client: TestClient
    ) -> None:
        """AC2 [P1]: Unit test mock returns resolved LOB name for known code.

        The conftest.py mock_reference_data_service autouse fixture configures
        app.state.reference_data to return LobMapping('Retail Banking', 'Jane Doe',
        'Tier 1 Critical') when resolve() is called. This verifies that the route
        handler actually calls resolve() and includes the result in the response.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        # These values come from the mock_reference_data_service fixture in conftest.py
        assert body.get("lob_name") == "Retail Banking", (
            f"Expected lob_name='Retail Banking' from mock ReferenceDataService, "
            f"got {body.get('lob_name')!r}. "
            "Check that route handler calls ref_svc.resolve(row['lookup_code']) "
            "and sets lob_name from the result."
        )
        assert body.get("owner") == "Jane Doe", (
            f"Expected owner='Jane Doe' from mock, got {body.get('owner')!r}"
        )
        assert body.get("classification") == "Tier 1 Critical", (
            f"Expected classification='Tier 1 Critical' from mock, "
            f"got {body.get('classification')!r}"
        )

    def test_dataset_detail_full_key_set_after_4_5(self, client: TestClient) -> None:
        """AC2 [P1]: Full DatasetDetail key set (base fields + resolved reference data fields).

        The complete set of expected snake_case keys includes both
        the base fields and the three new resolved reference data fields.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(DATASET_DETAIL_ENDPOINT.format(dataset_id=KNOWN_DATASET_ID))
        assert response.status_code == 200

        body = response.json()
        actual_keys = set(body.keys())
        missing_keys = EXPECTED_DATASET_DETAIL_KEYS_4_5 - actual_keys
        assert not missing_keys, (
            f"DatasetDetail response is missing resolved reference data fields: {missing_keys}. "
            f"Got keys: {actual_keys}."
        )


class TestDatasetDetailPydanticModelAfter45:
    """AC2 [P0]: DatasetDetail Pydantic model must include resolved field declarations.

    TDD RED PHASE: fails until DatasetDetail model is extended.
    """

    def test_dataset_detail_model_has_lob_name_field(self) -> None:
        """AC2 [P0]: DatasetDetail Pydantic model must declare 'lob_name: str'."""
        from serve.routes.datasets import DatasetDetail  # noqa: PLC0415

        assert "lob_name" in DatasetDetail.model_fields, (
            "DatasetDetail Pydantic model missing 'lob_name' field. "
            "Add 'lob_name: str' to DatasetDetail in routes/datasets.py."
        )
        field = DatasetDetail.model_fields["lob_name"]
        # Should be 'str', not Optional[str]
        annotation = field.annotation
        # Allow str or string type alias
        assert annotation is str or str(annotation) == "str", (
            f"'lob_name' must be declared as 'str' (not Optional), got {annotation!r}. "
            "resolve() always returns a string — never null."
        )

    def test_dataset_detail_model_has_owner_field(self) -> None:
        """AC2 [P0]: DatasetDetail Pydantic model must declare 'owner: str'."""
        from serve.routes.datasets import DatasetDetail  # noqa: PLC0415

        assert "owner" in DatasetDetail.model_fields, (
            "DatasetDetail Pydantic model missing 'owner' field. "
            "Add 'owner: str' to DatasetDetail in routes/datasets.py."
        )

    def test_dataset_detail_model_has_classification_field(self) -> None:
        """AC2 [P0]: DatasetDetail Pydantic model must declare 'classification: str'."""
        from serve.routes.datasets import DatasetDetail  # noqa: PLC0415

        assert "classification" in DatasetDetail.model_fields, (
            "DatasetDetail Pydantic model missing 'classification' field. "
            "Add 'classification: str' to DatasetDetail in routes/datasets.py."
        )


class TestLifespanAndServiceWiring:
    """AC1 [P0]: FastAPI lifespan must wire ReferenceDataService into app.state.

    TDD RED PHASE: fails until main.py lifespan is implemented.
    """

    def test_app_has_lifespan_that_sets_reference_data_state(self) -> None:
        """AC1 [P0]: FastAPI lifespan must set app.state.reference_data on startup.

        Expected pattern:
          @asynccontextmanager
          async def lifespan(app):
              svc = ReferenceDataService(db_factory=SessionLocal)
              svc.refresh()
              app.state.reference_data = svc
              yield

        The app must NOT use the deprecated @app.on_event('startup') pattern.
        """
        from serve.main import app  # noqa: PLC0415

        # The lifespan is tested by verifying app.state.reference_data is set
        # after a request (TestClient triggers the lifespan context manager)
        with TestClient(app):
            # After TestClient startup, app.state.reference_data must exist
            assert hasattr(app.state, "reference_data"), (
                "app.state.reference_data not set after startup. "
                "Add FastAPI lifespan context manager to main.py that creates "
                "ReferenceDataService and calls refresh()."
            )
            from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415
            assert isinstance(app.state.reference_data, ReferenceDataService), (
                f"app.state.reference_data must be a ReferenceDataService instance, "
                f"got {type(app.state.reference_data).__name__}"
            )
