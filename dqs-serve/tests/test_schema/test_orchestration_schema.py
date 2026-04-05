"""Acceptance tests for orchestration schema.

Table coverage:
  - dq_orchestration_run (AC1, AC3)
  - dq_run FK to dq_orchestration_run (AC2)

Test categories:
  - Integration tests (real Postgres): verify table, columns, constraints, uniqueness, FK
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
class TestDqOrchestrationRunTableExists:
    """AC1: dq_orchestration_run table exists in public schema after DDL is applied."""

    def test_dq_orchestration_run_table_exists(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: dq_orchestration_run must exist in the public schema."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = 'dq_orchestration_run'
            )
            """
        )
        exists = cur.fetchone()[0]
        assert exists, (
            "Table 'dq_orchestration_run' does not exist — prepend CREATE TABLE "
            "dq_orchestration_run to dqs-serve/src/serve/schema/ddl.sql (before dq_run)"
        )

    def test_dq_orchestration_run_id_is_serial_pk(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: dq_orchestration_run.id must be SERIAL (integer) PRIMARY KEY."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_orchestration_run'
            AND column_name = 'id'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'id' not found in dq_orchestration_run"
        assert row[0] == "integer", (
            f"Expected 'id' to be integer (SERIAL), got '{row[0]}'"
        )

        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'dq_orchestration_run'
            AND tc.constraint_type = 'PRIMARY KEY'
            AND kcu.column_name = 'id'
            """
        )
        pk_row = cur.fetchone()
        assert pk_row is not None, (
            "Column 'id' is not the PRIMARY KEY of dq_orchestration_run"
        )

    @pytest.mark.parametrize(
        "column_name,expected_data_type,is_nullable",
        [
            ("id", "integer", "NO"),
            ("parent_path", "text", "NO"),
            ("run_status", "text", "NO"),
            ("start_time", "timestamp without time zone", "YES"),
            ("end_time", "timestamp without time zone", "YES"),
            ("total_datasets", "integer", "YES"),
            ("passed_datasets", "integer", "YES"),
            ("failed_datasets", "integer", "YES"),
            ("error_summary", "text", "YES"),
            ("create_date", "timestamp without time zone", "NO"),
            ("expiry_date", "timestamp without time zone", "NO"),
        ],
    )
    def test_dq_orchestration_run_column_exists_with_correct_type(
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
            AND table_name = 'dq_orchestration_run'
            AND column_name = %s
            """,
            (column_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Column '{column_name}' not found in dq_orchestration_run — "
            "check ddl.sql for missing column definition"
        )
        actual_data_type, actual_nullable = row
        assert actual_data_type == expected_data_type, (
            f"Column '{column_name}': expected type '{expected_data_type}', got '{actual_data_type}'"
        )
        assert actual_nullable == is_nullable, (
            f"Column '{column_name}': expected nullable='{is_nullable}', got '{actual_nullable}'"
        )

    def test_dq_orchestration_run_expiry_date_default_is_sentinel(
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
            AND table_name = 'dq_orchestration_run'
            AND column_name = 'expiry_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'expiry_date' not found in dq_orchestration_run"
        default_val = row[0]
        assert default_val is not None, (
            f"expiry_date has no DEFAULT — must DEFAULT to '{EXPIRY_SENTINEL}'"
        )
        assert EXPIRY_SENTINEL in str(default_val), (
            f"expiry_date DEFAULT '{default_val}' does not contain '{EXPIRY_SENTINEL}'"
        )

    def test_dq_orchestration_run_create_date_default_is_now(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: create_date must DEFAULT to NOW()."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_orchestration_run'
            AND column_name = 'create_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'create_date' not found in dq_orchestration_run"
        default_val = row[0]
        assert default_val is not None, (
            "create_date has no DEFAULT — must DEFAULT to NOW()"
        )
        assert "now" in str(default_val).lower(), (
            f"create_date DEFAULT '{default_val}' does not reference NOW()"
        )


@pytest.mark.integration
class TestDqOrchestrationRunUniqueConstraint:
    """AC1/AC3: dq_orchestration_run unique constraint on (parent_path, expiry_date)."""

    CONSTRAINT_NAME = "uq_dq_orchestration_run_parent_path_expiry_date"

    def test_dq_orchestration_run_unique_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: Unique constraint must exist with exact prescribed name."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND table_name = 'dq_orchestration_run'
            AND constraint_type = 'UNIQUE'
            AND constraint_name = %s
            """,
            (self.CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Unique constraint '{self.CONSTRAINT_NAME}' not found on dq_orchestration_run — "
            "add CONSTRAINT uq_dq_orchestration_run_parent_path_expiry_date "
            "UNIQUE (parent_path, expiry_date) in ddl.sql"
        )

    def test_dq_orchestration_run_unique_constraint_covers_correct_columns(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: Unique constraint must cover exactly (parent_path, expiry_date)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.key_column_usage kcu
            JOIN information_schema.table_constraints tc
                ON kcu.constraint_name = tc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_orchestration_run'
            AND tc.constraint_name = %s
            ORDER BY kcu.ordinal_position
            """,
            (self.CONSTRAINT_NAME,),
        )
        columns = [row[0] for row in cur.fetchall()]
        expected_columns = ["parent_path", "expiry_date"]
        assert columns == expected_columns, (
            f"Unique constraint columns {columns} != {expected_columns} — "
            "the constraint must be on exactly (parent_path, expiry_date) in that order"
        )

    def test_dq_orchestration_run_rejects_duplicate_active_parent_path(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P1]: Inserting two active dq_orchestration_run rows with same
        parent_path must raise UniqueViolation.

        Both rows share expiry_date = sentinel (active), so the composite
        constraint fires.
        """
        import psycopg2  # noqa: PLC0415

        cur = db_conn.cursor()

        # Insert first active row — should succeed
        # Both rows rely on the DDL DEFAULT for expiry_date (EXPIRY_SENTINEL value)
        cur.execute(
            "INSERT INTO dq_orchestration_run (parent_path, run_status) "
            "VALUES ('/data/consumer/sales', 'RUNNING')"
        )

        # Insert duplicate active row with same parent_path — must raise UniqueViolation
        with pytest.raises(psycopg2.errors.UniqueViolation):
            cur.execute(
                "INSERT INTO dq_orchestration_run (parent_path, run_status) "
                "VALUES ('/data/consumer/sales', 'SUCCESS')"
            )


@pytest.mark.integration
class TestDqOrchestrationRunFunctional:
    """AC3: dq_orchestration_run supports tracking multiple parent paths per run."""

    def test_dq_orchestration_run_different_parent_paths_can_be_inserted(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P1]: Two active dq_orchestration_run rows with different parent_paths
        must both be accepted — one row per parent-path invocation.
        """
        cur = db_conn.cursor()

        cur.execute(
            "INSERT INTO dq_orchestration_run (parent_path, run_status) "
            "VALUES ('/data/consumer/sales', 'RUNNING') RETURNING id"
        )
        row1 = cur.fetchone()
        assert row1 is not None, "First INSERT into dq_orchestration_run returned no row"

        cur.execute(
            "INSERT INTO dq_orchestration_run (parent_path, run_status) "
            "VALUES ('/data/consumer/inventory', 'RUNNING') RETURNING id"
        )
        row2 = cur.fetchone()
        assert row2 is not None, "Second INSERT into dq_orchestration_run returned no row"

        assert row1[0] != row2[0], "Two inserted rows must have distinct ids"

    def test_dq_orchestration_run_nullable_columns_accept_null(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P1]: A minimal row with only required NOT NULL columns must be accepted.

        Validates that start_time, end_time, total_datasets, passed_datasets,
        failed_datasets, and error_summary are all nullable — they are populated
        as the run progresses, not at row creation time.
        """
        cur = db_conn.cursor()
        cur.execute(
            """
            INSERT INTO dq_orchestration_run (parent_path, run_status)
            VALUES ('/data/consumer/minimal', 'RUNNING')
            RETURNING id, start_time, end_time, total_datasets, passed_datasets,
                      failed_datasets, error_summary
            """
        )
        row = cur.fetchone()
        assert row is not None, (
            "Minimal INSERT into dq_orchestration_run (only required columns) returned no row"
        )
        inserted_id, start_time, end_time, total_datasets, passed_datasets, failed_datasets, error_summary = row
        assert inserted_id is not None, "inserted id should not be NULL"
        assert start_time is None, f"start_time must be NULL when not provided, got '{start_time}'"
        assert end_time is None, f"end_time must be NULL when not provided, got '{end_time}'"
        assert total_datasets is None, f"total_datasets must be NULL when not provided, got '{total_datasets}'"
        assert passed_datasets is None, f"passed_datasets must be NULL when not provided, got '{passed_datasets}'"
        assert failed_datasets is None, f"failed_datasets must be NULL when not provided, got '{failed_datasets}'"
        assert error_summary is None, f"error_summary must be NULL when not provided, got '{error_summary}'"


@pytest.mark.integration
class TestDqRunOrchestrationRunForeignKey:
    """AC2: dq_run.orchestration_run_id FK constraint fk_dq_run_orchestration_run exists."""

    FK_CONSTRAINT_NAME = "fk_dq_run_orchestration_run"

    def test_dq_run_fk_orchestration_run_exists(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: FK constraint 'fk_dq_run_orchestration_run' must exist in
        information_schema.referential_constraints.
        """
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT constraint_name
            FROM information_schema.referential_constraints
            WHERE constraint_schema = 'public'
            AND constraint_name = %s
            """,
            (self.FK_CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"FK constraint '{self.FK_CONSTRAINT_NAME}' not found — "
            "add ALTER TABLE dq_run ADD CONSTRAINT fk_dq_run_orchestration_run "
            "FOREIGN KEY (orchestration_run_id) REFERENCES dq_orchestration_run(id) "
            "in ddl.sql after the dq_run CREATE TABLE + index"
        )

    def test_dq_run_fk_references_dq_orchestration_run(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: FK constraint must reference the dq_orchestration_run table."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT rc.constraint_name, ccu.table_name AS referenced_table
            FROM information_schema.referential_constraints rc
            JOIN information_schema.constraint_column_usage ccu
                ON rc.unique_constraint_name = ccu.constraint_name
            WHERE rc.constraint_schema = 'public'
            AND rc.constraint_name = %s
            """,
            (self.FK_CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"FK constraint '{self.FK_CONSTRAINT_NAME}' not found in referential_constraints"
        )
        referenced_table = row[1]
        assert referenced_table == "dq_orchestration_run", (
            f"FK references '{referenced_table}', expected 'dq_orchestration_run'"
        )

    def test_dq_run_rejects_invalid_orchestration_run_id(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P1]: Inserting a dq_run row referencing a non-existent
        orchestration_run_id must raise ForeignKeyViolation.
        """
        import psycopg2  # noqa: PLC0415

        cur = db_conn.cursor()
        with pytest.raises(psycopg2.errors.ForeignKeyViolation):
            cur.execute(
                """
                INSERT INTO dq_run (dataset_name, partition_date, check_status, orchestration_run_id)
                VALUES ('sales_daily', '2026-01-01', 'PASS', 99999)
                """
            )

    def test_dq_run_accepts_null_orchestration_run_id(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P1]: Inserting a dq_run row with orchestration_run_id = NULL must
        succeed — the FK is nullable; not all runs have an orchestration parent.
        """
        cur = db_conn.cursor()
        cur.execute(
            """
            INSERT INTO dq_run (dataset_name, partition_date, check_status)
            VALUES ('sales_daily', '2026-01-01', 'PASS')
            RETURNING id, orchestration_run_id
            """
        )
        row = cur.fetchone()
        assert row is not None, (
            "INSERT into dq_run without orchestration_run_id returned no row"
        )
        inserted_id, orchestration_run_id = row
        assert inserted_id is not None, "inserted id should not be NULL"
        assert orchestration_run_id is None, (
            f"orchestration_run_id must be NULL when not provided, got '{orchestration_run_id}'"
        )

    def test_dq_run_accepts_valid_orchestration_run_id(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P1]: Inserting a dq_run row referencing an existing
        orchestration_run_id must succeed — FK allows valid references.
        """
        cur = db_conn.cursor()

        # Create a valid orchestration run first
        cur.execute(
            "INSERT INTO dq_orchestration_run (parent_path, run_status) "
            "VALUES ('/data/consumer/sales', 'RUNNING') RETURNING id"
        )
        orch_row = cur.fetchone()
        assert orch_row is not None, "Failed to insert dq_orchestration_run for FK test"
        orch_id = orch_row[0]

        # Now insert a dq_run row referencing it
        cur.execute(
            """
            INSERT INTO dq_run (dataset_name, partition_date, check_status, orchestration_run_id)
            VALUES ('sales_daily', '2026-01-01', 'PASS', %s)
            RETURNING id, orchestration_run_id
            """,
            (orch_id,),
        )
        run_row = cur.fetchone()
        assert run_row is not None, (
            "INSERT into dq_run with valid orchestration_run_id returned no row"
        )
        inserted_id, linked_orch_id = run_row
        assert inserted_id is not None, "inserted id should not be NULL"
        assert linked_orch_id == orch_id, (
            f"Expected orchestration_run_id={orch_id}, got {linked_orch_id}"
        )
