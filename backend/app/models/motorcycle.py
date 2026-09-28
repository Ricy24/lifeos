"""
LifeOS Finance — Motorcycle Model
"""

import uuid
from datetime import datetime, timezone
from sqlalchemy import BigInteger, Column, DateTime, Float, ForeignKey, Integer, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.core.database import Base


class Motorcycle(Base):
    __tablename__ = "motorcycles"

    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(UUID(as_uuid=False), ForeignKey("users.id", ondelete="CASCADE"), nullable=False, unique=True)

    name = Column(String(100), default="Mi Moto", nullable=False)
    model = Column(String(50), default="2024", nullable=False)
    current_mileage = Column(Integer, default=0, nullable=False)

    # Maintenance intervals and records
    oil_change_interval = Column(Integer, default=2500, nullable=False)
    last_oil_change_mileage = Column(Integer, default=0, nullable=False)

    front_tire_mileage = Column(Integer, default=0, nullable=False)
    front_tire_life_km = Column(Integer, default=18000, nullable=False)

    rear_tire_mileage = Column(Integer, default=0, nullable=False)
    rear_tire_life_km = Column(Integer, default=12000, nullable=False)

    brake_pads_mileage = Column(Integer, default=0, nullable=False)
    brake_pads_life_km = Column(Integer, default=8000, nullable=False)

    chain_maintenance_mileage = Column(Integer, default=0, nullable=False)
    chain_maintenance_interval = Column(Integer, default=1000, nullable=False)

    soat_expiry_date = Column(BigInteger, nullable=False, default=lambda: int((datetime.now(timezone.utc).timestamp() + 31536000) * 1000))
    techno_expiry_date = Column(BigInteger, nullable=False, default=lambda: int((datetime.now(timezone.utc).timestamp() + 31536000) * 1000))

    cost_per_km = Column(Float, default=45.0, nullable=False)
    last_updated = Column(BigInteger, nullable=False, default=lambda: int(datetime.now(timezone.utc).timestamp() * 1000))

    user = relationship("User", backref="motorcycle")
