"""pytest fixtures for dqs-serve tests.

Test against real Postgres per project-context.md testing rules.
Temporal pattern and active-record views need real DB validation.
"""
import pytest
from fastapi.testclient import TestClient
from serve.main import app


@pytest.fixture
def client() -> TestClient:
    """Return a TestClient for the FastAPI app."""
    return TestClient(app)
