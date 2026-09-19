"""
LifeOS Finance — Auth V2 Schemas
"""

from typing import Optional
from pydantic import BaseModel, EmailStr, Field

from app.schemas.common import ContractBaseModel
from app.schemas.auth import UserResponse


class RegisterRequestV2(BaseModel):
    email: EmailStr
    password: str = Field(..., min_length=6, description="Password must be at least 6 characters")
    full_name: Optional[str] = Field(None, max_length=255)


class LoginRequestV2(BaseModel):
    email: EmailStr
    password: str


class RefreshRequestV2(BaseModel):
    refresh_token: str = Field(..., description="V2 JWT Refresh token")


class LogoutRequestV2(BaseModel):
    refresh_token: str = Field(..., description="V2 JWT Refresh token to revoke")


class TokenResponseV2(ContractBaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in: int = 900  # 15 minutes in seconds
    user: Optional[UserResponse] = None


class MessageResponse(ContractBaseModel):
    message: str
