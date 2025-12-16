-- V3: Create student group table with hierarchy
-- Based on design.md: StudentGroup has recursive parent-child relationship

CREATE TABLE student_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    size INT NOT NULL,
    parent_group_id BIGINT,
    FOREIGN KEY (parent_group_id) REFERENCES student_group(id) ON DELETE SET NULL
);

-- Indexes for hierarchy traversal
CREATE INDEX idx_student_group_parent ON student_group(parent_group_id);
CREATE INDEX idx_student_group_name ON student_group(name);
