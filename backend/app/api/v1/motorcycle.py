"""
LifeOS Finance — Motorcycle API Endpoints
"""

from datetime import datetime, timezone
import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.models.motorcycle import Motorcycle
from app.schemas.motorcycle import MotorcycleResponse, MotorcycleUpdate

router = APIRouter(prefix="/motorcycle", tags=["Motorcycle Garage"])


@router.get("", response_model=MotorcycleResponse)
async def get_motorcycle(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get the user's motorcycle or create a default one if none exists."""
    stmt = select(Motorcycle).where(Motorcycle.user_id == current_user.id)
    result = await db.execute(stmt)
    moto = result.scalar_one_or_none()

    if not moto:
        now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
        one_year_ms = 31536000 * 1000
        moto = Motorcycle(
            id=str(uuid.uuid4()),
            user_id=current_user.id,
            name="Mi Moto",
            model="2024",
            current_mileage=12500,
            oil_change_interval=2500,
            last_oil_change_mileage=11000,
            front_tire_mileage=2000,
            front_tire_life_km=18000,
            rear_tire_mileage=4500,
            rear_tire_life_km=12000,
            brake_pads_mileage=3000,
            brake_pads_life_km=8000,
            chain_maintenance_mileage=12200,
            chain_maintenance_interval=1000,
            soat_expiry_date=now_ms + one_year_ms,
            techno_expiry_date=now_ms + one_year_ms,
            cost_per_km=45.0,
            last_updated=now_ms,
        )
        db.add(moto)
        await db.commit()
        await db.refresh(moto)

    return moto


@router.put("", response_model=MotorcycleResponse)
async def update_motorcycle(
    update_data: MotorcycleUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update any or all parameters of the motorcycle."""
    stmt = select(Motorcycle).where(Motorcycle.user_id == current_user.id)
    result = await db.execute(stmt)
    moto = result.scalar_one_or_none()

    if not moto:
        # Create first with defaults, then update
        now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
        one_year_ms = 31536000 * 1000
        moto = Motorcycle(
            id=str(uuid.uuid4()),
            user_id=current_user.id,
            name=update_data.name or "Mi Moto",
            model=update_data.model or "2024",
            current_mileage=update_data.current_mileage or 0,
            oil_change_interval=update_data.oil_change_interval or 2500,
            last_oil_change_mileage=update_data.last_oil_change_mileage or 0,
            front_tire_mileage=update_data.front_tire_mileage or 0,
            front_tire_life_km=update_data.front_tire_life_km or 18000,
            rear_tire_mileage=update_data.rear_tire_mileage or 0,
            rear_tire_life_km=update_data.rear_tire_life_km or 12000,
            brake_pads_mileage=update_data.brake_pads_mileage or 0,
            brake_pads_life_km=update_data.brake_pads_life_km or 8000,
            chain_maintenance_mileage=update_data.chain_maintenance_mileage or 0,
            chain_maintenance_interval=update_data.chain_maintenance_interval or 1000,
            soat_expiry_date=update_data.soat_expiry_date or (now_ms + one_year_ms),
            techno_expiry_date=update_data.techno_expiry_date or (now_ms + one_year_ms),
            cost_per_km=update_data.cost_per_km or 45.0,
            last_updated=now_ms,
        )
        db.add(moto)
    else:
        update_dict = update_data.model_dump(exclude_unset=True)
        for key, value in update_dict.items():
            setattr(moto, key, value)
        moto.last_updated = int(datetime.now(timezone.utc).timestamp() * 1000)

    await db.commit()
    await db.refresh(moto)
    return moto
