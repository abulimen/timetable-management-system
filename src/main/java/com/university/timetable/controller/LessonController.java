package com.university.timetable.controller;

import com.university.timetable.domain.Lesson;
import com.university.timetable.dto.LessonUpdateDTO;
import com.university.timetable.dto.TimetableViewDTO;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * LessonController - manages individual lessons.
 * 
 * Based on design.md API Specification:
 * PATCH /api/v1/lessons/{id} - Update lesson (pin, assign timeslot/room)
 */
@RestController
@RequestMapping("/api/v1/lessons")
@RequiredArgsConstructor
@Slf4j
public class LessonController {

    private final LessonRepository lessonRepository;
    private final TimeslotRepository timeslotRepository;
    private final RoomRepository roomRepository;

    /**
     * PATCH /api/v1/lessons/{id}
     * Body: {"assigned_timeslot": "...", "pinned": true}
     * Action: Updates DB, sets is_pinned=true.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateLesson(
            @PathVariable Long id,
            @RequestBody LessonUpdateDTO updateDTO) {
        
        log.info("Updating lesson {}: {}", id, updateDTO);
        
        Lesson lesson = lessonRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + id));
        
        // Update timeslot if provided
        if (updateDTO.getAssignedTimeslotId() != null) {
            lesson.setTimeslot(
                timeslotRepository.findById(updateDTO.getAssignedTimeslotId())
                    .orElseThrow(() -> new IllegalArgumentException("Timeslot not found"))
            );
        }
        
        // Update room if provided
        if (updateDTO.getAssignedRoomId() != null) {
            lesson.setRoom(
                roomRepository.findById(updateDTO.getAssignedRoomId())
                    .orElseThrow(() -> new IllegalArgumentException("Room not found"))
            );
        }
        
        // Update pinned status if provided
        if (updateDTO.getPinned() != null) {
            lesson.setPinned(updateDTO.getPinned());
        }
        
        Lesson saved = lessonRepository.save(lesson);
        log.info("Updated lesson: {}", saved);
        
        return ResponseEntity.ok(toDTO(saved));
    }

    /**
     * GET /api/v1/lessons/{id}
     * Get a single lesson by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TimetableViewDTO> getLesson(@PathVariable Long id) {
        Lesson lesson = lessonRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + id));
        return ResponseEntity.ok(toDTO(lesson));
    }

    private TimetableViewDTO toDTO(Lesson lesson) {
        TimetableViewDTO dto = new TimetableViewDTO();
        dto.setLessonId(lesson.getId());
        dto.setPartNumber(lesson.getPartNumber());
        dto.setDurationHours(lesson.getDurationHours());
        dto.setPinned(lesson.isPinned());
        dto.setScheduled(lesson.getTimeslot() != null && lesson.getRoom() != null);
        
        if (lesson.getCourse() != null) {
            dto.setCourseCode(lesson.getCourse().getCode());
            dto.setCourseName(lesson.getCourse().getName());
        }
        if (lesson.getTimeslot() != null) {
            dto.setDayOfWeek(lesson.getTimeslot().getDayOfWeek());
            dto.setStartTime(lesson.getTimeslot().getStartTime());
            dto.setEndTime(lesson.getEndTime());
        }
        if (lesson.getRoom() != null) {
            dto.setRoomId(lesson.getRoom().getId());
            dto.setRoomName(lesson.getRoom().getName());
            dto.setRoomCapacity(lesson.getRoom().getCapacity());
        }
        if (lesson.getLecturer() != null) {
            dto.setLecturerId(lesson.getLecturer().getId());
            dto.setLecturerName(lesson.getLecturer().getName());
        }
        if (lesson.getStudentGroup() != null) {
            dto.setStudentGroupId(lesson.getStudentGroup().getId());
            dto.setStudentGroupName(lesson.getStudentGroup().getName());
            dto.setStudentGroupSize(lesson.getStudentGroup().getSize());
        }
        
        return dto;
    }
}
