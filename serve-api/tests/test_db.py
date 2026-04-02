import pytest
from dqs_api.db import get_engine

def test_engine_creation():
    engine = get_engine("postgresql+pg8000://postgres:localdev@localhost:5433/postgres")
    assert engine.url.database == "postgres"
    assert engine.url.port == 5433
