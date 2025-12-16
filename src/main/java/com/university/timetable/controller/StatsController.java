package com.university.timetable.controller;

import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StatsController {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final RoomRepository roomRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final ZoneRepository zoneRepository;
    private final FeatureRepository featureRepository;
    private final TimeslotRepository timeslotRepository;

    @GetMapping
    public StatsDTO getStats() {
        StatsDTO stats = new StatsDTO();
        stats.courseCount = courseRepository.count();
        stats.lessonCount = lessonRepository.count();
        stats.roomCount = roomRepository.count();
        stats.lecturerCount = lecturerRepository.count();
        stats.studentGroupCount = studentGroupRepository.count();
        stats.zoneCount = zoneRepository.count();
        stats.featureCount = featureRepository.count();
        stats.timeslotCount = timeslotRepository.count();
        
        // Calculate scheduled vs unscheduled
        stats.scheduledLessonCount = lessonRepository.findAll().stream()
                .filter(l -> l.getTimeslot() != null && l.getRoom() != null)
                .count();
        stats.unscheduledLessonCount = stats.lessonCount - stats.scheduledLessonCount;
        
        // Calculate pinned lessons
        stats.pinnedLessonCount = lessonRepository.findAll().stream()
                .filter(l -> l.isPinned())
                .count();
        
        return stats;
    }

    public static class StatsDTO {
        public long courseCount;
        public long lessonCount;
        public long roomCount;
        public long lecturerCount;
        public long studentGroupCount;
        public long zoneCount;
        public long featureCount;
        public long timeslotCount;
        public long scheduledLessonCount;
        public long unscheduledLessonCount;
        public long pinnedLessonCount;
    }
}

