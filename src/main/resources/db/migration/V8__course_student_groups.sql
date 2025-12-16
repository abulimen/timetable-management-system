-- V8__course_student_groups.sql
-- Support multiple student groups per course (combined classes)

-- Create join table for course-studentgroup many-to-many relationship
CREATE TABLE course_student_group (
    course_id BIGINT NOT NULL,
    student_group_id BIGINT NOT NULL,
    PRIMARY KEY (course_id, student_group_id),
    FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    FOREIGN KEY (student_group_id) REFERENCES student_group(id) ON DELETE CASCADE
);

-- Migrate existing single studentGroup data to the new join table
INSERT INTO course_student_group (course_id, student_group_id)
SELECT id, student_group_id FROM course WHERE student_group_id IS NOT NULL;

-- Note: We keep student_group_id column for backward compatibility
-- It can be removed in a future migration after verifying the new system works
