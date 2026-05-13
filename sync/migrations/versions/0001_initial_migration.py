"""Initial migration

Revision ID: 0001
Revises: 
Create Date: 2026-05-13 21:05:00.000000

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


# revision identifiers, used by Alembic.
revision = '0001'
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        'chest_memories',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('server_id', sa.String(length=255), nullable=False),
        sa.Column('world_id', sa.String(length=255), nullable=False),
        sa.Column('pos_x', sa.Integer(), nullable=False),
        sa.Column('pos_y', sa.Integer(), nullable=False),
        sa.Column('pos_z', sa.Integer(), nullable=False),
        sa.Column('items_data', postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column('last_updated', sa.DateTime(), server_default=sa.func.now(), nullable=True),
        sa.Column('updated_by', postgresql.UUID(as_uuid=True), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_chest_memories_server_id'), 'chest_memories', ['server_id'], unique=False)
    op.create_index(op.f('ix_chest_memories_world_id'), 'chest_memories', ['world_id'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_chest_memories_world_id'), table_name='chest_memories')
    op.drop_index(op.f('ix_chest_memories_server_id'), table_name='chest_memories')
    op.drop_table('chest_memories')
