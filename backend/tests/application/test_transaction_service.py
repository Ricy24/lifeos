import pytest
from app.application.transaction_service import transaction_service, CreateIncomeCommand, CreateExpenseCommand

def test_create_income_success():
    cmd = CreateIncomeCommand("A", "user_A_nequi", 3000, "COP", "test", None, None)
    res = transaction_service.create_income(cmd)
    assert res["status"] == "success"
    assert res["transaction"]["amount_minor"] == 3000

def test_negative_amount_rejection():
    cmd = CreateIncomeCommand("A", "user_A_nequi", -100, "COP", "test", None, None)
    with pytest.raises(ValueError):
        transaction_service.create_income(cmd)

def test_cross_user_account_rejection():
    cmd = CreateExpenseCommand("A", "user_B_nequi", 15000, "COP", "food", "lunch", None, None)
    with pytest.raises(PermissionError):
        transaction_service.create_expense(cmd)

def test_idempotency_skipped():
    cmd1 = CreateIncomeCommand("A", "user_A_nequi", 3000, "COP", "test", None, "idem_1")
    res1 = transaction_service.create_income(cmd1)
    res2 = transaction_service.create_income(cmd1)
    
    assert res1["status"] == "success"
    assert res2["status"] == "idempotent_skipped"
