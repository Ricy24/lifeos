"""add motorcycles table

Revision ID: e8f9a0b1c2d3
Revises: c7d8e9f0a1b2
Create Date: 2026-09-28 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


# revision identifiers, used by Alembic.
revision: str = 'e8f9a0b1c2d3'
down_revision: Union[str, None] = 'c7d8e9f0a1b2'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'motorcycles',
        sa.Column('id', sa.String(length=36), nullable=False),
        sa.Column('user_id', sa.String(length=36), nullable=False),
        sa.Column('name', sa.String(length=100), nullable=False, server_default='Mi Moto'),
        sa.Column('model', sa.String(length=50), nullable=False, server_default='2024'),
        sa.Column('current_mileage', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('oil_change_interval', sa.Integer(), nullable=False, server_default='2500'),
        sa.Column('last_oil_change_mileage', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('front_tire_mileage', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('front_tire_life_km', sa.Integer(), nullable=False, server_default='18000'),
        sa.Column('rear_tire_mileage', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('rear_tire_life_km', sa.Integer(), nullable=False, server_default='12000'),
        sa.Column('brake_pads_mileage', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('brake_pads_life_km', sa.Integer(), nullable=False, server_default='8000'),
        sa.Column('chain_maintenance_mileage', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('chain_maintenance_interval', sa.Integer(), nullable=False, server_default='1000'),
        sa.Column('soat_expiry_date', sa.BigInteger(), nullable=False),
        sa.Column('techno_expiry_date', sa.BigInteger(), nullable=False),
        sa.Column('cost_per_km', sa.Float(), nullable=False, server_default='45.0'),
        sa.Column('last_updated', sa.BigInteger(), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('user_id')
    )


def downgrade() -> None:
    op.drop_table('motorcycles')
