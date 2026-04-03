"""Acceptance tests for orchestrator CLI — Story 3-1: Python CLI & Parent Path Configuration.

AC Coverage:
  AC1 — load_parent_paths reads config and identifies parent paths
  AC2 — parse_args parses --date, --datasets, --rerun arguments
  AC3 — missing/malformed config exits with a clear error message
"""

from unittest.mock import patch

import pytest
from orchestrator.cli import load_parent_paths, main, parse_args
from orchestrator.models import JobResult

# ---------------------------------------------------------------------------
# AC1 + AC3: load_parent_paths
# ---------------------------------------------------------------------------


def test_load_parent_paths_returns_correct_list_from_valid_yaml(tmp_path: pytest.TempPathFactory) -> None:
    """AC1: Given a parent_paths.yaml with 2 parent paths, load_parent_paths returns correct list."""
    config_file = tmp_path / "parent_paths.yaml"
    config_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
        "    description: Finance loan dataset parent path\n"
        "  - path: /data/finance/deposits\n"
        "    description: Finance deposits dataset parent path\n"
    )

    result = load_parent_paths(str(config_file))

    assert result == ["/data/finance/loans", "/data/finance/deposits"]


def test_load_parent_paths_returns_all_three_paths(tmp_path: pytest.TempPathFactory) -> None:
    """AC1: load_parent_paths returns all paths from config, not just first entry."""
    config_file = tmp_path / "parent_paths.yaml"
    config_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
        "  - path: /data/finance/deposits\n"
        "  - path: /data/risk/credit\n"
    )

    result = load_parent_paths(str(config_file))

    assert len(result) == 3
    assert "/data/risk/credit" in result


def test_load_parent_paths_returns_plain_list_of_strings(tmp_path: pytest.TempPathFactory) -> None:
    """AC1: load_parent_paths returns list[str], not list of dicts."""
    config_file = tmp_path / "parent_paths.yaml"
    config_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
        "    description: Finance loan dataset parent path\n"
    )

    result = load_parent_paths(str(config_file))

    assert isinstance(result, list)
    assert all(isinstance(p, str) for p in result)


def test_load_parent_paths_exits_on_missing_file(tmp_path: pytest.TempPathFactory) -> None:
    """AC3: Given a missing config file, load_parent_paths exits with a clear error message."""
    nonexistent = str(tmp_path / "nonexistent.yaml")

    with pytest.raises(SystemExit) as exc_info:
        load_parent_paths(nonexistent)

    # Must exit with non-zero and mention the file path in the error
    assert exc_info.value.code != 0
    assert nonexistent in str(exc_info.value.code)


def test_load_parent_paths_exits_on_missing_parent_paths_key(tmp_path: pytest.TempPathFactory) -> None:
    """AC3: Given a malformed config (missing parent_paths key), exits with clear error."""
    config_file = tmp_path / "bad_config.yaml"
    config_file.write_text("other_key: some_value\n")

    with pytest.raises(SystemExit) as exc_info:
        load_parent_paths(str(config_file))

    assert exc_info.value.code != 0


def test_load_parent_paths_exits_on_empty_yaml_file(tmp_path: pytest.TempPathFactory) -> None:
    """AC3: Given an empty (null) YAML file, exits with clear error about missing parent_paths key."""
    config_file = tmp_path / "empty.yaml"
    config_file.write_text("")

    with pytest.raises(SystemExit) as exc_info:
        load_parent_paths(str(config_file))

    assert exc_info.value.code != 0


# ---------------------------------------------------------------------------
# AC2: parse_args
# ---------------------------------------------------------------------------


def test_parse_args_parses_date_argument() -> None:
    """AC2: --date 20260325 is parsed correctly as a string."""
    with patch("sys.argv", ["orchestrator", "--date", "20260325"]):
        args = parse_args()

    assert args.date == "20260325"


def test_parse_args_parses_datasets_as_list() -> None:
    """AC2: --datasets ds1 ds2 is parsed as a list."""
    with patch("sys.argv", ["orchestrator", "--datasets", "ue90-omni-transactions", "another-dataset"]):
        args = parse_args()

    assert args.datasets == ["ue90-omni-transactions", "another-dataset"]


def test_parse_args_parses_rerun_flag_as_true() -> None:
    """AC2: --rerun flag is parsed as True (store_true action)."""
    with patch("sys.argv", ["orchestrator", "--rerun"]):
        args = parse_args()

    assert args.rerun is True


def test_parse_args_defaults_date_is_none() -> None:
    """AC2: Without --date, default is None (caller resolves to today)."""
    with patch("sys.argv", ["orchestrator"]):
        args = parse_args()

    assert args.date is None


def test_parse_args_defaults_rerun_is_false() -> None:
    """AC2: Without --rerun, default is False."""
    with patch("sys.argv", ["orchestrator"]):
        args = parse_args()

    assert args.rerun is False


def test_parse_args_defaults_datasets_is_none() -> None:
    """AC2: Without --datasets, default is None."""
    with patch("sys.argv", ["orchestrator"]):
        args = parse_args()

    assert args.datasets is None


def test_parse_args_config_defaults_to_orchestrator_yaml() -> None:
    """AC2: --config defaults to config/orchestrator.yaml (existing unchanged argument)."""
    with patch("sys.argv", ["orchestrator"]):
        args = parse_args()

    assert args.config == "config/orchestrator.yaml"


def test_parse_args_single_dataset_filter() -> None:
    """AC2: --datasets with single value parses as a list with one element."""
    with patch("sys.argv", ["orchestrator", "--datasets", "ue90-omni-transactions"]):
        args = parse_args()

    assert args.datasets == ["ue90-omni-transactions"]


# ---------------------------------------------------------------------------
# AC1 + AC2 + AC3: main() wiring
# ---------------------------------------------------------------------------


def test_main_exits_cleanly_with_valid_config_and_parent_paths(tmp_path: pytest.TempPathFactory) -> None:
    """AC1+AC2+AC3: main() exits cleanly with valid orchestrator config and parent_paths.yaml."""
    # Create minimal orchestrator config
    orchestrator_config = tmp_path / "orchestrator.yaml"
    orchestrator_config.write_text(
        "database:\n"
        "  url: postgresql://localhost/test\n"
        "spark:\n"
        "  submit_path: spark-submit\n"
        "  master: yarn\n"
    )

    # Create parent_paths.yaml referenced from orchestrator config (default key)
    parent_paths_config = tmp_path / "parent_paths.yaml"
    parent_paths_config.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
        "  - path: /data/finance/deposits\n"
    )

    # Override parent_paths_config path via orchestrator config
    orchestrator_config_with_paths = tmp_path / "orchestrator_with_paths.yaml"
    orchestrator_config_with_paths.write_text(
        "database:\n"
        "  url: postgresql://localhost/test\n"
        "spark:\n"
        "  submit_path: spark-submit\n"
        "  master: yarn\n"
        f"parent_paths_config: {parent_paths_config}\n"
    )

    # main() must not crash on valid inputs
    with patch(
        "sys.argv",
        ["orchestrator", "--config", str(orchestrator_config_with_paths), "--date", "20260325"],
    ), patch(
        "orchestrator.cli.run_all_paths",
        return_value=[
            JobResult("/data/finance/loans", success=True),
            JobResult("/data/finance/deposits", success=True),
        ],
    ):
        # main() should complete without raising an exception or calling sys.exit
        main()


def test_main_exits_with_error_on_missing_orchestrator_config(tmp_path: pytest.TempPathFactory) -> None:
    """AC3: main() exits with clear error when orchestrator config is missing."""
    nonexistent_config = str(tmp_path / "nonexistent_orchestrator.yaml")

    with patch("sys.argv", ["orchestrator", "--config", nonexistent_config]):
        with pytest.raises(SystemExit) as exc_info:
            main()

    assert exc_info.value.code != 0


def test_main_resolves_today_when_date_not_provided(tmp_path: pytest.TempPathFactory) -> None:
    """AC2: main() resolves partition date to today when --date is not provided."""
    orchestrator_config = tmp_path / "orchestrator.yaml"
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    orchestrator_config.write_text(
        "spark:\n"
        "  submit_path: spark-submit\n"
        f"parent_paths_config: {parent_paths_file}\n"
    )

    # main() must not crash even when --date is omitted (defaults to today)
    with patch("sys.argv", ["orchestrator", "--config", str(orchestrator_config)]), patch(
        "orchestrator.cli.run_all_paths",
        return_value=[JobResult("/data/finance/loans", success=True)],
    ):
        main()  # Should complete without error


# ---------------------------------------------------------------------------
# Additional robustness tests (findings from code review)
# ---------------------------------------------------------------------------


def test_main_exits_on_null_orchestrator_config(tmp_path: pytest.TempPathFactory) -> None:
    """AC3: main() exits with clear error when orchestrator config file is empty (null YAML)."""
    empty_config = tmp_path / "empty_orchestrator.yaml"
    empty_config.write_text("")

    with patch("sys.argv", ["orchestrator", "--config", str(empty_config)]):
        with pytest.raises(SystemExit) as exc_info:
            main()

    assert exc_info.value.code != 0


def test_load_parent_paths_exits_on_entry_missing_path_key(tmp_path: pytest.TempPathFactory) -> None:
    """AC3: load_parent_paths exits with clear error when an entry is missing the 'path' key."""
    config_file = tmp_path / "bad_paths.yaml"
    config_file.write_text(
        "parent_paths:\n"
        "  - description: Finance loan dataset parent path\n"
    )

    with pytest.raises(SystemExit) as exc_info:
        load_parent_paths(str(config_file))

    assert exc_info.value.code != 0
