-- Add unavailability system control settings
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES 
    ('unavailability_system_enabled', 'false', 'BOOLEAN', 'FEATURES', 'Whether the unavailability system is enabled. When disabled, lecturers see nothing and solver ignores unavailability records.'),
    ('unavailability_requests_open', 'false', 'BOOLEAN', 'FEATURES', 'Whether lecturers can submit new unavailability requests. When closed, no new requests can be created.')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
