"""
Tests for Work / Income Engine
"""

import pytest
from datetime import datetime, timezone, timedelta
from app.domain.work.state import WorkSessionData, WorkMetrics
from app.domain.work.calculator import WorkCalculator
from app.domain.work.work_engine import WorkEngine


def make_session(
    income: int,
    duration: int = None,
    start: datetime = None,
    end: datetime = None
) -> WorkSessionData:
    start_time = start or datetime.now(timezone.utc)
    return WorkSessionData(
        id="test-id",
        start_time=start_time,
        end_time=end,
        duration_minutes=duration,
        income=income,
        activity_type="dev"
    )

class TestWorkCalculator:
    
    # 1. Arithmetic Tests
    
    @pytest.mark.parametrize("income,duration,expected_rate", [
        (10000, 60, 10000),      # 1 hour exactly
        (10000, 30, 20000),      # half hour
        (10000, 90, 6667),       # 1.5 hours: 10000 * 60 / 90 = 6666.666... -> 6667
        (10000, 120, 5000),      # 2 hours
        (10000, 0, None),        # zero duration
        (0, 60, 0),              # zero income
        (25000, 90, 16667),      # from user spec
        (50000, 120, 25000),     # from user spec
        (10000, 30, 20000),      # from user spec
    ])
    def test_calculate_session_income_per_hour(self, income, duration, expected_rate):
        rate = WorkCalculator.calculate_session_income_per_hour(income, duration)
        assert rate == expected_rate

    # 2. Aggregation Tests
    
    def test_aggregate_equal_duration(self):
        s1 = make_session(income=10000, duration=60, end=datetime.now())
        s2 = make_session(income=20000, duration=60, end=datetime.now())
        metrics = WorkCalculator.aggregate_metrics([s1, s2])
        assert metrics.total_income == 30000
        assert metrics.total_duration_minutes == 120
        assert metrics.income_per_hour == 15000

    def test_aggregate_unequal_duration(self):
        # 10k for 1 hour, 10k for 10 hours -> 20k for 11 hours = 20000 * 60 / 660 = 1818.18...
        s1 = make_session(income=10000, duration=60, end=datetime.now())
        s2 = make_session(income=10000, duration=600, end=datetime.now())
        metrics = WorkCalculator.aggregate_metrics([s1, s2])
        assert metrics.total_income == 20000
        assert metrics.total_duration_minutes == 660
        assert metrics.income_per_hour == 1818  # 20000 * 60 / 660 rounded

    def test_aggregate_active_and_completed(self):
        s1 = make_session(income=10000, duration=60, end=datetime.now()) # completed
        s2 = make_session(income=0, duration=None, end=None)             # active
        metrics = WorkCalculator.aggregate_metrics([s1, s2])
        assert metrics.active_sessions_count == 1
        assert metrics.completed_sessions_count == 1
        assert metrics.income_per_hour == 10000

    # 3. Target Math
    def test_required_work(self):
        metrics = WorkMetrics(total_income=10000, total_duration_minutes=60, active_sessions_count=0, completed_sessions_count=1)
        req = WorkCalculator.calculate_required_work(50000, metrics)
        # target 50000. rate is 10000. 50000 / (10000/60) = 300 minutes (5 hours)
        assert req == 300

    def test_required_work_insufficient_data(self):
        metrics = WorkMetrics(total_income=0, total_duration_minutes=0, active_sessions_count=0, completed_sessions_count=0)
        req = WorkCalculator.calculate_required_work(50000, metrics)
        assert req is None

    # 4. Boundaries
    def test_boundaries(self):
        now = datetime(2026, 9, 16, 12, 0, 0, tzinfo=timezone.utc)
        
        day_boundary = WorkCalculator.get_current_day_boundary(now)
        assert day_boundary == datetime(2026, 9, 16, 0, 0, 0, tzinfo=timezone.utc)
        
        week_boundary = WorkCalculator.get_current_week_boundary(now)
        # 2026-09-16 is a Wednesday (weekday 2), so Monday is 2026-09-14
        assert week_boundary == datetime(2026, 9, 14, 0, 0, 0, tzinfo=timezone.utc)
        
        month_boundary = WorkCalculator.get_current_month_boundary(now)
        assert month_boundary == datetime(2026, 9, 1, 0, 0, 0, tzinfo=timezone.utc)
        
        r7 = WorkCalculator.get_rolling_7_days_boundary(now)
        assert r7 == datetime(2026, 9, 9, 12, 0, 0, tzinfo=timezone.utc)
        
        r30 = WorkCalculator.get_rolling_30_days_boundary(now)
        assert r30 == datetime(2026, 8, 17, 12, 0, 0, tzinfo=timezone.utc)

class TestWorkEngine:
    
    def test_metrics_for_period(self):
        base_time = datetime(2026, 9, 16, 12, 0, 0, tzinfo=timezone.utc)
        s1 = make_session(income=10000, duration=60, start=base_time - timedelta(days=2), end=base_time - timedelta(days=2) + timedelta(hours=1))
        s2 = make_session(income=20000, duration=60, start=base_time - timedelta(hours=1), end=base_time)
        
        engine = WorkEngine([s1, s2])
        
        # Only s2 is in today
        metrics_today = engine.get_all_metrics(base_time)["current_day"]
        assert metrics_today.total_income == 20000
        
        # Both are in the current week (if Mon was before s1)
        # 2026-09-16 is Wed. s1 is 2026-09-14 (Mon). So both are in current week.
        metrics_week = engine.get_all_metrics(base_time)["current_week"]
        assert metrics_week.total_income == 30000
