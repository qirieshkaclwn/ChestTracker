-- Downgrade migration for 0001_initial_migration.sql

DROP INDEX IF EXISTS ix_chest_memories_coords;
DROP INDEX IF EXISTS ix_chest_memories_world_id;
DROP INDEX IF EXISTS ix_chest_memories_server_id;
DROP TABLE IF EXISTS chest_memories;
