"""GET /api/lobs and GET /api/lobs/{lob_id}/datasets endpoints.

All queries use v_dq_run_active and v_dq_metric_numeric_active (active-record views).
Never query raw tables directly.
Per project-context.md: snake_case JSON keys, SQLAlchemy 2.0 style, type hints on all functions.
"""
import datetime
import logging
from collections import defaultdict
from typing import Annotated, Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..db.session import get_db

router = APIRouter()
logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class LobDetail(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    lob_id: str
    dataset_count: int
    aggregate_score: Optional[float]
    healthy_count: int
    degraded_count: int
    critical_count: int


class DatasetInLob(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    dataset_id: int
    dataset_name: str
    dqs_score: Optional[float]
    check_status: str
    partition_date: datetime.date
    trend: list[float]
    freshness_status: Optional[str]
    volume_status: Optional[str]
    schema_status: Optional[str]


class LobDatasetsResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    lob_id: str
    datasets: list[DatasetInLob]


# ---------------------------------------------------------------------------
# SQL helpers
# ---------------------------------------------------------------------------

_TIME_RANGE_DAYS: dict[str, int] = {
    "7d": 7,
    "30d": 30,
    "90d": 90,
}


def _parse_time_range(time_range: str) -> int:
    """Map time_range string to number of days. Defaults to 7 on unknown input."""
    return _TIME_RANGE_DAYS.get(time_range, 7)


# ROW_NUMBER() CTE pattern replaces the correlated subquery anti-pattern.
_LOBS_LATEST_SQL = text(
    """
    WITH ranked AS (
        SELECT
            dataset_name,
            lookup_code,
            check_status,
            dqs_score,
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
        dqs_score
    FROM ranked
    WHERE rn = 1
    """
)

# ROW_NUMBER() CTE pattern — latest run per dataset within a specific LOB.
_DATASET_LATEST_FOR_LOB_SQL = text(
    """
    WITH ranked AS (
        SELECT
            id AS run_id,
            dataset_name,
            dqs_score,
            check_status,
            partition_date,
            ROW_NUMBER() OVER (
                PARTITION BY dataset_name
                ORDER BY partition_date DESC
            ) AS rn
        FROM v_dq_run_active
        WHERE lookup_code = :lob_id
    )
    SELECT
        run_id,
        dataset_name,
        dqs_score,
        check_status,
        partition_date
    FROM ranked
    WHERE rn = 1
    ORDER BY dataset_name
    """
)

# Batched dataset trend: fetches sparkline for ALL datasets in a single query
# using an IN clause, avoiding N+1 queries inside the loop.
# :days_back is computed as (days - 1) in Python before binding.
_DATASET_TREND_BATCH_SQL = text(
    """
    WITH ds_max AS (
        SELECT dataset_name, MAX(partition_date) AS max_date
        FROM v_dq_run_active
        WHERE dataset_name = ANY(:dataset_names)
        GROUP BY dataset_name
    )
    SELECT
        v.dataset_name,
        DATE(v.partition_date) AS day,
        AVG(v.dqs_score) AS avg_score
    FROM v_dq_run_active v
    JOIN ds_max m ON v.dataset_name = m.dataset_name
    WHERE v.partition_date >= m.max_date - CAST(:days_back AS INTEGER) * INTERVAL '1 day'
    GROUP BY v.dataset_name, DATE(v.partition_date)
    ORDER BY v.dataset_name, day ASC
    """
)

# Batched metric check types: fetches check types for ALL run_ids in a single
# query using an IN clause, avoiding N+1 queries inside the loop.
_METRIC_CHECK_TYPES_BATCH_SQL = text(
    """
    SELECT DISTINCT dq_run_id, check_type
    FROM v_dq_metric_numeric_active
    WHERE dq_run_id = ANY(:run_ids)
    """
)


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@router.get("/lobs", response_model=list[LobDetail])
def get_lobs(db: Session = Depends(get_db)) -> list[LobDetail]:
    """Return all LOBs with aggregate scores, dataset counts, and status distributions.

    Queries v_dq_run_active for distinct LOBs (via lookup_code).
    Aggregate score is the average of latest run per dataset within each LOB.
    """
    rows = db.execute(_LOBS_LATEST_SQL).mappings().all()

    lob_rows: dict[str, list] = defaultdict(list)
    for row in rows:
        lob_rows[row["lookup_code"]].append(row)

    result: list[LobDetail] = []
    for lob_id, lob_dataset_rows in lob_rows.items():
        scores = [r["dqs_score"] for r in lob_dataset_rows if r["dqs_score"] is not None]
        agg_score: Optional[float] = round(sum(scores) / len(scores), 2) if scores else None

        result.append(
            LobDetail(
                lob_id=lob_id,
                dataset_count=len(lob_dataset_rows),
                aggregate_score=agg_score,
                healthy_count=sum(1 for r in lob_dataset_rows if r["check_status"] == "PASS"),
                degraded_count=sum(1 for r in lob_dataset_rows if r["check_status"] == "WARN"),
                critical_count=sum(1 for r in lob_dataset_rows if r["check_status"] == "FAIL"),
            )
        )

    return result


@router.get("/lobs/{lob_id}/datasets", response_model=LobDatasetsResponse)
def get_lob_datasets(
    lob_id: Annotated[str, Field(pattern=r"^[A-Z0-9_]+$")],
    time_range: str = "7d",
    db: Session = Depends(get_db),
) -> LobDatasetsResponse:
    """Return all datasets within a LOB with DQS scores, trend sparkline, and per-check statuses.

    Path parameter lob_id maps to lookup_code in v_dq_run_active.
    lob_id must match pattern ^[A-Z0-9_]+$ (uppercase alphanumeric and underscores only).
    Query parameter time_range controls the sparkline window: 7d (default), 30d, 90d.
    Returns 404 with error_code NOT_FOUND if no datasets found for lob_id.
    """
    days = _parse_time_range(time_range)

    dataset_rows = db.execute(_DATASET_LATEST_FOR_LOB_SQL, {"lob_id": lob_id}).mappings().all()

    if not dataset_rows:
        raise HTTPException(
            status_code=404,
            detail={"detail": "LOB not found", "error_code": "NOT_FOUND"},
        )

    # Compute days_back in Python: window is [max_date - days_back, max_date] inclusive
    days_back = days - 1

    # Collect dataset names and run_ids for batched queries (avoids N+1)
    dataset_names: list[str] = [row["dataset_name"] for row in dataset_rows]
    run_ids: list[int] = [row["run_id"] for row in dataset_rows]

    # Batched trend query: one query for all datasets
    trend_rows = db.execute(
        _DATASET_TREND_BATCH_SQL,
        {"dataset_names": dataset_names, "days_back": days_back},
    ).mappings().all()

    # Build trend map: {dataset_name: [avg_score, ...]}
    trend_map: dict[str, list[float]] = defaultdict(list)
    for tr in trend_rows:
        trend_map[tr["dataset_name"]].append(round(float(tr["avg_score"]), 2))

    # Batched metric check types query: one query for all run_ids
    metric_rows = db.execute(
        _METRIC_CHECK_TYPES_BATCH_SQL,
        {"run_ids": run_ids},
    ).mappings().all()

    # Build check types map: {run_id: set[check_type]}
    check_types_map: dict[int, set[str]] = defaultdict(set)
    for mr in metric_rows:
        check_types_map[mr["dq_run_id"]].add(mr["check_type"])

    datasets: list[DatasetInLob] = []

    for row in dataset_rows:
        run_id: int = row["run_id"]
        dataset_name: str = row["dataset_name"]
        overall_status: str = row["check_status"]
        present_check_types: set[str] = check_types_map.get(run_id, set())

        freshness_status: Optional[str] = overall_status if "FRESHNESS" in present_check_types else None
        volume_status: Optional[str] = overall_status if "VOLUME" in present_check_types else None
        schema_status: Optional[str] = overall_status if "SCHEMA" in present_check_types else None

        datasets.append(
            DatasetInLob(
                dataset_id=run_id,
                dataset_name=dataset_name,
                dqs_score=row["dqs_score"],
                check_status=overall_status,
                partition_date=row["partition_date"],
                trend=trend_map.get(dataset_name, []),
                freshness_status=freshness_status,
                volume_status=volume_status,
                schema_status=schema_status,
            )
        )

    return LobDatasetsResponse(lob_id=lob_id, datasets=datasets)
