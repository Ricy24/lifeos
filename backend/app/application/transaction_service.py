from typing import Optional
from dataclasses import dataclass
from datetime import datetime

@dataclass
class CreateIncomeCommand:
    user_id: str
    account_id: str
    amount_minor: int
    currency: str
    description: Optional[str]
    occurred_at: Optional[datetime]
    idempotency_key: Optional[str]

@dataclass
class CreateExpenseCommand:
    user_id: str
    account_id: str
    amount_minor: int
    currency: str
    category: Optional[str]
    description: Optional[str]
    occurred_at: Optional[datetime]
    idempotency_key: Optional[str]

class TransactionService:
    def __init__(self):
        self._db = []
        self._idempotency_cache = set()

    def create_income(self, cmd: CreateIncomeCommand):
        self._validate_common(cmd)
        if cmd.idempotency_key:
            cache_key = f"{cmd.user_id}:{cmd.idempotency_key}"
            if cache_key in self._idempotency_cache:
                return {"status": "idempotent_skipped"}
            self._idempotency_cache.add(cache_key)
        
        tx = {"type": "INCOME", **cmd.__dict__}
        self._db.append(tx)
        return {"status": "success", "transaction": tx}

    def create_expense(self, cmd: CreateExpenseCommand):
        self._validate_common(cmd)
        if cmd.idempotency_key:
            cache_key = f"{cmd.user_id}:{cmd.idempotency_key}"
            if cache_key in self._idempotency_cache:
                return {"status": "idempotent_skipped"}
            self._idempotency_cache.add(cache_key)
            
        tx = {"type": "EXPENSE", **cmd.__dict__}
        self._db.append(tx)
        return {"status": "success", "transaction": tx}
        
    def _validate_common(self, cmd):
        if cmd.amount_minor <= 0:
            raise ValueError("Amount must be positive")
        if not cmd.account_id.startswith("user_" + cmd.user_id):
            raise PermissionError("Account belongs to another user")

transaction_service = TransactionService()
