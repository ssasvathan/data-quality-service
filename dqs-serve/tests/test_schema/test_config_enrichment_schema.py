"""Acceptance tests for Story 1-4: Configuration & Enrichment Schema.

Table coverage:
  - check_config      (AC1, AC3, AC5)
  - dataset_enrichment (AC2, AC4, AC6)

Test categories:
  - Integration tests (real Postgres): verify tables, columns, constraints, uniqueness
    All tests in this file are @pytest.mark.integration.

Run integration tests: cd dqs-serve && uv run pytest -m integration -v
Default suite (no -m flag) DESELECTS these via pyproject.toml addopts.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest

if TYPE_CHECKING:
    import psycopg2

# ---------------------------------------------------------------------------
# Integration tests — require a running Postgres instance
# Marked @pytest.mark.integration — deselected from default suite per pyproject.toml
# Run with: cd dqs-serve && uv run pytest -m integration -v
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestCheckConfigTableExists:
    """AC1: check_config table exists in public schema after DDL is applied."""

    def test_check_config_table_exists(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: check_config must exist in the public schema."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = 'check_config'
            )
            """
        )
        exists = cur.fetchone()[0]
        assert exists, (
            "Table 'check_config' does not exist — append CREATE TABLE check_config "
            "to dqs-serve/src/serve/schema/ddl.sql"
        )

    def test_check_config_id_is_serial_pk(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: check_config.id must be SERIAL (integer) PRIMARY KEY."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'check_config'
            AND column_name = 'id'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'id' not found in check_config"
        assert row[0] == "integer", (
            f"Expected 'id' to be integer (SERIAL), got '{row[0]}'"
        )

        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'check_config'
            AND tc.constraint_type = 'PRIMARY KEY'
            AND kcu.column_name = 'id'
            """
        )
        pk_row = cur.fetchone()
        assert pk_row is not None, "Column 'id' is not the PRIMARY KEY of check_config"

    @pytest.mark.parametrize(
        "column_name,expected_data_type,is_nullable",
        [
            ("id", "integer", "NO"),
            ("dataset_pattern", "text", "NO"),
            ("check_type", "text", "NO"),
            ("enabled", "boolean", "NO"),
            ("explosion_level", "integer", "NO"),
            ("create_date", "timestamp without time zone", "NO"),
            ("expiry_date", "timestamp without time zone", "NO"),
        ],
    )
    def test_check_config_column_exists_with_correct_type(
        self,
        db_conn: "psycopg2.connection",  # type: ignore[name-defined]
        column_name: str,
        expected_data_type: str,
        is_nullable: str,
    ) -> None:
        """AC1 [P0]: Each required column must exist with the correct type and nullability."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'check_config'
            AND column_name = %s
            """,
            (column_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Column '{column_name}' not found in check_config — "
            "check ddl.sql for missing column definition"
        )
        actual_data_type, actual_nullable = row
        assert actual_data_type == expected_data_type, (
            f"Column '{column_name}': expected type '{expected_data_type}', got '{actual_data_type}'"
        )
        assert actual_nullable == is_nullable, (
            f"Column '{column_name}': expected nullable='{is_nullable}', got '{actual_nullable}'"
        )

    def test_check_config_expiry_date_default_is_sentinel(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: expiry_date must DEFAULT to EXPIRY_SENTINEL ('9999-12-31 23:59:59')."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'check_config'
            AND column_name = 'expiry_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'expiry_date' not found in check_config"
        default_val = row[0]
        assert default_val is not None, (
            f"expiry_date has no DEFAULT — must DEFAULT to '{EXPIRY_SENTINEL}'"
        )
        assert EXPIRY_SENTINEL in str(default_val), (
            f"expiry_date DEFAULT '{default_val}' does not contain '{EXPIRY_SENTINEL}'"
        )

    def test_check_config_create_date_default_is_now(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: create_date must DEFAULT to NOW()."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'check_config'
            AND column_name = 'create_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'create_date' not found in check_config"
        default_val = row[0]
        assert default_val is not None, "create_date has no DEFAULT — must DEFAULT to NOW()"
        assert "now" in str(default_val).lower(), (
            f"create_date DEFAULT '{default_val}' does not reference NOW()"
        )


@pytest.mark.integration
class TestCheckConfigUniqueConstraint:
    """AC3: check_config unique constraint on (dataset_pattern, check_type, expiry_date)."""

    CONSTRAINT_NAME = "uq_check_config_dataset_pattern_check_type_expiry_date"

    def test_check_config_unique_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P0]: Unique constraint must exist with exact prescribed name."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND table_name = 'check_config'
            AND constraint_type = 'UNIQUE'
            AND constraint_name = %s
            """,
            (self.CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Unique constraint '{self.CONSTRAINT_NAME}' not found on check_config — "
            "add CONSTRAINT uq_check_config_dataset_pattern_check_type_expiry_date "
            "UNIQUE (dataset_pattern, check_type, expiry_date) in ddl.sql"
        )

    def test_check_config_unique_constraint_covers_correct_columns(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P0]: Unique constraint must cover exactly (dataset_pattern, check_type, expiry_date)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.key_column_usage kcu
            JOIN information_schema.table_constraints tc
                ON kcu.constraint_name = tc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'check_config'
            AND tc.constraint_name = %s
            ORDER BY kcu.ordinal_position
            """,
            (self.CONSTRAINT_NAME,),
        )
        columns = [row[0] for row in cur.fetchall()]
        expected_columns = ["dataset_pattern", "check_type", "expiry_date"]
        assert columns == expected_columns, (
            f"Unique constraint columns {columns} != {expected_columns} — "
            "the constraint must be on exactly (dataset_pattern, check_type, expiry_date) "
            "in that order"
        )

    def test_two_active_check_config_rows_same_natural_key_rejected(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P1]: Inserting two active check_config rows with same
        (dataset_pattern, check_type) must raise UniqueViolation.

        Both rows share expiry_date = sentinel (active), so the composite
        constraint fires.
        """
        import psycopg2  # noqa: PLC0415

        cur = db_conn.cursor()

        # Insert first active check_config row — should succeed
        # Both rows rely on the DDL DEFAULT for expiry_date (EXPIRY_SENTINEL value)
        cur.execute(
            "INSERT INTO check_config (dataset_pattern, check_type) VALUES ('sales_*', 'freshness')"
        )

        # Insert duplicate active row — must raise UniqueViolation
        with pytest.raises(psycopg2.errors.UniqueViolation):
            cur.execute(
                "INSERT INTO check_config (dataset_pattern, check_type) VALUES ('sales_*', 'freshness')"
            )


@pytest.mark.integration
class TestCheckConfigFunctional:
    """AC5: check_config row with enabled = FALSE can be inserted."""

    def test_check_config_row_with_enabled_false_can_be_inserted(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC5 [P1]: A check_config row with enabled=FALSE must be accepted by the DB.

        This validates the functional use case: disabling a specific check type
        for a dataset pattern without code changes.
        """
        cur = db_conn.cursor()

        cur.execute(
            """
            INSERT INTO check_config (dataset_pattern, check_type, enabled)
            VALUES ('legacy_*', 'schema', FALSE)
            RETURNING id, dataset_pattern, check_type, enabled
            """
        )
        row = cur.fetchone()
        assert row is not None, (
            "INSERT of check_config row with enabled=FALSE returned no row — unexpected error"
        )
        inserted_id, dataset_pattern, check_type, enabled = row
        assert inserted_id is not None, "inserted id should not be NULL"
        assert dataset_pattern == "legacy_*"
        assert check_type == "schema"
        assert enabled is False, (
            f"Expected enabled=False after insert, got {enabled}"
        )


# ---------------------------------------------------------------------------
# dataset_enrichment table tests
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDatasetEnrichmentTableExists:
    """AC2: dataset_enrichment table exists in public schema after DDL is applied."""

    def test_dataset_enrichment_table_exists(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: dataset_enrichment must exist in the public schema."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = 'dataset_enrichment'
            )
            """
        )
        exists = cur.fetchone()[0]
        assert exists, (
            "Table 'dataset_enrichment' does not exist — append CREATE TABLE dataset_enrichment "
            "to dqs-serve/src/serve/schema/ddl.sql"
        )

    def test_dataset_enrichment_id_is_serial_pk(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: dataset_enrichment.id must be SERIAL (integer) PRIMARY KEY."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dataset_enrichment'
            AND column_name = 'id'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'id' not found in dataset_enrichment"
        assert row[0] == "integer", (
            f"Expected 'id' to be integer (SERIAL), got '{row[0]}'"
        )

        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'dataset_enrichment'
            AND tc.constraint_type = 'PRIMARY KEY'
            AND kcu.column_name = 'id'
            """
        )
        pk_row = cur.fetchone()
        assert pk_row is not None, "Column 'id' is not the PRIMARY KEY of dataset_enrichment"

    @pytest.mark.parametrize(
        "column_name,expected_data_type,is_nullable",
        [
            ("id", "integer", "NO"),
            ("dataset_pattern", "text", "NO"),
            ("lookup_code", "text", "YES"),
            ("custom_weights", "jsonb", "YES"),
            ("sla_hours", "numeric", "YES"),
            ("explosion_level", "integer", "NO"),
            ("create_date", "timestamp without time zone", "NO"),
            ("expiry_date", "timestamp without time zone", "NO"),
        ],
    )
    def test_dataset_enrichment_column_exists_with_correct_type(
        self,
        db_conn: "psycopg2.connection",  # type: ignore[name-defined]
        column_name: str,
        expected_data_type: str,
        is_nullable: str,
    ) -> None:
        """AC2 [P0]: Each required column must exist with the correct type and nullability."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dataset_enrichment'
            AND column_name = %s
            """,
            (column_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Column '{column_name}' not found in dataset_enrichment — "
            "check ddl.sql for missing column definition"
        )
        actual_data_type, actual_nullable = row
        assert actual_data_type == expected_data_type, (
            f"Column '{column_name}': expected type '{expected_data_type}', got '{actual_data_type}'"
        )
        assert actual_nullable == is_nullable, (
            f"Column '{column_name}': expected nullable='{is_nullable}', got '{actual_nullable}'"
        )

    def test_dataset_enrichment_expiry_date_default_is_sentinel(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: expiry_date must DEFAULT to EXPIRY_SENTINEL ('9999-12-31 23:59:59')."""
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dataset_enrichment'
            AND column_name = 'expiry_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'expiry_date' not found in dataset_enrichment"
        default_val = row[0]
        assert default_val is not None, (
            f"expiry_date has no DEFAULT — must DEFAULT to '{EXPIRY_SENTINEL}'"
        )
        assert EXPIRY_SENTINEL in str(default_val), (
            f"expiry_date DEFAULT '{default_val}' does not contain '{EXPIRY_SENTINEL}'"
        )

    def test_dataset_enrichment_create_date_default_is_now(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: create_date must DEFAULT to NOW()."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dataset_enrichment'
            AND column_name = 'create_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'create_date' not found in dataset_enrichment"
        default_val = row[0]
        assert default_val is not None, "create_date has no DEFAULT — must DEFAULT to NOW()"
        assert "now" in str(default_val).lower(), (
            f"create_date DEFAULT '{default_val}' does not reference NOW()"
        )


@pytest.mark.integration
class TestDatasetEnrichmentUniqueConstraint:
    """AC4: dataset_enrichment unique constraint on (dataset_pattern, expiry_date)."""

    CONSTRAINT_NAME = "uq_dataset_enrichment_dataset_pattern_expiry_date"

    def test_dataset_enrichment_unique_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Unique constraint must exist with exact prescribed name."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND table_name = 'dataset_enrichment'
            AND constraint_type = 'UNIQUE'
            AND constraint_name = %s
            """,
            (self.CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Unique constraint '{self.CONSTRAINT_NAME}' not found on dataset_enrichment — "
            "add CONSTRAINT uq_dataset_enrichment_dataset_pattern_expiry_date "
            "UNIQUE (dataset_pattern, expiry_date) in ddl.sql"
        )

    def test_dataset_enrichment_unique_constraint_covers_correct_columns(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Unique constraint must cover exactly (dataset_pattern, expiry_date)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.key_column_usage kcu
            JOIN information_schema.table_constraints tc
                ON kcu.constraint_name = tc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dataset_enrichment'
            AND tc.constraint_name = %s
            ORDER BY kcu.ordinal_position
            """,
            (self.CONSTRAINT_NAME,),
        )
        columns = [row[0] for row in cur.fetchall()]
        expected_columns = ["dataset_pattern", "expiry_date"]
        assert columns == expected_columns, (
            f"Unique constraint columns {columns} != {expected_columns} — "
            "the constraint must be on exactly (dataset_pattern, expiry_date) "
            "in that order"
        )

    def test_two_active_dataset_enrichment_rows_same_dataset_pattern_rejected(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P1]: Inserting two active dataset_enrichment rows with same
        dataset_pattern must raise UniqueViolation.

        Both rows share expiry_date = sentinel (active), so the composite
        constraint fires.
        """
        import psycopg2  # noqa: PLC0415

        cur = db_conn.cursor()

        # Insert first active dataset_enrichment row — should succeed
        # Both rows rely on the DDL DEFAULT for expiry_date (EXPIRY_SENTINEL value)
        cur.execute(
            "INSERT INTO dataset_enrichment (dataset_pattern) VALUES ('sales_*')"
        )

        # Insert duplicate active row — must raise UniqueViolation
        with pytest.raises(psycopg2.errors.UniqueViolation):
            cur.execute(
                "INSERT INTO dataset_enrichment (dataset_pattern) VALUES ('sales_*')"
            )


@pytest.mark.integration
class TestDatasetEnrichmentFunctional:
    """AC6: dataset_enrichment row with custom lookup_code can be inserted."""

    def test_dataset_enrichment_row_with_lookup_code_can_be_inserted(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC6 [P1]: A dataset_enrichment row with a custom lookup_code must be accepted.

        This validates the functional use case: overriding the lookup code for
        a legacy path pattern without code changes.
        """
        cur = db_conn.cursor()

        cur.execute(
            """
            INSERT INTO dataset_enrichment (dataset_pattern, lookup_code)
            VALUES ('/data/legacy/sales/*', 'SALES_LOB')
            RETURNING id, dataset_pattern, lookup_code
            """
        )
        row = cur.fetchone()
        assert row is not None, (
            "INSERT of dataset_enrichment row with lookup_code returned no row — unexpected error"
        )
        inserted_id, dataset_pattern, lookup_code = row
        assert inserted_id is not None, "inserted id should not be NULL"
        assert dataset_pattern == "/data/legacy/sales/*"
        assert lookup_code == "SALES_LOB", (
            f"Expected lookup_code='SALES_LOB' after insert, got '{lookup_code}'"
        )

    def test_dataset_enrichment_partial_row_only_custom_weights_can_be_inserted(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2/AC6 [P1]: A partial dataset_enrichment row with only custom_weights
        (no lookup_code, no sla_hours) must be accepted — nullable columns are intentional.
        """
        cur = db_conn.cursor()

        cur.execute(
            """
            INSERT INTO dataset_enrichment (dataset_pattern, custom_weights)
            VALUES ('inventory_*', '{"freshness": 0.4, "volume": 0.3}')
            RETURNING id, dataset_pattern, lookup_code, custom_weights, sla_hours
            """
        )
        row = cur.fetchone()
        assert row is not None, (
            "INSERT of partial dataset_enrichment row (custom_weights only) returned no row"
        )
        inserted_id, dataset_pattern, lookup_code, custom_weights, sla_hours = row
        assert inserted_id is not None
        assert dataset_pattern == "inventory_*"
        assert lookup_code is None, (
            f"lookup_code must be NULL when not provided, got '{lookup_code}'"
        )
        assert custom_weights == {"freshness": 0.4, "volume": 0.3}, (
            f"custom_weights mismatch: got {custom_weights}"
        )
        assert sla_hours is None, (
            f"sla_hours must be NULL when not provided, got '{sla_hours}'"
        )
