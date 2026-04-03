"""Spark submit runner — invokes spark-submit per parent path with per-path failure isolation."""
import logging
import subprocess
from datetime import date

from orchestrator.models import JobResult

logger = logging.getLogger(__name__)


def run_spark_job(
    parent_path: str,
    partition_date: date,
    spark_config: dict,
    datasets: list[str] | None = None,
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

    if datasets:
        cmd.extend(["--datasets"] + datasets)

    logger.info("spark-submit starting: path=%s date=%s", parent_path, partition_date)

    result = subprocess.run(cmd, capture_output=True, text=True)  # noqa: S603

    if result.returncode == 0:
        logger.info("spark-submit SUCCESS: path=%s", parent_path)
        return JobResult(parent_path=parent_path, success=True)
    else:
        logger.error(
            "spark-submit FAILURE: path=%s exit_code=%d stderr=%s",
            parent_path, result.returncode, result.stderr,
        )
        return JobResult(
            parent_path=parent_path,
            success=False,
            error_message=f"exit_code={result.returncode} stderr={result.stderr}",
        )


def run_all_paths(
    parent_paths: list[str],
    partition_date: date,
    spark_config: dict,
    datasets: list[str] | None = None,
) -> list[JobResult]:
    """Run spark-submit for each parent path with per-path failure isolation.

    Failed paths are recorded but do not halt processing of remaining paths.
    """
    results: list[JobResult] = []

    for path in parent_paths:
        try:
            job_result = run_spark_job(path, partition_date, spark_config, datasets)
        except Exception as exc:  # noqa: BLE001
            logger.error("Unexpected error running spark job for path=%s: %s", path, exc)
            job_result = JobResult(
                parent_path=path,
                success=False,
                error_message=f"Unexpected error: {exc}",
            )
        results.append(job_result)

    return results
