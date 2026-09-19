"""
LifeOS Finance — Accounts Endpoints
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List
import uuid

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.models.account import Account
from app.schemas.account import AccountCreate, AccountUpdate, AccountResponse

router = APIRouter(prefix="/accounts", tags=["Accounts"])


@router.get("", response_model=List[AccountResponse])
async def get_accounts(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Get all accounts for the current user."""
    result = await db.execute(
        select(Account)
        .where(Account.user_id == user.id)
        .order_by(Account.name)
    )
    return result.scalars().all()


@router.post("", response_model=AccountResponse, status_code=status.HTTP_201_CREATED)
async def create_account(
    account_in: AccountCreate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Create a new account."""
    new_account = Account(
        id=account_in.id or str(uuid.uuid4()),
        user_id=user.id,
        name=account_in.name,
        account_type=account_in.account_type,
        balance=account_in.balance,
        currency=account_in.currency,
        description=account_in.description,
        color=account_in.color,
        icon=account_in.icon,
        include_in_total=account_in.include_in_total,
    )
    db.add(new_account)
    await db.commit()
    await db.refresh(new_account)
    return new_account


@router.get("/summary")
async def get_accounts_summary(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Summary of all accounts, total balance, net worth and canonical state."""
    from app.application.finance_service import FinancialTruthService
    from app.services.financial_engine import FinancialEngine
    service = FinancialTruthService(db, user.id)
    canonical = await service.get_canonical_state()
    engine = FinancialEngine(db, user.id)
    accounts = await engine.get_accounts_summary()
    return {
        "total_balance": canonical.available_cash,
        "net_worth": canonical.net_worth,
        "total_assets": canonical.total_assets,
        "total_liabilities": canonical.total_liabilities,
        "free_cash": canonical.free_cash,
        "protected_cash": canonical.protected_cash,
        "accounts": accounts,
    }


@router.get("/dashboard")
async def get_dashboard(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Complete financial dashboard powered by the Financial Truth Engine."""
    from app.application.finance_service import FinancialTruthService
    from app.services.financial_engine import FinancialEngine
    service = FinancialTruthService(db, user.id)
    canonical = await service.get_canonical_state()
    engine = FinancialEngine(db, user.id)
    legacy_dash = await engine.get_dashboard()
    legacy_dash.update({
        "available_cash": canonical.available_cash,
        "total_assets": canonical.total_assets,
        "total_liabilities": canonical.total_liabilities,
        "net_worth": canonical.net_worth,
        "monthly_obligations": canonical.monthly_obligations,
        "protected_cash": canonical.protected_cash,
        "free_cash": canonical.free_cash,
        "financial_health_score": canonical.financial_health_score,
        "financial_health_status": canonical.financial_health_status,
        "health_assessment": canonical.health_assessment.to_dict(),
        "protected_cash_breakdown": canonical.protected_cash_breakdown.to_dict(),
    })
    return legacy_dash


@router.get("/{account_id}", response_model=AccountResponse)
async def get_account(
    account_id: str,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Get a specific account by ID."""
    result = await db.execute(
        select(Account)
        .where(Account.id == account_id, Account.user_id == user.id)
    )
    account = result.scalar_one_or_none()
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")
    return account


@router.put("/{account_id}", response_model=AccountResponse)
async def update_account(
    account_id: str,
    account_in: AccountUpdate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Update a specific account."""
    result = await db.execute(
        select(Account)
        .where(Account.id == account_id, Account.user_id == user.id)
    )
    account = result.scalar_one_or_none()
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    update_data = account_in.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(account, field, value)

    await db.commit()
    await db.refresh(account)
    return account


@router.delete("/{account_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_account(
    account_id: str,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Delete a specific account."""
    result = await db.execute(
        select(Account)
        .where(Account.id == account_id, Account.user_id == user.id)
    )
    account = result.scalar_one_or_none()
    if not account:
        raise HTTPException(status_code=404, detail="Account not found")

    await db.delete(account)
    await db.commit()
