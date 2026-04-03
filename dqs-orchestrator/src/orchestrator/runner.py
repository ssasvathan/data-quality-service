"""Spark submit runner — invokes spark-submit per parent path with per-path failure isolation."""
import logging
import subprocess
from datetime import date
from typing import Any

from orchestrator.models import JobResult

logger = logging.getLogger(__name__)


def run_spark_job(
    parent_path: str,
    partition_date: date,
    spark_config: dict[str, Any],
    datasets: list[str] | None = None,
    orchestration_run_id: int | None = None,
    rerun_number: int | None = None,
) -> JobResult:
    """Submit a Spark DQ job for the given parent path.

    Builds and runs the spark-submit command. Returns JobResult with success status.
    Caller provides per-path isolation via run_all_paths().
    """
    submit_path = spark_config.get("submit_path", "spark-submit")
    app_jar = spark_config.get("app_jar", "dqs-spark.jar")

    cmd = [
        submit_path,
        "--master", spark_config.get("master", "yarn"),
        "--deploy-mode", spark_config.get("deploy_mode", "cluster"),
        "--driver-memory", spark_config.get("driver_memory", "2g"),
        "--executor-memory", spark_config.get("executor_memory", "4g"),
        app_jar,
        "--parent-path", parent_path,
        "--date", partition_date.strftime("%Y%m%d"),
    ]

    if datasets is not None:
        cmd.extend(["--datasets"] + datasets)

    if orchestration_run_id is not None:
        cmd.extend(["--orchestration-run-id", str(orchestration_run_id)])

    if rerun_number is not None:
        cmd.extend(["--rerun-number", str(rerun_number)])

    logger.info("spark-submit starting: path=%s date=%s", parent_path, partition_date)

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)  # noqa: S603
    except subprocess.TimeoutExpired:
        logger.error("spark-submit TIMEOUT: path=%s exceeded 600s", parent_path)
        return JobResult(
            parent_path=parent_path,
            success=False,
            error_message="spark-submit timed out after 600s",
        )

    if result.returncode == 0:
        logger.info("spark-submit SUCCESS: path=%s", parent_path)
        return JobResult(parent_path=parent_path, success=True)
    else:
        logger.error(
            "spark-submit FAILURE: path=%s exit_code=%d stderr=%s",
            parent_path, result.returncode, result.stderr[:2000],
        )
        return JobResult(
            parent_path=parent_path,
            success=False,
            error_message=f"exit_code={result.returncode} stderr={result.stderr[:2000]}",
        )


def run_all_paths(
    parent_paths: list[str],
    partition_date: date,
    spark_config: dict[str, Any],
    datasets: list[str] | None = None,
    orchestration_run_ids: dict[str, int] | None = None,
    rerun_numbers: dict[str, int] | None = None,
) -> list[JobResult]:
    """Run spark-submit for each parent path with per-path failure isolation.

    Failed paths are recorded but do not halt processing of remaining paths.
    """
    results: list[JobResult] = []

    for path in parent_paths:
        try:
            orch_id = orchestration_run_ids.get(path) if orchestration_run_ids else None
            # rerun_number applies at dataset level; use max across all datasets for this spark-submit
            rn = max(rerun_numbers.values()) if rerun_numbers else None
            job_result = run_spark_job(
                path, partition_date, spark_config, datasets,
                orchestration_run_id=orch_id,
                rerun_number=rn,
            )
        except Exception as exc:  # noqa: BLE001
            logger.error("Unexpected error running spark job for path=%s: %s", path, exc)
            job_result = JobResult(
                parent_path=path,
                success=False,
                error_message=f"Unexpected error: {exc}",
            )
        results.append(job_result)

    return results
