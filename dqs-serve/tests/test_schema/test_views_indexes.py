"""Acceptance tests for Story 1-6: Active-Record Views & Indexing.

Test categories:
  - View existence tests (AC1): all 6 v_*_active views exist — parametrized
  - View filter tests (AC2, AC4): active records visible, expired records excluded
  - Index existence tests (AC3): all 6 composite indexes exist — parametrized

All tests are @pytest.mark.integration — require a running Postgres instance
with ddl.sql AND views.sql applied (conftest.py db_conn fixture handles both).

Run integration tests: cd dqs-serve && uv run pytest -m integration -v
Default suite (no -m flag) DESELECTS these via pyproject.toml addopts.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest

if TYPE_CHECKING:
    import psycopg2


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------


def _insert_dq_run(cur: "psycopg2.cursor", dataset_name: str) -> int:  # type: ignore[name-defined]
    """Insert a minimal dq_run row and return its id.

    Used by view filter tests that need a parent dq_run to satisfy FK constraints
    on dq_metric_numeric and dq_metric_detail.
    """
    cur.execute(
        "INSERT INTO dq_run (dataset_name, partition_date, check_status) "
        "VALUES (%s, '2026-01-01', 'PASS') RETURNING id",
        (dataset_name,),
    )
    row = cur.fetchone()
    assert row is not None
    return row[0]


# ---------------------------------------------------------------------------
# AC1: All 6 active-record views exist in information_schema.views
# ---------------------------------------------------------------------------


@pytest.mark.integration
@pytest.mark.parametrize(
    "view_name",
    [
        "v_dq_orchestration_run_active",
        "v_dq_run_active",
        "v_dq_metric_numeric_active",
        "v_dq_metric_detail_active",
        "v_check_config_active",
        "v_dataset_enrichment_active",
    ],
)
class TestActiveViewsExist:
    """AC1: Each v_*_active view must exist in the public schema after views.sql is applied."""

    def test_view_exists(
        self,
        db_conn: "psycopg2.connection",  # type: ignore[name-defined]
        view_name: str,
    ) -> None:
        """AC1 [P0]: View must exist in information_schema.views (public schema)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT table_name FROM information_schema.views
            WHERE table_schema = 'public' AND table_name = %s
            """,
            (view_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"View '{view_name}' not found in information_schema.views — "
            "execute dqs-serve/src/serve/schema/views.sql to create it"
        )


# ---------------------------------------------------------------------------
# AC2 + AC4: v_dq_run_active — active record visible, expired excluded
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDqRunActiveView:
    """AC2+AC4: v_dq_run_active filters on EXPIRY_SENTINEL — returns active, excludes expired."""

    def test_active_record_visible(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: A row inserted with default expiry_date (sentinel) must appear in v_dq_run_active."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dq_run (dataset_name, partition_date, check_status) "
            "VALUES ('sales_daily', '2026-01-01', 'PASS')"
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_run_active WHERE dataset_name = 'sales_daily'"
        )
        count = cur.fetchone()[0]
        assert count == 1, (
            f"Expected 1 active record in v_dq_run_active for 'sales_daily', got {count}. "
            f"View must filter on expiry_date = '{EXPIRY_SENTINEL}'"
        )

    def test_expired_record_excluded(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: A row with expiry_date = NOW() (expired) must NOT appear in v_dq_run_active."""
        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dq_run (dataset_name, partition_date, check_status, expiry_date) "
            "VALUES ('sales_daily_expired', '2026-01-01', 'PASS', NOW())"
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_run_active WHERE dataset_name = 'sales_daily_expired'"
        )
        count = cur.fetchone()[0]
        assert count == 0, (
            f"Expected 0 records in v_dq_run_active for expired 'sales_daily_expired', got {count}. "
            "View must exclude rows where expiry_date != EXPIRY_SENTINEL"
        )


# ---------------------------------------------------------------------------
# AC2 + AC4: v_dq_metric_numeric_active
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDqMetricNumericActiveView:
    """AC2+AC4: v_dq_metric_numeric_active filters on EXPIRY_SENTINEL."""

    def test_active_record_visible(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Active metric_numeric row must appear in v_dq_metric_numeric_active."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        run_id = _insert_dq_run(cur, "metrics_test_ds")
        cur.execute(
            "INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value) "
            "VALUES (%s, 'volume', 'row_count', 100)",
            (run_id,),
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_metric_numeric_active WHERE dq_run_id = %s",
            (run_id,),
        )
        count = cur.fetchone()[0]
        assert count == 1, (
            f"Expected 1 active row in v_dq_metric_numeric_active for run_id={run_id}, got {count}. "
            f"View must filter on expiry_date = '{EXPIRY_SENTINEL}'"
        )

    def test_expired_record_excluded(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Expired metric_numeric row must NOT appear in v_dq_metric_numeric_active."""
        cur = db_conn.cursor()
        run_id = _insert_dq_run(cur, "metrics_test_ds_expired")
        cur.execute(
            "INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value, expiry_date) "
            "VALUES (%s, 'volume', 'row_count', 100, NOW())",
            (run_id,),
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_metric_numeric_active WHERE dq_run_id = %s",
            (run_id,),
        )
        count = cur.fetchone()[0]
        assert count == 0, (
            f"Expected 0 rows in v_dq_metric_numeric_active for expired run_id={run_id}, got {count}. "
            "View must exclude rows where expiry_date != EXPIRY_SENTINEL"
        )


# ---------------------------------------------------------------------------
# AC2 + AC4: v_dq_metric_detail_active
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDqMetricDetailActiveView:
    """AC2+AC4: v_dq_metric_detail_active filters on EXPIRY_SENTINEL."""

    def test_active_record_visible(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Active metric_detail row must appear in v_dq_metric_detail_active."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        run_id = _insert_dq_run(cur, "detail_test_ds")
        cur.execute(
            "INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type) "
            "VALUES (%s, 'freshness', 'field_name')",
            (run_id,),
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_metric_detail_active WHERE dq_run_id = %s",
            (run_id,),
        )
        count = cur.fetchone()[0]
        assert count == 1, (
            f"Expected 1 active row in v_dq_metric_detail_active for run_id={run_id}, got {count}. "
            f"View must filter on expiry_date = '{EXPIRY_SENTINEL}'"
        )

    def test_expired_record_excluded(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Expired metric_detail row must NOT appear in v_dq_metric_detail_active."""
        cur = db_conn.cursor()
        run_id = _insert_dq_run(cur, "detail_test_ds_expired")
        cur.execute(
            "INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, expiry_date) "
            "VALUES (%s, 'freshness', 'field_name', NOW())",
            (run_id,),
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_metric_detail_active WHERE dq_run_id = %s",
            (run_id,),
        )
        count = cur.fetchone()[0]
        assert count == 0, (
            f"Expected 0 rows in v_dq_metric_detail_active for expired run_id={run_id}, got {count}. "
            "View must exclude rows where expiry_date != EXPIRY_SENTINEL"
        )


# ---------------------------------------------------------------------------
# AC2 + AC4: v_check_config_active
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestCheckConfigActiveView:
    """AC2+AC4: v_check_config_active filters on EXPIRY_SENTINEL."""

    def test_active_record_visible(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Active check_config row must appear in v_check_config_active."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO check_config (dataset_pattern, check_type) "
            "VALUES ('sales_*', 'volume')"
        )
        cur.execute(
            "SELECT count(*) FROM v_check_config_active WHERE dataset_pattern = 'sales_*'"
        )
        count = cur.fetchone()[0]
        assert count == 1, (
            f"Expected 1 active row in v_check_config_active for 'sales_*', got {count}. "
            f"View must filter on expiry_date = '{EXPIRY_SENTINEL}'"
        )

    def test_expired_record_excluded(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Expired check_config row must NOT appear in v_check_config_active."""
        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO check_config (dataset_pattern, check_type, expiry_date) "
            "VALUES ('expired_pattern_*', 'freshness', NOW())"
        )
        cur.execute(
            "SELECT count(*) FROM v_check_config_active WHERE dataset_pattern = 'expired_pattern_*'"
        )
        count = cur.fetchone()[0]
        assert count == 0, (
            f"Expected 0 rows in v_check_config_active for expired 'expired_pattern_*', got {count}. "
            "View must exclude rows where expiry_date != EXPIRY_SENTINEL"
        )


# ---------------------------------------------------------------------------
# AC2 + AC4: v_dataset_enrichment_active
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDatasetEnrichmentActiveView:
    """AC2+AC4: v_dataset_enrichment_active filters on EXPIRY_SENTINEL."""

    def test_active_record_visible(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Active dataset_enrichment row must appear in v_dataset_enrichment_active."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dataset_enrichment (dataset_pattern) VALUES ('inventory_*')"
        )
        cur.execute(
            "SELECT count(*) FROM v_dataset_enrichment_active WHERE dataset_pattern = 'inventory_*'"
        )
        count = cur.fetchone()[0]
        assert count == 1, (
            f"Expected 1 active row in v_dataset_enrichment_active for 'inventory_*', got {count}. "
            f"View must filter on expiry_date = '{EXPIRY_SENTINEL}'"
        )

    def test_expired_record_excluded(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Expired dataset_enrichment row must NOT appear in v_dataset_enrichment_active."""
        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dataset_enrichment (dataset_pattern, expiry_date) "
            "VALUES ('expired_enrich_*', NOW())"
        )
        cur.execute(
            "SELECT count(*) FROM v_dataset_enrichment_active WHERE dataset_pattern = 'expired_enrich_*'"
        )
        count = cur.fetchone()[0]
        assert count == 0, (
            f"Expected 0 rows in v_dataset_enrichment_active for expired 'expired_enrich_*', got {count}. "
            "View must exclude rows where expiry_date != EXPIRY_SENTINEL"
        )


# ---------------------------------------------------------------------------
# AC2 + AC4: v_dq_orchestration_run_active
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDqOrchestrationRunActiveView:
    """AC2+AC4: v_dq_orchestration_run_active filters on EXPIRY_SENTINEL."""

    def test_active_record_visible(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Active dq_orchestration_run row must appear in v_dq_orchestration_run_active."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dq_orchestration_run (parent_path, run_status) "
            "VALUES ('/data/consumer/sales', 'RUNNING')"
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_orchestration_run_active "
            "WHERE parent_path = '/data/consumer/sales'"
        )
        count = cur.fetchone()[0]
        assert count == 1, (
            f"Expected 1 active row in v_dq_orchestration_run_active for '/data/consumer/sales', got {count}. "
            f"View must filter on expiry_date = '{EXPIRY_SENTINEL}'"
        )

    def test_expired_record_excluded(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Expired dq_orchestration_run row must NOT appear in v_dq_orchestration_run_active."""
        cur = db_conn.cursor()
        cur.execute(
            "INSERT INTO dq_orchestration_run (parent_path, run_status, expiry_date) "
            "VALUES ('/data/consumer/expired', 'SUCCESS', NOW())"
        )
        cur.execute(
            "SELECT count(*) FROM v_dq_orchestration_run_active "
            "WHERE parent_path = '/data/consumer/expired'"
        )
        count = cur.fetchone()[0]
        assert count == 0, (
            f"Expected 0 rows in v_dq_orchestration_run_active for expired '/data/consumer/expired', got {count}. "
            "View must exclude rows where expiry_date != EXPIRY_SENTINEL"
        )


# ---------------------------------------------------------------------------
# AC3: All 6 composite indexes exist in pg_indexes
# ---------------------------------------------------------------------------


@pytest.mark.integration
@pytest.mark.parametrize(
    "index_name",
    [
        "idx_dq_run_dataset_name_partition_date",
        "idx_dq_metric_numeric_dq_run_id",
        "idx_dq_metric_detail_dq_run_id",
        "idx_check_config_dataset_pattern",
        "idx_dataset_enrichment_dataset_pattern",
        "idx_dq_orchestration_run_parent_path",
    ],
)
class TestCompositeIndexesExist:
    """AC3: Each composite index must exist in pg_indexes (public schema) after DDL is applied."""

    def test_index_exists(
        self,
        db_conn: "psycopg2.connection",  # type: ignore[name-defined]
        index_name: str,
    ) -> None:
        """AC3 [P0]: Index must exist in pg_indexes for the public schema."""
        cur = db_conn.cursor()
        cur.execute(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname = %s",
            (index_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Index '{index_name}' not found in pg_indexes — "
            "append the CREATE INDEX statement to dqs-serve/src/serve/schema/ddl.sql"
        )
