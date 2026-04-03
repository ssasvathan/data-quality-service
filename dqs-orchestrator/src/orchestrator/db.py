"""Database helpers for dqs-orchestrator.

Uses psycopg2-binary (not SQLAlchemy) for lightweight CLI usage.
"""
import logging
import os
from datetime import datetime
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
