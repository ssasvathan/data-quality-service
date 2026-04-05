"""Acceptance tests for metric storage schema (dq_metric_numeric + dq_metric_detail).

Test categories:
  - Integration tests (real Postgres): verify tables, columns, constraints, FK, uniqueness
    All tests in this file are @pytest.mark.integration.

Run integration tests: pytest -m integration
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
# Run with: pytest -m integration
# ---------------------------------------------------------------------------


@pytest.mark.integration
class TestDqMetricNumericTableExists:
    """AC1: dq_metric_numeric table exists in public schema after DDL is applied."""

    def test_dq_metric_numeric_table_exists(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: dq_metric_numeric must exist in the public schema."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = 'dq_metric_numeric'
            )
            """
        )
        exists = cur.fetchone()[0]
        assert exists, (
            "Table 'dq_metric_numeric' does not exist — append CREATE TABLE dq_metric_numeric "
            "to dqs-serve/src/serve/schema/ddl.sql"
        )

    def test_dq_metric_numeric_id_is_serial_pk(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: dq_metric_numeric.id must be SERIAL (integer) PRIMARY KEY."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_metric_numeric'
            AND column_name = 'id'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'id' not found in dq_metric_numeric"
        assert row[0] == "integer", (
            f"Expected 'id' to be integer (SERIAL), got '{row[0]}'"
        )

        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'dq_metric_numeric'
            AND tc.constraint_type = 'PRIMARY KEY'
            AND kcu.column_name = 'id'
            """
        )
        pk_row = cur.fetchone()
        assert pk_row is not None, "Column 'id' is not the PRIMARY KEY of dq_metric_numeric"

    @pytest.mark.parametrize(
        "column_name,expected_data_type,is_nullable",
        [
            ("dq_run_id", "integer", "NO"),
            ("check_type", "text", "NO"),
            ("metric_name", "text", "NO"),
            ("metric_value", "numeric", "YES"),
            ("create_date", "timestamp without time zone", "NO"),
            ("expiry_date", "timestamp without time zone", "NO"),
        ],
    )
    def test_dq_metric_numeric_column_exists_with_correct_type(
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
            AND table_name = 'dq_metric_numeric'
            AND column_name = %s
            """,
            (column_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Column '{column_name}' not found in dq_metric_numeric — "
            "check ddl.sql for missing column definition"
        )
        actual_data_type, actual_nullable = row
        assert actual_data_type == expected_data_type, (
            f"Column '{column_name}': expected type '{expected_data_type}', got '{actual_data_type}'"
        )
        assert actual_nullable == is_nullable, (
            f"Column '{column_name}': expected nullable='{is_nullable}', got '{actual_nullable}'"
        )

    def test_dq_metric_numeric_expiry_date_default_is_sentinel(
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
            AND table_name = 'dq_metric_numeric'
            AND column_name = 'expiry_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'expiry_date' not found in dq_metric_numeric"
        default_val = row[0]
        assert default_val is not None, (
            f"expiry_date has no DEFAULT — must DEFAULT to '{EXPIRY_SENTINEL}'"
        )
        assert EXPIRY_SENTINEL in str(default_val), (
            f"expiry_date DEFAULT '{default_val}' does not contain '{EXPIRY_SENTINEL}'"
        )

    def test_dq_metric_numeric_create_date_default_is_now(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC1 [P0]: create_date must DEFAULT to NOW()."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_metric_numeric'
            AND column_name = 'create_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'create_date' not found in dq_metric_numeric"
        default_val = row[0]
        assert default_val is not None, "create_date has no DEFAULT — must DEFAULT to NOW()"
        assert "now" in str(default_val).lower(), (
            f"create_date DEFAULT '{default_val}' does not reference NOW()"
        )


@pytest.mark.integration
class TestDqMetricDetailTableExists:
    """AC2: dq_metric_detail table exists in public schema after DDL is applied."""

    def test_dq_metric_detail_table_exists(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: dq_metric_detail must exist in the public schema."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT EXISTS (
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = 'dq_metric_detail'
            )
            """
        )
        exists = cur.fetchone()[0]
        assert exists, (
            "Table 'dq_metric_detail' does not exist — append CREATE TABLE dq_metric_detail "
            "to dqs-serve/src/serve/schema/ddl.sql"
        )

    def test_dq_metric_detail_id_is_serial_pk(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: dq_metric_detail.id must be SERIAL (integer) PRIMARY KEY."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_metric_detail'
            AND column_name = 'id'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'id' not found in dq_metric_detail"
        assert row[0] == "integer", (
            f"Expected 'id' to be integer (SERIAL), got '{row[0]}'"
        )

        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'dq_metric_detail'
            AND tc.constraint_type = 'PRIMARY KEY'
            AND kcu.column_name = 'id'
            """
        )
        pk_row = cur.fetchone()
        assert pk_row is not None, "Column 'id' is not the PRIMARY KEY of dq_metric_detail"

    @pytest.mark.parametrize(
        "column_name,expected_data_type,is_nullable",
        [
            ("dq_run_id", "integer", "NO"),
            ("check_type", "text", "NO"),
            ("detail_type", "text", "NO"),
            ("detail_value", "jsonb", "YES"),
            ("create_date", "timestamp without time zone", "NO"),
            ("expiry_date", "timestamp without time zone", "NO"),
        ],
    )
    def test_dq_metric_detail_column_exists_with_correct_type(
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
            AND table_name = 'dq_metric_detail'
            AND column_name = %s
            """,
            (column_name,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Column '{column_name}' not found in dq_metric_detail — "
            "check ddl.sql for missing column definition"
        )
        actual_data_type, actual_nullable = row
        assert actual_data_type == expected_data_type, (
            f"Column '{column_name}': expected type '{expected_data_type}', got '{actual_data_type}'"
        )
        assert actual_nullable == is_nullable, (
            f"Column '{column_name}': expected nullable='{is_nullable}', got '{actual_nullable}'"
        )

    def test_dq_metric_detail_expiry_date_default_is_sentinel(
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
            AND table_name = 'dq_metric_detail'
            AND column_name = 'expiry_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'expiry_date' not found in dq_metric_detail"
        default_val = row[0]
        assert default_val is not None, (
            f"expiry_date has no DEFAULT — must DEFAULT to '{EXPIRY_SENTINEL}'"
        )
        assert EXPIRY_SENTINEL in str(default_val), (
            f"expiry_date DEFAULT '{default_val}' does not contain '{EXPIRY_SENTINEL}'"
        )

    def test_dq_metric_detail_create_date_default_is_now(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC2 [P0]: create_date must DEFAULT to NOW()."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'dq_metric_detail'
            AND column_name = 'create_date'
            """
        )
        row = cur.fetchone()
        assert row is not None, "Column 'create_date' not found in dq_metric_detail"
        default_val = row[0]
        assert default_val is not None, "create_date has no DEFAULT — must DEFAULT to NOW()"
        assert "now" in str(default_val).lower(), (
            f"create_date DEFAULT '{default_val}' does not reference NOW()"
        )


@pytest.mark.integration
class TestDqMetricNumericUniqueConstraint:
    """AC3: dq_metric_numeric unique constraint on (dq_run_id, check_type, metric_name, expiry_date)."""

    CONSTRAINT_NAME = (
        "uq_dq_metric_numeric_dq_run_id_check_type_metric_name_expiry_date"
    )

    def test_unique_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P0]: Unique constraint must exist with exact prescribed name."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND table_name = 'dq_metric_numeric'
            AND constraint_type = 'UNIQUE'
            AND constraint_name = %s
            """,
            (self.CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Unique constraint '{self.CONSTRAINT_NAME}' not found on dq_metric_numeric — "
            "add CONSTRAINT uq_dq_metric_numeric_dq_run_id_check_type_metric_name_expiry_date "
            "UNIQUE (dq_run_id, check_type, metric_name, expiry_date) in ddl.sql"
        )

    def test_unique_constraint_covers_correct_columns(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P0]: Unique constraint must cover exactly (dq_run_id, check_type, metric_name, expiry_date)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.key_column_usage kcu
            JOIN information_schema.table_constraints tc
                ON kcu.constraint_name = tc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_metric_numeric'
            AND tc.constraint_name = %s
            ORDER BY kcu.ordinal_position
            """,
            (self.CONSTRAINT_NAME,),
        )
        columns = [row[0] for row in cur.fetchall()]
        expected_columns = ["dq_run_id", "check_type", "metric_name", "expiry_date"]
        assert columns == expected_columns, (
            f"Unique constraint columns {columns} != {expected_columns} — "
            "the constraint must be on exactly (dq_run_id, check_type, metric_name, expiry_date) "
            "in that order"
        )

    def test_two_active_numeric_rows_same_natural_key_rejected(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P1]: Inserting two active dq_metric_numeric rows with same
        (dq_run_id, check_type, metric_name) must raise UniqueViolation.

        Both rows share expiry_date = sentinel (active), so the composite
        constraint fires.
        """
        import psycopg2  # noqa: PLC0415
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()

        # Insert a dq_run row to satisfy the FK constraint
        cur.execute(
            """
            INSERT INTO dq_run (dataset_name, partition_date, check_status)
            VALUES ('sales_daily', '2024-01-15', 'PASS')
            RETURNING id
            """
        )
        run_id = cur.fetchone()[0]

        # Insert first active metric row — should succeed
        cur.execute(
            """
            INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value)
            VALUES (%s, 'freshness', 'staleness_hours', 1.5)
            """,
            (run_id,),
        )

        # Insert duplicate active row — must raise UniqueViolation
        with pytest.raises(psycopg2.errors.UniqueViolation):
            cur.execute(
                """
                INSERT INTO dq_metric_numeric (dq_run_id, check_type, metric_name, metric_value)
                VALUES (%s, 'freshness', 'staleness_hours', 2.0)
                """,
                (run_id,),
            )

        # Confirm EXPIRY_SENTINEL was used (documents intent — no hardcoding)
        assert EXPIRY_SENTINEL == "9999-12-31 23:59:59"

    def test_active_and_expired_numeric_rows_same_natural_key_allowed(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC3 [P1]: One active + one expired row with same natural key must SUCCEED.

        Different expiry_date values (sentinel vs. past timestamp) do not trigger
        the constraint — this is the temporal pattern's core property.
        """
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()

        cur.execute(
            """
            INSERT INTO dq_run (dataset_name, partition_date, check_status)
            VALUES ('inventory_weekly', '2024-02-01', 'PASS')
            RETURNING id
            """
        )
        run_id = cur.fetchone()[0]

        # Insert an expired row (past expiry_date)
        cur.execute(
            """
            INSERT INTO dq_metric_numeric
                (dq_run_id, check_type, metric_name, metric_value, expiry_date)
            VALUES (%s, 'volume', 'row_count', 1000.0, '2024-03-01 00:00:00')
            """,
            (run_id,),
        )

        # Insert an active row with same natural key — must succeed
        cur.execute(
            """
            INSERT INTO dq_metric_numeric
                (dq_run_id, check_type, metric_name, metric_value)
            VALUES (%s, 'volume', 'row_count', 1200.0)
            """,
            (run_id,),
        )

        cur.execute(
            """
            SELECT COUNT(*) FROM dq_metric_numeric
            WHERE dq_run_id = %s
            AND check_type = 'volume'
            AND metric_name = 'row_count'
            """,
            (run_id,),
        )
        count = cur.fetchone()[0]
        assert count == 2, (
            f"Expected 2 rows (one expired, one active) but found {count} — "
            "temporal pattern: active and expired records with same natural key must coexist"
        )
        _ = EXPIRY_SENTINEL  # documents usage intent


@pytest.mark.integration
class TestDqMetricDetailUniqueConstraint:
    """AC4: dq_metric_detail unique constraint on (dq_run_id, check_type, detail_type, expiry_date)."""

    CONSTRAINT_NAME = (
        "uq_dq_metric_detail_dq_run_id_check_type_detail_type_expiry_date"
    )

    def test_unique_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Unique constraint must exist with exact prescribed name."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND table_name = 'dq_metric_detail'
            AND constraint_type = 'UNIQUE'
            AND constraint_name = %s
            """,
            (self.CONSTRAINT_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Unique constraint '{self.CONSTRAINT_NAME}' not found on dq_metric_detail — "
            "add CONSTRAINT uq_dq_metric_detail_dq_run_id_check_type_detail_type_expiry_date "
            "UNIQUE (dq_run_id, check_type, detail_type, expiry_date) in ddl.sql"
        )

    def test_unique_constraint_covers_correct_columns(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P0]: Unique constraint must cover exactly (dq_run_id, check_type, detail_type, expiry_date)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT kcu.column_name
            FROM information_schema.key_column_usage kcu
            JOIN information_schema.table_constraints tc
                ON kcu.constraint_name = tc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_metric_detail'
            AND tc.constraint_name = %s
            ORDER BY kcu.ordinal_position
            """,
            (self.CONSTRAINT_NAME,),
        )
        columns = [row[0] for row in cur.fetchall()]
        expected_columns = ["dq_run_id", "check_type", "detail_type", "expiry_date"]
        assert columns == expected_columns, (
            f"Unique constraint columns {columns} != {expected_columns} — "
            "the constraint must be on exactly (dq_run_id, check_type, detail_type, expiry_date) "
            "in that order"
        )

    def test_two_active_detail_rows_same_natural_key_rejected(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P1]: Inserting two active dq_metric_detail rows with same
        (dq_run_id, check_type, detail_type) must raise UniqueViolation.
        """
        import psycopg2  # noqa: PLC0415
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()

        # Insert a dq_run row to satisfy the FK constraint
        cur.execute(
            """
            INSERT INTO dq_run (dataset_name, partition_date, check_status)
            VALUES ('fraud_scores', '2024-03-10', 'PASS')
            RETURNING id
            """
        )
        run_id = cur.fetchone()[0]

        # Insert first active detail row — should succeed
        cur.execute(
            """
            INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value)
            VALUES (%s, 'schema', 'drift_summary', '{"expected_fields": ["id", "amount"]}')
            """,
            (run_id,),
        )

        # Insert duplicate active row — must raise UniqueViolation
        with pytest.raises(psycopg2.errors.UniqueViolation):
            cur.execute(
                """
                INSERT INTO dq_metric_detail (dq_run_id, check_type, detail_type, detail_value)
                VALUES (%s, 'schema', 'drift_summary', '{"expected_fields": ["id", "amt"]}')
                """,
                (run_id,),
            )

        _ = EXPIRY_SENTINEL  # documents usage intent

    def test_active_and_expired_detail_rows_same_natural_key_allowed(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC4 [P1]: One active + one expired detail row with same natural key must SUCCEED.

        Temporal pattern: soft-delete (expire old row) then insert new active row.
        """
        from serve.db.models import EXPIRY_SENTINEL  # noqa: PLC0415

        cur = db_conn.cursor()

        cur.execute(
            """
            INSERT INTO dq_run (dataset_name, partition_date, check_status)
            VALUES ('ops_metrics', '2024-04-01', 'FAIL')
            RETURNING id
            """
        )
        run_id = cur.fetchone()[0]

        # Insert an expired row
        cur.execute(
            """
            INSERT INTO dq_metric_detail
                (dq_run_id, check_type, detail_type, detail_value, expiry_date)
            VALUES (%s, 'ops', 'missing_ops_fields',
                    '{"missing_ops_fields": ["source_event_timestamp"]}',
                    '2024-04-02 00:00:00')
            """,
            (run_id,),
        )

        # Insert an active row with same natural key — must succeed
        cur.execute(
            """
            INSERT INTO dq_metric_detail
                (dq_run_id, check_type, detail_type, detail_value)
            VALUES (%s, 'ops', 'missing_ops_fields',
                    '{"missing_ops_fields": ["source_event_timestamp", "event_id"]}')
            """,
            (run_id,),
        )

        cur.execute(
            """
            SELECT COUNT(*) FROM dq_metric_detail
            WHERE dq_run_id = %s
            AND check_type = 'ops'
            AND detail_type = 'missing_ops_fields'
            """,
            (run_id,),
        )
        count = cur.fetchone()[0]
        assert count == 2, (
            f"Expected 2 rows (one expired, one active) but found {count} — "
            "temporal pattern: active and expired records with same natural key must coexist"
        )
        _ = EXPIRY_SENTINEL  # documents usage intent


@pytest.mark.integration
class TestDqMetricNumericForeignKey:
    """AC5: dq_metric_numeric FK constraint to dq_run.id."""

    FK_NAME = "fk_dq_metric_numeric_dq_run"

    def test_fk_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC5 [P0]: FK constraint fk_dq_metric_numeric_dq_run must exist on dq_metric_numeric."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT tc.constraint_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.referential_constraints rc
                ON tc.constraint_name = rc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_metric_numeric'
            AND tc.constraint_type = 'FOREIGN KEY'
            AND tc.constraint_name = %s
            """,
            (self.FK_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"FK constraint '{self.FK_NAME}' not found on dq_metric_numeric — "
            "add CONSTRAINT fk_dq_metric_numeric_dq_run FOREIGN KEY (dq_run_id) "
            "REFERENCES dq_run(id) in ddl.sql"
        )

    def test_fk_references_dq_run_id(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC5 [P0]: FK must reference dq_run.id (the parent table's PK)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT ccu.table_name AS foreign_table, ccu.column_name AS foreign_column
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            JOIN information_schema.constraint_column_usage ccu
                ON tc.constraint_name = ccu.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_metric_numeric'
            AND tc.constraint_type = 'FOREIGN KEY'
            AND tc.constraint_name = %s
            """,
            (self.FK_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Cannot resolve FK '{self.FK_NAME}' reference — constraint may not exist"
        )
        foreign_table, foreign_column = row
        assert foreign_table == "dq_run", (
            f"FK '{self.FK_NAME}' references '{foreign_table}' instead of 'dq_run'"
        )
        assert foreign_column == "id", (
            f"FK '{self.FK_NAME}' references column '{foreign_column}' instead of 'id'"
        )

    def test_insert_with_invalid_dq_run_id_raises_fk_violation(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC5 [P1]: Inserting a dq_metric_numeric row with a non-existent dq_run_id
        must raise ForeignKeyViolation — the FK is enforced.
        """
        import psycopg2  # noqa: PLC0415

        cur = db_conn.cursor()

        # Use a dq_run_id that is guaranteed not to exist
        invalid_run_id = 999999

        with pytest.raises(psycopg2.errors.ForeignKeyViolation):
            cur.execute(
                """
                INSERT INTO dq_metric_numeric
                    (dq_run_id, check_type, metric_name, metric_value)
                VALUES (%s, 'freshness', 'staleness_hours', 5.0)
                """,
                (invalid_run_id,),
            )


@pytest.mark.integration
class TestDqMetricDetailForeignKey:
    """AC5: dq_metric_detail FK constraint to dq_run.id."""

    FK_NAME = "fk_dq_metric_detail_dq_run"

    def test_fk_constraint_exists_with_correct_name(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC5 [P0]: FK constraint fk_dq_metric_detail_dq_run must exist on dq_metric_detail."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT tc.constraint_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.referential_constraints rc
                ON tc.constraint_name = rc.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_metric_detail'
            AND tc.constraint_type = 'FOREIGN KEY'
            AND tc.constraint_name = %s
            """,
            (self.FK_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"FK constraint '{self.FK_NAME}' not found on dq_metric_detail — "
            "add CONSTRAINT fk_dq_metric_detail_dq_run FOREIGN KEY (dq_run_id) "
            "REFERENCES dq_run(id) in ddl.sql"
        )

    def test_fk_references_dq_run_id(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC5 [P0]: FK must reference dq_run.id (the parent table's PK)."""
        cur = db_conn.cursor()
        cur.execute(
            """
            SELECT ccu.table_name AS foreign_table, ccu.column_name AS foreign_column
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
            JOIN information_schema.constraint_column_usage ccu
                ON tc.constraint_name = ccu.constraint_name
            WHERE tc.table_schema = 'public'
            AND tc.table_name = 'dq_metric_detail'
            AND tc.constraint_type = 'FOREIGN KEY'
            AND tc.constraint_name = %s
            """,
            (self.FK_NAME,),
        )
        row = cur.fetchone()
        assert row is not None, (
            f"Cannot resolve FK '{self.FK_NAME}' reference — constraint may not exist"
        )
        foreign_table, foreign_column = row
        assert foreign_table == "dq_run", (
            f"FK '{self.FK_NAME}' references '{foreign_table}' instead of 'dq_run'"
        )
        assert foreign_column == "id", (
            f"FK '{self.FK_NAME}' references column '{foreign_column}' instead of 'id'"
        )

    def test_insert_with_invalid_dq_run_id_raises_fk_violation(
        self, db_conn: "psycopg2.connection"  # type: ignore[name-defined]
    ) -> None:
        """AC5 [P1]: Inserting a dq_metric_detail row with a non-existent dq_run_id
        must raise ForeignKeyViolation — the FK is enforced.
        """
        import psycopg2  # noqa: PLC0415

        cur = db_conn.cursor()

        # Use a dq_run_id that is guaranteed not to exist
        invalid_run_id = 999999

        with pytest.raises(psycopg2.errors.ForeignKeyViolation):
            cur.execute(
                """
                INSERT INTO dq_metric_detail
                    (dq_run_id, check_type, detail_type, detail_value)
                VALUES (%s, 'schema', 'drift_summary', '{}')
                """,
                (invalid_run_id,),
            )
