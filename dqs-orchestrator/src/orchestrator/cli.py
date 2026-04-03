"""CLI entry point for dqs-orchestrator.

Manages spark-submit invocations for each parent path and sends summary email.
"""
import argparse


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments."""
    parser = argparse.ArgumentParser(description="DQS Orchestrator — runs Spark DQ jobs")
    parser.add_argument("--config", default="config/orchestrator.yaml", help="Path to orchestrator config")
    parser.add_argument("--datasets", nargs="*", help="Optional dataset filter for rerun")
    return parser.parse_args()


def main() -> None:
    """Entry point — placeholder for story 1-3+."""
    args = parse_args()
    print(f"DQS Orchestrator — config={args.config}, datasets={args.datasets}")


if __name__ == "__main__":
    main()
