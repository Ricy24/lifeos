from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from datetime import datetime, timezone
import zoneinfo
from typing import Dict, Any

from app.core import deps
from app.models.work_session import WorkSession
from app.models.financial_config import FinancialConfig
from app.domain.work.state import WorkSessionData
from app.domain.forecast.engine import ForecastEngine

router = APIRouter()

@router.get("/summary")
def get_forecast_summary(
    db: Session = Depends(deps.get_db),
    current_user = Depends(deps.get_current_user)
) -> Dict[str, Any]:
    
    # 1. Get user timezone
    config = db.query(FinancialConfig).filter(FinancialConfig.user_id == current_user.id).first()
    tz_str = config.timezone if config else "UTC"
    
    # 2. Get canonical data
    sessions_db = db.query(WorkSession).filter(
        WorkSession.user_id == current_user.id,
        WorkSession.deleted_at.is_(None)
    ).all()
    
    sessions_data = [
        WorkSessionData(
            id=str(s.id),
            start_time=s.start_time,
            end_time=s.end_time,
            duration_minutes=s.duration_minutes,
            income=s.income,
            activity_type=s.activity_type
        )
        for s in sessions_db
    ]
    
    engine = ForecastEngine(tz_str)
    summary = engine.generate_summary(sessions_data, datetime.now(timezone.utc))
    
    # Map to dict
    result = {"models": {}}
    for k, v in summary.models.items():
        result["models"][k] = {
            "forecast_value": v.forecast_value,
            "quality": v.quality.value,
            "sample_size": v.sample_size,
            "missing_observations": v.missing_observations,
            "historical_window_days": v.historical_window_days
        }
        if v.expected_work_minutes is not None:
            result["models"][k]["expected_work_minutes"] = v.expected_work_minutes
        if v.expected_income_per_hour is not None:
            result["models"][k]["expected_income_per_hour"] = v.expected_income_per_hour
            
    return result
