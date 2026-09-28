"""
LifeOS Finance — Analytics V2 Schemas
"""

from typing import Dict, List, Optional
from pydantic import BaseModel, Field

from app.schemas.common import ContractBaseModel


class HealthIndexRequest(BaseModel):
    liquidity_months: Optional[float] = Field(None, description="Months of expenses covered by liquid assets")
    savings_rate: Optional[float] = Field(None, description="Current monthly savings rate (e.g. 0.20 for 20%)")
    debt_to_income_ratio: Optional[float] = Field(None, description="Monthly debt payments / monthly income")
    weights: Optional[Dict[str, float]] = Field(None, description="Custom weights for liquidity, savings, debt")
    thresholds: Optional[Dict[str, float]] = Field(None, description="Custom targets for thresholds")


class HealthIndexResponse(ContractBaseModel):
    health_index: Optional[float] = None
    liquidity_score: Optional[float] = None
    savings_score: Optional[float] = None
    debt_score: Optional[float] = None
    normalized_weights: Optional[Dict[str, float]] = None
    status: str = "sufficient_data"
