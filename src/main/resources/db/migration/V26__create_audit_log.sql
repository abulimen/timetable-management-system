-- Flyway Migration V26: Create audit_log table for tracking all data changes
-- See AUDIT_LOGGING_REQUIREMENTS.md for full specification

CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    timestamp DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    
    -- Actor Information
    actor_type VARCHAR(20) NOT NULL,
    actor_id VARCHAR(100),
    actor_name VARCHAR(255),
    actor_ip_address VARCHAR(45),
    
    -- Action Information
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    entity_name VARCHAR(255),
    
    -- Change Details (JSON for flexible storage)
    previous_value JSON,
    new_value JSON,
    changed_fields VARCHAR(1000),
    
    -- Context
    description VARCHAR(500),
    request_id VARCHAR(50),
    session_id VARCHAR(100),
    
    -- Metadata
    success BOOLEAN DEFAULT TRUE,
    error_message VARCHAR(500),
    
    -- Indexes for common query patterns
    INDEX idx_audit_timestamp (timestamp),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_composite (timestamp, entity_type, action)
);
