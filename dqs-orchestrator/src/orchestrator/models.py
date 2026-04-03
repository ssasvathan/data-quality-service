"""Data models for dqs-orchestrator."""
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class JobResult:
    """Result of a single spark-submit invocation."""
    parent_path: str
    success: bool
    failed_datasets: list[str] = field(default_factory=list)
    error_message: Optional[str] = None


@dataclass
class RunSummary:
    """Summary data for composing the post-run email notification."""
    run_id: int
    parent_path: str
    start_time: Optional[datetime]
    end_time: Optional[datetime]
    failed_datasets: int
    dashboard_url: str
    total_datasets: Optional[int] = None
    passed_datasets: Optional[int] = None
    error_summary: Optional[str] = None
    check_type_failures: dict[str, int] = field(default_factory=dict)
    failed_dataset_names: list[str] = field(default_factory=list)
