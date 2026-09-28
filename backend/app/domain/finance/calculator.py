"""
LifeOS Finance — Pure Financial Calculators
100% deterministic, pure math functions with zero external dependencies.
All calculations are safe against division-by-zero and missing data.
"""

from typing import Any, Dict, List, Optional, Tuple
from app.domain.finance.state import (
    CanonicalFinancialState,
    FinancialHealthAssessment,
    FinancialHealthIndicators,
    ProtectedCashBreakdown,
    ProtectedCashItem,
)

# Standard Weights for Composite Financial Health Score (Must sum to 1.0)
DEFAULT_HEALTH_WEIGHTS: Dict[str, float] = {
    "liquidity": 0.25,
    "obligation_coverage": 0.25,
    "debt_pressure": 0.20,
    "savings_rate": 0.15,
    "income_stability": 0.10,
    "goal_progress": 0.05,
}

# Standard Reference Thresholds
HEALTH_THRESHOLDS = {
    "min_liquidity_months_target": 3.0,     # 3 months of expenses is considered optimal
    "min_obligation_coverage_green": 1.20,  # Income must exceed obligations by at least 20%
    "max_debt_pressure_green": 0.35,        # Debt payments should not exceed 35% of income
    "target_savings_rate_green": 0.20,      # 20% savings rate is optimal
    "score_green_cutoff": 70.0,
    "score_yellow_cutoff": 45.0,
}


def calculate_available_cash(accounts: List[Dict[str, Any]]) -> float:
    """
    Calculate liquid available cash.
    Includes active accounts where include_in_total is True and type is liquid
    (cash, bank, nequi, daviplata, savings). Excludes credit cards and investments.
    """
    liquid_types = {"cash", "bank", "nequi", "daviplata", "savings"}
    total = 0.0
    for acc in accounts:
        if not acc.get("is_active", True):
            continue
        if not acc.get("include_in_total", True):
            continue
        acc_type = str(acc.get("account_type", "cash")).lower()
        if acc_type in liquid_types:
            bal = float(acc.get("balance", 0.0) or 0.0)
            if bal > 0:
                total += bal
    return round(total, 2)


def calculate_total_assets(accounts: List[Dict[str, Any]]) -> float:
    """
    Calculate total assets across all active positive accounts
    (cash, bank, investments, savings, etc.).
    """
    total = 0.0
    for acc in accounts:
        if not acc.get("is_active", True):
            continue
        balance = float(acc.get("balance", 0.0) or 0.0)
        if balance > 0:
            total += balance
    return round(total, 2)


def calculate_total_liabilities(
    accounts: List[Dict[str, Any]],
    debts: List[Dict[str, Any]],
) -> float:
    """
    Calculate total liabilities:
    Sum of negative balances (e.g. credit cards or overdrafts) +
    sum of remaining debt amounts for active 'i_owe' debts.
    """
    total = 0.0
    # Negative account balances
    for acc in accounts:
        if not acc.get("is_active", True):
            continue
        balance = float(acc.get("balance", 0.0) or 0.0)
        if balance < 0:
            total += abs(balance)

    # Debts owed to others
    for d in debts:
        status = str(d.get("status", "pending")).lower()
        debt_type = str(d.get("debt_type", "i_owe")).lower()
        if status in {"pending", "partially_paid", "overdue"} and debt_type == "i_owe":
            total += float(d.get("remaining_amount", 0.0) or 0.0)

    return round(total, 2)


def calculate_net_worth(total_assets: float, total_liabilities: float) -> float:
    """Net worth = Assets - Liabilities. Can be negative."""
    return round(total_assets - total_liabilities, 2)


def calculate_monthly_obligations(
    recurring_expenses: List[Dict[str, Any]],
    debts: List[Dict[str, Any]],
) -> Tuple[float, List[ProtectedCashItem]]:
    """
    Calculate monthly fixed obligations and return traceable items:
    1. Recurring monthly expenses (housing, utilities, subscriptions, bills).
    2. Debt obligations (remaining amounts of debts due or overdue, or monthly payment requirements).
    """
    total = 0.0
    items: List[ProtectedCashItem] = []

    # 1. Recurring expenses
    for r in recurring_expenses:
        amount = float(r.get("amount", 0.0) or 0.0)
        if amount <= 0:
            continue
        pattern = str(r.get("recurring_pattern", "monthly")).lower()
        # Normalize to monthly
        monthly_amount = amount
        if pattern == "daily":
            monthly_amount = amount * 30
        elif pattern == "weekly":
            monthly_amount = amount * 4.33
        elif pattern == "yearly":
            monthly_amount = amount / 12.0

        total += monthly_amount
        items.append(
            ProtectedCashItem(
                category="hard_obligation",
                source_id=str(r.get("id", "")),
                name=str(r.get("description") or r.get("category") or "Gasto recurrente"),
                amount=round(monthly_amount, 2),
                is_hard_constraint=True,
                notes=f"Recurrente ({pattern})",
            )
        )

    # 2. Debts with status pending/partially_paid/overdue that are I_OWE
    for d in debts:
        status = str(d.get("status", "pending")).lower()
        debt_type = str(d.get("debt_type", "i_owe")).lower()
        if status in {"pending", "partially_paid", "overdue"} and debt_type == "i_owe":
            rem = float(d.get("remaining_amount", 0.0) or 0.0)
            if rem > 0:
                # If priority is high (1 or 2) or overdue, it's a hard obligation
                is_urgent = status == "overdue" or int(d.get("priority", 3)) <= 2
                total += rem
                items.append(
                    ProtectedCashItem(
                        category="hard_obligation",
                        source_id=str(d.get("id", "")),
                        name=f"Deuda: {d.get('person_or_entity', 'Acreedor')}",
                        amount=round(rem, 2),
                        is_hard_constraint=is_urgent,
                        notes=f"Estado: {status}, Prioridad: {d.get('priority', 3)}",
                    )
                )

    return round(total, 2), items


def calculate_protected_cash(
    available_cash: float,
    monthly_obligations: float,
    obligation_items: List[ProtectedCashItem],
    emergency_minimum: float = 0.0,
    partner_percentage: float = 0.0,
    monthly_income: float = 0.0,
    active_goals: Optional[List[Dict[str, Any]]] = None,
) -> ProtectedCashBreakdown:
    """
    Calculate protected cash with complete source traceability.
    Distinguishes:
    - hard_obligations (debts, bills)
    - emergency_minimum (defined safety floor)
    - protected_partner_money (configured % from actual available cash or income)
    - protected_goal_money (emergency fund or high priority goal allocations)
    """
    items = list(obligation_items)

    # Emergency minimum floor
    emergency_prot = max(0.0, float(emergency_minimum or 0.0))
    if emergency_prot > 0:
        items.append(
            ProtectedCashItem(
                category="emergency_minimum",
                source_id=None,
                name="Fondo de Emergencia Mínimo",
                amount=round(emergency_prot, 2),
                is_hard_constraint=True,
                notes="Colchón de seguridad obligatorio",
            )
        )

    # Partner money allocation (% of available cash if positive)
    partner_prot = 0.0
    if partner_percentage > 0 and available_cash > 0:
        partner_prot = round(available_cash * (partner_percentage / 100.0), 2)
        items.append(
            ProtectedCashItem(
                category="partner_money",
                source_id=None,
                name=f"Asignación Pareja ({partner_percentage}%)",
                amount=partner_prot,
                is_hard_constraint=False,
                notes="Presupuesto asignado a planes en pareja",
            )
        )

    # Protected goal allocations (emergency or top priority goals)
    goal_prot = 0.0
    if active_goals:
        for g in active_goals:
            status = str(g.get("status", "active")).lower()
            if status != "active":
                continue
            cat = str(g.get("category", "purchase")).lower()
            rem = max(0.0, float(g.get("target_amount", 0.0)) - float(g.get("current_amount", 0.0)))
            if rem <= 0:
                continue
            # If emergency goal or priority 1, protect a monthly allocation
            priority = int(g.get("priority", 3))
            if cat == "emergency" or priority == 1:
                # Allocate reasonable chunk (e.g. 10% of remaining or up to remaining)
                alloc = round(min(rem, max(50000.0, rem * 0.1)), 2)
                goal_prot += alloc
                items.append(
                    ProtectedCashItem(
                        category="goal_allocation",
                        source_id=str(g.get("id", "")),
                        name=f"Meta: {g.get('name', 'Ahorro prioritario')}",
                        amount=alloc,
                        is_hard_constraint=(cat == "emergency"),
                        notes=f"Categoría: {cat}, Prioridad: {priority}",
                    )
                )

    total_protected = round(monthly_obligations + emergency_prot + partner_prot + goal_prot, 2)
    free_cash = round(max(0.0, available_cash - total_protected), 2)

    return ProtectedCashBreakdown(
        available_cash=round(available_cash, 2),
        hard_obligations=round(monthly_obligations, 2),
        emergency_minimum=emergency_prot,
        protected_partner_money=partner_prot,
        protected_goal_money=round(goal_prot, 2),
        total_protected=total_protected,
        free_cash=free_cash,
        items=items,
    )


def calculate_health_indicators(
    available_cash: float,
    monthly_income: float,
    monthly_expenses: float,
    monthly_obligations: float,
    monthly_debt_payments: float,
    active_goals: Optional[List[Dict[str, Any]]] = None,
) -> FinancialHealthIndicators:
    """
    Pure indicator calculation. Handles zero division and edge cases safely.
    """
    # 1. Liquidity in months: how long available cash lasts with current expenses
    # Fallback to monthly obligations if expenses are 0
    burn_rate = monthly_expenses if monthly_expenses > 0 else (monthly_obligations if monthly_obligations > 0 else 1.0)
    liquidity_months = max(0.0, available_cash / burn_rate)

    # 2. Obligation coverage ratio: monthly income vs obligations
    if monthly_obligations > 0:
        obligation_coverage = max(0.0, monthly_income / monthly_obligations)
    else:
        obligation_coverage = 2.0 if monthly_income > 0 else 1.0

    # 3. Debt pressure ratio: debt burden / monthly income
    if monthly_income > 0:
        debt_pressure = min(2.0, max(0.0, monthly_debt_payments / monthly_income))
    else:
        debt_pressure = 1.0 if monthly_debt_payments > 0 else 0.0

    # 4. Savings rate: (income - expenses) / income
    if monthly_income > 0:
        savings_rate = max(-1.0, min(1.0, (monthly_income - monthly_expenses) / monthly_income))
    else:
        savings_rate = 0.0

    # 5. Income stability (baseline heuristic: 1.0 if income covers expenses, drops if income is zero or volatile)
    if monthly_income <= 0:
        income_stability = 0.0
    elif monthly_income >= monthly_obligations:
        income_stability = 1.0
    else:
        income_stability = max(0.1, monthly_income / (monthly_obligations or 1.0))

    # 6. Goal progress (average progress of active goals)
    if active_goals:
        active_list = [g for g in active_goals if str(g.get("status", "active")).lower() == "active"]
        if active_list:
            total_prog = 0.0
            for g in active_list:
                target = float(g.get("target_amount", 0.0) or 0.0)
                current = float(g.get("current_amount", 0.0) or 0.0)
                if target > 0:
                    total_prog += min(1.0, current / target)
                else:
                    total_prog += 1.0
            goal_progress = total_prog / len(active_list)
        else:
            goal_progress = 0.0
    else:
        goal_progress = 0.0

    return FinancialHealthIndicators(
        liquidity_months=liquidity_months,
        obligation_coverage=obligation_coverage,
        debt_pressure=debt_pressure,
        savings_rate=savings_rate,
        income_stability=income_stability,
        goal_progress=goal_progress,
    )


def evaluate_health_score(
    indicators: FinancialHealthIndicators,
    weights: Optional[Dict[str, float]] = None,
) -> float:
    """
    Calculate composite health score in [0.0, 100.0].
    Each component is scored from 0 to 100, then weighted.
    """
    w = weights or DEFAULT_HEALTH_WEIGHTS

    # 1. Liquidity score: 3+ months = 100
    liquidity_score = min(100.0, (indicators.liquidity_months / 3.0) * 100.0)

    # 2. Obligation coverage: 1.5+ = 100, 1.0 = 50, <0.8 = 0
    if indicators.obligation_coverage >= 1.5:
        cov_score = 100.0
    elif indicators.obligation_coverage >= 1.0:
        cov_score = 50.0 + (indicators.obligation_coverage - 1.0) * 100.0
    else:
        cov_score = max(0.0, indicators.obligation_coverage * 50.0)

    # 3. Debt pressure score: 0% debt = 100, 35% = 50, >70% = 0
    if indicators.debt_pressure <= 0.10:
        debt_score = 100.0
    elif indicators.debt_pressure <= 0.35:
        debt_score = 100.0 - ((indicators.debt_pressure - 0.10) / 0.25) * 50.0
    else:
        debt_score = max(0.0, 50.0 - ((indicators.debt_pressure - 0.35) / 0.35) * 50.0)

    # 4. Savings rate score: 20%+ = 100, 0% = 30, negative = 0
    if indicators.savings_rate >= 0.20:
        savings_score = 100.0
    elif indicators.savings_rate > 0:
        savings_score = 30.0 + (indicators.savings_rate / 0.20) * 70.0
    else:
        savings_score = max(0.0, 30.0 + indicators.savings_rate * 30.0)

    # 5. Income stability score: 0.0 - 1.0 -> 0 - 100
    stability_score = min(100.0, max(0.0, indicators.income_stability * 100.0))

    # 6. Goal progress score: 0.0 - 1.0 -> 0 - 100
    goal_score = min(100.0, max(0.0, indicators.goal_progress * 100.0))

    total = (
        liquidity_score * w.get("liquidity", 0.25)
        + cov_score * w.get("obligation_coverage", 0.25)
        + debt_score * w.get("debt_pressure", 0.20)
        + savings_score * w.get("savings_rate", 0.15)
        + stability_score * w.get("income_stability", 0.10)
        + goal_score * w.get("goal_progress", 0.05)
    )
    return round(min(100.0, max(0.0, total)), 1)


def evaluate_health_status(
    score: float,
    available_cash: float,
    hard_obligations: float,
    indicators: FinancialHealthIndicators,
) -> Tuple[str, List[str]]:
    """
    Determine RED / YELLOW / GREEN status with explicit human-readable rationales.
    
    Hard Rules:
    - RED: If available cash cannot cover upcoming hard obligations, or if score < 45.
    - GREEN: Score >= 70 AND obligation_coverage >= 1.2 AND available cash >= hard obligations.
    - YELLOW: Everything else.
    """
    explanations: List[str] = []

    # Check hard constraint: Available cash vs upcoming obligations
    if hard_obligations > 0 and available_cash < hard_obligations:
        deficit = hard_obligations - available_cash
        explanations.append(
            f"El efectivo disponible (${available_cash:,.0f}) es insuficiente para cubrir las obligaciones duras inmediatas (${hard_obligations:,.0f}). Déficit: ${deficit:,.0f}."
        )
        return "RED", explanations

    # Obligation coverage explanation
    if indicators.obligation_coverage < 1.0:
        explanations.append(
            f"Los ingresos del mes no cubren las obligaciones mensuales (cobertura: {indicators.obligation_coverage:.2f}x)."
        )
    elif indicators.obligation_coverage < 1.2:
        explanations.append(
            f"La cobertura de obligaciones es ajustada ({indicators.obligation_coverage:.2f}x), dejando poco margen de maniobra."
        )
    else:
        explanations.append(
            f"Excelente cobertura de obligaciones ({indicators.obligation_coverage:.2f}x)."
        )

    # Debt pressure explanation
    if indicators.debt_pressure > 0.40:
        explanations.append(
            f"La presión de deuda es elevada ({indicators.debt_pressure * 100:.1f}% de los ingresos)."
        )

    # Liquidity explanation
    if indicators.liquidity_months < 1.0:
        explanations.append(
            f"La liquidez disponible cubre menos de un mes de gastos ({indicators.liquidity_months:.1f} meses)."
        )
    elif indicators.liquidity_months >= 3.0:
        explanations.append(
            f"Colchón de liquidez saludable ({indicators.liquidity_months:.1f} meses de reserva)."
        )

    # Final status determination
    if indicators.obligation_coverage < 0.8 or score < HEALTH_THRESHOLDS["score_yellow_cutoff"]:
        status = "RED"
        explanations.append("Estado ROJO: Riesgo financiero alto debido a déficit de flujo o liquidez crítica.")
    elif (
        score >= HEALTH_THRESHOLDS["score_green_cutoff"]
        and indicators.obligation_coverage >= HEALTH_THRESHOLDS["min_obligation_coverage_green"]
        and indicators.debt_pressure <= HEALTH_THRESHOLDS["max_debt_pressure_green"]
    ):
        status = "GREEN"
        explanations.append("Estado VERDE: Finanzas estables, obligaciones cubiertas y margen de ahorro positivo.")
    else:
        status = "YELLOW"
        explanations.append("Estado AMARILLO: Obligaciones cubiertas pero margen de seguridad reducido o endeudamiento a vigilar.")

    return status, explanations
