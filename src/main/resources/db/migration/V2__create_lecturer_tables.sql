-- V2: Create lecturer tables
-- Based on design.md database schema

-- Lecturers
CREATE TABLE lecturer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255)
);

-- Lecturer unavailability (blackout periods)
CREATE TABLE lecturer_unavailability (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lecturer_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    FOREIGN KEY (lecturer_id) REFERENCES lecturer(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_unavail_lecturer ON lecturer_unavailability(lecturer_id);
CREATE INDEX idx_unavail_day ON lecturer_unavailability(day_of_week);
