"""Deterministic priority scoring for debts and financial goals."""

from datetime import datetime, timezone


def _clamp(value: float, minimum: float = 0.0, maximum: float = 100.0) -> int:
    return round(max(minimum, min(maximum, value)))


def _days_until(target: datetime | None, now: datetime) -> int | None:
    if target is None:
        return None
    if target.tzinfo is None:
        target = target.replace(tzinfo=timezone.utc)
    return (target - now).days


def calculate_debt_priority(
    priority: int,
    status: str,
    due_date: datetime | None,
    interest_rate: float,
    original_amount: float,
    remaining_amount: float,
    now: datetime | None = None,
) -> int:
    """Return a 0-100 urgency score; higher values are shown first."""
    if status == "paid":
        return 0

    current_time = now or datetime.now(timezone.utc)
    score = (6 - max(1, min(5, priority))) * 12

    if status == "overdue":
        score += 30

    days_until_due = _days_until(due_date, current_time)
    if days_until_due is not None:
        if days_until_due <= 0:
            score += 25
        elif days_until_due <= 7:
            score += 20
        elif days_until_due <= 30:
            score += 12
        elif days_until_due <= 90:
            score += 5

    score += min(15.0, max(0.0, interest_rate) * 1.5)
    if original_amount > 0:
        score += min(10.0, max(0.0, remaining_amount / original_amount) * 10)

    return _clamp(score)


def calculate_goal_priority(
    priority: int,
    status: str,
    category: str,
    target_date: datetime | None,
    target_amount: float,
    current_amount: float,
    now: datetime | None = None,
) -> int:
    """Return a 0-100 score for active goals; completed goals score zero."""
    if status != "active":
        return 0

    current_time = now or datetime.now(timezone.utc)
    score = (6 - max(1, min(5, priority))) * 12
    progress = current_amount / target_amount if target_amount > 0 else 1.0
    score += min(20.0, max(0.0, 1.0 - progress) * 20)

    if category == "emergency":
        score += 10

    days_until_target = _days_until(target_date, current_time)
    if days_until_target is not None:
        if days_until_target <= 0:
            score += 25
        elif days_until_target <= 30:
            score += 18
        elif days_until_target <= 90:
            score += 10
        elif days_until_target <= 180:
            score += 4

    return _clamp(score)
