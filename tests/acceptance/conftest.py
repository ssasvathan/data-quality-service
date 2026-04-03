"""
Shared fixtures for ATDD acceptance tests — Story 1.1.

Minimal conftest for the red phase. Fixtures will be extended in the green phase
once the components are implemented.
"""

import os

import pytest


@pytest.fixture(scope="session")
def project_root() -> str:
    """Return the absolute path to the project root directory."""
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))


@pytest.fixture(scope="session")
def component_paths(project_root: str) -> dict:
    """Return a dictionary of expected component root paths."""
    return {
        "dqs_spark": os.path.join(project_root, "dqs-spark"),
        "dqs_serve": os.path.join(project_root, "dqs-serve"),
        "dqs_orchestrator": os.path.join(project_root, "dqs-orchestrator"),
        "dqs_dashboard": os.path.join(project_root, "dqs-dashboard"),
    }
