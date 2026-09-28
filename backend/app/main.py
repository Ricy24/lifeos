"""
LifeOS Finance — Main Backend App
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

from app.core.config import settings
from app.core.database import engine
from app.api.v1 import auth, accounts, transactions, tasks, debts, goals, insights, telegram, finance, work, forecast
from app.api.v2 import auth as auth_v2
from app.api.v2 import analytics as analytics_v2
from app.services.telegram_bot_service import SmartTelegramBot, run_telegram_polling
import asyncio

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. Startup Protection: fail with clear message if JWT_SECRET is missing
    if not settings.effective_jwt_secret:
        raise RuntimeError("Missing required JWT_SECRET (or SECRET_KEY). Application cannot start securely without a configured JWT signing secret.")

    # Schema management is owned by Alembic migrations
    
    # Create default admin user if none exists
    from app.core.database import async_session_factory
    from app.api.v1.auth import create_initial_user
    async with async_session_factory() as db:
        await create_initial_user(db, settings.ADMIN_EMAIL, settings.ADMIN_PASSWORD)

    bot_task = None
    if settings.TELEGRAM_BOT_TOKEN:
        bot = SmartTelegramBot()
        if settings.TELEGRAM_WEBHOOK_URL:
            await bot.set_webhook(settings.TELEGRAM_WEBHOOK_URL, settings.TELEGRAM_WEBHOOK_SECRET)
        else:
            bot_task = asyncio.create_task(run_telegram_polling())
        
    yield
    # Shutdown
    if bot_task:
        bot_task.cancel()
    await engine.dispose()

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    lifespan=lifespan
)

# CORS Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(auth.router, prefix="/api/v1")
app.include_router(accounts.router, prefix="/api/v1")
app.include_router(transactions.router, prefix="/api/v1")
app.include_router(debts.router, prefix="/api/v1")
app.include_router(goals.router, prefix="/api/v1")
app.include_router(insights.router, prefix="/api/v1")
app.include_router(tasks.router, prefix="/api/v1")
app.include_router(telegram.router, prefix="/api/v1")
app.include_router(finance.router, prefix="/api/v1")
app.include_router(work.router, prefix="/api/v1")
app.include_router(forecast.router, prefix="/api/v1")

# Routers v2
app.include_router(auth_v2.router, prefix="/api/v2")
app.include_router(analytics_v2.router, prefix="/api/v2")

@app.get("/health", tags=["System"])
async def health_check():
    """Health check endpoint for Render pinging."""
    return {"status": "ok", "version": settings.APP_VERSION}

if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG
    )
