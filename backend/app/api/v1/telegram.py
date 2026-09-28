import asyncio
import logging
from fastapi import APIRouter, Request, Header, HTTPException, status
from app.core.config import settings
from app.services.telegram_bot_service import SmartTelegramBot

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Telegram"])
bot = SmartTelegramBot()


@router.post("/telegram/webhook")
async def telegram_webhook(
    request: Request,
    x_telegram_bot_api_secret_token: str | None = Header(default=None, alias="X-Telegram-Bot-Api-Secret-Token")
):
    """
    Receives incoming Telegram updates via HTTPS Webhook.
    """
    if settings.TELEGRAM_WEBHOOK_SECRET and x_telegram_bot_api_secret_token != settings.TELEGRAM_WEBHOOK_SECRET:
        logger.warning("Unauthorized webhook request received.")
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Invalid webhook secret")
    
    try:
        data = await request.json()
    except Exception:
        return {"ok": False, "error": "Invalid JSON"}

    message = data.get("message")
    if message and "text" in message:
        user_id = message["from"]["id"]
        chat_id = message["chat"]["id"]
        text = message["text"]
        asyncio.create_task(bot.handle_message(user_id, chat_id, text))

    return {"ok": True}
