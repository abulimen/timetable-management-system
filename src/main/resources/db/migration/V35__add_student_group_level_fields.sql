-- Add level and group fields to student_group table
ALTER TABLE student_group
    ADD COLUMN base_name VARCHAR(255) NOT NULL DEFAULT 'Unknown',
    ADD COLUMN level INT NOT NULL DEFAULT 100,
    ADD COLUMN group_notation VARCHAR(50) DEFAULT NULL;

-- Migrate existing data: for now, just use the existing name as base_name
-- Since we deleted all groups earlier, this won't affect anything
UPDATE student_group
SET base_name = name,
    level = 100
WHERE base_name = 'Unknown';

-- Remove default from base_name (MySQL syntax)
ALTER TABLE student_group
    MODIFY COLUMN base_name VARCHAR(255) NOT NULL;
