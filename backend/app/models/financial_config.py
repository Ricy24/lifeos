"""
LifeOS Finance — Financial Configuration Model
User's financial settings: hourly rate, daily targets, etc.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, ForeignKey, Integer, String, JSON
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class FinancialConfig(Base):
    __tablename__ = "financial_configs"

    id: Mapped[str] = mapped_column(
        UUID(as_uuid=False),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    user_id: Mapped[str] = mapped_column(
        UUID(as_uuid=False),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
        index=True,
    )

    # Income settings
    hourly_rate: Mapped[float] = mapped_column(Float, default=20000.0)  # COP per hour
    daily_target: Mapped[float] = mapped_column(Float, default=100000.0)
    weekly_target: Mapped[float] = mapped_column(Float, default=500000.0)
    monthly_target: Mapped[float] = mapped_column(Float, default=2000000.0)

    # Work settings
    work_days_per_week: Mapped[int] = mapped_column(Integer, default=6)
    work_hours_per_day: Mapped[float] = mapped_column(Float, default=8.0)

    # Currency & locale
    currency: Mapped[str] = mapped_column(String(10), default="COP")
    timezone: Mapped[str] = mapped_column(String(50), default="America/Bogota")

    # Custom categories (JSON arrays)
    income_categories: Mapped[dict] = mapped_column(JSON, nullable=True, default=list)
    expense_categories: Mapped[dict] = mapped_column(JSON, nullable=True, default=list)

    # Timestamps
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )
