"""
LifeOS Finance — Transaction Endpoints
"""

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional
import uuid

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.models.account import Account
from app.models.transaction import Transaction, TransactionType
from app.schemas.transaction import (
    TransactionCreate,
    TransactionUpdate,
    TransactionResponse,
    TransactionListResponse,
)

router = APIRouter(prefix="/transactions", tags=["Transactions"])


@router.get("", response_model=TransactionListResponse)
async def get_transactions(
    page: int = Query(1, ge=1),
    size: int = Query(20, ge=1, le=100),
    account_id: Optional[str] = None,
    tx_type: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Get paginated transactions, optionally filtered."""
    query = select(Transaction).where(Transaction.user_id == user.id)
    
    if account_id:
        query = query.where(Transaction.account_id == account_id)
    if tx_type:
        query = query.where(Transaction.transaction_type == tx_type)
        
    # Count total
    count_query = select(func.count()).select_from(query.subquery())
    total_result = await db.execute(count_query)
    total = total_result.scalar_one()
    
    # Get items
    query = query.order_by(Transaction.transaction_date.desc())
    query = query.offset((page - 1) * size).limit(size)
    result = await db.execute(query)
    transactions = result.scalars().all()
    
    return TransactionListResponse(
        transactions=transactions,
        total=total,
        page=page,
        page_size=size
    )


@router.post("", response_model=TransactionResponse, status_code=status.HTTP_201_CREATED)
async def create_transaction(
    tx_in: TransactionCreate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Create a new transaction and update account balance."""
    # Start atomic transaction
    async with db.begin_nested():
        # 1. Verify account exists if provided
        account = None
        if tx_in.account_id:
            acc_result = await db.execute(
                select(Account).where(Account.id == tx_in.account_id, Account.user_id == user.id)
            )
            account = acc_result.scalar_one_or_none()
            if not account:
                raise HTTPException(status_code=404, detail="Account not found")

        # 2. Create Transaction
        new_tx = Transaction(
            id=tx_in.id or str(uuid.uuid4()),
            user_id=user.id,
            account_id=tx_in.account_id,
            amount=tx_in.amount,
            transaction_type=tx_in.transaction_type,
            category=tx_in.category,
            description=tx_in.description,
            notes=tx_in.notes,
            transaction_date=tx_in.transaction_date,
            location=tx_in.location,
            tags=tx_in.tags or [],
            is_recurring=tx_in.is_recurring,
            recurring_pattern=tx_in.recurring_pattern,
            source=tx_in.source,
            metadata_extra=tx_in.metadata_extra or {}
        )
        db.add(new_tx)

        # 3. Update Account Balance
        if account:
            if tx_in.transaction_type == TransactionType.INCOME:
                account.balance += tx_in.amount
            elif tx_in.transaction_type == TransactionType.EXPENSE:
                account.balance -= tx_in.amount

    await db.commit()
    await db.refresh(new_tx)
    return new_tx


@router.delete("/{tx_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_transaction(
    tx_id: str,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Delete a transaction and revert its effect on the account balance."""
    async with db.begin_nested():
        result = await db.execute(
            select(Transaction).where(Transaction.id == tx_id, Transaction.user_id == user.id)
        )
        tx = result.scalar_one_or_none()
        if not tx:
            raise HTTPException(status_code=404, detail="Transaction not found")

        # Revert account balance
        if tx.account_id:
            acc_result = await db.execute(
                select(Account).where(Account.id == tx.account_id)
            )
            account = acc_result.scalar_one_or_none()
            if account:
                if tx.transaction_type == TransactionType.INCOME:
                    account.balance -= tx.amount
                elif tx.transaction_type == TransactionType.EXPENSE:
                    account.balance += tx.amount

        await db.delete(tx)
        
    await db.commit()
