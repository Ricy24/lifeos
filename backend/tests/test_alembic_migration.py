"""
Tests for Alembic Migrations:
(a) upgrade head on empty database succeeds
(b) alembic check detects no drift between models and schema
(c) database with original schema (d33e51b1a44d) + sample rows:
    stamp d33e51b1a44d + upgrade head preserves all rows with exact Numeric amounts
"""

import asyncio
from decimal import Decimal
import subprocess
import sys
import uuid
import pytest
import asyncpg

TEST_DB_DSN = "postgresql://postgres:Riki1049616429@localhost:5432/lifeos_test"
TEST_DB_ALEMBIC_URL = "postgresql+asyncpg://postgres:Riki1049616429@localhost:5432/lifeos_test"


async def reset_schema():
    conn = await asyncpg.connect(TEST_DB_DSN)
    await conn.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
    await conn.close()


def run_alembic(*args):
    import os
    cwd = os.getcwd()
    if not os.path.exists(os.path.join(cwd, "alembic.ini")):
        cwd = os.path.join(cwd, "backend")
    cmd = [
        sys.executable,
        "-m",
        "alembic",
        "-x",
        f"db_url={TEST_DB_ALEMBIC_URL}",
        *args,
    ]
    return subprocess.run(cmd, capture_output=True, text=True, cwd=cwd)


@pytest.mark.asyncio
async def test_alembic_empty_upgrade_and_check():
    # 1. Start with fresh empty schema
    await reset_schema()

    # 2. Upgrade to head
    res_up = run_alembic("upgrade", "head")
    assert res_up.returncode == 0, f"alembic upgrade head failed: {res_up.stderr}\n{res_up.stdout}"

    # 3. Alembic check for drift
    res_chk = run_alembic("check")
    assert res_chk.returncode == 0, f"alembic check failed: {res_chk.stderr}\n{res_chk.stdout}"
    assert "No new upgrade operations detected" in res_chk.stdout or res_chk.returncode == 0


@pytest.mark.asyncio
async def test_alembic_migration_preserves_data_and_converts_numeric():
    # 1. Reset database
    await reset_schema()

    # 2. Upgrade to base initial schema
    res_base = run_alembic("upgrade", "d33e51b1a44d")
    assert res_base.returncode == 0, f"upgrade to base failed: {res_base.stderr}"

    # 3. Insert sample rows using original schema
    conn = await asyncpg.connect(TEST_DB_DSN)
    user_id = str(uuid.uuid4())
    account_id = str(uuid.uuid4())
    tx_id = str(uuid.uuid4())
    debt_id = str(uuid.uuid4())
    payment_id = str(uuid.uuid4())
    goal_id = str(uuid.uuid4())

    await conn.execute(
        """
        INSERT INTO users (id, email, hashed_password, full_name, is_active, created_at, updated_at)
        VALUES ($1, $2, $3, $4, true, NOW(), NOW())
        """,
        user_id, "alembic_test@lifeos.finance", "hash", "Alembic Migrator"
    )

    await conn.execute(
        """
        INSERT INTO accounts (id, user_id, name, account_type, balance, currency, is_active, include_in_total, created_at, updated_at)
        VALUES ($1, $2, 'Test Checking', 'bank', 1234567.89, 'COP', true, true, NOW(), NOW())
        """,
        account_id, user_id
    )

    await conn.execute(
        """
        INSERT INTO debts (id, user_id, person_or_entity, debt_type, original_amount, remaining_amount, interest_rate, debt_date, priority, status, created_at, updated_at)
        VALUES ($1, $2, 'Creditor X', 'i_owe', 500000.50, 250000.25, 12.75, NOW(), 3, 'pending', NOW(), NOW())
        """,
        debt_id, user_id
    )

    await conn.execute(
        """
        INSERT INTO debt_payments (id, debt_id, amount, payment_date, created_at)
        VALUES ($1, $2, 249999.75, NOW(), NOW())
        """,
        payment_id, debt_id
    )

    await conn.execute(
        """
        INSERT INTO goals (id, user_id, name, target_amount, current_amount, category, priority, status, created_at, updated_at)
        VALUES ($1, $2, 'Car', 3000000.00, 150000.50, 'purchase', 3, 'active', NOW(), NOW())
        """,
        goal_id, user_id
    )

    await conn.execute(
        """
        INSERT INTO transactions (id, user_id, account_id, amount, transaction_type, category, transaction_date, is_recurring, source, sync_status, created_at, updated_at)
        VALUES ($1, $2, $3, 75432.10, 'expense', 'groceries', NOW(), false, 'app', 'synced', NOW(), NOW())
        """,
        tx_id, user_id, account_id
    )
    await conn.close()

    # 4. Stamp d33e51b1a44d
    res_stamp = run_alembic("stamp", "d33e51b1a44d")
    assert res_stamp.returncode == 0, f"stamp failed: {res_stamp.stderr}"

    # 5. Upgrade to head (runs migration 2)
    res_head = run_alembic("upgrade", "head")
    assert res_head.returncode == 0, f"upgrade head after stamp failed: {res_head.stderr}"

    # 6. Verify data preservation and types
    conn = await asyncpg.connect(TEST_DB_DSN)
    
    # Check account
    acc = await conn.fetchrow("SELECT balance, version, deleted_at FROM accounts WHERE id = $1", account_id)
    assert acc["balance"] == Decimal("1234567.89")
    assert acc["version"] == 1
    assert acc["deleted_at"] is None

    # Check debt
    debt = await conn.fetchrow("SELECT original_amount, remaining_amount, interest_rate, version FROM debts WHERE id = $1", debt_id)
    assert debt["original_amount"] == Decimal("500000.50")
    assert debt["remaining_amount"] == Decimal("250000.25")
    assert debt["interest_rate"] == Decimal("12.75")
    assert debt["version"] == 1

    # Check debt_payment updated_at
    payment = await conn.fetchrow("SELECT amount, version, updated_at FROM debt_payments WHERE id = $1", payment_id)
    assert payment["amount"] == Decimal("249999.75")
    assert payment["version"] == 1
    assert payment["updated_at"] is not None

    # Check goal
    goal = await conn.fetchrow("SELECT target_amount, current_amount, version FROM goals WHERE id = $1", goal_id)
    assert goal["target_amount"] == Decimal("3000000.00")
    assert goal["current_amount"] == Decimal("150000.50")
    assert goal["version"] == 1

    # Check transaction
    tx = await conn.fetchrow("SELECT amount, version FROM transactions WHERE id = $1", tx_id)
    assert tx["amount"] == Decimal("75432.10")
    assert tx["version"] == 1

    await conn.close()
