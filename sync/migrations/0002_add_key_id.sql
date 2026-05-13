-- Migration to add key_id to chest_memories

ALTER TABLE chest_memories ADD COLUMN IF NOT EXISTS key_id VARCHAR(255) NOT NULL DEFAULT 'minecraft:chest';

-- Update index to include key_id if needed, but usually we filter by coords.
-- However, multiple keys could technically exist at the same coords if providers are weird.
-- For now, let's just make sure the coords index is solid.
DROP INDEX IF EXISTS ix_chest_memories_coords;
CREATE INDEX IF NOT EXISTS ix_chest_memories_coords ON chest_memories (server_id, world_id, pos_x, pos_y, pos_z);
