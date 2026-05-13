-- Initial migration based on SYNC_DESIGN.md

CREATE TABLE IF NOT EXISTS chest_memories (
    id SERIAL PRIMARY KEY,
    server_id VARCHAR(255) NOT NULL,
    world_id VARCHAR(255) NOT NULL,
    pos_x INT NOT NULL,
    pos_y INT NOT NULL,
    pos_z INT NOT NULL,
    items_data JSONB NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL
);

-- Indices for performance
CREATE INDEX IF NOT EXISTS ix_chest_memories_server_id ON chest_memories (server_id);
CREATE INDEX IF NOT EXISTS ix_chest_memories_world_id ON chest_memories (world_id);
CREATE INDEX IF NOT EXISTS ix_chest_memories_coords ON chest_memories (server_id, world_id, pos_x, pos_y, pos_z);
