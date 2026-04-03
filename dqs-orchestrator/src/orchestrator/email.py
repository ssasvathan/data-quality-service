"""Summary email composer and sender for dqs-orchestrator.

Categorizes parent path failures vs dataset failures with rerun commands.
"""
import logging
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Any

from orchestrator.models import RunSummary

logger = logging.getLogger(__name__)


def compose_summary_email(run_summary: RunSummary) -> tuple[str, str]:
    """Compose subject and body for summary email.

    Returns (subject, body) tuple — pure function, no I/O.
    """
    # Determine status string for subject
    if run_summary.failed_datasets == 0:
        status_str = "PASSED"
    else:
        status_str = f"FAILED ({run_summary.failed_datasets} failures)"

    # Derive date from start_time for subject
    run_date = run_summary.start_time.strftime("%Y-%m-%d") if run_summary.start_time else "N/A"

    subject = f"DQS Run Summary \u2014 {run_summary.parent_path} \u2014 {run_date} \u2014 {status_str}"

    # Build body
    lines: list[str] = []
    lines.append("DQS Orchestration Run Summary")
    lines.append("=" * 30)
    lines.append(f"Run ID      : {run_summary.run_id}")
    lines.append(f"Parent Path : {run_summary.parent_path}")
    lines.append(f"Date        : {run_date}")
    lines.append(f"Start Time  : {run_summary.start_time}")
    lines.append(f"End Time    : {run_summary.end_time if run_summary.end_time is not None else 'N/A'}")
    lines.append("")
    lines.append("Results")
    lines.append("-" * 7)
    lines.append(f"Total Datasets  : {run_summary.total_datasets if run_summary.total_datasets is not None else 'N/A'}")
    lines.append(f"Passed Datasets : {run_summary.passed_datasets if run_summary.passed_datasets is not None else 'N/A'}")
    lines.append(f"Failed Datasets : {run_summary.failed_datasets}")
    lines.append("")
    lines.append("Top Failures by Check Type")
    lines.append("-" * 26)
    if run_summary.check_type_failures:
        sorted_failures = sorted(
            run_summary.check_type_failures.items(),
            key=lambda x: x[1],
            reverse=True,
        )
        for check_type, count in sorted_failures:
            lines.append(f"{check_type}: {count} failure(s)")
    else:
        lines.append("No check-type failures recorded.")
    lines.append("")
    lines.append("Failed Datasets \u2014 Rerun Commands")
    lines.append("-" * 33)
    if run_summary.failed_dataset_names:
        for name in run_summary.failed_dataset_names:
            lines.append(f"python -m orchestrator.cli --datasets {name} --rerun")
    else:
        lines.append("No failed datasets.")
    lines.append("")
    lines.append("Dashboard")
    lines.append("-" * 9)
    lines.append(run_summary.dashboard_url)

    body = "\n".join(lines)
    return subject, body


def send_summary_email(subject: str, body: str, smtp_config: dict[str, Any]) -> None:
    """Send summary email via SMTP. Non-fatal on all errors."""
    smtp_host = smtp_config.get("smtp_host", "")
    smtp_port = int(smtp_config.get("smtp_port", 25))
    from_addr = smtp_config.get("from_address", "")
    to_addrs = smtp_config.get("to_addresses", [])

    if not smtp_host or not to_addrs:
        logger.warning("SMTP not configured \u2014 skipping summary email")
        return

    try:
        msg = MIMEMultipart()
        msg["Subject"] = subject
        msg["From"] = from_addr
        msg["To"] = ", ".join(to_addrs)
        msg.attach(MIMEText(body, "plain"))

        with smtplib.SMTP(smtp_host, smtp_port) as smtp:
            smtp.sendmail(from_addr, to_addrs, msg.as_string())
        logger.info("Summary email sent to %d recipients", len(to_addrs))
    except (smtplib.SMTPException, OSError) as exc:
        logger.error("Failed to send summary email: %s", exc)
