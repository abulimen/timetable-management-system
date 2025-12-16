-- Add friday_latest_end_time setting for dynamic timeslot generation
INSERT IGNORE INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES (
    'friday_latest_end_time',
    '12:00',
    'TIME',
    'TIMING',
    'Latest time lessons can end on Friday'
);

