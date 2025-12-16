-- Add weight_early_morning setting for configuring 7am class penalty
INSERT IGNORE INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES (
    'weight_early_morning',
    '3',
    'INTEGER',
    'WEIGHTS',
    'Penalty weight for scheduling classes at 7am (higher = more avoidance)'
);
