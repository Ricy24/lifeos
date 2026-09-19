"""
LifeOS Finance — Financial Snapshot Model
Historical and cached snapshots of canonical financial state.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import DateTime, ForeignKey, JSON, Numeric, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base, AuditMixin


class FinancialSnapshot(AuditMixin, Base):
    __tablename__ = "financial_snapshots"

    id: Mapped[str] = mapped_column(
        UUID(as_uuid=False),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    user_id: Mapped[str] = mapped_column(
        UUID(as_uuid=False),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    snapshot_date: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        index=True,
    )
    snapshot_type: Mapped[str] = mapped_column(
        String(20),
        default="daily",  # daily, weekly, monthly, adhoc
    )

    # Core Canonical Financial State values
    available_cash: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    total_assets: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    total_liabilities: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    net_worth: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    monthly_income: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    monthly_expenses: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    monthly_obligations: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    protected_cash: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    free_cash: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)

    # Performance & Pressure Indicators
    goal_progress: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    debt_pressure: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    obligation_coverage: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    financial_health_score: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    financial_health_status: Mapped[str] = mapped_column(String(20), default="GREEN")

    # Detailed traceable breakdowns
    indicators: Mapped[dict] = mapped_column(JSON, nullable=True, default=dict)
    metadata_extra: Mapped[dict] = mapped_column(JSON, nullable=True, default=dict)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
    )
    
    # Relationships
    user = relationship("User")
