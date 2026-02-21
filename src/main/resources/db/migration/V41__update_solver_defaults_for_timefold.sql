-- Update solver tuning defaults to match Timefold Community Edition + performance optimizations.
-- V40 originally set these. This migration updates them for existing databases.
UPDATE constraint_setting
SET setting_value = '4',
    description = 'Moves evaluated per local-search step. Recommended: 2-8. Higher wastes cycles with diminishing returns. Requires backend restart.'
WHERE setting_key = 'solver_forager_accepted_count_limit';

UPDATE constraint_setting
SET setting_value = 'NON_REPRODUCIBLE',
    description = 'Solver execution mode. NON_REPRODUCIBLE is 15-25% faster. REPRODUCIBLE adds overhead for deterministic results. Requires backend restart.'
WHERE setting_key = 'solver_environment_mode';

UPDATE constraint_setting
SET description = 'Multi-threaded solving requires Timefold Enterprise. Community edition runs single-threaded. Requires backend restart.'
WHERE setting_key = 'solver_move_thread_count';
