-- V4: Create course tables
-- Based on design.md database schema

-- Courses
CREATE TABLE course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    total_weekly_hours INT NOT NULL,
    lecturer_id BIGINT,
    student_group_id BIGINT,
    FOREIGN KEY (lecturer_id) REFERENCES lecturer(id),
    FOREIGN KEY (student_group_id) REFERENCES student_group(id)
);

-- Course required features junction table
CREATE TABLE course_feature (
    course_id BIGINT,
    feature_id BIGINT,
    PRIMARY KEY (course_id, feature_id),
    FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    FOREIGN KEY (feature_id) REFERENCES feature(id) ON DELETE CASCADE
);

-- Course allowed zones junction table
CREATE TABLE course_allowed_zone (
    course_id BIGINT,
    zone_id BIGINT,
    PRIMARY KEY (course_id, zone_id),
    FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    FOREIGN KEY (zone_id) REFERENCES zone(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_course_lecturer ON course(lecturer_id);
CREATE INDEX idx_course_student_group ON course(student_group_id);
CREATE INDEX idx_course_code ON course(code);
