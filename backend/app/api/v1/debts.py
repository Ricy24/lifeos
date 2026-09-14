"""
LifeOS Finance — Debt Endpoints
"""

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional
import uuid

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.models.debt import Debt, DebtPayment, DebtStatus
from app.schemas.debt import DebtCreate, DebtUpdate, DebtResponse, DebtPaymentCreate, DebtPaymentResponse
from app.services.priority_engine import calculate_debt_priority

router = APIRouter(prefix="/debts", tags=["Debts"])

@router.get("", response_model=List[DebtResponse])
async def get_debts(
    status: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    """Get all debts, optionally filtered by status."""
    query = select(Debt).where(Debt.user_id == user.id)
    if status:
        query = query.where(Debt.status == status)
    
    result = await db.execute(query)
    debts = result.scalars().all()
    return sorted(
        debts,
        key=lambda debt: calculate_debt_priority(
            debt.priority,
            debt.status.value if isinstance(debt.status, DebtStatus) else debt.status,
            debt.due_date,
            debt.interest_rate,
            debt.original_amount,
            debt.remaining_amount,
        ),
        reverse=True,
    )

@router.post("", response_model=DebtResponse, status_code=status.HTTP_201_CREATED)
async def create_debt(
    debt_in: DebtCreate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    new_debt = Debt(
        id=debt_in.id or str(uuid.uuid4()),
        user_id=user.id,
        person_or_entity=debt_in.person_or_entity,
        debt_type=debt_in.debt_type,
        original_amount=debt_in.original_amount,
        remaining_amount=debt_in.remaining_amount if debt_in.remaining_amount is not None else debt_in.original_amount,
        interest_rate=debt_in.interest_rate,
        debt_date=debt_in.debt_date,
        due_date=debt_in.due_date,
        priority=debt_in.priority,
        description=debt_in.description,
        notes=debt_in.notes,
        tags=debt_in.tags or []
    )
    db.add(new_debt)
    await db.commit()
    await db.refresh(new_debt)
    return new_debt

@router.put("/{debt_id}", response_model=DebtResponse)
async def update_debt(
    debt_id: str,
    debt_in: DebtUpdate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    result = await db.execute(select(Debt).where(Debt.id == debt_id, Debt.user_id == user.id))
    debt = result.scalar_one_or_none()
    if not debt:
        raise HTTPException(status_code=404, detail="Debt not found")

    update_data = debt_in.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(debt, field, value)

    await db.commit()
    await db.refresh(debt)
    return debt

@router.post("/{debt_id}/payments", response_model=DebtPaymentResponse)
async def add_debt_payment(
    debt_id: str,
    payment_in: DebtPaymentCreate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    async with db.begin_nested():
        result = await db.execute(select(Debt).where(Debt.id == debt_id, Debt.user_id == user.id))
        debt = result.scalar_one_or_none()
        if not debt:
            raise HTTPException(status_code=404, detail="Debt not found")
            
        payment = DebtPayment(
            debt_id=debt.id,
            amount=payment_in.amount,
            payment_date=payment_in.payment_date,
            notes=payment_in.notes
        )
        db.add(payment)
        
        # Update debt remaining amount
        debt.remaining_amount -= payment.amount
        
        # Auto-update status if fully paid
        if debt.remaining_amount <= 0:
            debt.remaining_amount = 0
            debt.status = DebtStatus.PAID
        elif debt.status == DebtStatus.PENDING:
            debt.status = DebtStatus.PARTIALLY_PAID

    await db.commit()
    await db.refresh(payment)
    return payment
