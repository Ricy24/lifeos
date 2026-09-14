"""
LifeOS Finance — Financial Summary Schemas
Dashboard and analytics response models.
"""

from datetime import datetime
from typing import Optional, List, Dict
from pydantic import BaseModel


class PeriodSummary(BaseModel):
    """Summary for a specific time period."""
    total_income: float = 0.0
    total_expenses: float = 0.0
    net: float = 0.0
    transaction_count: int = 0


class DashboardResponse(BaseModel):
    """Main dashboard data."""
    # Balances
    total_balance: float = 0.0
    net_worth: float = 0.0
    accounts: List[dict] = []

    # Period summaries
    today: PeriodSummary = PeriodSummary()
    this_week: PeriodSummary = PeriodSummary()
    this_month: PeriodSummary = PeriodSummary()

    # Targets
    daily_target: float = 0.0
    weekly_target: float = 0.0
    monthly_target: float = 0.0
    daily_progress_percentage: float = 0.0
    daily_remaining: float = 0.0

    # Work hours
    hourly_rate: float = 0.0
    hours_needed_today: float = 0.0

    # Debts
    total_debt: float = 0.0
    urgent_debts: int = 0

    # Goals
    active_goals: int = 0
    primary_goal: Optional[dict] = None

    # Recent
    recent_transactions: List[dict] = []

    # Timestamps
    generated_at: datetime = datetime.utcnow()


class CategoryBreakdown(BaseModel):
    category: str
    total: float
    percentage: float
    count: int


class FinancialAnalytics(BaseModel):
    """Analytics and statistics response."""
    period_start: datetime
    period_end: datetime

    income_by_category: List[CategoryBreakdown] = []
    expenses_by_category: List[CategoryBreakdown] = []

    daily_income: List[Dict[str, float]] = []
    daily_expenses: List[Dict[str, float]] = []

    average_daily_income: float = 0.0
    average_daily_expense: float = 0.0

    best_day_income: Optional[Dict[str, float]] = None
    worst_day_expenses: Optional[Dict[str, float]] = None

    savings_rate: float = 0.0


class FinancialConfigResponse(BaseModel):
    hourly_rate: float
    daily_target: float
    weekly_target: float
    monthly_target: float
    work_days_per_week: int
    work_hours_per_day: float
    currency: str
    timezone: str

    class Config:
        from_attributes = True


class FinancialConfigUpdate(BaseModel):
    hourly_rate: Optional[float] = None
    daily_target: Optional[float] = None
    weekly_target: Optional[float] = None
    monthly_target: Optional[float] = None
    work_days_per_week: Optional[int] = None
    work_hours_per_day: Optional[float] = None
    currency: Optional[str] = None
    timezone: Optional[str] = None


class WorkCalculation(BaseModel):
    """How much work is needed for a target amount."""
    target_amount: float
    hourly_rate: float
    hours_needed: float
    days_needed: float
    work_hours_per_day: float
