"""
LifeOS Finance — Account Schemas
"""

from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field


class AccountCreate(BaseModel):
    name: str = Field(..., max_length=100)
    account_type: str = Field(default="cash")
    balance: float = Field(default=0.0)
    currency: str = Field(default="COP")
    description: Optional[str] = None
    color: Optional[str] = None
    icon: Optional[str] = None
    include_in_total: bool = True
    id: Optional[str] = None  # Client-provided UUID for offline sync


class AccountUpdate(BaseModel):
    name: Optional[str] = None
    account_type: Optional[str] = None
    balance: Optional[float] = None
    currency: Optional[str] = None
    description: Optional[str] = None
    color: Optional[str] = None
    icon: Optional[str] = None
    is_active: Optional[bool] = None
    include_in_total: Optional[bool] = None


class AccountResponse(BaseModel):
    id: str
    user_id: str
    name: str
    account_type: str
    balance: float
    currency: str
    description: Optional[str] = None
    color: Optional[str] = None
    icon: Optional[str] = None
    is_active: bool
    include_in_total: bool
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
