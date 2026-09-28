"""
LifeOS Finance — Finance Endpoints
Endpoints exposing the deterministic Financial Truth Engine.
"""

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Optional

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.application.finance_service import FinancialTruthService
from app.schemas.financial_truth import (
    CanonicalFinancialStateResponse,
    FinancialHealthResponse,
    ProtectedCashBreakdownSchema,
    FinancialSnapshotResponse,
)

router = APIRouter(prefix="/finance", tags=["Financial Truth Engine"])


@router.get("/health", response_model=FinancialHealthResponse)
async def get_financial_health(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """
    Get the deterministic Financial Health score, status (RED/YELLOW/GREEN),
    transparent indicators, and human-readable rationales.
    Zero LLM dependency.
    """
    service = FinancialTruthService(db, user.id)
    state = await service.get_canonical_state()
    return state.health_assessment.to_dict()


@router.get("/state", response_model=CanonicalFinancialStateResponse)
async def get_canonical_state(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """
    Get the complete canonical financial state (assets, liabilities, net worth,
    obligations, protected money, free cash, and health score).
    """
    service = FinancialTruthService(db, user.id)
    state = await service.get_canonical_state()
    return state.to_dict()


@router.get("/protected-cash", response_model=ProtectedCashBreakdownSchema)
async def get_protected_cash(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """
    Get the traceable breakdown of protected cash (hard obligations, emergency minimum,
    partner allocation, goal reserves, and free cash).
    """
    service = FinancialTruthService(db, user.id)
    state = await service.get_canonical_state()
    return state.protected_cash_breakdown.to_dict()


@router.post("/snapshot", response_model=FinancialSnapshotResponse, status_code=status.HTTP_201_CREATED)
async def take_financial_snapshot(
    snapshot_type: str = Query("adhoc", description="Type of snapshot: daily, weekly, monthly, adhoc"),
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """
    Compute current financial truth and record a historical cache snapshot.
    """
    service = FinancialTruthService(db, user.id)
    snapshot = await service.create_snapshot(snapshot_type=snapshot_type)
    return {
        "id": snapshot.id,
        "user_id": snapshot.user_id,
        "snapshot_date": snapshot.snapshot_date.isoformat(),
        "snapshot_type": snapshot.snapshot_type,
        "available_cash": snapshot.available_cash,
        "total_assets": snapshot.total_assets,
        "total_liabilities": snapshot.total_liabilities,
        "net_worth": snapshot.net_worth,
        "monthly_income": snapshot.monthly_income,
        "monthly_expenses": snapshot.monthly_expenses,
        "monthly_obligations": snapshot.monthly_obligations,
        "protected_cash": snapshot.protected_cash,
        "free_cash": snapshot.free_cash,
        "goal_progress": snapshot.goal_progress,
        "debt_pressure": snapshot.debt_pressure,
        "obligation_coverage": snapshot.obligation_coverage,
        "financial_health_score": snapshot.financial_health_score,
        "financial_health_status": snapshot.financial_health_status,
        "indicators": snapshot.indicators,
        "metadata_extra": snapshot.metadata_extra,
        "created_at": snapshot.created_at.isoformat(),
    }


@router.get("/snapshots/latest", response_model=Optional[FinancialSnapshotResponse])
async def get_latest_snapshot(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Get the latest recorded historical snapshot."""
    service = FinancialTruthService(db, user.id)
    snapshot = await service.get_latest_snapshot()
    if not snapshot:
        return None
    return {
        "id": snapshot.id,
        "user_id": snapshot.user_id,
        "snapshot_date": snapshot.snapshot_date.isoformat(),
        "snapshot_type": snapshot.snapshot_type,
        "available_cash": snapshot.available_cash,
        "total_assets": snapshot.total_assets,
        "total_liabilities": snapshot.total_liabilities,
        "net_worth": snapshot.net_worth,
        "monthly_income": snapshot.monthly_income,
        "monthly_expenses": snapshot.monthly_expenses,
        "monthly_obligations": snapshot.monthly_obligations,
        "protected_cash": snapshot.protected_cash,
        "free_cash": snapshot.free_cash,
        "goal_progress": snapshot.goal_progress,
        "debt_pressure": snapshot.debt_pressure,
        "obligation_coverage": snapshot.obligation_coverage,
        "financial_health_score": snapshot.financial_health_score,
        "financial_health_status": snapshot.financial_health_status,
        "indicators": snapshot.indicators,
        "metadata_extra": snapshot.metadata_extra,
        "created_at": snapshot.created_at.isoformat(),
    }
