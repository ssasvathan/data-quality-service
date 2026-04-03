# Epic 5: MCP & LLM Integration

LLM consumers can query data quality information via natural language MCP tools, getting the same data as the dashboard through conversational interfaces.

## Story 5.1: FastMCP Tool Registration & Failure Query Tool

As an **LLM consumer**,
I want an MCP tool that answers "What failed last night?",
So that I can get a quick failure summary without opening the dashboard.

**Acceptance Criteria:**

**Given** FastMCP is configured in dqs-serve
**When** the serve layer starts
**Then** MCP tools are registered and discoverable by LLM clients

**Given** an LLM consumer asks "What failed last night?"
**When** the MCP failure query tool executes
**Then** it calls the same API endpoints the dashboard uses
**And** returns a formatted text response: count of failures, dataset names, check types that failed, and LOB grouping

**Given** no failures occurred in the last run
**When** the failure query tool executes
**Then** it returns "All datasets passed in the latest run ({timestamp}). {N} datasets processed."

## Story 5.2: MCP Trending & Comparison Tools

As an **LLM consumer**,
I want MCP tools for trending queries and LOB comparisons,
So that I can get trend analysis and quality comparisons conversationally.

**Acceptance Criteria:**

**Given** an LLM consumer asks "Show trending for dataset ue90-omni-transactions"
**When** the MCP trending tool executes
**Then** it returns a formatted summary: current DQS score, trend direction (improving/degrading/stable), score history over the requested period, and flagged checks

**Given** an LLM consumer asks "Which LOB has worst quality?"
**When** the MCP comparison tool executes
**Then** it returns LOBs ranked by DQS score (ascending), with dataset counts and top failing check types per LOB

**Given** an LLM consumer queries a dataset that doesn't exist
**When** the MCP tool executes
**Then** it returns a clear message: "No dataset found matching '{query}'."

---
