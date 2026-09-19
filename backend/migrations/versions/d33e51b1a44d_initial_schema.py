"""initial_schema

Revision ID: d33e51b1a44d
Revises: 
Create Date: 2026-09-19 12:12:12.946866

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = 'd33e51b1a44d'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 1. users
    op.create_table(
        'users',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('email', sa.String(length=255), nullable=False),
        sa.Column('hashed_password', sa.String(length=255), nullable=False),
        sa.Column('full_name', sa.String(length=255), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=False, server_default=sa.text('true')),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_users_email'), 'users', ['email'], unique=True)

    # 2. accounts
    op.create_table(
        'accounts',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('name', sa.String(length=100), nullable=False),
        sa.Column('account_type', sa.Enum('cash', 'bank', 'nequi', 'daviplata', 'credit_card', 'savings', 'investment', 'other', name='account_type_enum'), nullable=False),
        sa.Column('balance', sa.Float(), nullable=False, server_default=sa.text('0.0')),
        sa.Column('currency', sa.String(length=10), nullable=False, server_default='COP'),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('color', sa.String(length=7), nullable=True),
        sa.Column('icon', sa.String(length=50), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=False, server_default=sa.text('true')),
        sa.Column('include_in_total', sa.Boolean(), nullable=False, server_default=sa.text('true')),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_accounts_user_id'), 'accounts', ['user_id'], unique=False)

    # 3. debts
    op.create_table(
        'debts',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('person_or_entity', sa.String(length=255), nullable=False),
        sa.Column('debt_type', sa.Enum('i_owe', 'owed_to_me', name='debt_type_enum'), nullable=False),
        sa.Column('original_amount', sa.Float(), nullable=False),
        sa.Column('remaining_amount', sa.Float(), nullable=False),
        sa.Column('interest_rate', sa.Float(), nullable=True, server_default=sa.text('0.0')),
        sa.Column('debt_date', sa.DateTime(timezone=True), nullable=False),
        sa.Column('due_date', sa.DateTime(timezone=True), nullable=True),
        sa.Column('priority', sa.Integer(), nullable=False, server_default='3'),
        sa.Column('status', sa.Enum('pending', 'partially_paid', 'paid', 'overdue', name='debt_status_enum'), nullable=False),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('notes', sa.Text(), nullable=True),
        sa.Column('tags', sa.JSON(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_debts_user_id'), 'debts', ['user_id'], unique=False)

    # 4. debt_payments
    op.create_table(
        'debt_payments',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('debt_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('amount', sa.Float(), nullable=False),
        sa.Column('payment_date', sa.DateTime(timezone=True), nullable=False),
        sa.Column('notes', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['debt_id'], ['debts.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_debt_payments_debt_id'), 'debt_payments', ['debt_id'], unique=False)

    # 5. goals
    op.create_table(
        'goals',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('name', sa.String(length=255), nullable=False),
        sa.Column('target_amount', sa.Float(), nullable=False),
        sa.Column('current_amount', sa.Float(), nullable=False, server_default=sa.text('0.0')),
        sa.Column('category', sa.Enum('purchase', 'savings', 'emergency', 'investment', 'travel', 'education', 'health', 'other', name='goal_category_enum'), nullable=False),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('image_url', sa.String(length=500), nullable=True),
        sa.Column('priority', sa.Integer(), nullable=False, server_default='3'),
        sa.Column('target_date', sa.DateTime(timezone=True), nullable=True),
        sa.Column('status', sa.Enum('active', 'paused', 'completed', 'cancelled', name='goal_status_enum'), nullable=False),
        sa.Column('notes', sa.Text(), nullable=True),
        sa.Column('tags', sa.JSON(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_goals_user_id'), 'goals', ['user_id'], unique=False)

    # 6. wishlist_items
    op.create_table(
        'wishlist_items',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('name', sa.String(length=255), nullable=False),
        sa.Column('price', sa.Float(), nullable=False),
        sa.Column('url', sa.String(length=1000), nullable=True),
        sa.Column('store', sa.String(length=255), nullable=True),
        sa.Column('image_url', sa.String(length=1000), nullable=True),
        sa.Column('category', sa.String(length=100), nullable=True),
        sa.Column('priority', sa.Integer(), nullable=False, server_default='3'),
        sa.Column('saved_amount', sa.Float(), nullable=False, server_default=sa.text('0.0')),
        sa.Column('previous_price', sa.Float(), nullable=True),
        sa.Column('status', sa.String(length=50), nullable=False, server_default='wanted'),
        sa.Column('notes', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_wishlist_items_user_id'), 'wishlist_items', ['user_id'], unique=False)

    # 7. financial_configs
    op.create_table(
        'financial_configs',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('hourly_rate', sa.Float(), nullable=False, server_default=sa.text('20000.0')),
        sa.Column('daily_target', sa.Float(), nullable=False, server_default=sa.text('100000.0')),
        sa.Column('weekly_target', sa.Float(), nullable=False, server_default=sa.text('500000.0')),
        sa.Column('monthly_target', sa.Float(), nullable=False, server_default=sa.text('2000000.0')),
        sa.Column('work_days_per_week', sa.Integer(), nullable=False, server_default='6'),
        sa.Column('work_hours_per_day', sa.Float(), nullable=False, server_default=sa.text('8.0')),
        sa.Column('currency', sa.String(length=10), nullable=False, server_default='COP'),
        sa.Column('timezone', sa.String(length=50), nullable=False, server_default='America/Bogota'),
        sa.Column('income_categories', sa.JSON(), nullable=True),
        sa.Column('expense_categories', sa.JSON(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_financial_configs_user_id'), 'financial_configs', ['user_id'], unique=True)

    # 8. transactions
    op.create_table(
        'transactions',
        sa.Column('id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=False), nullable=False),
        sa.Column('account_id', postgresql.UUID(as_uuid=False), nullable=True),
        sa.Column('amount', sa.Float(), nullable=False),
        sa.Column('transaction_type', sa.Enum('income', 'expense', name='transaction_type_enum'), nullable=False),
        sa.Column('category', sa.String(length=50), nullable=False),
        sa.Column('description', sa.String(length=500), nullable=True),
        sa.Column('notes', sa.Text(), nullable=True),
        sa.Column('transaction_date', sa.DateTime(timezone=True), nullable=False),
        sa.Column('location', sa.String(length=255), nullable=True),
        sa.Column('tags', sa.JSON(), nullable=True),
        sa.Column('is_recurring', sa.Boolean(), nullable=False, server_default=sa.text('false')),
        sa.Column('recurring_pattern', sa.String(length=50), nullable=True),
        sa.Column('source', sa.String(length=50), nullable=False, server_default='app'),
        sa.Column('metadata_extra', sa.JSON(), nullable=True),
        sa.Column('sync_status', sa.Enum('synced', 'pending', 'failed', name='sync_status_enum'), nullable=False, server_default='synced'),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['account_id'], ['accounts.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_transactions_account_id'), 'transactions', ['account_id'], unique=False)
    op.create_index(op.f('ix_transactions_transaction_date'), 'transactions', ['transaction_date'], unique=False)
    op.create_index(op.f('ix_transactions_user_id'), 'transactions', ['user_id'], unique=False)


def downgrade() -> None:
    op.drop_table('transactions')
    op.drop_table('financial_configs')
    op.drop_table('wishlist_items')
    op.drop_table('goals')
    op.drop_table('debt_payments')
    op.drop_table('debts')
    op.drop_table('accounts')
    op.drop_table('users')
