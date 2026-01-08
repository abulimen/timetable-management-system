-- V25: Create availability_change_requests table for Phase 5 availability safeguards
-- Per USER_AUTH_REQUIREMENTS.md Section 3.4

CREATE TABLE availability_change_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- The lecturer whose availability is being changed
    lecturer_id BIGINT NOT NULL,
    
    -- The user who submitted this request
    requested_by_id BIGINT NOT NULL,
    
    -- Requested change details
    day_of_week VARCHAR(20) NOT NULL,  -- e.g., 'MONDAY', 'TUESDAY'
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    new_status VARCHAR(20) NOT NULL,   -- 'AVAILABLE', 'UNAVAILABLE', 'PREFERRED'
    reason VARCHAR(1000) NOT NULL,
    
    -- Workflow status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- 'PENDING', 'APPROVED', 'REJECTED', 'RETURNED'
    reviewed_by_id BIGINT,
    reviewed_at DATETIME(3),
    review_notes VARCHAR(1000),
    
    -- Impact analysis
    affected_lessons_count INT DEFAULT 0,
    affected_lesson_ids VARCHAR(2000),
    
    -- Timestamps
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    
    -- Indexes
    INDEX idx_lecturer (lecturer_id),
    INDEX idx_requested_by (requested_by_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    
    -- Foreign keys
    CONSTRAINT fk_avail_change_lecturer FOREIGN KEY (lecturer_id) REFERENCES lecturer(id) ON DELETE CASCADE,
    CONSTRAINT fk_avail_change_requested_by FOREIGN KEY (requested_by_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_avail_change_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Note: Availability settings will be added when constraint_settings table exists
-- For now, the defaults will be handled in ConstraintSettingsService
