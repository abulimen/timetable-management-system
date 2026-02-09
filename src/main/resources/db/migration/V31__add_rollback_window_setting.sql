-- Add configurable rollback window setting
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES 
    ('bulk_import_rollback_window_hours', '24', 'INTEGER', 'SYSTEM', 'Number of hours after an import during which a rollback is allowed. Set to -1 for unlimited.')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
