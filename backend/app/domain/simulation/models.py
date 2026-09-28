from dataclasses import dataclass
from typing import List, Dict, Any, Optional
from enum import Enum
from app.domain.finance.state import CanonicalFinancialState

class SimulationEventType(Enum):
    EXPENSE = "expense"
    INCOME = "income"

@dataclass(frozen=True)
class SimulationEvent:
    event_type: SimulationEventType
    amount: int
    account_id: str
    description: Optional[str] = None

@dataclass(frozen=True)
class SimulationResult:
    baseline_state: CanonicalFinancialState
    simulated_state: CanonicalFinancialState
    delta_free_cash: int
    delta_available_cash: int
    health_transition: str
    is_safe: bool
    negative_balance_detected: bool
    protected_cash_violation: bool
    negative_free_cash: bool
    warnings: List[str]
