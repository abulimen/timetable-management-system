INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES ('solver_runtime_limit_enabled', 'true', 'BOOLEAN', 'SYSTEM',
        'When false, solver max runtime limit is disabled and solve can run indefinitely.')
ON DUPLICATE KEY UPDATE
description = VALUES(description);
