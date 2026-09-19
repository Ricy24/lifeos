"""
LifeOS Finance — Debt Repository
"""

from typing import List
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.debt import Debt, DebtStatus
from app.repositories.base import BaseRepository


class DebtRepository(BaseRepository[Debt]):
    def __init__(self, db: AsyncSession, user_id: str):
        super().__init__(Debt, db, user_id)

    async def get_pending_debts(self) -> List[Debt]:
        """Fetch pending and overdue debts belonging to this user."""
        query = (
            self._base_query()
            .where(Debt.status.in_([DebtStatus.PENDING.value, DebtStatus.OVERDUE.value]))
            .order_by(Debt.priority.asc())
        )
        result = await self.db.execute(query)
        return list(result.scalars().all())
