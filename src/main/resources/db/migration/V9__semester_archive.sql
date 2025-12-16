-- V9: Semester Archive Support
-- Metadata table to track archived semesters

CREATE TABLE semester_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,           -- "2024_2025_S1"
    name VARCHAR(100) NOT NULL,                 -- "2024/2025 1st Semester"
    academic_year VARCHAR(20),                  -- "2024/2025"
    semester_number INT,                        -- 1 or 2
    archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tables_prefix VARCHAR(50) NOT NULL,         -- "2024_2025_S1"
    course_count INT DEFAULT 0,
    lesson_count INT DEFAULT 0,
    student_group_count INT DEFAULT 0,
    lecturer_count INT DEFAULT 0
);

-- Index for quick lookup
CREATE INDEX idx_archive_code ON semester_archive(code);
