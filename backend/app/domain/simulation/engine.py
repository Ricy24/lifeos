import copy
from typing import List, Dict, Any, Optional
from app.domain.finance.truth_engine import FinancialTruthEngine
from app.domain.finance.state import CanonicalFinancialState
from .models import SimulationEvent, SimulationEventType, SimulationResult

class SimulationEngine:
    @classmethod
    def simulate(
        cls,
        events: List[SimulationEvent],
        accounts: List[Dict[str, Any]],
        debts: List[Dict[str, Any]],
        recurring_expenses: List[Dict[str, Any]],
        monthly_income: float,
        monthly_expenses: float,
        monthly_debt_payments: float = 0.0,
        emergency_minimum: float = 0.0,
        partner_percentage: float = 0.0,
        active_goals: Optional[List[Dict[str, Any]]] = None,
    ) -> SimulationResult:
        
        # 1. Compute Baseline
        baseline_state = FinancialTruthEngine.compute_state(
            accounts=accounts,
            debts=debts,
            recurring_expenses=recurring_expenses,
            monthly_income=monthly_income,
            monthly_expenses=monthly_expenses,
            monthly_debt_payments=monthly_debt_payments,
            emergency_minimum=emergency_minimum,
            partner_percentage=partner_percentage,
            active_goals=active_goals,
        )
        
        # 2. Deep Clone Inputs
        sim_accounts = copy.deepcopy(accounts)
        
        warnings = []
        
        # 3. Apply Events
        for event in events:
            if event.amount < 0:
                warnings.append(f"Rejected event with negative amount: {event.amount}")
                continue
                
            # Find account
            account = next((a for a in sim_accounts if str(a.get("id")) == str(event.account_id)), None)
            if not account:
                warnings.append(f"Account {event.account_id} not found for event.")
                continue
                
            if event.event_type == SimulationEventType.EXPENSE:
                account["balance"] -= event.amount
            elif event.event_type == SimulationEventType.INCOME:
                account["balance"] += event.amount
                
        # 4. Compute Simulated State
        simulated_state = FinancialTruthEngine.compute_state(
            accounts=sim_accounts,
            debts=debts,
            recurring_expenses=recurring_expenses,
            monthly_income=monthly_income,
            monthly_expenses=monthly_expenses, # Do NOT change historic monthly expenses
            monthly_debt_payments=monthly_debt_payments,
            emergency_minimum=emergency_minimum,
            partner_percentage=partner_percentage,
            active_goals=active_goals,
        )
        
        # 5. Delta Analysis
        delta_free_cash = int(simulated_state.free_cash - baseline_state.free_cash)
        delta_available_cash = int(simulated_state.available_cash - baseline_state.available_cash)
        health_transition = f"{baseline_state.financial_health_status} -> {simulated_state.financial_health_status}"
        
        # 6. Safety Analysis
        negative_balance_detected = simulated_state.available_cash < 0
        
        # A protected cash violation happens if available cash is less than the protected cash requirement.
        # This is exactly what causes free_cash to be negative.
        negative_free_cash = simulated_state.free_cash < 0
        protected_cash_violation = simulated_state.available_cash < simulated_state.protected_cash_breakdown.total_protected
        
        is_safe = True
        
        if negative_balance_detected:
            is_safe = False
            warnings.append("NEGATIVE_BALANCE")
            
        if negative_free_cash:
            warnings.append("NEGATIVE_FREE_CASH")
            
        if protected_cash_violation:
            is_safe = False
            warnings.append("PROTECTED_CASH_VIOLATION")
            
        if simulated_state.financial_health_status == "RED":
            is_safe = False
            warnings.append("HEALTH_RED")
            
        return SimulationResult(
            baseline_state=baseline_state,
            simulated_state=simulated_state,
            delta_free_cash=delta_free_cash,
            delta_available_cash=delta_available_cash,
            health_transition=health_transition,
            is_safe=is_safe,
            negative_balance_detected=negative_balance_detected,
            protected_cash_violation=protected_cash_violation,
            negative_free_cash=negative_free_cash,
            warnings=warnings
        )
