INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES
    ('solver_adaptive_limits_enabled', 'true', 'BOOLEAN', 'SYSTEM',
     'When true, solver applies adaptive runtime and no-improvement limits based on dataset size/profile.'),
    ('solver_adaptive_search_breadth_enabled', 'true', 'BOOLEAN', 'SYSTEM',
     'When true, solver adaptively scales search breadth (accepted count limit) by dataset size/profile.')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
