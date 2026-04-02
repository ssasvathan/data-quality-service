from mcp.server.fastmcp import FastMCP
from sqlalchemy import text
from .db import get_engine

mcp = FastMCP("DQS MCP Server")

@mcp.tool()
def get_dqs_datasets() -> list[dict]:
    """Retrieve all monitored datasets from the Data Quality Service."""
    engine = get_engine()
    with engine.connect() as conn:
        result = conn.execute(text("SELECT dataset_id, src_sys_nm FROM dataset")).mappings().all()
        return [dict(row) for row in result]

if __name__ == "__main__":
    mcp.run()
