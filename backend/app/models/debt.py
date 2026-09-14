"""
LifeOS Finance — Debt Model
Track debts with payments, priorities, and status.
"""

import uuid
from datetime import datetime, timezone
from enum import Enum as PyEnum

from sqlalchemy import (
    DateTime, Enum, Float, ForeignKey, Integer, String, Text, JSON
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base
from app.services.priority_engine import calculate_debt_priority


class DebtStatus(str, PyEnum):
    PENDING = "pending"
    PARTIALLY_PAID = "partially_paid"
    PAID = "paid"
    OVERDUE = "overdue"


class DebtType(str, PyEnum):
    I_OWE = "i_owe"        # I owe someone
    OWED_TO_ME = "owed_to_me"  # Someone owes me


class Debt(Base):
    __tablename__ = "debts"

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

    # Core
    person_or_entity: Mapped[str] = mapped_column(String(255), nullable=False)
    debt_type: Mapped[str] = mapped_column(
        Enum(DebtType, name="debt_type_enum"),
        default=DebtType.I_OWE,
    )
    original_amount: Mapped[float] = mapped_column(Float, nullable=False)
    remaining_amount: Mapped[float] = mapped_column(Float, nullable=False)
    interest_rate: Mapped[float] = mapped_column(Float, nullable=True, default=0.0)

    # Dates
    debt_date: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
    )
    due_date: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    # Priority & Status
    priority: Mapped[int] = mapped_column(Integer, default=3)  # 1=highest, 5=lowest
    status: Mapped[str] = mapped_column(
        Enum(DebtStatus, name="debt_status_enum"),
        default=DebtStatus.PENDING,
    )

    # Metadata
    description: Mapped[str] = mapped_column(Text, nullable=True)
    notes: Mapped[str] = mapped_column(Text, nullable=True)
    tags: Mapped[dict] = mapped_column(JSON, nullable=True, default=list)

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
    user = relationship("User", back_populates="debts")
    payments = relationship("DebtPayment", back_populates="debt", lazy="selectin")

    @property
    def priority_score(self) -> int:
        return calculate_debt_priority(
            priority=self.priority,
            status=self.status.value if isinstance(self.status, DebtStatus) else self.status,
            due_date=self.due_date,
            interest_rate=self.interest_rate,
            original_amount=self.original_amount,
            remaining_amount=self.remaining_amount,
        )


class DebtPayment(Base):
    __tablename__ = "debt_payments"

    id: Mapped[str] = mapped_column(
        UUID(as_uuid=False),
        primary_key=True,
        default=lambda: str(uuid.uuid4()),
    )
    debt_id: Mapped[str] = mapped_column(
        UUID(as_uuid=False),
        ForeignKey("debts.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    amount: Mapped[float] = mapped_column(Float, nullable=False)
    payment_date: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
    )
    notes: Mapped[str] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
    )

    # Relationships
    debt = relationship("Debt", back_populates="payments")
