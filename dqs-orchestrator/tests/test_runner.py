"""Acceptance tests for orchestrator runner.

AC Coverage (runner):
  AC1 — spark-submit invoked once per parent path with correct args
  AC2 — failure isolation: one failed path does not halt others; failure is captured
  AC3 — exit code 0 → path recorded as successful

AC Coverage (orchestration tracking):
  AC3 — run_spark_job appends --orchestration-run-id to spark-submit command when provided
  AC3 — run_all_paths threads orchestration_run_ids dict through to each run_spark_job call

AC Coverage (rerun management):
  AC1 — run_spark_job appends --rerun-number to spark-submit command when provided
  AC1 — run_all_paths threads rerun_numbers dict through to each run_spark_job call
"""

from datetime import date
from unittest.mock import MagicMock, call, patch

from orchestrator.models import JobResult
from orchestrator.runner import run_all_paths, run_spark_job

# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

SPARK_CONFIG = {
    "submit_path": "spark-submit",
    "master": "yarn",
    "deploy_mode": "cluster",
    "driver_memory": "2g",
    "executor_memory": "4g",
    "app_jar": "dqs-spark.jar",
}


def make_completed_process(returncode: int, stderr: str = "") -> MagicMock:
    """Build a mock CompletedProcess with the given returncode and stderr."""
    mock = MagicMock()
    mock.returncode = returncode
    mock.stderr = stderr
    return mock


# ---------------------------------------------------------------------------
# AC3: exit code 0 → success
# ---------------------------------------------------------------------------


def test_run_spark_job_returns_success_on_exit_code_0() -> None:
    """AC3: Given spark-submit returns exit code 0, run_spark_job records path as successful."""
    with patch("subprocess.run", return_value=make_completed_process(0)):
        result = run_spark_job("/data/finance/loans", date(2026, 3, 25), SPARK_CONFIG)

    assert result.success is True
    assert result.parent_path == "/data/finance/loans"


# ---------------------------------------------------------------------------
# AC2: non-zero exit → failure captured with exit code and stderr
# ---------------------------------------------------------------------------


def test_run_spark_job_returns_failure_on_nonzero_exit() -> None:
    """AC2: Given spark-submit returns non-zero exit code, failure is captured with exit code and stderr."""
    with patch("subprocess.run", return_value=make_completed_process(1, stderr="OOM killed")):
        result = run_spark_job("/data/finance/loans", date(2026, 3, 25), SPARK_CONFIG)

    assert result.success is False
    assert "exit_code=1" in result.error_message
    assert "OOM killed" in result.error_message


# ---------------------------------------------------------------------------
# AC1: correct command construction
# ---------------------------------------------------------------------------


def test_run_spark_job_builds_correct_command() -> None:
    """AC1: spark-submit command includes --parent-path, --date, spark flags, and app_jar.

    Verifies flag adjacency: --parent-path immediately precedes its value,
    --date immediately precedes its value.
    """
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job("/data/risk/credit", date(2026, 3, 25), SPARK_CONFIG)

    cmd = mock_run.call_args[0][0]

    # Verify --parent-path is immediately followed by its value
    parent_path_idx = cmd.index("--parent-path")
    assert cmd[parent_path_idx + 1] == "/data/risk/credit"

    # Verify --date is immediately followed by its value
    date_idx = cmd.index("--date")
    assert cmd[date_idx + 1] == "20260325"

    assert "dqs-spark.jar" in cmd
    assert "--master" in cmd
    assert "yarn" in cmd

    # Must NOT use shell=True
    call_kwargs = mock_run.call_args[1] if mock_run.call_args[1] else {}
    assert call_kwargs.get("shell", False) is False


def test_run_spark_job_passes_datasets_when_provided() -> None:
    """AC1: spark-submit command includes --datasets args when datasets parameter is provided."""
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job("/data/risk/credit", date(2026, 3, 25), SPARK_CONFIG, datasets=["ue90-omni"])

    cmd = mock_run.call_args[0][0]
    assert "--datasets" in cmd
    assert "ue90-omni" in cmd


def test_run_spark_job_passes_datasets_when_empty_list() -> None:
    """AC1: spark-submit command includes --datasets flag even when datasets is an empty list."""
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job("/data/risk/credit", date(2026, 3, 25), SPARK_CONFIG, datasets=[])

    cmd = mock_run.call_args[0][0]
    assert "--datasets" in cmd


# ---------------------------------------------------------------------------
# AC1 + AC2: run_all_paths — failure isolation
# ---------------------------------------------------------------------------


def test_run_all_paths_processes_all_paths_when_middle_fails() -> None:
    """AC2 (core): failure isolation — path 2 fails, paths 1 and 3 are still processed."""
    side_effects = [
        make_completed_process(0),                        # path 1: success
        make_completed_process(1, stderr="YARN error"),   # path 2: failure
        make_completed_process(0),                        # path 3: success
    ]
    with patch("subprocess.run", side_effect=side_effects):
        results = run_all_paths(
            ["/data/finance/loans", "/data/finance/deposits", "/data/risk/credit"],
            date(2026, 3, 25),
            SPARK_CONFIG,
        )

    assert len(results) == 3
    assert results[0].success is True
    assert results[1].success is False
    assert results[2].success is True


def test_run_all_paths_returns_correct_success_failure_results() -> None:
    """AC1+AC2+AC3: run_all_paths returns a JobResult per path with correct success/failure status."""
    side_effects = [
        make_completed_process(0),
        make_completed_process(2, stderr="job failed"),
    ]
    with patch("subprocess.run", side_effect=side_effects):
        results = run_all_paths(
            ["/data/finance/loans", "/data/finance/deposits"],
            date(2026, 3, 25),
            SPARK_CONFIG,
        )

    assert len(results) == 2
    assert results[0].parent_path == "/data/finance/loans"
    assert results[0].success is True
    assert results[1].parent_path == "/data/finance/deposits"
    assert results[1].success is False
    assert "exit_code=2" in results[1].error_message


def test_run_all_paths_continues_after_unexpected_exception() -> None:
    """AC2: isolation holds even when run_spark_job raises an unexpected Python exception."""
    with patch(
        "orchestrator.runner.run_spark_job",
        side_effect=[
            JobResult("/data/finance/loans", success=True),
            RuntimeError("unexpected internal error"),
            JobResult("/data/risk/credit", success=True),
        ],
    ):
        results = run_all_paths(
            ["/data/finance/loans", "/data/finance/deposits", "/data/risk/credit"],
            date(2026, 3, 25),
            SPARK_CONFIG,
        )

    assert len(results) == 3
    assert results[0].success is True
    assert results[1].success is False
    assert "Unexpected error" in results[1].error_message
    assert results[2].success is True


def test_run_all_paths_passes_datasets_to_each_run_spark_job_call() -> None:
    """AC1: run_all_paths forwards the datasets argument to every run_spark_job call."""
    datasets = ["ue90-omni", "another-ds"]
    paths = ["/data/finance/loans", "/data/finance/deposits"]
    partition_date = date(2026, 3, 25)

    with patch(
        "orchestrator.runner.run_spark_job",
        return_value=JobResult("/data/finance/loans", success=True),
    ) as mock_run_spark:
        run_all_paths(paths, partition_date, SPARK_CONFIG, datasets=datasets)

    assert mock_run_spark.call_count == 2
    expected_calls = [
        call("/data/finance/loans", partition_date, SPARK_CONFIG, datasets, orchestration_run_id=None, rerun_number=None),
        call("/data/finance/deposits", partition_date, SPARK_CONFIG, datasets, orchestration_run_id=None, rerun_number=None),
    ]
    mock_run_spark.assert_has_calls(expected_calls)


# ---------------------------------------------------------------------------
# orchestration_run_id passthrough
# ---------------------------------------------------------------------------


def test_run_spark_job_appends_orchestration_run_id_to_command() -> None:
    """run_spark_job appends --orchestration-run-id to spark-submit when provided."""
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job(
            "/data/finance/loans",
            date(2026, 3, 25),
            SPARK_CONFIG,
            orchestration_run_id=99,
        )

    cmd = mock_run.call_args[0][0]
    assert "--orchestration-run-id" in cmd
    orch_id_idx = cmd.index("--orchestration-run-id")
    assert cmd[orch_id_idx + 1] == "99"


def test_run_spark_job_omits_orchestration_run_id_when_none() -> None:
    """run_spark_job does NOT append --orchestration-run-id when orchestration_run_id is None."""
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job(
            "/data/finance/loans",
            date(2026, 3, 25),
            SPARK_CONFIG,
            orchestration_run_id=None,
        )

    cmd = mock_run.call_args[0][0]
    assert "--orchestration-run-id" not in cmd


def test_run_all_paths_threads_orchestration_run_ids_to_each_path() -> None:
    """run_all_paths looks up orchestration_run_id per path and passes it to run_spark_job."""
    paths = ["/data/finance/loans", "/data/finance/deposits"]
    partition_date = date(2026, 3, 25)
    orchestration_run_ids = {
        "/data/finance/loans": 10,
        "/data/finance/deposits": 11,
    }

    with patch(
        "orchestrator.runner.run_spark_job",
        return_value=JobResult("/data/finance/loans", success=True),
    ) as mock_run_spark:
        run_all_paths(
            paths,
            partition_date,
            SPARK_CONFIG,
            orchestration_run_ids=orchestration_run_ids,
        )

    assert mock_run_spark.call_count == 2
    first_call_kwargs = mock_run_spark.call_args_list[0]
    second_call_kwargs = mock_run_spark.call_args_list[1]

    # Verify orchestration_run_id is passed correctly for each path
    # run_spark_job(path, partition_date, spark_config, datasets, orchestration_run_id=...)
    assert first_call_kwargs == call(
        "/data/finance/loans", partition_date, SPARK_CONFIG, None, orchestration_run_id=10, rerun_number=None
    )
    assert second_call_kwargs == call(
        "/data/finance/deposits", partition_date, SPARK_CONFIG, None, orchestration_run_id=11, rerun_number=None
    )


def test_run_all_paths_passes_none_run_id_for_path_not_in_dict() -> None:
    """run_all_paths passes orchestration_run_id=None for paths not in the dict."""
    paths = ["/data/finance/loans", "/data/finance/deposits"]
    partition_date = date(2026, 3, 25)
    # Only one path in the dict — the other should get None
    orchestration_run_ids = {"/data/finance/loans": 10}

    with patch(
        "orchestrator.runner.run_spark_job",
        return_value=JobResult("/data/finance/loans", success=True),
    ) as mock_run_spark:
        run_all_paths(
            paths,
            partition_date,
            SPARK_CONFIG,
            orchestration_run_ids=orchestration_run_ids,
        )

    second_call = mock_run_spark.call_args_list[1]
    assert second_call == call(
        "/data/finance/deposits", partition_date, SPARK_CONFIG, None, orchestration_run_id=None, rerun_number=None
    )


# ---------------------------------------------------------------------------
# rerun_number passthrough
# ---------------------------------------------------------------------------


def test_run_spark_job_appends_rerun_number_to_command() -> None:
    """run_spark_job appends --rerun-number to spark-submit when rerun_number provided."""
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job(
            "/data/finance/loans",
            date(2026, 3, 25),
            SPARK_CONFIG,
            rerun_number=2,
        )

    cmd = mock_run.call_args[0][0]
    assert "--rerun-number" in cmd, (
        "spark-submit command must include --rerun-number when rerun_number is provided"
    )
    rerun_idx = cmd.index("--rerun-number")
    assert cmd[rerun_idx + 1] == "2", (
        "--rerun-number must be immediately followed by the string value '2'"
    )


def test_run_spark_job_omits_rerun_number_when_none() -> None:
    """run_spark_job does NOT append --rerun-number when rerun_number is None."""
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job(
            "/data/finance/loans",
            date(2026, 3, 25),
            SPARK_CONFIG,
            rerun_number=None,
        )

    cmd = mock_run.call_args[0][0]
    assert "--rerun-number" not in cmd, (
        "--rerun-number must NOT appear in command when rerun_number=None"
    )


def test_run_all_paths_threads_rerun_numbers_to_spark_job() -> None:
    """run_all_paths computes rerun_number from rerun_numbers dict and passes to run_spark_job."""
    paths = ["/data/finance/loans"]
    partition_date = date(2026, 3, 25)
    rerun_numbers = {"ue90-omni-transactions": 2}

    with patch(
        "orchestrator.runner.run_spark_job",
        return_value=JobResult("/data/finance/loans", success=True),
    ) as mock_run_spark:
        run_all_paths(
            paths,
            partition_date,
            SPARK_CONFIG,
            datasets=["ue90-omni-transactions"],
            rerun_numbers=rerun_numbers,
        )

    assert mock_run_spark.call_count == 1
    call_kwargs = mock_run_spark.call_args[1]
    assert "rerun_number" in call_kwargs, (
        "run_spark_job must be called with rerun_number kwarg when rerun_numbers is provided"
    )
    assert call_kwargs["rerun_number"] == 2, (
        "rerun_number must be 2 (max value from rerun_numbers dict)"
    )


def test_run_all_paths_passes_none_rerun_number_when_rerun_numbers_is_none() -> None:
    """run_all_paths passes rerun_number=None to run_spark_job when rerun_numbers is None."""
    paths = ["/data/finance/loans"]
    partition_date = date(2026, 3, 25)

    with patch(
        "orchestrator.runner.run_spark_job",
        return_value=JobResult("/data/finance/loans", success=True),
    ) as mock_run_spark:
        run_all_paths(
            paths,
            partition_date,
            SPARK_CONFIG,
            rerun_numbers=None,
        )

    call_kwargs = mock_run_spark.call_args[1]
    # rerun_number should be None (not passed as a flag to spark-submit)
    assert call_kwargs.get("rerun_number") is None, (
        "rerun_number must be None when rerun_numbers=None (non-rerun run)"
    )
