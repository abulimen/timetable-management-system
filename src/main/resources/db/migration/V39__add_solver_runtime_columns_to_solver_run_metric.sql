SET @schema_name := DATABASE();

SET @has_profile := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'solver_run_metric'
     AND COLUMN_NAME = 'profile'
);
SET @sql := IF(@has_profile = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN profile VARCHAR(32) NULL AFTER mode',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_move_thread := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'solver_run_metric'
     AND COLUMN_NAME = 'move_thread_count'
);
SET @sql := IF(@has_move_thread = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN move_thread_count VARCHAR(32) NULL AFTER error_message',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_environment_mode := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'solver_run_metric'
     AND COLUMN_NAME = 'environment_mode'
);
SET @sql := IF(@has_environment_mode = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN environment_mode VARCHAR(32) NULL AFTER move_thread_count',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_parallel_solver := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'solver_run_metric'
     AND COLUMN_NAME = 'parallel_solver_count'
);
SET @sql := IF(@has_parallel_solver = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN parallel_solver_count VARCHAR(32) NULL AFTER environment_mode',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_processors := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'solver_run_metric'
     AND COLUMN_NAME = 'available_processors'
);
SET @sql := IF(@has_processors = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN available_processors INT NULL AFTER parallel_solver_count',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
