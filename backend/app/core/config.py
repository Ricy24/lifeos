"""
LifeOS Finance — Application Settings
Loads configuration from environment variables.
"""

from pydantic_settings import BaseSettings
from pydantic import Field
from typing import List
import json


class Settings(BaseSettings):
    """Application configuration loaded from environment variables."""

    # App
    APP_NAME: str = "LifeOS Finance"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # Database
    DATABASE_URL: str = Field(
        default="postgresql+asyncpg://localhost/lifeos_finance",
        description="PostgreSQL connection string (asyncpg)"
    )

    # Security
    SECRET_KEY: str = Field(
        default="CHANGE-THIS-SECRET-KEY-IN-PRODUCTION",
        description="JWT signing key"
    )
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440  # 24 hours
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    CRON_SECRET: str = "change-this-cron-secret-in-production"

    # Initial User
    ADMIN_EMAIL: str = "admin@lifeos.finance"
    ADMIN_PASSWORD: str = "changeme"

    # AI
    AI_PROVIDER: str = "gemini"  # "gemini" or "openai"
    GEMINI_API_KEY: str = ""
    OPENAI_API_KEY: str = ""

    # Telegram
    TELEGRAM_BOT_TOKEN: str = ""
    TELEGRAM_ALLOWED_USER_ID: str = ""
    TELEGRAM_WEBHOOK_SECRET: str = ""
    TELEGRAM_WEBHOOK_URL: str = ""

    # Financial
    DEFAULT_CURRENCY: str = "COP"
    TIMEZONE: str = "America/Bogota"

    # CORS
    CORS_ORIGINS: str = '["*"]'

    @property
    def cors_origins_list(self) -> List[str]:
        """Parse CORS origins from JSON string."""
        try:
            return json.loads(self.CORS_ORIGINS)
        except (json.JSONDecodeError, TypeError):
            return ["*"]

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = True


settings = Settings()
