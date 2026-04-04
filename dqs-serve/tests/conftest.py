"""pytest fixtures for dqs-serve tests.

Test against real Postgres per project-context.md testing rules.
Temporal pattern and active-record views need real DB validation.
"""
import os
import pathlib
from typing import Generator
from unittest.mock import MagicMock

import psycopg2
import psycopg2.extensions
import pytest
from fastapi.testclient import TestClient
from serve.db.session import get_db
from serve.main import app
from serve.services.reference_data import LobMapping, ReferenceDataService

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://postgres:localdev@localhost:5432/postgres",
)

# Paths to schema files — relative to this conftest.py
_DDL_PATH = pathlib.Path(__file__).parent.parent / "src" / "serve" / "schema" / "ddl.sql"
_VIEWS_PATH = pathlib.Path(__file__).parent.parent / "src" / "serve" / "schema" / "views.sql"
_FIXTURES_PATH = pathlib.Path(__file__).parent.parent / "src" / "serve" / "schema" / "fixtures.sql"


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
            # Drop all DQS tables in reverse dependency order to handle FK constraints.
            # This ensures a clean slate even if a previous seeded_client fixture
            # committed DDL and data (seeded_client commits so its app connection can read data).
            cur.execute(
                """
                DROP TABLE IF EXISTS dq_metric_detail CASCADE;
                DROP TABLE IF EXISTS dq_metric_numeric CASCADE;
                DROP TABLE IF EXISTS dq_run CASCADE;
                DROP TABLE IF EXISTS dq_orchestration_run CASCADE;
                DROP TABLE IF EXISTS check_config CASCADE;
                DROP TABLE IF EXISTS dataset_enrichment CASCADE;
                DROP TABLE IF EXISTS lob_lookup CASCADE;
                DROP VIEW IF EXISTS v_dq_run_active CASCADE;
                DROP VIEW IF EXISTS v_dq_metric_numeric_active CASCADE;
                DROP VIEW IF EXISTS v_dq_metric_detail_active CASCADE;
                DROP VIEW IF EXISTS v_check_config_active CASCADE;
                DROP VIEW IF EXISTS v_dataset_enrichment_active CASCADE;
                DROP VIEW IF EXISTS v_dq_orchestration_run_active CASCADE;
                DROP VIEW IF EXISTS v_lob_lookup_active CASCADE;
                """
            )
            cur.execute(_DDL_PATH.read_text())
            cur.execute(_VIEWS_PATH.read_text())
        yield conn
    finally:
        conn.rollback()  # Undo DDL + views DDL + any DML inserted during the test
        conn.close()


@pytest.fixture
def seeded_client(db_conn: psycopg2.extensions.connection) -> Generator[TestClient, None, None]:
    """TestClient with schema + fixtures.sql seeded into a real Postgres DB.

    Builds on db_conn (which creates DDL + views in a transaction), then seeds
    fixtures.sql data and commits so the FastAPI app's OWN session can read it.

    The FastAPI app opens its own SQLAlchemy session (via get_db dependency) which
    is a separate connection from db_conn. The fixtures must be committed before the
    TestClient request so the app's connection can see the data.

    Teardown: After yield, db_conn fixture rolls back the entire transaction,
    cleaning up both DDL and DML. Because we committed the fixtures, the rollback
    will only undo changes made AFTER the commit (i.e., any DML from the test
    itself if it uses db_conn). The initial seeded data persists until the
    db_conn teardown calls conn.rollback() which undoes the DDL, effectively
    dropping all tables.

    Usage::

        @pytest.mark.integration
        def test_summary_returns_correct_counts(seeded_client):
            response = seeded_client.get('/api/summary')
            assert response.status_code == 200

    Note: If a test also needs db_conn directly (e.g., to insert extra rows),
    it must request BOTH fixtures. seeded_client already wraps db_conn, so
    requesting seeded_client and db_conn uses the SAME connection object.

    Requires @pytest.mark.integration — excluded from default suite per pyproject.toml.
    """
    # Seed fixture data into the transaction that db_conn started.
    with db_conn.cursor() as cur:
        cur.execute(_FIXTURES_PATH.read_text())
    # Commit so the FastAPI app's separate DB connection can read the seeded data.
    db_conn.commit()

    yield TestClient(app)

    # db_conn fixture handles final rollback/cleanup in its own teardown.


# ---------------------------------------------------------------------------
# Mock DB dependency override for unit tests (non-integration)
# ---------------------------------------------------------------------------


def _make_mock_db_session() -> MagicMock:
    """Return a MagicMock that mimics a SQLAlchemy Session with context-aware results.

    Provides a MagicMock session where db.execute(...).mappings().all() returns:
      - Empty list when the LOB parameter is not a known test LOB (triggers 404 for NONEXISTENT)
      - A single fake dataset row when LOB_RETAIL is queried (confirms route is registered)
      - Empty list for all other queries (summary totals = 0, lobs list = [], etc.)

    This satisfies route handlers across unit tests:
      - GET /api/summary → SummaryResponse with one LOB (200)
      - GET /api/lobs → list with one LOB (200)
      - GET /api/lobs/LOB_RETAIL/datasets → first execute returns one row → 200 (route registered)
      - GET /api/lobs/NONEXISTENT/datasets → first execute returns empty → 404 (LOB not found)
      - GET /api/datasets/9 → dataset_id=9 → fake DatasetDetail run row → 200
      - GET /api/datasets/9999 → dataset_id=9999 → empty → 404 (unknown)
      - GET /api/datasets/9/metrics → dataset_id=9 → first call returns run row, metric calls empty
      - GET /api/datasets/9/trend → dataset_id=9 → first call returns run row, trend call returns point
      - GET /api/search?q=sales → q='sales' → fake search result row → 200
      - GET /api/search?q=ZZZNOMATCH → q='ZZZNOMATCH' → empty → 200 with results=[]

    Query dispatch logic (by param keys present):
      - No params          → _LATEST_PER_DATASET_SQL or _LOBS_LATEST_SQL → fake LOB row
      - q (search param)   → _SEARCH_SQL (checked first, distinct param key)
          - q in (sales, alpha, retail) → fake search result row
          - other q values              → empty list (no-match → results=[])
      - days_back only     → _ALL_LOBS_TREND_BATCH_SQL (summary trend) → fake trend row with lookup_code
      - lob_id only        → _DATASET_LATEST_FOR_LOB_SQL
          - NONEXISTENT    → empty (triggers 404)
          - other lob_id   → fake dataset row (run_id key)
      - dataset_names      → _DATASET_TREND_BATCH_SQL → empty (no sparkline in unit tests)
      - run_ids            → _METRIC_CHECK_TYPES_BATCH_SQL → empty (no metrics in unit tests)
      - dataset_id (≠9999) → GET /api/datasets/{id} detail/metrics/trend → fake detail run row
      - dataset_id (=9999) → unknown dataset → empty list (triggers 404)
      - dataset_name       → trend or previous_row_count sub-query → fake trend point
    """
    import datetime  # noqa: PLC0415

    # run_id is the PK of dq_run (renamed from dataset_id to avoid naming confusion)
    _FAKE_DATASET_ROW = {
        "run_id": 1,
        "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
        "dqs_score": 98.50,
        "check_status": "PASS",
        "partition_date": datetime.date(2026, 4, 2),
    }

    _FAKE_LOB_ROW = {
        "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
        "lookup_code": "LOB_RETAIL",
        "check_status": "PASS",
        "dqs_score": 98.50,
        "partition_date": datetime.date(2026, 4, 2),
    }

    _FAKE_SUMMARY_TREND_ROW = {
        "lookup_code": "LOB_RETAIL",
        "day": datetime.date(2026, 4, 2),
        "avg_score": 98.50,
    }

    # Fake row for GET /api/datasets/{dataset_id} — matches DatasetDetail fields
    # dataset_id=9 is the well-known unit-test sentinel (mirrors story dev notes)
    _FAKE_DATASET_DETAIL_RUN_ROW = {
        "id": 9,
        "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
        "lookup_code": "LOB_RETAIL",
        "check_status": "PASS",
        "dqs_score": 98.50,
        "partition_date": datetime.date(2026, 4, 2),
        "rerun_number": 0,
        "orchestration_run_id": 1,
        "error_message": None,
        "create_date": datetime.datetime(2026, 4, 2, 6, 45, 0),
    }

    # Fake trend point for GET /api/datasets/{dataset_id}/trend
    _FAKE_TREND_ROW = {
        "date": datetime.date(2026, 4, 2),
        "dqs_score": 98.50,
    }

    # Fake orchestration run row for parent_path lookup
    _FAKE_ORCHESTRATION_RUN_ROW = {
        "id": 1,
        "parent_path": "lob=retail/src_sys_nm=alpha",
    }

    # Fake search result row for GET /api/search?q=<known_query>
    # id=9 matches _FAKE_DATASET_DETAIL_RUN_ROW sentinel for consistency
    _FAKE_SEARCH_RESULT_ROW = {
        "id": 9,
        "dataset_name": "lob=retail/src_sys_nm=alpha/dataset=sales_daily",
        "lookup_code": "LOB_RETAIL",
        "dqs_score": 98.50,
        "check_status": "PASS",
    }

    def _execute_side_effect(query: object, params: dict | None = None) -> MagicMock:
        """Return mock results based on query parameters.

        Distinguishes query types by inspecting param keys:
          - No params         → latest-per-dataset (summary) or lobs list → fake LOB row
          - q (search param)  → search query (checked first — distinct param key)
              - q in (sales, alpha, retail) → fake search result row
              - other q values              → empty list (no-match → results=[])
          - days_back only    → batched summary LOB trend → fake trend row with lookup_code
          - lob_id only       → dataset-latest-for-LOB
              - NONEXISTENT   → empty (triggers 404)
              - other lob_id  → fake dataset row (run_id key)
          - dataset_names     → batched dataset trend → empty (no sparkline in unit tests)
          - run_ids           → batched metric check types → empty (no metrics in unit tests)
          - dataset_id        → GET /api/datasets/{id} queries
              - 9999          → empty (triggers 404)
              - other id      → fake DatasetDetail run row (200)
          - dataset_name      → sub-queries for previous_row_count or trend → fake rows
          - orchestration_run_id → parent_path lookup → fake orchestration row
        """
        result = MagicMock()
        rows: list = []

        if params and isinstance(params, dict):
            q = params.get("q")
            run_ids = params.get("run_ids")
            dataset_names = params.get("dataset_names")
            days_back = params.get("days_back")
            lob_id = params.get("lob_id", "")
            dataset_id = params.get("dataset_id")
            dataset_name = params.get("dataset_name")
            orchestration_run_id = params.get("orchestration_run_id")

            if q is not None:
                # Search query — return fake search result for known queries, empty for no-match
                # q_prefix may also be present (used in CASE sort expression) — handled here too
                if q.lower() in ("sales", "alpha", "retail"):
                    rows = [_FAKE_SEARCH_RESULT_ROW]
                else:
                    rows = []  # no match → empty results array (AC3: not a 4xx error)
            elif run_ids is not None:
                # Batched metric check types query — empty (no per-check metrics in unit tests)
                rows = []
            elif dataset_names is not None:
                # Batched dataset trend query — empty (no sparkline data in unit tests)
                rows = []
            elif days_back is not None and dataset_id is None and dataset_name is None:
                # Batched summary LOB trend query — return a fake trend row with lookup_code
                rows = [_FAKE_SUMMARY_TREND_ROW]
            elif dataset_id is not None:
                # Dataset detail / metrics / trend queries by dataset_id
                if dataset_id == 9999:
                    # Unknown dataset — triggers 404
                    rows = []
                else:
                    # Known dataset (unit test sentinel id=9)
                    rows = [_FAKE_DATASET_DETAIL_RUN_ROW]
            elif dataset_name is not None:
                # Sub-queries by dataset_name:
                #   - previous_row_count (VOLUME/row_count OFFSET 1) → empty in unit tests
                #   - trend window query → fake trend point
                if days_back is not None:
                    # Trend window query
                    rows = [_FAKE_TREND_ROW]
                else:
                    # previous_row_count OFFSET 1 query — return empty (None previous)
                    rows = []
            elif orchestration_run_id is not None:
                # parent_path lookup from orchestration run
                rows = [_FAKE_ORCHESTRATION_RUN_ROW]
            elif lob_id and lob_id != "NONEXISTENT":
                # Dataset latest-for-LOB query for a known LOB
                rows = [_FAKE_DATASET_ROW]
            # else: NONEXISTENT lob or unknown param combination → empty list
        elif params is None:
            # No params — lobs list query or summary latest-per-dataset query.
            # Return one fake LOB row so snake_case key validation tests have data.
            rows = [_FAKE_LOB_ROW]

        result.mappings.return_value.all.return_value = rows
        return result

    mock_session = MagicMock()
    mock_session.execute.side_effect = _execute_side_effect
    return mock_session


@pytest.fixture(autouse=True)
def override_db_dependency(request: pytest.FixtureRequest) -> Generator[None, None, None]:
    """Auto-use fixture: override get_db with a mock for unit tests.

    For tests NOT marked @pytest.mark.integration, replace get_db with a mock
    that returns empty results so route tests pass without a real Postgres instance.

    For integration tests, remove the override so the real get_db is used.
    This fixture is harmless for tests that don't use the FastAPI TestClient.
    """
    if request.node.get_closest_marker("integration") is None:
        # Unit test — inject mock DB session
        mock_session = _make_mock_db_session()
        app.dependency_overrides[get_db] = lambda: mock_session
        yield
        app.dependency_overrides.pop(get_db, None)
    else:
        # Integration test — use real DB, no override
        app.dependency_overrides.pop(get_db, None)
        yield


@pytest.fixture(autouse=True)
def mock_reference_data_service(request: pytest.FixtureRequest) -> Generator[None, None, None]:
    """Auto-use fixture: inject a mock ReferenceDataService for unit tests.

    For tests NOT marked @pytest.mark.integration:
      - Sets app.state.reference_data to a predictable mock that returns:
          - LobMapping("Retail Banking", "Jane Doe", "Tier 1 Critical") for "LOB_RETAIL"
          - LobMapping("N/A", "N/A", "N/A") for all other codes
      - Patches serve.main.SessionLocal with a no-op DB factory so that any
        TestClient(app) lifespan startup (e.g. test_app_has_lifespan_that_sets_reference_data_state)
        does not attempt a real Postgres connection.

    For integration tests, leaves app.state untouched (the seeded_client fixture
    manages the real lifespan indirectly via the TestClient).

    Per story dev notes (4.5): ReferenceDataService is a singleton on app.state,
    not a per-request FastAPI Depends() factory.
    """
    from unittest.mock import patch  # noqa: PLC0415

    if request.node.get_closest_marker("integration") is None:
        def _mock_resolve(lookup_code: object) -> LobMapping:
            if lookup_code == "LOB_RETAIL":
                return LobMapping(
                    lob_name="Retail Banking",
                    owner="Jane Doe",
                    classification="Tier 1 Critical",
                )
            return LobMapping(lob_name="N/A", owner="N/A", classification="N/A")

        # Build a no-op DB session factory for the lifespan's ReferenceDataService.
        # This prevents connection attempts when TestClient(app) triggers lifespan startup
        # in unit tests (e.g. TestLifespanAndServiceWiring).
        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = []

        # Set a predictable mock on app.state for route tests that use
        # the module-level `client` fixture (which does NOT run lifespan).
        mock_svc = MagicMock(spec=ReferenceDataService)
        mock_svc.resolve.side_effect = _mock_resolve
        app.state.reference_data = mock_svc

        # Patch only the SessionLocal used by the lifespan in main.py so that
        # a TestClient(app) context manager doesn't need a real Postgres connection.
        # This does NOT affect ReferenceDataService instances created directly in tests
        # with their own mock db_factory arguments.
        with patch("serve.main.SessionLocal", return_value=mock_db):
            yield

        # Clean up: remove reference_data from app.state after unit test
        try:
            del app.state.reference_data
        except AttributeError:
            pass
    else:
        # Integration test — real ReferenceDataService via lifespan; do not mock
        yield
