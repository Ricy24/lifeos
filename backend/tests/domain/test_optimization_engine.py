import pytest
from app.domain.optimization.models import OptimizationStatus
from app.domain.optimization.engine import OptimizationEngine

def test_infeasible_does_not_fallback():
    debts = [
        {"id": "d1", "balance": 1000000, "interest_rate": 0.01, "minimum_payment": 200000},
        {"id": "d2", "balance": 500000, "interest_rate": 0.02, "minimum_payment": 100000}
    ]
    # Available cash (250k) is less than min payments (300k)
    res = OptimizationEngine.optimize_debt(debts, available_cash=250000)
    
    assert res.status == OptimizationStatus.INFEASIBLE
    assert res.is_fallback is False
