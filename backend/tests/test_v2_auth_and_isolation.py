"""
Tests for JWT v2 (Access + Refresh with rotation and revocation)
and Multi-tenant Isolation (A/B testing across Repositories and APIs).
"""

from decimal import Decimal
import uuid
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy import select

from app.main import app
from app.core.database import Base
from app.core.security import hash_password, create_access_token_v2
from app.models.user import User
from app.models.account import Account, AccountType
from app.models.transaction import Transaction, TransactionType
from app.models.goal import Goal, GoalCategory, GoalStatus
from app.models.refresh_token import RefreshToken
from app.repositories.account_repo import AccountRepository
from app.repositories.transaction_repo import TransactionRepository
from app.repositories.goal_repo import GoalRepository

from app.core.database import Base, get_db

TEST_DB_URL = "postgresql+asyncpg://postgres:Riki1049616429@localhost:5432/lifeos_test"


@pytest_asyncio.fixture
async def test_session_maker():
    engine = create_async_engine(TEST_DB_URL, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    maker = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    yield maker
    await engine.dispose()


@pytest_asyncio.fixture
async def db_session(test_session_maker):
    async with test_session_maker() as session:
        yield session


@pytest_asyncio.fixture
async def client(test_session_maker):
    async def override_db():
        async with test_session_maker() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    app.dependency_overrides[get_db] = override_db
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_v2_auth_register_login_rotation_and_logout(client: AsyncClient, db_session: AsyncSession):
    unique_email = f"user_v2_{uuid.uuid4().hex[:8]}@lifeos.finance"
    password = "SuperSecretPassword123"

    # 1. Register V2
    reg_resp = await client.post(
        "/api/v2/auth/register",
        json={"email": unique_email, "password": password, "full_name": "V2 User"},
    )
    assert reg_resp.status_code == 201, reg_resp.text
    reg_data = reg_resp.json()
    assert "access_token" in reg_data
    assert "refresh_token" in reg_data
    assert reg_data["token_type"] == "bearer"
    assert reg_data["user"]["email"] == unique_email

    first_refresh = reg_data["refresh_token"]

    # 2. Login V2
    login_resp = await client.post(
        "/api/v2/auth/login",
        json={"email": unique_email, "password": password},
    )
    assert login_resp.status_code == 200, login_resp.text
    login_data = login_resp.json()
    access_token_1 = login_data["access_token"]
    refresh_token_1 = login_data["refresh_token"]

    # Verify access token works on /api/v2/auth/me
    me_resp = await client.get(
        "/api/v2/auth/me",
        headers={"Authorization": f"Bearer {access_token_1}"},
    )
    assert me_resp.status_code == 200
    assert me_resp.json()["email"] == unique_email

    # 3. Refresh with rotation
    refresh_resp = await client.post(
        "/api/v2/auth/refresh",
        json={"refresh_token": refresh_token_1},
    )
    assert refresh_resp.status_code == 200, refresh_resp.text
    refresh_data = refresh_resp.json()
    access_token_2 = refresh_data["access_token"]
    refresh_token_2 = refresh_data["refresh_token"]
    assert refresh_token_2 != refresh_token_1

    # Verify new access token works
    me_resp2 = await client.get(
        "/api/v2/auth/me",
        headers={"Authorization": f"Bearer {access_token_2}"},
    )
    assert me_resp2.status_code == 200

    # 4. Rotation check: Old refresh token must be rejected upon reuse
    reused_resp = await client.post(
        "/api/v2/auth/refresh",
        json={"refresh_token": refresh_token_1},
    )
    assert reused_resp.status_code == 401
    assert "revoked" in reused_resp.json()["detail"].lower()

    # 5. Logout
    logout_resp = await client.post(
        "/api/v2/auth/logout",
        json={"refresh_token": refresh_token_2},
    )
    assert logout_resp.status_code == 200
    assert logout_resp.json()["message"] == "Successfully logged out"

    # 6. Verify logged out token cannot refresh
    post_logout_resp = await client.post(
        "/api/v2/auth/refresh",
        json={"refresh_token": refresh_token_2},
    )
    assert post_logout_resp.status_code == 401


@pytest.mark.asyncio
async def test_user_isolation_repository_ab(db_session: AsyncSession):
    """
    Test tenant isolation at the Repository layer between User A and User B.
    User B must never be able to view, query, update, or soft-delete User A's data.
    """
    # 1. Create User A and User B
    user_a = User(
        email=f"usera_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password=hash_password("passwordA123"),
        full_name="User A",
    )
    user_b = User(
        email=f"userb_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password=hash_password("passwordB123"),
        full_name="User B",
    )
    db_session.add_all([user_a, user_b])
    await db_session.commit()
    await db_session.refresh(user_a)
    await db_session.refresh(user_b)

    # 2. Instantiate Repositories
    repo_acc_a = AccountRepository(db_session, user_a.id)
    repo_acc_b = AccountRepository(db_session, user_b.id)

    repo_tx_a = TransactionRepository(db_session, user_a.id)
    repo_tx_b = TransactionRepository(db_session, user_b.id)

    repo_goal_a = GoalRepository(db_session, user_a.id)
    repo_goal_b = GoalRepository(db_session, user_b.id)

    # 3. User A creates an Account, a Transaction, and a Goal
    acc_a = await repo_acc_a.create(
        Account(
            name="User A Secret Vault",
            account_type=AccountType.BANK,
            balance=Decimal("9500000.00"),
            currency="COP",
        )
    )

    tx_a = await repo_tx_a.create(
        Transaction(
            account_id=acc_a.id,
            amount=Decimal("50000.00"),
            transaction_type=TransactionType.EXPENSE,
            category="confidential",
            description="User A classified payment",
        )
    )

    goal_a = await repo_goal_a.create(
        Goal(
            name="User A Private Island",
            target_amount=Decimal("100000000.00"),
            current_amount=Decimal("25000000.00"),
            category=GoalCategory.PURCHASE,
            status=GoalStatus.ACTIVE,
        )
    )

    # 4. User B attempts read operations on User A's data via Repository
    assert await repo_acc_b.get_by_id(acc_a.id) is None
    assert acc_a.id not in [acc.id for acc in await repo_acc_b.list()]
    assert acc_a.id not in [acc.id for acc in await repo_acc_b.get_active_accounts()]

    assert await repo_tx_b.get_by_id(tx_a.id) is None
    assert tx_a.id not in [t.id for t in await repo_tx_b.list()]
    assert tx_a.id not in [t.id for t in await repo_tx_b.get_recent()]

    assert await repo_goal_b.get_by_id(goal_a.id) is None
    assert goal_a.id not in [g.id for g in await repo_goal_b.list()]
    assert goal_a.id not in [g.id for g in await repo_goal_b.get_active_goals()]

    # 5. User B attempts write / mutation on User A's data
    update_res = await repo_acc_b.update(acc_a.id, name="Compromised Account", balance=Decimal("0.00"))
    assert update_res is None

    # Verify Account A is unchanged
    refreshed_acc_a = await repo_acc_a.get_by_id(acc_a.id)
    assert refreshed_acc_a.name == "User A Secret Vault"
    assert refreshed_acc_a.balance == Decimal("9500000.00")

    # User B attempts soft delete
    delete_res = await repo_acc_b.soft_delete(acc_a.id)
    assert delete_res is False

    refreshed_acc_a_after = await repo_acc_a.get_by_id(acc_a.id)
    assert refreshed_acc_a_after.deleted_at is None


@pytest.mark.asyncio
async def test_user_isolation_api_ab(client: AsyncClient, db_session: AsyncSession):
    """
    Test tenant isolation via HTTP API endpoints between User A and User B.
    User B cannot retrieve or delete User A's accounts or transactions.
    """
    # 1. Create User A & User B
    user_a = User(
        email=f"api_usera_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password=hash_password("pwA123"),
        full_name="API User A",
    )
    user_b = User(
        email=f"api_userb_{uuid.uuid4().hex[:8]}@lifeos.finance",
        hashed_password=hash_password("pwB123"),
        full_name="API User B",
    )
    db_session.add_all([user_a, user_b])
    await db_session.commit()
    await db_session.refresh(user_a)
    await db_session.refresh(user_b)

    token_a = create_access_token_v2(user_a.id)
    token_b = create_access_token_v2(user_b.id)

    # 2. User A creates Account
    acc_res = await client.post(
        "/api/v1/accounts",
        headers={"Authorization": f"Bearer {token_a}"},
        json={"name": "User A Bank", "account_type": "bank", "balance": 1500000.0},
    )
    assert acc_res.status_code == 201, acc_res.text
    account_a_id = acc_res.json()["id"]

    # 3. User A creates Transaction
    tx_res = await client.post(
        "/api/v1/transactions",
        headers={"Authorization": f"Bearer {token_a}"},
        json={
            "account_id": account_a_id,
            "amount": 20000.0,
            "transaction_type": "expense",
            "category": "groceries",
            "description": "User A groceries",
        },
    )
    assert tx_res.status_code == 201, tx_res.text
    tx_a_id = tx_res.json()["id"]

    # 4. User B tries to read Account A -> 404
    b_acc_res = await client.get(
        f"/api/v1/accounts/{account_a_id}",
        headers={"Authorization": f"Bearer {token_b}"},
    )
    assert b_acc_res.status_code == 404

    # 5. User B tries to read Transaction A -> 404
    b_tx_res = await client.get(
        f"/api/v1/transactions/{tx_a_id}",
        headers={"Authorization": f"Bearer {token_b}"},
    )
    assert b_tx_res.status_code == 404

    # 6. User B tries to delete Transaction A -> 404
    b_del_res = await client.delete(
        f"/api/v1/transactions/{tx_a_id}",
        headers={"Authorization": f"Bearer {token_b}"},
    )
    assert b_del_res.status_code == 404
