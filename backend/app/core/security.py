"""
LifeOS Finance — Security Utilities
JWT token creation/validation and password hashing.
"""

from datetime import datetime, timedelta, timezone
from typing import Optional

from jose import JWTError, jwt
from passlib.context import CryptContext

from app.core.config import settings

# Password hashing
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    """Hash a password using bcrypt."""
    return pwd_context.hash(password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify a password against its hash."""
    return pwd_context.verify(plain_password, hashed_password)


def get_jwt_secret() -> str:
    """Return configured JWT secret."""
    secret = settings.effective_jwt_secret
    if not secret:
        return settings.SECRET_KEY
    return secret


def create_access_token(
    data: dict,
    expires_delta: Optional[timedelta] = None
) -> str:
    """Create a JWT access token."""
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + (
        expires_delta or timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    )
    to_encode.update({"exp": expire, "type": "access"})
    return jwt.encode(to_encode, get_jwt_secret(), algorithm=settings.JWT_ALGORITHM)


def create_refresh_token(data: dict) -> str:
    """Create a JWT refresh token."""
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    to_encode.update({"exp": expire, "type": "refresh"})
    return jwt.encode(to_encode, get_jwt_secret(), algorithm=settings.JWT_ALGORITHM)


def decode_token(token: str) -> Optional[dict]:
    """Decode and validate a JWT token."""
    try:
        payload = jwt.decode(
            token,
            get_jwt_secret(),
            algorithms=[settings.JWT_ALGORITHM]
        )
        return payload
    except JWTError:
        return None


# ─── V2 Authentication Helpers ────────────────────────────
import hashlib
import secrets

ACCESS_TOKEN_EXPIRE_MINUTES_V2: int = 15
REFRESH_TOKEN_EXPIRE_DAYS_V2: int = 30


def hash_token(token: str) -> str:
    """Hash a token string using SHA-256 for secure database storage."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def create_access_token_v2(
    user_id: str,
    extra_claims: Optional[dict] = None,
    expires_minutes: int = ACCESS_TOKEN_EXPIRE_MINUTES_V2,
) -> str:
    """Create a short-lived V2 JWT access token (15 minutes)."""
    to_encode = (extra_claims or {}).copy()
    now = datetime.now(timezone.utc)
    expire = now + timedelta(minutes=expires_minutes)
    to_encode.update({
        "sub": user_id,
        "iat": now,
        "exp": expire,
        "type": "access_v2",
    })
    return jwt.encode(to_encode, get_jwt_secret(), algorithm=settings.JWT_ALGORITHM)


def create_refresh_token_v2(
    user_id: str,
    expires_days: int = REFRESH_TOKEN_EXPIRE_DAYS_V2,
) -> tuple[str, str, datetime]:
    """
    Create a V2 refresh token with cryptographic randomness.
    Returns (raw_token, token_hash, expires_at).
    """
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(days=expires_days)
    random_entropy = secrets.token_urlsafe(32)
    to_encode = {
        "sub": user_id,
        "jti": random_entropy,
        "iat": now,
        "exp": expires_at,
        "type": "refresh_v2",
    }
    raw_token = jwt.encode(to_encode, get_jwt_secret(), algorithm=settings.JWT_ALGORITHM)
    token_hash = hash_token(raw_token)
    return raw_token, token_hash, expires_at
