"""GET /api/search endpoint — dataset search with DQS score inline.

All queries use v_dq_run_active (active-record view). Never query raw dq_run table.
Per project-context.md: snake_case JSON keys, SQLAlchemy 2.0 style, type hints on all functions.
"""
import logging
from typing import Optional

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


class SearchResult(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    dataset_id: int
    dataset_name: str
    lob_id: Optional[str]       # lookup_code — nullable for legacy rows
    dqs_score: Optional[float]  # can be NULL in DB
    check_status: str


class SearchResponse(BaseModel):
    results: list[SearchResult]


# ---------------------------------------------------------------------------
# SQL constants
# ---------------------------------------------------------------------------

# ROW_NUMBER() CTE pattern — latest run per dataset_name matching ILIKE.
# :q      = raw user query string (used inside '%' || :q || '%' for substring)
# :q_prefix = q + '%' (bound in Python, used in CASE expression for prefix ordering)
_SEARCH_SQL = text(
    """
    WITH ranked AS (
        SELECT
            id,
            dataset_name,
            lookup_code,
            check_status,
            dqs_score,
            ROW_NUMBER() OVER (
                PARTITION BY dataset_name
                ORDER BY partition_date DESC
            ) AS rn
        FROM v_dq_run_active
        WHERE dataset_name ILIKE '%' || :q || '%'
    ),
    latest AS (
        SELECT id, dataset_name, lookup_code, check_status, dqs_score
        FROM ranked
        WHERE rn = 1
    )
    SELECT
        id,
        dataset_name,
        lookup_code,
        check_status,
        dqs_score,
        CASE
            WHEN dataset_name ILIKE :q_prefix THEN 0
            ELSE 1
        END AS sort_order
    FROM latest
    ORDER BY sort_order, dataset_name
    LIMIT 10
    """
)


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------


@router.get("/search", response_model=SearchResponse)
def search_datasets(q: str, db: Session = Depends(get_db)) -> SearchResponse:
    """Search datasets by name with DQS score inline.

    Returns up to 10 matching datasets ordered by prefix match first, then substring.
    Returns empty results (not 404) when no datasets match — AC3 is explicit.
    Queries v_dq_run_active — never raw dq_run table.

    q is a required query parameter — FastAPI auto-generates 422 when absent.
    Empty q string returns empty results gracefully.
    """
    if not q:
        return SearchResponse(results=[])

    rows = db.execute(
        _SEARCH_SQL,
        {"q": q, "q_prefix": q + "%"},
    ).mappings().all()

    return SearchResponse(
        results=[
            SearchResult(
                dataset_id=row["id"],
                dataset_name=row["dataset_name"],
                lob_id=row["lookup_code"],
                dqs_score=row["dqs_score"],
                check_status=row["check_status"],
            )
            for row in rows
        ]
    )
