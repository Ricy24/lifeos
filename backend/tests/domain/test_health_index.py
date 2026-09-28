"""
Tests for Financial Health Index (H) Engine:
- Exact known values
- Property tests (H always in [0, 100])
- Handling of missing / insufficient data (returns None)
- Custom weights with automatic renormalization
- Custom thresholds
- Authenticated V2 API integration test
"""

import math
import uuid
import numpy as np
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

from app.main import app
from app.core.database import Base, get_db
from app.core.security import create_access_token_v2
from app.models.user import User
from app.domain.finance.health_index import (
    calculate_health_index,
    clamp,
    DEFAULT_WEIGHTS,
    DEFAULT_THRESHOLDS,
)

TEST_DB_URL = "postgresql+asyncpg://postgres:Riki1049616429@localhost:5432/lifeos_test"


# ─── 1. Pure Unit Tests ───────────────────────────────────────────────────────

def test_clamp_helper():
    assert clamp(-5.0, 0.0, 1.0) == 0.0
    assert clamp(15.0, 0.0, 1.0) == 1.0
    assert clamp(0.45, 0.0, 1.0) == 0.45


def test_health_index_perfect_score():
    """L=1.0, S=1.0, D=1.0 -> H = 100.0"""
    res = calculate_health_index(
        liquidity_months=6.0,
        savings_rate=0.20,
        debt_to_income_ratio=0.0,
    )
    assert res is not None
    assert res.liquidity_score == 1.0
    assert res.savings_score == 1.0
    assert res.debt_score == 1.0
    assert res.composite_index == 100.0


def test_health_index_worst_score():
    """L=0.0, S=0.0, D=0.0 (debt >= 36%) -> H = 0.0"""
    res = calculate_health_index(
        liquidity_months=0.0,
        savings_rate=0.0,
        debt_to_income_ratio=0.36,
    )
    assert res is not None
    assert res.liquidity_score == 0.0
    assert res.savings_score == 0.0
    assert res.debt_score == 0.0
    assert res.composite_index == 0.0


def test_health_index_midpoint_known_value():
    """L=0.5, S=0.5, D=0.5 -> H = 50.0"""
    res = calculate_health_index(
        liquidity_months=3.0,     # 3 / 6 = 0.5
        savings_rate=0.10,        # 0.10 / 0.20 = 0.5
        debt_to_income_ratio=0.18 # 1 - (0.18 / 0.36) = 0.5
    )
    assert res is not None
    assert res.liquidity_score == 0.5
    assert res.savings_score == 0.5
    assert res.debt_score == 0.5
    assert res.composite_index == 50.0


def test_health_index_clamping_beyond_targets():
    """Inputs well beyond targets must clamp cleanly to [0, 1]."""
    res = calculate_health_index(
        liquidity_months=24.0,   # > 6 -> L = 1.0
        savings_rate=0.80,       # > 0.20 -> S = 1.0
        debt_to_income_ratio=0.90# > 0.36 -> D = 0.0
    )
    assert res is not None
    assert res.liquidity_score == 1.0
    assert res.savings_score == 1.0
    assert res.debt_score == 0.0
    # 0.4*1 + 0.4*1 + 0.2*0 = 0.8 -> 80.0
    assert res.composite_index == 80.0


def test_health_index_insufficient_data_returns_none():
    """Returns None when essential metrics are missing or NaN."""
    assert calculate_health_index(None, 0.20, 0.10) is None
    assert calculate_health_index(6.0, None, 0.10) is None
    assert calculate_health_index(6.0, 0.20, None) is None
    assert calculate_health_index(float("nan"), 0.20, 0.10) is None


def test_health_index_custom_weights_and_renormalization():
    """Custom weights are automatically normalized to sum to 1.0."""
    custom_w = {"liquidity": 1.0, "savings": 1.0, "debt": 2.0} # sum = 4.0 -> 0.25, 0.25, 0.50
    res = calculate_health_index(
        liquidity_months=6.0,  # L = 1.0
        savings_rate=0.20,     # S = 1.0
        debt_to_income_ratio=0.36, # D = 0.0
        weights=custom_w,
    )
    assert res is not None
    assert res.normalized_weights["liquidity"] == 0.25
    assert res.normalized_weights["savings"] == 0.25
    assert res.normalized_weights["debt"] == 0.50
    # H = 100 * (0.25*1 + 0.25*1 + 0.5*0) = 50.0
    assert res.composite_index == 50.0


def test_health_index_custom_thresholds():
    """Custom thresholds change the scaling."""
    custom_thresh = {
        "liquidity_months_target": 12.0,
        "savings_rate_target": 0.40,
        "debt_ratio_ceiling": 0.50,
    }
    res = calculate_health_index(
        liquidity_months=6.0,       # 6/12 = 0.5
        savings_rate=0.20,          # 0.20/0.40 = 0.5
        debt_to_income_ratio=0.25,  # 1 - (0.25/0.50) = 0.5
        thresholds=custom_thresh,
    )
    assert res is not None
    assert res.composite_index == 50.0


def test_health_index_property_always_between_0_and_100():
    """Property test: for 500 randomized edge-case inputs, H in [0.0, 100.0]."""
    rng = np.random.default_rng(seed=42)
    for _ in range(500):
        liq = float(rng.uniform(-10.0, 50.0))
        sav = float(rng.uniform(-0.5, 2.0))
        debt = float(rng.uniform(-0.5, 3.0))

        res = calculate_health_index(liq, sav, debt)
        assert res is not None
        assert 0.0 <= res.composite_index <= 100.0
        assert 0.0 <= res.liquidity_score <= 1.0
        assert 0.0 <= res.savings_score <= 1.0
        assert 0.0 <= res.debt_score <= 1.0


# ─── 2. API v2 Integration Test ───────────────────────────────────────────────

@pytest_asyncio.fixture
async def test_session_maker():
    engine = create_async_engine(TEST_DB_URL, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    maker = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    yield maker
    await engine.dispose()


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
async def test_api_v2_health_index_endpoint(client: AsyncClient, test_session_maker):
    async with test_session_maker() as session:
        user = User(
            id=str(uuid.uuid4()),
            email=f"health_{uuid.uuid4().hex[:8]}@lifeos.finance",
            hashed_password="pw",
            full_name="Health Tester",
        )
        session.add(user)
        await session.commit()

    token = create_access_token_v2(user.id)

    # 1. Post explicit values
    resp = await client.post(
        "/api/v2/analytics/health-index",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "liquidity_months": 6.0,
            "savings_rate": 0.20,
            "debt_to_income_ratio": 0.0,
        },
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["status"] == "sufficient_data"
    assert data["health_index"] == 100.0
    assert data["liquidity_score"] == 1.0
    assert data["savings_score"] == 1.0
    assert data["debt_score"] == 1.0

    # 2. Post without values for newly created user without transactions/income -> insufficient_data
    resp_empty = await client.post(
        "/api/v2/analytics/health-index",
        headers={"Authorization": f"Bearer {token}"},
        json={},
    )
    assert resp_empty.status_code == 200
    assert resp_empty.json()["status"] == "insufficient_data"
    assert resp_empty.json()["health_index"] is None
