"""
LifeOS Finance — Analytics V2 Router
Exposes pure mathematical engines via authenticated V2 endpoints.
"""

from fastapi import APIRouter, Depends, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.domain.finance.health_index import calculate_health_index
from app.schemas.analytics_v2 import HealthIndexRequest, HealthIndexResponse

router = APIRouter(prefix="/analytics", tags=["Analytics V2"])


@router.post("/health-index", response_model=HealthIndexResponse)
async def get_health_index_v2(
    request: HealthIndexRequest,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Calculate pure financial health index H continuous metric.
    If metrics are not passed explicitly, computes from the user's canonical state.
    """
    liquidity_months = request.liquidity_months
    savings_rate = request.savings_rate
    debt_to_income = request.debt_to_income_ratio

    # If parameters not supplied, fetch canonical state for the user
    if liquidity_months is None or savings_rate is None or debt_to_income is None:
        from app.application.finance_service import FinancialTruthService
        service = FinancialTruthService(db, user.id)
        state = await service.get_canonical_state()
        if state.monthly_income <= 0:
            return HealthIndexResponse(status="insufficient_data")

        indicators = state.health_assessment.indicators
        if liquidity_months is None:
            liquidity_months = indicators.liquidity_months
        if savings_rate is None:
            savings_rate = indicators.savings_rate
        if debt_to_income is None:
            debt_to_income = indicators.debt_pressure

    breakdown = calculate_health_index(
        liquidity_months=liquidity_months,
        savings_rate=savings_rate,
        debt_to_income_ratio=debt_to_income,
        weights=request.weights,
        thresholds=request.thresholds,
    )

    if breakdown is None:
        return HealthIndexResponse(
            status="insufficient_data",
        )

    return HealthIndexResponse(
        health_index=breakdown.composite_index,
        liquidity_score=breakdown.liquidity_score,
        savings_score=breakdown.savings_score,
        debt_score=breakdown.debt_score,
        normalized_weights=breakdown.normalized_weights,
        status="sufficient_data",
    )
