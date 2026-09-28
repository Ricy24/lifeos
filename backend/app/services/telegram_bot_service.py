"""
LifeOS Finance — Smart Telegram Bot with Gemini AI
Interprets natural language messages, extracts financial movements and registers them into the database.
"""

import asyncio
import json
import logging
from datetime import datetime, timezone
from decimal import Decimal
import re
from typing import Any, Optional

import httpx
from sqlalchemy import select, and_

from app.core.config import settings
from app.core.database import async_session_factory
from app.models.user import User
from app.models.account import Account, AccountType
from app.models.transaction import Transaction, TransactionType
from app.services.financial_engine import FinancialEngine

logger = logging.getLogger(__name__)


class SmartTelegramBot:
    def __init__(self, token: Optional[str] = None):
        self.token = token or settings.TELEGRAM_BOT_TOKEN
        self.allowed_user_id = str(settings.TELEGRAM_ALLOWED_USER_ID) if settings.TELEGRAM_ALLOWED_USER_ID else None
        self.base_url = f"https://api.telegram.org/bot{self.token}"

    async def send_message(self, chat_id: int | str, text: str) -> bool:
        if not self.token:
            return False
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.post(
                    f"{self.base_url}/sendMessage",
                    json={
                        "chat_id": chat_id,
                        "text": text,
                        "parse_mode": "HTML"
                    }
                )
                return resp.status_code == 200
        except Exception as e:
            logger.error(f"Error sending telegram message: {e}")
            return False

    async def set_webhook(self, webhook_url: str, secret_token: str | None = None) -> bool:
        if not self.token:
            return False
        payload = {"url": webhook_url}
        if secret_token:
            payload["secret_token"] = secret_token
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.post(f"{self.base_url}/setWebhook", json=payload)
                return response.status_code == 200 and response.json().get("ok", False)
        except Exception as error:
            logger.error(f"Error configuring Telegram webhook: {error}")
            return False

    async def interpret_with_gemini(self, text: str) -> dict[str, Any]:
        """Use Gemini 3.6 Flash to parse natural language financial messages into structured JSON."""
        if not settings.GEMINI_API_KEY:
            return self._heuristic_parse(text)

        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key={settings.GEMINI_API_KEY}"

        prompt = f"""Eres el motor de IA financiera de LifeOS. Tu trabajo es interpretar el mensaje del usuario y extraer la intención y los datos financieros.
Responde UNICAMENTE con un objeto JSON válido (sin markdown, sin ```json```, solo texto plano):
{{
  "intent": "expense" o "income" o "check_balance" o "summary" o "unknown",
  "amount": float o null,
  "category": string o null (ejemplos: "Comida", "Transporte", "Servicios", "Supermercado", "Ocio", "Salud", "Sueldo", "Negocio", "Inversión", "Transferencia"),
  "description": string o null,
  "account": string o null (ejemplo: "Nequi", "Bancolombia", "Daviplata", "Efectivo", etc.)
}}

Reglas:
- Si el usuario dice "hoy gaste 25.000 en almuerzo" -> intent: "expense", amount: 25000, category: "Comida", description: "almuerzo"
- Si dice "+ 10.000" o "+10000 sueldo" o "me pagaron 500k" -> intent: "income", amount: 10000/500000
- Si dice "- 15.000 cafe" -> intent: "expense", amount: 15000
- Si pregunta "¿cuánto tengo?", "saldo", "mis cuentas" -> intent: "check_balance"
- Si pide "resumen", "cómo voy", "balance" -> intent: "summary"
- Las cifras pueden tener puntos o comas o la letra 'k' (ej. 50k = 50000). Limpia el número a float.

Mensaje del usuario: "{text}"
"""

        try:
            async with httpx.AsyncClient(timeout=25.0) as client:
                res = await client.post(
                    url,
                    json={"contents": [{"parts": [{"text": prompt}]}]}
                )
                if res.status_code == 200:
                    raw = res.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
                    # Clean possible markdown wrap
                    if raw.startswith("```"):
                        raw = re.sub(r"^```(?:json)?\s*", "", raw)
                        raw = re.sub(r"\s*```$", "", raw)
                    return json.loads(raw)
        except Exception as e:
            logger.error(f"Error calling Gemini: {e}")

        # Fallback to local heuristic
        return self._heuristic_parse(text)

    def _heuristic_parse(self, text: str) -> dict[str, Any]:
        """Simple regex fallback if Gemini is offline."""
        lower = text.lower().strip()

        if any(w in lower for w in ["cuanto tengo", "saldo", "balance total", "/saldo"]):
            return {"intent": "check_balance"}
        if any(w in lower for w in ["resumen", "informe", "/resumen"]):
            return {"intent": "summary"}

        is_income = lower.startswith("+") or "ingreso" in lower or "me pagaron" in lower or "recibí" in lower or "recibi" in lower
        is_expense = lower.startswith("-") or "gaste" in lower or "gasté" in lower or "pague" in lower or "pagué" in lower or "compre" in lower or "compré" in lower

        # Extract numbers
        cleaned = lower.replace(".", "").replace(",", "")
        match = re.search(r"(\d+(?:\.\d+)?)", cleaned)
        amount = float(match.group(1)) if match else None

        intent = "income" if is_income else ("expense" if is_expense or amount else "unknown")

        return {
            "intent": intent,
            "amount": amount,
            "category": "General",
            "description": text,
            "account": None
        }

    async def handle_message(self, user_id: int | str, chat_id: int | str, text: str):
        # Security check: only respond to allowed user ID if configured
        if self.allowed_user_id and str(user_id) != self.allowed_user_id:
            await self.send_message(
                chat_id,
                "⛔ Acceso restringido. Este bot es de uso personal privado."
            )
            return

        text = text.strip()
        if text in ["/start", "hola", "Hola"]:
            await self.send_message(
                chat_id,
                "👋 <b>¡Hola Andrés! Soy tu asistente financiero inteligente LifeOS.</b>\n\n"
                "Puedo interpretar tus movimientos usando <b>IA de Gemini</b> y registrarlos al instante.\n\n"
                "💡 <b>Ejemplos de lo que puedes decirme:</b>\n"
                "• <i>hoy gasté 25.000 en almuerzo con amigos en Nequi</i>\n"
                "• <i>+ 10.000 sueldo</i>\n"
                "• <i>pagué 12.000 de uber con Bancolombia</i>\n"
                "• <i>- 8000 café</i>\n"
                "• <i>¿cuánto dinero tengo?</i>\n"
                "• <i>resumen de hoy</i>"
            )
            return

        # Query Gemini to understand intention
        data = await self.interpret_with_gemini(text)
        intent = data.get("intent")
        amount = data.get("amount")
        category = data.get("category") or "General"
        description = data.get("description") or text
        account_name = data.get("account")

        async with async_session_factory() as db:
            # Get main user
            res = await db.execute(select(User).limit(1))
            user = res.scalar_one_or_none()
            if not user:
                await self.send_message(chat_id, "⚠️ No se encontró ningún usuario configurado en la base de datos.")
                return

            if intent == "check_balance":
                engine = FinancialEngine(db, user.id)
                total = await engine.get_total_balance()
                accounts = await engine.get_accounts_summary()
                
                lines = [f"📊 <b>Balance Total:</b> ${total:,.0f} COP\n"]
                if accounts:
                    lines.append("<b>Tus Cuentas:</b>")
                    for acc in accounts:
                        lines.append(f"• <b>{acc['name']}:</b> ${acc['balance']:,.0f} COP")
                else:
                    lines.append("<i>Aún no tienes cuentas registradas.</i>")
                
                await self.send_message(chat_id, "\n".join(lines))
                return

            if intent == "summary":
                engine = FinancialEngine(db, user.id)
                summary = await engine.get_today_summary()
                msg = (
                    f"📅 <b>Resumen de Hoy:</b>\n"
                    f"• <b>Ingresos:</b> ${summary.get('total_income', 0):,.0f} COP\n"
                    f"• <b>Gastos:</b> ${summary.get('total_expenses', 0):,.0f} COP\n"
                    f"• <b>Balance Neto:</b> ${summary.get('net_balance', 0):,.0f} COP\n"
                    f"• <b>Movimientos:</b> {summary.get('transaction_count', 0)}"
                )
                await self.send_message(chat_id, msg)
                return

            if intent in ["expense", "income"] and amount and amount > 0:
                dec_amount = Decimal(str(amount))
                # Find matching account
                accounts_res = await db.execute(select(Account).where(Account.user_id == user.id))
                accounts = accounts_res.scalars().all()

                target_account = None
                if account_name and accounts:
                    for acc in accounts:
                        if account_name.lower() in acc.name.lower():
                            target_account = acc
                            break

                if not target_account:
                    if accounts:
                        target_account = accounts[0]
                    else:
                        # Create default account
                        target_account = Account(
                            user_id=user.id,
                            name="Billetera / Nequi",
                            account_type=AccountType.SAVINGS,
                            balance=Decimal("0.0"),
                            currency="COP",
                            include_in_total=True
                        )
                        db.add(target_account)
                        await db.flush()

                # Adjust balance
                if intent == "expense":
                    target_account.balance -= dec_amount
                    tx_type = TransactionType.EXPENSE
                    icon = "💸"
                    type_label = "Gasto"
                else:
                    target_account.balance += dec_amount
                    tx_type = TransactionType.INCOME
                    icon = "💰"
                    type_label = "Ingreso"

                # Record transaction
                transaction = Transaction(
                    user_id=user.id,
                    account_id=target_account.id,
                    amount=dec_amount,
                    transaction_type=tx_type,
                    category=category,
                    description=description,
                    transaction_date=datetime.now(timezone.utc),
                    source="telegram",
                    tags=["telegram"],
                    metadata_extra={"ai_intent": intent, "telegram_user_id": str(user_id)},
                )
                db.add(transaction)
                await db.commit()

                # Compute new total
                engine = FinancialEngine(db, user.id)
                new_total = await engine.get_total_balance()

                reply = (
                    f"✅ <b>{icon} {type_label} registrado con Gemini IA</b>\n\n"
                    f"• <b>Monto:</b> ${amount:,.0f} COP\n"
                    f"• <b>Categoría:</b> {category}\n"
                    f"• <b>Detalle:</b> {description}\n"
                    f"• <b>Cuenta:</b> {target_account.name}\n"
                    f"• <b>Saldo en {target_account.name}:</b> ${target_account.balance:,.0f} COP\n\n"
                    f"📊 <b>Saldo total disponible:</b> ${new_total:,.0f} COP"
                )
                await self.send_message(chat_id, reply)
                return

            # Unknown / couldn't extract
            await self.send_message(
                chat_id,
                "🤔 No logré identificar el monto o tipo de movimiento.\n\n"
                "Intenta con algo como:\n"
                "• <i>hoy gasté 15.000 en transporte</i>\n"
                "• <i>+ 50.000 sueldo</i>\n"
                "• <i>¿cuánto dinero tengo?</i>"
            )


async def run_telegram_polling():
    """Background polling loop for Telegram updates."""
    bot = SmartTelegramBot()
    if not bot.token:
        logger.warning("Telegram Bot Token not configured. Polling disabled.")
        return

    logger.info("Starting Telegram Bot long-polling worker...")
    offset = 0

    async with httpx.AsyncClient(timeout=35.0) as client:
        while True:
            try:
                url = f"{bot.base_url}/getUpdates?offset={offset}&timeout=25"
                res = await client.get(url)
                if res.status_code == 200:
                    updates = res.json().get("result", [])
                    for update in updates:
                        offset = update["update_id"] + 1
                        message = update.get("message")
                        if message and "text" in message:
                            user_id = message["from"]["id"]
                            chat_id = message["chat"]["id"]
                            text = message["text"]
                            asyncio.create_task(bot.handle_message(user_id, chat_id, text))
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Telegram polling error: {e}")
                await asyncio.sleep(5)
