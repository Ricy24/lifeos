"""
LifeOS Finance — Work Engine
"""

from typing import List, Dict
from datetime import datetime

from app.domain.work.state import WorkSessionData, WorkMetrics
from app.domain.work.calculator import WorkCalculator


class WorkEngine:
    """
    Orchestrator for computing work metrics across different timeframes.
    """

    def __init__(self, sessions: List[WorkSessionData]):
        self.sessions = sessions

    def get_metrics_for_period(self, start_time: datetime, end_time: datetime) -> WorkMetrics:
        """
        Returns metrics for sessions that overlap with or occurred within the given period.
        """
        filtered = [
            s for s in self.sessions
            if s.start_time >= start_time and s.start_time <= end_time
        ]
        return WorkCalculator.aggregate_metrics(filtered)

    def get_all_metrics(self, reference_time: datetime) -> Dict[str, WorkMetrics]:
        """
        Calculates all standard time window metrics relative to the reference time.
        """
        windows = {
            "current_day": WorkCalculator.get_current_day_boundary(reference_time),
            "current_week": WorkCalculator.get_current_week_boundary(reference_time),
            "current_month": WorkCalculator.get_current_month_boundary(reference_time),
            "rolling_7_days": WorkCalculator.get_rolling_7_days_boundary(reference_time),
            "rolling_30_days": WorkCalculator.get_rolling_30_days_boundary(reference_time),
        }

        results = {}
        for key, start_time in windows.items():
            results[key] = self.get_metrics_for_period(start_time, reference_time)
            
        return results
