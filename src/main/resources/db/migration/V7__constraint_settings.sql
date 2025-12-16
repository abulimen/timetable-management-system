-- V7: Constraint Settings Table
-- Allows admins to configure constraint parameters without code changes

CREATE TABLE constraint_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(255) NOT NULL,
    data_type VARCHAR(20) NOT NULL,  -- STRING, INTEGER, TIME, BOOLEAN
    category VARCHAR(50) NOT NULL,   -- TIMING, LIMITS, WEIGHTS
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_setting_key (setting_key),
    INDEX idx_category (category)
);

-- Default Timing Settings
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description) VALUES
('lunch_break_start', '12:00', 'TIME', 'TIMING', 'Start of lunch break - lessons cannot overlap'),
('lunch_break_end', '13:00', 'TIME', 'TIMING', 'End of lunch break - lessons cannot overlap'),
('earliest_start_time', '07:00', 'TIME', 'TIMING', 'Earliest time a lesson can start'),
('latest_end_time', '19:00', 'TIME', 'TIMING', 'Latest time a lesson can end');

-- Default Limit Settings
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description) VALUES
('max_lecturer_hours_per_day', '6', 'INTEGER', 'LIMITS', 'Maximum teaching hours per lecturer per day'),
('max_student_consecutive_hours', '4', 'INTEGER', 'LIMITS', 'Maximum consecutive hours for students without break'),
('min_break_between_lessons', '0', 'INTEGER', 'LIMITS', 'Minimum minutes between lessons for a lecturer');

-- Default Constraint Weights (for soft constraints)
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description) VALUES
('weight_room_capacity', '1', 'INTEGER', 'WEIGHTS', 'Weight for room capacity efficiency constraint'),
('weight_day_balance', '2', 'INTEGER', 'WEIGHTS', 'Weight for spreading lessons across days'),
('weight_lecturer_transition', '5', 'INTEGER', 'WEIGHTS', 'Weight for lecturer room transition penalty'),
('weight_student_fatigue', '1', 'INTEGER', 'WEIGHTS', 'Weight for student consecutive hours penalty');

-- Feature Flags
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description) VALUES
('enforce_lunch_break', 'true', 'BOOLEAN', 'FEATURES', 'Whether to enforce lunch break constraint'),
('enforce_day_balance', 'true', 'BOOLEAN', 'FEATURES', 'Whether to apply day balance constraint'),
('same_course_same_day_allowed', 'false', 'BOOLEAN', 'FEATURES', 'Whether same course parts can be on same day');
