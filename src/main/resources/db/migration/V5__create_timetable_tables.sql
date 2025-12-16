-- V5: Create timeslot and lesson tables
-- Based on design.md database schema

-- Timeslots (valid scheduling slots)
CREATE TABLE timeslot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    UNIQUE KEY unique_slot (day_of_week, start_time)
);

-- Lessons (Planning Entities - split from courses)
CREATE TABLE lesson (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    duration_hours INT NOT NULL DEFAULT 1,
    part_number INT NOT NULL DEFAULT 1,
    lecturer_id BIGINT,
    assigned_timeslot_id BIGINT,
    assigned_room_id BIGINT,
    is_pinned BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    FOREIGN KEY (lecturer_id) REFERENCES lecturer(id),
    FOREIGN KEY (assigned_timeslot_id) REFERENCES timeslot(id),
    FOREIGN KEY (assigned_room_id) REFERENCES room(id)
);

-- Indexes for lesson queries (as specified in design.md)
CREATE INDEX idx_lesson_course ON lesson(course_id);
CREATE INDEX idx_lesson_timeslot ON lesson(assigned_timeslot_id);
CREATE INDEX idx_lesson_room ON lesson(assigned_room_id);
CREATE INDEX idx_lesson_lecturer ON lesson(lecturer_id);
CREATE INDEX idx_lesson_pinned ON lesson(is_pinned);
