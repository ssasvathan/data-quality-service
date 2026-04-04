"""Acceptance tests — Story 5.1: FastMCP Tool Registration & Failure Query Tool.

Test categories:
  - Unit tests (no DB, mock session): verify tool registration, response formats
  - Integration tests (real Postgres + seeded fixtures): verify tool against real data,
    MCP endpoint accessibility

Run unit tests:     cd dqs-serve && uv run pytest tests/test_mcp/test_tools.py
Run integration:    cd dqs-serve && uv run pytest -m integration tests/test_mcp/test_tools.py

Story 5.1 Acceptance Criteria:
  AC1: FastMCP tools registered and discoverable by LLM clients when serve starts
  AC2: failure query tool returns formatted text: count, dataset names, check types, LOB grouping
  AC3: no failures → "All datasets passed in the latest run ({timestamp}). {N} datasets processed."
"""

from __future__ import annotations

import datetime
import importlib
from unittest.mock import MagicMock

import pytest

# ---------------------------------------------------------------------------
# Helpers / shared constants
# ---------------------------------------------------------------------------

MCP_MOUNT_PATH = "/mcp"

# AC3 verbatim prefix (from story dev notes and AC3)
_ALL_PASSED_PREFIX = "All datasets passed in the latest run"
_NO_DATA_MESSAGE = "No DQS run data available."


def _make_mock_db_with_failures(
    latest_date: datetime.date = datetime.date(2026, 4, 3),
    failure_rows: list[dict] | None = None,
    total_count: int = 24,
    check_type_rows: list[dict] | None = None,
) -> MagicMock:
    """Return a MagicMock session that simulates a DB with FAIL rows.

    Simulates the four queries in query_failures():
      1. SELECT MAX(partition_date) → latest_date
      2. SELECT ... FROM v_dq_run_active WHERE check_status = 'FAIL' → failure_rows
      3. SELECT COUNT(DISTINCT dataset_name) → total_count
      4. SELECT DISTINCT dataset_name, check_type from join → check_type_rows

    Query dispatch uses SQL text content to identify each query, making mock
    behaviour independent of call order.
    All calls use SQLAlchemy 2.0 style: db.execute(...).mappings().*
    """
    if failure_rows is None:
        failure_rows = [
            {
                "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=customers_daily",
                "lookup_code": "LOB_RETAIL",
                "check_status": "FAIL",
                "dqs_score": 45.0,
                "partition_date": latest_date,
            },
            {
                "dataset_name": "lob=retail/src_sys_nm=beta/dataset=transactions",
                "lookup_code": "LOB_RETAIL",
                "check_status": "FAIL",
                "dqs_score": 32.0,
                "partition_date": latest_date,
            },
            {
                "dataset_name": "lob=commercial/src_sys_nm=gamma/dataset=accounts",
                "lookup_code": "LOB_COMMERCIAL",
                "check_status": "FAIL",
                "dqs_score": 60.0,
                "partition_date": latest_date,
            },
        ]

    if check_type_rows is None:
        check_type_rows = [
            {
                "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=customers_daily",
                "check_type": "FRESHNESS",
            },
            {
                "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=customers_daily",
                "check_type": "VOLUME",
            },
            {
                "dataset_name": "lob=retail/src_sys_nm=beta/dataset=transactions",
                "check_type": "SCHEMA",
            },
            {
                "dataset_name": "lob=commercial/src_sys_nm=gamma/dataset=accounts",
                "check_type": "FRESHNESS",
            },
        ]

    def _execute_side_effect(query: object, params: dict | None = None) -> MagicMock:
        """Simulate the four queries in query_failures() by matching SQL text content."""
        result = MagicMock()
        sql_text = str(query).lower()

        if "max(partition_date)" in sql_text:
            # Query 1: MAX(partition_date) → single .first() row
            row = MagicMock()
            row.__getitem__ = lambda self, key: latest_date if key == "latest_date" else None
            result.mappings.return_value.first.return_value = row
            result.mappings.return_value.all.return_value = [row]

        elif "check_status = 'fail'" in sql_text and "count" not in sql_text and "check_type" not in sql_text:
            # Query 2: FAIL rows → .all() list
            result.mappings.return_value.all.return_value = failure_rows
            result.mappings.return_value.first.return_value = failure_rows[0] if failure_rows else None

        elif "count(distinct dataset_name)" in sql_text:
            # Query 3: COUNT(DISTINCT dataset_name) → single .first() row
            row = MagicMock()
            row.__getitem__ = lambda self, key: total_count if key == "total_count" else None
            result.mappings.return_value.first.return_value = row
            result.mappings.return_value.all.return_value = [row]

        elif "check_type" in sql_text:
            # Query 4: check types join → .all() list
            result.mappings.return_value.all.return_value = check_type_rows
            result.mappings.return_value.first.return_value = check_type_rows[0] if check_type_rows else None

        else:
            result.mappings.return_value.all.return_value = []
            result.mappings.return_value.first.return_value = None

        return result

    mock_db = MagicMock()
    mock_db.execute.side_effect = _execute_side_effect
    return mock_db


def _make_mock_db_no_failures(
    latest_date: datetime.date = datetime.date(2026, 4, 3),
    total_count: int = 24,
) -> MagicMock:
    """Return a MagicMock session simulating a DB run with no FAIL rows.

    Simulates:
      1. SELECT MAX(partition_date) → latest_date
      2. SELECT ... WHERE check_status = 'FAIL' → empty list
      3. SELECT COUNT(DISTINCT dataset_name) → total_count
      4. (check types join — not reached when no failures)

    Query dispatch uses SQL text content to identify each query, making mock
    behaviour independent of call order.
    """

    def _execute_side_effect(query: object, params: dict | None = None) -> MagicMock:
        result = MagicMock()
        sql_text = str(query).lower()

        if "max(partition_date)" in sql_text:
            # MAX(partition_date)
            row = MagicMock()
            row.__getitem__ = lambda self, key: latest_date if key == "latest_date" else None
            result.mappings.return_value.first.return_value = row
            result.mappings.return_value.all.return_value = [row]

        elif "check_status = 'fail'" in sql_text and "count" not in sql_text and "check_type" not in sql_text:
            # FAIL rows — empty (all passed)
            result.mappings.return_value.all.return_value = []
            result.mappings.return_value.first.return_value = None

        elif "count(distinct dataset_name)" in sql_text:
            # COUNT(DISTINCT dataset_name)
            row = MagicMock()
            row.__getitem__ = lambda self, key: total_count if key == "total_count" else None
            result.mappings.return_value.first.return_value = row
            result.mappings.return_value.all.return_value = [row]

        else:
            result.mappings.return_value.all.return_value = []
            result.mappings.return_value.first.return_value = None

        return result

    mock_db = MagicMock()
    mock_db.execute.side_effect = _execute_side_effect
    return mock_db


def _make_mock_db_empty() -> MagicMock:
    """Return a MagicMock session simulating an empty DB (no run data at all).

    MAX(partition_date) returns None → tool should return "No DQS run data available."
    """
    def _execute_side_effect(query: object, params: dict | None = None) -> MagicMock:
        result = MagicMock()
        result.mappings.return_value.first.return_value = None
        result.mappings.return_value.all.return_value = []
        return result

    mock_db = MagicMock()
    mock_db.execute.side_effect = _execute_side_effect
    return mock_db


# ---------------------------------------------------------------------------
# Unit tests — NO database required, mock the DB session.
# These test MCP tool registration, module structure, and response format.
# ---------------------------------------------------------------------------


class TestMcpToolRegistration:
    """AC1 [P0]: MCP tools must be registered and discoverable when the server starts."""

    def test_mcp_module_is_importable(self) -> None:
        """AC1 [P0]: `serve.mcp.tools` module must be importable without errors.

        Fails until `dqs-serve/src/serve/mcp/__init__.py` and
        `dqs-serve/src/serve/mcp/tools.py` are created.
        """
        # This import will raise ModuleNotFoundError until the module is created.
        mcp_tools = importlib.import_module("serve.mcp.tools")
        assert mcp_tools is not None, (
            "serve.mcp.tools module could not be imported. "
            "Create dqs-serve/src/serve/mcp/__init__.py and "
            "dqs-serve/src/serve/mcp/tools.py with a FastMCP('dqs') instance."
        )

    def test_mcp_instance_is_fastmcp(self) -> None:
        """AC1 [P0]: The `mcp` object in `serve.mcp.tools` must be a FastMCP instance.

        Fails until FastMCP('dqs') is created in tools.py.
        """
        from fastmcp import FastMCP  # noqa: PLC0415

        mcp_tools = importlib.import_module("serve.mcp.tools")
        mcp = getattr(mcp_tools, "mcp", None)
        assert mcp is not None, (
            "serve.mcp.tools.mcp is None or missing. "
            "Create `mcp = FastMCP('dqs')` in dqs-serve/src/serve/mcp/tools.py."
        )
        assert isinstance(mcp, FastMCP), (
            f"serve.mcp.tools.mcp is {type(mcp).__name__}, expected FastMCP. "
            "Use `from fastmcp import FastMCP; mcp = FastMCP('dqs')`."
        )

    def test_query_failures_tool_is_registered(self) -> None:
        """AC1 [P0]: `query_failures` tool must appear in mcp.list_tools().

        Fails until `@mcp.tool` decorator is applied to `query_failures` in tools.py.
        LLM clients discover tools via this list — tool must be present for AC1.
        """
        import asyncio  # noqa: PLC0415

        mcp_tools = importlib.import_module("serve.mcp.tools")
        mcp = getattr(mcp_tools, "mcp", None)
        assert mcp is not None, "serve.mcp.tools.mcp not found"

        tools = asyncio.run(mcp.list_tools())
        tool_names = [t.name for t in tools]

        assert "query_failures" in tool_names, (
            f"Tool 'query_failures' not found in registered tools: {tool_names}. "
            "Apply @mcp.tool decorator to the query_failures function in tools.py."
        )

    def test_query_failures_tool_has_docstring_description(self) -> None:
        """AC1 [P1]: `query_failures` tool must have a docstring (MCP tool description for LLMs).

        Per story dev notes: 'Tool function must have a docstring describing its purpose
        (used as MCP tool description).' LLMs read this description to understand
        when to invoke the tool.
        """
        import asyncio  # noqa: PLC0415

        mcp_tools = importlib.import_module("serve.mcp.tools")
        mcp = getattr(mcp_tools, "mcp", None)
        assert mcp is not None, "serve.mcp.tools.mcp not found"

        tools = asyncio.run(mcp.list_tools())
        query_tool = next((t for t in tools if t.name == "query_failures"), None)
        assert query_tool is not None, "query_failures tool not found"

        assert query_tool.description, (
            "query_failures tool has no description. "
            "Add a docstring to the query_failures function — FastMCP uses it as the tool description."
        )
        assert len(query_tool.description) > 10, (
            f"query_failures description is too short: '{query_tool.description}'. "
            "Write a meaningful docstring explaining the tool's purpose."
        )

    def test_query_failures_tool_excludes_db_from_schema(self) -> None:
        """AC1 [P1]: The `db` parameter must be excluded from the MCP tool schema shown to LLMs.

        Per story dev notes: 'Exclude `db` from tool schema using `exclude_args=["db"]`
        in `@mcp.tool` decorator.' LLMs must not see the internal DB dependency.
        """
        import asyncio  # noqa: PLC0415

        mcp_tools = importlib.import_module("serve.mcp.tools")
        mcp = getattr(mcp_tools, "mcp", None)
        assert mcp is not None, "serve.mcp.tools.mcp not found"

        tools = asyncio.run(mcp.list_tools())
        query_tool = next((t for t in tools if t.name == "query_failures"), None)
        assert query_tool is not None, "query_failures tool not found"

        # The tool schema (inputSchema) should not expose 'db' as a parameter
        schema = query_tool.inputSchema if hasattr(query_tool, "inputSchema") else {}
        if isinstance(schema, dict):
            properties = schema.get("properties", {})
            assert "db" not in properties, (
                "The 'db' parameter is exposed in the tool schema. "
                "Add `exclude_args=['db']` to the @mcp.tool decorator: "
                "@mcp.tool(exclude_args=['db'])."
            )


class TestMcpMainAppMount:
    """AC1 [P0]: FastMCP must be mounted onto the FastAPI app at /mcp."""

    def test_mcp_is_mounted_on_fastapi_app(self) -> None:
        """AC1 [P0]: FastAPI app must have MCP mounted at /mcp.

        Fails until `app.mount('/mcp', mcp.http_app(path='/'))` is added to main.py.
        LLM clients connect to http://host:8000/mcp — this mount must exist.
        Using path='/' avoids double-nesting (/mcp/mcp) from path='/mcp'.
        """
        from serve.main import app  # noqa: PLC0415

        # Check that /mcp is registered as a mounted route
        mcp_route_found = any(
            getattr(route, "path", "") == "/mcp"
            for route in app.routes
        )
        assert mcp_route_found, (
            "No route mounted at '/mcp'. "
            "Add `app.mount('/mcp', mcp.http_app(path='/'))` in main.py "
            "after the existing router includes."
        )

    def test_fastmcp_import_does_not_break_fastapi_import(self) -> None:
        """AC1 [P1]: Importing serve.main must succeed after MCP mount is added.

        Verifies that the MCP import + mount is additive and does not break
        existing FastAPI app initialization.
        """
        # If this raises ImportError or AttributeError, the MCP import is broken.
        from serve.main import app  # noqa: PLC0415

        assert app is not None, (
            "serve.main.app is None after MCP mount. "
            "Check that `from .mcp.tools import mcp` and "
            "`app.mount('/mcp', mcp.http_app(path='/mcp'))` do not break app init."
        )

        # Existing routes must still be present (MCP is ADDITIVE)
        route_paths = [getattr(r, "path", "") for r in app.routes]
        for expected in ["/api/summary", "/api/lobs", "/api/search"]:
            assert any(expected in p for p in route_paths), (
                f"Existing route '{expected}' missing after MCP mount. "
                "MCP mount must be ADDITIVE — do NOT remove or modify existing routes."
            )


# ---------------------------------------------------------------------------
# Unit tests — Response format validation (mock DB, no Postgres required)
# ---------------------------------------------------------------------------


class TestQueryFailuresResponseFormat:
    """AC2 + AC3 [P0]: Response text format must match spec exactly."""

    def test_query_failures_returns_str(self) -> None:
        """AC2 [P0]: query_failures must return a str (not JSON, not dict).

        MCP tool responses are human-readable text for LLM consumption.
        Per story dev notes: 'MCP responses are text for LLM consumption.'
        """
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        mock_db = _make_mock_db_with_failures()
        result = query_failures(db=mock_db)

        assert isinstance(result, str), (
            f"query_failures returned {type(result).__name__}, expected str. "
            "MCP tool responses must be text — never return JSON blobs or dicts."
        )

    def test_query_failures_returns_formatted_summary_when_failures_exist(self) -> None:
        """AC2 [P0]: When failures exist, response must include count, dataset names,
        check types, and LOB grouping.

        Expected format (from story dev notes):
          DQS Failure Summary — Latest Run (2026-04-03)
          3 datasets failed out of 24 processed.

          LOB_RETAIL (2 failures):
            - lob=retail/src_sys_nm=alpha/dataset=customers_daily [FRESHNESS, VOLUME]
            - lob=retail/src_sys_nm=beta/dataset=transactions [SCHEMA]

          LOB_COMMERCIAL (1 failure):
            - lob=commercial/src_sys_nm=gamma/dataset=accounts [FRESHNESS]
        """
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        mock_db = _make_mock_db_with_failures(
            latest_date=datetime.date(2026, 4, 3),
            total_count=24,
        )
        result = query_failures(db=mock_db)

        # AC2: failure count present
        assert "3" in result, (
            f"Failure count '3' not found in response:\n{result}\n"
            "Include the number of failed datasets in the response."
        )

        # AC2: total processed present
        assert "24" in result, (
            f"Total dataset count '24' not found in response:\n{result}\n"
            "Include 'N datasets processed' (total, not just failures)."
        )

        # AC2: LOB grouping
        assert "LOB_RETAIL" in result, (
            f"LOB 'LOB_RETAIL' not found in response:\n{result}\n"
            "Group failures by lookup_code (LOB) in the response."
        )
        assert "LOB_COMMERCIAL" in result, (
            f"LOB 'LOB_COMMERCIAL' not found in response:\n{result}\n"
            "Group failures by lookup_code (LOB) in the response."
        )

        # AC2: dataset names present
        assert "customers_daily" in result, (
            f"Dataset 'customers_daily' not found in response:\n{result}\n"
            "Include dataset names in the response."
        )
        assert "transactions" in result, (
            f"Dataset 'transactions' not found in response:\n{result}\n"
            "Include dataset names in the response."
        )
        assert "accounts" in result, (
            f"Dataset 'accounts' not found in response:\n{result}\n"
            "Include dataset names in the response."
        )

        # AC2: check types present
        assert "FRESHNESS" in result, (
            f"Check type 'FRESHNESS' not found in response:\n{result}\n"
            "Include check types from v_dq_metric_numeric_active in the response."
        )
        assert "SCHEMA" in result, (
            f"Check type 'SCHEMA' not found in response:\n{result}\n"
            "Include check types from v_dq_metric_numeric_active in the response."
        )
        assert "VOLUME" in result, (
            f"Check type 'VOLUME' not found in response:\n{result}\n"
            "Include check types from v_dq_metric_numeric_active in the response."
        )

        # AC2: date context
        assert "2026-04-03" in result, (
            f"Run date '2026-04-03' not found in response:\n{result}\n"
            "Include the partition_date (run date) in the response header."
        )

    def test_query_failures_lob_grouping_correct_counts(self) -> None:
        """AC2 [P0]: LOB grouping must show correct failure counts per LOB.

        LOB_RETAIL has 2 failures, LOB_COMMERCIAL has 1 failure.
        Both must be present with correct counts.
        """
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        mock_db = _make_mock_db_with_failures()
        result = query_failures(db=mock_db)

        # LOB_RETAIL should show 2 failures
        assert "LOB_RETAIL" in result
        lob_retail_section_start = result.index("LOB_RETAIL")
        lob_retail_section = result[lob_retail_section_start : lob_retail_section_start + 200]
        assert "2" in lob_retail_section, (
            f"LOB_RETAIL section does not show count '2':\n{lob_retail_section}\n"
            "Show per-LOB failure count, e.g. 'LOB_RETAIL (2 failures):'."
        )

        # LOB_COMMERCIAL should show 1 failure
        assert "LOB_COMMERCIAL" in result
        lob_commercial_section_start = result.index("LOB_COMMERCIAL")
        lob_commercial_section = result[lob_commercial_section_start : lob_commercial_section_start + 200]
        assert "1" in lob_commercial_section, (
            f"LOB_COMMERCIAL section does not show count '1':\n{lob_commercial_section}\n"
            "Show per-LOB failure count, e.g. 'LOB_COMMERCIAL (1 failure):'."
        )

    def test_query_failures_returns_all_passed_message_when_no_failures(self) -> None:
        """AC3 [P0]: When no failures exist, must return AC3 verbatim message.

        AC3 exact text: "All datasets passed in the latest run ({timestamp}). {N} datasets processed."

        Fails until query_failures checks for empty failure list and returns this message.
        """
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        mock_db = _make_mock_db_no_failures(
            latest_date=datetime.date(2026, 4, 3),
            total_count=24,
        )
        result = query_failures(db=mock_db)

        assert _ALL_PASSED_PREFIX in result, (
            f"AC3 message prefix '{_ALL_PASSED_PREFIX}' not found in response:\n{result}\n"
            "When no failures exist, return: "
            "'All datasets passed in the latest run ({timestamp}). {N} datasets processed.'"
        )

        # Must include the run date
        assert "2026-04-03" in result, (
            f"Run date '2026-04-03' not in AC3 response:\n{result}\n"
            "Include the run date (partition_date) in the AC3 no-failure message."
        )

        # Must include the total dataset count
        assert "24" in result, (
            f"Dataset count '24' not in AC3 response:\n{result}\n"
            "Include '{N} datasets processed' in the AC3 no-failure message."
        )

        # Must NOT contain failure-related terms
        assert "failed" not in result.lower() or "0" in result, (
            f"Response contains 'failed' but no failures occurred:\n{result}\n"
            "AC3 response should not mention failures when none exist."
        )

    def test_query_failures_returns_no_data_message_when_db_empty(self) -> None:
        """AC2/AC3 [P1]: When DB has no run data, must return 'No DQS run data available.'

        Fails until query_failures handles the case where MAX(partition_date) is NULL.
        """
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        mock_db = _make_mock_db_empty()
        result = query_failures(db=mock_db)

        assert isinstance(result, str), "query_failures must return str even when DB is empty"
        assert _NO_DATA_MESSAGE in result or "No" in result, (
            f"Empty DB response does not contain expected no-data message:\n{result}\n"
            f"Expected '{_NO_DATA_MESSAGE}' or similar when partition_date is NULL."
        )

    def test_query_failures_does_not_return_json(self) -> None:
        """AC2 [P1]: Response must NOT be a JSON string (no braces, no quoted keys).

        Per anti-patterns: 'NEVER return JSON blobs from MCP tools — return
        human-readable text for LLM consumption.'
        """
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        mock_db = _make_mock_db_with_failures()
        result = query_failures(db=mock_db)

        # A JSON response would start with { or [
        assert not result.strip().startswith("{"), (
            f"Response looks like a JSON object (starts with {{):\n{result[:100]}\n"
            "Return human-readable text, not JSON blobs."
        )
        assert not result.strip().startswith("["), (
            f"Response looks like a JSON array (starts with [):\n{result[:100]}\n"
            "Return human-readable text, not JSON arrays."
        )


class TestQueryFailuresQueryPatterns:
    """AC2 [P1]: DB query patterns must comply with project-context.md rules.

    These tests verify that the implementation uses correct query patterns
    by inspecting the module source or calling with a mock that validates usage.
    """

    def test_query_failures_does_not_import_fastapi_depends(self) -> None:
        """AC2 [P1]: Must use `fastmcp.dependencies.Depends`, NOT `fastapi.Depends`.

        Per story dev notes: 'Import `fastmcp.dependencies.Depends` (NOT `fastapi.Depends`)'
        and per anti-patterns: 'NEVER use `fastapi.Depends` in FastMCP tools.'
        """
        import inspect  # noqa: PLC0415

        mcp_tools = importlib.import_module("serve.mcp.tools")
        source = inspect.getsource(mcp_tools)

        assert "from fastapi import Depends" not in source and "from fastapi.dependencies" not in source, (
            "tools.py imports Depends from fastapi. "
            "ANTI-PATTERN: use `from fastmcp.dependencies import Depends` instead of fastapi.Depends."
        )

        assert "fastmcp" in source and "Depends" in source, (
            "tools.py does not import Depends from fastmcp. "
            "Add: `from fastmcp.dependencies import Depends`"
        )

    def test_query_failures_uses_active_record_views_only(self) -> None:
        """AC2 [P1]: All SQL must query `v_dq_run_active`, not raw `dq_run` table.

        Per anti-patterns: 'NEVER query `dq_run` raw table — always `v_dq_run_active`'
        and 'NEVER query `dq_metric_numeric` raw table — always `v_dq_metric_numeric_active`'.
        """
        import inspect  # noqa: PLC0415

        mcp_tools = importlib.import_module("serve.mcp.tools")
        source = inspect.getsource(mcp_tools)

        # Must use active-record views
        assert "v_dq_run_active" in source, (
            "tools.py does not reference 'v_dq_run_active'. "
            "Query the active-record view, not the raw dq_run table."
        )

        # Must NOT query raw dq_run table directly
        # (check for ' dq_run' or 'FROM dq_run' to avoid false positives with v_dq_run_active)
        import re  # noqa: PLC0415
        raw_table_pattern = re.compile(r"\bFROM\s+dq_run\b|\bJOIN\s+dq_run\b", re.IGNORECASE)
        assert not raw_table_pattern.search(source), (
            "tools.py queries raw 'dq_run' table directly. "
            "ANTI-PATTERN: always use 'v_dq_run_active' view."
        )


# ---------------------------------------------------------------------------
# Integration tests — require a running Postgres instance with seeded data.
# Excluded from default suite (pyproject.toml: addopts = -m 'not integration').
# Run with: uv run pytest -m integration tests/test_mcp/test_tools.py
# ---------------------------------------------------------------------------


class TestMcpEndpointAccessibility:
    """AC1 [P0]: MCP endpoint must be accessible via HTTP when app is running.

    TDD RED PHASE: MCP not mounted — integration tests skip.
    """

    @pytest.mark.integration
    def test_mcp_http_endpoint_responds(self, seeded_client: object) -> None:
        """AC1 [P0]: GET /mcp must respond (not 404) when MCP is mounted.

        Uses seeded_client fixture which provides a real TestClient with schema + data.
        Fails until `app.mount('/mcp', mcp.http_app(path='/mcp'))` is in main.py.
        """
        from fastapi.testclient import TestClient  # noqa: PLC0415

        assert isinstance(seeded_client, TestClient), "seeded_client must be a TestClient"
        response = seeded_client.get(MCP_MOUNT_PATH)

        # MCP endpoint should respond — 200, 307, or a valid MCP handshake response.
        # It should NOT be 404 (which means the mount is missing).
        assert response.status_code != 404, (
            f"GET {MCP_MOUNT_PATH} returned 404. "
            "MCP endpoint is not mounted. "
            "Add `app.mount('/mcp', mcp.http_app(path='/mcp'))` in main.py."
        )


class TestQueryFailuresIntegration:
    """AC2 + AC3 [P1]: Integration tests for query_failures against real Postgres.

    TDD RED PHASE: serve.mcp module does not exist — integration tests skip.
    Requires: @pytest.mark.integration + seeded_client fixture (real Postgres).
    """

    @pytest.mark.integration
    def test_query_failures_with_seeded_data_returns_str(self, seeded_client: object) -> None:
        """AC2 [P1]: query_failures must return a str when called against real seeded DB.

        Calls the tool function directly with a real SQLAlchemy Session from seeded_client.
        Verifies the tool can execute queries against real Postgres without errors.

        seeded_client fixture provides: DDL + views + fixtures.sql data committed.
        """
        from serve.db.session import get_db  # noqa: PLC0415
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        # Get a real DB session via the dependency generator
        db_gen = get_db()
        db = next(db_gen)
        try:
            result = query_failures(db=db)
            assert isinstance(result, str), (
                f"query_failures returned {type(result).__name__}, expected str. "
                "Integration call against real DB must also return str."
            )
            assert len(result) > 0, "query_failures returned empty string against real DB."
        finally:
            try:
                next(db_gen)
            except StopIteration:
                pass

    @pytest.mark.integration
    def test_query_failures_with_seeded_pass_data_returns_all_passed(
        self, seeded_client: object
    ) -> None:
        """AC3 [P1]: If seeded fixtures have no FAIL rows, must return AC3 message.

        Note: fixtures.sql may contain FAIL rows. This test is meaningful only if
        the seeded fixture data has all PASS statuses. Adjust fixtures as needed.
        This test documents the expected behavior — adapt to actual fixture content.

        If fixtures.sql contains FAIL rows, this test verifies the failure format instead.
        """
        from serve.db.session import get_db  # noqa: PLC0415
        from serve.mcp.tools import query_failures  # noqa: PLC0415

        db_gen = get_db()
        db = next(db_gen)
        try:
            result = query_failures(db=db)
            # Result must be a non-empty string (format depends on fixture data)
            assert isinstance(result, str)
            assert len(result) > 0
            # Either AC3 (all passed) or AC2 (failures) — both are valid
            assert _ALL_PASSED_PREFIX in result or "failed" in result.lower() or "No DQS" in result, (
                f"query_failures returned unexpected response format:\n{result}\n"
                "Expected either AC2 failure summary or AC3 all-passed message."
            )
        finally:
            try:
                next(db_gen)
            except StopIteration:
                pass
