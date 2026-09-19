"""
LifeOS Finance — Debt Schemas
"""

from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel, Field

from app.schemas.common import ContractBaseModel


class DebtCreate(BaseModel):
    person_or_entity: str = Field(..., max_length=255)
    debt_type: str = Field(default="i_owe")
    original_amount: float = Field(..., gt=0)
    remaining_amount: Optional[float] = None  # Defaults to original_amount
    interest_rate: float = Field(default=0.0, ge=0)
    debt_date: Optional[datetime] = None
    due_date: Optional[datetime] = None
    priority: int = Field(default=3, ge=1, le=5)
    description: Optional[str] = None
    notes: Optional[str] = None
    tags: Optional[List[str]] = None
    id: Optional[str] = None


class DebtUpdate(BaseModel):
    person_or_entity: Optional[str] = None
    debt_type: Optional[str] = None
    remaining_amount: Optional[float] = None
    interest_rate: Optional[float] = None
    due_date: Optional[datetime] = None
    priority: Optional[int] = None
    status: Optional[str] = None
    description: Optional[str] = None
    notes: Optional[str] = None


class DebtPaymentCreate(BaseModel):
    amount: float = Field(..., gt=0)
    payment_date: Optional[datetime] = None
    notes: Optional[str] = None


class DebtPaymentResponse(ContractBaseModel):
    id: str
    debt_id: str
    amount: float
    payment_date: datetime
    notes: Optional[str] = None
    created_at: datetime

    class Config:
        from_attributes = True


class DebtResponse(ContractBaseModel):
    id: str
    user_id: str
    person_or_entity: str
    debt_type: str
    original_amount: float
    remaining_amount: float
    interest_rate: float
    debt_date: datetime
    due_date: Optional[datetime] = None
    priority: int
    priority_score: int = 0
    status: str
    description: Optional[str] = None
    notes: Optional[str] = None
    tags: Optional[List[str]] = None
    payments: List[DebtPaymentResponse] = []
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True
