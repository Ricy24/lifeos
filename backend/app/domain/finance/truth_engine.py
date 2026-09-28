"""
LifeOS Finance — Financial Truth Engine
Coordinates pure domain calculations into a canonical financial state.
100% deterministic, testable in isolation without a database.
"""

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from app.domain.finance.calculator import (
    calculate_available_cash,
    calculate_total_assets,
    calculate_total_liabilities,
    calculate_net_worth,
    calculate_monthly_obligations,
    calculate_protected_cash,
    calculate_health_indicators,
    evaluate_health_score,
    evaluate_health_status,
    DEFAULT_HEALTH_WEIGHTS,
    HEALTH_THRESHOLDS,
)
from app.domain.finance.state import (
    CanonicalFinancialState,
    FinancialHealthAssessment,
)


class FinancialTruthEngine:
    """
    Canonical Financial Truth Engine.
    Operates purely on in-memory representations.
    """

    @classmethod
    def compute_state(
        cls,
        accounts: List[Dict[str, Any]],
        debts: List[Dict[str, Any]],
        recurring_expenses: List[Dict[str, Any]],
        monthly_income: float,
        monthly_expenses: float,
        monthly_debt_payments: float = 0.0,
        emergency_minimum: float = 0.0,
        partner_percentage: float = 0.0,
        active_goals: Optional[List[Dict[str, Any]]] = None,
        as_of: Optional[datetime] = None,
    ) -> CanonicalFinancialState:
        """
        Produce the canonical financial state deterministically.
        """
        timestamp = as_of or datetime.now(timezone.utc)

        # 1. Balances & Assets
        available_cash = calculate_available_cash(accounts)
        total_assets = calculate_total_assets(accounts)
        total_liabilities = calculate_total_liabilities(accounts, debts)
        net_worth = calculate_net_worth(total_assets, total_liabilities)

        # 2. Monthly Obligations & Protected Cash
        monthly_obligations, obligation_items = calculate_monthly_obligations(
            recurring_expenses=recurring_expenses,
            debts=debts,
        )

        protected_cash_breakdown = calculate_protected_cash(
            available_cash=available_cash,
            monthly_obligations=monthly_obligations,
            obligation_items=obligation_items,
            emergency_minimum=emergency_minimum,
            partner_percentage=partner_percentage,
            monthly_income=monthly_income,
            active_goals=active_goals,
        )

        # 3. Indicators & Health Assessment
        indicators = calculate_health_indicators(
            available_cash=available_cash,
            monthly_income=monthly_income,
            monthly_expenses=monthly_expenses,
            monthly_obligations=monthly_obligations,
            monthly_debt_payments=monthly_debt_payments,
            active_goals=active_goals,
        )

        health_score = evaluate_health_score(indicators, DEFAULT_HEALTH_WEIGHTS)
        health_status, explanations = evaluate_health_status(
            score=health_score,
            available_cash=available_cash,
            hard_obligations=monthly_obligations,
            indicators=indicators,
        )

        health_assessment = FinancialHealthAssessment(
            score=health_score,
            status=health_status,
            indicators=indicators,
            weights=DEFAULT_HEALTH_WEIGHTS,
            thresholds=HEALTH_THRESHOLDS,
            explanations=explanations,
        )

        return CanonicalFinancialState(
            available_cash=available_cash,
            total_assets=total_assets,
            total_liabilities=total_liabilities,
            net_worth=net_worth,
            monthly_income=round(monthly_income, 2),
            monthly_expenses=round(monthly_expenses, 2),
            monthly_obligations=monthly_obligations,
            protected_cash=protected_cash_breakdown.total_protected,
            free_cash=protected_cash_breakdown.free_cash,
            goal_progress=indicators.goal_progress,
            debt_pressure=indicators.debt_pressure,
            obligation_coverage=indicators.obligation_coverage,
            financial_health_score=health_score,
            financial_health_status=health_status,
            protected_cash_breakdown=protected_cash_breakdown,
            health_assessment=health_assessment,
            timestamp=timestamp,
        )
