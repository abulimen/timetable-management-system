package com.university.timetable.controller;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Feature;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.LecturerUnavailability;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.Zone;
import com.university.timetable.dto.InfeasibilityIssue;
import com.university.timetable.dto.InfeasibilityReport;
import com.university.timetable.repository.CourseRepository;
import com.university.timetable.repository.FeatureRepository;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.TimeslotRepository;
import com.university.timetable.repository.ZoneRepository;
import com.university.timetable.service.ConstraintSettingsService;
import com.university.timetable.service.InfeasibilityChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InsightsController {

    private final ZoneRepository zoneRepository;
    private final RoomRepository roomRepository;
    private final FeatureRepository featureRepository;
    private final CourseRepository courseRepository;
    private final LecturerRepository lecturerRepository;
    private final LessonRepository lessonRepository;
    private final TimeslotRepository timeslotRepository;
    private final ConstraintSettingsService settingsService;
    private final InfeasibilityChecker infeasibilityChecker;

    @GetMapping("/zones/summary")
    @PreAuthorize("isAuthenticated()")
    public ZoneSummaryResponse zonesSummary() {
        List<Zone> zones = zoneRepository.findAll();
        List<Room> rooms = roomRepository.findAll();

        Map<Long, List<Room>> roomsByZone = rooms.stream()
                .filter(room -> room.getZone() != null)
                .collect(Collectors.groupingBy(room -> room.getZone().getId()));

        List<ZoneSummaryItem> items = zones.stream()
                .map(zone -> {
                    List<Room> zoneRooms = roomsByZone.getOrDefault(zone.getId(), List.of());
                    int roomCount = zoneRooms.size();
                    int capacity = zoneRooms.stream().mapToInt(Room::getCapacity).sum();
                    return new ZoneSummaryItem(zone.getId(), zone.getName(), roomCount, capacity);
                })
                .sorted(Comparator.comparing(ZoneSummaryItem::name))
                .toList();

        int totalZones = zones.size();
        int usedZones = (int) items.stream().filter(item -> item.roomCount() > 0).count();
        int totalCapacity = rooms.stream().mapToInt(Room::getCapacity).sum();

        return new ZoneSummaryResponse(
                totalZones,
                usedZones,
                totalZones - usedZones,
                rooms.size(),
                totalCapacity,
                items
        );
    }

    @GetMapping("/features/summary")
    @PreAuthorize("isAuthenticated()")
    public FeatureSummaryResponse featuresSummary() {
        List<Feature> features = featureRepository.findAll();
        List<Room> rooms = roomRepository.findAll();
        List<Course> courses = courseRepository.findAll();

        List<FeatureSummaryItem> items = features.stream()
                .map(feature -> {
                    String featureName = feature.getName();
                    int supplyCount = (int) rooms.stream()
                            .filter(room -> room.getFeatures() != null && room.getFeatures().stream()
                                    .anyMatch(f -> equalsIgnoreCase(f.getName(), featureName)))
                            .count();
                    int demandCount = (int) courses.stream()
                            .filter(course -> course.getRequiredFeatures() != null && course.getRequiredFeatures().stream()
                                    .anyMatch(f -> equalsIgnoreCase(f.getName(), featureName)))
                            .count();
                    boolean unboundedScarcity = supplyCount == 0 && demandCount > 0;
                    Double scarcityRatio = supplyCount == 0
                            ? (demandCount > 0 ? null : 0.0)
                            : (double) demandCount / supplyCount;
                    return new FeatureSummaryItem(
                            feature.getId(),
                            featureName,
                            supplyCount,
                            demandCount,
                            scarcityRatio,
                            unboundedScarcity
                    );
                })
                .sorted(Comparator.comparing(FeatureSummaryItem::name))
                .toList();

        int orphaned = (int) items.stream()
                .filter(item -> item.supplyCount() == 0 && item.demandCount() == 0)
                .count();

        return new FeatureSummaryResponse(features.size(), orphaned, items);
    }

    @GetMapping("/lecturers/summary")
    @PreAuthorize("isAuthenticated()")
    public LecturerSummaryResponse lecturersSummary() {
        List<Lecturer> lecturers = lecturerRepository.findAll();
        List<Course> courses = courseRepository.findAll();

        Map<Long, Long> assignmentCountByLecturerId = courses.stream()
                .filter(course -> course.getLecturer() != null)
                .collect(Collectors.groupingBy(course -> course.getLecturer().getId(), Collectors.counting()));

        int total = lecturers.size();
        int noEmail = (int) lecturers.stream().filter(lecturer -> isBlank(lecturer.getEmail())).count();
        int unassigned = (int) lecturers.stream()
                .filter(lecturer -> assignmentCountByLecturerId.getOrDefault(lecturer.getId(), 0L) == 0L)
                .count();

        final int overloadThreshold = 5;
        int overloaded = (int) lecturers.stream()
                .filter(lecturer -> assignmentCountByLecturerId.getOrDefault(lecturer.getId(), 0L) >= overloadThreshold)
                .count();

        LocalTime densityStart = settingsService.getEarliestStartTime();
        LocalTime densityEnd = settingsService.getLatestEndTime();
        final int densitySlotHours = 1;
        List<String> slots = buildDensitySlots(densityStart, densityEnd, densitySlotHours);
        List<DayOfWeek> days = List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
        );

        Map<String, Integer> density = new LinkedHashMap<>();
        for (DayOfWeek day : days) {
            for (String slot : slots) {
                density.put(day.name() + "|" + slot, 0);
            }
        }

        for (Lecturer lecturer : lecturers) {
            List<LecturerUnavailability> unavailabilities = lecturer.getUnavailabilities() == null
                    ? List.of()
                    : lecturer.getUnavailabilities();
            for (LecturerUnavailability unavailability : unavailabilities) {
                for (String slot : slots) {
                    if (isSlotCovered(unavailability.getStartTime(), unavailability.getEndTime(), slot)) {
                        String key = unavailability.getDayOfWeek().name() + "|" + slot;
                        density.computeIfPresent(key, (k, value) -> value + 1);
                    }
                }
            }
        }

        return new LecturerSummaryResponse(
                total,
                noEmail,
                unassigned,
                overloaded,
                overloadThreshold,
                densityStart.toString(),
                densityEnd.toString(),
                densitySlotHours,
                slots,
                density);
    }

    @GetMapping("/diagnostics/course-feasibility")
    @PreAuthorize("isAuthenticated()")
    public CourseFeasibilitySnapshot courseFeasibilitySnapshot() {
        InfeasibilityReport report = infeasibilityChecker.checkFeasibility();
        List<InfeasibilityIssue> criticalIssues = report.getIssues() == null
                ? List.of()
                : report.getIssues().stream().filter(issue -> "CRITICAL".equals(issue.getSeverity())).toList();
        List<InfeasibilityIssue> highIssues = report.getIssues() == null
                ? List.of()
                : report.getIssues().stream().filter(issue -> "HIGH".equals(issue.getSeverity())).toList();
        List<InfeasibilityIssue> mediumIssues = report.getIssues() == null
                ? List.of()
                : report.getIssues().stream().filter(issue -> "MEDIUM".equals(issue.getSeverity())).toList();
        
        // Combine critical and high as "blocking" for backward compatibility
        List<InfeasibilityIssue> blockingIssues = new java.util.ArrayList<>(criticalIssues);
        blockingIssues.addAll(highIssues);
        List<InfeasibilityIssue> warningIssues = mediumIssues;
        
        return new CourseFeasibilitySnapshot(
                report.isFeasible(),
                report.getLessonCount(),
                report.getTimeslotCount(),
                report.getRoomCount(),
                report.getAvailableRoomSlots(),
                report.getCriticalCount() + report.getHighCount(),
                report.getMediumCount() + report.getLowCount(),
                blockingIssues,
                warningIssues
        );
    }

    @GetMapping("/diagnostics/feature-scarcity")
    @PreAuthorize("isAuthenticated()")
    public FeatureScarcitySnapshot featureScarcitySnapshot() {
        FeatureSummaryResponse summary = featuresSummary();
        List<FeatureScarcityItem> items = summary.features().stream()
                .map(item -> {
                    String risk = "LOW";
                    if (item.unboundedScarcity()) {
                        risk = "CRITICAL";
                    } else if (item.scarcityRatio() != null && item.scarcityRatio() > 1.0) {
                        risk = "HIGH";
                    } else if (item.scarcityRatio() != null && item.scarcityRatio() >= 0.5) {
                        risk = "MEDIUM";
                    }
                    return new FeatureScarcityItem(
                            item.id(),
                            item.name(),
                            item.supplyCount(),
                            item.demandCount(),
                            item.scarcityRatio(),
                            item.unboundedScarcity(),
                            risk
                    );
                })
                .sorted(Comparator
                        .comparing((FeatureScarcityItem item) -> riskScore(item.risk())).reversed()
                        .thenComparing(FeatureScarcityItem::demandCount, Comparator.reverseOrder())
                        .thenComparing(FeatureScarcityItem::name))
                .toList();
        int criticalCount = (int) items.stream().filter(item -> "CRITICAL".equals(item.risk())).count();
        int highCount = (int) items.stream().filter(item -> "HIGH".equals(item.risk())).count();
        return new FeatureScarcitySnapshot(items.size(), criticalCount, highCount, items);
    }

    @GetMapping("/diagnostics/lecturer-load")
    @PreAuthorize("isAuthenticated()")
    public LecturerLoadSnapshot lecturerLoadSnapshot() {
        List<Lecturer> lecturers = lecturerRepository.findAll();
        List<Lesson> lessons = lessonRepository.findAll();
        int totalTimeslots = (int) timeslotRepository.count();

        Map<Long, Integer> assignedHoursByLecturer = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getLecturer() == null) {
                continue;
            }
            assignedHoursByLecturer.merge(
                    lesson.getLecturer().getId(),
                    lesson.getDurationHours(),
                    Integer::sum
            );
        }

        List<LecturerLoadItem> items = lecturers.stream()
                .map(lecturer -> {
                    int unavailable = lecturer.getUnavailabilities() == null ? 0 : lecturer.getUnavailabilities().size();
                    int availableHours = Math.max(0, totalTimeslots - unavailable);
                    int assignedHours = assignedHoursByLecturer.getOrDefault(lecturer.getId(), 0);
                    double loadRatio = availableHours > 0 ? (assignedHours * 1.0 / availableHours) : 0.0;
                    String risk = "LOW";
                    if (availableHours == 0 && assignedHours > 0) {
                        risk = "CRITICAL";
                    } else if (assignedHours > availableHours) {
                        risk = "CRITICAL";
                    } else if (loadRatio >= 0.9) {
                        risk = "HIGH";
                    } else if (loadRatio >= 0.75) {
                        risk = "MEDIUM";
                    }
                    return new LecturerLoadItem(
                            lecturer.getId(),
                            lecturer.getName(),
                            assignedHours,
                            availableHours,
                            unavailable,
                            loadRatio,
                            risk
                    );
                })
                .sorted(Comparator
                        .comparing((LecturerLoadItem item) -> riskScore(item.risk())).reversed()
                        .thenComparing(LecturerLoadItem::assignedHours, Comparator.reverseOrder())
                        .thenComparing(LecturerLoadItem::name))
                .toList();

        int criticalCount = (int) items.stream().filter(item -> "CRITICAL".equals(item.risk())).count();
        int highCount = (int) items.stream().filter(item -> "HIGH".equals(item.risk())).count();
        return new LecturerLoadSnapshot(totalTimeslots, criticalCount, highCount, items);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isSlotCovered(LocalTime start, LocalTime end, String slot) {
        LocalTime point = LocalTime.parse(slot);
        return !point.isBefore(start) && point.isBefore(end);
    }

    private static List<String> buildDensitySlots(LocalTime start, LocalTime end, int intervalHours) {
        if (start == null || end == null || !start.isBefore(end) || intervalHours <= 0) {
            return List.of("08:00", "10:00", "12:00", "14:00", "16:00");
        }
        List<String> slots = new ArrayList<>();
        LocalTime current = start;
        while (current.isBefore(end)) {
            slots.add(current.toString());
            current = current.plusHours(intervalHours);
        }
        return slots;
    }

    private static int riskScore(String risk) {
        if ("CRITICAL".equals(risk)) {
            return 3;
        }
        if ("HIGH".equals(risk)) {
            return 2;
        }
        if ("MEDIUM".equals(risk)) {
            return 1;
        }
        return 0;
    }

    public record ZoneSummaryItem(Long id, String name, int roomCount, int capacity) {
    }

    public record ZoneSummaryResponse(
            int totalZones,
            int usedZones,
            int unusedZones,
            int totalRooms,
            int totalCapacity,
            List<ZoneSummaryItem> zones
    ) {
    }

    public record FeatureSummaryItem(
            Long id,
            String name,
            int supplyCount,
            int demandCount,
            Double scarcityRatio,
            boolean unboundedScarcity
    ) {
    }

    public record FeatureSummaryResponse(
            int totalFeatures,
            int orphanedFeatures,
            List<FeatureSummaryItem> features
    ) {
    }

    public record LecturerSummaryResponse(
            int totalLecturers,
            int noEmailCount,
            int unassignedCount,
            int overloadedCount,
            int overloadThreshold,
            String densityStartTime,
            String densityEndTime,
            int densitySlotHours,
            List<String> densitySlots,
            Map<String, Integer> unavailabilityDensity
    ) {
    }

    public record CourseFeasibilitySnapshot(
            boolean feasible,
            int lessonCount,
            int timeslotCount,
            int roomCount,
            int availableRoomSlots,
            int blockingCount,
            int warningCount,
            List<InfeasibilityIssue> blockingIssues,
            List<InfeasibilityIssue> warningIssues
    ) {
    }

    public record FeatureScarcityItem(
            Long id,
            String name,
            int supplyCount,
            int demandCount,
            Double scarcityRatio,
            boolean unboundedScarcity,
            String risk
    ) {
    }

    public record FeatureScarcitySnapshot(
            int totalFeatures,
            int criticalCount,
            int highCount,
            List<FeatureScarcityItem> items
    ) {
    }

    public record LecturerLoadItem(
            Long id,
            String name,
            int assignedHours,
            int availableHours,
            int unavailableSlots,
            double loadRatio,
            String risk
    ) {
    }

    public record LecturerLoadSnapshot(
            int totalTimeslots,
            int criticalCount,
            int highCount,
            List<LecturerLoadItem> items
    ) {
    }
}
