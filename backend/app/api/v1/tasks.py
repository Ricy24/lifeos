"""
LifeOS Finance — Task Endpoints
Triggered by external cron services (e.g. cron-job.org)
"""

from fastapi import APIRouter, Depends, HTTPException, Header, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.services.financial_engine import FinancialEngine
from app.services.telegram_service import TelegramService
from app.core.config import settings
from app.models.user import User
from sqlalchemy import select

router = APIRouter(prefix="/tasks", tags=["Tasks"])

# Extremely simple auth for the cron job
async def verify_cron_secret(x_cron_secret: str = Header(...)):
    if x_cron_secret != settings.CRON_SECRET:
        raise HTTPException(status_code=401, detail="Invalid cron secret")
    return True

@router.post("/daily-summary", dependencies=[Depends(verify_cron_secret)])
async def generate_daily_summary(db: AsyncSession = Depends(get_db)):
    """
    Triggered daily at night. 
    In the future, this will compile the day's stats and send a Telegram message.
    """
    # For now, just a placeholder that proves the task ran securely
    # (We will add Telegram sending logic in Phase 4/5)
    
    # 1. Get the admin user (since this is a personal app, there is only one)
    result = await db.execute(select(User).limit(1))
    user = result.scalar_one_or_none()
    
    if not user:
        return {"status": "skipped", "reason": "No users found"}
        
    engine = FinancialEngine(db, user.id)
    summary = await engine.get_today_summary()
    
    telegram_sent = False
    try:
        telegram_sent = await TelegramService().send_summary("Resumen diario", summary)
    except Exception:
        # The scheduled endpoint must still return the financial summary if delivery fails.
        telegram_sent = False
    
    return {
        "status": "success", 
        "summary": summary,
        "telegram_sent": telegram_sent,
    }

@router.post("/weekly-summary", dependencies=[Depends(verify_cron_secret)])
async def generate_weekly_summary(db: AsyncSession = Depends(get_db)):
    """Triggered weekly on Sunday."""
    result = await db.execute(select(User).limit(1))
    user = result.scalar_one_or_none()
    if not user:
        return {"status": "skipped", "reason": "No users found"}

    summary = await FinancialEngine(db, user.id).get_week_summary()
    telegram_sent = False
    try:
        telegram_sent = await TelegramService().send_summary("Resumen semanal", summary)
    except Exception:
        telegram_sent = False

    return {
        "status": "success",
        "summary": summary,
        "telegram_sent": telegram_sent,
    }
