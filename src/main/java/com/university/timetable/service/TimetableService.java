package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.dto.TimetableViewDTO;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TimetableService - provides timetable views with filters.
 * Based on design.md Timetable Visualization API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableService {

    private final LessonRepository lessonRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final LecturerRepository lecturerRepository;
    private final RoomRepository roomRepository;

    /**
     * Get all lessons as timetable view DTOs.
     */
    public List<TimetableViewDTO> getAllLessons() {
        return lessonRepository.findAll().stream()
            .map(this::toViewDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get lessons for a specific student group.
     * Based on design.md: SELECT * FROM Timetable WHERE student_group_id = ?
     */
    public List<TimetableViewDTO> getLessonsByStudentGroup(Long studentGroupId) {
        StudentGroup group = studentGroupRepository.findById(studentGroupId)
            .orElseThrow(() -> new IllegalArgumentException("Student group not found: " + studentGroupId));
        
        return lessonRepository.findByStudentGroup(group).stream()
            .map(this::toViewDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get lessons for a specific lecturer.
     * Based on design.md: SELECT * FROM Timetable WHERE teacher_id = ?
     */
    public List<TimetableViewDTO> getLessonsByLecturer(Long lecturerId) {
        Lecturer lecturer = lecturerRepository.findById(lecturerId)
            .orElseThrow(() -> new IllegalArgumentException("Lecturer not found: " + lecturerId));
        
        return lessonRepository.findByLecturer(lecturer).stream()
            .map(this::toViewDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get lessons for a specific room.
     */
    public List<TimetableViewDTO> getLessonsByRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        
        return lessonRepository.findByRoom(room).stream()
            .map(this::toViewDTO)
            .collect(Collectors.toList());
    }

    /**
     * Convert Lesson entity to TimetableViewDTO.
     */
    private TimetableViewDTO toViewDTO(Lesson lesson) {
        TimetableViewDTO dto = new TimetableViewDTO();
        
        dto.setLessonId(lesson.getId());
        dto.setPartNumber(lesson.getPartNumber());
        dto.setDurationHours(lesson.getDurationHours());
        dto.setPinned(lesson.isPinned());
        dto.setOnline(lesson.isOnline());
        // Online lessons are scheduled if they have a timeslot (no room required)
        dto.setScheduled(lesson.getTimeslot() != null && (lesson.isOnline() || lesson.getRoom() != null));
        
        // Course
        if (lesson.getCourse() != null) {
            dto.setCourseCode(lesson.getCourse().getCode());
            dto.setCourseName(lesson.getCourse().getName());
        }
        
        // Timeslot
        if (lesson.getTimeslot() != null) {
            dto.setDayOfWeek(lesson.getTimeslot().getDayOfWeek());
            dto.setStartTime(lesson.getTimeslot().getStartTime());
            dto.setEndTime(lesson.getEndTime());
        }
        
        // Room
        if (lesson.getRoom() != null) {
            dto.setRoomId(lesson.getRoom().getId());
            dto.setRoomName(lesson.getRoom().getName());
            dto.setRoomCapacity(lesson.getRoom().getCapacity());
        }
        
        // Lecturer
        if (lesson.getLecturer() != null) {
            dto.setLecturerId(lesson.getLecturer().getId());
            dto.setLecturerName(lesson.getLecturer().getName());
        }
        
        // Student Group
        StudentGroup group = lesson.getStudentGroup();
        if (group != null) {
            dto.setStudentGroupId(group.getId());
            dto.setStudentGroupName(group.getName());
            dto.setStudentGroupSize(group.getSize());
        }
        
        return dto;
    }
}
