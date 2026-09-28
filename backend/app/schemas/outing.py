"""
LifeOS Finance — Outings & Places Schemas
"""

from typing import List, Optional
from pydantic import BaseModel, ConfigDict


class OutingStop(BaseModel):
    order: int
    title: str
    category: str
    estimated_cost: float
    description: str
    maps_query: str
    maps_url: str
    image_url: Optional[str] = None
    rating: float = 4.8
    review_count: int = 120
    highlight_review: Optional[str] = None
    distance_km: Optional[float] = None


class OutingPlanRequest(BaseModel):
    outing_type: str = "Cita Romántica"  # Cita Romántica, Tarde con Amigos, Salida Motera, Café & Charla
    budget: Optional[float] = None
    area_or_city: str = "Bogotá"
    preferences: Optional[str] = None
    use_current_location: bool = False
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    radius_km: Optional[int] = 5


class OutingPlanResponse(BaseModel):
    title: str
    summary: str
    total_estimated_cost: float
    safe_budget_available: float
    stops: List[OutingStop]
    financial_advice: str


class VisitedPlaceCreate(BaseModel):
    name: str
    category: str = "Restaurante"
    address_or_area: str = ""
    rating: int = 5
    average_cost: float = 0.0
    notes: Optional[str] = None
    maps_url: Optional[str] = None
    visited_date: Optional[int] = None


class VisitedPlaceResponse(VisitedPlaceCreate):
    id: str
    user_id: str
    created_at: int

    model_config = ConfigDict(from_attributes=True)
