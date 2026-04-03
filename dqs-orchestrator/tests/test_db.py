"""Acceptance tests for db helpers — Story 3-3 + Story 3-4.

TDD RED PHASE (Story 3-3): Tests that were failing until Story 3-3 implemented:
  - create_orchestration_run() — now exists
  - finalize_orchestration_run() — now exists
  - EXPIRY_SENTINEL constant — now exists

AC Coverage (Story 3-3):
  AC1 — create_orchestration_run inserts row with run_status='running' and returns auto-generated id
  AC2 — finalize_orchestration_run updates row with end_time, counts, and correct run_status

TDD RED PHASE (Story 3-4): All tests in the Story 3-4 section WILL FAIL until:
  - expire_previous_run() does not exist in db.py yet
  - get_next_rerun_number() does not exist in db.py yet

AC Coverage (Story 3-4):
  AC1 — get_next_rerun_number returns MAX(rerun_number)+1 across all history
  AC2 — expire_previous_run sets expiry_date on dq_run AND cascades to both metric tables in one transaction
  AC3 — (Audit trail) full history preserved in raw tables; only latest rerun visible via active views
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


# ---------------------------------------------------------------------------
# Story 3-4 ATDD tests — TDD RED PHASE
# All tests below WILL FAIL until expire_previous_run() and
# get_next_rerun_number() are added to db.py.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# AC2: expire_previous_run — sets expiry_date on dq_run and cascades to metrics
# ---------------------------------------------------------------------------


def test_expire_previous_run_updates_expiry_date_on_dq_run() -> None:
    """AC2: expire_previous_run() executes UPDATE on dq_run with correct params.

    TDD RED: Will raise ImportError until expire_previous_run is added to db.py.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import EXPIRY_SENTINEL, expire_previous_run  # noqa: PLC0415

    expiry_ts = datetime(2026, 3, 25, 10, 5, 0)
    partition_date = date(2026, 3, 25)
    # Simulate: cursor finds one active run row (id=7, rerun_number=0)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[7, 0])

    result = expire_previous_run(mock_conn, "ue90-omni-transactions", partition_date, expiry_ts)

    # Must return the expired run's rerun_number (0) for next-rerun-number calculation
    assert result == 0

    # First execute call must be the dq_run UPDATE
    first_call_sql = mock_cursor.execute.call_args_list[0][0][0]
    assert "UPDATE dq_run" in first_call_sql
    assert "expiry_date" in first_call_sql

    # The UPDATE params must include: expiry_ts, dataset_name, partition_date, EXPIRY_SENTINEL
    first_call_params = mock_cursor.execute.call_args_list[0][0][1]
    assert expiry_ts in first_call_params
    assert "ue90-omni-transactions" in first_call_params
    assert partition_date in first_call_params
    assert EXPIRY_SENTINEL in first_call_params


def test_expire_previous_run_cascades_to_metric_numeric() -> None:
    """AC2: expire_previous_run() also updates dq_metric_numeric with the run's id.

    TDD RED: Will fail until expire_previous_run exists and cascades metric expiry.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import expire_previous_run  # noqa: PLC0415

    expiry_ts = datetime(2026, 3, 25, 10, 5, 0)
    partition_date = date(2026, 3, 25)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[55, 1])

    expire_previous_run(mock_conn, "ue90-omni-transactions", partition_date, expiry_ts)

    # Three execute calls expected: UPDATE dq_run, UPDATE dq_metric_numeric, UPDATE dq_metric_detail
    assert mock_cursor.execute.call_count == 3, (
        f"Expected 3 execute calls (dq_run + numeric + detail), got {mock_cursor.execute.call_count}"
    )

    # Second call must be dq_metric_numeric UPDATE
    second_call_sql = mock_cursor.execute.call_args_list[1][0][0]
    assert "dq_metric_numeric" in second_call_sql
    assert "expiry_date" in second_call_sql

    # Second call params must include the run_id=55 returned from dq_run query
    second_call_params = mock_cursor.execute.call_args_list[1][0][1]
    assert 55 in second_call_params


def test_expire_previous_run_cascades_to_metric_detail() -> None:
    """AC2: expire_previous_run() also updates dq_metric_detail with the run's id.

    TDD RED: Will fail until expire_previous_run exists and cascades metric detail expiry.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import expire_previous_run  # noqa: PLC0415

    expiry_ts = datetime(2026, 3, 25, 10, 5, 0)
    partition_date = date(2026, 3, 25)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[99, 2])

    expire_previous_run(mock_conn, "ue90-omni-transactions", partition_date, expiry_ts)

    # Third call must be dq_metric_detail UPDATE
    third_call_sql = mock_cursor.execute.call_args_list[2][0][0]
    assert "dq_metric_detail" in third_call_sql
    assert "expiry_date" in third_call_sql

    # Third call params must include the run_id=99 returned from dq_run query
    third_call_params = mock_cursor.execute.call_args_list[2][0][1]
    assert 99 in third_call_params


def test_expire_previous_run_commits_all_three_updates_in_one_transaction() -> None:
    """AC2: expire_previous_run() calls conn.commit() once after all three UPDATEs.

    TDD RED: Will fail until expire_previous_run commits atomically.
    Per story: all three UPDATEs must execute within one transaction.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import expire_previous_run  # noqa: PLC0415

    expiry_ts = datetime(2026, 3, 25, 10, 5, 0)
    partition_date = date(2026, 3, 25)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[42, 0])

    expire_previous_run(mock_conn, "ue90-omni-transactions", partition_date, expiry_ts)

    # conn.commit() called exactly once (one transaction for all 3 UPDATEs)
    mock_conn.commit.assert_called_once()


def test_expire_previous_run_returns_none_when_no_active_run() -> None:
    """AC2 (edge): expire_previous_run() returns None when no active run found.

    TDD RED: Will fail until expire_previous_run handles missing rows gracefully.
    First-time run — no active run to expire. Must return None without raising.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import expire_previous_run  # noqa: PLC0415

    expiry_ts = datetime(2026, 3, 25, 10, 5, 0)
    partition_date = date(2026, 3, 25)
    # No rows returned (no active run for this dataset)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=None)

    result = expire_previous_run(mock_conn, "new-dataset-first-run", partition_date, expiry_ts)

    assert result is None, (
        "expire_previous_run must return None when no active run exists (first-time run — not an error)"
    )
    # Must still commit even when no rows found (commit after empty fetchone)
    mock_conn.commit.assert_called()


def test_expire_previous_run_uses_expiry_sentinel_constant_in_where_clause() -> None:
    """AC2: expire_previous_run() uses EXPIRY_SENTINEL in the WHERE clause — never hardcodes sentinel.

    TDD RED: Will fail until expire_previous_run uses EXPIRY_SENTINEL properly.
    Per project-context.md: always use EXPIRY_SENTINEL constant, never hardcode '9999-12-31 23:59:59'.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import EXPIRY_SENTINEL, expire_previous_run  # noqa: PLC0415

    expiry_ts = datetime(2026, 3, 25, 10, 5, 0)
    partition_date = date(2026, 3, 25)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[1, 0])

    expire_previous_run(mock_conn, "ue90-omni-transactions", partition_date, expiry_ts)

    # The WHERE clause of the dq_run UPDATE must use EXPIRY_SENTINEL as a param
    first_call_params = mock_cursor.execute.call_args_list[0][0][1]
    assert EXPIRY_SENTINEL in first_call_params, (
        "EXPIRY_SENTINEL must appear in SQL params — never hardcode '9999-12-31 23:59:59' inline"
    )


# ---------------------------------------------------------------------------
# AC1: get_next_rerun_number — returns MAX(rerun_number)+1 across all history
# ---------------------------------------------------------------------------


def test_get_next_rerun_number_returns_zero_when_no_history() -> None:
    """AC1: get_next_rerun_number() returns 0 when no previous runs exist (first-time run).

    TDD RED: Will raise ImportError until get_next_rerun_number is added to db.py.
    Formula: COALESCE(MAX(rerun_number), -1) + 1 = COALESCE(NULL, -1) + 1 = 0.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import get_next_rerun_number  # noqa: PLC0415

    partition_date = date(2026, 3, 25)
    # COALESCE(MAX(NULL), -1)+1 = 0 when no rows exist
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[0])

    result = get_next_rerun_number(mock_conn, "new-dataset", partition_date)

    assert result == 0, (
        "get_next_rerun_number must return 0 when no previous runs exist (first-time run)"
    )
    mock_cursor.execute.assert_called_once()


def test_get_next_rerun_number_returns_one_when_first_run_exists() -> None:
    """AC1: get_next_rerun_number() returns 1 when one run exists with rerun_number=0.

    TDD RED: Will fail until get_next_rerun_number exists in db.py.
    Formula: COALESCE(MAX(0), -1) + 1 = 0 + 1 = 1 (first rerun).
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import get_next_rerun_number  # noqa: PLC0415

    partition_date = date(2026, 3, 25)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[1])

    result = get_next_rerun_number(mock_conn, "ue90-omni-transactions", partition_date)

    assert result == 1, (
        "get_next_rerun_number must return 1 when one run with rerun_number=0 exists"
    )


def test_get_next_rerun_number_queries_all_history_including_expired() -> None:
    """AC1: get_next_rerun_number() queries ALL dq_run rows — not just active ones.

    TDD RED: Will fail until get_next_rerun_number exists and queries across all history.
    Must NOT filter by expiry_date — we need MAX across all reruns including expired ones.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import get_next_rerun_number  # noqa: PLC0415

    partition_date = date(2026, 3, 25)
    mock_conn, mock_cursor = make_mock_conn(fetchone_return=[3])

    result = get_next_rerun_number(mock_conn, "ue90-omni-transactions", partition_date)

    assert result == 3

    # The SQL must NOT include 'expiry_date' in the WHERE clause — must query all history
    call_sql = mock_cursor.execute.call_args[0][0]
    assert "expiry_date" not in call_sql.lower(), (
        "get_next_rerun_number must query ALL rows (no expiry_date filter) "
        "— MAX must cover expired history too"
    )
    # Must include the dataset_name and partition_date as params
    call_params = mock_cursor.execute.call_args[0][1]
    assert "ue90-omni-transactions" in call_params
    assert partition_date in call_params


# ---------------------------------------------------------------------------
# Story 3-5 ATDD tests — TDD RED PHASE
# All tests below WILL FAIL until query_run_summary() is added to db.py.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# AC1: query_run_summary — returns orchestration run + failed datasets + check-type counts
# ---------------------------------------------------------------------------


def test_query_run_summary_returns_orchestration_run_data() -> None:
    """AC1: query_run_summary() returns a dict with dq_orchestration_run fields.

    TDD RED: Will raise ImportError until query_run_summary is added to db.py.
    The returned dict must have keys matching RunSummary field names:
    run_id, parent_path, start_time, end_time, total_datasets, passed_datasets,
    failed_datasets, error_summary.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import query_run_summary  # noqa: PLC0415

    start_time = datetime(2026, 4, 3, 8, 0, 0)
    end_time = datetime(2026, 4, 3, 9, 15, 0)

    mock_conn, mock_cursor = make_mock_conn()
    # Query 1: dq_orchestration_run row
    # Columns: id, parent_path, run_status, start_time, end_time,
    #          total_datasets, passed_datasets, failed_datasets, error_summary
    mock_cursor.fetchone.return_value = (
        42, "/data/finance/loans", "completed", start_time, end_time,
        10, 8, 2, None,
    )
    # Query 2: failed datasets (list)
    # Query 3: check-type failure counts (list)
    mock_cursor.fetchall.side_effect = [
        [("ue90-omni-transactions",), ("ue90-card-balances",)],  # failed datasets
        [("volume", 3), ("schema", 2)],  # check-type counts
    ]

    partition_date = date(2026, 4, 3)
    result = query_run_summary(mock_conn, orchestration_run_id=42, partition_date=partition_date)

    assert result["run_id"] == 42, "result['run_id'] must equal orchestration_run_id"
    assert result["parent_path"] == "/data/finance/loans", "result must include parent_path"
    assert result["start_time"] == start_time, "result must include start_time from the DB row"
    assert result["end_time"] == end_time, "result must include end_time from the DB row"
    assert result["total_datasets"] == 10, "result must include total_datasets count"
    assert result["passed_datasets"] == 8, "result must include passed_datasets count"
    assert result["failed_datasets"] == 2, "result must include failed_datasets count"


def test_query_run_summary_returns_failed_datasets() -> None:
    """AC1: query_run_summary() returns failed dataset names as a list under 'failed_dataset_names'.

    TDD RED: Will fail until query_run_summary executes Query 2 and maps results to
    'failed_dataset_names' key in the returned dict.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import query_run_summary  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn()
    mock_cursor.fetchone.return_value = (
        7, "/data/risk/credit", "completed_with_errors",
        datetime(2026, 4, 3, 8, 0, 0), datetime(2026, 4, 3, 9, 0, 0),
        5, 3, 2, None,
    )
    mock_cursor.fetchall.side_effect = [
        [("risk-dataset-a",), ("risk-dataset-b",)],  # Query 2: failed datasets
        [],  # Query 3: check-type counts (empty)
    ]

    partition_date = date(2026, 4, 3)
    result = query_run_summary(mock_conn, orchestration_run_id=7, partition_date=partition_date)

    assert "failed_dataset_names" in result, (
        "result dict must have 'failed_dataset_names' key for RunSummary(**result) unpacking"
    )
    assert "risk-dataset-a" in result["failed_dataset_names"], (
        "failed_dataset_names must include 'risk-dataset-a' from dq_run query"
    )
    assert "risk-dataset-b" in result["failed_dataset_names"], (
        "failed_dataset_names must include 'risk-dataset-b' from dq_run query"
    )


def test_query_run_summary_returns_check_type_failure_counts() -> None:
    """AC1: query_run_summary() returns check-type failure counts as dict under 'check_type_failures'.

    TDD RED: Will fail until query_run_summary executes Query 3 and maps results to
    'check_type_failures' key as dict[str, int] in the returned dict.
    """
    from datetime import date  # noqa: PLC0415

    from orchestrator.db import query_run_summary  # noqa: PLC0415

    mock_conn, mock_cursor = make_mock_conn()
    mock_cursor.fetchone.return_value = (
        99, "/data/finance/loans", "completed_with_errors",
        datetime(2026, 4, 3, 8, 0, 0), None,
        3, 0, 3, None,
    )
    mock_cursor.fetchall.side_effect = [
        [("dataset-x",), ("dataset-y",), ("dataset-z",)],  # Query 2: failed datasets
        [("volume", 3), ("schema", 2)],  # Query 3: check-type counts
    ]

    partition_date = date(2026, 4, 3)
    result = query_run_summary(mock_conn, orchestration_run_id=99, partition_date=partition_date)

    assert "check_type_failures" in result, (
        "result dict must have 'check_type_failures' key for RunSummary(**result) unpacking"
    )
    assert result["check_type_failures"].get("volume") == 3, (
        "check_type_failures['volume'] must equal 3 (from grouped COUNT query)"
    )
    assert result["check_type_failures"].get("schema") == 2, (
        "check_type_failures['schema'] must equal 2 (from grouped COUNT query)"
    )
