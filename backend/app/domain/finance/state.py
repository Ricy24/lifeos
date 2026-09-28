"""
LifeOS Finance — Canonical Financial State
Pure data structures representing the deterministic financial truth.
Zero AI, zero external dependencies.
"""

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional


@dataclass(frozen=True)
class ProtectedCashItem:
    """A traceable component of protected money."""
    category: str           # "hard_obligation", "emergency_minimum", "partner_money", "goal_allocation"
    source_id: Optional[str]
    name: str
    amount: float
    is_hard_constraint: bool
    notes: Optional[str] = None


@dataclass(frozen=True)
class ProtectedCashBreakdown:
    """
    Detailed, traceable breakdown of protected money.
    Every protected peso must be traceable to its origin.
    """
    available_cash: float
    hard_obligations: float
    emergency_minimum: float
    protected_partner_money: float
    protected_goal_money: float
    total_protected: float
    free_cash: float
    items: List[ProtectedCashItem] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "available_cash": self.available_cash,
            "hard_obligations": self.hard_obligations,
            "emergency_minimum": self.emergency_minimum,
            "protected_partner_money": self.protected_partner_money,
            "protected_goal_money": self.protected_goal_money,
            "total_protected": self.total_protected,
            "free_cash": self.free_cash,
            "items": [
                {
                    "category": item.category,
                    "source_id": item.source_id,
                    "name": item.name,
                    "amount": item.amount,
                    "is_hard_constraint": item.is_hard_constraint,
                    "notes": item.notes,
                }
                for item in self.items
            ]
        }


@dataclass(frozen=True)
class FinancialHealthIndicators:
    """
    Transparent mathematical indicators for health scoring.
    All indicators are normalized between 0.0 and 1.0 (or higher for ratios).
    """
    liquidity_months: float          # available_cash / monthly_expenses (in months)
    obligation_coverage: float       # monthly_income / monthly_obligations ratio
    debt_pressure: float             # monthly_debt_payments / monthly_income ratio
    savings_rate: float              # (monthly_income - monthly_expenses) / monthly_income
    income_stability: float          # 0.0 (erratic) to 1.0 (steady)
    goal_progress: float             # average progress percentage of active goals (0.0 - 1.0)

    def to_dict(self) -> Dict[str, float]:
        return {
            "liquidity_months": round(self.liquidity_months, 2),
            "obligation_coverage": round(self.obligation_coverage, 2),
            "debt_pressure": round(self.debt_pressure, 2),
            "savings_rate": round(self.savings_rate, 2),
            "income_stability": round(self.income_stability, 2),
            "goal_progress": round(self.goal_progress, 2),
        }


@dataclass(frozen=True)
class FinancialHealthAssessment:
    """
    Deterministic composite assessment of financial health.
    Status can be GREEN, YELLOW, or RED with explicit rationales.
    """
    score: float                     # 0.0 to 100.0
    status: str                      # "GREEN", "YELLOW", "RED"
    indicators: FinancialHealthIndicators
    weights: Dict[str, float]
    thresholds: Dict[str, Any]
    explanations: List[str]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "score": round(self.score, 1),
            "status": self.status,
            "indicators": self.indicators.to_dict(),
            "weights": self.weights,
            "thresholds": self.thresholds,
            "explanations": self.explanations,
        }


@dataclass(frozen=True)
class CanonicalFinancialState:
    """
    The canonical, authoritative financial state.
    Calculated purely from persisted records without probabilistic logic.
    """
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
    protected_cash_breakdown: ProtectedCashBreakdown
    health_assessment: FinancialHealthAssessment
    timestamp: datetime = field(default_factory=lambda: datetime.now(timezone.utc))

    def to_dict(self) -> Dict[str, Any]:
        return {
            "available_cash": round(self.available_cash, 2),
            "total_assets": round(self.total_assets, 2),
            "total_liabilities": round(self.total_liabilities, 2),
            "net_worth": round(self.net_worth, 2),
            "monthly_income": round(self.monthly_income, 2),
            "monthly_expenses": round(self.monthly_expenses, 2),
            "monthly_obligations": round(self.monthly_obligations, 2),
            "protected_cash": round(self.protected_cash, 2),
            "free_cash": round(self.free_cash, 2),
            "goal_progress": round(self.goal_progress, 2),
            "debt_pressure": round(self.debt_pressure, 2),
            "obligation_coverage": round(self.obligation_coverage, 2),
            "financial_health_score": round(self.financial_health_score, 1),
            "financial_health_status": self.financial_health_status,
            "protected_cash_breakdown": self.protected_cash_breakdown.to_dict(),
            "health_assessment": self.health_assessment.to_dict(),
            "timestamp": self.timestamp.isoformat(),
        }
