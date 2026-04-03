"""Data models for dqs-orchestrator — placeholder for story 1-3+."""
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class JobResult:
    """Result of a single spark-submit invocation."""
    parent_path: str
    success: bool
    failed_datasets: list[str] = field(default_factory=list)
    error_message: Optional[str] = None
