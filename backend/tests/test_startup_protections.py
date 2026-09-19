"""
Tests for System Protections:
1. Startup fails with clear message if JWT_SECRET is missing.
2. Test runner / database utilities abort if database name does not end in '_test'.
"""

import pytest
from app.main import lifespan, app
from app.core.config import settings
from tests.conftest import assert_test_database_name


@pytest.mark.asyncio
async def test_startup_fails_without_jwt_secret():
    """Verify application startup fails with a clear message if JWT_SECRET is missing."""
    # Temporarily clear secret
    orig_jwt_secret = settings.JWT_SECRET
    orig_secret_key = settings.SECRET_KEY
    try:
        settings.JWT_SECRET = ""
        settings.SECRET_KEY = "CHANGE-THIS-SECRET-KEY-IN-PRODUCTION"

        with pytest.raises(RuntimeError) as exc_info:
            async with lifespan(app):
                pass

        assert "Missing required JWT_SECRET" in str(exc_info.value)
        assert "Application cannot start securely" in str(exc_info.value)
    finally:
        settings.JWT_SECRET = orig_jwt_secret
        settings.SECRET_KEY = orig_secret_key


def test_database_name_protection_rejects_non_test_db():
    """Verify test guard aborts when database name does not end with '_test'."""
    safe_url = "postgresql+asyncpg://postgres:pass@localhost:5432/my_app_test"
    # Safe database should not raise
    assert_test_database_name(safe_url)

    dangerous_urls = [
        "postgresql+asyncpg://postgres:pass@localhost:5432/lifeos_finance",
        "postgresql+asyncpg://postgres:pass@localhost:5432/production_db",
        "postgresql+asyncpg://postgres:pass@localhost:5432/test_db_prod",
    ]

    for bad_url in dangerous_urls:
        with pytest.raises(RuntimeError) as exc_info:
            assert_test_database_name(bad_url)
        assert "does not end with '_test'" in str(exc_info.value)
