-- Special Events table for fixed events like interdisciplinary seminars
CREATE TABLE special_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    duration_hours INT NOT NULL DEFAULT 2,
    room_id BIGINT,
    lecturer_id BIGINT,
    is_online BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_special_event_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE SET NULL,
    CONSTRAINT fk_special_event_lecturer FOREIGN KEY (lecturer_id) REFERENCES lecturer(id) ON DELETE SET NULL
);

-- Junction table for special event to student groups (many-to-many)
CREATE TABLE special_event_student_group (
    special_event_id BIGINT NOT NULL,
    student_group_id BIGINT NOT NULL,
    PRIMARY KEY (special_event_id, student_group_id),
    CONSTRAINT fk_sesg_special_event FOREIGN KEY (special_event_id) REFERENCES special_event(id) ON DELETE CASCADE,
    CONSTRAINT fk_sesg_student_group FOREIGN KEY (student_group_id) REFERENCES student_group(id) ON DELETE CASCADE
);

-- Index for efficient queries
CREATE INDEX idx_special_event_day ON special_event(day_of_week);
CREATE INDEX idx_special_event_active ON special_event(is_active);
