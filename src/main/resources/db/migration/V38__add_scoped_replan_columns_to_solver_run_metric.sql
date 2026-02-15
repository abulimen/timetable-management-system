SET @schema_name = DATABASE();

SET @has_impacted := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'solver_run_metric'
      AND COLUMN_NAME = 'impacted_lessons_count'
);
SET @sql := IF(@has_impacted = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN impacted_lessons_count INT NULL AFTER duration_ms',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_locked := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'solver_run_metric'
      AND COLUMN_NAME = 'locked_lessons_count'
);
SET @sql := IF(@has_locked = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN locked_lessons_count INT NULL AFTER impacted_lessons_count',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_changed := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'solver_run_metric'
      AND COLUMN_NAME = 'changed_lessons_count'
);
SET @sql := IF(@has_changed = 0,
    'ALTER TABLE solver_run_metric ADD COLUMN changed_lessons_count INT NULL AFTER locked_lessons_count',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
