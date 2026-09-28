from dataclasses import dataclass
from enum import Enum
from typing import Optional, Dict, Any

class ForecastQuality(Enum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"
    INSUFFICIENT_DATA = "INSUFFICIENT_DATA"

class ForecastModelType(Enum):
    NAIVE = "NAIVE"
    MOVING_AVERAGE_7 = "MOVING_AVERAGE_7"
    MOVING_AVERAGE_14 = "MOVING_AVERAGE_14"
    MOVING_AVERAGE_30 = "MOVING_AVERAGE_30"
    EWMA = "EWMA"
    SEASONALITY = "SEASONALITY"
    WORK_BASED = "WORK_BASED"

@dataclass(frozen=True)
class ForecastResult:
    forecast_value: Optional[int]
    model_type: ForecastModelType
    forecast_period: str
    historical_window_days: int
    sample_size: int
    missing_observations: int
    quality: ForecastQuality
    expected_work_minutes: Optional[int] = None
    expected_income_per_hour: Optional[int] = None

@dataclass(frozen=True)
class ForecastSummary:
    models: Dict[str, ForecastResult]
