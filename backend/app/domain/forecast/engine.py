from datetime import datetime, timedelta
import zoneinfo
from typing import List, Dict, Tuple, Optional
from app.domain.work.state import WorkSessionData
from .models import ForecastResult, ForecastModelType, ForecastQuality, ForecastSummary
from .math_utils import determine_quality, calculate_ewma

class ForecastEngine:
    def __init__(self, timezone_str: str):
        self.tz = zoneinfo.ZoneInfo(timezone_str)

    def _get_start_of_day(self, dt: datetime) -> datetime:
        dt_tz = dt.astimezone(self.tz)
        return datetime(dt_tz.year, dt_tz.month, dt_tz.day, tzinfo=self.tz)

    def _aggregate_daily_stats(self, sessions: List[WorkSessionData], now: datetime, window_days: int) -> Tuple[List[int], List[int], int]:
        end_boundary = self._get_start_of_day(now)
        start_boundary = end_boundary - timedelta(days=window_days)
        
        daily_sums = {i: {"income": 0, "duration": 0, "count": 0} for i in range(window_days)}
        
        for s in sessions:
            if not s.is_active and s.start_time >= start_boundary and s.start_time < end_boundary:
                s_start_tz = s.start_time.astimezone(self.tz)
                day_offset = (self._get_start_of_day(s_start_tz) - start_boundary).days
                if 0 <= day_offset < window_days:
                    daily_sums[day_offset]["income"] += s.income
                    if s.duration_minutes:
                        daily_sums[day_offset]["duration"] += s.duration_minutes
                    daily_sums[day_offset]["count"] += 1
                    
        timeline_income = []
        timeline_duration = []
        missing = 0
        for i in range(window_days):
            if daily_sums[i]["count"] == 0:
                missing += 1
            timeline_income.append(daily_sums[i]["income"])
            timeline_duration.append(daily_sums[i]["duration"])
            
        return timeline_income, timeline_duration, missing

    def forecast_naive(self, sessions: List[WorkSessionData], now: datetime) -> ForecastResult:
        end_boundary = self._get_start_of_day(now)
        
        valid_sessions = [s for s in sessions if not s.is_active and s.start_time < end_boundary]
        if not valid_sessions:
            return ForecastResult(None, ForecastModelType.NAIVE, "next_day", 0, 0, 0, ForecastQuality.INSUFFICIENT_DATA)
            
        days_map = {}
        for s in valid_sessions:
            s_tz = s.start_time.astimezone(self.tz)
            day_start = self._get_start_of_day(s_tz)
            if day_start not in days_map:
                days_map[day_start] = 0
            days_map[day_start] += s.income
            
        most_recent_day = max(days_map.keys())
        val = days_map[most_recent_day]
        days_ago = (end_boundary - most_recent_day).days
        
        return ForecastResult(val, ForecastModelType.NAIVE, "next_day", days_ago, 1, days_ago - 1, ForecastQuality.LOW)

    def forecast_moving_average(self, sessions: List[WorkSessionData], now: datetime, window: int) -> ForecastResult:
        timeline_income, _, missing = self._aggregate_daily_stats(sessions, now, window)
        model_type = ForecastModelType[f"MOVING_AVERAGE_{window}"]
        
        valid_sessions = [s for s in sessions if not s.is_active and s.start_time < self._get_start_of_day(now)]
        if not valid_sessions:
            return ForecastResult(None, model_type, "next_day", window, 0, window, ForecastQuality.INSUFFICIENT_DATA)
            
        first_session = min(valid_sessions, key=lambda s: s.start_time)
        days_since_first = (self._get_start_of_day(now) - self._get_start_of_day(first_session.start_time)).days
        
        sample_size = window - missing
        
        if days_since_first < window - 1:
            return ForecastResult(None, model_type, "next_day", window, sample_size, missing, ForecastQuality.INSUFFICIENT_DATA)
            
        mean_val = sum(timeline_income) // window
        qual = determine_quality(timeline_income)
        
        return ForecastResult(mean_val, model_type, "next_day", window, sample_size, missing, qual)

    def forecast_ewma(self, sessions: List[WorkSessionData], now: datetime) -> ForecastResult:
        # Define a reasonably large window to get good decay, e.g., 30 days
        window = 30
        timeline_income, _, missing = self._aggregate_daily_stats(sessions, now, window)
        
        valid_sessions = [s for s in sessions if not s.is_active and s.start_time < self._get_start_of_day(now)]
        if not valid_sessions:
            return ForecastResult(None, ForecastModelType.EWMA, "next_day", window, 0, window, ForecastQuality.INSUFFICIENT_DATA)
            
        first_session = min(valid_sessions, key=lambda s: s.start_time)
        days_since_first = (self._get_start_of_day(now) - self._get_start_of_day(first_session.start_time)).days
        
        # Determine how much of the timeline is valid (from first session)
        valid_timeline_length = min(window, days_since_first)
        
        if valid_timeline_length < 3:
            return ForecastResult(None, ForecastModelType.EWMA, "next_day", window, 0, missing, ForecastQuality.INSUFFICIENT_DATA)
            
        # Extract the valid part of the timeline
        valid_timeline = timeline_income[-valid_timeline_length:]
        
        ewma_val = calculate_ewma(valid_timeline)
        qual = determine_quality(valid_timeline)
        
        sample_size = len([x for x in valid_timeline if x > 0]) # rough sample size, actually it's days with sessions
        
        return ForecastResult(ewma_val, ForecastModelType.EWMA, "next_day", valid_timeline_length, valid_timeline_length, window - valid_timeline_length, qual)

    def forecast_seasonality(self, sessions: List[WorkSessionData], now: datetime) -> ForecastResult:
        end_boundary = self._get_start_of_day(now)
        target_weekday = end_boundary.weekday()
        
        valid_sessions = [s for s in sessions if not s.is_active and s.start_time < end_boundary]
        days_map = {}
        for s in valid_sessions:
            s_tz = s.start_time.astimezone(self.tz)
            day_start = self._get_start_of_day(s_tz)
            if day_start.weekday() == target_weekday:
                if day_start not in days_map:
                    days_map[day_start] = 0
                days_map[day_start] += s.income
                
        sample_size = len(days_map)
        if sample_size < 3:
            return ForecastResult(None, ForecastModelType.SEASONALITY, "next_day", 0, sample_size, 0, ForecastQuality.INSUFFICIENT_DATA)
            
        values = list(days_map.values())
        mean_val = sum(values) // sample_size
        qual = determine_quality(values)
        
        # Calculate timeline days as days since first occurrence of this weekday
        first_day = min(days_map.keys())
        timeline_days = (end_boundary - first_day).days
        
        return ForecastResult(mean_val, ForecastModelType.SEASONALITY, "next_day", timeline_days, sample_size, 0, qual)

    def forecast_work_based(self, sessions: List[WorkSessionData], now: datetime) -> ForecastResult:
        window = 7
        timeline_income, timeline_duration, missing = self._aggregate_daily_stats(sessions, now, window)
        
        valid_sessions = [s for s in sessions if not s.is_active and s.start_time < self._get_start_of_day(now)]
        if not valid_sessions:
            return ForecastResult(None, ForecastModelType.WORK_BASED, "next_day", window, 0, window, ForecastQuality.INSUFFICIENT_DATA)
            
        first_session = min(valid_sessions, key=lambda s: s.start_time)
        days_since_first = (self._get_start_of_day(now) - self._get_start_of_day(first_session.start_time)).days
        
        sample_size = window - missing
        
        if days_since_first < window - 1:
            return ForecastResult(None, ForecastModelType.WORK_BASED, "next_day", window, sample_size, missing, ForecastQuality.INSUFFICIENT_DATA)
            
        expected_work_minutes = sum(timeline_duration) // window
        
        # Compute expected income per hour across the whole window exactly
        total_window_income = sum(timeline_income)
        total_window_duration = sum(timeline_duration)
        
        if total_window_duration == 0:
            return ForecastResult(None, ForecastModelType.WORK_BASED, "next_day", window, sample_size, missing, ForecastQuality.INSUFFICIENT_DATA)
            
        numerator = total_window_income * 60
        expected_income_per_hour = (numerator + total_window_duration // 2) // total_window_duration
        
        forecast_value = (expected_work_minutes * expected_income_per_hour + 30) // 60
        
        # Quality can be derived from the income variance
        qual = determine_quality(timeline_income)
        
        return ForecastResult(
            forecast_value=forecast_value,
            model_type=ForecastModelType.WORK_BASED,
            forecast_period="next_day",
            historical_window_days=window,
            sample_size=sample_size,
            missing_observations=missing,
            quality=qual,
            expected_work_minutes=expected_work_minutes,
            expected_income_per_hour=expected_income_per_hour
        )

    def generate_summary(self, sessions: List[WorkSessionData], now: datetime) -> ForecastSummary:
        models = {}
        models["naive"] = self.forecast_naive(sessions, now)
        models["moving_average_7"] = self.forecast_moving_average(sessions, now, 7)
        models["moving_average_14"] = self.forecast_moving_average(sessions, now, 14)
        models["moving_average_30"] = self.forecast_moving_average(sessions, now, 30)
        models["ewma"] = self.forecast_ewma(sessions, now)
        models["seasonality"] = self.forecast_seasonality(sessions, now)
        models["work_based"] = self.forecast_work_based(sessions, now)
        
        return ForecastSummary(models=models)
