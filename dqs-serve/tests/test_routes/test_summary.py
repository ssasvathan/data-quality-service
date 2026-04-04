"""Acceptance tests — Story 4.2: GET /api/summary endpoint.

TDD RED PHASE: All tests are marked @pytest.mark.skip because the endpoint
does not exist yet. Tests assert EXPECTED behavior per acceptance criteria.
Remove skip markers after implementation to enter green phase.

Test categories:
  - Unit tests (no DB, mock session): verify response structure, snake_case fields
  - Integration tests (real Postgres + seeded fixtures): verify data correctness,
    view usage, aggregate calculations

Run unit tests:     cd dqs-serve && uv run pytest tests/test_routes/test_summary.py
Run integration:    cd dqs-serve && uv run pytest -m integration tests/test_routes/test_summary.py
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest
from fastapi.testclient import TestClient

if TYPE_CHECKING:
    import psycopg2.extensions


# ---------------------------------------------------------------------------
# Helpers / shared constants
# ---------------------------------------------------------------------------

SUMMARY_ENDPOINT = "/api/summary"

# Expected snake_case keys in SummaryResponse (top-level)
EXPECTED_SUMMARY_TOP_KEYS = {
    "total_datasets",
    "healthy_count",
    "degraded_count",
    "critical_count",
    "lobs",
}

# Expected snake_case keys in each LobSummaryItem
EXPECTED_LOB_ITEM_KEYS = {
    "lob_id",
    "dataset_count",
    "aggregate_score",
    "healthy_count",
    "degraded_count",
    "critical_count",
    "trend",
}


# ---------------------------------------------------------------------------
# Unit tests — NO database required, mock the DB session
# These test route wiring and Pydantic model shapes.
# ---------------------------------------------------------------------------


class TestSummaryEndpointRouteWiring:
    """AC1 + AC2 [P0]: Route must be registered and return 200 with correct shape.

    TDD RED PHASE: endpoint does not exist — all tests skip.
    """

    def test_summary_endpoint_returns_200(self) -> None:
        """AC1 [P0]: GET /api/summary must return HTTP 200.

        Fails until route module is created and wired into FastAPI app.
        """
        # Import here so the test fails at import time if route doesn't exist yet,
        # producing a clear failure message rather than a module-not-found error.
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200, (
            f"GET {SUMMARY_ENDPOINT} returned {response.status_code}, expected 200. "
            "Route not yet registered — add router in src/serve/routes/summary.py "
            "and include it in main.py with prefix='/api'."
        )

    def test_summary_response_is_json(self) -> None:
        """AC2 [P0]: Response Content-Type must be application/json."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SUMMARY_ENDPOINT)
        assert "application/json" in response.headers.get("content-type", ""), (
            "GET /api/summary did not return JSON content-type"
        )

    def test_summary_response_has_snake_case_top_level_keys(self) -> None:
        """AC2 [P1]: All top-level JSON keys must be snake_case.

        Per project-context.md: all API responses use snake_case JSON field names.
        Pydantic model field names become JSON keys — they must all be snake_case.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        actual_keys = set(body.keys())
        assert actual_keys >= EXPECTED_SUMMARY_TOP_KEYS, (
            f"Missing snake_case keys in summary response. "
            f"Expected (subset): {EXPECTED_SUMMARY_TOP_KEYS}, "
            f"Got: {actual_keys}. "
            "Check Pydantic model field names in src/serve/routes/summary.py — "
            "all fields must be snake_case (no camelCase, no PascalCase)."
        )
        # Also verify NO camelCase keys leaked through
        camel_case_keys = [k for k in actual_keys if k != k.lower() and "_" not in k]
        assert camel_case_keys == [], (
            f"camelCase keys found in summary response: {camel_case_keys}. "
            "Use snake_case field names in Pydantic models."
        )

    def test_summary_lob_items_have_snake_case_keys(self) -> None:
        """AC2 [P1]: Each LOB item in the `lobs` array must have snake_case keys."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        lobs = body.get("lobs", [])
        assert isinstance(lobs, list), "lobs field must be a list"
        assert lobs != [], (
            "lobs list must not be empty — mock DB session must return at least one LOB row. "
            "Check _make_mock_db_session() in conftest.py."
        )

        for item in lobs:
            actual_keys = set(item.keys())
            assert actual_keys >= EXPECTED_LOB_ITEM_KEYS, (
                f"LOB item missing snake_case keys. "
                f"Expected (subset): {EXPECTED_LOB_ITEM_KEYS}, "
                f"Got: {actual_keys}"
            )

    def test_summary_lob_trend_is_list_of_floats(self) -> None:
        """AC1 [P2]: Each LOB's `trend` field must be a list of floats (for sparkline)."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        lobs = body.get("lobs", [])
        for lob in lobs:
            trend = lob.get("trend")
            assert isinstance(trend, list), (
                f"LOB '{lob.get('lob_id')}': trend must be a list, got {type(trend)}"
            )
            for val in trend:
                assert isinstance(val, (int, float)), (
                    f"LOB '{lob.get('lob_id')}': trend values must be numeric, got {type(val)}"
                )

    def test_summary_counts_are_non_negative_integers(self) -> None:
        """AC1 [P1]: total_datasets, healthy_count, degraded_count, critical_count must be non-negative ints."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        for field in ("total_datasets", "healthy_count", "degraded_count", "critical_count"):
            val = body.get(field)
            assert isinstance(val, int), (
                f"Field '{field}' must be int, got {type(val)}"
            )
            assert val >= 0, f"Field '{field}' must be non-negative, got {val}"

    def test_summary_pydantic_models_are_importable(self) -> None:
        """AC2 [P0]: Pydantic models LobSummaryItem and SummaryResponse must be importable.

        This is a structural test — fails until src/serve/routes/summary.py is created.
        """
        # Verify they are Pydantic models
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.summary import LobSummaryItem, SummaryResponse  # noqa: PLC0415

        assert issubclass(LobSummaryItem, BaseModel), "LobSummaryItem must inherit from pydantic.BaseModel"
        assert issubclass(SummaryResponse, BaseModel), "SummaryResponse must inherit from pydantic.BaseModel"

    def test_summary_response_model_field_names_are_snake_case(self) -> None:
        """AC2 [P1]: Pydantic SummaryResponse model fields must all be snake_case."""
        from serve.routes.summary import SummaryResponse  # noqa: PLC0415

        fields = SummaryResponse.model_fields
        for field_name in fields:
            assert field_name == field_name.lower(), (
                f"Field '{field_name}' in SummaryResponse is not snake_case. "
                "Per project-context.md: all API responses use snake_case JSON field names."
            )
            assert field_name.replace("_", "").isalpha() or field_name[-1].isdigit(), (
                f"Field '{field_name}' has unexpected characters"
            )


# ---------------------------------------------------------------------------
# Integration tests — require real Postgres + seeded fixtures
# Marked @pytest.mark.integration — excluded from default suite per pyproject.toml
# Run: cd dqs-serve && uv run pytest -m integration tests/test_routes/test_summary.py
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestSummaryEndpointDataCorrectness:
    """AC1 + AC6 [P0]: Data correctness against seeded fixtures from fixtures.sql.

    TDD RED PHASE: All tests skip until /api/summary is implemented.

    Fixture data (from fixtures.sql):
      LOB_RETAIL: 3 datasets (sales_daily PASS 98.50, products FAIL 45.00, customers WARN 72.00)
      LOB_COMMERCIAL: 2 datasets (transactions WARN 60.00, payments FAIL 0.00)
      LOB_LEGACY: 1 dataset (customer_profile PASS 95.00)
      Total: 6 datasets
      PASS count: 2 (sales_daily, customer_profile)
      WARN count: 2 (customers, transactions)
      FAIL count: 2 (products, payments)
    """

    def test_summary_total_datasets_matches_fixture_count(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: total_datasets must equal number of distinct datasets in fixtures (6).

        Expected: 6 (sales_daily, products, customers, transactions, payments, customer_profile)
        Note: sales_daily has 7 rows (historical trend), but only 1 unique dataset counted.
        Query must use DISTINCT dataset_name with latest partition_date per dataset.
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert body["total_datasets"] == 6, (
            f"total_datasets={body['total_datasets']}, expected 6. "
            "The query must count DISTINCT datasets (latest run per dataset), "
            "not total rows. sales_daily has 7 historical rows but counts as 1 dataset."
        )

    def test_summary_healthy_count_counts_pass_datasets(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: healthy_count must equal datasets with check_status='PASS' (2).

        From fixtures: sales_daily (PASS) + customer_profile (PASS) = 2 healthy datasets.
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert body["healthy_count"] == 2, (
            f"healthy_count={body['healthy_count']}, expected 2. "
            "Count DISTINCT datasets where check_status='PASS' in the latest run. "
            "From fixtures: sales_daily + customer_profile."
        )

    def test_summary_degraded_count_counts_warn_datasets(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: degraded_count must equal datasets with check_status='WARN' (2).

        From fixtures: customers (WARN) + transactions (WARN) = 2 degraded datasets.
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert body["degraded_count"] == 2, (
            f"degraded_count={body['degraded_count']}, expected 2. "
            "Count DISTINCT datasets where check_status='WARN' in the latest run. "
            "From fixtures: customers + transactions."
        )

    def test_summary_critical_count_counts_fail_datasets(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: critical_count must equal datasets with check_status='FAIL' (2).

        From fixtures: products (FAIL) + payments (FAIL) = 2 critical datasets.
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert body["critical_count"] == 2, (
            f"critical_count={body['critical_count']}, expected 2. "
            "Count DISTINCT datasets where check_status='FAIL' in the latest run. "
            "From fixtures: products + payments."
        )

    def test_summary_counts_add_up_to_total(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: healthy + degraded + critical counts must sum to total_datasets."""
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        total = body["total_datasets"]
        count_sum = body["healthy_count"] + body["degraded_count"] + body["critical_count"]
        assert count_sum == total, (
            f"Count sum {count_sum} != total_datasets {total}. "
            "Every dataset must be classified as healthy/degraded/critical."
        )

    def test_summary_lobs_list_contains_all_three_lobs(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: lobs list must contain entries for all 3 LOBs from fixtures.

        Expected: LOB_RETAIL, LOB_COMMERCIAL, LOB_LEGACY
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        lob_ids = {lob["lob_id"] for lob in body["lobs"]}
        expected_lobs = {"LOB_RETAIL", "LOB_COMMERCIAL", "LOB_LEGACY"}
        assert expected_lobs == lob_ids, (
            f"Expected LOB IDs {expected_lobs}, got {lob_ids}. "
            "The summary must include all LOBs from v_dq_run_active (via lookup_code)."
        )

    def test_summary_retail_lob_has_correct_dataset_count(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: LOB_RETAIL must have dataset_count=3.

        From fixtures: sales_daily, products, customers (all LOB_RETAIL).
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        retail = next(
            (lob for lob in body["lobs"] if lob["lob_id"] == "LOB_RETAIL"), None
        )
        assert retail is not None, "LOB_RETAIL not found in lobs list"
        assert retail["dataset_count"] == 3, (
            f"LOB_RETAIL dataset_count={retail['dataset_count']}, expected 3. "
            "From fixtures: sales_daily, products, customers."
        )

    def test_summary_retail_lob_aggregate_score_is_rounded_average_of_latest(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: LOB_RETAIL aggregate_score must be ROUND(AVG(latest dqs_score), 2).

        Latest scores from fixtures:
          sales_daily partition_date=2026-04-02 dqs_score=98.50
          products    partition_date=2026-04-02 dqs_score=45.00
          customers   partition_date=2026-04-02 dqs_score=72.00
          AVG = (98.50 + 45.00 + 72.00) / 3 = 215.50 / 3 = 71.83 (rounded to 2dp)
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        retail = next(
            (lob for lob in body["lobs"] if lob["lob_id"] == "LOB_RETAIL"), None
        )
        assert retail is not None, "LOB_RETAIL not found in lobs list"
        assert retail["aggregate_score"] == pytest.approx(71.83, abs=0.01), (
            f"LOB_RETAIL aggregate_score={retail['aggregate_score']}, expected ~71.83. "
            "Use ROUND(AVG(dqs_score), 2) on the LATEST partition_date per dataset."
        )

    def test_summary_retail_lob_status_distribution_correct(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: LOB_RETAIL must have healthy=1, degraded=1, critical=1.

        sales_daily=PASS (healthy), customers=WARN (degraded), products=FAIL (critical)
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        retail = next(
            (lob for lob in body["lobs"] if lob["lob_id"] == "LOB_RETAIL"), None
        )
        assert retail is not None, "LOB_RETAIL not found"
        assert retail["healthy_count"] == 1, (
            f"LOB_RETAIL healthy_count={retail['healthy_count']}, expected 1 (sales_daily)"
        )
        assert retail["degraded_count"] == 1, (
            f"LOB_RETAIL degraded_count={retail['degraded_count']}, expected 1 (customers)"
        )
        assert retail["critical_count"] == 1, (
            f"LOB_RETAIL critical_count={retail['critical_count']}, expected 1 (products)"
        )

    def test_summary_retail_lob_trend_is_non_empty_list(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P2]: LOB_RETAIL trend must be a non-empty list of floats.

        sales_daily has 7 days of historical data in fixtures (2026-03-27 to 2026-04-02),
        all with dqs_score=98.50. Default 7d window should return at minimum 1 data point.
        """
        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        retail = next(
            (lob for lob in body["lobs"] if lob["lob_id"] == "LOB_RETAIL"), None
        )
        assert retail is not None, "LOB_RETAIL not found"
        trend = retail.get("trend", [])
        assert isinstance(trend, list), f"LOB_RETAIL trend must be a list, got {type(trend)}"
        assert len(trend) >= 1, (
            "LOB_RETAIL trend must have at least 1 data point. "
            "fixtures.sql has 7 days of data for sales_daily in LOB_RETAIL."
        )
        for val in trend:
            assert isinstance(val, (int, float)), (
                f"trend values must be numeric, got {type(val)}: {val}"
            )


@pytest.mark.integration
class TestSummaryEndpointViewUsage:
    """AC6 [P0]: Summary endpoint must query v_dq_run_active, never raw dq_run table.

    TDD RED PHASE: All tests skip until implementation exists.
    """

    def test_summary_excludes_expired_records(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P0]: Summary must not count expired records (expiry_date != sentinel).

        Inserts an expired dq_run record and verifies total_datasets remains 6.
        If the endpoint queries raw dq_run instead of v_dq_run_active, it will
        count expired records and the total will be wrong.
        """
        # Insert an expired record (soft-deleted)
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, expiry_date)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=expired_dataset',
                     '2026-04-02', 'LOB_RETAIL', 'PASS', 90.00, 0, '2026-03-01 00:00:00')
                """
            )
        db_conn.commit()

        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        assert body["total_datasets"] == 6, (
            f"total_datasets={body['total_datasets']}, expected 6. "
            "The endpoint queried raw dq_run (includes expired) instead of v_dq_run_active. "
            "ANTI-PATTERN: never query raw tables in the serve layer."
        )

    def test_summary_reflects_new_active_record_immediately(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P1]: Adding a new active dq_run record must be reflected in the summary.

        Inserts a 7th active dataset and verifies total_datasets becomes 7.
        Validates that the view correctly includes all sentinel-expiry records.
        """
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=new_dataset',
                     '2026-04-02', 'LOB_RETAIL', 'PASS', 85.00, 0)
                """
            )
        db_conn.commit()

        response = seeded_client.get(SUMMARY_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        # Note: new_dataset on 2026-04-02 — distinct dataset, so total should be 7
        assert body["total_datasets"] == 7, (
            f"total_datasets={body['total_datasets']}, expected 7 after insert. "
            "The v_dq_run_active view must reflect newly inserted active records."
        )
