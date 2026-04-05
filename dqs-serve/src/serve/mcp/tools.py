"""FastMCP tool definitions for the Data Quality Service MCP server.

Registers tools that LLM clients can discover and invoke via the MCP protocol.
All tools query v_dq_run_active (active-record views) — never raw tables.
MCP tools return LLM-optimised plain text strings, not JSON.

Per story dev notes:
  - Use fastmcp.dependencies.Depends (NOT fastapi.Depends)
  - Use exclude_args=["db"] to hide internal DB dependency from tool schema
  - Query v_dq_run_active and v_dq_metric_numeric_active only
"""
from __future__ import annotations

import logging
from collections import defaultdict
from typing import Any

from fastmcp import FastMCP
from fastmcp.dependencies import Depends
from sqlalchemy import text
from sqlalchemy.orm import Session

from ..db.session import get_db

# ---------------------------------------------------------------------------
# Time range helpers — duplicated here to keep MCP tools module independent
# ---------------------------------------------------------------------------

_TIME_RANGE_DAYS: dict[str, int] = {"7d": 7, "30d": 30, "90d": 90}


def _parse_time_range(time_range: str) -> int:
    """Return number of days for the given time_range string (default 7)."""
    return _TIME_RANGE_DAYS.get(time_range, 7)


def _normalize_time_range(time_range: str) -> str:
    """Return the canonical time_range string, defaulting to '7d' for unrecognised values."""
    return time_range if time_range in _TIME_RANGE_DAYS else "7d"


def _compute_trend_direction(scores: list[float]) -> str:
    """Compute trend direction from chronological score history.

    Returns 'improving', 'degrading', or 'stable' based on delta between
    first and last score. Threshold of 2.0 points is appropriate for 0-100 DQS scores.
    """
    if len(scores) < 2:
        return "stable"
    delta = scores[-1] - scores[0]
    if delta > 2.0:
        return "improving"
    if delta < -2.0:
        return "degrading"
    return "stable"

logger = logging.getLogger(__name__)

mcp: FastMCP = FastMCP("dqs")

# ---------------------------------------------------------------------------
# SQL queries — use active-record views only, never raw tables
# ---------------------------------------------------------------------------

_LATEST_DATE_SQL = text(
    "SELECT MAX(partition_date) AS latest_date FROM v_dq_run_active"
)

_FAIL_ROWS_SQL = text(
    """
    SELECT
        dataset_name,
        lookup_code,
        check_status,
        dqs_score,
        partition_date
    FROM v_dq_run_active
    WHERE partition_date = :latest_date
      AND check_status = 'FAIL'
    ORDER BY lookup_code, dataset_name
    """
)

_TOTAL_COUNT_SQL = text(
    """
    SELECT COUNT(DISTINCT dataset_name) AS total_count
    FROM v_dq_run_active
    WHERE partition_date = :latest_date
    """
)

_CHECK_TYPES_SQL = text(
    """
    SELECT DISTINCT r.dataset_name, m.check_type
    FROM v_dq_run_active r
    JOIN v_dq_metric_numeric_active m ON m.run_id = r.id
    WHERE r.partition_date = :latest_date
      AND r.check_status = 'FAIL'
    ORDER BY r.dataset_name, m.check_type
    """
)

# ---------------------------------------------------------------------------
# SQL for trending/comparison tools — active-record views only, never raw tables
# ---------------------------------------------------------------------------

_TREND_DATASET_SEARCH_SQL = text(
    """
    WITH ranked AS (
        SELECT
            id,
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
        WHERE dataset_name ILIKE '%' || :q || '%'
    )
    SELECT id, dataset_name, lookup_code, check_status, dqs_score, partition_date
    FROM ranked
    WHERE rn = 1
    ORDER BY dataset_name
    LIMIT 1
    """
)

_TREND_HISTORY_SQL = text(
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

_FAILING_CHECKS_FOR_RUN_SQL = text(
    """
    SELECT DISTINCT m.check_type
    FROM v_dq_metric_numeric_active m
    WHERE m.dq_run_id = :run_id
    ORDER BY m.check_type
    """
)

_LOB_LATEST_PER_DATASET_SQL = text(
    """
    WITH ranked AS (
        SELECT
            id AS run_id,
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
    SELECT run_id, dataset_name, lookup_code, check_status, dqs_score
    FROM ranked
    WHERE rn = 1
    """
)

_LOB_CHECK_TYPES_BATCH_SQL = text(
    """
    SELECT DISTINCT dq_run_id, check_type
    FROM v_dq_metric_numeric_active
    WHERE dq_run_id = ANY(:run_ids)
    """
)


# ---------------------------------------------------------------------------
# Tools
# ---------------------------------------------------------------------------


@mcp.tool(exclude_args=["db"])
def query_failures(
    time_range: str = "latest",
    db: Session = Depends(get_db),
) -> str:
    """Answer 'What failed last night?' — returns failure summary from latest DQS run.

    Queries the latest DQS run and returns a formatted text summary of all failed
    datasets, grouped by LOB (Line of Business). If no failures occurred, returns
    the AC3 all-passed message. If no run data is available, returns a no-data message.

    Args:
        time_range: Time range for the query. Only "latest" (most recent run) is
            currently supported. Reserved for future time-range filtering support.

    Returns:
        Plain text summary of failures from the latest run, suitable for LLM consumption.
    """
    logger.debug("query_failures called with time_range=%r", time_range)

    try:
        # Query 1: Find the latest partition_date (most recent run)
        latest_row = db.execute(_LATEST_DATE_SQL).mappings().first()
    except Exception:
        logger.error("query_failures: DB error fetching latest partition_date", exc_info=True)
        return "Error querying DQS run data. Please try again later."

    if latest_row is None or latest_row["latest_date"] is None:
        return "No DQS run data available."

    latest_date = latest_row["latest_date"]

    try:
        # Query 2: Get all FAIL rows from the latest run
        fail_rows = db.execute(_FAIL_ROWS_SQL, {"latest_date": latest_date}).mappings().all()
    except Exception:
        logger.error("query_failures: DB error fetching failure rows", exc_info=True)
        return "Error querying DQS run data. Please try again later."

    try:
        # Query 3: Get total distinct dataset count for the latest run
        count_row = db.execute(_TOTAL_COUNT_SQL, {"latest_date": latest_date}).mappings().first()
        total_count = (
            count_row["total_count"]
            if count_row is not None and count_row["total_count"] is not None
            else 0
        )
    except Exception:
        logger.error("query_failures: DB error fetching total dataset count", exc_info=True)
        return "Error querying DQS run data. Please try again later."

    # AC3: no failures case
    if not fail_rows:
        return (
            f"All datasets passed in the latest run ({latest_date}). "
            f"{total_count} datasets processed."
        )

    try:
        # Query 4: Get check types for failed datasets
        check_type_rows = db.execute(_CHECK_TYPES_SQL, {"latest_date": latest_date}).mappings().all()
    except Exception:
        logger.error("query_failures: DB error fetching check types", exc_info=True)
        return "Error querying DQS run data. Please try again later."

    # Build dataset → check_types map
    dataset_check_types: dict[str, list[str]] = defaultdict(list)
    for ct_row in check_type_rows:
        dataset_check_types[ct_row["dataset_name"]].append(ct_row["check_type"])

    # Group failures by LOB — guard against NULL lookup_code
    lob_failures: dict[str, list[Any]] = defaultdict(list)
    for row in fail_rows:
        lob_key = row["lookup_code"] if row["lookup_code"] is not None else "UNKNOWN"
        lob_failures[lob_key].append(row)

    failure_count = len(fail_rows)
    lines: list[str] = [
        f"DQS Failure Summary \u2014 Latest Run ({latest_date})",
        f"{failure_count} datasets failed out of {total_count} processed.",
        "",
    ]

    for lob_code, lob_rows in lob_failures.items():
        lob_count = len(lob_rows)
        failure_word = "failure" if lob_count == 1 else "failures"
        lines.append(f"{lob_code} ({lob_count} {failure_word}):")
        for row in lob_rows:
            dataset = row["dataset_name"]
            check_types = dataset_check_types.get(dataset, [])
            check_str = f" [{', '.join(check_types)}]" if check_types else ""
            lines.append(f"  - {dataset}{check_str}")
        lines.append("")

    # Remove trailing blank line
    if lines and lines[-1] == "":
        lines.pop()

    return "\n".join(lines)


@mcp.tool(exclude_args=["db"])
def query_dataset_trend(
    dataset_name: str,
    time_range: str = "7d",
    db: Session = Depends(get_db),
) -> str:
    """Show DQS score trend for a dataset over a time window.

    Use for queries like 'Show trending for dataset X' or 'How has dataset Y been performing?'
    Returns current DQS score, trend direction (improving/degrading/stable), score history
    over the requested period, and any flagged check types for the latest run.

    Args:
        dataset_name: Partial or full dataset name to search for (case-insensitive ILIKE).
        time_range: Time window for trend history. One of '7d', '30d', '90d'. Default '7d'.

    Returns:
        Plain text trend summary suitable for LLM consumption, or a not-found message
        if no dataset matches the search query.
    """
    logger.debug("query_dataset_trend called with dataset_name=%r, time_range=%r", dataset_name, time_range)

    # Step 1: Search for dataset by partial name using ILIKE
    try:
        matched_row = db.execute(_TREND_DATASET_SEARCH_SQL, {"q": dataset_name}).mappings().first()
    except Exception:
        logger.error("query_dataset_trend: DB error during dataset search", exc_info=True)
        return "Error querying DQS data. Please try again later."

    # Step 2: If no match → return not-found message (AC3)
    if matched_row is None:
        return f"No dataset found matching '{dataset_name}'."

    matched_dataset_name: str = matched_row["dataset_name"]
    current_score: float = matched_row["dqs_score"]
    check_status: str = matched_row["check_status"]
    partition_date = matched_row["partition_date"]
    run_id = matched_row["id"]

    # Step 3 + 4: Get trend history using the ds_max CTE pattern
    canonical_time_range: str = _normalize_time_range(time_range)
    days: int = _parse_time_range(time_range)
    days_back: int = days - 1

    try:
        trend_rows = db.execute(
            _TREND_HISTORY_SQL,
            {"dataset_name": matched_dataset_name, "days_back": days_back},
        ).mappings().all()
    except Exception:
        logger.error("query_dataset_trend: DB error fetching trend history", exc_info=True)
        return "Error querying DQS data. Please try again later."

    # Step 5: Compute trend direction from score history
    score_history: list[float] = [float(r["dqs_score"]) for r in trend_rows]
    trend_direction: str = _compute_trend_direction(score_history)

    # Step 6: Get flagged check types for latest run (only for non-PASS runs)
    flagged_checks: list[str] = []
    if check_status != "PASS":
        try:
            check_rows = db.execute(_FAILING_CHECKS_FOR_RUN_SQL, {"run_id": run_id}).mappings().all()
            flagged_checks = [r["check_type"] for r in check_rows]
        except Exception:
            logger.error("query_dataset_trend: DB error fetching flagged checks", exc_info=True)
            return "Error querying DQS data. Please try again later."

    # Format response as LLM-optimised plain text
    lines: list[str] = [
        f"DQS Trend \u2014 dataset: {matched_dataset_name} ({canonical_time_range})",
        f"Current Score: {current_score:.2f} | Status: {check_status} | Date: {partition_date}",
        f"Trend: {trend_direction}",
        "",
        "Score History (oldest \u2192 newest):",
    ]

    for r in trend_rows:
        score_val = float(r["dqs_score"])
        lines.append(f"  {r['date']}: {score_val:.2f}")

    lines.append("")
    flagged_str = ", ".join(flagged_checks) if flagged_checks else "None"
    lines.append(f"Flagged Checks: {flagged_str}")

    return "\n".join(lines)


@mcp.tool(exclude_args=["db"])
def compare_lob_quality(
    db: Session = Depends(get_db),
) -> str:
    """Compare data quality across all Lines of Business (LOBs) ranked by DQS score.

    Use for queries like 'Which LOB has worst quality?' or 'Compare LOB quality' or
    'Show me the quality ranking of all lines of business.'
    Returns LOBs ranked from worst to best by aggregate DQS score, with dataset counts,
    failing dataset counts, and top failing check types per LOB.

    Returns:
        Plain text LOB quality comparison suitable for LLM consumption, or a no-data message
        if no LOB data is available.
    """
    logger.debug("compare_lob_quality called")

    # Step 1: Fetch latest run per dataset using ROW_NUMBER() CTE
    try:
        lob_dataset_rows = db.execute(_LOB_LATEST_PER_DATASET_SQL).mappings().all()
    except Exception:
        logger.error("compare_lob_quality: DB error fetching LOB dataset rows", exc_info=True)
        return "Error querying DQS data. Please try again later."

    # No data case
    if not lob_dataset_rows:
        return "No LOB quality data available."

    # Step 2: Group by lookup_code, compute aggregate score, dataset count, FAIL count
    lob_scores: dict[str, list[float]] = defaultdict(list)
    lob_fail_counts: dict[str, int] = defaultdict(int)
    lob_dataset_counts: dict[str, int] = defaultdict(int)

    for row in lob_dataset_rows:
        lob_code: str = row["lookup_code"]
        lob_scores[lob_code].append(float(row["dqs_score"]))
        lob_dataset_counts[lob_code] += 1
        if row["check_status"] == "FAIL":
            lob_fail_counts[lob_code] += 1

    # Step 3: Get top failing check types per LOB from FAIL runs only
    fail_run_ids: list[int] = [
        row["run_id"] for row in lob_dataset_rows if row["check_status"] == "FAIL"
    ]

    # Map run_id → lob_code for attribution
    run_id_to_lob: dict[int, str] = {
        row["run_id"]: row["lookup_code"]
        for row in lob_dataset_rows
        if row["check_status"] == "FAIL"
    }

    lob_check_types: dict[str, set[str]] = defaultdict(set)

    if fail_run_ids:
        try:
            check_type_rows = db.execute(
                _LOB_CHECK_TYPES_BATCH_SQL,
                {"run_ids": fail_run_ids},
            ).mappings().all()
            for ct_row in check_type_rows:
                run_id_val = ct_row["dq_run_id"]
                lob_code_for_run = run_id_to_lob.get(run_id_val)
                if lob_code_for_run:
                    lob_check_types[lob_code_for_run].add(ct_row["check_type"])
        except Exception:
            logger.error("compare_lob_quality: DB error fetching check types", exc_info=True)
            return "Error querying DQS data. Please try again later."

    # Step 4: Rank LOBs by aggregate score ascending (worst first)
    lob_aggregate_scores: list[tuple[str, float]] = [
        (lob_code, sum(scores) / len(scores))
        for lob_code, scores in lob_scores.items()
    ]
    lob_aggregate_scores.sort(key=lambda x: x[1])

    # Build response
    lines: list[str] = [
        "LOB Quality Comparison \u2014 All LOBs (worst to best)",
        "",
    ]

    for rank, (lob_code, avg_score) in enumerate(lob_aggregate_scores, start=1):
        dataset_count = lob_dataset_counts[lob_code]
        fail_count = lob_fail_counts[lob_code]
        check_types_for_lob = sorted(lob_check_types.get(lob_code, set()))

        line = (
            f"{rank}. {lob_code} \u2014 Score: {avg_score:.2f} | "
            f"{dataset_count} datasets | {fail_count} failing"
        )
        lines.append(line)

        if check_types_for_lob:
            lines.append(f"   Top failing checks: {', '.join(check_types_for_lob)}")

    return "\n".join(lines)
