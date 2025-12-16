package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadata entity tracking archived semesters.
 * Each archive creates tables with prefix like "2024_2025_S1_course".
 */
@Entity
@Table(name = "semester_archive")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemesterArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;  // "2024_2025_S1"

    @Column(nullable = false)
    private String name;  // "2024/2025 1st Semester"

    @Column(name = "academic_year")
    private String academicYear;  // "2024/2025"

    @Column(name = "semester_number")
    private Integer semesterNumber;  // 1 or 2

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "tables_prefix", nullable = false)
    private String tablesPrefix;  // "2024_2025_S1"

    @Column(name = "course_count")
    private Integer courseCount;

    @Column(name = "lesson_count")
    private Integer lessonCount;

    @Column(name = "student_group_count")
    private Integer studentGroupCount;

    @Column(name = "lecturer_count")
    private Integer lecturerCount;

    @PrePersist
    public void prePersist() {
        if (archivedAt == null) {
            archivedAt = LocalDateTime.now();
        }
        if (tablesPrefix == null) {
            tablesPrefix = code;
        }
    }
}
