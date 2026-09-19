"""
Contract V1 Integration Tests against real PostgreSQL database.
Verifies:
1. Pydantic v2 serializes Decimal / Numeric fields as JSON numbers (float), not strings.
2. CRUD for User, Account, Transaction, and Goal against real database.
3. ID is UUID string, amounts are JSON numbers, exact V1 keys maintained.
4. Priority engine handles Decimal values without TypeError.
"""

from datetime import datetime, timezone
from decimal import Decimal
import uuid
import httpx
import pytest
import pytest_asyncio
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

from app.core.database import Base, get_db
from app.core.security import create_access_token, hash_password
from app.main import app
from app.models.user import User
from app.schemas.common import ContractBaseModel, DecimalFloat
from app.services.priority_engine import calculate_debt_priority, calculate_goal_priority

TEST_DB_URL = "postgresql+asyncpg://postgres:Riki1049616429@localhost:5432/lifeos_test"


class SampleModel(ContractBaseModel):
    amount: Decimal
    custom_float: DecimalFloat


def test_pydantic_decimal_serializer_outputs_number_not_string():
    """Verify that Pydantic v2 serializes Decimal as float JSON number, not string."""
    obj = SampleModel(amount=Decimal("1234.56"), custom_float=Decimal("78.90"))
    data = obj.model_dump(mode="json")
    
    # Assert type in serialized JSON dictionary
    assert isinstance(data["amount"], (float, int))
    assert not isinstance(data["amount"], str)
    assert data["amount"] == 1234.56

    assert isinstance(data["custom_float"], (float, int))
    assert not isinstance(data["custom_float"], str)
    assert data["custom_float"] == 78.90

    json_str = obj.model_dump_json()
    assert '"amount":1234.56' in json_str
    assert '"custom_float":78.9' in json_str


def test_priority_engine_with_decimals():
    """Verify priority engine works seamlessly with Decimal numbers."""
    debt_score = calculate_debt_priority(
        priority=2,
        status="pending",
        due_date=datetime.now(timezone.utc),
        interest_rate=Decimal("15.5"),
        original_amount=Decimal("1000000.00"),
        remaining_amount=Decimal("500000.00"),
    )
    assert 0 <= debt_score <= 100

    goal_score = calculate_goal_priority(
        priority=1,
        status="active",
        category="emergency",
        target_date=datetime.now(timezone.utc),
        target_amount=Decimal("5000000.00"),
        current_amount=Decimal("2500000.00"),
    )
    assert 0 <= goal_score <= 100


@pytest_asyncio.fixture
async def test_session_maker():
    engine = create_async_engine(TEST_DB_URL, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    maker = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    yield maker
    await engine.dispose()


@pytest_asyncio.fixture
async def authenticated_client(test_session_maker):
    async with test_session_maker() as session:
        user_id = str(uuid.uuid4())
        user = User(
            id=user_id,
            email=f"v1_contract_{uuid.uuid4().hex[:8]}@lifeos.finance",
            hashed_password=hash_password("test_pass"),
            full_name="Contract Tester",
        )
        session.add(user)
        await session.commit()

    async def override_db():
        async with test_session_maker() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    app.dependency_overrides[get_db] = override_db
    token = create_access_token({"sub": user_id})

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://test",
        headers={"Authorization": f"Bearer {token}"},
    ) as client:
        yield client, user_id

    app.dependency_overrides.clear()


@pytest.mark.asyncio
async def test_user_contract_against_real_db(authenticated_client):
    client, user_id = authenticated_client
    res = await client.get("/api/v1/auth/me")
    assert res.status_code == 200
    data = res.json()

    # Verify UUID string
    assert data["id"] == user_id
    uuid_obj = uuid.UUID(data["id"])
    assert str(uuid_obj) == data["id"]

    # Verify keys
    expected_keys = {"id", "email", "full_name", "is_active"}
    assert set(data.keys()) == expected_keys


@pytest.mark.asyncio
async def test_account_crud_contract_against_real_db(authenticated_client):
    client, user_id = authenticated_client

    # 1. CREATE Account
    create_payload = {
        "name": "Bancolombia Savings",
        "account_type": "bank",
        "balance": 1850000.75,
        "currency": "COP",
        "description": "Main account",
        "include_in_total": True,
    }
    res_create = await client.post("/api/v1/accounts", json=create_payload)
    assert res_create.status_code == 201
    created = res_create.json()

    # Verify keys
    expected_keys = {
        "id", "user_id", "name", "account_type", "balance", "currency",
        "description", "color", "icon", "is_active", "include_in_total",
        "created_at", "updated_at"
    }
    assert set(created.keys()) == expected_keys

    # Verify id is UUID string
    account_id = created["id"]
    assert str(uuid.UUID(account_id)) == account_id
    assert created["user_id"] == user_id

    # Verify amount is JSON number (float), not string
    assert isinstance(created["balance"], (int, float))
    assert not isinstance(created["balance"], str)
    assert created["balance"] == 1850000.75

    # 2. READ Account
    res_get = await client.get(f"/api/v1/accounts/{account_id}")
    assert res_get.status_code == 200
    account_data = res_get.json()
    assert account_data["id"] == account_id
    assert isinstance(account_data["balance"], (int, float))
    assert account_data["balance"] == 1850000.75

    # 3. UPDATE Account
    update_payload = {"balance": 2100000.25}
    res_update = await client.put(f"/api/v1/accounts/{account_id}", json=update_payload)
    assert res_update.status_code == 200
    updated = res_update.json()
    assert isinstance(updated["balance"], (int, float))
    assert updated["balance"] == 2100000.25

    # 4. DELETE Account
    res_del = await client.delete(f"/api/v1/accounts/{account_id}")
    assert res_del.status_code in [200, 204]


@pytest.mark.asyncio
async def test_transaction_crud_contract_against_real_db(authenticated_client):
    client, user_id = authenticated_client

    # Create account first
    acc_res = await client.post("/api/v1/accounts", json={"name": "Cash Wallet", "balance": 500000.0})
    acc_id = acc_res.json()["id"]

    # 1. CREATE Transaction
    tx_payload = {
        "account_id": acc_id,
        "amount": 35750.50,
        "transaction_type": "expense",
        "category": "food",
        "description": "Supermarket purchase",
    }
    res_create = await client.post("/api/v1/transactions", json=tx_payload)
    assert res_create.status_code == 201
    created = res_create.json()

    expected_keys = {
        "id", "user_id", "account_id", "amount", "transaction_type", "category",
        "description", "notes", "transaction_date", "location", "tags",
        "is_recurring", "recurring_pattern", "source", "sync_status",
        "created_at", "updated_at"
    }
    assert set(created.keys()) == expected_keys
    tx_id = created["id"]
    assert str(uuid.UUID(tx_id)) == tx_id

    # Check amount is JSON number (float), not string
    assert isinstance(created["amount"], (int, float))
    assert not isinstance(created["amount"], str)
    assert created["amount"] == 35750.50

    # 2. READ Transactions list
    res_list = await client.get("/api/v1/transactions")
    assert res_list.status_code == 200
    tx_list = res_list.json()["transactions"]
    matching = next((t for t in tx_list if t["id"] == tx_id), None)
    assert matching is not None
    assert isinstance(matching["amount"], (int, float))
    assert matching["amount"] == 35750.50

    # 3. UPDATE Transaction
    res_update = await client.put(f"/api/v1/transactions/{tx_id}", json={"amount": 42000.00})
    assert res_update.status_code == 200
    updated = res_update.json()
    assert isinstance(updated["amount"], (int, float))
    assert updated["amount"] == 42000.00

    # 4. DELETE Transaction
    res_del = await client.delete(f"/api/v1/transactions/{tx_id}")
    assert res_del.status_code in [200, 204]


@pytest.mark.asyncio
async def test_goal_crud_contract_against_real_db(authenticated_client):
    client, user_id = authenticated_client

    # 1. CREATE Goal
    goal_payload = {
        "name": "MacBook Pro M4",
        "target_amount": 8500000.00,
        "current_amount": 2125000.00,
        "category": "purchase",
        "priority": 2,
    }
    res_create = await client.post("/api/v1/goals", json=goal_payload)
    assert res_create.status_code == 201
    created = res_create.json()

    expected_keys = {
        "id", "user_id", "name", "target_amount", "current_amount", "category",
        "description", "image_url", "priority", "priority_score", "target_date",
        "status", "notes", "tags", "progress_percentage", "remaining_amount",
        "created_at", "updated_at"
    }
    assert set(created.keys()) == expected_keys
    goal_id = created["id"]
    assert str(uuid.UUID(goal_id)) == goal_id

    # Verify amounts are JSON numbers (float), not string
    assert isinstance(created["target_amount"], (int, float))
    assert not isinstance(created["target_amount"], str)
    assert created["target_amount"] == 8500000.00

    assert isinstance(created["current_amount"], (int, float))
    assert not isinstance(created["current_amount"], str)
    assert created["current_amount"] == 2125000.00

    assert isinstance(created["progress_percentage"], (int, float))
    assert created["progress_percentage"] == 25.0

    assert isinstance(created["remaining_amount"], (int, float))
    assert created["remaining_amount"] == 6375000.00

    # 2. READ Goals
    res_list = await client.get("/api/v1/goals")
    assert res_list.status_code == 200
    goals = res_list.json()
    matching = next((g for g in goals if g["id"] == goal_id), None)
    assert matching is not None
    assert isinstance(matching["target_amount"], (int, float))
    assert isinstance(matching["current_amount"], (int, float))
    assert matching["target_amount"] == 8500000.00

    # 3. UPDATE Goal
    res_update = await client.put(f"/api/v1/goals/{goal_id}", json={"current_amount": 4250000.00})
    assert res_update.status_code == 200
    updated = res_update.json()
    assert isinstance(updated["current_amount"], (int, float))
    assert updated["current_amount"] == 4250000.00
    assert updated["progress_percentage"] == 50.0

    # 4. DELETE Goal
    res_del = await client.delete(f"/api/v1/goals/{goal_id}")
    assert res_del.status_code in [200, 204]
