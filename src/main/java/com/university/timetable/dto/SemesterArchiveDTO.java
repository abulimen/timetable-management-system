package com.university.timetable.dto;

import com.university.timetable.domain.SemesterArchive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for semester archive.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemesterArchiveDTO {
    
    private Long id;
    private String code;
    private String name;
    private String academicYear;
    private Integer semesterNumber;
    private LocalDateTime archivedAt;
    private Integer courseCount;
    private Integer lessonCount;
    private Integer studentGroupCount;
    private Integer lecturerCount;
    
    public static SemesterArchiveDTO fromEntity(SemesterArchive entity) {
        return new SemesterArchiveDTO(
            entity.getId(),
            entity.getCode(),
            entity.getName(),
            entity.getAcademicYear(),
            entity.getSemesterNumber(),
            entity.getArchivedAt(),
            entity.getCourseCount(),
            entity.getLessonCount(),
            entity.getStudentGroupCount(),
            entity.getLecturerCount()
        );
    }
}
