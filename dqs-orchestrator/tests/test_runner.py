"""Acceptance tests for orchestrator runner — Story 3-2: Spark-Submit Runner with Failure Isolation.

TDD RED PHASE: These tests are written before implementation.
They WILL FAIL until runner.py implements run_spark_job() and run_all_paths()
per the story acceptance criteria.

AC Coverage:
  AC1 — spark-submit invoked once per parent path with correct args
  AC2 — failure isolation: one failed path does not halt others; failure is captured
  AC3 — exit code 0 → path recorded as successful
"""

from datetime import date
from unittest.mock import MagicMock, patch

import pytest

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
    """AC1: spark-submit command includes --parent-path, --date, spark flags, and app_jar."""
    with patch("subprocess.run", return_value=make_completed_process(0)) as mock_run:
        run_spark_job("/data/risk/credit", date(2026, 3, 25), SPARK_CONFIG)

    cmd = mock_run.call_args[0][0]
    assert "--parent-path" in cmd
    assert "/data/risk/credit" in cmd
    assert "--date" in cmd
    assert "20260325" in cmd
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
