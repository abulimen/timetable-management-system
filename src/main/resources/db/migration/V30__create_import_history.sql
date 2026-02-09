-- Import history table for tracking all bulk imports with rollback capability
CREATE TABLE import_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT,
    entity_type VARCHAR(50) NOT NULL,
    import_mode VARCHAR(20) NOT NULL DEFAULT 'STRICT',
    file_name VARCHAR(255),
    created_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    created_ids TEXT,
    updated_data TEXT,
    can_rollback BOOLEAN DEFAULT TRUE,
    rolled_back BOOLEAN DEFAULT FALSE,
    rolled_back_at TIMESTAMP NULL,
    rolled_back_by BIGINT,
    CONSTRAINT fk_import_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_import_history_rolled_back_by FOREIGN KEY (rolled_back_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Index for efficient history queries
CREATE INDEX idx_import_history_timestamp ON import_history(timestamp DESC);
CREATE INDEX idx_import_history_user ON import_history(user_id);
CREATE INDEX idx_import_history_entity ON import_history(entity_type);
