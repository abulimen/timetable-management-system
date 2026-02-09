-- V34: Add import_history_id to import_batch for rollback-to-staging feature
ALTER TABLE import_batch
ADD COLUMN import_history_id BIGINT NULL;
