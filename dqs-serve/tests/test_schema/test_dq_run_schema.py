"""Acceptance tests for core dq_run schema with temporal pattern.

Test categories:
  - Structural tests (no DB): verify EXPIRY_SENTINEL constant — run in default suite
  - Integration tests (real Postgres): verify table, columns, constraint — @pytest.mark.integration
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import pytest

if TYPE_CHECKING:
    import psycopg2

# ---------------------------------------------------------------------------
# Structural / unit-level tests — NO database required
# Run in the default pytest suite (not marked integration)
# ---------------------------------------------------------------------------


class TestExpirySentinelConstant:
    """AC3: EXPIRY_SENTINEL constant is defined in dqs-serve Python.

    These tests validate the constant WITHOUT a database connection.
    """

    def test_expiry_sentinel_constant_exists_in_models(self) -> None:
        """AC3 [P1]: EXPIRY_SENTINEL must be importable from serve.db.models."""
        from serve.db import models  # noqa: PLC0415

        assert hasattr(models, "EXPIRY_SENTINEL"), (
            "EXPIRY_SENTINEL constant not found in serve.db.models — "
            "add EXPIRY_SENTINEL: str = '9999-12-31 23:59:59' to models.py"
        )

    def test_expiry_sentinel_has_correct_value(self) -> None:
        """AC3 [P1]: EXPIRY_SENTINEL must equal the canonical sentinel value."""
        from serve.db import models  # noqa: PLC0415

        expected = "9999-12-31 23:59:59"
        assert models.EXPIRY_SENTINEL == expected, (
            f"EXPIRY_SENTINEL value '{models.EXPIRY_SENTINEL}' != '{expected}' — "
            "the sentinel must match the DDL DEFAULT exactly"
        )

    def test_expiry_sentinel_is_string_type(self) -> None:
        """AC3 [P1]: EXPIRY_SENTINEL must be a str (type-hinted constant)."""
        from serve.db import models  # noqa: PLC0415

        assert isinstance(models.EXPIRY_SENTINEL, str), (
            f"EXPIRY_SENTINEL is {type(models.EXPIRY_SENTINEL)}, expected str — "
            "per project-context.md: EXPIRY_SENTINEL: str = '9999-12-31 23:59:59'"
        )

    def test_no_hardcoded_sentinel_in_python_sources(self) -> None:
        """AC3 [P1]: No inline '9999-12-31 23:59:59' in Python source files.

        The only allowed occurrence is the constant definition itself in models.py.
        All other code must reference EXPIRY_SENTINEL.
        """
        import pathlib  # noqa: PLC0415

        project_root = pathlib.Path(__file__).parent.parent.parent
        src_dir = project_root / "src"

        hardcoded_occurrences: list[str] = []
        sentinel_literal = "9999-12-31 23:59:59"

        for py_file in src_dir.rglob("*.py"):
            content = py_file.read_text()
            lines = content.splitlines()
            for line_num, line in enumerate(lines, start=1):
                if sentinel_literal in line:
                    # Allow the definition line itself: `EXPIRY_SENTINEL: str = "9999-12-31 23:59:59"`
                    if "EXPIRY_SENTINEL" in line and ("=" in line or ":" in line):
                        continue
                    hardcoded_occurrences.append(f"{py_file.relative_to(project_root)}:{line_num}: {line.strip()}")

        assert hardcoded_occurrences == [], (
            "Hardcoded '9999-12-31 23:59:59' found in Python source — use EXPIRY_SENTINEL constant:\n"
            + "\n".join(hardcoded_occurrences)
        )


# ---------------------------------------------------------------------------
# Integration tests — require a running Postgres instance
# Marked @pytest.mark.integration — deselected from default suite per pyproject.toml
# Run with: pytest -m integration
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDqRunTableExists:
    """AC1: dq_run table exists with all required columns after DDL is applied."""

    def test_dq_run_table_exists(self, db_conn: "psycopg2.connection") -> None:  # type: ignore[name-defined]
        """AC1 [P0]: dq_run table must exist in the public schema."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = 'dq_run'
            )
            """
        )
        exists = cur.fetchone()[0]
        assert exists, (
            "Table 'dq_run' does not exist — execute dqs-serve/src/serve/schema/ddl.sql "
            "to create it"
        )

    def test_dq_run_has_id_serial_pk(self, db_conn: "psycopg2.connection") -> None:  # type: ignore[name-defined]
        """AC1 [P0]: dq_run.id must be a SERIAL (integer) PRIMARY KEY."""
        cur = db_conn.cursor()
        # Check column type
        cur.execute(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_run'
            AND column_name = 'id'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'id' not found in dq_run"
        assert row[0] == "integer", f"Expected 'id' to be integer (SERIAL), got '{row[0]}'"

        # Check it is the primary key
        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'dq_run'
            AND tc.constraint_type = 'PRIMARY KEY'
            AND kcu.column_name = 'id'
            """
        )
        pk_row = cur.fetchone()
        assert pk_row is not None, "Column 'id' is not the PRIMARY KEY of dq_run"

    @pytest.mark.parametrize(
        "column_name,expected_data_type,is_nullable",
        [
            ("dataset_name", "text", "NO"),
            ("partition_date", "date", "NO"),
            ("lookup_code", "text", "YES"),
            ("check_status", "text", "NO"),
            ("dqs_score", "numeric", "YES"),
            ("rerun_number", "integer", "NO"),
            ("orchestration_run_id", "integer", "YES"),
            ("error_message", "text", "YES"),
            ("create_date", "timestamp without time zone", "NO"),
            ("expiry_date", "timestamp without time zone", "NO"),
        ],
    )
    def test_dq_run_column_exists_with_correct_type(
        self,
        db_conn: "psycopg2.connection",  # type: ignore[name-defined]
        column_name: str,
        expected_data_type: str,
        is_nullable: str,
    ) -> None:
        """AC1 [P0]: Each required column must exist with the correct data type and nullability."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_run'
            AND column_name = %s
            """,
            (column_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Column '{column_name}' not found in dq_run — "
            "check ddl.sql for missing column definition"
        )
        actual_data_type, actual_nullable = row
        assert actual_data_type == expected_data_type, (
            f"Column '{column_name}': expected type '{expected_data_type}', got '{actual_data_type}'"
        )
        assert actual_nullable == is_nullable, (
            f"Column '{column_name}': expected nullable='{is_nullable}', got '{actual_nullable}'"
        )

    def test_dq_run_expiry_date_default_is_sentinel(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: expiry_date must DEFAULT to '9999-12-31 23:59:59'."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_run'
            AND column_name = 'expiry_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'expiry_date' not found in dq_run"
        default_val = row[0]
        assert default_val is not None, "expiry_date has no DEFAULT — must DEFAULT to '9999-12-31 23:59:59'"
        # Postgres stores the default as a cast expression
        assert "9999-12-31 23:59:59" in str(default_val), (
            f"expiry_date DEFAULT '{default_val}' does not contain '9999-12-31 23:59:59'"
        )

    def test_dq_run_create_date_default_is_now(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: create_date must DEFAULT to NOW()."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_run'
            AND column_name = 'create_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'create_date' not found in dq_run"
        default_val = row[0]
        assert default_val is not None, "create_date has no DEFAULT — must DEFAULT to NOW()"
        assert "now" in str(default_val).lower(), (
            f"create_date DEFAULT '{default_val}' does not reference NOW()"
        )

    def test_dq_run_rerun_number_default_is_zero(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: rerun_number must DEFAULT to 0."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_run'
            AND column_name = 'rerun_number'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'rerun_number' not found in dq_run"
        default_val = row[0]
        assert default_val is not None, "rerun_number has no DEFAULT — must DEFAULT to 0"
        assert str(default_val).strip() == "0", (
            f"rerun_number DEFAULT '{default_val}' is not '0'"
        )


@pytest.mark.integration
class TestDqRunUniqueConstraint:
    """AC2 + AC4: Composite unique constraint on (dataset_name, partition_date, rerun_number, expiry_date)."""

    CONSTRAINT_NAME = "uq_dq_run_dataset_name_partition_date_rerun_number_expiry_date"

    def test_unique_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: Composite unique constraint must exist with exact prescribed name."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND table_name = 'dq_run'
            AND constraint_type = 'UNIQUE'
            AND constraint_name = %s
            """,
            (self.CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Unique constraint '{self.CONSTRAINT_NAME}' not found on dq_run — "
            "add CONSTRAINT uq_dq_run_dataset_name_partition_date_rerun_number_expiry_date "
            "UNIQUE (dataset_name, partition_date, rerun_number, expiry_date) in ddl.sql"
        )

    def test_unique_constraint_covers_correct_columns(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: Unique constraint must cover exactly (dataset_name, partition_date, rerun_number, expiry_date)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.key_column_usage kcu
            JOIN information_schema.table_constraints tc
                ON kcu.constraint_name = tc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_run'
            AND tc.constraint_name = %s
            ORDER BY kcu.ordinal_position
            """,
            (self.CONSTRAINT_NAME,),
        )
        columns = [row[0] for row in cur.fetchall()]
        expected_columns = ["dataset_name", "partition_date", "rerun_number", "expiry_date"]
        assert columns == expected_columns, (
            f"Unique constraint columns {columns} != {expected_columns} — "
            "the constraint must be on exactly (dataset_name, partition_date, rerun_number, expiry_date) "
            "in that order"
        )

    def test_two_active_records_same_natural_key_rejected(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Inserting two active records with same (dataset_name, partition_date, rerun_number)
        must be rejected with UniqueViolation.

        Active records share the same expiry_date = EXPIRY_SENTINEL, so the composite
        constraint fires.
        """
        import psycopg2  # noqa: PLC0415

        cur = db_conn.cursor()

        # Insert first active record — should succeed
        cur.execute(
            """
            INSERT INTO dq_run
                (dataset_name, partition_date, check_status, rerun_number)
            VALUES
                ('sales_daily', '2024-01-15', 'PASS', 0)
            """,
        )

        # Insert second active record with same natural key — must be rejected
        with pytest.raises(psycopg2.errors.UniqueViolation):
            cur.execute(
                """
                INSERT INTO dq_run
                    (dataset_name, partition_date, check_status, rerun_number)
                VALUES
                    ('sales_daily', '2024-01-15', 'FAIL', 0)
                """,
            )

    def test_active_and_expired_records_same_natural_key_allowed(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Inserting one active and one expired record with same natural key
        must SUCCEED — different expiry_date values do not trigger the constraint.

        This validates the temporal pattern's core property: soft-delete by expiring old records
        then inserting new active ones.
        """
        cur = db_conn.cursor()

        # Insert an expired record (past expiry_date)
        cur.execute(
            """
            INSERT INTO dq_run
                (dataset_name, partition_date, check_status, rerun_number, expiry_date)
            VALUES
                ('inventory_weekly', '2024-02-01', 'PASS', 0, '2024-03-01 00:00:00')
            """,
        )

        # Insert an active record with the same natural key — must succeed because
        # expiry_date differs (sentinel vs past timestamp)
        cur.execute(
            """
            INSERT INTO dq_run
                (dataset_name, partition_date, check_status, rerun_number)
            VALUES
                ('inventory_weekly', '2024-02-01', 'PASS', 0)
            """,
        )

        # Verify both rows exist
        cur.execute(
            """
            SELECT COUNT(*) FROM dq_run
            WHERE dataset_name = 'inventory_weekly'
            AND partition_date = '2024-02-01'
            AND rerun_number = 0
            """
        )
        count = cur.fetchone()[0]
        assert count == 2, (
            f"Expected 2 rows (one expired, one active) but found {count} — "
            "the temporal pattern requires both active and expired records with same natural key to coexist"
        )

    def test_rerun_number_differentiates_reruns(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P1]: Two active records with same (dataset_name, partition_date) but different
        rerun_number must be allowed — rerun_number is part of the natural key.
        """
        cur = db_conn.cursor()

        # Insert rerun 0 (initial run)
        cur.execute(
            """
            INSERT INTO dq_run
                (dataset_name, partition_date, check_status, rerun_number)
            VALUES
                ('fraud_scores', '2024-03-10', 'FAIL', 0)
            """,
        )

        # Insert rerun 1 (first rerun) — must succeed because rerun_number differs
        cur.execute(
            """
            INSERT INTO dq_run
                (dataset_name, partition_date, check_status, rerun_number)
            VALUES
                ('fraud_scores', '2024-03-10', 'PASS', 1)
            """,
        )

        cur.execute(
            """
            SELECT COUNT(*) FROM dq_run
            WHERE dataset_name = 'fraud_scores'
            AND partition_date = '2024-03-10'
            """
        )
        count = cur.fetchone()[0]
        assert count == 2, (
            f"Expected 2 rerun records but found {count} — "
            "different rerun_number values must allow multiple active records for the same date"
        )
