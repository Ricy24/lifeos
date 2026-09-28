from dataclasses import dataclass
from typing import List, Dict, Any, Optional
from enum import Enum

class OptimizationStatus(Enum):
    OPTIMAL = "OPTIMAL"
    FEASIBLE = "FEASIBLE"
    FEASIBLE_PARTIAL = "FEASIBLE_PARTIAL"
    INFEASIBLE = "INFEASIBLE"
    INVALID_INPUT = "INVALID_INPUT"
    TIME_LIMIT = "TIME_LIMIT"
    SOLVER_ERROR = "SOLVER_ERROR"
    FALLBACK = "FALLBACK"

@dataclass(frozen=True)
class Allocation:
    month_index: int
    target_id: str
    payment_amount: int
    is_extra_payment: bool

@dataclass(frozen=True)
class OptimizationResult:
    status: OptimizationStatus
    strategy_used: str
    is_fallback: bool
    optimality_guaranteed: bool
    planning_horizon_months: int
    total_interest: int
    months_to_payoff: int
    total_paid: int
    allocations: List[Allocation]
