"""Acceptance tests for Story 1-7: Create Test Data Fixtures.

Validates that fixtures.sql loads cleanly and populates the schema with:
  - AC1: 3+ mock source systems covering Avro and Parquet formats
  - AC2: Mixed anomaly rows (stale partition, zero rows, schema drift, high nulls)
  - AC3: Legacy omni path exercising dataset_enrichment lookup resolution
  - AC4: eventAttribute JSONB values covering all 6 JSON literal types
  - AC5: 7-day historical baseline in dq_metric_numeric for statistical checks
  - AC6: check_config and dataset_enrichment populated with sample rows

All tests use the local db_with_fixtures fixture — do NOT use db_conn.
Do NOT modify tests/conftest.py.

Run: cd dqs-serve && uv run pytest tests/test_schema/test_fixtures.py -m integration -v
"""

from __future__ import annotations

import pathlib
from typing import TYPE_CHECKING

import pytest
from serve.db.models import EXPIRY_SENTINEL

if TYPE_CHECKING:
    import psycopg2

# ---------------------------------------------------------------------------
# Path constants — resolved relative to this file
# ---------------------------------------------------------------------------

_SCHEMA_DIR = pathlib.Path(__file__).parent.parent.parent / "src" / "serve" / "schema"
_DDL_PATH = _SCHEMA_DIR / "ddl.sql"
_VIEWS_PATH = _SCHEMA_DIR / "views.sql"
_FIXTURES_PATH = _SCHEMA_DIR / "fixtures.sql"


# ---------------------------------------------------------------------------
# Local fixture: full schema + fixtures in a single rolled-back transaction
# Does NOT modify conftest.py — isolated to this test module
# ---------------------------------------------------------------------------


@pytest.fixture
def db_with_fixtures() -> "psycopg2.extensions.connection":
    """Real Postgres connection with full schema (DDL + views + fixtures) loaded.

    Loads ddl.sql, views.sql, and fixtures.sql inside a single transaction
    that is rolled back on teardown, leaving the database clean.

    Requires a running Postgres instance.  All tests using this fixture
    must be marked @pytest.mark.integration.
    """
    import os

    import psycopg2

    database_url = os.getenv(
        "DATABASE_URL",
        "postgresql://postgres:localdev@localhost:5432/postgres",
    )
    conn = psycopg2.connect(database_url)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            # Drop all DQS tables in reverse dependency order before creating.
            # This ensures a clean slate even if a previous test's seeded_client fixture
            # committed DDL+data (seeded_client commits so its app connection can read data).
            cur.execute(
                """
                DROP TABLE IF EXISTS dq_metric_detail CASCADE;
                DROP TABLE IF EXISTS dq_metric_numeric CASCADE;
                DROP TABLE IF EXISTS dq_run CASCADE;
                DROP TABLE IF EXISTS dq_orchestration_run CASCADE;
                DROP TABLE IF EXISTS check_config CASCADE;
                DROP TABLE IF EXISTS dataset_enrichment CASCADE;
                DROP VIEW IF EXISTS v_dq_run_active CASCADE;
                DROP VIEW IF EXISTS v_dq_metric_numeric_active CASCADE;
                DROP VIEW IF EXISTS v_dq_metric_detail_active CASCADE;
                DROP VIEW IF EXISTS v_check_config_active CASCADE;
                DROP VIEW IF EXISTS v_dataset_enrichment_active CASCADE;
                DROP VIEW IF EXISTS v_dq_orchestration_run_active CASCADE;
                """
            )
            cur.execute(_DDL_PATH.read_text())
            cur.execute(_VIEWS_PATH.read_text())
            cur.execute(_FIXTURES_PATH.read_text())
        yield conn
    finally:
        conn.rollback()
        conn.close()


# ---------------------------------------------------------------------------
# AC1: 3+ source systems with both Avro and Parquet format coverage
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestFixturesSourceSystems:
    """AC1: at least 3 distinct source systems exist; Avro and Parquet represented."""

    def test_at_least_3_distinct_source_systems(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC1 [P0]: v_dq_run_active must contain at least 3 distinct dataset_names."""
        with db_with_fixtures.cursor() as cur:
            cur.execute("SELECT COUNT(DISTINCT dataset_name) FROM v_dq_run_active")
            count: int = cur.fetchone()[0]
        assert count >= 3, (
            f"Expected >= 3 distinct dataset_names in v_dq_run_active, got {count}. "
            "fixtures.sql must include runs for alpha, beta, and omni source systems."
        )

    def test_parquet_format_datasets_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC1 [P0]: Parquet-format datasets (lob=retail/src_sys_nm=alpha) must appear in v_dq_run_active."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dq_run_active WHERE dataset_name LIKE 'lob=retail/%'"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 Parquet-format (lob=retail/src_sys_nm=alpha) run, got {count}."
        )

    def test_avro_format_datasets_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC1 [P0]: Avro-format datasets (lob=commercial/src_sys_nm=beta) must appear in v_dq_run_active."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dq_run_active WHERE dataset_name LIKE 'lob=commercial/%'"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 Avro-format (lob=commercial/src_sys_nm=beta) run, got {count}."
        )

    def test_active_rows_carry_expiry_sentinel(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC1 [P1]: All rows returned by v_dq_run_active must have expiry_date = EXPIRY_SENTINEL."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dq_run_active WHERE expiry_date::TEXT != %s",
                (EXPIRY_SENTINEL,),
            )
            bad_count: int = cur.fetchone()[0]
        assert bad_count == 0, (
            f"Found {bad_count} rows in v_dq_run_active with expiry_date != EXPIRY_SENTINEL. "
            "All fixture rows must use the sentinel value for active records."
        )


# ---------------------------------------------------------------------------
# AC2: Mixed anomaly rows present
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestFixturesAnomalies:
    """AC2: stale partition, zero rows, schema drift, and high nulls are all represented."""

    def test_stale_partition_anomaly_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC2 [P0]: A FRESHNESS metric with a high hours_since_update (>= 48) must exist (stale partition)."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_numeric_active
                WHERE check_type = 'FRESHNESS'
                  AND metric_name = 'hours_since_update'
                  AND metric_value >= 48
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 stale-partition row (FRESHNESS hours_since_update >= 48), got {count}. "
            "fixtures.sql must include a FRESHNESS metric with value >= 48."
        )

    def test_zero_row_anomaly_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC2 [P0]: A VOLUME row_count = 0 metric must exist (zero-row dataset)."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_numeric_active
                WHERE check_type = 'VOLUME'
                  AND metric_name = 'row_count'
                  AND metric_value = 0
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 zero-row anomaly (VOLUME row_count = 0), got {count}."
        )

    def test_schema_drift_anomaly_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC2 [P0]: A SCHEMA missing_columns metric > 0 must exist (schema drift)."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_numeric_active
                WHERE check_type = 'SCHEMA'
                  AND metric_name = 'missing_columns'
                  AND metric_value > 0
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 schema-drift row (SCHEMA missing_columns > 0), got {count}."
        )

    def test_high_null_rate_anomaly_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC2 [P0]: An OPS null_rate metric >= 0.75 must exist (high-null dataset)."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_numeric_active
                WHERE check_type = 'OPS'
                  AND metric_name = 'null_rate'
                  AND metric_value >= 0.75
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 high-null row (OPS null_rate >= 0.75), got {count}."
        )

    def test_warn_status_runs_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC2 [P1]: At least one dq_run with check_status = 'WARN' must exist."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dq_run_active WHERE check_status = 'WARN'"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 WARN-status run, got {count}."
        )

    def test_fail_status_runs_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC2 [P1]: At least one dq_run with check_status = 'FAIL' must exist."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dq_run_active WHERE check_status = 'FAIL'"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 FAIL-status run, got {count}."
        )


# ---------------------------------------------------------------------------
# AC3: Legacy omni path exercises dataset_enrichment lookup resolution
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestFixturesLegacyPath:
    """AC3: src_sys_nm=omni legacy path present in both dq_run and dataset_enrichment."""

    def test_legacy_path_in_dq_run_active(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC3 [P0]: v_dq_run_active must contain at least 1 row with the legacy omni path."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dq_run_active WHERE dataset_name LIKE 'src_sys_nm=omni/%'"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 legacy omni run in v_dq_run_active, got {count}. "
            "The omni path must not have a 'lob=' prefix."
        )

    def test_legacy_path_in_dataset_enrichment_active(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC3 [P0]: v_dataset_enrichment_active must contain the src_sys_nm=omni/% pattern."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dataset_enrichment_active WHERE dataset_pattern LIKE 'src_sys_nm=omni/%'"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 legacy omni enrichment row in v_dataset_enrichment_active, got {count}. "
            "fixtures.sql must include dataset_enrichment row with dataset_pattern = 'src_sys_nm=omni/%'."
        )

    def test_legacy_enrichment_has_lookup_code(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC3 [P1]: The omni enrichment row must have a non-null lookup_code."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT lookup_code
                FROM v_dataset_enrichment_active
                WHERE dataset_pattern LIKE 'src_sys_nm=omni/%'
                LIMIT 1
                """
            )
            row = cur.fetchone()
        assert row is not None, "No omni enrichment row found."
        assert row[0] is not None, "lookup_code is NULL for the omni enrichment row."
        assert len(row[0]) > 0, "lookup_code is empty for the omni enrichment row."


# ---------------------------------------------------------------------------
# AC4: eventAttribute JSONB values cover all 6 JSON literal types
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestFixturesEventAttributeTypes:
    """AC4: dq_metric_detail contains JSONB detail_value for all 6 JSON literal types."""

    def test_all_json_literal_types_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC4 [P0]: All 6 JSON literal types must appear in v_dq_metric_detail_active.

        psycopg2 automatically deserialises JSONB to native Python types:
          - JSON string  → Python str
          - JSON number  → Python int or float
          - JSON boolean → Python bool
          - JSON array   → Python list
          - JSON object  → Python dict
          - Nested       → Python dict whose values contain list or dict
        """
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT detail_value
                FROM v_dq_metric_detail_active
                WHERE detail_type LIKE 'eventAttribute%'
                """
            )
            rows = cur.fetchall()
        assert rows, "No eventAttribute rows found in v_dq_metric_detail_active."

        types_seen: set[str] = set()
        for (value,) in rows:
            # psycopg2 deserialises JSONB → native Python type
            # bool must be checked before int/float since bool is a subclass of int
            if isinstance(value, bool):
                types_seen.add("boolean")
            elif isinstance(value, str):
                types_seen.add("string")
            elif isinstance(value, (int, float)):
                types_seen.add("number")
            elif isinstance(value, list):
                types_seen.add("array")
            elif isinstance(value, dict):
                types_seen.add("object")
                # Nested = object whose values include a list or another dict
                if any(isinstance(v, (list, dict)) for v in value.values()):
                    types_seen.add("nested")

        expected = {"string", "number", "boolean", "array", "object", "nested"}
        missing = expected - types_seen
        assert not missing, (
            f"Missing JSON literal types in eventAttribute detail rows: {missing}. "
            f"Types found: {types_seen}. "
            "fixtures.sql must include rows for all 6 JSONB literal types."
        )

    def test_string_type_detail_value_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC4 [P1]: At least one eventAttribute row must have a JSON string value."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_detail_active
                WHERE detail_type LIKE 'eventAttribute%'
                  AND jsonb_typeof(detail_value) = 'string'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, f"No JSON string eventAttribute rows found, got {count}."

    def test_number_type_detail_value_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC4 [P1]: At least one eventAttribute row must have a JSON number value."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_detail_active
                WHERE detail_type LIKE 'eventAttribute%'
                  AND jsonb_typeof(detail_value) = 'number'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, f"No JSON number eventAttribute rows found, got {count}."

    def test_boolean_type_detail_value_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC4 [P1]: At least one eventAttribute row must have a JSON boolean value."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_detail_active
                WHERE detail_type LIKE 'eventAttribute%'
                  AND jsonb_typeof(detail_value) = 'boolean'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, f"No JSON boolean eventAttribute rows found, got {count}."

    def test_array_type_detail_value_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC4 [P1]: At least one eventAttribute row must have a JSON array value."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_detail_active
                WHERE detail_type LIKE 'eventAttribute%'
                  AND jsonb_typeof(detail_value) = 'array'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, f"No JSON array eventAttribute rows found, got {count}."

    def test_object_type_detail_value_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC4 [P1]: At least one eventAttribute row must have a JSON object value."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_detail_active
                WHERE detail_type LIKE 'eventAttribute%'
                  AND jsonb_typeof(detail_value) = 'object'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, f"No JSON object eventAttribute rows found, got {count}."

    def test_nested_object_with_array_present(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC4 [P1]: At least one eventAttribute row must be an object containing a nested array."""
        with db_with_fixtures.cursor() as cur:
            # An object that contains a 'fields' key whose value is an array
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dq_metric_detail_active
                WHERE detail_type LIKE 'eventAttribute%'
                  AND jsonb_typeof(detail_value) = 'object'
                  AND jsonb_typeof(detail_value -> 'fields') = 'array'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"No nested (object containing array) eventAttribute rows found, got {count}. "
            "fixtures.sql must include a JSONB object with a nested array (e.g. {'fields': [...]})."
        )


# ---------------------------------------------------------------------------
# AC5: 7-day historical baseline in dq_metric_numeric
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestFixturesHistoricalBaseline:
    """AC5: dq_metric_numeric contains at least 7 distinct partition_dates for the same check."""

    def test_seven_day_history_for_volume_check(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC5 [P0]: v_dq_metric_numeric_active must have >= 7 distinct partition_dates for
        VOLUME/row_count on the sales_daily dataset."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(DISTINCT r.partition_date)
                FROM v_dq_metric_numeric_active m
                JOIN v_dq_run_active r ON r.id = m.dq_run_id
                WHERE m.check_type = 'VOLUME'
                  AND m.metric_name = 'row_count'
                  AND r.dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 7, (
            f"Expected >= 7 distinct partition_dates for VOLUME/row_count on sales_daily, got {count}. "
            "fixtures.sql must include 7 dq_run rows with consecutive partition_dates plus "
            "one dq_metric_numeric row per run."
        )

    def test_historical_partition_dates_are_sequential(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC5 [P1]: The 7 partition_dates must span at least 6 consecutive calendar days."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT MAX(r.partition_date) - MIN(r.partition_date)
                FROM v_dq_metric_numeric_active m
                JOIN v_dq_run_active r ON r.id = m.dq_run_id
                WHERE m.check_type = 'VOLUME'
                  AND m.metric_name = 'row_count'
                  AND r.dataset_name = 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
                """
            )
            span_days = cur.fetchone()[0]
        assert span_days is not None, "No VOLUME/row_count rows found for sales_daily."
        assert span_days >= 6, (
            f"Partition date span is only {span_days} days — expected >= 6 days to cover 7 dates. "
            "fixtures.sql must use consecutive daily dates (e.g. 2026-03-27 through 2026-04-02)."
        )


# ---------------------------------------------------------------------------
# AC6: check_config and dataset_enrichment populated with sample rows
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestFixturesCheckConfig:
    """AC6: v_check_config_active contains enabled and disabled rows."""

    def test_enabled_check_config_rows_exist(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC6 [P0]: At least one check_config row with enabled = TRUE must exist."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_check_config_active WHERE enabled = TRUE"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 enabled check_config row, got {count}."
        )

    def test_disabled_check_config_rows_exist(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC6 [P0]: At least one check_config row with enabled = FALSE must exist."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_check_config_active WHERE enabled = FALSE"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 disabled check_config row, got {count}."
        )

    def test_wildcard_pattern_check_config_exists(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC6 [P1]: A wildcard dataset_pattern (containing '%') must exist in check_config."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_check_config_active WHERE position('%' IN dataset_pattern) > 0"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 wildcard-pattern (containing '%') check_config row, got {count}."
        )

    def test_nonzero_explosion_level_check_config_exists(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC6 [P1]: At least one check_config row with explosion_level > 0 must exist."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_check_config_active WHERE explosion_level > 0"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 check_config row with explosion_level > 0, got {count}."
        )


@pytest.mark.integration
class TestFixturesDatasetEnrichment:
    """AC6: v_dataset_enrichment_active contains required enrichment rows."""

    def test_dataset_enrichment_rows_exist(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC6 [P0]: At least 2 rows must exist in v_dataset_enrichment_active."""
        with db_with_fixtures.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM v_dataset_enrichment_active")
            count: int = cur.fetchone()[0]
        assert count >= 2, (
            f"Expected >= 2 dataset_enrichment rows, got {count}."
        )

    def test_enrichment_with_custom_weights_jsonb_exists(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC6 [P1]: At least one enrichment row must have a non-null JSONB custom_weights value."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM v_dataset_enrichment_active WHERE custom_weights IS NOT NULL"
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 dataset_enrichment row with custom_weights, got {count}."
        )

    def test_enrichment_custom_weights_is_valid_jsonb_object(
        self, db_with_fixtures: "psycopg2.extensions.connection"
    ) -> None:
        """AC6 [P1]: The custom_weights column must store a JSONB object (not null, not array)."""
        with db_with_fixtures.cursor() as cur:
            cur.execute(
                """
                SELECT COUNT(*)
                FROM v_dataset_enrichment_active
                WHERE custom_weights IS NOT NULL
                  AND jsonb_typeof(custom_weights) = 'object'
                """
            )
            count: int = cur.fetchone()[0]
        assert count >= 1, (
            f"Expected >= 1 enrichment row where custom_weights is a JSONB object, got {count}."
        )
