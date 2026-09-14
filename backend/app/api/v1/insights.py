"""Optional AI financial insights endpoint."""

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.services.ai_service import FinancialInsightsService
from app.services.financial_engine import FinancialEngine

router = APIRouter(prefix="/insights", tags=["Insights"])


class InsightResponse(BaseModel):
    enabled: bool
    insight: str | None = None


@router.post("/financial", response_model=InsightResponse)
async def generate_financial_insight(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    dashboard = await FinancialEngine(db, user.id).get_dashboard()
    try:
        insight = await FinancialInsightsService().generate(dashboard)
    except Exception as error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="AI provider unavailable",
        ) from error

    return InsightResponse(enabled=insight is not None, insight=insight)
