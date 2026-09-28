import pytest
from datetime import datetime, timedelta, timezone
from app.domain.work.state import WorkSessionData
from app.domain.forecast.engine import ForecastEngine
from app.domain.forecast.models import ForecastModelType, ForecastQuality
import zoneinfo

@pytest.fixture
def tz():
    return "America/Bogota"

@pytest.fixture
def engine(tz):
    return ForecastEngine(tz)

def create_session(days_ago: int, income: int, duration: int = 60, is_active=False) -> WorkSessionData:
    tz = zoneinfo.ZoneInfo("America/Bogota")
    now = datetime.now(timezone.utc).astimezone(tz)
    # Start of day minus days_ago
    start_of_day = datetime(now.year, now.month, now.day, 10, 0, 0, tzinfo=tz) - timedelta(days=days_ago)
    return WorkSessionData(
        id=str(days_ago),
        start_time=start_of_day,
        end_time=None if is_active else start_of_day + timedelta(minutes=duration),
        duration_minutes=duration,
        income=income,
        activity_type="dev"
    )

def test_forecast_naive(engine):
    now = datetime.now(timezone.utc)
    sessions = [create_session(1, 100000), create_session(3, 150000)]
    res = engine.forecast_naive(sessions, now)
    assert res.forecast_value == 100000
    assert res.quality == ForecastQuality.LOW

def test_forecast_moving_average_7_insufficient_data(engine):
    now = datetime.now(timezone.utc)
    # Only 3 days of history
    sessions = [create_session(1, 1000), create_session(2, 1000), create_session(3, 1000)]
    res = engine.forecast_moving_average(sessions, now, 7)
    assert res.quality == ForecastQuality.INSUFFICIENT_DATA
    assert res.forecast_value is None

def test_forecast_moving_average_7(engine):
    now = datetime.now(timezone.utc)
    # 7 days of history
    sessions = [create_session(i, 100000) for i in range(1, 8)]
    res = engine.forecast_moving_average(sessions, now, 7)
    assert res.forecast_value == 100000
    assert res.quality == ForecastQuality.HIGH

def test_forecast_work_based(engine):
    now = datetime.now(timezone.utc)
    # 7 days of history, varying durations
    sessions = [
        create_session(1, 100000, 60), 
        create_session(2, 50000, 30),  
        create_session(3, 0, 0),       
        create_session(4, 100000, 60),
        create_session(5, 100000, 60),
        create_session(6, 100000, 60),
        create_session(7, 100000, 60),
    ]
    res = engine.forecast_work_based(sessions, now)
    assert res.quality == ForecastQuality.MEDIUM
    
def test_forecast_zero_mean(engine):
    now = datetime.now(timezone.utc)
    sessions = [create_session(i, 0, 60) for i in range(1, 8)]
    res = engine.forecast_moving_average(sessions, now, 7)
    assert res.forecast_value == 0
    assert res.quality == ForecastQuality.HIGH
