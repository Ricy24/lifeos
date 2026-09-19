"""
LifeOS Finance — Financial Engine
Deterministic financial calculations. NO AI dependency.
All calculations here are pure math — the AI layer only interprets/explains.
"""

from datetime import datetime, timedelta, timezone
from typing import List, Dict, Optional, Tuple

from sqlalchemy import select, func, and_, extract
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.transaction import Transaction, TransactionType
from app.models.account import Account
from app.models.debt import Debt, DebtStatus
from app.models.goal import Goal, GoalStatus
from app.models.financial_config import FinancialConfig


class FinancialEngine:
    """
    Core financial calculation engine.
    All methods are deterministic — no AI, no guessing.
    """

    def __init__(self, db: AsyncSession, user_id: str):
        self.db = db
        self.user_id = user_id

    # ─── Balance ──────────────────────────────────────────────

    async def get_total_balance(self) -> float:
        """Sum of all active account balances."""
        result = await self.db.execute(
            select(func.coalesce(func.sum(Account.balance), 0.0))
            .where(
                and_(
                    Account.user_id == self.user_id,
                    Account.is_active == True,
                    Account.include_in_total == True,
                )
            )
        )
        return float(result.scalar() or 0.0)

    async def get_accounts_summary(self) -> List[dict]:
        """Get all active accounts with balances."""
        result = await self.db.execute(
            select(Account)
            .where(
                and_(
                    Account.user_id == self.user_id,
                    Account.is_active == True,
                )
            )
            .order_by(Account.name)
        )
        accounts = result.scalars().all()
        return [
            {
                "id": a.id,
                "name": a.name,
                "type": a.account_type,
                "balance": float(a.balance),
                "currency": a.currency,
                "color": a.color,
                "icon": a.icon,
            }
            for a in accounts
        ]

    # ─── Period Calculations ──────────────────────────────────

    async def get_period_summary(
        self,
        start: datetime,
        end: datetime,
    ) -> dict:
        """Calculate income/expenses for a date range."""
        # Income
        income_result = await self.db.execute(
            select(
                func.coalesce(func.sum(Transaction.amount), 0.0),
                func.count(Transaction.id),
            ).where(
                and_(
                    Transaction.user_id == self.user_id,
                    Transaction.transaction_type == TransactionType.INCOME,
                    Transaction.transaction_date >= start,
                    Transaction.transaction_date < end,
                )
            )
        )
        income_row = income_result.one()
        total_income = float(income_row[0])
        income_count = int(income_row[1])

        # Expenses
        expense_result = await self.db.execute(
            select(
                func.coalesce(func.sum(Transaction.amount), 0.0),
                func.count(Transaction.id),
            ).where(
                and_(
                    Transaction.user_id == self.user_id,
                    Transaction.transaction_type == TransactionType.EXPENSE,
                    Transaction.transaction_date >= start,
                    Transaction.transaction_date < end,
                )
            )
        )
        expense_row = expense_result.one()
        total_expenses = float(expense_row[0])
        expense_count = int(expense_row[1])

        return {
            "total_income": total_income,
            "total_expenses": total_expenses,
            "net": total_income - total_expenses,
            "transaction_count": income_count + expense_count,
        }

    async def get_today_summary(self) -> dict:
        now = datetime.now(timezone.utc)
        start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        end = start + timedelta(days=1)
        return await self.get_period_summary(start, end)

    async def get_week_summary(self) -> dict:
        now = datetime.now(timezone.utc)
        start = now - timedelta(days=now.weekday())
        start = start.replace(hour=0, minute=0, second=0, microsecond=0)
        end = start + timedelta(days=7)
        return await self.get_period_summary(start, end)

    async def get_month_summary(self) -> dict:
        now = datetime.now(timezone.utc)
        start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        if now.month == 12:
            end = start.replace(year=now.year + 1, month=1)
        else:
            end = start.replace(month=now.month + 1)
        return await self.get_period_summary(start, end)

    # ─── Targets & Work ──────────────────────────────────────

    async def get_financial_config(self) -> Optional[FinancialConfig]:
        result = await self.db.execute(
            select(FinancialConfig)
            .where(FinancialConfig.user_id == self.user_id)
        )
        return result.scalar_one_or_none()

    async def get_daily_target_progress(self) -> dict:
        """How much has been earned today vs target."""
        config = await self.get_financial_config()
        if not config:
            return {
                "target": 0,
                "earned": 0,
                "remaining": 0,
                "percentage": 0,
                "hours_needed": 0,
            }

        today = await self.get_today_summary()
        earned = float(today["total_income"])
        target = float(config.daily_target)
        hourly_rate = float(config.hourly_rate)
        remaining = max(0.0, target - earned)
        percentage = min(100.0, (earned / target * 100)) if target > 0 else 0.0
        hours_needed = remaining / hourly_rate if hourly_rate > 0 else 0.0

        return {
            "target": target,
            "earned": earned,
            "remaining": remaining,
            "percentage": round(percentage, 1),
            "hours_needed": round(hours_needed, 1),
        }

    def calculate_work_hours(
        self,
        amount: float,
        hourly_rate: float,
        hours_per_day: float = 8.0,
    ) -> dict:
        """Calculate work needed for a target amount."""
        if hourly_rate <= 0:
            return {"hours": 0, "days": 0}
        hours = amount / hourly_rate
        days = hours / hours_per_day if hours_per_day > 0 else 0
        return {
            "hours": round(hours, 1),
            "days": round(days, 1),
        }

    # ─── Category Breakdown ──────────────────────────────────

    async def get_expenses_by_category(
        self,
        start: datetime,
        end: datetime,
    ) -> List[dict]:
        """Breakdown of expenses by category for a period."""
        result = await self.db.execute(
            select(
                Transaction.category,
                func.sum(Transaction.amount).label("total"),
                func.count(Transaction.id).label("count"),
            )
            .where(
                and_(
                    Transaction.user_id == self.user_id,
                    Transaction.transaction_type == TransactionType.EXPENSE,
                    Transaction.transaction_date >= start,
                    Transaction.transaction_date < end,
                )
            )
            .group_by(Transaction.category)
            .order_by(func.sum(Transaction.amount).desc())
        )
        rows = result.all()
        total_expenses = sum(float(r[1]) for r in rows)

        return [
            {
                "category": r[0],
                "total": float(r[1]),
                "percentage": round(float(r[1]) / total_expenses * 100, 1) if total_expenses > 0 else 0,
                "count": int(r[2]),
            }
            for r in rows
        ]

    async def get_income_by_category(
        self,
        start: datetime,
        end: datetime,
    ) -> List[dict]:
        """Breakdown of income by category for a period."""
        result = await self.db.execute(
            select(
                Transaction.category,
                func.sum(Transaction.amount).label("total"),
                func.count(Transaction.id).label("count"),
            )
            .where(
                and_(
                    Transaction.user_id == self.user_id,
                    Transaction.transaction_type == TransactionType.INCOME,
                    Transaction.transaction_date >= start,
                    Transaction.transaction_date < end,
                )
            )
            .group_by(Transaction.category)
            .order_by(func.sum(Transaction.amount).desc())
        )
        rows = result.all()
        total_income = sum(float(r[1]) for r in rows)

        return [
            {
                "category": r[0],
                "total": float(r[1]),
                "percentage": round(float(r[1]) / total_income * 100, 1) if total_income > 0 else 0,
                "count": int(r[2]),
            }
            for r in rows
        ]

    # ─── Debts ────────────────────────────────────────────────

    async def get_debt_summary(self) -> dict:
        """Total debt overview."""
        result = await self.db.execute(
            select(Debt)
            .where(
                and_(
                    Debt.user_id == self.user_id,
                    Debt.status.in_([DebtStatus.PENDING, DebtStatus.PARTIALLY_PAID, DebtStatus.OVERDUE]),
                )
            )
        )
        debts = result.scalars().all()

        total = sum(float(d.remaining_amount) for d in debts)
        urgent = sum(
            1 for d in debts
            if d.priority <= 2 or d.status == DebtStatus.OVERDUE
        )

        return {
            "total_debt": float(total),
            "debt_count": len(debts),
            "urgent_debts": urgent,
        }

    # ─── Goals ────────────────────────────────────────────────

    async def get_goals_summary(self) -> dict:
        """Active goals overview."""
        result = await self.db.execute(
            select(Goal)
            .where(
                and_(
                    Goal.user_id == self.user_id,
                    Goal.status == GoalStatus.ACTIVE,
                )
            )
            .order_by(Goal.priority)
        )
        goals = result.scalars().all()

        primary = None
        if goals:
            g = goals[0]
            primary = {
                "id": g.id,
                "name": g.name,
                "target_amount": float(g.target_amount),
                "current_amount": float(g.current_amount),
                "progress_percentage": float(g.progress_percentage),
                "remaining_amount": float(g.remaining_amount),
            }

        return {
            "active_goals": len(goals),
            "primary_goal": primary,
        }

    # ─── Dashboard ────────────────────────────────────

    async def get_dashboard(self) -> dict:
        """Assemble complete dashboard data."""
        total_balance = await self.get_total_balance()
        accounts = await self.get_accounts_summary()
        today = await self.get_today_summary()
        week = await self.get_week_summary()
        month = await self.get_month_summary()
        target_progress = await self.get_daily_target_progress()
        debt_summary = await self.get_debt_summary()
        goals_summary = await self.get_goals_summary()
        config = await self.get_financial_config()

        # Recent transactions
        recent_result = await self.db.execute(
            select(Transaction)
            .where(Transaction.user_id == self.user_id)
            .order_by(Transaction.transaction_date.desc())
            .limit(10)
        )
        recent = recent_result.scalars().all()
        recent_list = [
            {
                "id": t.id,
                "amount": float(t.amount),
                "type": t.transaction_type,
                "category": t.category,
                "description": t.description,
                "date": t.transaction_date.isoformat(),
            }
            for t in recent
        ]

        return {
            "total_balance": total_balance,
            "net_worth": total_balance - debt_summary["total_debt"],
            "accounts": accounts,
            "today": today,
            "this_week": week,
            "this_month": month,
            "daily_target": target_progress.get("target", 0),
            "weekly_target": float(config.weekly_target) if config else 0.0,
            "monthly_target": float(config.monthly_target) if config else 0.0,
            "daily_progress_percentage": target_progress.get("percentage", 0),
            "daily_remaining": target_progress.get("remaining", 0),
            "hourly_rate": float(config.hourly_rate) if config else 0.0,
            "hours_needed_today": target_progress.get("hours_needed", 0),
            "total_debt": debt_summary["total_debt"],
            "urgent_debts": debt_summary["urgent_debts"],
            "active_goals": goals_summary["active_goals"],
            "primary_goal": goals_summary["primary_goal"],
            "recent_transactions": recent_list,
            "generated_at": datetime.now(timezone.utc).isoformat(),
        }

    # ─── Context for AI ───────────────────────────────────────

    async def get_ai_context(self) -> dict:
        """
        Build a structured financial context for the AI.
        This is a SUMMARY — not the entire database.
        """
        dashboard = await self.get_dashboard()
        config = await self.get_financial_config()

        now = datetime.now(timezone.utc)
        month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        month_end = (month_start + timedelta(days=32)).replace(day=1)

        expenses_by_cat = await self.get_expenses_by_category(month_start, month_end)
        income_by_cat = await self.get_income_by_category(month_start, month_end)

        return {
            "balance": dashboard["total_balance"],
            "net_worth": dashboard["net_worth"],
            "today_income": dashboard["today"]["total_income"],
            "today_expenses": dashboard["today"]["total_expenses"],
            "week_income": dashboard["this_week"]["total_income"],
            "week_expenses": dashboard["this_week"]["total_expenses"],
            "month_income": dashboard["this_month"]["total_income"],
            "month_expenses": dashboard["this_month"]["total_expenses"],
            "daily_target": dashboard["daily_target"],
            "daily_remaining": dashboard["daily_remaining"],
            "hourly_rate": dashboard["hourly_rate"],
            "hours_needed_today": dashboard["hours_needed_today"],
            "total_debt": dashboard["total_debt"],
            "urgent_debts": dashboard["urgent_debts"],
            "active_goals": dashboard["active_goals"],
            "primary_goal": dashboard["primary_goal"],
            "top_expense_categories": expenses_by_cat[:5],
            "top_income_categories": income_by_cat[:3],
            "currency": config.currency if config else "COP",
        }
