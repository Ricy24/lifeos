"""
LifeOS Finance — Goal Repository
"""

from typing import List
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.goal import Goal, GoalStatus
from app.repositories.base import BaseRepository


class GoalRepository(BaseRepository[Goal]):
    def __init__(self, db: AsyncSession, user_id: str):
        super().__init__(Goal, db, user_id)

    async def get_active_goals(self) -> List[Goal]:
        """Fetch active goals belonging to this user."""
        query = (
            self._base_query()
            .where(Goal.status == GoalStatus.ACTIVE.value)
            .order_by(Goal.priority.asc())
        )
        result = await self.db.execute(query)
        return list(result.scalars().all())
