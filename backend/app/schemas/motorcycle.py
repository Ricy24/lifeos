"""
LifeOS Finance — Motorcycle Schemas
"""

from typing import Optional
from pydantic import BaseModel, ConfigDict


class MotorcycleBase(BaseModel):
    name: str = "Mi Moto"
    model: str = "2024"
    current_mileage: int = 0
    oil_change_interval: int = 2500
    last_oil_change_mileage: int = 0
    front_tire_mileage: int = 0
    front_tire_life_km: int = 18000
    rear_tire_mileage: int = 0
    rear_tire_life_km: int = 12000
    brake_pads_mileage: int = 0
    brake_pads_life_km: int = 8000
    chain_maintenance_mileage: int = 0
    chain_maintenance_interval: int = 1000
    soat_expiry_date: int
    techno_expiry_date: int
    cost_per_km: float = 45.0


class MotorcycleUpdate(BaseModel):
    name: Optional[str] = None
    model: Optional[str] = None
    current_mileage: Optional[int] = None
    oil_change_interval: Optional[int] = None
    last_oil_change_mileage: Optional[int] = None
    front_tire_mileage: Optional[int] = None
    front_tire_life_km: Optional[int] = None
    rear_tire_mileage: Optional[int] = None
    rear_tire_life_km: Optional[int] = None
    brake_pads_mileage: Optional[int] = None
    brake_pads_life_km: Optional[int] = None
    chain_maintenance_mileage: Optional[int] = None
    chain_maintenance_interval: Optional[int] = None
    soat_expiry_date: Optional[int] = None
    techno_expiry_date: Optional[int] = None
    cost_per_km: Optional[float] = None


class MotorcycleResponse(MotorcycleBase):
    id: str
    user_id: str
    last_updated: int

    model_config = ConfigDict(from_attributes=True)
