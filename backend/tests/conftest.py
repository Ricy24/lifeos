"""
LifeOS Finance — Pytest Root Configuration & Safety Hooks
Enforces database name guard (_test suffix) before running tests.
"""

from urllib.parse import urlparse
import pytest
from app.core.config import settings


def extract_db_name(url: str) -> str:
    """Extract database name from connection URL."""
    path = urlparse(url).path
    return path.lstrip("/").split("?")[0]


def assert_test_database_name(url: str):
    """Enforce that the target database name strictly ends with '_test'."""
    db_name = extract_db_name(url)
    if not db_name.endswith("_test"):
        raise RuntimeError(
            f"ABORT: Database '{db_name}' does not end with '_test'. "
            "Tests must only run against dedicated test databases ending in '_test'."
        )


def pytest_sessionstart(session):
    """
    Hook called before starting pytest runner.
    Aborts test execution if any database name does not end with '_test'.
    """
    # Check default test database
    test_db_urls = [
        "postgresql+asyncpg://postgres:Riki1049616429@localhost:5432/lifeos_test",
        "postgresql://postgres:Riki1049616429@localhost:5432/lifeos_test",
    ]
    for url in test_db_urls:
        assert_test_database_name(url)
