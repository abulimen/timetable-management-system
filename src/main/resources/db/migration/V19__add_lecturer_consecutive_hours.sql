-- Add max_lecturer_consecutive_hours setting
INSERT IGNORE INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES ('max_lecturer_consecutive_hours', '4', 'INTEGER', 'LIMITS', 'Maximum consecutive teaching hours for lecturers without break');
