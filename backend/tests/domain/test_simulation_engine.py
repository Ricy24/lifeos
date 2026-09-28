import pytest
from app.domain.simulation.models import SimulationEvent, SimulationEventType
from app.domain.simulation.engine import SimulationEngine

@pytest.fixture
def base_inputs():
    return {
        "accounts": [
            {"id": "acc1", "balance": 1000000.0, "type": "checking", "exclude_from_totals": False, "is_active": True}
        ],
        "debts": [],
        "recurring_expenses": [{"amount": 200000.0, "is_hard_obligation": True, "name": "Rent"}],
        "monthly_income": 3000000.0,
        "monthly_expenses": 1000000.0,
        "emergency_minimum": 100000.0
    }

def test_instant_expense_simulation(base_inputs):
    events = [SimulationEvent(SimulationEventType.EXPENSE, 150000, "acc1")]
    res = SimulationEngine.simulate(events, **base_inputs)
    
    # 1M - 200k(rent) - 100k(emerg) = 700k baseline free cash
    # simulated 1M - 150k = 850k available
    # 850k - 200k - 100k = 550k simulated free cash
    assert res.delta_free_cash == -150000
    assert res.delta_available_cash == -150000
    assert res.is_safe is True
    assert res.negative_free_cash is False
    assert res.protected_cash_violation is False
    assert res.baseline_state.available_cash == 1000000.0 # immutability check
    
def test_insufficient_balance_rejection(base_inputs):
    # Expense larger than free cash but less than available cash
    events = [SimulationEvent(SimulationEventType.EXPENSE, 800000, "acc1")]
    res = SimulationEngine.simulate(events, **base_inputs)
    
    # 1M available - 800k = 200k available.
    # 200k available - 300k protected = 0k free cash (floored).
    assert res.negative_free_cash is False
    assert res.protected_cash_violation is True
    assert res.is_safe is False
    assert "PROTECTED_CASH_VIOLATION" in res.warnings
    
def test_multiple_events(base_inputs):
    events = [
        SimulationEvent(SimulationEventType.EXPENSE, 50000, "acc1"),
        SimulationEvent(SimulationEventType.INCOME, 200000, "acc1")
    ]
    res = SimulationEngine.simulate(events, **base_inputs)
    assert res.delta_available_cash == 150000
    assert res.delta_free_cash == 150000
    
def test_repeated_determinism(base_inputs):
    events = [SimulationEvent(SimulationEventType.EXPENSE, 100000, "acc1")]
    res1 = SimulationEngine.simulate(events, **base_inputs)
    res2 = SimulationEngine.simulate(events, **base_inputs)
    assert res1.delta_free_cash == res2.delta_free_cash
    assert res1.baseline_state.available_cash == res2.baseline_state.available_cash
