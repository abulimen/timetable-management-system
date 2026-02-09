CREATE TABLE import_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entity_type VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_data MEDIUMBLOB NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    approval_date DATETIME(6),
    
    CONSTRAINT fk_import_batch_creator FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_import_batch_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id)
);
