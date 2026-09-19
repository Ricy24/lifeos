"""
Tests for Versioning and Optimistic Locking (AuditMixin).
Verifies:
1. version starts at 1
2. version increments on UPDATE
3. version increments on soft delete (setting deleted_at via ORM)
4. Optimistic locking detects stale updates
"""

import uuid
from datetime import datetime, timezone
import pytest
import pytest_asyncio
from sqlalchemy import select
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm.exc import StaleDataError

from app.core.database import Base
from app.models import (
    User,
    Account,
    AccountType,
    Transaction,
    TransactionType,
    Debt,
    DebtType,
    Goal,
    GoalCategory,
)

TEST_DB_URL = "postgresql+asyncpg://postgres:Riki1049616429@localhost:5432/lifeos_test"


@pytest_asyncio.fixture
async def test_engine():
    engine = create_async_engine(TEST_DB_URL, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    await engine.dispose()


@pytest_asyncio.fixture
async def db_session(test_engine):
    session_factory = async_sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)
    async with session_factory() as session:
        yield session
        await session.rollback()


@pytest.mark.asyncio
async def test_user_version_on_update_and_soft_delete(db_session: AsyncSession):
    user = User(
        id=str(uuid.uuid4()),
        email=f"version_user_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password="hashed_secret",
        full_name="Version Test User",
    )
    db_session.add(user)
    await db_session.commit()
    assert user.version == 1

    # Update field
    user.full_name = "Version Test User Updated"
    await db_session.commit()
    assert user.version == 2

    # Soft delete
    user.deleted_at = datetime.now(timezone.utc)
    await db_session.commit()
    assert user.version == 3


@pytest.mark.asyncio
async def test_account_version_on_update_and_soft_delete(db_session: AsyncSession):
    user = User(
        id=str(uuid.uuid4()),
        email=f"account_user_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password="hash",
    )
    db_session.add(user)
    await db_session.commit()

    account = Account(
        id=str(uuid.uuid4()),
        user_id=user.id,
        name="Main Checking",
        account_type=AccountType.BANK,
        balance=1500000.0,
    )
    db_session.add(account)
    await db_session.commit()
    assert account.version == 1

    # Update
    account.balance = 2000000.0
    await db_session.commit()
    assert account.version == 2

    # Soft delete
    account.deleted_at = datetime.now(timezone.utc)
    await db_session.commit()
    assert account.version == 3


@pytest.mark.asyncio
async def test_transaction_version_on_update_and_soft_delete(db_session: AsyncSession):
    user = User(
        id=str(uuid.uuid4()),
        email=f"tx_user_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password="hash",
    )
    db_session.add(user)
    await db_session.commit()

    tx = Transaction(
        id=str(uuid.uuid4()),
        user_id=user.id,
        amount=50000.0,
        transaction_type=TransactionType.EXPENSE,
        category="food",
        description="Lunch",
    )
    db_session.add(tx)
    await db_session.commit()
    assert tx.version == 1

    tx.description = "Dinner"
    await db_session.commit()
    assert tx.version == 2

    tx.deleted_at = datetime.now(timezone.utc)
    await db_session.commit()
    assert tx.version == 3


@pytest.mark.asyncio
async def test_debt_and_goal_version_on_update_and_soft_delete(db_session: AsyncSession):
    user = User(
        id=str(uuid.uuid4()),
        email=f"debt_user_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password="hash",
    )
    db_session.add(user)
    await db_session.commit()

    debt = Debt(
        id=str(uuid.uuid4()),
        user_id=user.id,
        person_or_entity="Bank",
        debt_type=DebtType.I_OWE,
        original_amount=1000000.0,
        remaining_amount=1000000.0,
    )
    goal = Goal(
        id=str(uuid.uuid4()),
        user_id=user.id,
        name="Emergency Fund",
        target_amount=5000000.0,
        category=GoalCategory.EMERGENCY,
    )
    db_session.add_all([debt, goal])
    await db_session.commit()
    assert debt.version == 1
    assert goal.version == 1

    debt.remaining_amount = 800000.0
    goal.current_amount = 500000.0
    await db_session.commit()
    assert debt.version == 2
    assert goal.version == 2

    debt.deleted_at = datetime.now(timezone.utc)
    goal.deleted_at = datetime.now(timezone.utc)
    await db_session.commit()
    assert debt.version == 3
    assert goal.version == 3


@pytest.mark.asyncio
async def test_optimistic_locking_stale_data_detection(test_engine):
    session_factory = async_sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)
    
    # 1. Create a user
    user_id = str(uuid.uuid4())
    async with session_factory() as s1:
        user = User(
            id=user_id,
            email=f"stale_{uuid.uuid4().hex[:8]}@lifeos.finance",
            hashed_password="hash",
            full_name="Original Name",
        )
        s1.add(user)
        await s1.commit()
        assert user.version == 1

    # 2. Session 1 loads user
    async with session_factory() as s1:
        u1 = await s1.get(User, user_id)
        assert u1.version == 1

        # Session 2 concurrently updates user
        async with session_factory() as s2:
            u2 = await s2.get(User, user_id)
            u2.full_name = "Session 2 Name"
            await s2.commit()
            assert u2.version == 2

        # Session 1 attempts update with stale version 1
        u1.full_name = "Session 1 Name"
        with pytest.raises(StaleDataError):
            await s1.commit()

