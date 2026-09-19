"""
LifeOS Finance — Account Repository
"""

from typing import List, Optional
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.account import Account
from app.repositories.base import BaseRepository


class AccountRepository(BaseRepository[Account]):
    def __init__(self, db: AsyncSession, user_id: str):
        super().__init__(Account, db, user_id)

    async def get_active_accounts(self) -> List[Account]:
        """List active, non-deleted accounts for this user."""
        query = self._base_query().where(Account.is_active.is_(True))
        result = await self.db.execute(query)
        return list(result.scalars().all())

    async def get_by_name(self, name: str) -> Optional[Account]:
        """Find an account by exact name for this user."""
        query = self._base_query().where(Account.name == name)
        result = await self.db.execute(query)
        return result.scalar_one_or_none()
