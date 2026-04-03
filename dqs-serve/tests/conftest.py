"""pytest fixtures for dqs-serve tests.

Test against real Postgres per project-context.md testing rules.
Temporal pattern and active-record views need real DB validation.
"""
import os
import pathlib
from typing import Generator

import psycopg2
import psycopg2.extensions
import pytest
from fastapi.testclient import TestClient
from serve.main import app

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://postgres:localdev@localhost:5432/postgres",
)

# Paths to schema files — relative to this conftest.py
_DDL_PATH = pathlib.Path(__file__).parent.parent / "src" / "serve" / "schema" / "ddl.sql"
_VIEWS_PATH = pathlib.Path(__file__).parent.parent / "src" / "serve" / "schema" / "views.sql"


@pytest.fixture
def client() -> TestClient:
    """Return a TestClient for the FastAPI app."""
    return TestClient(app)


@pytest.fixture
def db_conn() -> Generator[psycopg2.extensions.connection, None, None]:
    """Real Postgres connection for integration tests.

    Each test is wrapped in an explicit transaction that is rolled back on
    teardown, leaving the DB in a clean state regardless of test outcome.

    The DDL (CREATE TABLE statements) and views DDL are executed inside the
    same transaction. Postgres allows DDL inside a transaction, and ROLLBACK
    undoes it, so each test starts with a fresh schema and no residual data.

    views.sql is executed after ddl.sql because views depend on tables existing.

    Requires a running Postgres instance.  Mark tests that use this fixture
    with @pytest.mark.integration so they are excluded from the default suite
    (pyproject.toml: addopts = -m 'not integration').

    Usage::

        @pytest.mark.integration
        def test_something(db_conn):
            cur = db_conn.cursor()
            ...  # DB is empty, dq_run table exists after DDL ran, views exist
    """
    conn = psycopg2.connect(DATABASE_URL)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            cur.execute(_DDL_PATH.read_text())
            cur.execute(_VIEWS_PATH.read_text())
        yield conn
    finally:
        conn.rollback()  # Undo DDL + views DDL + any DML inserted during the test
        conn.close()
