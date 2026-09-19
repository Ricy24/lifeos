"""
LifeOS Finance — Transaction Model
Income and expense transactions with full metadata.
"""

import uuid
from datetime import datetime, timezone
from enum import Enum as PyEnum

from sqlalchemy import (
    Boolean, DateTime, Enum, ForeignKey, Numeric, String, Text, JSON
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base, AuditMixin


class TransactionType(str, PyEnum):
    INCOME = "income"
    EXPENSE = "expense"


class IncomeCategory(str, PyEnum):
    WORK = "work"
    SALARY = "salary"
    FREELANCE = "freelance"
    SALE = "sale"
    REFUND = "refund"
    GIFT = "gift"
    INVESTMENT = "investment"
    OTHER = "other"


class ExpenseCategory(str, PyEnum):
    FOOD = "food"
    GASOLINE = "gasoline"
    TRANSPORT = "transport"
    GYM = "gym"
    ENTERTAINMENT = "entertainment"
    SHOPPING = "shopping"
    BILLS = "bills"
    HOUSING = "housing"
    TECHNOLOGY = "technology"
    MOTORCYCLE = "motorcycle"
    HEALTH = "health"
    EDUCATION = "education"
    CLOTHING = "clothing"
    PERSONAL = "personal"
    SUBSCRIPTIONS = "subscriptions"
    DEBT_PAYMENT = "debt_payment"
    SAVINGS = "savings"
    OTHER = "other"


class SyncStatus(str, PyEnum):
    SYNCED = "synced"
    PENDING = "pending"
    FAILED = "failed"


class Transaction(AuditMixin, Base):
    __tablename__ = "transactions"

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
    account_id: Mapped[str] = mapped_column(
        UUID(as_uuid=False),
        ForeignKey("accounts.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )

    # Core fields
    amount: Mapped[float] = mapped_column(Numeric(14, 2), nullable=False)
    transaction_type: Mapped[str] = mapped_column(
        Enum(TransactionType, name="transaction_type_enum", values_callable=lambda x: [e.value for e in x]),
        nullable=False,
    )
    category: Mapped[str] = mapped_column(String(50), nullable=False)
    description: Mapped[str] = mapped_column(String(500), nullable=True)
    notes: Mapped[str] = mapped_column(Text, nullable=True)

    # Time
    transaction_date: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        index=True,
    )

    # Metadata
    location: Mapped[str] = mapped_column(String(255), nullable=True)
    tags: Mapped[dict] = mapped_column(JSON, nullable=True, default=list)
    is_recurring: Mapped[bool] = mapped_column(Boolean, default=False)
    recurring_pattern: Mapped[str] = mapped_column(String(50), nullable=True)  # daily, weekly, monthly
    source: Mapped[str] = mapped_column(String(50), default="app")  # app, telegram, import
    metadata_extra: Mapped[dict] = mapped_column(JSON, nullable=True, default=dict)

    # Sync
    sync_status: Mapped[str] = mapped_column(
        Enum(SyncStatus, name="sync_status_enum", values_callable=lambda x: [e.value for e in x]),
        default=SyncStatus.SYNCED,
    )

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

    # Relationships
    user = relationship("User", back_populates="transactions")
    account = relationship("Account", back_populates="transactions")
