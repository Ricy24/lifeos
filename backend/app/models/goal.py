"""
LifeOS Finance — Goal Model
Financial goals and wishlist items.
"""

import uuid
from datetime import datetime, timezone
from enum import Enum as PyEnum

from sqlalchemy import (
    DateTime, Enum, ForeignKey, Integer, Numeric, String, Text, JSON
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base, AuditMixin
from app.services.priority_engine import calculate_goal_priority


class GoalStatus(str, PyEnum):
    ACTIVE = "active"
    PAUSED = "paused"
    COMPLETED = "completed"
    CANCELLED = "cancelled"


class GoalCategory(str, PyEnum):
    PURCHASE = "purchase"
    SAVINGS = "savings"
    EMERGENCY = "emergency"
    INVESTMENT = "investment"
    TRAVEL = "travel"
    EDUCATION = "education"
    HEALTH = "health"
    OTHER = "other"


class Goal(AuditMixin, Base):
    __tablename__ = "goals"

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
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    target_amount: Mapped[float] = mapped_column(Numeric(14, 2), nullable=False)
    current_amount: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    category: Mapped[str] = mapped_column(
        Enum(GoalCategory, name="goal_category_enum"),
        default=GoalCategory.PURCHASE,
    )

    # Details
    description: Mapped[str] = mapped_column(Text, nullable=True)
    image_url: Mapped[str] = mapped_column(String(500), nullable=True)
    priority: Mapped[int] = mapped_column(Integer, default=3)  # 1=highest
    target_date: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=True)
    status: Mapped[str] = mapped_column(
        Enum(GoalStatus, name="goal_status_enum"),
        default=GoalStatus.ACTIVE,
    )
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
    user = relationship("User", back_populates="goals")

    @property
    def progress_percentage(self) -> float:
        """Calculate progress as a percentage."""
        if self.target_amount <= 0:
            return 100.0
        return min(100.0, (self.current_amount / self.target_amount) * 100)

    @property
    def remaining_amount(self) -> float:
        """Calculate remaining amount needed."""
        return max(0, self.target_amount - self.current_amount)

    @property
    def priority_score(self) -> int:
        return calculate_goal_priority(
            priority=self.priority,
            status=self.status.value if isinstance(self.status, GoalStatus) else self.status,
            category=self.category.value if isinstance(self.category, GoalCategory) else self.category,
            target_date=self.target_date,
            target_amount=self.target_amount,
            current_amount=self.current_amount,
        )


class WishlistItem(AuditMixin, Base):
    __tablename__ = "wishlist_items"

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

    # Product info
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    price: Mapped[float] = mapped_column(Numeric(14, 2), nullable=False)
    url: Mapped[str] = mapped_column(String(1000), nullable=True)
    store: Mapped[str] = mapped_column(String(255), nullable=True)
    image_url: Mapped[str] = mapped_column(String(1000), nullable=True)
    category: Mapped[str] = mapped_column(String(100), nullable=True)

    # Tracking
    priority: Mapped[int] = mapped_column(Integer, default=3)
    saved_amount: Mapped[float] = mapped_column(Numeric(14, 2), default=0.0)
    previous_price: Mapped[float] = mapped_column(Numeric(14, 2), nullable=True)
    status: Mapped[str] = mapped_column(String(50), default="wanted")  # wanted, saving, purchased, cancelled
    notes: Mapped[str] = mapped_column(Text, nullable=True)

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

    @property
    def progress_percentage(self) -> float:
        if self.price <= 0:
            return 100.0
        return min(100.0, (self.saved_amount / self.price) * 100)

    @property
    def remaining_amount(self) -> float:
        return max(0, self.price - self.saved_amount)
