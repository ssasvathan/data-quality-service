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
