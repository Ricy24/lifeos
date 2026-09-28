"""
LifeOS Finance — Work API
"""

from datetime import datetime, timezone
from typing import List, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session
from sqlalchemy import select

from app.core.database import get_db
from app.models.work_session import WorkSession
from app.domain.work.state import WorkSessionData, WorkMetrics
from app.domain.work.work_engine import WorkEngine

router = APIRouter(prefix="/sessions", tags=["work"])


# --- Schemas ---

class WorkSessionCreate(BaseModel):
    user_id: UUID
    start_time: datetime
    end_time: Optional[datetime] = None
    duration_minutes: Optional[int] = None
    income: int = 0
    activity_type: str
    location: Optional[str] = None
    notes: Optional[str] = None

class WorkSessionUpdate(BaseModel):
    end_time: Optional[datetime] = None
    duration_minutes: Optional[int] = None
    income: Optional[int] = None
    activity_type: Optional[str] = None
    location: Optional[str] = None
    notes: Optional[str] = None
    version: int

class WorkSessionResponse(BaseModel):
    id: UUID
    user_id: UUID
    start_time: datetime
    end_time: Optional[datetime]
    duration_minutes: Optional[int]
    income: int
    activity_type: str
    location: Optional[str]
    notes: Optional[str]
    version: int
    created_at: datetime
    updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)

class WorkMetricsResponse(BaseModel):
    total_income: int
    total_duration_minutes: int
    active_sessions_count: int
    completed_sessions_count: int
    income_per_hour: Optional[int]


# --- Helpers ---

def _to_domain(session_model: WorkSession) -> WorkSessionData:
    return WorkSessionData(
        id=str(session_model.id),
        start_time=session_model.start_time,
        end_time=session_model.end_time,
        duration_minutes=session_model.duration_minutes,
        income=session_model.income,
        activity_type=session_model.activity_type,
    )


# --- Endpoints ---

@router.post("/", response_model=WorkSessionResponse, status_code=status.HTTP_201_CREATED)
def create_session(session: WorkSessionCreate, db: Session = Depends(get_db)):
    db_session = WorkSession(
        user_id=str(session.user_id),
        start_time=session.start_time,
        end_time=session.end_time,
        duration_minutes=session.duration_minutes,
        income=session.income,
        activity_type=session.activity_type,
        location=session.location,
        notes=session.notes,
    )
    db.add(db_session)
    db.commit()
    db.refresh(db_session)
    return db_session


@router.get("/", response_model=List[WorkSessionResponse])
def list_sessions(user_id: UUID, db: Session = Depends(get_db)):
    sessions = db.execute(
        select(WorkSession)
        .where(WorkSession.user_id == str(user_id))
        .where(WorkSession.deleted_at.is_(None))
        .order_by(WorkSession.start_time.desc())
    ).scalars().all()
    return sessions


@router.put("/{session_id}", response_model=WorkSessionResponse)
def update_session(session_id: UUID, update_data: WorkSessionUpdate, db: Session = Depends(get_db)):
    db_session = db.get(WorkSession, str(session_id))
    if not db_session or db_session.deleted_at:
        raise HTTPException(status_code=404, detail="Session not found")
        
    if db_session.version != update_data.version:
        raise HTTPException(status_code=409, detail="Conflict: Version mismatch")

    if update_data.end_time is not None:
        db_session.end_time = update_data.end_time
    if update_data.duration_minutes is not None:
        db_session.duration_minutes = update_data.duration_minutes
    if update_data.income is not None:
        db_session.income = update_data.income
    if update_data.activity_type is not None:
        db_session.activity_type = update_data.activity_type
    if update_data.location is not None:
        db_session.location = update_data.location
    if update_data.notes is not None:
        db_session.notes = update_data.notes
        
    db_session.version += 1
    db.commit()
    db.refresh(db_session)
    return db_session


@router.delete("/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_session(session_id: UUID, db: Session = Depends(get_db)):
    db_session = db.get(WorkSession, str(session_id))
    if not db_session or db_session.deleted_at:
        raise HTTPException(status_code=404, detail="Session not found")
        
    db_session.deleted_at = datetime.now(timezone.utc)
    db.commit()
    return None


@router.get("/metrics/summary", response_model=dict[str, WorkMetricsResponse])
def get_metrics_summary(user_id: UUID, db: Session = Depends(get_db)):
    db_sessions = db.execute(
        select(WorkSession)
        .where(WorkSession.user_id == str(user_id))
        .where(WorkSession.deleted_at.is_(None))
    ).scalars().all()
    
    domain_sessions = [_to_domain(s) for s in db_sessions]
    engine = WorkEngine(domain_sessions)
    now = datetime.now(timezone.utc)
    
    metrics = engine.get_all_metrics(now)
    
    # Convert domain response to Pydantic models
    return {
        key: WorkMetricsResponse(
            total_income=m.total_income,
            total_duration_minutes=m.total_duration_minutes,
            active_sessions_count=m.active_sessions_count,
            completed_sessions_count=m.completed_sessions_count,
            income_per_hour=m.income_per_hour,
        ) for key, m in metrics.items()
    }
