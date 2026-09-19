"""
LifeOS Finance — Database Configuration
Async SQLAlchemy engine and session management.
"""

from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.orm import DeclarativeBase

from app.core.config import settings


is_sqlite = settings.DATABASE_URL.startswith("sqlite")
engine_kwargs = {"echo": settings.DEBUG}
if not is_sqlite:
    engine_kwargs.update({
        "pool_pre_ping": True,
        "pool_size": 5,
        "max_overflow": 10,
    })

# Create async engine
engine = create_async_engine(
    settings.DATABASE_URL,
    **engine_kwargs
)

# Session factory
async_session_factory = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


class Base(DeclarativeBase):
    """Base class for all SQLAlchemy models."""
    pass

from datetime import datetime, timezone
from sqlalchemy import DateTime, Integer
from sqlalchemy.orm import Mapped, declared_attr, mapped_column

class AuditMixin:
    """Mixin for audit and versioning columns."""
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    version: Mapped[int] = mapped_column(Integer, default=1, nullable=False, server_default="1")
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )
    
    @declared_attr
    def __mapper_args__(cls):
        return {"version_id_col": cls.version}


async def get_db() -> AsyncSession:
    """Dependency that provides a database session."""
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


def is_safe_dev_environment(db_url: str) -> bool:
    """Validate that the database connection points strictly to localhost dev/test DB."""
    from urllib.parse import urlparse
    # normalize scheme for urlparse
    normalized = db_url
    for prefix in ["postgresql+asyncpg://", "postgresql://", "sqlite+aiosqlite:///", "sqlite:///"]:
        if normalized.startswith(prefix):
            normalized = "http://" + normalized[len(prefix):]
            break
    parsed = urlparse(normalized)
    hostname = parsed.hostname or "localhost"
    dbname = parsed.path.lstrip("/").split("?")[0]
    safe_hosts = {"localhost", "127.0.0.1"}
    safe_dbs = {"lifeos_finance", "lifeos_test"}
    return hostname in safe_hosts and (dbname in safe_dbs or dbname.endswith("_test"))


async def safe_drop_all():
    """Drop all tables only with guard of localhost host and development DB name."""
    if not is_safe_dev_environment(settings.DATABASE_URL):
        raise RuntimeError(
            f"Blocked drop_all: {settings.DATABASE_URL} is not a verified local development/test database."
        )
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
