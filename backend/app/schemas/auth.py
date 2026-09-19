"""
LifeOS Finance — Auth Schemas
"""

from pydantic import BaseModel, EmailStr

from app.schemas.common import ContractBaseModel


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshRequest(BaseModel):
    refresh_token: str


class UserResponse(ContractBaseModel):
    id: str
    email: str
    full_name: str | None = None
    is_active: bool

    class Config:
        from_attributes = True
