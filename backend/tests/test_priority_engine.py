from datetime import datetime, timedelta, timezone

from app.services.priority_engine import calculate_debt_priority, calculate_goal_priority


NOW = datetime(2026, 1, 15, tzinfo=timezone.utc)


def test_overdue_debt_scores_above_far_due_debt():
    overdue = calculate_debt_priority(
        priority=3,
        status="overdue",
        due_date=NOW - timedelta(days=1),
        interest_rate=0,
        original_amount=1000,
        remaining_amount=1000,
        now=NOW,
    )
    far_due = calculate_debt_priority(
        priority=3,
        status="pending",
        due_date=NOW + timedelta(days=180),
        interest_rate=0,
        original_amount=1000,
        remaining_amount=100,
        now=NOW,
    )
    assert overdue > far_due


def test_paid_debt_has_no_priority():
    assert calculate_debt_priority(
        priority=1,
        status="paid",
        due_date=NOW,
        interest_rate=20,
        original_amount=1000,
        remaining_amount=0,
        now=NOW,
    ) == 0


def test_emergency_goal_with_deadline_scores_above_completed_goal():
    urgent = calculate_goal_priority(
        priority=1,
        status="active",
        category="emergency",
        target_date=NOW + timedelta(days=14),
        target_amount=1000,
        current_amount=100,
        now=NOW,
    )
    completed = calculate_goal_priority(
        priority=1,
        status="completed",
        category="emergency",
        target_date=NOW,
        target_amount=1000,
        current_amount=1000,
        now=NOW,
    )
    assert urgent > completed
