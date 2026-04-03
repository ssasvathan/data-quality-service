"""Database helpers for dqs-orchestrator.

Uses psycopg2-binary (not SQLAlchemy) for lightweight CLI usage.
"""
import logging
import os
from datetime import date, datetime
from typing import Any

import psycopg2

logger = logging.getLogger(__name__)

# Temporal pattern: active records use this sentinel as expiry_date
# NEVER hardcode '9999-12-31 23:59:59' inline — always use this constant
# See project-context.md § Temporal Data Pattern (ALL COMPONENTS)
EXPIRY_SENTINEL = "9999-12-31 23:59:59"


def get_connection(dsn: str | None = None) -> Any:
    """Return a psycopg2 connection using environment or config defaults."""
    resolved_dsn: str = dsn or os.getenv("DATABASE_URL", "postgresql://postgres:localdev@localhost:5432/postgres")
    return psycopg2.connect(resolved_dsn)


def create_orchestration_run(conn: Any, parent_path: str, start_time: datetime) -> int:
    """Insert a new dq_orchestration_run row with run_status='running'.

    Returns the auto-generated id.
    """
    sql = """
        INSERT INTO dq_orchestration_run
            (parent_path, run_status, start_time, expiry_date)
        VALUES (%s, %s, %s, %s)
        RETURNING id
    """
    with conn.cursor() as cur:
        cur.execute(sql, (parent_path, "running", start_time, EXPIRY_SENTINEL))
        row = cur.fetchone()
        conn.commit()
        run_id: int = row[0]
        logger.info("Created orchestration_run: id=%d parent_path=%s", run_id, parent_path)
        return run_id


def finalize_orchestration_run(
    conn: Any,
    run_id: int,
    end_time: datetime,
    total_datasets: int | None,
    passed_datasets: int | None,
    failed_datasets: int,
    error_summary: str | None,
) -> None:
    """Update dq_orchestration_run row with final status, counts, and error_summary.

    Sets run_status='completed' if failed_datasets==0, else 'completed_with_errors'.
    """
    run_status = "completed" if failed_datasets == 0 else "completed_with_errors"
    sql = """
        UPDATE dq_orchestration_run
        SET end_time = %s,
            total_datasets = %s,
            passed_datasets = %s,
            failed_datasets = %s,
            run_status = %s,
            error_summary = %s
        WHERE id = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (
            end_time, total_datasets, passed_datasets,
            failed_datasets, run_status, error_summary, run_id,
        ))
        conn.commit()
    logger.info(
        "Finalized orchestration_run: id=%d status=%s failed=%d",
        run_id, run_status, failed_datasets,
    )


def expire_previous_run(
    conn: Any,
    dataset_name: str,
    partition_date: date,
    current_run_expiry: datetime,
) -> int | None:
    """Expire the active dq_run for this dataset+date and cascade to metrics.

    Sets expiry_date = current_run_expiry on:
    1. The dq_run row (must have expiry_date = EXPIRY_SENTINEL)
    2. All dq_metric_numeric rows for that dq_run_id
    3. All dq_metric_detail rows for that dq_run_id

    Returns the rerun_number of the expired run (for computing next rerun_number),
    or None if no active run exists (first-time run — not an error).

    All three UPDATEs execute in one transaction (conn.commit() after all three).
    """
    sql_expire_run = """
        UPDATE dq_run
        SET expiry_date = %s
        WHERE dataset_name = %s
          AND partition_date = %s
          AND expiry_date = %s
        RETURNING id, rerun_number
    """
    sql_expire_numeric = """
        UPDATE dq_metric_numeric
        SET expiry_date = %s
        WHERE dq_run_id = %s
          AND expiry_date = %s
    """
    sql_expire_detail = """
        UPDATE dq_metric_detail
        SET expiry_date = %s
        WHERE dq_run_id = %s
          AND expiry_date = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql_expire_run, (current_run_expiry, dataset_name, partition_date, EXPIRY_SENTINEL))
        row = cur.fetchone()
        if not row or len(row) < 2:
            conn.commit()
            logger.info(
                "expire_previous_run: no active run found for dataset=%s date=%s — skipping",
                dataset_name, partition_date,
            )
            return None
        run_id, rerun_number = row[0], row[1]

        cur.execute(sql_expire_numeric, (current_run_expiry, run_id, EXPIRY_SENTINEL))
        cur.execute(sql_expire_detail, (current_run_expiry, run_id, EXPIRY_SENTINEL))
        conn.commit()

    logger.info(
        "expire_previous_run: expired dq_run id=%d rerun_number=%d for dataset=%s date=%s",
        run_id, rerun_number, dataset_name, partition_date,
    )
    return rerun_number


def get_next_rerun_number(conn: Any, dataset_name: str, partition_date: date) -> int:
    """Return the next rerun_number for this dataset+date across all history.

    Queries MAX(rerun_number) across ALL dq_run rows (including expired).
    Returns MAX + 1. Returns 0 if no history exists.
    """
    sql = """
        SELECT COALESCE(MAX(rerun_number), -1) + 1
        FROM dq_run
        WHERE dataset_name = %s
          AND partition_date = %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (dataset_name, partition_date))
        row = cur.fetchone()
        return row[0] if row else 0


def query_run_summary(
    conn: Any,
    orchestration_run_id: int,
    partition_date: date,
) -> dict[str, Any]:
    """Query Postgres for run summary data for email composition.

    Returns dict with keys matching RunSummary fields.
    Returns safe defaults if orchestration_run_id not found.
    """
    sql_orch_run = """
        SELECT id, parent_path, run_status, start_time, end_time,
               total_datasets, passed_datasets, failed_datasets, error_summary
        FROM dq_orchestration_run
        WHERE id = %s
    """
    sql_failed_datasets = """
        SELECT dataset_name
        FROM dq_run
        WHERE orchestration_run_id = %s
          AND check_status != 'passed'
          AND expiry_date = %s
    """
    sql_check_type_counts = """
        SELECT d.check_type, COUNT(*) as failure_count
        FROM dq_metric_detail d
        JOIN dq_run r ON r.id = d.dq_run_id
        WHERE r.orchestration_run_id = %s
          AND r.check_status != 'passed'
          AND d.expiry_date = %s
        GROUP BY d.check_type
        ORDER BY failure_count DESC
    """
    with conn.cursor() as cur:
        cur.execute(sql_orch_run, (orchestration_run_id,))
        row = cur.fetchone()

        if not row:
            logger.warning(
                "query_run_summary: orchestration_run_id=%d not found — returning safe defaults",
                orchestration_run_id,
            )
            return {
                "run_id": orchestration_run_id,
                "parent_path": "",
                "start_time": None,
                "end_time": None,
                "total_datasets": None,
                "passed_datasets": None,
                "failed_datasets": 0,
                "error_summary": None,
                "check_type_failures": {},
                "failed_dataset_names": [],
            }

        (
            run_id, parent_path, _run_status, start_time, end_time,
            total_datasets, passed_datasets, failed_datasets, error_summary,
        ) = row

        cur.execute(sql_failed_datasets, (orchestration_run_id, EXPIRY_SENTINEL))
        failed_rows = cur.fetchall()
        failed_dataset_names = [r[0] for r in failed_rows]

        cur.execute(sql_check_type_counts, (orchestration_run_id, EXPIRY_SENTINEL))
        count_rows = cur.fetchall()
        check_type_failures = {r[0]: r[1] for r in count_rows}

    logger.info(
        "query_run_summary: run_id=%d parent_path=%s failed_datasets=%d",
        run_id, parent_path, failed_datasets or 0,
    )
    return {
        "run_id": run_id,
        "parent_path": parent_path,
        "start_time": start_time,
        "end_time": end_time,
        "total_datasets": total_datasets,
        "passed_datasets": passed_datasets,
        "failed_datasets": failed_datasets or 0,
        "error_summary": error_summary,
        "check_type_failures": check_type_failures,
        "failed_dataset_names": failed_dataset_names,
    }
