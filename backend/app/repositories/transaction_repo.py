"""
LifeOS Finance — Transaction Repository
"""

from datetime import datetime
from typing import List, Optional
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.transaction import Transaction, TransactionType
from app.repositories.base import BaseRepository


class TransactionRepository(BaseRepository[Transaction]):
    def __init__(self, db: AsyncSession, user_id: str):
        super().__init__(Transaction, db, user_id)

    async def get_by_account(
        self,
        account_id: str,
        limit: int = 50,
    ) -> List[Transaction]:
        """Fetch transactions for a specific account belonging to this user."""
        query = (
            self._base_query()
            .where(Transaction.account_id == account_id)
            .order_by(Transaction.transaction_date.desc())
            .limit(limit)
        )
        result = await self.db.execute(query)
        return list(result.scalars().all())

    async def get_recent(self, limit: int = 20) -> List[Transaction]:
        """Fetch recent transactions belonging to this user."""
        query = (
            self._base_query()
            .order_by(Transaction.transaction_date.desc())
            .limit(limit)
        )
        result = await self.db.execute(query)
        return list(result.scalars().all())
