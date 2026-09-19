"""
LifeOS Finance — Goal & Wishlist Schemas
"""

from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field

from app.schemas.common import ContractBaseModel


class GoalCreate(BaseModel):
    name: str = Field(..., max_length=255)
    target_amount: float = Field(..., gt=0)
    current_amount: float = Field(default=0.0, ge=0)
    category: str = Field(default="purchase")
    description: Optional[str] = None
    image_url: Optional[str] = None
    priority: int = Field(default=3, ge=1, le=5)
    target_date: Optional[datetime] = None
    notes: Optional[str] = None
    tags: Optional[List[str]] = None
    id: Optional[str] = None


class GoalUpdate(BaseModel):
    name: Optional[str] = None
    target_amount: Optional[float] = None
    current_amount: Optional[float] = None
    category: Optional[str] = None
    description: Optional[str] = None
    image_url: Optional[str] = None
    priority: Optional[int] = None
    target_date: Optional[datetime] = None
    status: Optional[str] = None
    notes: Optional[str] = None


class GoalResponse(ContractBaseModel):
    id: str
    user_id: str
    name: str
    target_amount: float
    current_amount: float
    category: str
    description: Optional[str] = None
    image_url: Optional[str] = None
    priority: int
    priority_score: int = 0
    target_date: Optional[datetime] = None
    status: str
    notes: Optional[str] = None
    tags: Optional[List[str]] = None
    progress_percentage: float = 0.0
    remaining_amount: float = 0.0
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class WishlistItemCreate(BaseModel):
    name: str = Field(..., max_length=255)
    price: float = Field(..., gt=0)
    url: Optional[str] = None
    store: Optional[str] = None
    image_url: Optional[str] = None
    category: Optional[str] = None
    priority: int = Field(default=3, ge=1, le=5)
    saved_amount: float = Field(default=0.0, ge=0)
    notes: Optional[str] = None
    id: Optional[str] = None


class WishlistItemUpdate(BaseModel):
    name: Optional[str] = None
    price: Optional[float] = None
    url: Optional[str] = None
    store: Optional[str] = None
    image_url: Optional[str] = None
    category: Optional[str] = None
    priority: Optional[int] = None
    saved_amount: Optional[float] = None
    status: Optional[str] = None
    notes: Optional[str] = None


class WishlistItemResponse(ContractBaseModel):
    id: str
    user_id: str
    name: str
    price: float
    url: Optional[str] = None
    store: Optional[str] = None
    image_url: Optional[str] = None
    category: Optional[str] = None
    priority: int
    saved_amount: float
    previous_price: Optional[float] = None
    status: str
    notes: Optional[str] = None
    progress_percentage: float = 0.0
    remaining_amount: float = 0.0
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
