"""
LifeOS Finance — Base Repository
Enforces tenant isolation by strictly scoping all database queries and mutations to user_id.
"""

from datetime import datetime, timezone
from typing import Any, Generic, List, Optional, Type, TypeVar
from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import Base

ModelT = TypeVar("ModelT", bound=Base)


class BaseRepository(Generic[ModelT]):
    """
    Tenant-scoped Base Repository.
    Every operation automatically enforces `user_id == self.user_id`
    and filters out soft-deleted records.
    """

    def __init__(self, model_class: Type[ModelT], db: AsyncSession, user_id: str):
        self.model_class = model_class
        self.db = db
        self.user_id = user_id

    def _base_query(self, include_deleted: bool = False):
        query = select(self.model_class)
        conditions = [getattr(self.model_class, "user_id") == self.user_id]
        if not include_deleted and hasattr(self.model_class, "deleted_at"):
            conditions.append(getattr(self.model_class, "deleted_at").is_(None))
        return query.where(and_(*conditions))

    async def get_by_id(self, id: str, include_deleted: bool = False) -> Optional[ModelT]:
        """Fetch a record by primary key, strictly restricted to this repository's user_id."""
        query = self._base_query(include_deleted=include_deleted).where(
            getattr(self.model_class, "id") == id
        )
        result = await self.db.execute(query)
        return result.scalar_one_or_none()

    async def list(
        self,
        offset: int = 0,
        limit: int = 100,
        include_deleted: bool = False,
    ) -> List[ModelT]:
        """List records belonging only to this user_id."""
        query = self._base_query(include_deleted=include_deleted).offset(offset).limit(limit)
        result = await self.db.execute(query)
        return list(result.scalars().all())

    async def count(self, include_deleted: bool = False) -> int:
        """Count records belonging to this user_id."""
        query = select(func.count(getattr(self.model_class, "id")))
        conditions = [getattr(self.model_class, "user_id") == self.user_id]
        if not include_deleted and hasattr(self.model_class, "deleted_at"):
            conditions.append(getattr(self.model_class, "deleted_at").is_(None))
        query = query.where(and_(*conditions))
        result = await self.db.execute(query)
        return result.scalar() or 0

    async def create(self, instance: ModelT) -> ModelT:
        """Create a new record, strictly attaching self.user_id."""
        setattr(instance, "user_id", self.user_id)
        self.db.add(instance)
        await self.db.commit()
        await self.db.refresh(instance)
        return instance

    async def update(self, id: str, **kwargs) -> Optional[ModelT]:
        """Update a record if and only if it belongs to self.user_id."""
        instance = await self.get_by_id(id)
        if not instance:
            return None

        # Disallow tampering with tenant isolation or primary key
        kwargs.pop("id", None)
        kwargs.pop("user_id", None)

        for key, value in kwargs.items():
            if hasattr(instance, key):
                setattr(instance, key, value)

        await self.db.commit()
        await self.db.refresh(instance)
        return instance

    async def soft_delete(self, id: str) -> bool:
        """Soft-delete a record if and only if it belongs to self.user_id."""
        instance = await self.get_by_id(id)
        if not instance:
            return False

        if hasattr(instance, "deleted_at"):
            setattr(instance, "deleted_at", datetime.now(timezone.utc))
            await self.db.commit()
            return True
        else:
            await self.db.delete(instance)
            await self.db.commit()
            return True

    async def hard_delete(self, id: str) -> bool:
        """Hard-delete a record if and only if it belongs to self.user_id."""
        instance = await self.get_by_id(id, include_deleted=True)
        if not instance:
            return False

        await self.db.delete(instance)
        await self.db.commit()
        return True
