-- Add availability deadline setting
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES ('availability_deadline', '', 'STRING', 'SYSTEM', 
        'Deadline for lecturers to submit availability. Format: YYYY-MM-DD. Leave empty for no deadline.')
ON DUPLICATE KEY UPDATE description = 'Deadline for lecturers to submit availability. Format: YYYY-MM-DD. Leave empty for no deadline.';
