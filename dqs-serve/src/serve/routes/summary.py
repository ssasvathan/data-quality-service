"""GET /api/summary — aggregated LOB summary endpoint.

All queries use v_dq_run_active (active-record view). Never query dq_run directly.
Per project-context.md: snake_case JSON keys, SQLAlchemy 2.0 style, type hints on all functions.
"""
import logging
from collections import defaultdict
from typing import Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..db.session import get_db

router = APIRouter()
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

# Number of days in the LOB trend sparkline window. The SQL uses INTERVAL based
# on this value (days - 1 because we count from the max date inclusive).
# This is intentionally fixed for the summary endpoint — no time_range param per AC.
_SUMMARY_TREND_DAYS = 7


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class LobSummaryItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    lob_id: str
    dataset_count: int
    aggregate_score: Optional[float]
    healthy_count: int
    degraded_count: int
    critical_count: int
    trend: list[float]


class SummaryResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    total_datasets: int
    healthy_count: int
    degraded_count: int
    critical_count: int
    lobs: list[LobSummaryItem]


# ---------------------------------------------------------------------------
# Helper queries
# ---------------------------------------------------------------------------

# ROW_NUMBER() CTE pattern replaces the correlated subquery anti-pattern.
# This avoids a correlated subquery per row for finding MAX(partition_date).
_LATEST_PER_DATASET_SQL = text(
    """
    WITH ranked AS (
        SELECT
            dataset_name,
            lookup_code,
            check_status,
            dqs_score,
            partition_date,
            ROW_NUMBER() OVER (
                PARTITION BY dataset_name
                ORDER BY partition_date DESC
            ) AS rn
        FROM v_dq_run_active
        WHERE lookup_code IS NOT NULL
    )
    SELECT
        dataset_name,
        lookup_code,
        check_status,
        dqs_score,
        partition_date
    FROM ranked
    WHERE rn = 1
    """
)

# Batched trend query: retrieves 7-day trend for ALL lobs in a single query.
# Grouped by lookup_code and day to avoid N+1 queries (one per LOB).
# The window is (_SUMMARY_TREND_DAYS - 1) days back from each LOB's latest date,
# making the window exactly _SUMMARY_TREND_DAYS days inclusive.
# INTERVAL is intentionally hardcoded to _SUMMARY_TREND_DAYS: the summary endpoint
# has no time_range parameter per the acceptance criteria.
_ALL_LOBS_TREND_SQL = text(
    """
    WITH lob_max AS (
        SELECT lookup_code, MAX(partition_date) AS max_date
        FROM v_dq_run_active
        WHERE lookup_code IS NOT NULL
        GROUP BY lookup_code
    )
    SELECT
        v.lookup_code,
        DATE(v.partition_date) AS day,
        AVG(v.dqs_score) AS avg_score
    FROM v_dq_run_active v
    JOIN lob_max m ON v.lookup_code = m.lookup_code
    WHERE v.partition_date >= m.max_date - CAST(:days_back AS INTEGER) * INTERVAL '1 day'
    GROUP BY v.lookup_code, DATE(v.partition_date)
    ORDER BY v.lookup_code, day ASC
    """
)


# ---------------------------------------------------------------------------
# Route
# ---------------------------------------------------------------------------


@router.get("/summary", response_model=SummaryResponse)
def get_summary(db: Session = Depends(get_db)) -> SummaryResponse:
    """Return aggregated LOB data for the dashboard summary view.

    Queries v_dq_run_active (never raw dq_run) for the latest run per dataset.
    Per-LOB trend uses a fixed 7-day window (_SUMMARY_TREND_DAYS) from the
    active-record view — no time_range parameter per the acceptance criteria.
    """
    # Fetch latest run per dataset (excludes NULL lookup_code rows)
    rows = db.execute(_LATEST_PER_DATASET_SQL).mappings().all()

    # Global counts
    total_datasets = len(rows)
    healthy_count = sum(1 for r in rows if r["check_status"] == "PASS")
    degraded_count = sum(1 for r in rows if r["check_status"] == "WARN")
    critical_count = sum(1 for r in rows if r["check_status"] == "FAIL")

    # Group by LOB
    lob_rows: dict[str, list] = defaultdict(list)
    for row in rows:
        lob_rows[row["lookup_code"]].append(row)

    # Fetch trend for all LOBs in a single batched query (avoids N+1)
    # days_back = _SUMMARY_TREND_DAYS - 1 so the window is exactly _SUMMARY_TREND_DAYS
    # days inclusive (max_date - (days-1) to max_date)
    days_back = _SUMMARY_TREND_DAYS - 1
    trend_rows = db.execute(_ALL_LOBS_TREND_SQL, {"days_back": days_back}).mappings().all()

    # Build per-LOB trend map: {lob_id: [avg_score, ...]}
    lob_trends: dict[str, list[float]] = defaultdict(list)
    for tr in trend_rows:
        lob_trends[tr["lookup_code"]].append(round(float(tr["avg_score"]), 2))

    # Build per-LOB summaries
    lob_items: list[LobSummaryItem] = []
    for lob_id, lob_dataset_rows in lob_rows.items():
        scores = [r["dqs_score"] for r in lob_dataset_rows if r["dqs_score"] is not None]
        agg_score: Optional[float] = round(sum(scores) / len(scores), 2) if scores else None

        lob_items.append(
            LobSummaryItem(
                lob_id=lob_id,
                dataset_count=len(lob_dataset_rows),
                aggregate_score=agg_score,
                healthy_count=sum(1 for r in lob_dataset_rows if r["check_status"] == "PASS"),
                degraded_count=sum(1 for r in lob_dataset_rows if r["check_status"] == "WARN"),
                critical_count=sum(1 for r in lob_dataset_rows if r["check_status"] == "FAIL"),
                trend=lob_trends.get(lob_id, []),
            )
        )

    return SummaryResponse(
        total_datasets=total_datasets,
        healthy_count=healthy_count,
        degraded_count=degraded_count,
        critical_count=critical_count,
        lobs=lob_items,
    )
