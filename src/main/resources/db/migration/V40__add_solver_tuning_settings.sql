-- Add solver tuning settings so admins can tune speed/quality from Settings UI.
INSERT INTO constraint_setting (setting_key, setting_value, data_type, category, description)
VALUES
    ('solver_move_thread_count', '4', 'STRING', 'SYSTEM', 'Number of move threads for local search. Higher can be faster on multi-core CPUs. Requires backend restart to apply.'),
    ('solver_environment_mode', 'REPRODUCIBLE', 'STRING', 'SYSTEM', 'Solver execution mode. REPRODUCIBLE gives stable repeatable results; NON_REPRODUCIBLE may be faster. Requires backend restart.'),
    ('solver_parallel_solver_count', '1', 'STRING', 'SYSTEM', 'Parallel solver jobs capacity. Keep 1 unless you run multiple independent solve jobs. Requires backend restart.'),
    ('solver_minutes_spent_limit', '30', 'INTEGER', 'SYSTEM', 'Maximum total solver run time in minutes. Lower values stop earlier and return faster. Requires backend restart.'),
    ('solver_unimproved_seconds_spent_limit', '60', 'INTEGER', 'SYSTEM', 'Stop solver after this many seconds without improvement. Lower values speed up completion. Requires backend restart.'),
    ('solver_forager_accepted_count_limit', '1000', 'INTEGER', 'SYSTEM', 'How many accepted candidate moves are evaluated each local-search step. Lower values are faster; higher values can improve quality. Requires backend restart.'),
    ('solver_checkpoint_enabled', 'false', 'BOOLEAN', 'SYSTEM', 'Persist intermediate best solutions during solving. Can increase write load when enabled.'),
    ('solver_checkpoint_min_interval_ms', '120000', 'INTEGER', 'SYSTEM', 'Minimum interval between checkpoint saves in milliseconds.'),
    ('solver_checkpoint_every_n_improvements', '0', 'INTEGER', 'SYSTEM', 'Optional save cadence by improvement count. 0 disables this trigger.')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
