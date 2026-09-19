"""audit_columns_and_numeric

Revision ID: a1b2c3d4e5f6
Revises: d33e51b1a44d
Create Date: 2026-09-19 12:45:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = 'a1b2c3d4e5f6'
down_revision: Union[str, None] = 'd33e51b1a44d'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. Add audit columns (deleted_at, version, updated_at if missing) to existing tables
    tables = [
        'users',
        'accounts',
        'debts',
        'debt_payments',
        'goals',
        'wishlist_items',
        'financial_configs',
        'transactions',
    ]
    for table in tables:
        op.add_column(table, sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True))
        op.add_column(table, sa.Column('version', sa.Integer(), server_default='1', nullable=False))

    # debt_payments didn't have updated_at
    op.add_column('debt_payments', sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False))

    # 2. Alter Float columns to Numeric(14, 2) with postgresql_using
    op.alter_column('accounts', 'balance',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='balance::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('0.0'))

    op.alter_column('debts', 'original_amount',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='original_amount::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False)
    op.alter_column('debts', 'remaining_amount',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='remaining_amount::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False)
    op.alter_column('debts', 'interest_rate',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='interest_rate::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=True,
                    existing_server_default=sa.text('0.0'))

    op.alter_column('debt_payments', 'amount',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='amount::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False)

    op.alter_column('goals', 'target_amount',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='target_amount::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False)
    op.alter_column('goals', 'current_amount',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='current_amount::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('0.0'))

    op.alter_column('wishlist_items', 'price',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='price::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False)
    op.alter_column('wishlist_items', 'saved_amount',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='saved_amount::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('0.0'))
    op.alter_column('wishlist_items', 'previous_price',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='previous_price::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=True)

    op.alter_column('financial_configs', 'hourly_rate',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='hourly_rate::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('20000.0'))
    op.alter_column('financial_configs', 'daily_target',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='daily_target::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('100000.0'))
    op.alter_column('financial_configs', 'weekly_target',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='weekly_target::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('500000.0'))
    op.alter_column('financial_configs', 'monthly_target',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='monthly_target::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('2000000.0'))
    op.alter_column('financial_configs', 'work_hours_per_day',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='work_hours_per_day::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False,
                    existing_server_default=sa.text('8.0'))

    op.alter_column('transactions', 'amount',
                    type_=sa.Numeric(precision=14, scale=2),
                    postgresql_using='amount::numeric(14,2)',
                    existing_type=sa.Float(),
                    existing_nullable=False)

    # 3. Create tables added in current version (financial_snapshots, work_sessions)
    op.create_table(
        'financial_snapshots',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('snapshot_date', sa.DateTime(timezone=True), nullable=False),
        sa.Column('snapshot_type', sa.String(length=20), nullable=False, server_default='daily'),
        sa.Column('available_cash', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('total_assets', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('total_liabilities', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('net_worth', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('monthly_income', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('monthly_expenses', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('monthly_obligations', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('protected_cash', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('free_cash', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('goal_progress', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('debt_pressure', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('obligation_coverage', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('financial_health_score', sa.Numeric(precision=14, scale=2), nullable=False, server_default='0.0'),
        sa.Column('financial_health_status', sa.String(length=20), nullable=False, server_default='GREEN'),
        sa.Column('indicators', sa.JSON(), nullable=True),
        sa.Column('metadata_extra', sa.JSON(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('version', sa.Integer(), server_default='1', nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_financial_snapshots_snapshot_date'), 'financial_snapshots', ['snapshot_date'], unique=False)
    op.create_index(op.f('ix_financial_snapshots_user_id'), 'financial_snapshots', ['user_id'], unique=False)

    op.create_table(
        'work_sessions',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('start_time', sa.DateTime(timezone=True), nullable=False),
        sa.Column('end_time', sa.DateTime(timezone=True), nullable=True),
        sa.Column('duration_minutes', sa.Integer(), nullable=True),
        sa.Column('income', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('activity_type', sa.String(length=255), nullable=False),
        sa.Column('location', sa.String(length=255), nullable=True),
        sa.Column('notes', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('deleted_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('version', sa.Integer(), server_default='1', nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_work_sessions_user_id'), 'work_sessions', ['user_id'], unique=False)


def downgrade() -> None:
    op.drop_table('work_sessions')
    op.drop_table('financial_snapshots')

    # Revert columns to Float
    op.alter_column('transactions', 'amount', type_=sa.Float(), postgresql_using='amount::float')
    op.alter_column('financial_configs', 'work_hours_per_day', type_=sa.Float(), postgresql_using='work_hours_per_day::float')
    op.alter_column('financial_configs', 'monthly_target', type_=sa.Float(), postgresql_using='monthly_target::float')
    op.alter_column('financial_configs', 'weekly_target', type_=sa.Float(), postgresql_using='weekly_target::float')
    op.alter_column('financial_configs', 'daily_target', type_=sa.Float(), postgresql_using='daily_target::float')
    op.alter_column('financial_configs', 'hourly_rate', type_=sa.Float(), postgresql_using='hourly_rate::float')
    op.alter_column('wishlist_items', 'previous_price', type_=sa.Float(), postgresql_using='previous_price::float')
    op.alter_column('wishlist_items', 'saved_amount', type_=sa.Float(), postgresql_using='saved_amount::float')
    op.alter_column('wishlist_items', 'price', type_=sa.Float(), postgresql_using='price::float')
    op.alter_column('goals', 'current_amount', type_=sa.Float(), postgresql_using='current_amount::float')
    op.alter_column('goals', 'target_amount', type_=sa.Float(), postgresql_using='target_amount::float')
    op.alter_column('debt_payments', 'amount', type_=sa.Float(), postgresql_using='amount::float')
    op.alter_column('debts', 'interest_rate', type_=sa.Float(), postgresql_using='interest_rate::float')
    op.alter_column('debts', 'remaining_amount', type_=sa.Float(), postgresql_using='remaining_amount::float')
    op.alter_column('debts', 'original_amount', type_=sa.Float(), postgresql_using='original_amount::float')
    op.alter_column('accounts', 'balance', type_=sa.Float(), postgresql_using='balance::float')

    op.drop_column('debt_payments', 'updated_at')

    tables = [
        'users',
        'accounts',
        'debts',
        'debt_payments',
        'goals',
        'wishlist_items',
        'financial_configs',
        'transactions',
    ]
    for table in tables:
        op.drop_column(table, 'version')
        op.drop_column(table, 'deleted_at')
