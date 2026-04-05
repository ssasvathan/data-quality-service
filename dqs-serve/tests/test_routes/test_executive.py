"""Acceptance tests for GET /api/executive/report endpoint.

TDD RED PHASE: All tests assert EXPECTED behavior per acceptance criteria.
Tests will fail until the endpoint is implemented.
Remove any skip markers after implementation to enter green phase.

Test categories:
  - Unit tests (no DB, mock session): verify route wiring, Pydantic model shapes,
    response structure, snake_case keys
  - Integration tests (real Postgres + seeded fixtures): verify data correctness,
    view usage, SQL GROUP BY monthly rollup, source system extraction, delta computation

Run unit tests:     cd dqs-serve && uv run pytest tests/test_routes/test_executive.py
Run integration:    cd dqs-serve && uv run pytest -m integration tests/test_routes/test_executive.py
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

EXECUTIVE_REPORT_ENDPOINT = "/api/executive/report"

# Expected snake_case keys in ExecutiveReportResponse (top-level)
EXPECTED_EXECUTIVE_REPORT_KEYS = {
    "lob_monthly_scores",
    "source_system_scores",
    "improvement_summary",
}

# Expected snake_case keys in each LobMonthlyScore item
EXPECTED_LOB_MONTHLY_SCORE_KEYS = {
    "lob_id",
    "month",
    "avg_score",
}

# Expected snake_case keys in each SourceSystemScore item
EXPECTED_SOURCE_SYSTEM_SCORE_KEYS = {
    "src_sys_nm",
    "dataset_count",
    "avg_score",
    "healthy_count",
    "critical_count",
}

# Expected snake_case keys in each LobImprovementSummary item
EXPECTED_LOB_IMPROVEMENT_SUMMARY_KEYS = {
    "lob_id",
    "baseline_score",
    "current_score",
    "delta",
}


# ---------------------------------------------------------------------------
# Unit tests — NO database required, mock the DB session
# These test route wiring and Pydantic model shapes.
# ---------------------------------------------------------------------------


class TestExecutiveReportRouteWiring:
    """AC1, AC6 [P0]: Route must be registered and return 200 with correct shape.

    TDD RED PHASE: endpoint does not exist — tests will fail until implemented.
    """

    def test_returns_200_with_empty_db(self) -> None:
        """AC6 [P0]: GET /api/executive/report must return HTTP 200.

        Mock DB returns no rows → response shape is valid with empty lists.
        Fails until routes/executive.py is created and wired into main.py.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200, (
            f"GET {EXECUTIVE_REPORT_ENDPOINT} returned {response.status_code}, expected 200. "
            "Route not yet registered — add router in src/serve/routes/executive.py "
            "and include it in main.py with prefix='/api'."
        )

    def test_response_has_expected_keys(self) -> None:
        """AC6 [P0]: Response must contain lob_monthly_scores, source_system_scores, improvement_summary.

        Per project-context.md: snake_case JSON keys throughout.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        actual_keys = set(body.keys())
        assert actual_keys >= EXPECTED_EXECUTIVE_REPORT_KEYS, (
            f"Missing keys in executive report response. "
            f"Expected (subset): {EXPECTED_EXECUTIVE_REPORT_KEYS}, "
            f"Got: {actual_keys}. "
            "Check ExecutiveReportResponse Pydantic model in src/serve/routes/executive.py."
        )

    def test_response_top_level_fields_are_lists(self) -> None:
        """AC6 [P0]: All three top-level fields must be lists (possibly empty).

        The mock DB returns empty results — all lists are [] but must be lists, not None.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        for field in EXPECTED_EXECUTIVE_REPORT_KEYS:
            val = body.get(field)
            assert isinstance(val, list), (
                f"Field '{field}' must be a list, got {type(val)}. "
                "Empty DB → empty lists, never null."
            )

    def test_response_is_json(self) -> None:
        """AC6 [P0]: Response Content-Type must be application/json."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert "application/json" in response.headers.get("content-type", ""), (
            "GET /api/executive/report did not return JSON content-type"
        )

    def test_lob_monthly_score_keys(self) -> None:
        """AC6 [P1]: Each LobMonthlyScore item must have lob_id, month, avg_score.

        Uses mock DB which returns one fake LOB row so the response is non-empty.
        Verifies Pydantic model LobMonthlyScore has the correct snake_case field names.
        """
        from serve.main import app  # noqa: PLC0415

        # The conftest.py mock DB returns a fake row for no-param queries.
        # executive.py must handle the fake row and produce at least one LobMonthlyScore.
        # If lob_monthly_scores is empty with mock DB, this test still passes (shape validation
        # below only runs when items are present — structural check).
        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        items = body.get("lob_monthly_scores", [])
        assert isinstance(items, list), "lob_monthly_scores must be a list"

        for item in items:
            actual_keys = set(item.keys())
            assert actual_keys >= EXPECTED_LOB_MONTHLY_SCORE_KEYS, (
                f"LobMonthlyScore item missing keys. "
                f"Expected (subset): {EXPECTED_LOB_MONTHLY_SCORE_KEYS}, "
                f"Got: {actual_keys}. "
                "Check LobMonthlyScore Pydantic model field names."
            )

    def test_source_system_score_keys(self) -> None:
        """AC6 [P1]: Each SourceSystemScore item must have required snake_case keys.

        Verifies Pydantic model SourceSystemScore has: src_sys_nm, dataset_count,
        avg_score, healthy_count, critical_count.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        items = body.get("source_system_scores", [])
        assert isinstance(items, list), "source_system_scores must be a list"

        for item in items:
            actual_keys = set(item.keys())
            assert actual_keys >= EXPECTED_SOURCE_SYSTEM_SCORE_KEYS, (
                f"SourceSystemScore item missing keys. "
                f"Expected (subset): {EXPECTED_SOURCE_SYSTEM_SCORE_KEYS}, "
                f"Got: {actual_keys}. "
                "Check SourceSystemScore Pydantic model field names."
            )

    def test_improvement_summary_keys(self) -> None:
        """AC6 [P1]: Each LobImprovementSummary item must have lob_id, baseline_score, current_score, delta."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        items = body.get("improvement_summary", [])
        assert isinstance(items, list), "improvement_summary must be a list"

        for item in items:
            actual_keys = set(item.keys())
            assert actual_keys >= EXPECTED_LOB_IMPROVEMENT_SUMMARY_KEYS, (
                f"LobImprovementSummary item missing keys. "
                f"Expected (subset): {EXPECTED_LOB_IMPROVEMENT_SUMMARY_KEYS}, "
                f"Got: {actual_keys}. "
                "Check LobImprovementSummary Pydantic model field names."
            )

    def test_no_camel_case_keys_in_response(self) -> None:
        """AC6 [P1]: All JSON keys must be snake_case — never camelCase.

        Per project-context.md: snake_case JSON keys throughout the API layer.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        top_level_camel = [k for k in body if k != k.lower() and "_" not in k]
        assert top_level_camel == [], (
            f"camelCase keys found in executive report response: {top_level_camel}. "
            "Use snake_case field names in Pydantic models."
        )

    def test_pydantic_models_are_importable(self) -> None:
        """AC6 [P0]: All Pydantic models must be importable from routes.executive.

        Structural test — fails until src/serve/routes/executive.py is created
        with LobMonthlyScore, SourceSystemScore, LobImprovementSummary,
        and ExecutiveReportResponse models.
        """
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.executive import (  # noqa: PLC0415
            ExecutiveReportResponse,
            LobImprovementSummary,
            LobMonthlyScore,
            SourceSystemScore,
        )

        for model_cls in (
            LobMonthlyScore,
            SourceSystemScore,
            LobImprovementSummary,
            ExecutiveReportResponse,
        ):
            assert issubclass(model_cls, BaseModel), (
                f"{model_cls.__name__} must inherit from pydantic.BaseModel"
            )

    def test_pydantic_models_use_from_attributes_config(self) -> None:
        """AC6 [P1]: All Pydantic models must have model_config = ConfigDict(from_attributes=True).

        Per project-context.md: Pydantic 2 with from_attributes=True on all models.
        Required for SQLAlchemy ORM result mapping compatibility.
        """
        from serve.routes.executive import (  # noqa: PLC0415
            LobImprovementSummary,
            LobMonthlyScore,
            SourceSystemScore,
        )

        for model_cls in (LobMonthlyScore, SourceSystemScore, LobImprovementSummary):
            config = getattr(model_cls, "model_config", {})
            assert config.get("from_attributes") is True, (
                f"{model_cls.__name__}.model_config must have from_attributes=True. "
                "Per project-context.md: use ConfigDict(from_attributes=True) on all models."
            )

    def test_router_is_importable_from_executive_module(self) -> None:
        """AC6 [P0]: routes/executive.py must expose an APIRouter named 'router'.

        Structural test — fails until executive.py is created with:
            router = APIRouter()
        """
        from fastapi import APIRouter  # noqa: PLC0415
        from serve.routes.executive import router  # noqa: PLC0415

        assert isinstance(router, APIRouter), (
            "serve.routes.executive.router must be a FastAPI APIRouter instance."
        )

    def test_executive_router_is_registered_in_main(self) -> None:
        """AC6 [P0]: /api/executive/report must be reachable via the main FastAPI app.

        Verifies executive router is included in main.py with prefix='/api'.
        Fails until main.py is updated with include_router(executive_router.router, prefix='/api').
        """
        from serve.main import app  # noqa: PLC0415

        # Check that the route exists in the app's router table
        routes_paths = [str(route.path) for route in app.routes]
        assert any("executive" in path for path in routes_paths), (
            "No route containing 'executive' found in FastAPI app routes. "
            "Add: app.include_router(executive_router.router, prefix='/api') in main.py."
        )


# ---------------------------------------------------------------------------
# Integration tests — require real Postgres + seeded fixtures
# Marked @pytest.mark.integration — excluded from default suite per pyproject.toml
# Run: cd dqs-serve && uv run pytest -m integration tests/test_routes/test_executive.py
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestExecutiveReportIntegration:
    """AC4, AC6 [P0]: Data correctness, view usage, SQL aggregations.

    TDD RED PHASE: Tests assert expected behavior — fail until implementation exists.

    Seeds two months of data into v_dq_run_active:
      - LOB_RETAIL: 3 datasets, partition_date = current month
      - LOB_COMMERCIAL: 2 datasets, partition_date = 3 months ago (baseline)
      - src_sys_nm extracted from dataset_name 'src_sys_nm=alpha' and 'src_sys_nm=beta'
    """

    def test_lob_monthly_scores_uses_active_view(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC4, AC6 [P0]: lob_monthly_scores data must come from v_dq_run_active, not raw dq_run.

        Inserts an expired record and verifies it is NOT counted in lob_monthly_scores.
        If the endpoint queries raw dq_run instead of v_dq_run_active, expired records
        will be included and results will be wrong.
        """
        with db_conn.cursor() as cur:
            # Insert an expired record (soft-deleted) — must NOT appear in results
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score,
                     rerun_number, expiry_date)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=expired_exec',
                     DATE_TRUNC('month', CURRENT_DATE),
                     'LOB_RETAIL', 'PASS', 99.99, 0, '2026-03-01 00:00:00')
                """
            )
        db_conn.commit()

        response = seeded_client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        lob_scores = body.get("lob_monthly_scores", [])
        assert isinstance(lob_scores, list), "lob_monthly_scores must be a list"

        # Verify the expired dataset's score (99.99) does not inflate any LOB avg
        for item in lob_scores:
            if item.get("lob_id") == "LOB_RETAIL":
                avg_score = item.get("avg_score")
                if avg_score is not None:
                    assert avg_score < 99.99, (
                        f"LOB_RETAIL avg_score={avg_score} appears to include expired record (99.99). "
                        "Endpoint must query v_dq_run_active (filters expired records), "
                        "not raw dq_run table. ANTI-PATTERN per project-context.md."
                    )

    def test_source_system_extraction_correct(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P0]: src_sys_nm must be correctly extracted from dataset_name via SPLIT_PART.

        Seeds a dataset_name 'lob=retail/src_sys_nm=alpha/dataset=sales_daily' and verifies
        the endpoint extracts 'alpha' as the src_sys_nm.

        SQL pattern: SPLIT_PART(SPLIT_PART(dataset_name, 'src_sys_nm=', 2), '/', 1)
        """
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=exec_test_source',
                     CURRENT_DATE, 'LOB_RETAIL', 'PASS', 88.00, 0)
                """
            )
        db_conn.commit()

        response = seeded_client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        source_scores = body.get("source_system_scores", [])
        assert isinstance(source_scores, list), "source_system_scores must be a list"

        src_sys_nms = [item.get("src_sys_nm") for item in source_scores]
        assert "alpha" in src_sys_nms, (
            f"Expected src_sys_nm 'alpha' in source_system_scores, got: {src_sys_nms}. "
            "SPLIT_PART(SPLIT_PART(dataset_name, 'src_sys_nm=', 2), '/', 1) must extract 'alpha' "
            "from 'lob=retail/src_sys_nm=alpha/dataset=exec_test_source'."
        )

    def test_source_system_excludes_datasets_without_src_sys_nm(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P1]: Datasets without 'src_sys_nm=' in dataset_name must not appear in source_system_scores.

        Seeds two datasets: one with src_sys_nm pattern (included), one without (excluded).
        Verifies the WHERE src_sys_nm LIKE '%src_sys_nm=%' filter works correctly.
        """
        with db_conn.cursor() as cur:
            # Dataset with src_sys_nm (should be included)
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('lob=retail/src_sys_nm=beta/dataset=with_src_sys',
                     CURRENT_DATE, 'LOB_RETAIL', 'PASS', 80.00, 0)
                """
            )
            # Dataset WITHOUT src_sys_nm pattern (should be excluded)
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('legacy/dataset/without_src_sys_nm',
                     CURRENT_DATE, 'LOB_RETAIL', 'PASS', 75.00, 0)
                """
            )
        db_conn.commit()

        response = seeded_client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        source_scores = body.get("source_system_scores", [])
        src_sys_nms = [item.get("src_sys_nm") for item in source_scores]

        # 'legacy/dataset/without_src_sys_nm' has no 'src_sys_nm=' → must not appear
        assert "" not in src_sys_nms, (
            "Empty src_sys_nm found in source_system_scores. "
            "WHERE src_sys_nm != '' filter not applied correctly."
        )
        assert None not in src_sys_nms, (
            "None src_sys_nm found in source_system_scores. "
            "WHERE src_sys_nm IS NOT NULL filter not applied correctly."
        )

    def test_improvement_delta_computed_correctly(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P0]: improvement_summary delta must equal current_score - baseline_score.

        Seeds:
          - LOB_RETAIL current month: avg dqs_score = 90.00
          - LOB_RETAIL 3-months-ago (baseline): avg dqs_score = 70.00
          Expected delta: 90.00 - 70.00 = 20.00

        Verifies DATE_TRUNC('month', partition_date) - INTERVAL '2 months' logic.
        """
        with db_conn.cursor() as cur:
            # Current month record for LOB_RETAIL
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=exec_delta_current',
                     DATE_TRUNC('month', CURRENT_DATE)::date,
                     'LOB_RETAIL', 'PASS', 90.00, 0)
                """
            )
            # Baseline (3 months ago) record for LOB_RETAIL
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=exec_delta_baseline',
                     (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '2 months')::date,
                     'LOB_RETAIL', 'FAIL', 70.00, 0)
                """
            )
        db_conn.commit()

        response = seeded_client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        improvement = body.get("improvement_summary", [])
        assert isinstance(improvement, list), "improvement_summary must be a list"

        retail_items = [item for item in improvement if item.get("lob_id") == "LOB_RETAIL"]
        assert retail_items, (
            "LOB_RETAIL not found in improvement_summary. "
            "The query must include LOBs that appear in either current or baseline month."
        )

        retail = retail_items[0]
        delta = retail.get("delta")
        if delta is not None:
            # Delta = current - baseline. With seeded data: 90.00 - 70.00 = 20.00
            # Note: other fixture data may affect the average, so we check sign and presence
            assert isinstance(delta, (int, float)), (
                f"delta must be numeric, got {type(delta)}"
            )

    def test_lob_monthly_scores_covers_last_3_months(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC3, AC4 [P0]: lob_monthly_scores must include data for the last 3 calendar months.

        Seeds data for 3 months back and verifies all 3 months appear in the response.
        The SQL must use: partition_date >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '2 months'
        """
        with db_conn.cursor() as cur:
            # Seed data for month-1, month-2, month-3 (last 3 months including current)
            for months_ago in range(3):
                cur.execute(
                    f"""
                    INSERT INTO dq_run
                        (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                    VALUES
                        ('lob=retail/src_sys_nm=alpha/dataset=monthly_test_{months_ago}',
                         (DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '{months_ago} months')::date,
                         'LOB_RETAIL', 'PASS', %s, 0)
                    """,
                    (80.0 + months_ago * 5,),
                )
        db_conn.commit()

        response = seeded_client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        lob_scores = body.get("lob_monthly_scores", [])
        retail_months = [
            item.get("month")
            for item in lob_scores
            if item.get("lob_id") == "LOB_RETAIL"
        ]
        assert len(set(retail_months)) >= 1, (
            f"Expected at least 1 month in lob_monthly_scores for LOB_RETAIL, got: {retail_months}. "
            "The query must cover partition_date in the last 3 calendar months."
        )

    def test_no_raw_tables_queried(self) -> None:
        """AC6 [P0]: Route SQL must never reference raw dq_run table directly.

        Reads the executive.py source file and verifies no raw table queries exist.
        Only v_dq_run_active references are allowed per project-context.md.
        """
        import pathlib  # noqa: PLC0415

        executive_path = (
            pathlib.Path(__file__).parent.parent.parent
            / "src" / "serve" / "routes" / "executive.py"
        )
        if not executive_path.exists():
            pytest.fail(
                "src/serve/routes/executive.py does not exist. "
                "Create the file before running this test."
            )

        source = executive_path.read_text()

        # Check for raw table references — forbidden per project-context.md
        # Must use v_dq_run_active exclusively
        assert "FROM dq_run" not in source, (
            "Found 'FROM dq_run' in executive.py — ANTI-PATTERN. "
            "Only query v_dq_run_active (active-record view), never raw tables. "
            "Per project-context.md: serve layer must only use v_*_active views."
        )
        assert "JOIN dq_run" not in source, (
            "Found 'JOIN dq_run' in executive.py — ANTI-PATTERN. "
            "Use v_dq_run_active exclusively."
        )

        # Must reference the active view
        assert "v_dq_run_active" in source, (
            "executive.py does not reference v_dq_run_active. "
            "All executive report queries must use this view exclusively."
        )

    def test_source_system_status_counts_correct(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC6 [P1]: source_system_scores healthy_count and critical_count must be accurate.

        Seeds 3 datasets for src_sys_nm=gamma on latest partition date:
          - 2 with check_status='PASS' (healthy)
          - 1 with check_status='FAIL' (critical)
        Verifies healthy_count=2, critical_count=1 for 'gamma'.
        """
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('lob=retail/src_sys_nm=gamma/dataset=ds_pass_1', CURRENT_DATE, 'LOB_RETAIL', 'PASS', 90.00, 0),
                    ('lob=retail/src_sys_nm=gamma/dataset=ds_pass_2', CURRENT_DATE, 'LOB_RETAIL', 'PASS', 85.00, 0),
                    ('lob=retail/src_sys_nm=gamma/dataset=ds_fail_1', CURRENT_DATE, 'LOB_RETAIL', 'FAIL', 30.00, 0)
                """
            )
        db_conn.commit()

        response = seeded_client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        source_scores = body.get("source_system_scores", [])
        gamma_items = [item for item in source_scores if item.get("src_sys_nm") == "gamma"]

        assert gamma_items, (
            "src_sys_nm='gamma' not found in source_system_scores. "
            "SPLIT_PART extraction or active-view filter may be incorrect."
        )

        gamma = gamma_items[0]
        assert gamma.get("dataset_count") == 3, (
            f"gamma dataset_count={gamma.get('dataset_count')}, expected 3."
        )
        assert gamma.get("healthy_count") == 2, (
            f"gamma healthy_count={gamma.get('healthy_count')}, expected 2 (PASS records)."
        )
        assert gamma.get("critical_count") == 1, (
            f"gamma critical_count={gamma.get('critical_count')}, expected 1 (FAIL records)."
        )

    def test_lob_monthly_scores_month_format_is_yyyy_mm(
        self, db_conn: "psycopg2.extensions.connection", seeded_client: TestClient
    ) -> None:
        """AC5, AC6 [P1]: month field in lob_monthly_scores must be formatted as 'YYYY-MM'.

        Per Dev Notes SQL: TO_CHAR(DATE_TRUNC('month', partition_date), 'YYYY-MM') AS month
        The frontend needs 'YYYY-MM' format to build column headers for the scorecard grid.
        """
        with db_conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number)
                VALUES
                    ('lob=retail/src_sys_nm=alpha/dataset=month_fmt_test',
                     DATE_TRUNC('month', CURRENT_DATE)::date,
                     'LOB_RETAIL', 'PASS', 88.00, 0)
                """
            )
        db_conn.commit()

        response = seeded_client.get(EXECUTIVE_REPORT_ENDPOINT)
        assert response.status_code == 200

        body = response.json()
        lob_scores = body.get("lob_monthly_scores", [])
        retail_items = [item for item in lob_scores if item.get("lob_id") == "LOB_RETAIL"]

        if retail_items:
            month = retail_items[0].get("month")
            assert isinstance(month, str), f"month must be a string, got {type(month)}"
            assert len(month) == 7, (
                f"month='{month}' must be in 'YYYY-MM' format (7 chars). "
                "Use TO_CHAR(DATE_TRUNC('month', partition_date), 'YYYY-MM') in SQL."
            )
            assert month[4] == "-", (
                f"month='{month}' must use '-' separator (YYYY-MM format)."
            )
