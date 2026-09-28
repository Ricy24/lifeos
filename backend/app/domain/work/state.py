"""
LifeOS Finance — Work / Income State
"""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass(frozen=True)
class WorkSessionData:
    """Pure domain representation of a work session."""
    id: str
    start_time: datetime
    end_time: Optional[datetime]
    duration_minutes: Optional[int]
    income: int
    activity_type: str
    
    @property
    def is_active(self) -> bool:
        """A session is active when start_time is known and end_time is null."""
        return self.end_time is None


@dataclass(frozen=True)
class WorkMetrics:
    """Calculated metrics for a set of work sessions."""
    total_income: int
    total_duration_minutes: int
    active_sessions_count: int
    completed_sessions_count: int
    
    @property
    def income_per_hour(self) -> Optional[int]:
        """
        Aggregate income per hour using weighted formula:
        total_income * 60 / total_duration_minutes
        
        Returns None if total_duration_minutes is 0.
        """
        if self.total_duration_minutes <= 0:
            return None
        
        # We need to round appropriately. We will use integer division.
        # Since income is in minor units, the resulting rate will also be in minor units.
        # Standard rounding: (a + b//2) // b for positive numbers
        numerator = self.total_income * 60
        denominator = self.total_duration_minutes
        return (numerator + denominator // 2) // denominator
