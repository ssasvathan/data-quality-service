"""Acceptance tests for db helpers — Story 3-3: Orchestration Run Tracking & Error Capture.

TDD RED PHASE: All tests in this file are written for expected behavior.
They WILL FAIL until the implementation is complete because:
  - create_orchestration_run() does not exist in db.py yet
  - finalize_orchestration_run() does not exist in db.py yet
  - EXPIRY_SENTINEL constant does not exist in db.py yet

AC Coverage:
  AC1 — create_orchestration_run inserts row with run_status='running' and returns auto-generated id
  AC2 — finalize_orchestration_run updates row with end_time, counts, and correct run_status
"""

from datetime import datetime
from unittest.mock import MagicMock, patch


def make_mock_conn(fetchone_return=None):
    """Return a mock psycopg2 connection with cursor context manager support."""
    mock_cursor = MagicMock()
    mock_cursor.fetchone.return_value = fetchone_return if fetchone_return is not None else [42]
    mock_cursor.__enter__ = lambda s: mock_cursor
    mock_cursor.__exit__ = MagicMock(return_value=False)
    mock_conn = MagicMock()
    mock_conn.cursor.return_value = mock_cursor
    return mock_conn, mock_cursor


# ---------------------------------------------------------------------------
# AC1: create_orchestration_run — INSERT with run_status='running', returns id
# ---------------------------------------------------------------------------


def test_create_orchestration_run_returns_id() -> None:
    """AC1: create_orchestration_run executes INSERT and returns the auto-generated id from RETURNING id."""
    # TDD RED: This will raise ImportError until create_orchestration_run is added to db.py
    from orchestrator.db import create_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[42])
    result = create_orchestration_run(mock_conn, "/data/finance/loans", datetime(2026, 3, 25, 8, 0))

    assert result == 42
    mock_cursor.execute.assert_called_once()
    mock_conn.commit.assert_called_once()


def test_create_orchestration_run_inserts_running_status() -> None:
    """AC1: create_orchestration_run inserts run_status='running' in the SQL parameters."""
    # TDD RED: Will fail until create_orchestration_run exists and executes correct SQL
    from orchestrator.db import create_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[7])
    create_orchestration_run(mock_conn, "/data/risk/credit", datetime(2026, 3, 25, 9, 0))

    call_args = mock_cursor.execute.call_args[0][1]  # second positional arg = params tuple
    assert "running" in call_args


def test_create_orchestration_run_uses_expiry_sentinel() -> None:
    """AC1: create_orchestration_run uses EXPIRY_SENTINEL constant in SQL params — never hardcoded string.

    Per project-context.md temporal pattern: EXPIRY_SENTINEL must be a module-level constant.
    """
    # TDD RED: Will fail until EXPIRY_SENTINEL and create_orchestration_run both exist in db.py
    from orchestrator.db import EXPIRY_SENTINEL, create_orchestration_run  # noqa: PLC0415

    assert EXPIRY_SENTINEL == "9999-12-31 23:59:59", (
        "EXPIRY_SENTINEL must equal '9999-12-31 23:59:59' per project-context.md temporal pattern"
    )

    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[1])
    create_orchestration_run(mock_conn, "/data/finance/loans", datetime.now())

    call_args = mock_cursor.execute.call_args[0][1]
    assert EXPIRY_SENTINEL in call_args, (
        "EXPIRY_SENTINEL value must appear in SQL params — never hardcode '9999-12-31 23:59:59' inline"
    )


def test_create_orchestration_run_includes_parent_path_and_start_time() -> None:
    """AC1: create_orchestration_run includes parent_path and start_time in SQL params."""
    # TDD RED: Will fail until create_orchestration_run exists
    from orchestrator.db import create_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[5])
    start_time = datetime(2026, 3, 25, 8, 30, 0)
    create_orchestration_run(mock_conn, "/data/finance/loans", start_time)

    call_args = mock_cursor.execute.call_args[0][1]
    assert "/data/finance/loans" in call_args
    assert start_time in call_args


# ---------------------------------------------------------------------------
# AC2: finalize_orchestration_run — UPDATE with correct run_status
# ---------------------------------------------------------------------------


def test_finalize_orchestration_run_sets_completed_on_zero_failures() -> None:
    """AC2: finalize_orchestration_run sets run_status='completed' when failed_datasets == 0."""
    # TDD RED: Will fail until finalize_orchestration_run exists in db.py
    from orchestrator.db import finalize_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn()
    finalize_orchestration_run(
        mock_conn,
        run_id=42,
        end_time=datetime.now(),
        total_datasets=None,
        passed_datasets=None,
        failed_datasets=0,
        error_summary=None,
    )

    call_args = mock_cursor.execute.call_args[0][1]
    assert "completed" in call_args
    assert "completed_with_errors" not in call_args


def test_finalize_orchestration_run_sets_completed_with_errors_on_failures() -> None:
    """AC2: finalize_orchestration_run sets run_status='completed_with_errors' when failed_datasets > 0."""
    # TDD RED: Will fail until finalize_orchestration_run exists in db.py
    from orchestrator.db import finalize_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn()
    finalize_orchestration_run(
        mock_conn,
        run_id=42,
        end_time=datetime.now(),
        total_datasets=10,
        passed_datasets=8,
        failed_datasets=2,
        error_summary="path /data/risk/credit failed: exit_code=1",
    )

    call_args = mock_cursor.execute.call_args[0][1]
    assert "completed_with_errors" in call_args


def test_finalize_orchestration_run_includes_error_summary() -> None:
    """AC2: finalize_orchestration_run passes error_summary in SQL params."""
    # TDD RED: Will fail until finalize_orchestration_run exists in db.py
    from orchestrator.db import finalize_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn()
    error_summary = "path /data/risk/credit failed: exit_code=1 stderr=OOM"
    finalize_orchestration_run(
        mock_conn,
        run_id=42,
        end_time=datetime.now(),
        total_datasets=5,
        passed_datasets=4,
        failed_datasets=1,
        error_summary=error_summary,
    )

    call_args = mock_cursor.execute.call_args[0][1]
    assert error_summary in call_args


def test_finalize_orchestration_run_commits_transaction() -> None:
    """AC2: finalize_orchestration_run calls conn.commit() to persist the UPDATE."""
    # TDD RED: Will fail until finalize_orchestration_run exists in db.py
    from orchestrator.db import finalize_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn()
    finalize_orchestration_run(mock_conn, 99, datetime.now(), None, None, 0, None)

    mock_conn.commit.assert_called_once()


def test_finalize_orchestration_run_uses_correct_run_id() -> None:
    """AC2: finalize_orchestration_run passes run_id as WHERE clause param in UPDATE."""
    # TDD RED: Will fail until finalize_orchestration_run exists in db.py
    from orchestrator.db import finalize_orchestration_run  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn()
    finalize_orchestration_run(mock_conn, 77, datetime.now(), None, None, 0, None)

    call_args = mock_cursor.execute.call_args[0][1]
    assert 77 in call_args


# ---------------------------------------------------------------------------
# AC1 (infrastructure): get_connection uses DATABASE_URL env var
# ---------------------------------------------------------------------------


def test_get_connection_uses_database_url_env(monkeypatch) -> None:
    """AC1 (infra): get_connection() uses DATABASE_URL env var when set."""
    monkeypatch.setenv("DATABASE_URL", "postgresql://user:pass@myhost:5432/mydb")
    with patch("psycopg2.connect") as mock_connect:
        mock_connect.return_value = MagicMock()
        from orchestrator.db import get_connection  # noqa: PLC0415
        get_connection()
        mock_connect.assert_called_once_with("postgresql://user:pass@myhost:5432/mydb")
