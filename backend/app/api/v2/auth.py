"""
LifeOS Finance — Auth V2 Endpoints
Implements modern JWT pair (access + refresh with rotation and revocation).
"""

from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.core.security import (
    create_access_token_v2,
    create_refresh_token_v2,
    decode_token,
    hash_password,
    hash_token,
    verify_password,
    ACCESS_TOKEN_EXPIRE_MINUTES_V2,
)
from app.models.financial_config import FinancialConfig
from app.models.refresh_token import RefreshToken
from app.models.user import User
from app.schemas.auth import UserResponse
from app.schemas.auth_v2 import (
    LoginRequestV2,
    LogoutRequestV2,
    MessageResponse,
    RefreshRequestV2,
    RegisterRequestV2,
    TokenResponseV2,
)

router = APIRouter(prefix="/auth", tags=["Authentication V2"])


@router.post("/register", response_model=TokenResponseV2, status_code=status.HTTP_201_CREATED)
async def register_v2(
    request: RegisterRequestV2,
    db: AsyncSession = Depends(get_db),
):
    """Register a new user, issue initial access + refresh tokens, and store refresh token hash."""
    # 1. Check existing user
    existing_res = await db.execute(select(User).where(User.email == request.email))
    if existing_res.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered",
        )

    # 2. Create user
    user = User(
        email=request.email,
        hashed_password=hash_password(request.password),
        full_name=request.full_name,
        is_active=True,
    )
    db.add(user)
    await db.flush()

    # 3. Create default financial config
    config = FinancialConfig(
        user_id=user.id,
        hourly_rate=20000.0,
        daily_target=100000.0,
        weekly_target=500000.0,
        monthly_target=2000000.0,
    )
    db.add(config)

    # 4. Generate JWT pair
    access_token = create_access_token_v2(user.id)
    raw_refresh, token_hash, expires_at = create_refresh_token_v2(user.id)

    # 5. Store refresh token hash in DB
    refresh_record = RefreshToken(
        user_id=user.id,
        token_hash=token_hash,
        expires_at=expires_at,
    )
    db.add(refresh_record)
    await db.commit()
    await db.refresh(user)

    return TokenResponseV2(
        access_token=access_token,
        refresh_token=raw_refresh,
        expires_in=ACCESS_TOKEN_EXPIRE_MINUTES_V2 * 60,
        user=UserResponse.model_validate(user),
    )


@router.post("/login", response_model=TokenResponseV2)
async def login_v2(
    request: LoginRequestV2,
    db: AsyncSession = Depends(get_db),
):
    """Authenticate user with email and password, store hashed refresh token, return token pair."""
    result = await db.execute(select(User).where(User.email == request.email))
    user = result.scalar_one_or_none()

    if not user or not verify_password(request.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password",
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User account is disabled",
        )

    # Generate JWT pair
    access_token = create_access_token_v2(user.id)
    raw_refresh, token_hash, expires_at = create_refresh_token_v2(user.id)

    # Store refresh token hash in DB
    refresh_record = RefreshToken(
        user_id=user.id,
        token_hash=token_hash,
        expires_at=expires_at,
    )
    db.add(refresh_record)
    await db.commit()
    await db.refresh(user)

    return TokenResponseV2(
        access_token=access_token,
        refresh_token=raw_refresh,
        expires_in=ACCESS_TOKEN_EXPIRE_MINUTES_V2 * 60,
        user=UserResponse.model_validate(user),
    )


@router.post("/refresh", response_model=TokenResponseV2)
async def refresh_v2(
    request: RefreshRequestV2,
    db: AsyncSession = Depends(get_db),
):
    """
    Refresh access token using refresh token with rotation.
    The old refresh token is marked as revoked, and a brand new refresh token is issued.
    """
    # 1. Decode token
    payload = decode_token(request.refresh_token)
    if payload is None or payload.get("type") != "refresh_v2":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid refresh token format or signature",
        )

    user_id = payload.get("sub")
    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid refresh token payload",
        )

    # 2. Check token hash in database
    token_hash = hash_token(request.refresh_token)
    res = await db.execute(select(RefreshToken).where(RefreshToken.token_hash == token_hash))
    token_record = res.scalar_one_or_none()

    if not token_record:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token not recognized",
        )

    now = datetime.now(timezone.utc)
    if token_record.revoked_at is not None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token has already been revoked or used (possible replay attack)",
        )

    if token_record.expires_at <= now:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token has expired",
        )

    # 3. Check user status
    user_res = await db.execute(select(User).where(User.id == user_id))
    user = user_res.scalar_one_or_none()
    if not user or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found or disabled",
        )

    # 4. Token rotation: revoke old token
    token_record.revoked_at = now

    # 5. Issue new token pair
    new_access_token = create_access_token_v2(user.id)
    new_raw_refresh, new_token_hash, new_expires_at = create_refresh_token_v2(user.id)

    new_refresh_record = RefreshToken(
        user_id=user.id,
        token_hash=new_token_hash,
        expires_at=new_expires_at,
    )
    db.add(new_refresh_record)
    await db.commit()

    return TokenResponseV2(
        access_token=new_access_token,
        refresh_token=new_raw_refresh,
        expires_in=ACCESS_TOKEN_EXPIRE_MINUTES_V2 * 60,
        user=UserResponse.model_validate(user),
    )


@router.post("/logout", response_model=MessageResponse)
async def logout_v2(
    request: LogoutRequestV2,
    db: AsyncSession = Depends(get_db),
):
    """Revoke a refresh token on logout."""
    token_hash = hash_token(request.refresh_token)
    res = await db.execute(select(RefreshToken).where(RefreshToken.token_hash == token_hash))
    token_record = res.scalar_one_or_none()

    if token_record and token_record.revoked_at is None:
        token_record.revoked_at = datetime.now(timezone.utc)
        await db.commit()

    return MessageResponse(message="Successfully logged out")


@router.get("/me", response_model=UserResponse)
async def get_me_v2(user: User = Depends(get_current_user)):
    """Get profile of the currently authenticated user."""
    return user
