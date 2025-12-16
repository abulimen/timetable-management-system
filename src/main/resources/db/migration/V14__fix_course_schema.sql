-- V14: Fix course schema for realistic university data model
-- Remove UNIQUE constraint on course.code to allow same course for multiple groups

-- Drop the unique constraint on course code
ALTER TABLE course DROP INDEX code;

-- Create a non-unique index for performance
CREATE INDEX idx_course_code_lookup ON course(code);
