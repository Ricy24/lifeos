"""Optional AI insights backed by Gemini or OpenAI."""

import json
from typing import Any

import httpx

from app.core.config import settings


class FinancialInsightsService:
    async def generate(self, dashboard: dict[str, Any]) -> str | None:
        if settings.AI_PROVIDER.lower() == "openai" and settings.OPENAI_API_KEY:
            return await self._openai(dashboard)
        if settings.GEMINI_API_KEY:
            return await self._gemini(dashboard)
        return None

    @staticmethod
    def _prompt(dashboard: dict[str, Any]) -> str:
        data = json.dumps(dashboard, ensure_ascii=True, default=str)
        return (
            "Actua como asesor financiero personal. Analiza estos datos de LifeOS Finance "
            "y devuelve exactamente tres recomendaciones breves, concretas y accionables. "
            "No inventes datos, no prometas rendimientos y no incluyas asesoramiento legal.\n\n"
            f"Datos: {data}"
        )

    async def _openai(self, dashboard: dict[str, Any]) -> str:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                "https://api.openai.com/v1/chat/completions",
                headers={"Authorization": f"Bearer {settings.OPENAI_API_KEY}"},
                json={
                    "model": "gpt-4o-mini",
                    "messages": [{"role": "user", "content": self._prompt(dashboard)}],
                    "temperature": 0.2,
                },
            )
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"]

    async def _gemini(self, dashboard: dict[str, Any]) -> str:
        url = (
            "https://generativelanguage.googleapis.com/v1beta/models/"
            f"gemini-3.6-flash:generateContent?key={settings.GEMINI_API_KEY}"
        )
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                url,
                json={"contents": [{"parts": [{"text": self._prompt(dashboard)}]}]},
            )
            response.raise_for_status()
            return response.json()["candidates"][0]["content"]["parts"][0]["text"]
