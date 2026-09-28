"""
LifeOS Finance — Pydantic Schemas for Financial Truth & Health
"""

from typing import Any, Dict, List, Optional
from pydantic import BaseModel


class ProtectedCashItemSchema(BaseModel):
    category: str
    source_id: Optional[str] = None
    name: str
    amount: float
    is_hard_constraint: bool
    notes: Optional[str] = None


class ProtectedCashBreakdownSchema(BaseModel):
    available_cash: float
    hard_obligations: float
    emergency_minimum: float
    protected_partner_money: float
    protected_goal_money: float
    total_protected: float
    free_cash: float
    items: List[ProtectedCashItemSchema]


class FinancialHealthIndicatorsSchema(BaseModel):
    liquidity_months: float
    obligation_coverage: float
    debt_pressure: float
    savings_rate: float
    income_stability: float
    goal_progress: float


class FinancialHealthResponse(BaseModel):
    score: float
    status: str  # GREEN, YELLOW, RED
    indicators: FinancialHealthIndicatorsSchema
    weights: Dict[str, float]
    thresholds: Dict[str, Any]
    explanations: List[str]


class CanonicalFinancialStateResponse(BaseModel):
    available_cash: float
    total_assets: float
    total_liabilities: float
    net_worth: float
    monthly_income: float
    monthly_expenses: float
    monthly_obligations: float
    protected_cash: float
    free_cash: float
    goal_progress: float
    debt_pressure: float
    obligation_coverage: float
    financial_health_score: float
    financial_health_status: str
    protected_cash_breakdown: ProtectedCashBreakdownSchema
    health_assessment: FinancialHealthResponse
    timestamp: str


class FinancialSnapshotResponse(BaseModel):
    id: str
    user_id: str
    snapshot_date: str
    snapshot_type: str
    available_cash: float
    total_assets: float
    total_liabilities: float
    net_worth: float
    monthly_income: float
    monthly_expenses: float
    monthly_obligations: float
    protected_cash: float
    free_cash: float
    goal_progress: float
    debt_pressure: float
    obligation_coverage: float
    financial_health_score: float
    financial_health_status: str
    indicators: Optional[Dict[str, Any]] = None
    metadata_extra: Optional[Dict[str, Any]] = None
    created_at: str

    class Config:
        from_attributes = True
