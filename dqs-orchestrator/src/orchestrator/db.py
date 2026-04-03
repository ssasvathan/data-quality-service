"""Database helpers for dqs-orchestrator — placeholder for story 1-3+.

Uses psycopg2-binary (not SQLAlchemy) for lightweight CLI usage.
"""
import os
from typing import Any

import psycopg2


def get_connection() -> Any:
    """Return a psycopg2 connection using environment or config defaults."""
    dsn: str = os.getenv("DATABASE_URL", "postgresql://postgres:localdev@localhost:5432/postgres")
    return psycopg2.connect(dsn)
