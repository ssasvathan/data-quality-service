"""GET /api/executive/report — executive reporting suite endpoint.

All queries use v_dq_run_active (active-record view). Never query dq_run directly.
Per project-context.md: snake_case JSON keys, SQLAlchemy 2.0 style, type hints.
"""
import logging

from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..db.session import get_db

router = APIRouter()
logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class LobMonthlyScore(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    lob_id: str
    month: str
    avg_score: float | None


class SourceSystemScore(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    src_sys_nm: str
    dataset_count: int
    avg_score: float | None
    healthy_count: int
    critical_count: int


class LobImprovementSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    lob_id: str
    baseline_score: float | None
    current_score: float | None
    delta: float | None


class ExecutiveReportResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    lob_monthly_scores: list[LobMonthlyScore]
    source_system_scores: list[SourceSystemScore]
    improvement_summary: list[LobImprovementSummary]


# ---------------------------------------------------------------------------
# SQL queries — all against v_dq_run_active exclusively
# ---------------------------------------------------------------------------

_LOB_MONTHLY_ROLLUP_SQL = text(
    """
    SELECT
        lookup_code AS lob_id,
        TO_CHAR(DATE_TRUNC('month', partition_date), 'YYYY-MM') AS month,
        ROUND(AVG(dqs_score)::numeric, 2) AS avg_score
    FROM v_dq_run_active
    WHERE lookup_code IS NOT NULL
      AND dqs_score IS NOT NULL
      AND partition_date >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '2 months'
    GROUP BY lookup_code, DATE_TRUNC('month', partition_date)
    ORDER BY lob_id, month ASC
    """
)

_SOURCE_SYSTEM_ACCOUNTABILITY_SQL = text(
    """
    WITH latest AS (
        SELECT MAX(partition_date) AS max_date FROM v_dq_run_active
    ),
    src_sys AS (
        SELECT
            SPLIT_PART(SPLIT_PART(dataset_name, 'src_sys_nm=', 2), '/', 1) AS src_sys_nm,
            dqs_score,
            check_status
        FROM v_dq_run_active, latest
        WHERE partition_date = latest.max_date
          AND dataset_name LIKE '%src_sys_nm=%'
    )
    SELECT
        src_sys_nm,
        COUNT(*) AS dataset_count,
        ROUND(AVG(dqs_score)::numeric, 2) AS avg_score,
        COUNT(*) FILTER (WHERE check_status = 'PASS') AS healthy_count,
        COUNT(*) FILTER (WHERE check_status = 'FAIL') AS critical_count
    FROM src_sys
    WHERE src_sys_nm IS NOT NULL AND src_sys_nm != ''
    GROUP BY src_sys_nm
    ORDER BY avg_score ASC NULLS LAST
    """
)

_IMPROVEMENT_DELTA_SQL = text(
    """
    WITH monthly AS (
        SELECT
            lookup_code AS lob_id,
            DATE_TRUNC('month', partition_date) AS month_start,
            AVG(dqs_score) AS avg_score
        FROM v_dq_run_active
        WHERE lookup_code IS NOT NULL AND dqs_score IS NOT NULL
          AND partition_date >= DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '2 months'
        GROUP BY lookup_code, DATE_TRUNC('month', partition_date)
    ),
    current_month AS (
        SELECT lob_id, avg_score AS current_score
        FROM monthly
        WHERE month_start = DATE_TRUNC('month', CURRENT_DATE)
    ),
    baseline_month AS (
        SELECT lob_id, avg_score AS baseline_score
        FROM monthly
        WHERE month_start = DATE_TRUNC('month', CURRENT_DATE) - INTERVAL '2 months'
    )
    SELECT
        COALESCE(c.lob_id, b.lob_id) AS lob_id,
        ROUND(b.baseline_score::numeric, 2) AS baseline_score,
        ROUND(c.current_score::numeric, 2) AS current_score,
        ROUND((c.current_score - b.baseline_score)::numeric, 2) AS delta
    FROM current_month c
    FULL OUTER JOIN baseline_month b ON c.lob_id = b.lob_id
    ORDER BY lob_id
    """
)


# ---------------------------------------------------------------------------
# Route
# ---------------------------------------------------------------------------


@router.get("/executive/report", response_model=ExecutiveReportResponse)
def get_executive_report(db: Session = Depends(get_db)) -> ExecutiveReportResponse:
    """Return executive reporting suite data.

    Queries v_dq_run_active exclusively (never raw dq_run).
    Returns three aggregated data sets:
    - lob_monthly_scores: Monthly LOB DQS score rollup (last 3 calendar months)
    - source_system_scores: Source system accountability for latest partition date
    - improvement_summary: Current month vs. 3-months-ago baseline delta per LOB
    """
    logger.info("Executing executive report queries against v_dq_run_active")

    # Monthly LOB rollup — last 3 calendar months
    lob_monthly_rows = db.execute(_LOB_MONTHLY_ROLLUP_SQL).mappings().all()
    lob_monthly_scores: list[LobMonthlyScore] = []
    for row in lob_monthly_rows:
        lob_id = row.get("lob_id")
        month = row.get("month")
        if lob_id is None or month is None:
            # Skip rows that don't match the expected schema (e.g. mock data)
            continue
        raw_score = row.get("avg_score")
        lob_monthly_scores.append(
            LobMonthlyScore(
                lob_id=str(lob_id),
                month=str(month),
                avg_score=float(raw_score) if raw_score is not None else None,
            )
        )

    # Source system accountability — latest partition date
    source_rows = db.execute(_SOURCE_SYSTEM_ACCOUNTABILITY_SQL).mappings().all()
    source_system_scores: list[SourceSystemScore] = []
    for row in source_rows:
        src_sys_nm = row.get("src_sys_nm")
        dataset_count = row.get("dataset_count")
        if src_sys_nm is None or dataset_count is None:
            # Skip rows that don't match the expected schema (e.g. mock data)
            continue
        raw_avg = row.get("avg_score")
        source_system_scores.append(
            SourceSystemScore(
                src_sys_nm=str(src_sys_nm),
                dataset_count=int(dataset_count),
                avg_score=float(raw_avg) if raw_avg is not None else None,
                healthy_count=int(row.get("healthy_count") or 0),
                critical_count=int(row.get("critical_count") or 0),
            )
        )

    # Improvement delta — current month vs. baseline (3 months ago)
    delta_rows = db.execute(_IMPROVEMENT_DELTA_SQL).mappings().all()
    improvement_summary: list[LobImprovementSummary] = []
    for row in delta_rows:
        lob_id_d = row.get("lob_id")
        if lob_id_d is None:
            # Skip rows that don't match the expected schema (e.g. mock data)
            continue
        raw_baseline = row.get("baseline_score")
        raw_current = row.get("current_score")
        raw_delta = row.get("delta")
        improvement_summary.append(
            LobImprovementSummary(
                lob_id=str(lob_id_d),
                baseline_score=float(raw_baseline) if raw_baseline is not None else None,
                current_score=float(raw_current) if raw_current is not None else None,
                delta=float(raw_delta) if raw_delta is not None else None,
            )
        )

    return ExecutiveReportResponse(
        lob_monthly_scores=lob_monthly_scores,
        source_system_scores=source_system_scores,
        improvement_summary=improvement_summary,
    )
