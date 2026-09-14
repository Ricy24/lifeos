"""Telegram delivery for scheduled financial summaries."""

from typing import Any

import httpx

from app.core.config import settings


class TelegramService:
    def __init__(self, token: str | None = None, chat_id: str | None = None):
        self.token = token or settings.TELEGRAM_BOT_TOKEN
        self.chat_id = chat_id or settings.TELEGRAM_ALLOWED_USER_ID

    @property
    def is_configured(self) -> bool:
        return bool(self.token and self.chat_id)

    async def send_message(self, text: str) -> bool:
        """Send a message and return False when Telegram is not configured."""
        if not self.is_configured:
            return False

        url = f"https://api.telegram.org/bot{self.token}/sendMessage"
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(
                url,
                json={
                    "chat_id": self.chat_id,
                    "text": text,
                    "disable_web_page_preview": True,
                },
            )
            response.raise_for_status()
        return True

    async def send_summary(self, title: str, summary: dict[str, Any]) -> bool:
        return await self.send_message(self._format_summary(title, summary))

    @staticmethod
    def _format_summary(title: str, summary: dict[str, Any]) -> str:
        lines = [f"LifeOS Finance - {title}"]
        for key, value in summary.items():
            if isinstance(value, dict):
                lines.append(f"\n{key}:")
                lines.extend(f"- {nested_key}: {nested_value}" for nested_key, nested_value in value.items())
            elif isinstance(value, list):
                lines.append(f"{key}: {len(value)} elementos")
            else:
                lines.append(f"{key}: {value}")
        return "\n".join(lines)
