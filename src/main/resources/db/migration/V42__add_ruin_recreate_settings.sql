-- Ruin-and-Recreate solver settings
-- These control the intelligent ruin-and-recreate move mechanism that helps
-- resolve circular deadlocks in large timetables.

INSERT IGNORE INTO constraint_setting (setting_key, setting_value, description, category, data_type)
VALUES
    ('solver_ruin_recreate_enabled', 'false',
     'Enable deep restructuring (Ruin & Recreate). When enabled, the solver detects conflict clusters and rebuilds them from scratch. Recommended for large timetables (300+ lessons).',
     'SYSTEM', 'BOOLEAN');

INSERT IGNORE INTO constraint_setting (setting_key, setting_value, description, category, data_type)
VALUES
    ('solver_ruin_recreate_cluster_size', '8',
     'How many conflicting lessons to pull out and rebuild at once. The solver targets the most problematic lessons. Recommended: 6-12.',
     'SYSTEM', 'INTEGER');
