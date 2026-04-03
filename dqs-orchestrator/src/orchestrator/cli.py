"""CLI entry point for dqs-orchestrator."""
import argparse
import logging
import sys
from datetime import date, datetime

import yaml

from orchestrator.db import (
    create_orchestration_run,
    expire_previous_run,
    finalize_orchestration_run,
    get_connection,
    query_run_summary,
)
from orchestrator.email import compose_summary_email, send_summary_email
from orchestrator.models import RunSummary
from orchestrator.runner import run_all_paths

logger = logging.getLogger(__name__)


def load_parent_paths(config_path: str) -> list[str]:
    """Load parent paths from YAML config file.

    Returns list of path strings.
    Exits with clear error if file is missing or malformed.
    """
    try:
        with open(config_path) as f:
            config = yaml.safe_load(f)
    except FileNotFoundError:
        sys.exit(f"ERROR: Parent paths config not found: {config_path}")
    except PermissionError:
        sys.exit(f"ERROR: Permission denied reading parent paths config: {config_path}")
    except yaml.YAMLError as e:
        sys.exit(f"ERROR: Failed to parse parent paths config {config_path}: {e}")

    if not isinstance(config, dict) or "parent_paths" not in config:
        sys.exit(f"ERROR: 'parent_paths' key missing in config: {config_path}")

    raw_entries = config["parent_paths"]
    if not isinstance(raw_entries, list):
        sys.exit(f"ERROR: 'parent_paths' must be a list in config: {config_path}")

    paths = []
    for entry in raw_entries:
        if not isinstance(entry, dict) or "path" not in entry:
            sys.exit(f"ERROR: Each entry in 'parent_paths' must have a 'path' key in config: {config_path}")
        paths.append(entry["path"])

    if not paths:
        sys.exit(f"ERROR: 'parent_paths' list is empty in config: {config_path}")

    return paths


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments."""
    parser = argparse.ArgumentParser(description="DQS Orchestrator — runs Spark DQ jobs")
    parser.add_argument("--config", default="config/orchestrator.yaml", help="Path to orchestrator config")
    parser.add_argument("--date", default=None, help="Partition date in yyyyMMdd format (default: today)")
    parser.add_argument("--datasets", nargs="*", help="Optional dataset filter for rerun")
    parser.add_argument("--rerun", action="store_true", default=False, help="Rerun mode: expire previous results")
    return parser.parse_args()


def main() -> None:
    """Entry point — reads config, loads parent paths, triggers spark-submit per path."""
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    args = parse_args()

    # Load orchestrator config
    try:
        with open(args.config) as f:
            orchestrator_config = yaml.safe_load(f)
    except FileNotFoundError:
        sys.exit(f"ERROR: Orchestrator config not found: {args.config}")
    except PermissionError:
        sys.exit(f"ERROR: Permission denied reading orchestrator config: {args.config}")
    except yaml.YAMLError as e:
        sys.exit(f"ERROR: Failed to parse orchestrator config: {e}")

    if not orchestrator_config or not isinstance(orchestrator_config, dict):
        sys.exit(f"ERROR: Orchestrator config is empty or invalid: {args.config}")

    # Resolve parent paths config path
    parent_paths_config = orchestrator_config.get("parent_paths_config", "config/parent_paths.yaml")
    parent_paths = load_parent_paths(parent_paths_config)

    # Resolve partition date
    if args.date is not None:
        try:
            partition_date = datetime.strptime(args.date, "%Y%m%d").date()
        except ValueError:
            sys.exit(f"ERROR: Invalid date format '{args.date}' — expected yyyyMMdd")
    else:
        partition_date = date.today()

    logger.info(
        "DQS Orchestrator starting: date=%s, parent_paths=%d, datasets=%s, rerun=%s",
        partition_date, len(parent_paths), args.datasets, args.rerun,
    )
    for path in parent_paths:
        logger.debug("Parent path: %s", path)

    spark_config = orchestrator_config.get("spark", {})
    if "spark" not in orchestrator_config:
        logger.warning("'spark' key missing from orchestrator config — using built-in defaults")

    # Resolve database URL from config or environment
    db_url = orchestrator_config.get("database", {}).get("url")

    # Create one orchestration run record per parent path (non-fatal on DB error)
    orchestration_run_ids: dict[str, int] = {}
    for path in parent_paths:
        try:
            conn = get_connection(db_url)
            try:
                run_id = create_orchestration_run(conn, path, datetime.now())
                orchestration_run_ids[path] = run_id
            finally:
                conn.close()
        except Exception as exc:  # noqa: BLE001
            logger.error("Failed to create orchestration_run for path=%s: %s", path, exc)
            # Do NOT block spark-submit — continue without run_id

    # Rerun management: expire previous metrics before spark-submit (non-fatal on error)
    rerun_numbers: dict[str, int] = {}  # dataset_name -> next rerun_number
    if args.rerun and args.datasets:
        expiry_ts = datetime.now()
        for dataset in args.datasets:
            try:
                conn = get_connection(db_url)
                try:
                    prev_rerun = expire_previous_run(conn, dataset, partition_date, expiry_ts)
                    rerun_numbers[dataset] = (prev_rerun or 0) + 1
                finally:
                    conn.close()
            except Exception as exc:  # noqa: BLE001
                logger.error(
                    "Failed to expire previous run for dataset=%s: %s — using rerun_number=0",
                    dataset, exc,
                )
                rerun_numbers[dataset] = 0  # safe default: may cause duplicate if old run not expired

    results = run_all_paths(
        parent_paths, partition_date, spark_config, args.datasets,
        orchestration_run_ids=orchestration_run_ids if orchestration_run_ids else None,
        rerun_numbers=rerun_numbers if rerun_numbers else None,
    )

    # Finalize each orchestration run record (non-fatal on DB error)
    for result in results:
        run_id = orchestration_run_ids.get(result.parent_path)
        if run_id is None:
            continue  # run record was not created — skip finalize
        failed_count = len(result.failed_datasets)
        error_summary = result.error_message if not result.success else None
        try:
            conn = get_connection(db_url)
            try:
                finalize_orchestration_run(
                    conn,
                    run_id,
                    datetime.now(),
                    total_datasets=None,
                    passed_datasets=None,
                    failed_datasets=failed_count,
                    error_summary=error_summary,
                )
            finally:
                conn.close()
        except Exception as exc:  # noqa: BLE001
            logger.error("Failed to finalize orchestration_run id=%d: %s", run_id, exc)

    # Send summary email per path (non-fatal — email errors never affect exit code)
    email_config = orchestrator_config.get("email", {})
    if email_config.get("smtp_host") and email_config.get("to_addresses"):
        for result in results:
            run_id = orchestration_run_ids.get(result.parent_path)
            if run_id is None:
                continue
            try:
                conn = get_connection(db_url)
                try:
                    summary_dict = query_run_summary(conn, run_id, partition_date)
                finally:
                    conn.close()
                summary_dict["dashboard_url"] = email_config.get("dashboard_url", "")
                run_summary = RunSummary(**summary_dict)
                subject, body = compose_summary_email(run_summary)
                send_summary_email(subject, body, email_config)
            except Exception as exc:  # noqa: BLE001
                logger.error("Failed to send summary email for path=%s: %s", result.parent_path, exc)

    succeeded = sum(1 for r in results if r.success)
    failed = len(results) - succeeded
    logger.info(
        "DQS Orchestrator complete: total_paths=%d succeeded=%d failed=%d",
        len(results), succeeded, failed,
    )

    if failed > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
