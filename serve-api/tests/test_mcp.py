import pytest
from mcp.server.fastmcp import FastMCP
from dqs_api.mcp_server import mcp

def test_mcp_server_initialization():
    assert isinstance(mcp, FastMCP)
    assert mcp.name == "DQS MCP Server"
    tools = mcp._tool_manager.list_tools()
    assert any(t.name == "get_dqs_datasets" for t in tools)
