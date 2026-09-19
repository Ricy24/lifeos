"""
LifeOS Finance — Transaction Schemas
"""

from datetime import datetime
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field

from app.schemas.common import ContractBaseModel


class TransactionCreate(BaseModel):
    amount: float = Field(..., gt=0, description="Transaction amount (positive)")
    transaction_type: str = Field(..., description="income or expense")
    category: str = Field(..., description="Transaction category")
    description: Optional[str] = None
    notes: Optional[str] = None
    account_id: Optional[str] = None
    transaction_date: Optional[datetime] = None
    location: Optional[str] = None
    tags: Optional[List[str]] = None
    is_recurring: bool = False
    recurring_pattern: Optional[str] = None
    source: str = "app"
    metadata_extra: Optional[Dict[str, Any]] = None
    # For offline sync
    id: Optional[str] = None  # Client can provide UUID


class TransactionUpdate(BaseModel):
    amount: Optional[float] = Field(None, gt=0)
    transaction_type: Optional[str] = None
    category: Optional[str] = None
    description: Optional[str] = None
    notes: Optional[str] = None
    account_id: Optional[str] = None
    transaction_date: Optional[datetime] = None
    location: Optional[str] = None
    tags: Optional[List[str]] = None
    is_recurring: Optional[bool] = None
    recurring_pattern: Optional[str] = None


class TransactionResponse(ContractBaseModel):
    id: str
    user_id: str
    account_id: Optional[str] = None
    amount: float
    transaction_type: str
    category: str
    description: Optional[str] = None
    notes: Optional[str] = None
    transaction_date: datetime
    location: Optional[str] = None
    tags: Optional[List[str]] = None
    is_recurring: bool = False
    recurring_pattern: Optional[str] = None
    source: str = "app"
    sync_status: str = "synced"
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class TransactionListResponse(BaseModel):
    transactions: List[TransactionResponse]
    total: int
    page: int
    page_size: int
