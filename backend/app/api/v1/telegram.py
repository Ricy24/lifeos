"""Telegram webhook backed by the shared smart bot implementation."""

import asyncio

from fastapi import APIRouter, Header, HTTPException, Request, status

from app.core.config import settings
from app.services.telegram_bot_service import SmartTelegramBot

router = APIRouter(prefix="/telegram", tags=["Telegram"])


@router.post("/webhook")
async def telegram_webhook(
    request: Request,
    x_telegram_bot_api_secret_token: str | None = Header(default=None),
):
    if settings.TELEGRAM_WEBHOOK_SECRET and x_telegram_bot_api_secret_token != settings.TELEGRAM_WEBHOOK_SECRET:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Telegram secret")

    data = await request.json()
    message = data.get("message") or {}
    if message.get("text"):
        bot = SmartTelegramBot()
        asyncio.create_task(
            bot.handle_message(
                user_id=message.get("from", {}).get("id"),
                chat_id=message.get("chat", {}).get("id"),
                text=message["text"],
            )
        )
    return {"ok": True}
