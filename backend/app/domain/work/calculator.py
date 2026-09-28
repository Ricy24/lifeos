"""
LifeOS Finance — Work / Income Calculator
"""

from typing import List, Optional
from datetime import datetime, timezone, timedelta

from app.domain.work.state import WorkSessionData, WorkMetrics


class WorkCalculator:
    """
    Pure deterministic calculator for work metrics.
    """

    @staticmethod
    def calculate_session_income_per_hour(income: int, duration_minutes: Optional[int]) -> Optional[int]:
        """
        Calculate income per hour for a single session.
        Income is in minor units (e.g. 10000 COP).
        Returns rate in minor units, rounded to nearest integer.
        Returns None if duration is 0 or None.
        """
        if not duration_minutes or duration_minutes <= 0:
            return None

        numerator = income * 60
        return (numerator + duration_minutes // 2) // duration_minutes

    @staticmethod
    def aggregate_metrics(sessions: List[WorkSessionData]) -> WorkMetrics:
        """
        Calculate aggregate metrics for a list of work sessions.
        """
        total_income = 0
        total_duration = 0
        active_count = 0
        completed_count = 0

        for session in sessions:
            if session.is_active:
                active_count += 1
            else:
                completed_count += 1
                total_income += session.income
                if session.duration_minutes is not None:
                    total_duration += session.duration_minutes

        return WorkMetrics(
            total_income=total_income,
            total_duration_minutes=total_duration,
            active_sessions_count=active_count,
            completed_sessions_count=completed_count,
        )

    @staticmethod
    def calculate_required_work(target_income: int, metrics: WorkMetrics) -> Optional[int]:
        """
        Calculate required work (in minutes) to reach a target income.
        Uses historical deterministic income rate.
        Returns None if insufficient data (e.g., no completed work duration).
        """
        rate = metrics.income_per_hour
        if rate is None or rate <= 0:
            return None

        # required_minutes = target_income / (rate / 60)
        # = target_income * 60 / rate
        numerator = target_income * 60
        return (numerator + rate // 2) // rate

    @staticmethod
    def get_current_day_boundary(now: datetime) -> datetime:
        """Returns the start of the current day in the provided datetime's timezone."""
        return now.replace(hour=0, minute=0, second=0, microsecond=0)

    @staticmethod
    def get_current_week_boundary(now: datetime) -> datetime:
        """Returns the start of the current week (Monday) in the provided datetime's timezone."""
        day_start = WorkCalculator.get_current_day_boundary(now)
        return day_start - timedelta(days=day_start.weekday())

    @staticmethod
    def get_current_month_boundary(now: datetime) -> datetime:
        """Returns the start of the current calendar month in the provided datetime's timezone."""
        return now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)

    @staticmethod
    def get_rolling_7_days_boundary(now: datetime) -> datetime:
        """Returns the exact boundary 7 days prior to now."""
        return now - timedelta(days=7)

    @staticmethod
    def get_rolling_30_days_boundary(now: datetime) -> datetime:
        """Returns the exact boundary 30 days prior to now."""
        return now - timedelta(days=30)
