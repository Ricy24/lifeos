"""
LifeOS Finance — Visited Places Model
"""

import uuid
from datetime import datetime, timezone
from sqlalchemy import BigInteger, Column, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship

from app.core.database import Base


class VisitedPlace(Base):
    __tablename__ = "visited_places"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)

    name = Column(String(150), nullable=False)
    category = Column(String(50), default="Restaurante", nullable=False)  # Restaurante, Café, Bar, Mirador, Actividad
    address_or_area = Column(String(200), default="", nullable=False)
    rating = Column(Integer, default=5, nullable=False)  # 1 to 5
    average_cost = Column(Float, default=0.0, nullable=False)
    notes = Column(Text, nullable=True)
    maps_url = Column(String(500), nullable=True)
    visited_date = Column(BigInteger, nullable=False, default=lambda: int(datetime.now(timezone.utc).timestamp() * 1000))
    created_at = Column(BigInteger, nullable=False, default=lambda: int(datetime.now(timezone.utc).timestamp() * 1000))

    user = relationship("User", backref="visited_places")
