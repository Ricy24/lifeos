"""
LifeOS Finance — Goals & Wishlist Endpoints
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Optional
import uuid

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.user import User
from app.models.goal import Goal, WishlistItem, GoalStatus
from app.schemas.goal import GoalCreate, GoalUpdate, GoalResponse, WishlistItemCreate, WishlistItemUpdate, WishlistItemResponse
from app.services.priority_engine import calculate_goal_priority

router = APIRouter(prefix="/goals", tags=["Goals & Wishlist"])

@router.get("", response_model=List[GoalResponse])
async def get_goals(
    status: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    query = select(Goal).where(Goal.user_id == user.id)
    if status:
        query = query.where(Goal.status == status)
    
    result = await db.execute(query)
    goals = result.scalars().all()
    return sorted(
        goals,
        key=lambda goal: calculate_goal_priority(
            goal.priority,
            goal.status.value if isinstance(goal.status, GoalStatus) else goal.status,
            goal.category.value if hasattr(goal.category, "value") else goal.category,
            goal.target_date,
            goal.target_amount,
            goal.current_amount,
        ),
        reverse=True,
    )

@router.post("", response_model=GoalResponse, status_code=status.HTTP_201_CREATED)
async def create_goal(
    goal_in: GoalCreate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    new_goal = Goal(
        id=goal_in.id or str(uuid.uuid4()),
        user_id=user.id,
        name=goal_in.name,
        target_amount=goal_in.target_amount,
        current_amount=goal_in.current_amount,
        category=goal_in.category,
        description=goal_in.description,
        image_url=goal_in.image_url,
        priority=goal_in.priority,
        target_date=goal_in.target_date,
        notes=goal_in.notes,
        tags=goal_in.tags or []
    )
    db.add(new_goal)
    await db.commit()
    await db.refresh(new_goal)
    return new_goal


@router.get("/{goal_id}", response_model=GoalResponse)
async def get_goal(
    goal_id: str,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    result = await db.execute(select(Goal).where(Goal.id == goal_id, Goal.user_id == user.id))
    goal = result.scalar_one_or_none()
    if not goal:
        raise HTTPException(status_code=404, detail="Goal not found")
    return goal


@router.put("/{goal_id}", response_model=GoalResponse)
async def update_goal(
    goal_id: str,
    goal_in: GoalUpdate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    result = await db.execute(select(Goal).where(Goal.id == goal_id, Goal.user_id == user.id))
    goal = result.scalar_one_or_none()
    if not goal:
        raise HTTPException(status_code=404, detail="Goal not found")

    update_data = goal_in.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(goal, field, value)

    # Auto-update status if completed
    if goal.current_amount >= goal.target_amount and goal.status == GoalStatus.ACTIVE:
        goal.status = GoalStatus.COMPLETED

    await db.commit()
    await db.refresh(goal)
    return goal

@router.delete("/{goal_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_goal(
    goal_id: str,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    result = await db.execute(select(Goal).where(Goal.id == goal_id, Goal.user_id == user.id))
    goal = result.scalar_one_or_none()
    if not goal:
        raise HTTPException(status_code=404, detail="Goal not found")
    await db.delete(goal)
    await db.commit()

# --- Wishlist ---

@router.get("/wishlist", response_model=List[WishlistItemResponse])
async def get_wishlist(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    query = select(WishlistItem).where(WishlistItem.user_id == user.id).order_by(WishlistItem.priority.asc())
    result = await db.execute(query)
    return result.scalars().all()

@router.post("/wishlist", response_model=WishlistItemResponse, status_code=status.HTTP_201_CREATED)
async def create_wishlist_item(
    item_in: WishlistItemCreate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    new_item = WishlistItem(
        id=item_in.id or str(uuid.uuid4()),
        user_id=user.id,
        name=item_in.name,
        price=item_in.price,
        url=item_in.url,
        store=item_in.store,
        image_url=item_in.image_url,
        category=item_in.category,
        priority=item_in.priority,
        saved_amount=item_in.saved_amount,
        notes=item_in.notes
    )
    db.add(new_item)
    await db.commit()
    await db.refresh(new_item)
    return new_item

@router.put("/wishlist/{item_id}", response_model=WishlistItemResponse)
async def update_wishlist_item(
    item_id: str,
    item_in: WishlistItemUpdate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    result = await db.execute(select(WishlistItem).where(WishlistItem.id == item_id, WishlistItem.user_id == user.id))
    item = result.scalar_one_or_none()
    if not item:
        raise HTTPException(status_code=404, detail="Wishlist item not found")
    for field, value in item_in.model_dump(exclude_unset=True).items():
        setattr(item, field, value)
    await db.commit()
    await db.refresh(item)
    return item

@router.delete("/wishlist/{item_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_wishlist_item(
    item_id: str,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user)
):
    result = await db.execute(select(WishlistItem).where(WishlistItem.id == item_id, WishlistItem.user_id == user.id))
    item = result.scalar_one_or_none()
    if not item:
        raise HTTPException(status_code=404, detail="Wishlist item not found")
    await db.delete(item)
    await db.commit()
