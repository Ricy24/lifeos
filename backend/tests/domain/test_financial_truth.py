"""
LifeOS Finance — Deterministic Financial Truth Engine Tests
Verifies exact numerical calculations across all edge cases.
Zero probabilistic logic, 100% deterministic assertions.
"""

from datetime import datetime, timezone
import pytest

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
)
from app.domain.finance.truth_engine import FinancialTruthEngine


# ─── 1. Account Balances, Exclusions, and Net Worth ──────────────────────────

def test_normal_positive_balance_multiple_accounts():
    accounts = [
        {"account_type": "cash", "balance": 150000.0, "is_active": True, "include_in_total": True},
        {"account_type": "bank", "balance": 850000.0, "is_active": True, "include_in_total": True},
        {"account_type": "nequi", "balance": 200000.0, "is_active": True, "include_in_total": True},
    ]
    available = calculate_available_cash(accounts)
    assert available == 1200000.0

    assets = calculate_total_assets(accounts)
    assert assets == 1200000.0


def test_zero_balance_accounts():
    accounts = [
        {"account_type": "cash", "balance": 0.0, "is_active": True, "include_in_total": True},
        {"account_type": "bank", "balance": 0.0, "is_active": True, "include_in_total": True},
    ]
    available = calculate_available_cash(accounts)
    assets = calculate_total_assets(accounts)
    assert available == 0.0
    assert assets == 0.0


def test_negative_balance_and_liabilities():
    accounts = [
        {"account_type": "bank", "balance": -50000.0, "is_active": True, "include_in_total": True},
        {"account_type": "credit_card", "balance": -250000.0, "is_active": True, "include_in_total": True},
        {"account_type": "cash", "balance": 100000.0, "is_active": True, "include_in_total": True},
    ]
    debts = [
        {"debt_type": "i_owe", "remaining_amount": 300000.0, "status": "pending"},
        {"debt_type": "owed_to_me", "remaining_amount": 150000.0, "status": "pending"},
    ]
    # Available cash includes only positive liquid accounts
    available = calculate_available_cash(accounts)
    assert available == 100000.0

    # Total assets = 100000
    assets = calculate_total_assets(accounts)
    assert assets == 100000.0

    # Liabilities = 50000 (bank overdraft) + 250000 (credit card) + 300000 (i_owe debt) = 600000
    liabilities = calculate_total_liabilities(accounts, debts)
    assert liabilities == 600000.0

    # Net worth = 100000 - 600000 = -500000
    net_worth = calculate_net_worth(assets, liabilities)
    assert net_worth == -500000.0


def test_excluded_and_inactive_accounts():
    accounts = [
        {"account_type": "cash", "balance": 100000.0, "is_active": True, "include_in_total": True},
        {"account_type": "bank", "balance": 500000.0, "is_active": False, "include_in_total": True},  # Inactive
        {"account_type": "savings", "balance": 300000.0, "is_active": True, "include_in_total": False}, # Excluded from total
    ]
    available = calculate_available_cash(accounts)
    # Only active and included cash account
    assert available == 100000.0


# ─── 2. Monthly Obligations & Protected Cash ─────────────────────────────────

def test_recurring_obligations_and_debts():
    recurring = [
        {"id": "rec-1", "amount": 100000.0, "recurring_pattern": "monthly", "description": "Internet"},
        {"id": "rec-2", "amount": 10000.0, "recurring_pattern": "weekly", "description": "Transport"}, # 10000 * 4.33 = 43300
    ]
    debts = [
        {"id": "debt-1", "debt_type": "i_owe", "remaining_amount": 50000.0, "status": "pending", "priority": 1},
        {"id": "debt-2", "debt_type": "owed_to_me", "remaining_amount": 200000.0, "status": "pending", "priority": 1}, # ignored for my obligations
        {"id": "debt-3", "debt_type": "i_owe", "remaining_amount": 0.0, "status": "paid", "priority": 1}, # paid, ignored
    ]
    total_obligations, items = calculate_monthly_obligations(recurring, debts)
    # Internet (100,000) + Transport (43,300) + Debt-1 (50,000) = 193,300
    assert total_obligations == 193300.0
    assert len(items) == 3


def test_protected_cash_and_free_cash_traceability():
    available_cash = 500000.0
    monthly_obligations = 200000.0
    obligation_items = []
    emergency_minimum = 100000.0
    partner_percentage = 10.0  # 10% of 500,000 = 50,000
    active_goals = [
        {"id": "g1", "name": "Emergency Fund", "target_amount": 1000000.0, "current_amount": 200000.0, "category": "emergency", "priority": 1, "status": "active"}
    ] # Remainder 800,000; allocation min(800k, max(50k, 80k)) = 80,000

    breakdown = calculate_protected_cash(
        available_cash=available_cash,
        monthly_obligations=monthly_obligations,
        obligation_items=obligation_items,
        emergency_minimum=emergency_minimum,
        partner_percentage=partner_percentage,
        monthly_income=1000000.0,
        active_goals=active_goals,
    )

    # Protected = 200,000 (obligations) + 100,000 (emergency min) + 50,000 (partner) + 80,000 (goal) = 430,000
    assert breakdown.hard_obligations == 200000.0
    assert breakdown.emergency_minimum == 100000.0
    assert breakdown.protected_partner_money == 50000.0
    assert breakdown.protected_goal_money == 80000.0
    assert breakdown.total_protected == 430000.0
    # Free cash = 500,000 - 430,000 = 70,000
    assert breakdown.free_cash == 70000.0


def test_free_cash_floored_at_zero():
    # When protected money exceeds available cash
    available_cash = 100000.0
    monthly_obligations = 300000.0
    breakdown = calculate_protected_cash(
        available_cash=available_cash,
        monthly_obligations=monthly_obligations,
        obligation_items=[],
        emergency_minimum=0.0,
    )
    assert breakdown.total_protected == 300000.0
    assert breakdown.free_cash == 0.0


# ─── 3. Zero Division & Edge Case Safety ─────────────────────────────────────

def test_zero_income_and_zero_expenses_safe():
    indicators = calculate_health_indicators(
        available_cash=0.0,
        monthly_income=0.0,
        monthly_expenses=0.0,
        monthly_obligations=0.0,
        monthly_debt_payments=0.0,
        active_goals=None,
    )
    assert indicators.liquidity_months == 0.0
    assert indicators.obligation_coverage == 1.0
    assert indicators.debt_pressure == 0.0
    assert indicators.savings_rate == 0.0
    assert indicators.income_stability == 0.0
    assert indicators.goal_progress == 0.0

    score = evaluate_health_score(indicators)
    assert 0.0 <= score <= 100.0


def test_missing_optional_data():
    state = FinancialTruthEngine.compute_state(
        accounts=[],
        debts=[],
        recurring_expenses=[],
        monthly_income=0.0,
        monthly_expenses=0.0,
    )
    assert state.available_cash == 0.0
    assert state.total_assets == 0.0
    assert state.total_liabilities == 0.0
    assert state.net_worth == 0.0
    assert state.free_cash == 0.0


# ─── 4. Health States: RED, YELLOW, GREEN ────────────────────────────────────

def test_red_state_due_to_cash_deficit_for_hard_obligations():
    # Available cash 50,000 but hard obligations 100,000
    indicators = calculate_health_indicators(
        available_cash=50000.0,
        monthly_income=120000.0,
        monthly_expenses=90000.0,
        monthly_obligations=100000.0,
        monthly_debt_payments=0.0,
    )
    score = evaluate_health_score(indicators)
    status, explanations = evaluate_health_status(
        score=score,
        available_cash=50000.0,
        hard_obligations=100000.0,
        indicators=indicators,
    )
    assert status == "RED"
    assert any("insuficiente para cubrir las obligaciones" in exp for exp in explanations)


def test_green_state_healthy_finances():
    # 6 months liquidity, high coverage, zero debt pressure, 30% savings rate
    indicators = calculate_health_indicators(
        available_cash=3000000.0,
        monthly_income=2000000.0,
        monthly_expenses=1000000.0,
        monthly_obligations=800000.0,
        monthly_debt_payments=50000.0,
    )
    score = evaluate_health_score(indicators)
    assert score >= 70.0

    status, explanations = evaluate_health_status(
        score=score,
        available_cash=3000000.0,
        hard_obligations=800000.0,
        indicators=indicators,
    )
    assert status == "GREEN"
    assert any("Estado VERDE" in exp for exp in explanations)


def test_yellow_state_tight_margin():
    # Available cash covers obligations, but coverage is 1.05 and savings rate is low
    indicators = calculate_health_indicators(
        available_cash=500000.0,
        monthly_income=1050000.0,
        monthly_expenses=1000000.0,
        monthly_obligations=1000000.0,
        monthly_debt_payments=300000.0,
    )
    score = evaluate_health_score(indicators)
    status, explanations = evaluate_health_status(
        score=score,
        available_cash=500000.0,
        hard_obligations=500000.0,
        indicators=indicators,
    )
    assert status == "YELLOW"
    assert any("Estado AMARILLO" in exp for exp in explanations)


# ─── 5. Full Canonical State Computation ─────────────────────────────────────

def test_full_canonical_state_computation():
    accounts = [
        {"account_type": "bank", "balance": 1000000.0, "is_active": True, "include_in_total": True},
        {"account_type": "cash", "balance": 200000.0, "is_active": True, "include_in_total": True},
    ]
    debts = [
        {"id": "d1", "debt_type": "i_owe", "remaining_amount": 300000.0, "status": "pending", "priority": 2},
    ]
    recurring = [
        {"id": "r1", "amount": 150000.0, "recurring_pattern": "monthly", "description": "Arriendo parcial"},
    ]

    state = FinancialTruthEngine.compute_state(
        accounts=accounts,
        debts=debts,
        recurring_expenses=recurring,
        monthly_income=2500000.0,
        monthly_expenses=1200000.0,
        monthly_debt_payments=100000.0,
        emergency_minimum=200000.0,
        partner_percentage=10.0,
    )

    # 1. Available cash = 1,200,000
    assert state.available_cash == 1200000.0
    # 2. Total assets = 1,200,000
    assert state.total_assets == 1200000.0
    # 3. Total liabilities = 300,000
    assert state.total_liabilities == 300000.0
    # 4. Net worth = 900,000
    assert state.net_worth == 900000.0
    # 5. Monthly obligations = 150,000 + 300,000 = 450,000
    assert state.monthly_obligations == 450000.0
    # 6. Partner money = 10% of 1,200,000 = 120,000
    assert state.protected_cash_breakdown.protected_partner_money == 120000.0
    # 7. Total protected = 450,000 + 200,000 + 120,000 = 770,000
    assert state.protected_cash == 770000.0
    # 8. Free cash = 1,200,000 - 770,000 = 430,000
    assert state.free_cash == 430000.0
    # 9. Health status should be GREEN
    assert state.financial_health_status == "GREEN"
    assert state.financial_health_score >= 70.0
