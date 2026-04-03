"""Acceptance tests for email composer and sender — Story 3-5.

TDD RED PHASE (Story 3-5): All tests in this file WILL FAIL until:
  - RunSummary dataclass is added to models.py (Task 6)
  - compose_summary_email() is implemented in email.py (Task 1)
  - send_summary_email() is added to email.py (Task 3)

AC Coverage (Story 3-5):
  AC1 — compose_summary_email() produces correct email body and subject from RunSummary
  AC2 — send_summary_email() delivers email to configured SRE distribution list via SMTP
  AC3 — send_summary_email() is non-fatal on SMTPException and OSError
"""

import smtplib
from datetime import datetime
from unittest.mock import MagicMock, patch

# ---------------------------------------------------------------------------
# Helpers / factories
# ---------------------------------------------------------------------------


def make_run_summary(**overrides):
    """Return a RunSummary instance with sensible defaults for testing.

    TDD RED: Will raise ImportError until RunSummary is added to models.py.
    """
    from orchestrator.models import RunSummary  # noqa: PLC0415

    defaults = {
        "run_id": 42,
        "parent_path": "/data/finance/loans",
        "start_time": datetime(2026, 4, 3, 8, 0, 0),
        "end_time": datetime(2026, 4, 3, 9, 15, 0),
        "failed_datasets": 2,
        "dashboard_url": "http://localhost:5173/summary",
        "total_datasets": 10,
        "passed_datasets": 8,
        "error_summary": None,
        "check_type_failures": {"volume": 3, "schema": 2},
        "failed_dataset_names": ["ue90-omni-transactions", "ue90-card-balances"],
    }
    defaults.update(overrides)
    return RunSummary(**defaults)


# ---------------------------------------------------------------------------
# AC1: compose_summary_email — email body content
# ---------------------------------------------------------------------------


def test_compose_summary_email_includes_run_id() -> None:
    """AC1: compose_summary_email() includes the run_id in the email body.

    TDD RED: Will fail until compose_summary_email() is implemented in email.py.
    The current placeholder raises NotImplementedError.
    """
    from orchestrator.email import compose_summary_email  # noqa: PLC0415

    run_summary = make_run_summary(run_id=42)
    subject, body = compose_summary_email(run_summary)

    assert "42" in body, (
        "Email body must contain the run_id (42) so SRE team can correlate with DB records"
    )


def test_compose_summary_email_includes_pass_fail_counts() -> None:
    """AC1: compose_summary_email() includes total, passed, and failed dataset counts in the body.

    TDD RED: Will fail until compose_summary_email() is implemented in email.py.
    """
    from orchestrator.email import compose_summary_email  # noqa: PLC0415

    run_summary = make_run_summary(total_datasets=10, passed_datasets=8, failed_datasets=2)
    subject, body = compose_summary_email(run_summary)

    assert "10" in body, "Email body must contain total_datasets count (10)"
    assert "8" in body, "Email body must contain passed_datasets count (8)"
    assert "2" in body, "Email body must contain failed_datasets count (2)"


def test_compose_summary_email_includes_check_type_failures() -> None:
    """AC1: compose_summary_email() groups failures by check type in the body.

    TDD RED: Will fail until compose_summary_email() is implemented in email.py.
    Body must show e.g. 'volume: 3 failure(s)' and 'schema: 2 failure(s)'.
    """
    from orchestrator.email import compose_summary_email  # noqa: PLC0415

    run_summary = make_run_summary(check_type_failures={"volume": 3, "schema": 2})
    subject, body = compose_summary_email(run_summary)

    assert "volume" in body, "Email body must list 'volume' check type failures"
    assert "schema" in body, "Email body must list 'schema' check type failures"
    assert "3" in body, "Email body must include volume failure count (3)"
    assert "2" in body, "Email body must include schema failure count (2)"


def test_compose_summary_email_includes_rerun_commands() -> None:
    """AC1: compose_summary_email() includes rerun command for each failed dataset.

    TDD RED: Will fail until compose_summary_email() is implemented in email.py.
    Per AC1: 'python -m orchestrator.cli --datasets <name> --rerun' must appear for each failed dataset.
    """
    from orchestrator.email import compose_summary_email  # noqa: PLC0415

    failed_names = ["ue90-omni-transactions", "ue90-card-balances"]
    run_summary = make_run_summary(failed_dataset_names=failed_names)
    subject, body = compose_summary_email(run_summary)

    assert "python -m orchestrator.cli" in body, (
        "Email body must contain rerun command 'python -m orchestrator.cli'"
    )
    assert "--rerun" in body, "Email body must contain '--rerun' flag in rerun commands"
    assert "ue90-omni-transactions" in body, (
        "Email body must contain first failed dataset name in rerun command"
    )
    assert "ue90-card-balances" in body, (
        "Email body must contain second failed dataset name in rerun command"
    )


def test_compose_summary_email_includes_dashboard_link() -> None:
    """AC1: compose_summary_email() includes the dashboard URL in the email body.

    TDD RED: Will fail until compose_summary_email() is implemented in email.py.
    Per AC1: body must include a link to the summary dashboard view.
    """
    from orchestrator.email import compose_summary_email  # noqa: PLC0415

    run_summary = make_run_summary(dashboard_url="http://localhost:5173/summary")
    subject, body = compose_summary_email(run_summary)

    assert "http://localhost:5173/summary" in body, (
        "Email body must contain the dashboard_url for SRE team to navigate to the run summary"
    )


def test_compose_summary_email_subject_passes_status() -> None:
    """AC1: compose_summary_email() sets subject status to PASSED when failed_datasets==0.

    TDD RED: Will fail until compose_summary_email() is implemented in email.py.
    Subject format: 'DQS Run Summary — {parent_path} — {date} — PASSED'
                 or 'DQS Run Summary — {parent_path} — {date} — FAILED (N failures)'
    """
    from orchestrator.email import compose_summary_email  # noqa: PLC0415

    # Case 1: No failures → PASSED
    passing_summary = make_run_summary(failed_datasets=0, passed_datasets=10, total_datasets=10)
    subject_pass, _ = compose_summary_email(passing_summary)

    assert "PASSED" in subject_pass, (
        "Subject must contain 'PASSED' when failed_datasets == 0"
    )
    assert "FAILED" not in subject_pass, (
        "Subject must NOT contain 'FAILED' when failed_datasets == 0"
    )

    # Case 2: Failures → FAILED (N failures)
    failing_summary = make_run_summary(failed_datasets=3)
    subject_fail, _ = compose_summary_email(failing_summary)

    assert "FAILED" in subject_fail, (
        "Subject must contain 'FAILED' when failed_datasets > 0"
    )
    assert "3" in subject_fail, (
        "Subject must contain the failure count (3) when there are failures"
    )


# ---------------------------------------------------------------------------
# AC2: send_summary_email — SMTP delivery
# ---------------------------------------------------------------------------


def test_send_summary_email_calls_smtp() -> None:
    """AC2: send_summary_email() calls smtplib.SMTP.sendmail() with correct from/to addresses.

    TDD RED: Will raise ImportError until send_summary_email() is added to email.py.
    """
    from orchestrator.email import send_summary_email  # noqa: PLC0415

    with patch("orchestrator.email.smtplib.SMTP") as mock_smtp_cls:
        mock_smtp = MagicMock()
        mock_smtp_cls.return_value.__enter__ = lambda s: mock_smtp
        mock_smtp_cls.return_value.__exit__ = MagicMock(return_value=False)

        send_summary_email(
            subject="DQS Run Summary — PASSED",
            body="Run completed successfully.",
            smtp_config={
                "smtp_host": "localhost",
                "smtp_port": 25,
                "from_address": "dqs-alerts@example.com",
                "to_addresses": ["data-engineering@example.com"],
            },
        )

        mock_smtp.sendmail.assert_called_once()
        call_args = mock_smtp.sendmail.call_args[0]
        # call_args[0] = from_addr, call_args[1] = to_addrs
        assert call_args[0] == "dqs-alerts@example.com", (
            "sendmail from_addr must be 'dqs-alerts@example.com'"
        )
        assert "data-engineering@example.com" in call_args[1], (
            "sendmail to_addrs must contain 'data-engineering@example.com'"
        )


# ---------------------------------------------------------------------------
# AC3: send_summary_email — non-fatal error handling
# ---------------------------------------------------------------------------


def test_send_summary_email_non_fatal_on_smtp_exception() -> None:
    """AC3: send_summary_email() does NOT re-raise SMTPException — email errors are non-fatal.

    TDD RED: Will raise ImportError until send_summary_email() is added to email.py.
    Per AC3: SMTP failure must not cause the orchestration run to be marked as failed.
    """
    from orchestrator.email import send_summary_email  # noqa: PLC0415

    smtp_config = {
        "smtp_host": "unreachable-host",
        "smtp_port": 25,
        "from_address": "dqs-alerts@example.com",
        "to_addresses": ["sre@example.com"],
    }

    with patch("orchestrator.email.smtplib.SMTP") as mock_smtp_cls:
        mock_smtp_cls.side_effect = smtplib.SMTPException("Connection failed")

        # Must NOT raise — SMTPException is non-fatal by design (AC3)
        send_summary_email(
            subject="DQS Run Summary — FAILED (2 failures)",
            body="2 datasets failed.",
            smtp_config=smtp_config,
        )


def test_send_summary_email_non_fatal_on_connection_refused() -> None:
    """AC3: send_summary_email() does NOT re-raise OSError (connection refused/host unreachable).

    TDD RED: Will raise ImportError until send_summary_email() is added to email.py.
    Per AC3 and dev notes: OSError covers 'connection refused' and 'host unreachable' cases.
    """
    from orchestrator.email import send_summary_email  # noqa: PLC0415

    smtp_config = {
        "smtp_host": "localhost",
        "smtp_port": 25,
        "from_address": "dqs-alerts@example.com",
        "to_addresses": ["sre@example.com"],
    }

    with patch("orchestrator.email.smtplib.SMTP") as mock_smtp_cls:
        mock_smtp_cls.side_effect = OSError("Connection refused")

        # Must NOT raise — OSError is non-fatal by design (AC3)
        send_summary_email(
            subject="DQS Run Summary — PASSED",
            body="All datasets passed.",
            smtp_config=smtp_config,
        )
