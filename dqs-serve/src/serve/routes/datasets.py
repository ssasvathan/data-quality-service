"""GET /api/datasets/{dataset_id}, /metrics, and /trend endpoints.

All queries use active-record views (v_dq_run_active, v_dq_metric_numeric_active,
v_dq_metric_detail_active, v_dq_orchestration_run_active). Never query raw tables.
Per project-context.md: snake_case JSON keys, SQLAlchemy 2.0 style, type hints on all functions.
"""
from __future__ import annotations

import datetime
import json
import logging
from collections import defaultdict
from typing import TYPE_CHECKING, Any, Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..db.session import get_db
from ..dependencies import get_reference_data_service

if TYPE_CHECKING:
    # [Low-3] Import ReferenceDataService only for type-checking purposes;
    # at runtime the dependency is resolved via Depends(get_reference_data_service).
    from ..services.reference_data import ReferenceDataService  # noqa: TC001

router = APIRouter()
logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class DatasetDetail(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    dataset_id: int
    dataset_name: str
    lob_id: Optional[str]
    source_system: str
    format: str
    hdfs_path: str
    parent_path: Optional[str]
    partition_date: datetime.date
    row_count: Optional[float]
    previous_row_count: Optional[float]
    last_updated: Optional[datetime.datetime]
    run_id: int
    rerun_number: int
    dqs_score: Optional[float]
    check_status: str
    error_message: Optional[str]
    lob_name: str       # resolved from lookup_code via ReferenceDataService
    owner: str          # resolved from lookup_code via ReferenceDataService
    classification: str  # resolved from lookup_code via ReferenceDataService


class NumericMetric(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    metric_name: str
    metric_value: Optional[float]


class DetailMetric(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    detail_type: str
    detail_value: Optional[Any]


class CheckResult(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    check_type: str
    status: str
    numeric_metrics: list[NumericMetric]
    detail_metrics: list[DetailMetric]


class DatasetMetricsResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    dataset_id: int
    check_results: list[CheckResult]


class TrendPoint(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    date: datetime.date
    dqs_score: Optional[float]


class DatasetTrendResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    dataset_id: int
    time_range: str
    trend: list[TrendPoint]


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

_TIME_RANGE_DAYS: dict[str, int] = {
    "7d": 7,
    "30d": 30,
    "90d": 90,
}


# ---------------------------------------------------------------------------
# SQL constants
# ---------------------------------------------------------------------------

# Direct lookup by dq_run.id (dataset_id = dq_run.id)
_DATASET_DETAIL_SQL = text(
    """
    SELECT
        id,
        dataset_name,
        lookup_code,
        check_status,
        dqs_score,
        partition_date,
        rerun_number,
        orchestration_run_id,
        error_message,
        create_date
    FROM v_dq_run_active
    WHERE id = :dataset_id
    """
)

# Parent path lookup from orchestration run
_ORCHESTRATION_PARENT_PATH_SQL = text(
    """
    SELECT parent_path
    FROM v_dq_orchestration_run_active
    WHERE id = :orchestration_run_id
    """
)

# VOLUME row_count for the current run
_ROW_COUNT_SQL = text(
    """
    SELECT metric_value
    FROM v_dq_metric_numeric_active
    WHERE dq_run_id = :dataset_id
      AND check_type = 'VOLUME'
      AND metric_name = 'row_count'
    """
)

# Previous run VOLUME row_count (OFFSET 1 = skip the latest, take second latest)
_PREVIOUS_ROW_COUNT_SQL = text(
    """
    SELECT n.metric_value
    FROM v_dq_metric_numeric_active n
    JOIN v_dq_run_active r ON n.dq_run_id = r.id
    WHERE r.dataset_name = :dataset_name
      AND n.check_type = 'VOLUME'
      AND n.metric_name = 'row_count'
    ORDER BY r.partition_date DESC
    LIMIT 1 OFFSET 1
    """
)

# Format detail from SCHEMA check
_FORMAT_DETAIL_SQL = text(
    """
    SELECT detail_value
    FROM v_dq_metric_detail_active
    WHERE dq_run_id = :dataset_id
      AND check_type = 'SCHEMA'
      AND detail_type = 'eventAttribute_format'
    LIMIT 1
    """
)

# All numeric metrics for a run
_NUMERIC_METRICS_SQL = text(
    """
    SELECT check_type, metric_name, metric_value
    FROM v_dq_metric_numeric_active
    WHERE dq_run_id = :dataset_id
    ORDER BY check_type, metric_name
    """
)

# All detail metrics for a run
_DETAIL_METRICS_SQL = text(
    """
    SELECT check_type, detail_type, detail_value
    FROM v_dq_metric_detail_active
    WHERE dq_run_id = :dataset_id
    ORDER BY check_type, detail_type
    """
)

# Trend: MAX(partition_date) anchor pattern — same pattern as lobs.py
# days_back = days - 1 (window is [max_date - days_back, max_date] inclusive)
_TREND_SQL = text(
    """
    WITH ds_max AS (
        SELECT MAX(partition_date) AS max_date
        FROM v_dq_run_active
        WHERE dataset_name = :dataset_name
    )
    SELECT
        DATE(v.partition_date) AS date,
        AVG(v.dqs_score) AS dqs_score
    FROM v_dq_run_active v, ds_max m
    WHERE v.dataset_name = :dataset_name
      AND v.partition_date >= m.max_date - CAST(:days_back AS INTEGER) * INTERVAL '1 day'
    GROUP BY DATE(v.partition_date)
    ORDER BY date ASC
    """
)


# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------


def _extract_source_system(dataset_name: str) -> str:
    """Extract source system from dataset_name path segments.

    Examples:
      'lob=retail/src_sys_nm=alpha/dataset=sales_daily' → 'alpha'
      'src_sys_nm=omni/dataset=customer_profile' → 'omni'
    """
    for segment in dataset_name.split("/"):
        if segment.startswith("src_sys_nm="):
            return segment.split("=", 1)[1]
    return "unknown"


def _compose_hdfs_path(dataset_name: str, partition_date: datetime.date) -> str:
    """Compose the HDFS path for a dataset.

    Example: 'lob=retail/src_sys_nm=alpha/dataset=sales_daily', date 2026-04-02
    → '/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402'
    """
    date_str = partition_date.strftime("%Y%m%d")
    return f"/prod/datalake/{dataset_name}/partition_date={date_str}"


def _parse_format(detail_value: Any) -> str:
    """Extract format string from JSONB detail value.

    detail_value may be a Python str (psycopg2 returns jsonb as string),
    dict, or None. Handle all cases.
    """
    if detail_value is None:
        return "Unknown"
    if isinstance(detail_value, str):
        try:
            parsed = json.loads(detail_value)
            return str(parsed).capitalize()
        except (json.JSONDecodeError, TypeError):
            return detail_value.capitalize()
    return str(detail_value).capitalize()


def _parse_time_range(time_range: str) -> int:
    """Map time_range string to number of days. Defaults to 7 on unknown input."""
    return _TIME_RANGE_DAYS.get(time_range, 7)


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@router.get("/datasets/{dataset_id}", response_model=DatasetDetail)
def get_dataset(
    dataset_id: int,
    db: Session = Depends(get_db),
    ref_svc: ReferenceDataService = Depends(get_reference_data_service),
) -> DatasetDetail:
    """Return full metadata for a single dataset (dq_run record).

    dataset_id is dq_run.id (the primary key of the run record).
    Queries v_dq_run_active — never raw dq_run table.
    Returns 404 if no active record found for the given id.
    lookup_code is resolved to lob_name, owner, classification via ReferenceDataService.
    """
    rows = db.execute(_DATASET_DETAIL_SQL, {"dataset_id": dataset_id}).mappings().all()

    if not rows:
        raise HTTPException(
            status_code=404,
            detail={"detail": "Dataset not found", "error_code": "NOT_FOUND"},
        )

    run = rows[0]
    dataset_name: str = run["dataset_name"]
    partition_date: datetime.date = run["partition_date"]
    orchestration_run_id = run["orchestration_run_id"]

    # Derive parent_path from orchestration run (if available)
    parent_path: Optional[str] = None
    if orchestration_run_id is not None:
        orch_rows = db.execute(
            _ORCHESTRATION_PARENT_PATH_SQL,
            {"orchestration_run_id": orchestration_run_id},
        ).mappings().all()
        if orch_rows:
            parent_path = orch_rows[0]["parent_path"]

    # Derive row_count from VOLUME metric
    row_count_rows = db.execute(_ROW_COUNT_SQL, {"dataset_id": dataset_id}).mappings().all()
    row_count: Optional[float] = (
        row_count_rows[0].get("metric_value") if row_count_rows else None
    )

    # Derive previous_row_count from second-latest VOLUME/row_count for this dataset_name
    prev_rows = db.execute(
        _PREVIOUS_ROW_COUNT_SQL, {"dataset_name": dataset_name}
    ).mappings().all()
    previous_row_count: Optional[float] = (
        prev_rows[0].get("metric_value") if prev_rows else None
    )

    # Derive format from SCHEMA/eventAttribute_format detail
    format_rows = db.execute(_FORMAT_DETAIL_SQL, {"dataset_id": dataset_id}).mappings().all()
    format_val: str = _parse_format(
        format_rows[0].get("detail_value") if format_rows else None
    )

    # Resolve lookup_code to human-readable LOB names via ReferenceDataService
    mapping = ref_svc.resolve(run["lookup_code"])

    return DatasetDetail(
        dataset_id=dataset_id,
        dataset_name=dataset_name,
        lob_id=run["lookup_code"],
        source_system=_extract_source_system(dataset_name),
        format=format_val,
        hdfs_path=_compose_hdfs_path(dataset_name, partition_date),
        parent_path=parent_path,
        partition_date=partition_date,
        row_count=row_count,
        previous_row_count=previous_row_count,
        last_updated=run["create_date"],
        run_id=run["id"],
        rerun_number=run["rerun_number"],
        dqs_score=run["dqs_score"],
        check_status=run["check_status"],
        error_message=run["error_message"],
        lob_name=mapping.lob_name,
        owner=mapping.owner,
        classification=mapping.classification,
    )


@router.get("/datasets/{dataset_id}/metrics", response_model=DatasetMetricsResponse)
def get_dataset_metrics(
    dataset_id: int, db: Session = Depends(get_db)
) -> DatasetMetricsResponse:
    """Return all check metrics grouped by check_type for a dataset.

    Queries v_dq_metric_numeric_active and v_dq_metric_detail_active.
    Returns 404 if the dataset_id does not exist in v_dq_run_active.
    Per project-context.md: Do NOT add check-type-specific logic — return raw metric data.
    """
    # Verify dataset exists
    run_rows = db.execute(_DATASET_DETAIL_SQL, {"dataset_id": dataset_id}).mappings().all()
    if not run_rows:
        raise HTTPException(
            status_code=404,
            detail={"detail": "Dataset not found", "error_code": "NOT_FOUND"},
        )

    overall_status: str = run_rows[0]["check_status"]

    # Fetch all numeric metrics
    numeric_rows = db.execute(_NUMERIC_METRICS_SQL, {"dataset_id": dataset_id}).mappings().all()

    # Fetch all detail metrics
    detail_rows = db.execute(_DETAIL_METRICS_SQL, {"dataset_id": dataset_id}).mappings().all()

    # Group by check_type — skip rows missing expected keys (safety guard)
    numeric_by_check: dict[str, list[NumericMetric]] = defaultdict(list)
    for row in numeric_rows:
        if "check_type" not in row or "metric_name" not in row:
            continue
        numeric_by_check[row["check_type"]].append(
            NumericMetric(
                metric_name=row["metric_name"],
                metric_value=row.get("metric_value"),
            )
        )

    detail_by_check: dict[str, list[DetailMetric]] = defaultdict(list)
    for row in detail_rows:
        if "check_type" not in row or "detail_type" not in row:
            continue
        detail_by_check[row["check_type"]].append(
            DetailMetric(
                detail_type=row["detail_type"],
                detail_value=row.get("detail_value"),
            )
        )

    # Collect all check_types present across both tables
    all_check_types: set[str] = set(numeric_by_check.keys()) | set(detail_by_check.keys())

    check_results: list[CheckResult] = [
        CheckResult(
            check_type=check_type,
            status=overall_status,
            numeric_metrics=numeric_by_check.get(check_type, []),
            detail_metrics=detail_by_check.get(check_type, []),
        )
        for check_type in sorted(all_check_types)
    ]

    return DatasetMetricsResponse(
        dataset_id=dataset_id,
        check_results=check_results,
    )


@router.get("/datasets/{dataset_id}/trend", response_model=DatasetTrendResponse)
def get_dataset_trend(
    dataset_id: int,
    time_range: str = "7d",
    db: Session = Depends(get_db),
) -> DatasetTrendResponse:
    """Return daily DQS score trend for a dataset over a time window.

    Queries v_dq_run_active using MAX(partition_date) anchor pattern.
    time_range: '7d' (default), '30d', '90d' — maps to days_back = days - 1.
    Returns 404 if the dataset_id does not exist in v_dq_run_active.
    """
    # Verify dataset exists
    run_rows = db.execute(_DATASET_DETAIL_SQL, {"dataset_id": dataset_id}).mappings().all()
    if not run_rows:
        raise HTTPException(
            status_code=404,
            detail={"detail": "Dataset not found", "error_code": "NOT_FOUND"},
        )

    dataset_name: str = run_rows[0]["dataset_name"]
    days: int = _parse_time_range(time_range)
    days_back: int = days - 1

    trend_rows = db.execute(
        _TREND_SQL,
        {"dataset_name": dataset_name, "days_back": days_back},
    ).mappings().all()

    trend: list[TrendPoint] = [
        TrendPoint(
            date=row["date"],
            dqs_score=round(float(row["dqs_score"]), 2) if row["dqs_score"] is not None else None,
        )
        for row in trend_rows
    ]

    return DatasetTrendResponse(
        dataset_id=dataset_id,
        time_range=time_range,
        trend=trend,
    )
