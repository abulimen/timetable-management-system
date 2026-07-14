package com.university.timetable.service;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.IntervalVar;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.Literal;
import com.university.timetable.domain.Course;
import com.university.timetable.domain.Feature;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.SpecialEvent;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.domain.Timeslot;
import com.university.timetable.domain.Zone;
import com.university.timetable.dto.InfeasibilityIssue;
import com.university.timetable.dto.InfeasibilityReport;
import com.university.timetable.dto.LessonBreakdownDTO;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.SpecialEventRepository;
import com.university.timetable.repository.TimeslotRepository;
import com.university.timetable.service.ConstraintSettingsService;
import com.university.timetable.service.TimeslotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CP-SAT based feasibility checker that uses the EXACT same constraint logic
 * as the solver to detect infeasibility issues accurately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CpSatFeasibilityChecker {

    private final LessonRepository lessonRepository;
    private final TimeslotRepository timeslotRepository;
    private final RoomRepository roomRepository;
    private final SpecialEventRepository specialEventRepository;
    private final ConstraintSettingsService constraintSettingsService;
    private final TimeslotService timeslotService;
    
    private boolean isSameCourseSameDayAllowed() {
        return constraintSettingsService.isSameCourseSameDayAllowed();
    }
    
    static {
        Loader.loadNativeLibraries();
    }

    @Transactional
    public InfeasibilityReport checkFeasibility() {
        long startMs = System.currentTimeMillis();
        log.info("CP-SAT Feasibility: Starting feasibility probe...");

        List<Timeslot> timeslots = timeslotService.ensureTimeslotsMatchSettings();
        List<Lesson> lessons = lessonRepository.findAllWithCourseAndLecturer();
        List<Room> rooms = roomRepository.findAllWithFeatures();
        List<SpecialEvent> specialEvents = specialEventRepository.findByActiveTrue();
        
        log.info("CP-SAT Feasibility: Loaded {} lessons, {} timeslots, {} rooms, {} special events",
                lessons.size(), timeslots.size(), rooms.size(), specialEvents.size());

        // Build index mappings
        Map<Long, Integer> timeslotToIndex = new HashMap<>();
        Map<DayOfWeek, Integer> dayStartIndex = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> dayEndIndex = new EnumMap<>(DayOfWeek.class);
        
        int timeIndex = 0;
        for (DayOfWeek day : Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            dayStartIndex.put(day, timeIndex);
            for (Timeslot ts : timeslots) {
                if (ts.getDayOfWeek() == day) {
                    timeslotToIndex.put(ts.getId(), timeIndex);
                    timeIndex++;
                }
            }
            dayEndIndex.put(day, timeIndex);
        }
        int totalTimeSlots = timeIndex;

        List<Room> physicalRooms = rooms.stream().filter(r -> r.getId() != null).toList();
        Map<Long, Integer> roomToIndex = new HashMap<>();
        for (int i = 0; i < physicalRooms.size(); i++) {
            roomToIndex.put(physicalRooms.get(i).getId(), i);
        }

        List<InfeasibilityIssue> issues = new ArrayList<>();
        Map<Long, Boolean> courseHasIssue = new HashMap<>();
        int lessonsWithNoRooms = 0;
        
        // Check room availability
        for (Lesson lesson : lessons) {
            if (lesson.isOnline()) continue;
            
            int students = lesson.getTotalStudentCount();
            Course course = lesson.getCourse();
            int validRoomCount = 0;
            int filteredByCapacity = 0, filteredByFeatures = 0, filteredByZone = 0;
            
            for (Room room : physicalRooms) {
                if (room.getCapacity() < students) { filteredByCapacity++; continue; }
                if (course != null && course.getRequiredFeatures() != null && !course.getRequiredFeatures().isEmpty()) {
                    if (!room.hasAllFeatures(course.getRequiredFeatures())) { filteredByFeatures++; continue; }
                }
                if (course != null && course.getAllowedZones() != null && !course.getAllowedZones().isEmpty()) {
                    if (room.getZone() == null || !course.getAllowedZones().contains(room.getZone())) { filteredByZone++; continue; }
                }
                validRoomCount++;
            }
            
            if (validRoomCount == 0) {
                lessonsWithNoRooms++;
                String courseCode = course != null ? course.getCode() : "UNKNOWN";
                StringBuilder description = new StringBuilder();
                StringBuilder recommendation = new StringBuilder();
                description.append("The course '").append(courseCode).append("' has ").append(students).append(" students, but ");
                
                List<String> problems = new ArrayList<>();
                List<String> solutions = new ArrayList<>();
                
                if (filteredByCapacity > 0 && filteredByCapacity == physicalRooms.size()) {
                    problems.add("all rooms are too small");
                    solutions.add("add a larger room that can hold at least " + students + " students");
                } else if (filteredByFeatures > 0 && filteredByZone > 0) {
                    String features = course.getRequiredFeatures().stream().map(Feature::getName).collect(Collectors.joining(" and "));
                    String zones = course.getAllowedZones().stream().map(Zone::getName).collect(Collectors.joining(" or "));
                    problems.add("no room has the required features (" + features + ") AND is in the right building (" + zones + ")");
                    solutions.add("add a room with " + features + " in " + zones);
                } else if (filteredByFeatures > 0) {
                    String features = course.getRequiredFeatures().stream().map(Feature::getName).collect(Collectors.joining(" and "));
                    problems.add("no room has the required features: " + features);
                    solutions.add("add a room with " + features);
                } else if (filteredByZone > 0) {
                    String zones = course.getAllowedZones().stream().map(Zone::getName).collect(Collectors.joining(" or "));
                    problems.add("no room is in the required building: " + zones);
                    solutions.add("add a room in " + zones);
                }
                
                description.append(String.join(", ", problems)).append(".");
                recommendation.append("To fix this, ").append(String.join(" or ", solutions)).append(".");
                
                Long courseId = course != null ? course.getId() : -1L;
                if (!courseHasIssue.containsKey(courseId)) {
                    courseHasIssue.put(courseId, true);
                    issues.add(InfeasibilityIssue.blocking("NO_VALID_ROOM", description.toString(), recommendation.toString()));
                }
            }
        }

        if (lessonsWithNoRooms > 0) {
            log.warn("CP-SAT Feasibility: {} lessons have no valid rooms", lessonsWithNoRooms);
            InfeasibilityReport report = InfeasibilityReport.infeasible(lessons.size(), timeslots.size(), rooms.size(), issues);
            report.setAnalysisText("CRITICAL: " + lessonsWithNoRooms + " lessons have no valid rooms. Add rooms with required features/zones.");
            return report;
        }
        
        // Check same-course-same-day constraint (per group, not per course)
        // MTH101 for GRP A and GRP B can be on same day, but GRP A's lessons must be on different days
        if (!isSameCourseSameDayAllowed()) {
            Map<String, List<Lesson>> lessonsByCourseAndGroup = new HashMap<>();
            for (Lesson lesson : lessons) {
                if (lesson.getCourse() == null || lesson.getTimeslot() == null) continue;
                if (lesson.getStudentGroups() == null || lesson.getStudentGroups().isEmpty()) continue;
                
                DayOfWeek day = lesson.getTimeslot().getDayOfWeek();
                for (StudentGroup group : lesson.getStudentGroups()) {
                    String key = lesson.getCourse().getId() + "_" + group.getId() + "_" + day;
                    lessonsByCourseAndGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(lesson);
                }
            }
            
            for (Map.Entry<String, List<Lesson>> entry : lessonsByCourseAndGroup.entrySet()) {
                if (entry.getValue().size() > 1) {
                    Lesson first = entry.getValue().get(0);
                    String courseCode = first.getCourse().getCode();
                    String groupName = first.getStudentGroups().stream().map(StudentGroup::getName).collect(Collectors.joining(", "));
                    String dayName = first.getTimeslot().getDayOfWeek().toString();
                    
                    issues.add(InfeasibilityIssue.blocking("SAME_COURSE_SAME_DAY",
                        String.format("Course %s for group %s has %d lessons on %s - must be on different days",
                            courseCode, groupName, entry.getValue().size(), dayName),
                        String.format("Move some %s lessons for %s to a different day", courseCode, groupName)));
                }
            }
            
            if (!issues.isEmpty()) {
                log.warn("CP-SAT Feasibility: Same-course-same-day violations detected");
                InfeasibilityReport report = InfeasibilityReport.infeasible(lessons.size(), timeslots.size(), rooms.size(), issues);
                report.setAnalysisText("CRITICAL: Same-course-same-day constraint violations detected in existing schedule.");
                return report;
            }
        }
        
        // Build full CP-SAT model with hard constraints
        CpModel model = new CpModel();
        int n = lessons.size();
        IntVar[] startVars = new IntVar[n];
        IntVar[] roomVars = new IntVar[n];
        IntervalVar[] intervals = new IntervalVar[n];
        
        for (int i = 0; i < n; i++) {
            Lesson lesson = lessons.get(i);
            List<Integer> validStarts = computeValidStarts(lesson, timeslots, timeslotToIndex, dayStartIndex, dayEndIndex, specialEvents);
            if (validStarts.isEmpty()) {
                issues.add(InfeasibilityIssue.critical("NO_VALID_TIMESLOT",
                    "The course '" + (lesson.getCourse() != null ? lesson.getCourse().getCode() : "UNKNOWN") + "' has no available time slots.",
                    "Check if the course conflicts with special events or exceeds allowed time limits."));
                InfeasibilityReport report = InfeasibilityReport.infeasible(lessons.size(), timeslots.size(), rooms.size(), issues);
                report.setAnalysisText("CRITICAL: Some lessons have no valid timeslots due to special events or time restrictions.");
                return report;
            }
            
            // Create variable with min/max range, then restrict to valid values
            int minStart = Collections.min(validStarts);
            int maxStart = Collections.max(validStarts);
            startVars[i] = model.newIntVar(minStart, maxStart, "start_" + lesson.getId());
            
            // Forbid any value not in validStarts
            Set<Integer> validSet = new HashSet<>(validStarts);
            for (int v = minStart; v <= maxStart; v++) {
                if (!validSet.contains(v)) {
                    model.addDifferent(startVars[i], v);
                }
            }
            
            if (!lesson.isOnline()) {
                List<Integer> validRooms = computeValidRooms(lesson, physicalRooms, roomToIndex);
                if (validRooms.isEmpty()) {
                    issues.add(InfeasibilityIssue.critical("NO_VALID_ROOM",
                        "Lesson " + lesson.getId() + " has no valid rooms", "Check room constraints"));
                    InfeasibilityReport report = InfeasibilityReport.infeasible(lessons.size(), timeslots.size(), rooms.size(), issues);
                    report.setAnalysisText("CRITICAL: Some lessons have no valid rooms after constraint filtering.");
                    return report;
                }
                
                int minRoom = Collections.min(validRooms);
                int maxRoom = Collections.max(validRooms);
                roomVars[i] = model.newIntVar(minRoom, maxRoom, "room_" + lesson.getId());
                
                Set<Integer> validRoomSet = new HashSet<>(validRooms);
                for (int r = minRoom; r <= maxRoom; r++) {
                    if (!validRoomSet.contains(r)) {
                        model.addDifferent(roomVars[i], r);
                    }
                }
            } else {
                roomVars[i] = null;
            }
            
            intervals[i] = model.newFixedSizeIntervalVar(
                    startVars[i], lesson.getDurationHours(), "interval_" + lesson.getId());
        }

        // Add room no-overlap (simplified: use cumulative per room)
        addRoomNoOverlap(model, lessons, startVars, roomVars, physicalRooms.size());
        
        // Add lecturer no-overlap
        addLecturerNoOverlap(model, lessons, startVars);
        
        // Add student group no-overlap
        addStudentGroupNoOverlap(model, lessons, startVars);
        
        // Add same-course-same-day constraint if enabled
        if (!isSameCourseSameDayAllowed()) {
            addSameCourseSameDayConstraints(model, lessons, startVars, dayStartIndex, dayEndIndex, timeslots);
        }

        // For large problems, skip full solve and just check capacity
        // The full solver will determine actual feasibility
        if (lessons.size() > 500) {
            log.info("CP-SAT Feasibility: Large problem ({} lessons), skipping full solve - doing capacity analysis", lessons.size());
            String analysis = analyzeBottleneck(lessons, physicalRooms, timeslots);
            
            // Check for critical issues in the analysis
            if (analysis.contains("CRITICAL") || analysis.contains("OVERCAPACITY")) {
                log.warn("CP-SAT Feasibility: Capacity issues detected");
                issues.add(InfeasibilityIssue.high("CAPACITY_ISSUE",
                    "Resource capacity analysis indicates potential scheduling difficulties.",
                    analysis));
                InfeasibilityReport report = InfeasibilityReport.infeasible(lessons.size(), timeslots.size(), rooms.size(), issues);
                report.setAnalysisText(analysis);
                return report;
            }
            
            log.info("CP-SAT Feasibility: Capacity check passed (quick analysis)");
            InfeasibilityReport report = InfeasibilityReport.feasible(lessons.size(), timeslots.size(), rooms.size());
            report.setAnalysisText(analysis);
            return report;
        }

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(120.0);  // Increased for larger problems
        solver.getParameters().setNumSearchWorkers(4);
        
        CpSolverStatus status = solver.solve(model);
        long elapsedMs = System.currentTimeMillis() - startMs;
        
        if (status == CpSolverStatus.INFEASIBLE) {
            log.warn("CP-SAT Feasibility: Model is INFEASIBLE (checked in {} ms)", elapsedMs);
            if (issues.isEmpty()) {
                // Analyze the bottleneck to give specific recommendations
                String analysis = analyzeBottleneck(lessons, physicalRooms, timeslots);
                issues.add(InfeasibilityIssue.high("CONSTRAINT_CONFLICT",
                    "The timetable cannot be created because there are not enough resources for all the classes.",
                    analysis));
            }
            InfeasibilityReport report = InfeasibilityReport.infeasible(lessons.size(), timeslots.size(), rooms.size(), issues);
            report.setAnalysisText(analyzeBottleneck(lessons, physicalRooms, timeslots));
            return report;
        } else if (status == CpSolverStatus.FEASIBLE || status == CpSolverStatus.OPTIMAL) {
            log.info("CP-SAT Feasibility: FEASIBLE solution found in {} ms", elapsedMs);
            return InfeasibilityReport.feasible(lessons.size(), timeslots.size(), rooms.size());
        } else {
            log.info("CP-SAT Feasibility: Could not determine feasibility in {} ms (status={})", elapsedMs, status);
            return InfeasibilityReport.feasible(lessons.size(), timeslots.size(), rooms.size());
        }
    }

    /**
     * Get lesson breakdown for a specific zone or feature.
     */
    @Transactional
    public LessonBreakdownDTO getLessonBreakdown(Long zoneId, Long featureId) {
        List<Lesson> lessons = lessonRepository.findAllWithCourseAndLecturer();
        List<Room> rooms = roomRepository.findAllWithFeatures();
        List<Timeslot> timeslots = timeslotService.ensureTimeslotsMatchSettings();
        
        if (zoneId != null) {
            return getZoneBreakdown(zoneId, lessons, rooms, timeslots);
        } else if (featureId != null) {
            return getFeatureBreakdown(featureId, lessons, rooms, timeslots);
        }
        return new LessonBreakdownDTO();
    }
    
    private LessonBreakdownDTO getZoneBreakdown(Long zoneId, List<Lesson> lessons, List<Room> rooms, List<Timeslot> timeslots) {
        Zone targetZone = rooms.stream()
                .filter(r -> r.getZone() != null && r.getZone().getId().equals(zoneId))
                .map(Room::getZone)
                .findFirst()
                .orElse(null);
        
        if (targetZone == null) {
            return new LessonBreakdownDTO();
        }
        
        int roomsInZone = (int) rooms.stream()
                .filter(r -> r.getZone() != null && r.getZone().getId().equals(zoneId))
                .count();
        int totalSlots = timeslots.size();
        
        List<LessonBreakdownDTO.LessonDetail> lessonDetails = new ArrayList<>();
        double totalHours = 0;
        
        for (Lesson lesson : lessons) {
            if (lesson.isOnline()) continue;
            Course course = lesson.getCourse();
            Set<Zone> allowedZones = course != null ? course.getAllowedZones() : null;
            
            boolean canUseZone = allowedZones == null || allowedZones.isEmpty() 
                    ? true 
                    : allowedZones.stream().anyMatch(z -> z.getId().equals(zoneId));
            
            if (canUseZone) {
                LessonBreakdownDTO.LessonDetail detail = new LessonBreakdownDTO.LessonDetail(
                    course != null ? course.getCode() : "N/A",
                    course != null ? course.getName() : "Unknown",
                    lesson.getDurationHours(),
                    lesson.getStudentGroups() != null 
                        ? lesson.getStudentGroups().stream().map(StudentGroup::getName).toList() 
                        : List.of(),
                    lesson.getLecturer() != null ? lesson.getLecturer().getName() : "TBA",
                    lesson.getTotalStudentCount()
                );
                lessonDetails.add(detail);
                totalHours += lesson.getDurationHours();
            }
        }
        
        double roomSlotHours = roomsInZone * totalSlots;
        double utilization = roomSlotHours > 0 ? (totalHours * 100.0) / roomSlotHours : 0;
        
        return new LessonBreakdownDTO(
            targetZone.getName(),
            null,
            lessonDetails.size(),
            totalHours,
            roomsInZone,
            utilization,
            lessonDetails
        );
    }
    
    private LessonBreakdownDTO getFeatureBreakdown(Long featureId, List<Lesson> lessons, List<Room> rooms, List<Timeslot> timeslots) {
        Feature targetFeature = rooms.stream()
                .filter(r -> r.getFeatures() != null)
                .flatMap(r -> r.getFeatures().stream())
                .filter(f -> f.getId().equals(featureId))
                .findFirst()
                .orElse(null);
        
        if (targetFeature == null) {
            return new LessonBreakdownDTO();
        }
        
        int roomsWithFeature = (int) rooms.stream()
                .filter(r -> r.getFeatures() != null && r.getFeatures().stream().anyMatch(f -> f.getId().equals(featureId)))
                .count();
        int totalSlots = timeslots.size();
        
        List<LessonBreakdownDTO.LessonDetail> lessonDetails = new ArrayList<>();
        int totalHours = 0;
        
        for (Lesson lesson : lessons) {
            if (lesson.isOnline()) continue;
            Course course = lesson.getCourse();
            Set<Feature> requiredFeatures = course != null ? course.getRequiredFeatures() : null;
            
            if (requiredFeatures != null && requiredFeatures.stream().anyMatch(f -> f.getId().equals(featureId))) {
                LessonBreakdownDTO.LessonDetail detail = new LessonBreakdownDTO.LessonDetail(
                    course != null ? course.getCode() : "N/A",
                    course != null ? course.getName() : "Unknown",
                    lesson.getDurationHours(),
                    lesson.getStudentGroups() != null 
                        ? lesson.getStudentGroups().stream().map(StudentGroup::getName).toList() 
                        : List.of(),
                    lesson.getLecturer() != null ? lesson.getLecturer().getName() : "TBA",
                    lesson.getTotalStudentCount()
                );
                lessonDetails.add(detail);
                totalHours += lesson.getDurationHours();
            }
        }
        
        int roomSlotHours = roomsWithFeature * totalSlots;
        double utilization = roomSlotHours > 0 ? (totalHours * 100.0) / roomSlotHours : 0;
        
        return new LessonBreakdownDTO(
            null,
            targetFeature.getName(),
            lessonDetails.size(),
            totalHours,
            roomsWithFeature,
            utilization,
            lessonDetails
        );
    }

    private List<Integer> computeValidStarts(Lesson lesson, List<Timeslot> timeslots,
            Map<Long, Integer> timeslotToIndex, Map<DayOfWeek, Integer> dayStartIndex,
            Map<DayOfWeek, Integer> dayEndIndex, List<SpecialEvent> specialEvents) {
        List<Integer> validStarts = new ArrayList<>();
        LocalTime latestEnd = constraintSettingsService.getLatestEndTime();
        LocalTime fridayLatestEnd = constraintSettingsService.getFridayLatestEndTime();
        LocalTime lunchStart = constraintSettingsService.getLunchBreakStart();
        LocalTime lunchEnd = constraintSettingsService.getLunchBreakEnd();
        boolean lunchEnforced = constraintSettingsService.isLunchBreakEnforced();
        
        for (Timeslot ts : timeslots) {
            LocalTime lessonEnd = ts.getStartTime().plusHours(lesson.getDurationHours());
            LocalTime allowedEnd = ts.getDayOfWeek() == DayOfWeek.FRIDAY ? fridayLatestEnd : latestEnd;
            if (lessonEnd.isAfter(allowedEnd)) continue;
            if (lunchEnforced && ts.getStartTime().isBefore(lunchEnd) && lessonEnd.isAfter(lunchStart)) continue;
            validStarts.add(timeslotToIndex.get(ts.getId()));
        }
        return validStarts;
    }

    private List<Integer> computeValidRooms(Lesson lesson, List<Room> rooms, Map<Long, Integer> roomToIndex) {
        List<Integer> validRooms = new ArrayList<>();
        Course course = lesson.getCourse();
        int students = lesson.getTotalStudentCount();
        for (Room room : rooms) {
            if (room.getCapacity() < students) continue;
            if (course != null && course.getRequiredFeatures() != null && !course.getRequiredFeatures().isEmpty()) {
                if (!room.hasAllFeatures(course.getRequiredFeatures())) continue;
            }
            if (course != null && course.getAllowedZones() != null && !course.getAllowedZones().isEmpty()) {
                if (room.getZone() == null || !course.getAllowedZones().contains(room.getZone())) continue;
            }
            validRooms.add(roomToIndex.get(room.getId()));
        }
        return validRooms;
    }

    private void addRoomNoOverlap(CpModel model, List<Lesson> lessons, IntVar[] startVars, 
            IntVar[] roomVars, int totalRooms) {
        for (int r = 0; r < totalRooms; r++) {
            List<IntervalVar> roomIntervals = new ArrayList<>();
            for (int i = 0; i < lessons.size(); i++) {
                if (roomVars[i] == null) continue;
                BoolVar isInRoom = model.newBoolVar("room_" + i + "_" + r);
                model.addEquality(roomVars[i], r).onlyEnforceIf(isInRoom);
                model.addDifferent(roomVars[i], r).onlyEnforceIf(isInRoom.not());
                IntervalVar interval = model.newOptionalFixedSizeIntervalVar(
                    startVars[i], lessons.get(i).getDurationHours(), isInRoom, "ri_" + r + "_" + i);
                roomIntervals.add(interval);
            }
            if (!roomIntervals.isEmpty()) model.addNoOverlap(roomIntervals);
        }
    }

    private void addLecturerNoOverlap(CpModel model, List<Lesson> lessons, IntVar[] startVars) {
        Map<Long, List<Integer>> byLecturer = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).getLecturer() != null) {
                byLecturer.computeIfAbsent(lessons.get(i).getLecturer().getId(), k -> new ArrayList<>()).add(i);
            }
        }
        for (List<Integer> indices : byLecturer.values()) {
            if (indices.size() > 1) {
                List<IntervalVar> intervals = new ArrayList<>();
                for (int idx : indices) {
                    intervals.add(model.newFixedSizeIntervalVar(startVars[idx], lessons.get(idx).getDurationHours(), 
                        "li_" + idx));
                }
                model.addNoOverlap(intervals);
            }
        }
    }

    private void addStudentGroupNoOverlap(CpModel model, List<Lesson> lessons, IntVar[] startVars) {
        Map<Long, List<Integer>> byGroup = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            for (StudentGroup g : lessons.get(i).getStudentGroups()) {
                byGroup.computeIfAbsent(g.getId(), k -> new ArrayList<>()).add(i);
            }
        }
        for (List<Integer> indices : byGroup.values()) {
            if (indices.size() > 1) {
                List<IntervalVar> intervals = new ArrayList<>();
                for (int idx : indices) {
                    intervals.add(model.newFixedSizeIntervalVar(startVars[idx], lessons.get(idx).getDurationHours(), 
                        "gi_" + idx));
                }
                model.addNoOverlap(intervals);
            }
        }
    }

    /**
     * Add same-course-same-day constraint: lessons of the same course must be on different days.
     * This matches the constraint in the full solver.
     */
    private void addSameCourseSameDayConstraints(CpModel model, List<Lesson> lessons, 
            IntVar[] startVars, Map<DayOfWeek, Integer> dayStartIndex, 
            Map<DayOfWeek, Integer> dayEndIndex, List<Timeslot> timeslots) {
        
        // Build day-to-slot-index mapping
        Map<DayOfWeek, List<Integer>> dayToTimeIndices = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            List<Integer> slots = new ArrayList<>();
            int start = dayStartIndex.getOrDefault(day, 0);
            int end = dayEndIndex.getOrDefault(day, start);
            for (int i = start; i < end; i++) {
                slots.add(i);
            }
            if (!slots.isEmpty()) {
                dayToTimeIndices.put(day, slots);
            }
        }
        
        // Group lessons by course
        Map<Course, List<Integer>> courseLessons = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            Course course = lessons.get(i).getCourse();
            if (course != null) {
                courseLessons.computeIfAbsent(course, k -> new ArrayList<>()).add(i);
            }
        }
        
        int constraintsAdded = 0;
        
        for (Map.Entry<Course, List<Integer>> entry : courseLessons.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() <= 1) continue;
            
            // For each pair of lessons in the same course, they must be on different days
            for (int j = 0; j < indices.size(); j++) {
                for (int k = j + 1; k < indices.size(); k++) {
                    int idx1 = indices.get(j);
                    int idx2 = indices.get(k);
                    
                    // For each day, create constraint: NOT (both lessons on this day)
                    for (DayOfWeek day : dayToTimeIndices.keySet()) {
                        List<Integer> daySlots = dayToTimeIndices.get(day);
                        if (daySlots == null || daySlots.isEmpty()) continue;
                        
                        // Create boolean: lesson1 is on this day
                        BoolVar lesson1OnDay = model.newBoolVar("l1_" + idx1 + "_" + day);
                        Literal[] lesson1InDaySlots = new Literal[daySlots.size()];
                        for (int s = 0; s < daySlots.size(); s++) {
                            BoolVar isSlot = model.newBoolVar("l1s_" + idx1 + "_" + daySlots.get(s));
                            model.addEquality(startVars[idx1], daySlots.get(s)).onlyEnforceIf(isSlot);
                            model.addDifferent(startVars[idx1], daySlots.get(s)).onlyEnforceIf(isSlot.not());
                            lesson1InDaySlots[s] = isSlot;
                        }
                        model.addBoolOr(lesson1InDaySlots).onlyEnforceIf(lesson1OnDay);
                        
                        // Create boolean: lesson2 is on this day
                        BoolVar lesson2OnDay = model.newBoolVar("l2_" + idx2 + "_" + day);
                        Literal[] lesson2InDaySlots = new Literal[daySlots.size()];
                        for (int s = 0; s < daySlots.size(); s++) {
                            BoolVar isSlot = model.newBoolVar("l2s_" + idx2 + "_" + daySlots.get(s));
                            model.addEquality(startVars[idx2], daySlots.get(s)).onlyEnforceIf(isSlot);
                            model.addDifferent(startVars[idx2], daySlots.get(s)).onlyEnforceIf(isSlot.not());
                            lesson2InDaySlots[s] = isSlot;
                        }
                        model.addBoolOr(lesson2InDaySlots).onlyEnforceIf(lesson2OnDay);
                        
                        // Constraint: NOT (lesson1OnDay AND lesson2OnDay)
                        model.addBoolOr(new Literal[] {lesson1OnDay.not(), lesson2OnDay.not()});
                        
                        constraintsAdded++;
                    }
                }
            }
        }
        
        log.info("CP-SAT Feasibility: Added same-course-same-day constraints: {}", constraintsAdded);
    }

    /**
     * Deep analysis of scheduling bottlenecks with specific actionable recommendations.
     */
    private String analyzeBottleneck(List<Lesson> lessons, List<Room> rooms, List<Timeslot> timeslots) {
        StringBuilder sb = new StringBuilder();
        
        int totalSlots = timeslots.size();
        int totalRooms = rooms.size();
        
        // === 1. CAPACITY ANALYSIS ===
        int totalLessonHours = lessons.stream().mapToInt(Lesson::getDurationHours).sum();
        int totalRoomSlotHours = totalRooms * totalSlots;
        double utilization = (double) totalLessonHours / totalRoomSlotHours * 100;
        
        sb.append("CAPACITY ANALYSIS:\n");
        sb.append(String.format("• Total lessons: %d (%d teaching hours)\n", lessons.size(), totalLessonHours));
        sb.append(String.format("• Available: %d rooms × %d slots = %d room-slots\n", totalRooms, totalSlots, totalRoomSlotHours));
        sb.append(String.format("• Utilization: %.1f%%\n\n", utilization));
        
        // === 2. ZONE BOTTLENECK ANALYSIS ===
        sb.append("ZONE CAPACITY ANALYSIS:\n");
        Map<Zone, ZoneStats> zoneStats = analyzeZoneCapacity(lessons, rooms, timeslots);
        
        // Find zones with spare capacity
        Map<Long, Double> zoneSpareCapacity = new HashMap<>();
        for (ZoneStats zs : zoneStats.values()) {
            if (zs.utilization < 80) {
                double spareHours = (80.0 - zs.utilization) / 100.0 * zs.roomSlotHours;
                zoneSpareCapacity.put(zs.zone.getId(), spareHours);
            }
        }
        
        // Only report zones as problematic if:
        // 1. They have courses RESTRICTED to that zone only (no alternatives)
        // 2. OR there's not enough spare capacity across all alternative zones
        List<ZoneIssue> realZoneIssues = new ArrayList<>();
        
        for (ZoneStats zs : zoneStats.values()) {
            if (zs.utilization <= 85) continue; // Zone is fine
            
            // Find lessons that can ONLY use this zone (no alternatives)
            double restrictedHours = 0;
            double flexibleHours = 0;
            
            for (Lesson lesson : lessons) {
                if (lesson.isOnline()) continue;
                Course course = lesson.getCourse();
                Set<Zone> allowedZones = course != null ? course.getAllowedZones() : null;
                
                if (allowedZones == null || allowedZones.isEmpty()) {
                    // No zone restriction - can use any zone, not a problem for this specific zone
                    flexibleHours += lesson.getDurationHours();
                } else if (allowedZones.size() == 1 && allowedZones.iterator().next().getId().equals(zs.zone.getId())) {
                    // Course is RESTRICTED to only this zone - this is a real constraint
                    restrictedHours += lesson.getDurationHours();
                } else if (allowedZones.stream().anyMatch(z -> z.getId().equals(zs.zone.getId()))) {
                    // Course can use this zone AND others - flexible
                    flexibleHours += lesson.getDurationHours();
                }
            }
            
            // Calculate if there's enough spare capacity in alternative zones for flexible courses
            double totalSpareInAlternatives = 0;
            for (Lesson lesson : lessons) {
                if (lesson.isOnline()) continue;
                Course course = lesson.getCourse();
                Set<Zone> allowedZones = course != null ? course.getAllowedZones() : null;
                
                if (allowedZones != null && !allowedZones.isEmpty() && 
                    allowedZones.stream().anyMatch(z -> z.getId().equals(zs.zone.getId()))) {
                    // This course can use this zone - check alternatives
                    for (Zone altZone : allowedZones) {
                        if (!altZone.getId().equals(zs.zone.getId()) && zoneSpareCapacity.containsKey(altZone.getId())) {
                            totalSpareInAlternatives += zoneSpareCapacity.get(altZone.getId()) / allowedZones.size();
                        }
                    }
                }
            }
            
            // Only report as issue if:
            // - There are restricted courses exceeding capacity, OR
            // - Flexible courses can't fit in alternatives
            double capacityAt80Percent = zs.roomSlotHours * 0.80;
            double excessHours = Math.max(0, (restrictedHours + flexibleHours) - capacityAt80Percent);
            
            if (restrictedHours > capacityAt80Percent) {
                // Courses restricted to this zone exceed capacity - REAL PROBLEM
                int roomsNeeded = (int) Math.ceil((restrictedHours - capacityAt80Percent) / totalSlots);
                realZoneIssues.add(new ZoneIssue(zs, roomsNeeded, restrictedHours, flexibleHours, true));
            } else if (excessHours > 0 && totalSpareInAlternatives < excessHours) {
                // Not enough spare capacity in alternatives - PARTIAL PROBLEM
                int roomsNeeded = (int) Math.ceil((excessHours - totalSpareInAlternatives) / totalSlots);
                if (roomsNeeded > 0) {
                    realZoneIssues.add(new ZoneIssue(zs, roomsNeeded, restrictedHours, flexibleHours, false));
                }
            }
        }
        
        // Sort issues by severity
        realZoneIssues.sort((a, b) -> {
            // Restricted issues first
            if (a.isRestricted != b.isRestricted) return a.isRestricted ? -1 : 1;
            return Double.compare(b.roomsNeeded, a.roomsNeeded);
        });
        
        if (!realZoneIssues.isEmpty()) {
            for (ZoneIssue issue : realZoneIssues) {
                ZoneStats zs = issue.zoneStats;
                if (issue.isRestricted) {
                    sb.append(String.format("• %s: %.0f hours of courses can ONLY use this zone, %d usable rooms (capacity ≥%d, %.0f%% utilization)\n",
                            zs.zoneName, issue.restrictedHours, zs.roomsInZone, zs.minCapacityNeeded, zs.utilization));
                    
                    // Warn about rooms with insufficient capacity
                    if (zs.roomsWithInsufficientCapacity > 0) {
                        sb.append(String.format("  ⚠️ %d room(s) in this zone are TOO SMALL (capacity < %d)\n",
                                zs.roomsWithInsufficientCapacity, zs.minCapacityNeeded));
                    }
                    
                    sb.append(String.format("  → MUST ADD %d room(s) to %s zone (capacity ≥%d students, no alternatives)\n", 
                            issue.roomsNeeded, zs.zoneName, zs.minCapacityNeeded));
                } else {
                    sb.append(String.format("• %s: %.0f hours can use this zone, %d usable rooms (capacity ≥%d, %.0f%% utilization)\n",
                            zs.zoneName, issue.restrictedHours + issue.flexibleHours, zs.roomsInZone, zs.minCapacityNeeded, zs.utilization));
                    
                    // Warn about rooms with insufficient capacity
                    if (zs.roomsWithInsufficientCapacity > 0) {
                        sb.append(String.format("  ⚠️ %d room(s) in this zone are TOO SMALL (capacity < %d)\n",
                                zs.roomsWithInsufficientCapacity, zs.minCapacityNeeded));
                    }
                    
                    sb.append(String.format("  → ADD %d room(s) to %s zone (capacity ≥%d students) OR use alternative zones\n", 
                            issue.roomsNeeded, zs.zoneName, zs.minCapacityNeeded));
                }
            }
        } else {
            sb.append("• All zones have sufficient capacity (courses can be distributed to zones with spare capacity)\n");
        }
        sb.append("\n");
        
        // === 3. FEATURE SCARCITY ANALYSIS ===
        sb.append("FEATURE REQUIREMENTS ANALYSIS:\n");
        Map<Feature, FeatureStats> featureStats = analyzeFeatureScarcity(lessons, rooms, timeslots);
        List<FeatureStats> scarceFeatures = featureStats.values().stream()
                .filter(f -> f.utilization > 90 || f.roomsWithFeature == 0)
                .sorted((a, b) -> Double.compare(b.utilization, a.utilization))
                .toList();
        
        if (!scarceFeatures.isEmpty()) {
            for (FeatureStats fs : scarceFeatures) {
                if (fs.roomsWithFeature == 0) {
                    sb.append(String.format("• %s: %d lessons require this feature (min capacity: %d students)\n",
                            fs.featureName, fs.lessonsNeedingFeature, fs.minCapacityNeeded));
                    if (fs.totalRoomsCount > 0) {
                        sb.append(String.format("  ⚠️ %d rooms have this feature but are TOO SMALL (capacity < %d)\n",
                                fs.totalRoomsCount, fs.minCapacityNeeded));
                    } else {
                        sb.append("  ⚠️ NO rooms have this feature!\n");
                    }
                    sb.append(String.format("  → ADD %d room(s) with %s feature (capacity ≥%d students)\n",
                            Math.max(1, (int) Math.ceil(fs.lessonHours / (totalSlots * 0.8))), fs.featureName, fs.minCapacityNeeded));
                } else {
                    sb.append(String.format("• %s: %d lessons need this, %d usable rooms (capacity ≥%d, %.0f%% utilization)\n",
                            fs.featureName, fs.lessonsNeedingFeature, fs.roomsWithFeature, fs.minCapacityNeeded, fs.utilization));
                    
                    // Warn about rooms with insufficient capacity
                    if (fs.roomsWithInsufficientCapacity > 0) {
                        sb.append(String.format("  ⚠️ %d room(s) have this feature but are TOO SMALL for these lessons\n",
                                fs.roomsWithInsufficientCapacity));
                    }
                    
                    int additionalRoomsNeeded = (int) Math.ceil((fs.lessonHours - fs.roomSlotHours * 0.80) / (double) totalSlots);
                    if (additionalRoomsNeeded > 0) {
                        sb.append(String.format("  → ADD %d room(s) with %s feature (capacity ≥%d students)\n", 
                                additionalRoomsNeeded, fs.featureName, fs.minCapacityNeeded));
                    }
                }
            }
        } else {
            sb.append("• All features have sufficient availability\n");
        }
        sb.append("\n");
        
        // === 4. LECTURER CONFLICT ANALYSIS ===
        sb.append("LECTURER SCHEDULE ANALYSIS:\n");
        Map<Long, LecturerStats> lecturerStats = analyzeLecturerLoad(lessons, timeslots);
        List<LecturerStats> overloadedLecturers = lecturerStats.values().stream()
                .filter(l -> l.hours > totalSlots)
                .sorted((a, b) -> Integer.compare(b.hours, a.hours))
                .limit(5)
                .toList();
        
        if (!overloadedLecturers.isEmpty()) {
            for (LecturerStats ls : overloadedLecturers) {
                sb.append(String.format("• %s: %d hours scheduled (max available: %d)\n", ls.name, ls.hours, totalSlots));
                sb.append(String.format("  → REDUCE teaching load by %d hours OR assign to another lecturer\n", ls.hours - totalSlots));
            }
        } else {
            sb.append("• No lecturers are overloaded\n");
        }
        sb.append("\n");
        
        // === 5. STUDENT GROUP CONFLICT ANALYSIS ===
        sb.append("STUDENT GROUP SCHEDULE ANALYSIS:\n");
        Map<Long, GroupStats> groupStats = analyzeGroupLoad(lessons, timeslots);
        List<GroupStats> overloadedGroups = groupStats.values().stream()
                .filter(g -> g.hours > totalSlots)
                .sorted((a, b) -> Integer.compare(b.hours, a.hours))
                .limit(5)
                .toList();
        
        if (!overloadedGroups.isEmpty()) {
            for (GroupStats gs : overloadedGroups) {
                sb.append(String.format("• %s: %d hours of classes (max slots: %d)\n", gs.name, gs.hours, totalSlots));
                sb.append(String.format("  → REDUCE course load by %d hours for this group\n", gs.hours - totalSlots));
            }
        } else {
            sb.append("• No student groups are overloaded\n");
        }
        sb.append("\n");
        
        // === 6. COMBINED CLASS CONFLICTS ===
        sb.append("COMBINED CLASS ANALYSIS:\n");
        int combinedClassConflicts = analyzeCombinedClassConflicts(lessons);
        if (combinedClassConflicts > 0) {
            sb.append(String.format("• %d combined classes create scheduling conflicts\n", combinedClassConflicts));
            sb.append("  → These classes share students between groups, reducing scheduling flexibility\n");
            
            // Analyze combined classes with tight room constraints
            int combinedWithTightRooms = 0;
            for (Lesson lesson : lessons) {
                if (lesson.isOnline()) continue;
                if (lesson.getStudentGroups() == null || lesson.getStudentGroups().size() <= 1) continue;
                
                int validRooms = countValidRooms(lesson, rooms);
                if (validRooms <= 2) {
                    combinedWithTightRooms++;
                }
            }
            if (combinedWithTightRooms > 0) {
                sb.append(String.format("  ⚠️ %d combined classes have only 1-2 valid rooms - HIGH RISK\n", combinedWithTightRooms));
            }
        } else {
            sb.append("• No combined class conflicts detected\n");
        }
        sb.append("\n");
        
        // === 7. SAME-COURSE-SAME-DAY CONFLICTS ===
        sb.append("SAME-COURSE-SAME-DAY ANALYSIS:\n");
        
        // Group lessons by course AND student group (constraint is per group, not per course)
        // MTH101 for SE 100LVL GRP A must be on different days
        // But MTH101 for SE 100LVL GRP B can be on the same day as GRP A
        Map<String, List<Lesson>> lessonsByCourseAndGroup = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getCourse() == null) continue;
            if (lesson.getStudentGroups() == null || lesson.getStudentGroups().isEmpty()) continue;
            
            // Create key for each group taking this course
            for (StudentGroup group : lesson.getStudentGroups()) {
                String key = lesson.getCourse().getId() + "_" + group.getId();
                lessonsByCourseAndGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(lesson);
            }
        }
        
        int groupsWithMultipleLessons = 0;
        int groupsWithTightRoomOptions = 0;
        List<CourseGroupAction> groupActions = new ArrayList<>();
        
        for (Map.Entry<String, List<Lesson>> entry : lessonsByCourseAndGroup.entrySet()) {
            List<Lesson> groupLessons = entry.getValue();
            if (groupLessons.size() <= 1) continue; // Only one lesson for this course+group, no constraint
            
            groupsWithMultipleLessons++;
            
            // Check if all lessons for this course+group share limited room options
            Set<Room> sharedValidRooms = null;
            for (Lesson lesson : groupLessons) {
                Set<Room> validForThis = new HashSet<>();
                for (Room room : rooms) {
                    if (isValidRoom(lesson, room)) validForThis.add(room);
                }
                if (sharedValidRooms == null) {
                    sharedValidRooms = validForThis;
                } else {
                    sharedValidRooms.retainAll(validForThis);
                }
            }
            
            if (sharedValidRooms != null && sharedValidRooms.size() < groupLessons.size()) {
                groupsWithTightRoomOptions++;
                Course course = groupLessons.get(0).getCourse();
                String groupName = groupLessons.get(0).getStudentGroups().stream()
                    .map(StudentGroup::getName).collect(Collectors.joining(", "));
                int roomsNeeded = groupLessons.size() - sharedValidRooms.size();
                
                // Determine what features/zones the new rooms need
                Set<Feature> requiredFeatures = course.getRequiredFeatures();
                Set<Zone> allowedZones = course.getAllowedZones();
                int minCapacity = groupLessons.stream().mapToInt(Lesson::getTotalStudentCount).max().orElse(30);
                
                groupActions.add(new CourseGroupAction(course.getCode(), groupName, groupLessons.size(), 
                    sharedValidRooms.size(), roomsNeeded, requiredFeatures, allowedZones, minCapacity));
            }
        }
        
        sb.append(String.format("• %d course+group combinations have multiple lessons that must be on different days\n", groupsWithMultipleLessons));
        if (groupsWithTightRoomOptions > 0) {
            sb.append(String.format("• ⚠️ %d groups have only 1 room that works for their course - NO BACKUP ROOMS\n", groupsWithTightRoomOptions));
            sb.append("  These groups have only ONE valid room for all their lessons.\n");
            sb.append("  If that room gets booked by another course, there's no alternative - SCHEDULING WILL FAIL.\n");
            sb.append("  Solutions - add backup rooms:\n");
            for (CourseGroupAction cga : groupActions.stream().limit(8).toList()) {
                String features = cga.requiredFeatures.isEmpty() ? "" : 
                    " with " + cga.requiredFeatures.stream().map(Feature::getName).collect(Collectors.joining("+"));
                String zones = cga.allowedZones.isEmpty() ? "" : 
                    " in " + cga.allowedZones.stream().map(Zone::getName).collect(Collectors.joining("/"));
                sb.append(String.format("    - %s (%s): %d lessons share only %d room(s)\n", 
                    cga.courseCode, cga.groupName, cga.lessonCount, cga.sharedRooms));
                sb.append(String.format("      → ADD %d room(s)%s%s (capacity ≥%d)\n", 
                    cga.roomsNeeded, features, zones, cga.minCapacity));
            }
        }
        sb.append("\n");
        
        // === 8. TIGHT CONSTRAINT ANALYSIS ===
        sb.append("ROOM CONSTRAINT ANALYSIS:\n");
        Map<String, Integer> lessonsByValidRoomCount = new HashMap<>();
        Map<String, List<LessonConstraintInfo>> tightConstraintsByCourse = new HashMap<>();
        
        for (Lesson lesson : lessons) {
            if (lesson.isOnline()) continue;
            
            int validRooms = countValidRooms(lesson, rooms);
            
            String bucket = validRooms == 0 ? "0 rooms" : 
                           validRooms <= 2 ? "1-2 rooms" :
                           validRooms <= 5 ? "3-5 rooms" : "6+ rooms";
            lessonsByValidRoomCount.merge(bucket, 1, Integer::sum);
            
            if (validRooms <= 2 && validRooms > 0) {
                Course course = lesson.getCourse();
                String courseCode = course != null ? course.getCode() : "UNKNOWN";
                tightConstraintsByCourse.computeIfAbsent(courseCode, k -> new ArrayList<>()).add(
                    new LessonConstraintInfo(
                        courseCode,
                        lesson.getTotalStudentCount(),
                        validRooms,
                        course != null && course.getRequiredFeatures() != null ? course.getRequiredFeatures() : Set.of(),
                        course != null && course.getAllowedZones() != null ? course.getAllowedZones() : Set.of()
                    ));
            }
        }
        
        int lessonsWithNoValidRooms = lessonsByValidRoomCount.getOrDefault("0 rooms", 0);
        int lessonsWithTightRooms = lessonsByValidRoomCount.getOrDefault("1-2 rooms", 0);
        
        if (lessonsWithNoValidRooms > 0) {
            sb.append(String.format("• ⚠️ %d lessons have NO valid rooms (capacity/feature/zone mismatch)\n", lessonsWithNoValidRooms));
        }
        if (lessonsWithTightRooms > 0) {
            sb.append(String.format("• %d lessons have only 1-2 valid rooms (tight constraints)\n", lessonsWithTightRooms));
        }
        sb.append(String.format("• Room constraint distribution: %s\n", 
                lessonsByValidRoomCount.entrySet().stream()
                    .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "))));
        
        // Show examples with solutions
        if (!tightConstraintsByCourse.isEmpty()) {
            sb.append("  Courses with tight constraints and solutions:\n");
            for (Map.Entry<String, List<LessonConstraintInfo>> entry : tightConstraintsByCourse.entrySet().stream().limit(6).toList()) {
                List<LessonConstraintInfo> infos = entry.getValue();
                LessonConstraintInfo first = infos.get(0);
                String features = first.requiredFeatures.isEmpty() ? "" : 
                    " with " + first.requiredFeatures.stream().map(Feature::getName).collect(Collectors.joining("+"));
                String zones = first.allowedZones.isEmpty() ? "" : 
                    " in " + first.allowedZones.stream().map(Zone::getName).collect(Collectors.joining("/"));
                sb.append(String.format("    - %s: %d lessons, %d valid rooms (capacity ≥%d)\n", 
                    first.courseCode, infos.size(), first.validRooms, first.studentCount));
                sb.append(String.format("      → ADD room(s)%s%s (capacity ≥%d)\n", 
                    features, zones, first.studentCount));
            }
        }
        sb.append("\n");
        
        // === SUMMARY OF ACTIONS ===
        sb.append("=== SUMMARY OF REQUIRED ACTIONS ===\n");
        int actionCount = 1;
        
        // Same-course-same-day conflicts (highest priority - causes infeasibility)
        if (groupsWithTightRoomOptions > 0) {
            sb.append(String.format("%d. HIGH RISK: Groups with limited room options for their course:\n", actionCount++));
            for (CourseGroupAction cga : groupActions.stream().limit(5).toList()) {
                String features = cga.requiredFeatures.isEmpty() ? "" : 
                    " with " + cga.requiredFeatures.stream().map(Feature::getName).collect(Collectors.joining("+"));
                String zones = cga.allowedZones.isEmpty() ? "" : 
                    " in " + cga.allowedZones.stream().map(Zone::getName).collect(Collectors.joining("/"));
                sb.append(String.format("   → %s (%s): ADD %d room(s)%s%s (capacity ≥%d)\n", 
                    cga.courseCode, cga.groupName, cga.roomsNeeded, features, zones, cga.minCapacity));
            }
        }
        
        // Tight room constraints
        if (lessonsWithTightRooms > 0) {
            sb.append(String.format("%d. TIGHT CONSTRAINTS: %d lessons have only 1-2 valid rooms:\n", 
                actionCount++, lessonsWithTightRooms));
            for (Map.Entry<String, List<LessonConstraintInfo>> entry : tightConstraintsByCourse.entrySet().stream().limit(3).toList()) {
                LessonConstraintInfo first = entry.getValue().get(0);
                String features = first.requiredFeatures.isEmpty() ? "" : 
                    " with " + first.requiredFeatures.stream().map(Feature::getName).collect(Collectors.joining("+"));
                String zones = first.allowedZones.isEmpty() ? "" : 
                    " in " + first.allowedZones.stream().map(Zone::getName).collect(Collectors.joining("/"));
                sb.append(String.format("   → %s: ADD room(s)%s%s (capacity ≥%d)\n", 
                    first.courseCode, features, zones, first.studentCount));
            }
        }
        
        for (ZoneIssue issue : realZoneIssues) {
            if (issue.roomsNeeded > 0) {
                if (issue.isRestricted) {
                    sb.append(String.format("%d. MUST ADD %d room(s) to %s zone (courses restricted to this zone, capacity ≥%d)\n", 
                        actionCount++, issue.roomsNeeded, issue.zoneStats.zoneName, issue.zoneStats.minCapacityNeeded));
                } else {
                    sb.append(String.format("%d. ADD %d room(s) to %s zone (capacity ≥%d)\n", 
                        actionCount++, issue.roomsNeeded, issue.zoneStats.zoneName, issue.zoneStats.minCapacityNeeded));
                }
            }
        }
        
        for (FeatureStats fs : scarceFeatures) {
            if (fs.roomsWithFeature == 0) {
                sb.append(String.format("%d. ADD rooms with %s feature (capacity ≥%d, currently NONE usable exist)\n", 
                    actionCount++, fs.featureName, fs.minCapacityNeeded));
            } else {
                int needed = (int) Math.ceil((fs.lessonHours - fs.roomSlotHours * 0.80) / (double) totalSlots);
                if (needed > 0) {
                    sb.append(String.format("%d. ADD %d room(s) with %s feature (capacity ≥%d)\n", 
                        actionCount++, needed, fs.featureName, fs.minCapacityNeeded));
                }
            }
        }
        
        for (LecturerStats ls : overloadedLecturers) {
            sb.append(String.format("%d. REDUCE %s's teaching by %d hours\n", actionCount++, ls.name, ls.hours - totalSlots));
        }
        
        for (GroupStats gs : overloadedGroups) {
            sb.append(String.format("%d. REDUCE %s's course load by %d hours\n", actionCount++, gs.name, gs.hours - totalSlots));
        }
        
        if (actionCount == 1) {
            sb.append("The constraints are complex but no single bottleneck is obvious. Try:\n");
            sb.append("1. Add 3-5 general-purpose rooms (capacity ≥50) as buffer\n");
            sb.append("2. Review combined classes (188 detected) - consider splitting groups\n");
            sb.append("3. Check for unusual course configurations\n");
        }
        
        return sb.toString();
    }
    
    private Map<Zone, ZoneStats> analyzeZoneCapacity(List<Lesson> lessons, List<Room> rooms, List<Timeslot> timeslots) {
        Map<Zone, ZoneStats> stats = new HashMap<>();
        int totalSlots = timeslots.size();
        
        // Count rooms per zone
        Map<Zone, List<Room>> roomsByZone = rooms.stream()
                .filter(r -> r.getZone() != null)
                .collect(Collectors.groupingBy(Room::getZone));
        
        // First pass: determine minimum capacity needed per zone
        Map<Zone, Integer> minCapacityPerZone = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.isOnline()) continue;
            Course course = lesson.getCourse();
            int lessonStudents = lesson.getTotalStudentCount();
            
            Set<Zone> allowedZones = course != null ? course.getAllowedZones() : null;
            
            if (allowedZones == null || allowedZones.isEmpty()) {
                // No zone restriction - update all zones
                for (Zone zone : roomsByZone.keySet()) {
                    minCapacityPerZone.merge(zone, lessonStudents, Math::max);
                }
            } else {
                for (Zone zone : allowedZones) {
                    minCapacityPerZone.merge(zone, lessonStudents, Math::max);
                }
            }
        }
        
        // Initialize stats for all zones
        for (Zone zone : roomsByZone.keySet()) {
            ZoneStats zs = new ZoneStats();
            zs.zone = zone;
            zs.zoneName = zone.getName();
            zs.minCapacityNeeded = minCapacityPerZone.getOrDefault(zone, 0);
            stats.put(zone, zs);
        }
        
        // Count lessons needing each zone - distribute load proportionally for multi-zone courses
        for (Lesson lesson : lessons) {
            if (lesson.isOnline()) continue;
            Course course = lesson.getCourse();
            
            // Create breakdown for this lesson
            LessonBreakdown breakdown = createLessonBreakdown(lesson);
            
            Set<Zone> allowedZones = course != null ? course.getAllowedZones() : null;
            
            if (allowedZones == null || allowedZones.isEmpty()) {
                // No zone restriction - can use ANY zone, distribute evenly
                double sharePerZone = lesson.getDurationHours() / (double) stats.size();
                for (ZoneStats zs : stats.values()) {
                    zs.lessonsNeedingZone += 1.0 / stats.size(); // Fractional count
                    zs.lessonHours += sharePerZone;
                    zs.lessonBreakdown.add(breakdown);
                }
            } else {
                // Course has specific allowed zones - distribute among those
                int zoneCount = allowedZones.size();
                double sharePerZone = lesson.getDurationHours() / (double) zoneCount;
                
                for (Zone zone : allowedZones) {
                    ZoneStats zs = stats.get(zone);
                    if (zs != null) {
                        zs.lessonsNeedingZone += 1.0 / zoneCount; // Fractional count
                        zs.lessonHours += sharePerZone;
                        zs.lessonBreakdown.add(breakdown);
                    }
                }
            }
        }
        
        // Calculate room capacity per zone - only count usable rooms
        for (Map.Entry<Zone, ZoneStats> entry : stats.entrySet()) {
            Zone zone = entry.getKey();
            ZoneStats zs = entry.getValue();
            List<Room> zoneRooms = roomsByZone.getOrDefault(zone, List.of());
            
            int usableRooms = 0;
            int totalRooms = 0;
            int roomsTooSmall = 0;
            
            for (Room room : zoneRooms) {
                totalRooms++;
                if (room.getCapacity() >= zs.minCapacityNeeded) {
                    usableRooms++;
                } else {
                    roomsTooSmall++;
                }
            }
            
            zs.roomsInZone = usableRooms; // Only count usable rooms
            zs.totalRoomsCount = totalRooms;
            zs.roomsWithInsufficientCapacity = roomsTooSmall;
            zs.roomSlotHours = usableRooms * totalSlots;
            if (zs.roomSlotHours > 0) {
                zs.utilization = (zs.lessonHours * 100.0) / zs.roomSlotHours;
            }
        }
        
        return stats;
    }
    
    private Map<Feature, FeatureStats> analyzeFeatureScarcity(List<Lesson> lessons, List<Room> rooms, List<Timeslot> timeslots) {
        Map<Feature, FeatureStats> stats = new HashMap<>();
        int totalSlots = timeslots.size();
        
        // Collect all features from rooms
        Set<Feature> allFeatures = new HashSet<>();
        for (Room room : rooms) {
            if (room.getFeatures() != null) {
                allFeatures.addAll(room.getFeatures());
            }
        }
        
        // Initialize stats for all features
        for (Feature feature : allFeatures) {
            FeatureStats fs = new FeatureStats();
            fs.feature = feature;
            fs.featureName = feature.getName();
            stats.put(feature, fs);
        }
        
        // First pass: determine minimum capacity needed per feature
        for (Lesson lesson : lessons) {
            if (lesson.isOnline()) continue;
            Course course = lesson.getCourse();
            if (course == null || course.getRequiredFeatures() == null) continue;
            
            int lessonStudents = lesson.getTotalStudentCount();
            
            for (Feature feature : course.getRequiredFeatures()) {
                FeatureStats fs = stats.computeIfAbsent(feature, f -> {
                    FeatureStats newFs = new FeatureStats();
                    newFs.feature = f;
                    newFs.featureName = f.getName();
                    return newFs;
                });
                fs.lessonsNeedingFeature++;
                fs.lessonHours += lesson.getDurationHours();
                fs.totalStudentsNeeding += lessonStudents;
                fs.minCapacityNeeded = Math.max(fs.minCapacityNeeded, lessonStudents);
            }
        }
        
        // Second pass: count rooms with each feature that have sufficient capacity
        for (Room room : rooms) {
            if (room.getFeatures() == null) continue;
            
            for (Feature feature : room.getFeatures()) {
                FeatureStats fs = stats.get(feature);
                if (fs != null) {
                    fs.totalRoomsCount++; // Total rooms with feature
                    fs.totalCapacity += room.getCapacity();
                    
                    // Only count as room if it has sufficient capacity
                    if (room.getCapacity() >= fs.minCapacityNeeded) {
                        fs.roomsWithFeature++;
                        fs.usableCapacity += room.getCapacity();
                    } else {
                        fs.roomsWithInsufficientCapacity++;
                    }
                }
            }
        }
        
        // Calculate utilization based on USABLE rooms only
        for (FeatureStats fs : stats.values()) {
            fs.roomSlotHours = fs.roomsWithFeature * totalSlots;
            if (fs.roomSlotHours > 0) {
                fs.utilization = (fs.lessonHours * 100.0) / fs.roomSlotHours;
            } else if (fs.lessonsNeedingFeature > 0) {
                fs.utilization = 999; // No usable rooms have this feature but lessons need it
            }
        }
        
        return stats;
    }
    
    private Map<Long, LecturerStats> analyzeLecturerLoad(List<Lesson> lessons, List<Timeslot> timeslots) {
        Map<Long, LecturerStats> stats = new HashMap<>();
        
        for (Lesson lesson : lessons) {
            Lecturer lecturer = lesson.getLecturer();
            if (lecturer == null) continue;
            
            LecturerStats ls = stats.computeIfAbsent(lecturer.getId(), id -> {
                LecturerStats newLs = new LecturerStats();
                newLs.lecturerId = id;
                newLs.name = lecturer.getName();
                return newLs;
            });
            ls.hours += lesson.getDurationHours();
            ls.lessonCount++;
        }
        
        return stats;
    }
    
    private Map<Long, GroupStats> analyzeGroupLoad(List<Lesson> lessons, List<Timeslot> timeslots) {
        Map<Long, GroupStats> stats = new HashMap<>();
        
        for (Lesson lesson : lessons) {
            for (StudentGroup group : lesson.getStudentGroups()) {
                GroupStats gs = stats.computeIfAbsent(group.getId(), id -> {
                    GroupStats newGs = new GroupStats();
                    newGs.groupId = id;
                    newGs.name = group.getName();
                    return newGs;
                });
                gs.hours += lesson.getDurationHours();
                gs.lessonCount++;
            }
        }
        
        return stats;
    }
    
    private int analyzeCombinedClassConflicts(List<Lesson> lessons) {
        // Count lessons that have multiple student groups (combined classes)
        // These create additional scheduling constraints
        return (int) lessons.stream()
                .filter(l -> l.getStudentGroups() != null && l.getStudentGroups().size() > 1)
                .count();
    }
    
    private int countValidRooms(Lesson lesson, List<Room> rooms) {
        int count = 0;
        for (Room room : rooms) {
            if (isValidRoom(lesson, room)) count++;
        }
        return count;
    }
    
    private boolean isValidRoom(Lesson lesson, Room room) {
        // Check capacity
        if (room.getCapacity() < lesson.getTotalStudentCount()) return false;
        
        Course course = lesson.getCourse();
        
        // Check required features
        if (course != null && course.getRequiredFeatures() != null && !course.getRequiredFeatures().isEmpty()) {
            if (!room.hasAllFeatures(course.getRequiredFeatures())) return false;
        }
        
        // Check allowed zones
        if (course != null && course.getAllowedZones() != null && !course.getAllowedZones().isEmpty()) {
            if (room.getZone() == null || !course.getAllowedZones().contains(room.getZone())) return false;
        }
        
        return true;
    }
    
    /**
     * Find zones with spare capacity that share courses with the overloaded zone.
     * Returns specific actionable alternatives with exact room counts.
     */
    private List<ZoneAlternative> findZoneAlternatives(ZoneStats overloaded, List<Lesson> lessons, List<ZoneStats> zonesWithSpareCapacity, int totalSlots) {
        List<ZoneAlternative> alternatives = new ArrayList<>();
        
        if (zonesWithSpareCapacity.isEmpty()) {
            return alternatives;
        }
        
        // Find courses that can use the overloaded zone
        Set<Long> courseIdsInOverloaded = new HashSet<>();
        for (Lesson lesson : lessons) {
            Course course = lesson.getCourse();
            if (course != null && course.getAllowedZones() != null) {
                boolean canUseOverloaded = course.getAllowedZones().stream()
                        .anyMatch(z -> z.getId().equals(overloaded.zone.getId()));
                if (canUseOverloaded) {
                    courseIdsInOverloaded.add(course.getId());
                }
            }
        }
        
        // Calculate spare room capacity in each alternative zone
        // Spare capacity = (80% threshold - current utilization) * room-slot hours / slots
        List<ZoneCapacity> availableAlternatives = new ArrayList<>();
        for (ZoneStats spare : zonesWithSpareCapacity) {
            if (spare.zone.getId().equals(overloaded.zone.getId())) continue;
            
            // Check if courses in overloaded zone can also use this spare zone
            boolean hasOverlap = false;
            for (Lesson lesson : lessons) {
                Course course = lesson.getCourse();
                if (course != null && courseIdsInOverloaded.contains(course.getId()) 
                        && course.getAllowedZones() != null) {
                    boolean canUseSpare = course.getAllowedZones().stream()
                            .anyMatch(z -> z.getId().equals(spare.zone.getId()));
                    if (canUseSpare) {
                        hasOverlap = true;
                        break;
                    }
                }
            }
            
            if (hasOverlap) {
                // Calculate how many additional rooms worth of capacity is available
                double spareCapacityHours = (80.0 - spare.utilization) / 100.0 * spare.roomSlotHours;
                int spareRoomsEquivalent = (int) Math.floor(spareCapacityHours / totalSlots);
                if (spareRoomsEquivalent > 0) {
                    availableAlternatives.add(new ZoneCapacity(spare.zoneName, spareRoomsEquivalent, spare.utilization));
                }
            }
        }
        
        // Generate alternative combinations
        // Sort by utilization (prefer zones with more spare capacity)
        availableAlternatives.sort((a, b) -> Double.compare(a.utilization, b.utilization));
        
        int roomsNeeded = (int) Math.ceil((overloaded.lessonHours - overloaded.roomSlotHours * 0.80) / totalSlots);
        
        // Calculate total spare capacity available across alternatives
        int totalSpareCapacity = availableAlternatives.stream().mapToInt(a -> a.spareRooms).sum();
        
        // Option 2: Use existing spare capacity in alternative zones (no new rooms needed!)
        if (totalSpareCapacity >= roomsNeeded) {
            // We have enough spare capacity - just show which zones to use
            StringBuilder desc = new StringBuilder("Use existing spare capacity in: ");
            int remaining = roomsNeeded;
            List<String> parts = new ArrayList<>();
            for (ZoneCapacity alt : availableAlternatives) {
                int useFromThis = Math.min(remaining, alt.spareRooms);
                if (useFromThis > 0) {
                    parts.add(String.format("%s (%.0f%% used, %d rooms spare)", 
                        alt.zoneName, alt.utilization, alt.spareRooms));
                    remaining -= useFromThis;
                    if (remaining <= 0) break;
                }
            }
            desc.append(String.join(", ", parts));
            desc.append(" - NO new rooms needed!");
            alternatives.add(new ZoneAlternative(desc.toString()));
        } else {
            // Not enough spare capacity - need to add some rooms
            int shortfall = roomsNeeded - totalSpareCapacity;
            StringBuilder desc = new StringBuilder();
            if (totalSpareCapacity > 0) {
                desc.append(String.format("Use spare capacity in %s (%d rooms equivalent) + ADD %d room(s) to %s",
                    availableAlternatives.get(0).zoneName, 
                    Math.min(availableAlternatives.get(0).spareRooms, roomsNeeded),
                    shortfall, overloaded.zoneName));
            } else {
                desc.append(String.format("ADD %d room(s) to %s", shortfall, overloaded.zoneName));
            }
            alternatives.add(new ZoneAlternative(desc.toString()));
        }
        
        // Option 3: Split - add some to overloaded, use some spare capacity
        if (availableAlternatives.size() >= 1 && availableAlternatives.get(0).spareRooms > 0) {
            ZoneCapacity best = availableAlternatives.get(0);
            int useFromSpare = Math.min(roomsNeeded / 2, best.spareRooms);
            int addToOverloaded = roomsNeeded - useFromSpare;
            if (addToOverloaded > 0 && useFromSpare > 0) {
                alternatives.add(new ZoneAlternative(
                    String.format("ADD %d room(s) to %s + use %d rooms worth of spare capacity in %s", 
                        addToOverloaded, overloaded.zoneName, useFromSpare, best.zoneName)));
            }
        }
        
        return alternatives;
    }
    
    // Helper class for zone issues
    private static class ZoneIssue {
        ZoneStats zoneStats;
        int roomsNeeded;
        double restrictedHours;
        double flexibleHours;
        boolean isRestricted;
        
        ZoneIssue(ZoneStats zoneStats, int roomsNeeded, double restrictedHours, double flexibleHours, boolean isRestricted) {
            this.zoneStats = zoneStats;
            this.roomsNeeded = roomsNeeded;
            this.restrictedHours = restrictedHours;
            this.flexibleHours = flexibleHours;
            this.isRestricted = isRestricted;
        }
    }
    
    // Helper class for tight constraint info
    private static class LessonConstraintInfo {
        String courseCode;
        int studentCount;
        int validRooms;
        Set<Feature> requiredFeatures;
        Set<Zone> allowedZones;
        
        LessonConstraintInfo(String courseCode, int studentCount, int validRooms, Set<Feature> requiredFeatures, Set<Zone> allowedZones) {
            this.courseCode = courseCode;
            this.studentCount = studentCount;
            this.validRooms = validRooms;
            this.requiredFeatures = requiredFeatures;
            this.allowedZones = allowedZones;
        }
    }
    
    // Helper class for course-specific actions
    private static class CourseAction {
        String courseCode;
        int lessonCount;
        int sharedRooms;
        int roomsNeeded;
        Set<Feature> requiredFeatures;
        Set<Zone> allowedZones;
        int minCapacity;
        
        CourseAction(String courseCode, int lessonCount, int sharedRooms, int roomsNeeded, 
                     Set<Feature> requiredFeatures, Set<Zone> allowedZones, int minCapacity) {
            this.courseCode = courseCode;
            this.lessonCount = lessonCount;
            this.sharedRooms = sharedRooms;
            this.roomsNeeded = roomsNeeded;
            this.requiredFeatures = requiredFeatures != null ? requiredFeatures : Set.of();
            this.allowedZones = allowedZones != null ? allowedZones : Set.of();
            this.minCapacity = minCapacity;
        }
    }
    
    // Helper class for course+group specific actions
    private static class CourseGroupAction {
        String courseCode;
        String groupName;
        int lessonCount;
        int sharedRooms;
        int roomsNeeded;
        Set<Feature> requiredFeatures;
        Set<Zone> allowedZones;
        int minCapacity;
        
        CourseGroupAction(String courseCode, String groupName, int lessonCount, int sharedRooms, int roomsNeeded, 
                          Set<Feature> requiredFeatures, Set<Zone> allowedZones, int minCapacity) {
            this.courseCode = courseCode;
            this.groupName = groupName;
            this.lessonCount = lessonCount;
            this.sharedRooms = sharedRooms;
            this.roomsNeeded = roomsNeeded;
            this.requiredFeatures = requiredFeatures != null ? requiredFeatures : Set.of();
            this.allowedZones = allowedZones != null ? allowedZones : Set.of();
            this.minCapacity = minCapacity;
        }
    }
    
    // Helper class for zone alternatives
    private static class ZoneAlternative {
        String description;
        ZoneAlternative(String description) { this.description = description; }
    }
    
    // Helper class for zone capacity tracking
    private static class ZoneCapacity {
        String zoneName;
        int spareRooms;
        double utilization;
        ZoneCapacity(String zoneName, int spareRooms, double utilization) {
            this.zoneName = zoneName;
            this.spareRooms = spareRooms;
            this.utilization = utilization;
        }
    }
    
    private LessonBreakdown createLessonBreakdown(Lesson lesson) {
        Course course = lesson.getCourse();
        return new LessonBreakdown(
            course != null ? course.getCode() : "N/A",
            course != null ? course.getName() : "Unknown Course",
            lesson.getDurationHours(),
            lesson.getStudentGroups() != null 
                ? lesson.getStudentGroups().stream().map(StudentGroup::getName).toList() 
                : List.of(),
            lesson.getLecturer() != null ? lesson.getLecturer().getName() : "TBA",
            lesson.getTotalStudentCount()
        );
    }
    
    // Helper classes for statistics
    private static class ZoneStats {
        Zone zone;
        String zoneName;
        double lessonsNeedingZone; // Fractional - lesson can span multiple zones
        double lessonHours; // Fractional
        int roomsInZone;             // Usable rooms with sufficient capacity
        int totalRoomsCount;         // Total rooms in zone
        int roomsWithInsufficientCapacity; // Rooms too small for lessons
        int roomSlotHours;
        int minCapacityNeeded;       // Minimum capacity required for this zone
        double utilization;
        List<LessonBreakdown> lessonBreakdown = new ArrayList<>();
    }
    
    private static class FeatureStats {
        Feature feature;
        String featureName;
        int lessonsNeedingFeature;
        int lessonHours;
        int roomsWithFeature;      // Rooms with feature AND sufficient capacity
        int totalRoomsCount;       // Total rooms with feature (regardless of capacity)
        int roomsWithInsufficientCapacity; // Rooms with feature but too small
        int roomSlotHours;
        int totalCapacity;
        int usableCapacity;        // Capacity of rooms with sufficient size
        int totalStudentsNeeding;
        int minCapacityNeeded;     // Minimum capacity required for this feature
        double utilization;
        List<LessonBreakdown> lessonBreakdown = new ArrayList<>();
    }
    
    private static class LecturerStats {
        Long lecturerId;
        String name;
        int hours;
        int lessonCount;
        List<LessonBreakdown> lessonBreakdown = new ArrayList<>();
    }
    
    private static class GroupStats {
        Long groupId;
        String name;
        int hours;
        int lessonCount;
        List<LessonBreakdown> lessonBreakdown = new ArrayList<>();
    }
    
    // Lesson breakdown for popup display
    public static class LessonBreakdown {
        public String courseCode;
        public String courseName;
        public int weeklyHours;
        public List<String> studentGroups;
        public String lecturerName;
        public int studentCount;
        
        public LessonBreakdown(String courseCode, String courseName, int weeklyHours, 
                List<String> studentGroups, String lecturerName, int studentCount) {
            this.courseCode = courseCode;
            this.courseName = courseName;
            this.weeklyHours = weeklyHours;
            this.studentGroups = studentGroups;
            this.lecturerName = lecturerName;
            this.studentCount = studentCount;
        }
    }
}
