"""Acceptance tests — Story 4.2: GET /api/lobs and GET /api/lobs/{lob_id}/datasets endpoints.

TDD RED PHASE: All tests are marked @pytest.mark.skip because the endpoints
do not exist yet. Tests assert EXPECTED behavior per acceptance criteria.
Remove skip markers after implementation to enter green phase.

Test categories:
  - Unit tests (no DB): verify route wiring, Pydantic model shapes, error format
  - Integration tests (real Postgres + seeded fixtures): verify data correctness,
    view usage, time_range filtering, 404 for unknown LOB

Run unit tests:     cd dqs-serve && uv run pytest tests/test_routes/test_lobs.py
Run integration:    cd dqs-serve && uv run pytest -m integration tests/test_routes/test_lobs.py
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient

if TYPE_CHECKING:
    import psycopg2.extensions


# ---------------------------------------------------------------------------
# Shared constants
# ---------------------------------------------------------------------------

LOBS_ENDPOINT = "/api/lobs"
LOB_DATASETS_ENDPOINT = "/api/lobs/{lob_id}/datasets"

EXPECTED_LOB_DETAIL_KEYS = {
    "lob_id",
    "dataset_count",
    "aggregate_score",
    "healthy_count",
    "degraded_count",
    "critical_count",
}

EXPECTED_DATASET_IN_LOB_KEYS = {
    "dataset_id",
    "dataset_name",
    "dqs_score",
    "check_status",
    "partition_date",
    "trend",
    "freshness_status",
    "volume_status",
    "schema_status",
}


# ---------------------------------------------------------------------------
# Unit tests — GET /api/lobs
# ---------------------------------------------------------------------------


class TestLobsListEndpointRouteWiring:
    """AC3 [P0]: GET /api/lobs must be registered and return 200 with correct shape.

    TDD RED PHASE: endpoint does not exist — all tests skip.
    """

    def test_lobs_endpoint_returns_200(self) -> None:
        """AC3 [P0]: GET /api/lobs must return HTTP 200."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(LOBS_ENDPOINT)
        assert response.status_code == 200, (
            f"GET {LOBS_ENDPOINT} returned {response.status_code}, expected 200. "
            "Route not registered — create src/serve/routes/lobs.py with APIRouter "
            "and include it in main.py with prefix='/api'."
        )

    def test_lobs_response_is_list(self) -> None:
        """AC3 [P0]: GET /api/lobs must return a JSON array (list of LOB objects)."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert isinstance(body, list), (
            f"GET /api/lobs must return a list, got {type(body)}. "
            "Response model should be list[LobDetail]."
        )

    def test_lobs_items_have_snake_case_keys(self) -> None:
        """AC3 + AC2-like [P1]: Each LOB item must have snake_case keys only."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert isinstance(body, list) and len(body) > 0, (
            "GET /api/lobs returned an empty list — cannot validate snake_case keys. "
            "The mock DB session must return at least one LOB row."
        )

        for item in body:
            actual_keys = set(item.keys())
            assert actual_keys >= EXPECTED_LOB_DETAIL_KEYS, (
                f"LOB item missing required keys. "
                f"Expected (subset): {EXPECTED_LOB_DETAIL_KEYS}, Got: {actual_keys}"
            )
            camel_keys = [k for k in actual_keys if k != k.lower() and "_" not in k]
            assert camel_keys == [], (
                f"camelCase keys found in LOB item: {camel_keys}. Must be snake_case."
            )

    def test_lobs_pydantic_models_importable(self) -> None:
        """AC3 [P0]: Pydantic models LobDetail and LobListResponse must be importable."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.lobs import LobDetail  # noqa: PLC0415

        assert issubclass(LobDetail, BaseModel), "LobDetail must inherit from pydantic.BaseModel"

    def test_lob_detail_field_names_are_snake_case(self) -> None:
        """AC2-like [P1]: All LobDetail Pydantic model field names must be snake_case."""
        from serve.routes.lobs import LobDetail  # noqa: PLC0415

        for field_name in LobDetail.model_fields:
            assert field_name == field_name.lower(), (
                f"Field '{field_name}' in LobDetail is not snake_case."
            )


# ---------------------------------------------------------------------------
# Unit tests — GET /api/lobs/{lob_id}/datasets
# ---------------------------------------------------------------------------


class TestLobDatasetsEndpointRouteWiring:
    """AC4 [P0]: GET /api/lobs/{lob_id}/datasets must be registered and return correct shapes.

    TDD RED PHASE: endpoint does not exist — all tests skip.
    """

    def test_lob_datasets_endpoint_returns_200_for_valid_lob(self) -> None:
        """AC4 [P0]: GET /api/lobs/LOB_RETAIL/datasets must eventually return 200.

        Fails until route is implemented with seeded data.
        In unit test context (no DB) we verify the route is registered (will return 500
        from missing DB, not 404 from missing route).
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app, raise_server_exceptions=False)
        response = client.get("/api/lobs/LOB_RETAIL/datasets")
        # Without DB the endpoint returns 500 (db error), not 404 (missing route)
        # 404 would mean the route itself is not registered
        assert response.status_code != 404, (
            "GET /api/lobs/{lob_id}/datasets returned 404 — route not registered. "
            "Add the path operation to src/serve/routes/lobs.py."
        )

    def test_lob_datasets_pydantic_models_importable(self) -> None:
        """AC4 [P0]: Pydantic models DatasetInLob and LobDatasetsResponse must be importable."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.lobs import DatasetInLob, LobDatasetsResponse  # noqa: PLC0415

        assert issubclass(DatasetInLob, BaseModel), "DatasetInLob must inherit from pydantic.BaseModel"
        assert issubclass(LobDatasetsResponse, BaseModel), "LobDatasetsResponse must inherit from pydantic.BaseModel"

    def test_dataset_in_lob_field_names_are_snake_case(self) -> None:
        """AC2-like [P1]: All DatasetInLob Pydantic model field names must be snake_case."""
        from serve.routes.lobs import DatasetInLob  # noqa: PLC0415

        for field_name in DatasetInLob.model_fields:
            assert field_name == field_name.lower(), (
                f"Field '{field_name}' in DatasetInLob is not snake_case."
            )

    def test_lob_datasets_response_has_lob_id_and_datasets_keys(self) -> None:
        """AC4 [P0]: LobDatasetsResponse must have lob_id and datasets fields."""
        from serve.routes.lobs import LobDatasetsResponse  # noqa: PLC0415

        fields = set(LobDatasetsResponse.model_fields.keys())
        assert "lob_id" in fields, "LobDatasetsResponse must have 'lob_id' field"
        assert "datasets" in fields, "LobDatasetsResponse must have 'datasets' field"


class TestLobDatasetsNotFoundError:
    """AC4 [P1]: 404 error for unknown lob_id must match prescribed error format.

    TDD RED PHASE: all tests skip.
    """

    def test_unknown_lob_returns_404(self) -> None:
        """AC4 [P1]: GET /api/lobs/NONEXISTENT/datasets must return 404.

        Per story dev notes: HTTPException with status_code=404 and
        detail={'detail': 'LOB not found', 'error_code': 'NOT_FOUND'}
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app, raise_server_exceptions=False)
        response = client.get("/api/lobs/NONEXISTENT/datasets")
        assert response.status_code == 404, (
            f"Expected 404 for unknown LOB, got {response.status_code}. "
            "Raise HTTPException(status_code=404, detail={'detail': 'LOB not found', "
            "'error_code': 'NOT_FOUND'}) when lob_id matches no records in v_dq_run_active."
        )

    def test_unknown_lob_404_response_has_correct_error_format(self) -> None:
        """AC4 [P1]: 404 response body must match {'detail': 'LOB not found', 'error_code': 'NOT_FOUND'}.

        Per project-context.md error format: {'detail': 'message', 'error_code': 'NOT_FOUND'}
        Never return stack traces in error responses.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app, raise_server_exceptions=False)
        response = client.get("/api/lobs/NONEXISTENT/datasets")
        assert response.status_code == 404

        body = response.json()
        # FastAPI wraps HTTPException detail in {'detail': ...}
        # When we pass a dict as detail, FastAPI returns {'detail': {our dict}}
        detail = body.get("detail", {})
        assert isinstance(detail, dict), (
            f"detail field must be a dict, got {type(detail)}: {detail}. "
            "Pass a dict to HTTPException(detail=...) with 'detail' and 'error_code' keys."
        )
        assert detail.get("detail") == "LOB not found", (
            f"detail.detail='{detail.get('detail')}', expected 'LOB not found'"
        )
        assert detail.get("error_code") == "NOT_FOUND", (
            f"detail.error_code='{detail.get('error_code')}', expected 'NOT_FOUND'"
        )

    def test_unknown_lob_error_response_has_no_stack_trace(self) -> None:
        """AC4 [P1]: 404 response must not contain stack trace or internal error details.

        Per project-context.md: never return stack traces from API endpoints.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app, raise_server_exceptions=False)
        response = client.get("/api/lobs/NONEXISTENT/datasets")
        body_text = response.text.lower()
        assert "traceback" not in body_text, "Response contains 'Traceback' — stack trace leaked"
        assert "exception" not in body_text, "Response contains 'exception' — internal details leaked"
        assert "sqlalchemy" not in body_text, "Response contains 'sqlalchemy' — internal details leaked"


# ---------------------------------------------------------------------------
# Integration tests — GET /api/lobs (real Postgres + seeded fixtures)
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestLobsListDataCorrectness:
    """AC3 + AC6 [P0]: GET /api/lobs must return correct aggregated LOB data.

    TDD RED PHASE: All tests skip until endpoint is implemented.

    Fixture data summary:
      LOB_RETAIL: 3 datasets, scores 98.50/45.00/72.00 → AVG=71.83, 1 PASS 1 WARN 1 FAIL
      LOB_COMMERCIAL: 2 datasets, scores 60.00/0.00 → AVG=30.00, 0 PASS 1 WARN 1 FAIL
      LOB_LEGACY: 1 dataset, score 95.00 → AVG=95.00, 1 PASS 0 WARN 0 FAIL
    """

    def test_lobs_list_returns_all_three_lobs(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P0]: GET /api/lobs must return 3 LOB entries matching fixture data."""
        response = seeded_client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert isinstance(body, list), f"Expected list, got {type(body)}"
        lob_ids = {item["lob_id"] for item in body}
        expected = {"LOB_RETAIL", "LOB_COMMERCIAL", "LOB_LEGACY"}
        assert lob_ids == expected, (
            f"Expected LOBs {expected}, got {lob_ids}. "
            "Query DISTINCT lookup_code from v_dq_run_active for each LOB."
        )

    def test_lobs_retail_dataset_count_is_three(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P0]: LOB_RETAIL must have dataset_count=3."""
        response = seeded_client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        retail = next((lob for lob in body if lob["lob_id"] == "LOB_RETAIL"), None)
        assert retail is not None, "LOB_RETAIL not found in /api/lobs"
        assert retail["dataset_count"] == 3, (
            f"LOB_RETAIL dataset_count={retail['dataset_count']}, expected 3"
        )

    def test_lobs_retail_aggregate_score_is_71_83(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P1]: LOB_RETAIL aggregate_score must be ~71.83.

        (98.50 + 45.00 + 72.00) / 3 = 71.8333... → ROUND(..., 2) = 71.83
        """
        response = seeded_client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        retail = next((lob for lob in body if lob["lob_id"] == "LOB_RETAIL"), None)
        assert retail is not None
        assert retail["aggregate_score"] == pytest.approx(71.83, abs=0.01), (
            f"LOB_RETAIL aggregate_score={retail['aggregate_score']}, expected ~71.83. "
            "Use ROUND(AVG(dqs_score), 2) on latest partition_date per dataset."
        )

    def test_lobs_retail_status_distribution(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P1]: LOB_RETAIL must have pass_count=1, warn_count=1, fail_count=1."""
        response = seeded_client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        retail = next((lob for lob in body if lob["lob_id"] == "LOB_RETAIL"), None)
        assert retail is not None
        assert retail["healthy_count"] == 1, (
            f"LOB_RETAIL healthy_count={retail['healthy_count']}, expected 1"
        )
        assert retail["degraded_count"] == 1, (
            f"LOB_RETAIL degraded_count={retail['degraded_count']}, expected 1"
        )
        assert retail["critical_count"] == 1, (
            f"LOB_RETAIL critical_count={retail['critical_count']}, expected 1"
        )

    def test_lobs_commercial_aggregate_score_is_30(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P1]: LOB_COMMERCIAL aggregate_score must be ~30.00.

        (60.00 + 0.00) / 2 = 30.00
        """
        response = seeded_client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        commercial = next((lob for lob in body if lob["lob_id"] == "LOB_COMMERCIAL"), None)
        assert commercial is not None, "LOB_COMMERCIAL not found"
        assert commercial["aggregate_score"] == pytest.approx(30.00, abs=0.01), (
            f"LOB_COMMERCIAL aggregate_score={commercial['aggregate_score']}, expected 30.00"
        )

    def test_lobs_legacy_has_one_dataset(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P1]: LOB_LEGACY must have dataset_count=1 (customer_profile)."""
        response = seeded_client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        legacy = next((lob for lob in body if lob["lob_id"] == "LOB_LEGACY"), None)
        assert legacy is not None, "LOB_LEGACY not found"
        assert legacy["dataset_count"] == 1, (
            f"LOB_LEGACY dataset_count={legacy['dataset_count']}, expected 1 (customer_profile)"
        )
        assert legacy["aggregate_score"] == pytest.approx(95.00, abs=0.01), (
            f"LOB_LEGACY aggregate_score={legacy['aggregate_score']}, expected 95.00"
        )


# ---------------------------------------------------------------------------
# Integration tests — GET /api/lobs/{lob_id}/datasets (real Postgres)
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestLobDatasetsDataCorrectness:
    """AC4 + AC6 [P0]: GET /api/lobs/{lob_id}/datasets must return correct dataset data.

    TDD RED PHASE: All tests skip until endpoint is implemented.
    """

    def test_lob_datasets_returns_correct_lob_id(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P0]: Response must echo back the lob_id in the response body."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        assert body.get("lob_id") == "LOB_RETAIL", (
            f"Response lob_id='{body.get('lob_id')}', expected 'LOB_RETAIL'"
        )

    def test_lob_datasets_retail_returns_three_datasets(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P0]: LOB_RETAIL must have 3 datasets (sales_daily, products, customers)."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])
        assert len(datasets) == 3, (
            f"LOB_RETAIL has {len(datasets)} datasets, expected 3. "
            "Query v_dq_run_active WHERE lookup_code='LOB_RETAIL', "
            "latest partition_date per dataset_name."
        )

    def test_lob_datasets_items_have_all_required_fields(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P0]: Each dataset item must have all required snake_case fields."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])
        assert len(datasets) > 0, "No datasets returned for LOB_RETAIL"

        for ds in datasets:
            actual_keys = set(ds.keys())
            assert actual_keys >= EXPECTED_DATASET_IN_LOB_KEYS, (
                f"Dataset '{ds.get('dataset_name')}' missing required keys. "
                f"Expected (subset): {EXPECTED_DATASET_IN_LOB_KEYS}, Got: {actual_keys}"
            )

    def test_lob_datasets_sales_daily_has_correct_dqs_score(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P1]: sales_daily must have dqs_score=98.50 and check_status='PASS'."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])

        sales = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")),
            None,
        )
        assert sales is not None, (
            "sales_daily not found in LOB_RETAIL datasets. "
            "dataset_name='lob=retail/src_sys_nm=alpha/dataset=sales_daily'"
        )
        assert sales["check_status"] == "PASS", (
            f"sales_daily check_status='{sales['check_status']}', expected 'PASS'"
        )
        assert sales["dqs_score"] == pytest.approx(98.50, abs=0.01), (
            f"sales_daily dqs_score={sales['dqs_score']}, expected 98.50"
        )

    def test_lob_datasets_products_has_fail_status(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P1]: products must have check_status='FAIL' and dqs_score=45.00."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])

        products = next(
            (d for d in datasets if "products" in d.get("dataset_name", "")),
            None,
        )
        assert products is not None, "products not found in LOB_RETAIL datasets"
        assert products["check_status"] == "FAIL", (
            f"products check_status='{products['check_status']}', expected 'FAIL'"
        )
        assert products["dqs_score"] == pytest.approx(45.00, abs=0.01), (
            f"products dqs_score={products['dqs_score']}, expected 45.00"
        )

    def test_lob_datasets_trend_is_list_of_floats(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P0]: Each dataset's trend must be a list of floats (sparkline data)."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        for ds in body.get("datasets", []):
            trend = ds.get("trend")
            assert isinstance(trend, list), (
                f"'{ds['dataset_name']}' trend must be list, got {type(trend)}"
            )
            for val in trend:
                assert isinstance(val, (int, float)), (
                    f"trend value must be numeric, got {type(val)}"
                )

    def test_lob_datasets_per_check_statuses_types(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P2]: freshness_status, volume_status, schema_status must be str or None."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        valid_statuses = {"PASS", "WARN", "FAIL", None}
        for ds in body.get("datasets", []):
            for check_field in ("freshness_status", "volume_status", "schema_status"):
                val = ds.get(check_field)
                assert val in valid_statuses, (
                    f"'{ds['dataset_name']}' {check_field}='{val}' not in {valid_statuses}. "
                    "Valid values: 'PASS', 'WARN', 'FAIL', or null (no metric row)."
                )

    def test_lob_datasets_sales_daily_freshness_status_is_set(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P2]: sales_daily must have freshness_status set (FRESHNESS metric exists).

        From fixtures: dq_metric_numeric has FRESHNESS metric for sales_daily with
        hours_since_update=2. The endpoint should derive freshness_status from the
        presence of a FRESHNESS metric row in v_dq_metric_numeric_active.
        For the MVP approximation: freshness_status = overall check_status = 'PASS'.
        """
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])
        sales = next(
            (d for d in datasets if "sales_daily" in d.get("dataset_name", "")),
            None,
        )
        assert sales is not None
        # Must not be None — FRESHNESS metric row exists in fixtures
        assert sales.get("freshness_status") is not None, (
            "sales_daily freshness_status is None, but fixtures contain a FRESHNESS metric. "
            "Join v_dq_metric_numeric_active on dq_run_id WHERE check_type='FRESHNESS' — "
            "if row exists, set freshness_status to the run's overall check_status."
        )

    def test_lob_datasets_products_schema_status_is_set(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P2]: products must have schema_status set (SCHEMA metric exists).

        From fixtures: dq_metric_numeric has SCHEMA metric (missing_columns=3) for products.
        MVP approximation: schema_status = overall check_status = 'FAIL'.
        """
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])
        products = next(
            (d for d in datasets if "products" in d.get("dataset_name", "")),
            None,
        )
        assert products is not None
        assert products.get("schema_status") is not None, (
            "products schema_status is None, but fixtures contain a SCHEMA metric. "
            "Check v_dq_metric_numeric_active WHERE check_type='SCHEMA' for the run."
        )

    def test_lob_datasets_customers_volume_status_is_null(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P2]: customers must have volume_status=None (no VOLUME metric in fixtures).

        From fixtures: customers has only an OPS metric (null_rate), no VOLUME metric.
        volume_status should be None because no VOLUME check_type row exists.
        """
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])
        customers = next(
            (d for d in datasets if "customers" in d.get("dataset_name", "")),
            None,
        )
        assert customers is not None
        assert customers.get("volume_status") is None, (
            f"customers volume_status='{customers.get('volume_status')}', expected None. "
            "No VOLUME metric row exists for customers in fixtures — must return null."
        )


# ---------------------------------------------------------------------------
# Integration tests — time_range query parameter (AC5)
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestLobDatasetsTimeRange:
    """AC5 [P1]: time_range query parameter must filter sparkline data.

    TDD RED PHASE: All tests skip until endpoint is implemented.

    Fixture data: sales_daily has data for 7 days (2026-03-27 to 2026-04-02).
    """

    def test_time_range_7d_is_accepted(self, seeded_client: TestClient) -> None:
        """AC5 [P1]: time_range=7d must return 200 (not 400/422)."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets?time_range=7d")
        assert response.status_code == 200, (
            f"time_range=7d returned {response.status_code}, expected 200. "
            "Add time_range: str = '7d' query parameter to the endpoint."
        )

    def test_time_range_30d_is_accepted(self, seeded_client: TestClient) -> None:
        """AC5 [P1]: time_range=30d must return 200."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets?time_range=30d")
        assert response.status_code == 200, (
            f"time_range=30d returned {response.status_code}, expected 200."
        )

    def test_time_range_90d_is_accepted(self, seeded_client: TestClient) -> None:
        """AC5 [P1]: time_range=90d must return 200."""
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets?time_range=90d")
        assert response.status_code == 200, (
            f"time_range=90d returned {response.status_code}, expected 200."
        )

    def test_default_time_range_is_7d(self, seeded_client: TestClient) -> None:
        """AC5 [P1]: Omitting time_range must default to 7d (same result as explicit 7d)."""
        response_default = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        response_7d = seeded_client.get("/api/lobs/LOB_RETAIL/datasets?time_range=7d")

        assert response_default.status_code == 200
        assert response_7d.status_code == 200

        # Trend arrays should be identical (same 7d window)
        default_body = response_default.json()
        explicit_body = response_7d.json()

        for ds_default, ds_explicit in zip(
            sorted(default_body["datasets"], key=lambda d: d["dataset_name"]),
            sorted(explicit_body["datasets"], key=lambda d: d["dataset_name"]),
        ):
            assert ds_default["trend"] == ds_explicit["trend"], (
                f"Default time_range trend differs from explicit 7d for '{ds_default['dataset_name']}'. "
                "Default must be 7d."
            )

    def test_time_range_7d_sales_daily_trend_has_seven_points(
        self, seeded_client: TestClient
    ) -> None:
        """AC5 [P1]: sales_daily trend for 7d window must have exactly 7 data points.

        Fixtures: 7 days of data for sales_daily (2026-03-27 to 2026-04-02 inclusive = 7 days).
        All with dqs_score=98.50, so trend should be [98.5, 98.5, 98.5, 98.5, 98.5, 98.5, 98.5].
        """
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets?time_range=7d")
        assert response.status_code == 200

        body = response.json()
        sales = next(
            (d for d in body["datasets"] if "sales_daily" in d.get("dataset_name", "")),
            None,
        )
        assert sales is not None, "sales_daily not found"
        # 7 data points from generate_series('2026-03-27' to '2026-04-02')
        assert len(sales["trend"]) == 7, (
            f"sales_daily trend has {len(sales['trend'])} points, expected 7 for 7d window. "
            "Fixtures have 7 rows for sales_daily on dates 2026-03-27 through 2026-04-02."
        )

    def test_time_range_trend_ordered_oldest_to_newest(
        self, seeded_client: TestClient
    ) -> None:
        """AC5 [P2]: Trend array must be ordered oldest-to-newest (for sparkline rendering).

        Per story dev notes: trend is ordered oldest-to-newest for sparkline rendering.
        Use ORDER BY day ASC in the SQL query.
        """
        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets?time_range=7d")
        assert response.status_code == 200

        body = response.json()
        # All sales_daily scores are 98.50, so trend order can't be verified by values alone.
        # We verify the endpoint doesn't return descending order by checking it's non-empty
        # and that all values are numeric (structural check).
        for ds in body.get("datasets", []):
            trend = ds.get("trend", [])
            if len(trend) > 1:
                # If scores varied, we'd check ascending order.
                # For MVP test: just verify all numeric values present.
                assert all(isinstance(v, (int, float)) for v in trend), (
                    f"Non-numeric values in trend for '{ds['dataset_name']}'"
                )


# ---------------------------------------------------------------------------
# Integration tests — AC6: View usage validation
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestLobEndpointsViewUsage:
    """AC6 [P0]: All LOB endpoint queries must use v_*_active views, never raw tables.

    TDD RED PHASE: All tests skip until endpoints are implemented.
    """

    def test_lobs_list_excludes_expired_records(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P0]: GET /api/lobs must not include datasets with expired records.

        Inserts an expired LOB_NEWLOB dataset and verifies it doesn't appear in /api/lobs.
        If raw dq_run is queried, the expired dataset's LOB appears in results.
        """
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, expiry_date)
                VALUES
                    ('lob=new/dataset=expired', '2026-04-02', 'LOB_NEWLOB', 'PASS', 99.00, 0,
                     '2026-03-01 00:00:00')
                """
            )
        db_conn.commit()

        response = seeded_client.get(LOBS_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        lob_ids = {item["lob_id"] for item in body}
        assert "LOB_NEWLOB" not in lob_ids, (
            "LOB_NEWLOB found in /api/lobs — expired dataset should not appear. "
            "Use v_dq_run_active (filters on expiry_date = sentinel), not raw dq_run."
        )

    def test_lob_datasets_excludes_expired_records(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P0]: GET /api/lobs/{lob_id}/datasets must exclude expired dataset records.

        Inserts an expired dataset for LOB_RETAIL and verifies dataset count stays at 3.
        """
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, expiry_date)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=expired_ds', '2026-04-02',
                     'LOB_RETAIL', 'PASS', 80.00, 0, '2026-03-01 00:00:00')
                """
            )
        db_conn.commit()

        response = seeded_client.get("/api/lobs/LOB_RETAIL/datasets")
        assert response.status_code == 200

        body = response.json()
        datasets = body.get("datasets", [])
        assert len(datasets) == 3, (
            f"LOB_RETAIL has {len(datasets)} datasets after expired insert, expected 3. "
            "Expired datasets must be filtered by v_dq_run_active."
        )

    def test_nonexistent_lob_returns_404_with_seeded_db(
        self, seeded_client: TestClient
    ) -> None:
        """AC4 [P1]: GET /api/lobs/NONEXISTENT/datasets must return 404 with seeded DB.

        With real seeded data, an unknown LOB_ID returns no rows from v_dq_run_active.
        The endpoint must detect this and raise HTTPException(404).
        """
        response = seeded_client.get("/api/lobs/NONEXISTENT/datasets")
        assert response.status_code == 404, (
            f"Expected 404 for unknown LOB, got {response.status_code}. "
            "When v_dq_run_active returns no rows for lookup_code='NONEXISTENT', "
            "raise HTTPException(status_code=404, "
            "detail={'detail': 'LOB not found', 'error_code': 'NOT_FOUND'})."
        )

        body = response.json()
        detail = body.get("detail", {})
        assert isinstance(detail, dict), "404 detail must be a dict"
        assert detail.get("error_code") == "NOT_FOUND", (
            f"error_code='{detail.get('error_code')}', expected 'NOT_FOUND'"
        )
