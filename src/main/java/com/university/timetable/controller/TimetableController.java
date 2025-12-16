package com.university.timetable.controller;

import com.university.timetable.dto.TimetableViewDTO;
import com.university.timetable.service.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TimetableController - retrieves generated timetables.
 * 
 * Based on design.md API Specification:
 * GET /api/v1/timetable - Get timetable with optional filters
 */
@RestController
@RequestMapping("/api/v1/timetable")
@RequiredArgsConstructor
@Slf4j
public class TimetableController {

    private final TimetableService timetableService;

    /**
     * GET /api/v1/timetable
     * Params: student_group_id, lecturer_id, room_id
     * Response: JSON array of scheduled lessons.
     */
    @GetMapping
    public ResponseEntity<List<TimetableViewDTO>> getTimetable(
            @RequestParam(value = "student_group_id", required = false) Long studentGroupId,
            @RequestParam(value = "lecturer_id", required = false) Long lecturerId,
            @RequestParam(value = "room_id", required = false) Long roomId) {
        
        List<TimetableViewDTO> lessons;
        
        if (studentGroupId != null) {
            log.info("Fetching timetable for student group: {}", studentGroupId);
            lessons = timetableService.getLessonsByStudentGroup(studentGroupId);
        } else if (lecturerId != null) {
            log.info("Fetching timetable for lecturer: {}", lecturerId);
            lessons = timetableService.getLessonsByLecturer(lecturerId);
        } else if (roomId != null) {
            log.info("Fetching timetable for room: {}", roomId);
            lessons = timetableService.getLessonsByRoom(roomId);
        } else {
            log.info("Fetching complete timetable");
            lessons = timetableService.getAllLessons();
        }
        
        return ResponseEntity.ok(lessons);
    }
}
