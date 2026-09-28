"""
LifeOS Finance — Financial Application Service
Orchestrates data retrieval from SQLAlchemy async models,
delegates calculations to FinancialTruthEngine, and optionally records snapshots.
"""

from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional
import uuid

from sqlalchemy import select, func, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.domain.finance.state import (
    CanonicalFinancialState,
    FinancialHealthAssessment,
    ProtectedCashBreakdown,
)
from app.domain.finance.truth_engine import FinancialTruthEngine
from app.models.account import Account
from app.models.debt import Debt, DebtPayment, DebtStatus
from app.models.financial_config import FinancialConfig
from app.models.financial_snapshot import FinancialSnapshot
from app.models.goal import Goal, GoalStatus
from app.models.transaction import Transaction, TransactionType


class FinancialTruthService:
    """
    Application service bridging API routers and the pure FinancialTruthEngine.
    """

    def __init__(self, db: AsyncSession, user_id: str):
        self.db = db
        self.user_id = user_id

    async def get_canonical_state(
        self,
        as_of: Optional[datetime] = None,
    ) -> CanonicalFinancialState:
        """Fetch all necessary records and compute the canonical state."""
        now = as_of or datetime.now(timezone.utc)

        # 1. Accounts
        accounts_res = await self.db.execute(
            select(Account).where(
                and_(Account.user_id == self.user_id, Account.is_active == True)
            )
        )
        accounts = [
            {
                "id": a.id,
                "name": a.name,
                "account_type": a.account_type,
                "balance": a.balance,
                "include_in_total": a.include_in_total,
                "is_active": a.is_active,
            }
            for a in accounts_res.scalars().all()
        ]

        # 2. Debts
        debts_res = await self.db.execute(
            select(Debt).where(
                and_(
                    Debt.user_id == self.user_id,
                    Debt.status.in_([DebtStatus.PENDING, DebtStatus.PARTIALLY_PAID, DebtStatus.OVERDUE]),
                )
            )
        )
        debts = [
            {
                "id": d.id,
                "person_or_entity": d.person_or_entity,
                "debt_type": d.debt_type.value if hasattr(d.debt_type, "value") else d.debt_type,
                "original_amount": d.original_amount,
                "remaining_amount": d.remaining_amount,
                "priority": d.priority,
                "status": d.status.value if hasattr(d.status, "value") else d.status,
                "due_date": d.due_date,
            }
            for d in debts_res.scalars().all()
        ]

        # 3. Monthly window for income/expenses
        month_start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        if now.month == 12:
            month_end = month_start.replace(year=now.year + 1, month=1)
        else:
            month_end = month_start.replace(month=now.month + 1)

        tx_res = await self.db.execute(
            select(
                Transaction.transaction_type,
                Transaction.category,
                func.sum(Transaction.amount).label("total_amount"),
            )
            .where(
                and_(
                    Transaction.user_id == self.user_id,
                    Transaction.transaction_date >= month_start,
                    Transaction.transaction_date < month_end,
                )
            )
            .group_by(Transaction.transaction_type, Transaction.category)
        )
        tx_rows = tx_res.all()

        monthly_income = 0.0
        monthly_expenses = 0.0
        monthly_debt_payments = 0.0

        for r in tx_rows:
            tx_type = r[0]
            cat = str(r[1]).lower()
            amt = float(r[2] or 0.0)
            if tx_type == TransactionType.INCOME or tx_type == "income":
                monthly_income += amt
            elif tx_type == TransactionType.EXPENSE or tx_type == "expense":
                monthly_expenses += amt
                if cat == "debt_payment":
                    monthly_debt_payments += amt

        # 4. Recurring obligations
        recurring_res = await self.db.execute(
            select(Transaction).where(
                and_(
                    Transaction.user_id == self.user_id,
                    Transaction.transaction_type == TransactionType.EXPENSE,
                    Transaction.is_recurring == True,
                )
            )
        )
        recurring_expenses = [
            {
                "id": t.id,
                "amount": t.amount,
                "category": t.category,
                "description": t.description,
                "recurring_pattern": t.recurring_pattern or "monthly",
            }
            for t in recurring_res.scalars().all()
        ]

        # 5. Active Goals
        goals_res = await self.db.execute(
            select(Goal).where(
                and_(Goal.user_id == self.user_id, Goal.status == GoalStatus.ACTIVE)
            )
        )
        active_goals = [
            {
                "id": g.id,
                "name": g.name,
                "target_amount": g.target_amount,
                "current_amount": g.current_amount,
                "category": g.category.value if hasattr(g.category, "value") else g.category,
                "priority": g.priority,
                "status": g.status.value if hasattr(g.status, "value") else g.status,
            }
            for g in goals_res.scalars().all()
        ]

        # 6. Financial Config
        config_res = await self.db.execute(
            select(FinancialConfig).where(FinancialConfig.user_id == self.user_id)
        )
        config = config_res.scalar_one_or_none()
        emergency_minimum = 0.0
        partner_percentage = 0.0
        if config:
            # Check if custom settings are stored in categories or default
            emergency_minimum = float(getattr(config, "emergency_minimum", 0.0) or 0.0)
            partner_percentage = float(getattr(config, "partner_percentage", 0.0) or 0.0)

        # Delegate to pure domain engine
        return FinancialTruthEngine.compute_state(
            accounts=accounts,
            debts=debts,
            recurring_expenses=recurring_expenses,
            monthly_income=monthly_income,
            monthly_expenses=monthly_expenses,
            monthly_debt_payments=monthly_debt_payments,
            emergency_minimum=emergency_minimum,
            partner_percentage=partner_percentage,
            active_goals=active_goals,
            as_of=now,
        )

    async def create_snapshot(
        self,
        snapshot_type: str = "daily",
    ) -> FinancialSnapshot:
        """
        Record a snapshot from the current canonical financial state.
        This is an aggregate cache and NOT an authoritative source of truth.
        """
        state = await self.get_canonical_state()

        snapshot = FinancialSnapshot(
            id=str(uuid.uuid4()),
            user_id=self.user_id,
            snapshot_date=state.timestamp,
            snapshot_type=snapshot_type,
            available_cash=state.available_cash,
            total_assets=state.total_assets,
            total_liabilities=state.total_liabilities,
            net_worth=state.net_worth,
            monthly_income=state.monthly_income,
            monthly_expenses=state.monthly_expenses,
            monthly_obligations=state.monthly_obligations,
            protected_cash=state.protected_cash,
            free_cash=state.free_cash,
            goal_progress=state.goal_progress,
            debt_pressure=state.debt_pressure,
            obligation_coverage=state.obligation_coverage,
            financial_health_score=state.financial_health_score,
            financial_health_status=state.financial_health_status,
            indicators=state.health_assessment.indicators.to_dict(),
            metadata_extra={
                "protected_cash_breakdown": state.protected_cash_breakdown.to_dict(),
                "explanations": state.health_assessment.explanations,
            },
        )
        self.db.add(snapshot)
        await self.db.commit()
        await self.db.refresh(snapshot)
        return snapshot

    async def get_latest_snapshot(self) -> Optional[FinancialSnapshot]:
        result = await self.db.execute(
            select(FinancialSnapshot)
            .where(FinancialSnapshot.user_id == self.user_id)
            .order_by(FinancialSnapshot.snapshot_date.desc())
            .limit(1)
        )
        return result.scalar_one_or_none()
