"""CLI entry point for dqs-orchestrator."""
import argparse
import logging
import sys
from datetime import date, datetime

import yaml

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
    results = run_all_paths(parent_paths, partition_date, spark_config, args.datasets)

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
