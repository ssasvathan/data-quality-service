"""Acceptance tests for orchestrator CLI — Story 3-1 + Story 3-3 + Story 3-4.

AC Coverage (Story 3-1):
  AC1 — load_parent_paths reads config and identifies parent paths
  AC2 — parse_args parses --date, --datasets, --rerun arguments
  AC3 — missing/malformed config exits with a clear error message

AC Coverage (Story 3-3):
  AC1 (3-3) — main() calls create_orchestration_run before run_all_paths for each parent path
  AC2 (3-3) — main() calls finalize_orchestration_run after run_all_paths for each JobResult
  AC2 (3-3) — finalize uses 'completed_with_errors' when JobResult has failures
  AC3 (3-3) — main() passes orchestration_run_ids dict to run_all_paths
  Safety    — DB errors in create_orchestration_run do NOT prevent spark-submit
  Safety    — DB errors in finalize_orchestration_run do NOT cause sys.exit(1)

AC Coverage (Story 3-4 — TDD RED PHASE):
  AC1 (3-4) — main() calls expire_previous_run for each dataset when --rerun flag is set
  AC2 (3-4) — main() does NOT call expire_previous_run on normal (non-rerun) runs
  Safety    — DB errors in expire_previous_run are non-fatal (spark-submit proceeds)

TDD RED PHASE TESTS (Story 3-4):
  Tests marked "# TDD RED (3-4)" will FAIL until cli.py imports expire_previous_run and wires it.
  Mock target: 'orchestrator.cli.expire_previous_run' (imported at module level in cli.py).
"""

from unittest.mock import MagicMock, patch

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


# ---------------------------------------------------------------------------
# AC1 (Story 3-3): create_orchestration_run called before run_all_paths — TDD RED
# ---------------------------------------------------------------------------


def _write_minimal_config(tmp_path, parent_paths_file: str) -> str:
    """Helper: write a minimal orchestrator config that references parent_paths_file."""
    config_file = tmp_path / "orchestrator.yaml"
    config_file.write_text(
        "database:\n"
        "  url: postgresql://localhost/test\n"
        "spark:\n"
        "  submit_path: spark-submit\n"
        "  master: yarn\n"
        f"parent_paths_config: {parent_paths_file}\n"
    )
    return str(config_file)


def test_main_calls_create_orchestration_run_before_run_all_paths(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """AC1 (3-3): main() calls create_orchestration_run once per parent path before spark-submit.

    TDD RED: Will fail until cli.py imports create_orchestration_run and calls it.
    Mock target: 'orchestrator.cli.create_orchestration_run' (imported at module level in cli.py).
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
        "  - path: /data/finance/deposits\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_create = MagicMock(side_effect=[1, 2])  # returns run_id 1, 2

    with patch("sys.argv", ["orchestrator", "--config", config_file, "--date", "20260325"]), \
         patch("orchestrator.cli.run_all_paths", return_value=[
             JobResult("/data/finance/loans", success=True),
             JobResult("/data/finance/deposits", success=True),
         ]), \
         patch("orchestrator.cli.create_orchestration_run", mock_create), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run"):
        main()

    # create_orchestration_run must be called once per parent path
    assert mock_create.call_count == 2
    call_paths = [c[0][1] for c in mock_create.call_args_list]  # second positional arg = parent_path
    assert "/data/finance/loans" in call_paths
    assert "/data/finance/deposits" in call_paths


def test_main_passes_orchestration_run_ids_to_run_all_paths(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """AC3 (3-3): main() passes orchestration_run_ids dict to run_all_paths after creating records.

    TDD RED: Will fail until cli.py wires the orchestration_run_ids mapping into run_all_paths().
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
        "  - path: /data/finance/deposits\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    # create_orchestration_run returns 10, 11 for loans and deposits respectively
    mock_create = MagicMock(side_effect=[10, 11])
    mock_run_all = MagicMock(return_value=[
        JobResult("/data/finance/loans", success=True),
        JobResult("/data/finance/deposits", success=True),
    ])

    with patch("sys.argv", ["orchestrator", "--config", config_file, "--date", "20260325"]), \
         patch("orchestrator.cli.run_all_paths", mock_run_all), \
         patch("orchestrator.cli.create_orchestration_run", mock_create), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run"):
        main()

    # run_all_paths must receive orchestration_run_ids kwarg with correct mapping
    call_kwargs = mock_run_all.call_args[1]  # keyword args
    assert "orchestration_run_ids" in call_kwargs
    orch_ids = call_kwargs["orchestration_run_ids"]
    assert orch_ids["/data/finance/loans"] == 10
    assert orch_ids["/data/finance/deposits"] == 11


# ---------------------------------------------------------------------------
# AC2 (Story 3-3): finalize_orchestration_run called after run_all_paths — TDD RED
# ---------------------------------------------------------------------------


def test_main_calls_finalize_orchestration_run_after_run_all_paths(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """AC2 (3-3): main() calls finalize_orchestration_run once per JobResult after spark-submit.

    TDD RED: Will fail until cli.py imports finalize_orchestration_run and calls it.
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
        "  - path: /data/finance/deposits\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_finalize = MagicMock()

    with patch("sys.argv", ["orchestrator", "--config", config_file, "--date", "20260325"]), \
         patch("orchestrator.cli.run_all_paths", return_value=[
             JobResult("/data/finance/loans", success=True),
             JobResult("/data/finance/deposits", success=True),
         ]), \
         patch("orchestrator.cli.create_orchestration_run", side_effect=[10, 11]), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run", mock_finalize):
        main()

    # finalize_orchestration_run called once per result
    assert mock_finalize.call_count == 2


def test_main_finalizes_with_completed_with_errors_on_failure(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """AC2 (3-3): main() calls finalize with failed_datasets > 0 when JobResult has failures.

    TDD RED: Will fail until cli.py passes failed_count from result.failed_datasets to finalize.
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_finalize = MagicMock()
    failed_result = JobResult(
        "/data/finance/loans",
        success=False,
        failed_datasets=["ds-1", "ds-2"],
        error_message="exit_code=1 stderr=OOM",
    )

    with patch("sys.argv", ["orchestrator", "--config", config_file, "--date", "20260325"]), \
         patch("orchestrator.cli.run_all_paths", return_value=[failed_result]), \
         patch("orchestrator.cli.create_orchestration_run", return_value=10), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run", mock_finalize):
        with pytest.raises(SystemExit):  # sys.exit(1) expected when there are failures
            main()

    # finalize must be called with failed_datasets=2 (len of failed_datasets list)
    finalize_call = mock_finalize.call_args
    assert finalize_call is not None
    # Check failed_datasets kwarg or positional
    call_kwargs = finalize_call[1] if finalize_call[1] else {}
    call_args = finalize_call[0] if finalize_call[0] else ()
    # failed_datasets should be 2 — either as kwarg or positional
    failed_count_found = (
        call_kwargs.get("failed_datasets") == 2
        or (len(call_args) >= 6 and call_args[5] == 2)  # positional: conn, run_id, end_time, total, passed, failed
    )
    assert failed_count_found, (
        f"Expected failed_datasets=2 in finalize call. Got args={call_args}, kwargs={call_kwargs}"
    )


# ---------------------------------------------------------------------------
# Safety (Story 3-3): DB errors non-fatal — TDD RED
# ---------------------------------------------------------------------------


def test_main_continues_spark_submit_when_create_orchestration_run_raises(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """Safety (3-3): DB failure in create_orchestration_run must NOT prevent spark-submit.

    TDD RED: Will fail until cli.py wraps create_orchestration_run in try/except and continues.
    Per story: DB errors are non-fatal for orchestration — spark-submit must proceed.
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_run_all = MagicMock(return_value=[
        JobResult("/data/finance/loans", success=True),
    ])

    with patch("sys.argv", ["orchestrator", "--config", config_file, "--date", "20260325"]), \
         patch("orchestrator.cli.run_all_paths", mock_run_all), \
         patch("orchestrator.cli.create_orchestration_run", side_effect=Exception("DB connection refused")), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run"):
        # Must NOT raise — DB failure is non-fatal, spark-submit must proceed
        main()

    # run_all_paths was still called despite DB failure
    mock_run_all.assert_called_once()


def test_main_does_not_exit_1_when_finalize_orchestration_run_raises(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """Safety (3-3): DB failure in finalize_orchestration_run must NOT cause sys.exit(1) beyond spark results.

    TDD RED: Will fail until cli.py wraps finalize_orchestration_run in try/except.
    Per story: exit code is determined by spark results, not DB finalize errors.
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    with patch("sys.argv", ["orchestrator", "--config", config_file, "--date", "20260325"]), \
         patch("orchestrator.cli.run_all_paths", return_value=[
             JobResult("/data/finance/loans", success=True),
         ]), \
         patch("orchestrator.cli.create_orchestration_run", return_value=10), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run", side_effect=Exception("DB write failed")):
        # All spark results succeeded → main() must NOT call sys.exit(1) due to finalize failure
        main()  # If this raises SystemExit(1), the test fails correctly (TDD red)


# ---------------------------------------------------------------------------
# Story 3-4 ATDD tests — TDD RED PHASE
# All tests below WILL FAIL until cli.py imports expire_previous_run and wires
# the rerun-management block into main().
# ---------------------------------------------------------------------------


def test_main_calls_expire_previous_run_when_rerun_flag_set(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """AC1 (3-4): main() calls expire_previous_run for each dataset in args.datasets when --rerun is set.

    TDD RED (3-4): Will fail until cli.py imports expire_previous_run and calls it
    for each dataset in args.datasets when args.rerun is True.
    Mock target: 'orchestrator.cli.expire_previous_run' (module-level import in cli.py).
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_expire = MagicMock(return_value=0)  # Returns rerun_number=0 (previous was first run)

    with patch(
        "sys.argv",
        ["orchestrator", "--config", config_file, "--date", "20260325",
         "--datasets", "ue90-omni-transactions", "--rerun"],
    ), \
         patch("orchestrator.cli.run_all_paths", return_value=[
             JobResult("/data/finance/loans", success=True),
         ]), \
         patch("orchestrator.cli.create_orchestration_run", return_value=10), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run"), \
         patch("orchestrator.cli.expire_previous_run", mock_expire):
        main()

    # expire_previous_run must be called once for the single dataset
    assert mock_expire.call_count == 1, (
        f"expire_previous_run must be called once per dataset, got {mock_expire.call_count}"
    )
    # First positional arg after conn should be dataset_name
    call_args = mock_expire.call_args[0]
    assert "ue90-omni-transactions" in call_args, (
        "expire_previous_run must be called with the dataset_name from --datasets"
    )


def test_main_does_not_call_expire_previous_run_when_rerun_not_set(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """AC2 (3-4): main() does NOT call expire_previous_run on normal (non-rerun) runs.

    TDD RED (3-4): Will fail until cli.py guards expire_previous_run behind `if args.rerun`.
    Per story: expire_previous_run is ONLY called when --rerun flag is set.
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_expire = MagicMock()

    with patch(
        "sys.argv",
        # No --rerun flag and no --datasets
        ["orchestrator", "--config", config_file, "--date", "20260325"],
    ), \
         patch("orchestrator.cli.run_all_paths", return_value=[
             JobResult("/data/finance/loans", success=True),
         ]), \
         patch("orchestrator.cli.create_orchestration_run", return_value=10), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run"), \
         patch("orchestrator.cli.expire_previous_run", mock_expire):
        main()

    mock_expire.assert_not_called(), (
        "expire_previous_run must NOT be called on a normal (non-rerun) run"
    )


def test_main_expire_error_is_non_fatal_spark_submit_still_proceeds(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """Safety (3-4): expire_previous_run raising does NOT prevent spark-submit from proceeding.

    TDD RED (3-4): Will fail until cli.py wraps expire_previous_run in try/except Exception.
    Per story: DB errors in expire_previous_run are non-fatal — spark-submit must proceed.
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_run_all = MagicMock(return_value=[
        JobResult("/data/finance/loans", success=True),
    ])

    with patch(
        "sys.argv",
        ["orchestrator", "--config", config_file, "--date", "20260325",
         "--datasets", "ue90-omni-transactions", "--rerun"],
    ), \
         patch("orchestrator.cli.run_all_paths", mock_run_all), \
         patch("orchestrator.cli.create_orchestration_run", return_value=10), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run"), \
         patch("orchestrator.cli.expire_previous_run",
               side_effect=Exception("DB connection refused")):
        # Must NOT raise — DB failure is non-fatal, spark-submit must proceed
        main()

    # run_all_paths must still be called despite expire failure
    mock_run_all.assert_called_once(), (
        "run_all_paths must still be called even when expire_previous_run raises"
    )


def test_main_passes_rerun_numbers_to_run_all_paths(
    tmp_path: pytest.TempPathFactory,
) -> None:
    """AC1 (3-4): main() passes rerun_numbers kwarg to run_all_paths when --rerun is set.

    TDD RED (3-4): Will fail until cli.py computes rerun_numbers and passes them
    to run_all_paths() so each spark-submit gets the correct --rerun-number.
    """
    parent_paths_file = tmp_path / "parent_paths.yaml"
    parent_paths_file.write_text(
        "parent_paths:\n"
        "  - path: /data/finance/loans\n"
    )
    config_file = _write_minimal_config(tmp_path, str(parent_paths_file))

    mock_run_all = MagicMock(return_value=[
        JobResult("/data/finance/loans", success=True),
    ])

    # expire_previous_run returns 0 → next rerun_number should be 1
    with patch(
        "sys.argv",
        ["orchestrator", "--config", config_file, "--date", "20260325",
         "--datasets", "ue90-omni-transactions", "--rerun"],
    ), \
         patch("orchestrator.cli.run_all_paths", mock_run_all), \
         patch("orchestrator.cli.create_orchestration_run", return_value=10), \
         patch("orchestrator.cli.get_connection", return_value=MagicMock()), \
         patch("orchestrator.cli.finalize_orchestration_run"), \
         patch("orchestrator.cli.expire_previous_run", return_value=0):
        main()

    # run_all_paths must receive rerun_numbers kwarg
    call_kwargs = mock_run_all.call_args[1]
    assert "rerun_numbers" in call_kwargs, (
        "run_all_paths must be called with rerun_numbers kwarg when --rerun is set"
    )
    rerun_numbers = call_kwargs["rerun_numbers"]
    assert rerun_numbers is not None, "rerun_numbers must not be None when --rerun is set"
    # The value for ue90-omni-transactions should be 1 (prev rerun_number=0, so next=1)
    assert rerun_numbers.get("ue90-omni-transactions") == 1, (
        "rerun_numbers['ue90-omni-transactions'] must be 1 when previous rerun_number was 0"
    )
