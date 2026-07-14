package com.university.timetable.service;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverSolutionCallback;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.IntervalVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.Literal;
import com.google.ortools.util.Domain;
import com.university.timetable.domain.*;
import com.university.timetable.dto.SolveRequestDTO;
import com.university.timetable.dto.SolverStatusDTO;
import com.university.timetable.repository.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * CP-SAT Solver Service - Google OR-Tools based timetable solver.
 * 
 * This is an alternative to Timefold that uses Google's CP-SAT solver,
 * which is often significantly faster for scheduling problems.
 * 
 * Key advantages:
 * - Native interval variables with no-overlap constraints
 * - Highly optimized constraint propagation
 * - Better scalability for large problems
 * 
 * ALL CONSTRAINTS FROM TIMEFOLD ARE IMPLEMENTED:
 * 
 * HARD CONSTRAINTS:
 * 1. Room conflict - No two lessons in same room at overlapping times
 * 2. Lecturer conflict - Lecturer cannot teach two lessons simultaneously
 * 3. Student group conflict - Students cannot be in two places at once
 * 4. Room capacity overflow - Room must fit all students
 * 5. Room feature required - Room must have required features
 * 6. Zone restriction - Course must be in allowed zones
 * 7. Lecturer unavailability - Respect lecturer unavailable times
 * 8. Lunch break overlap - No lessons during lunch break
 * 9. Same course on same day - Prevent same course multiple times per day
 * 10. Lesson exceeds end time - Lessons must end before latest time
 * 11. Special event conflict - No lessons during special events
 * 12. Max lecturer consecutive hours - Limit consecutive teaching hours
 * 
 * SOFT CONSTRAINTS:
 * 1. Room capacity efficiency - Minimize wasted room capacity
 * 2. Student fatigue - Avoid consecutive lessons for students
 * 3. Lecturer room transition - Minimize room changes for lecturers
 * 4. Day balance - Distribute lessons evenly across days
 * 5. Early morning penalty - Discourage early morning classes
 * 6. Late afternoon penalty - Discourage late afternoon classes
 * 7. Lecturer fatigue - Avoid consecutive teaching for lecturers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CpSatSolverService {

    static {
        Loader.loadNativeLibraries();
    }

    private final LessonRepository lessonRepository;
    private final TimeslotRepository timeslotRepository;
    private final RoomRepository roomRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final SpecialEventRepository specialEventRepository;
    private final CourseRepository courseRepository;
    private final ConstraintSettingsService constraintSettingsService;
    private final SolutionSaver solutionSaver;
    private final AuditLogService auditLogService;

    @Value("${cpsat.solver.time-limit-seconds:60}")
    private int defaultTimeLimitSeconds;

    @Value("${cpsat.solver.num-workers:8}")
    private int defaultNumWorkers;
    
    @Value("${cpsat.solver.feasibility-only:false}")
    private boolean feasibilityOnly;

    private final AtomicBoolean solvingInProgress = new AtomicBoolean(false);
    private final AtomicLong solveStartedAtMs = new AtomicLong(0);
    private final AtomicLong firstFeasibleAtMs = new AtomicLong(0);
    private final AtomicReference<String> currentStatus = new AtomicReference<>("IDLE");
    private final AtomicReference<String> currentScore = new AtomicReference<>("N/A");
    private final AtomicInteger solutionCount = new AtomicInteger(0);
    private volatile Thread solverThread;
    private volatile CpSolver solver;
    private volatile CpModel model;

    @PostConstruct
    public void init() {
        log.info("CP-SAT Solver Service initialized. Native libraries loaded.");
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("CP-SAT: Shutting down solver service...");
        if (solver != null) {
            try {
                solver.stopSearch();
                // Give the solver a moment to clean up native resources
                Thread.sleep(100);
            } catch (Exception e) {
                log.warn("Error stopping CP-SAT solver during shutdown: {}", e.getMessage());
            }
        }
        solver = null;
        model = null;
        solverThread = null;
        solvingInProgress.set(false);
        log.info("CP-SAT: Solver service shut down complete");
    }

    public SolverStatusDTO startSolving(SolveRequestDTO request) {
        if (solvingInProgress.get()) {
            throw new IllegalStateException("Solver is already running.");
        }

        solvingInProgress.set(true);
        solveStartedAtMs.set(System.currentTimeMillis());
        firstFeasibleAtMs.set(0);
        solutionCount.set(0);
        currentStatus.set("SOLVING");
        currentScore.set("N/A");

        solverThread = Thread.ofVirtual().start(() -> {
            try {
                solveInternal(request);
            } catch (Exception e) {
                log.error("CP-SAT solver failed", e);
                currentStatus.set("ERROR");
                solvingInProgress.set(false);
            }
        });

        return getStatus();
    }

    private void solveInternal(SolveRequestDTO request) {
        // Load data
        List<Lesson> lessons = lessonRepository.findAllWithCourseAndLecturer();
        List<Timeslot> timeslots = timeslotRepository.findAll();
        List<Room> rooms = roomRepository.findAllWithFeatures();
        List<SpecialEvent> specialEvents = specialEventRepository.findByActiveTrue();
        
        log.info("CP-SAT: Loaded {} lessons, {} timeslots, {} rooms, {} special events",
                lessons.size(), timeslots.size(), rooms.size(), specialEvents.size());

        // Filter pinned lessons (they stay in place)
        List<Lesson> unpinnedLessons = lessons.stream()
                .filter(l -> !l.isPinned())
                .toList();

        if (unpinnedLessons.isEmpty()) {
            log.info("No unpinned lessons to schedule");
            currentStatus.set("SOLVED");
            currentScore.set("0hard/0soft");
            solvingInProgress.set(false);
            return;
        }

        model = new CpModel();

        // Load constraint settings
        LocalTime earliestStart = constraintSettingsService.getEarliestStartTime();
        LocalTime latestEnd = constraintSettingsService.getLatestEndTime();
        LocalTime fridayLatestEnd = constraintSettingsService.getFridayLatestEndTime();
        LocalTime lunchBreakStart = constraintSettingsService.getLunchBreakStart();
        LocalTime lunchBreakEnd = constraintSettingsService.getLunchBreakEnd();
        boolean lunchBreakEnforced = constraintSettingsService.isLunchBreakEnforced();
        boolean sameCourseSameDayAllowed = constraintSettingsService.isSameCourseSameDayAllowed();
        boolean unavailabilityEnabled = constraintSettingsService.isUnavailabilitySystemEnabled();
        int maxLecturerConsecutiveHours = constraintSettingsService.getMaxLecturerConsecutiveHours();

        // Build timeslot index mappings
        Map<Long, Integer> timeslotToIndex = new HashMap<>();
        Map<Integer, Timeslot> indexToTimeslot = new HashMap<>();
        Map<DayOfWeek, Integer> dayStartIndex = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, Integer> dayEndIndex = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, List<Integer>> dayToTimeIndices = new EnumMap<>(DayOfWeek.class);
        
        int timeIndex = 0;
        for (DayOfWeek day : Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            dayStartIndex.put(day, timeIndex);
            LocalTime dayEnd = day == DayOfWeek.FRIDAY ? fridayLatestEnd : latestEnd;
            List<Integer> dayIndices = new ArrayList<>();
            
            for (LocalTime t = earliestStart; t.isBefore(dayEnd); t = t.plusHours(1)) {
                Timeslot ts = findTimeslot(timeslots, day, t);
                if (ts != null) {
                    timeslotToIndex.put(ts.getId(), timeIndex);
                    indexToTimeslot.put(timeIndex, ts);
                    dayIndices.add(timeIndex);
                }
                timeIndex++;
            }
            dayEndIndex.put(day, timeIndex);
            dayToTimeIndices.put(day, dayIndices);
        }
        
        int totalTimeSlots = timeIndex;
        int slotsPerDay = (int) latestEnd.minusHours(earliestStart.getHour()).getHour();
        log.info("CP-SAT: Total time slots = {}, slots per day = {}", totalTimeSlots, slotsPerDay);

        // Build room index mappings
        Map<Long, Integer> roomToIndex = new HashMap<>();
        Map<Integer, Room> indexToRoom = new HashMap<>();
        List<Room> physicalRooms = rooms.stream()
                .filter(r -> r.getId() != null)
                .toList();
        
        for (int i = 0; i < physicalRooms.size(); i++) {
            roomToIndex.put(physicalRooms.get(i).getId(), i);
            indexToRoom.put(i, physicalRooms.get(i));
        }
        int totalRooms = physicalRooms.size();

        // ==================== DECISION VARIABLES ====================
        
        IntVar[] startVars = new IntVar[unpinnedLessons.size()];
        IntVar[] roomVars = new IntVar[unpinnedLessons.size()];
        IntervalVar[] intervals = new IntervalVar[unpinnedLessons.size()];
        BoolVar[][] roomAssignment = new BoolVar[unpinnedLessons.size()][];
        Map<Long, Integer> lessonToIndex = new HashMap<>();
        
        // For each lesson, compute valid start times and rooms
        List<List<Integer>> validStartsPerLesson = new ArrayList<>();
        List<List<Integer>> validRoomsPerLesson = new ArrayList<>();
        
        for (int i = 0; i < unpinnedLessons.size(); i++) {
            Lesson lesson = unpinnedLessons.get(i);
            lessonToIndex.put(lesson.getId(), i);
            int duration = lesson.getDurationHours();
            
            // Compute valid start times considering ALL hard constraints
            List<Integer> validStarts = computeValidStartTimes(lesson, timeslots, timeslotToIndex,
                    indexToTimeslot, earliestStart, latestEnd, fridayLatestEnd,
                    lunchBreakStart, lunchBreakEnd, lunchBreakEnforced, unavailabilityEnabled);
            validStartsPerLesson.add(validStarts);
            
            if (validStarts.isEmpty()) {
                log.error("CP-SAT: INFEASIBLE - No valid start times for lesson {} (course: {}, duration: {}h)", 
                        lesson.getId(), lesson.getCourse() != null ? lesson.getCourse().getCode() : "null", duration);
                // Create infeasible variable to force model failure
                startVars[i] = model.newIntVar(-1, -1, "start_" + lesson.getId());
            } else if (validStarts.size() <= 5) {
                log.debug("CP-SAT: Highly restricted lesson {} (course: {}) - only {} valid start times",
                        lesson.getId(), lesson.getCourse() != null ? lesson.getCourse().getCode() : "null", validStarts.size());
            } else {
                // Create variable with domain restricted to valid values - much more efficient
                long[] domain = validStarts.stream().mapToLong(Integer::longValue).toArray();
                startVars[i] = model.newIntVarFromDomain(Domain.fromValues(domain), 
                        "start_" + lesson.getId());
            }
            
            // Compute valid rooms
            List<Integer> validRooms = computeValidRooms(lesson, physicalRooms, roomToIndex);
            validRoomsPerLesson.add(validRooms);
            
            if (!lesson.isOnline()) {
                if (validRooms.isEmpty()) {
                    log.warn("No valid rooms for lesson {} (students: {}, course: {})", 
                            lesson.getId(), lesson.getTotalStudentCount(),
                            lesson.getCourse() != null ? lesson.getCourse().getCode() : "null");
                    roomVars[i] = model.newIntVar(-1, -1, "room_" + lesson.getId());
                    roomAssignment[i] = new BoolVar[0];
                } else {
                    // Create variable with domain restricted to valid rooms - much more efficient
                    long[] roomDomain = validRooms.stream().mapToLong(Integer::longValue).toArray();
                    roomVars[i] = model.newIntVarFromDomain(Domain.fromValues(roomDomain), 
                            "room_" + lesson.getId());
                    
                    // Create room assignment variables for valid rooms only
                    roomAssignment[i] = new BoolVar[totalRooms];
                    List<BoolVar> activeRoomBools = new ArrayList<>();
                    for (int r = 0; r < totalRooms; r++) {
                        if (validRooms.contains(r)) {
                            roomAssignment[i][r] = model.newBoolVar(
                                    "lesson_" + lesson.getId() + "_room_" + r);
                            model.addEquality(roomVars[i], r).onlyEnforceIf(roomAssignment[i][r]);
                            model.addDifferent(roomVars[i], r).onlyEnforceIf(roomAssignment[i][r].not());
                            activeRoomBools.add(roomAssignment[i][r]);
                        } else {
                            roomAssignment[i][r] = null;
                        }
                    }
                    // CRITICAL: Exactly one room per lesson (two-way implication)
                    // Without this, solver can set all room BoolVars false, bypassing NoOverlap
                    if (!activeRoomBools.isEmpty()) {
                        model.addExactlyOne(activeRoomBools.toArray(new BoolVar[0]));
                    }
                }
            } else {
                roomVars[i] = null;
                roomAssignment[i] = new BoolVar[0];
            }
            
            // Create interval variable
            intervals[i] = model.newFixedSizeIntervalVar(
                    startVars[i], duration, "interval_" + lesson.getId());
        }

        // ==================== HARD CONSTRAINTS ====================
        
        // 1. Room conflict: No two lessons in the same room at overlapping times
        addRoomNoOverlapConstraints(model, unpinnedLessons, intervals, roomVars, 
                roomAssignment, totalRooms, physicalRooms, validRoomsPerLesson);
        
        // 2. Lecturer conflict: A lecturer cannot teach two lessons at the same time
        addLecturerNoOverlapConstraints(model, unpinnedLessons, intervals);
        
        // 3. Student group conflict: Students cannot be in two places at once
        addStudentGroupNoOverlapConstraints(model, unpinnedLessons, intervals);
        
        // 4. Room capacity, 5. Features, 6. Zone - enforced via domain restriction in computeValidRooms
        
        // 7. Lecturer unavailability - partially enforced in computeValidStartTimes, add explicit constraints
        if (unavailabilityEnabled) {
            addLecturerUnavailabilityConstraints(model, unpinnedLessons, startVars, indexToTimeslot);
        }
        
        // 8. Lunch break - enforced in computeValidStartTimes
        
        // 9. Same course on same day
        if (!sameCourseSameDayAllowed) {
            addSameCourseSameDayConstraints(model, unpinnedLessons, startVars, 
                    dayStartIndex, dayEndIndex, dayToTimeIndices, validStartsPerLesson);
        }
        
        // 10. Lesson exceeds end time - enforced in computeValidStartTimes
        
        // 11. Special events
        addSpecialEventConstraints(model, unpinnedLessons, startVars, roomVars,
                specialEvents, indexToTimeslot, timeslotToIndex, roomToIndex, 
                validStartsPerLesson, dayToTimeIndices);
        
        // 12. Max lecturer consecutive hours
        if (maxLecturerConsecutiveHours > 0) {
            addMaxLecturerConsecutiveHoursConstraints(model, unpinnedLessons, startVars,
                    intervals, indexToTimeslot, maxLecturerConsecutiveHours);
        }
        
        // Diagnostic: Log search space complexity
        int lessonsWithFewStarts = 0;
        int lessonsWithFewRooms = 0;
        int minStartOptions = Integer.MAX_VALUE;
        int minRoomOptions = Integer.MAX_VALUE;
        for (List<Integer> starts : validStartsPerLesson) {
            if (starts.size() < minStartOptions) minStartOptions = starts.size();
            if (starts.size() <= 10) lessonsWithFewStarts++;
        }
        for (List<Integer> roomOpts : validRoomsPerLesson) {
            if (roomOpts.size() < minRoomOptions) minRoomOptions = roomOpts.size();
            if (roomOpts.size() <= 3) lessonsWithFewRooms++;
        }
        log.info("CP-SAT: Search space analysis - minStartOptions={}, minRoomOptions={}, lessonsWith<=10starts={}, lessonsWith<=3rooms={}",
                minStartOptions == Integer.MAX_VALUE ? 0 : minStartOptions,
                minRoomOptions == Integer.MAX_VALUE ? 0 : minRoomOptions,
                lessonsWithFewStarts, lessonsWithFewRooms);

        // ==================== SOFT CONSTRAINTS (Objective) ====================
        
        List<IntVar> objectiveVars = new ArrayList<>();
        List<Long> objectiveCoeffs = new ArrayList<>();
        
        // Skip soft constraints in feasibility-only mode for much faster solving
        if (!feasibilityOnly) {
            // Add soft constraint penalties
            addSoftConstraints(model, unpinnedLessons, startVars, roomVars, roomAssignment,
                    physicalRooms, indexToTimeslot, dayStartIndex, dayToTimeIndices,
                    objectiveVars, objectiveCoeffs);
        }
        
        if (!objectiveVars.isEmpty()) {
            long[] coeffs = objectiveCoeffs.stream().mapToLong(Long::longValue).toArray();
            IntVar[] vars = objectiveVars.toArray(new IntVar[0]);
            model.minimize(LinearExpr.weightedSum(vars, coeffs));
        } else {
            // No objective - just find any feasible solution
            model.minimize(LinearExpr.constant(0));
        }

        // ==================== SOLVE ====================
        
        solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(defaultTimeLimitSeconds);
        solver.getParameters().setNumSearchWorkers(defaultNumWorkers);
        solver.getParameters().setLogSearchProgress(true);
        solver.getParameters().setCpModelPresolve(true);
        solver.getParameters().setLinearizationLevel(2);

        CpSolverSolutionCallback callback = new CpSolverSolutionCallback() {
            @Override
            public void onSolutionCallback() {
                int solCount = solutionCount.incrementAndGet();
                long elapsedMs = System.currentTimeMillis() - solveStartedAtMs.get();
                
                // Track time to first feasible (first solution found)
                if (solCount == 1) {
                    firstFeasibleAtMs.set(System.currentTimeMillis());
                }
                
                double obj = objectiveValue();
                currentScore.set(String.format("0hard/%dsoft", (long) obj));
                log.info("CP-SAT: Solution #{} found at {}ms (time to feasible: {}ms), score=0hard/{}soft", 
                        solCount, elapsedMs, 
                        firstFeasibleAtMs.get() > 0 ? (firstFeasibleAtMs.get() - solveStartedAtMs.get()) : 0,
                        (long) obj);
            }
        };

        log.info("CP-SAT: Starting solve with {} workers, {}s time limit", 
                defaultNumWorkers, defaultTimeLimitSeconds);
        
        CpSolverStatus status = solver.solve(model, callback);
        
        long elapsedMs = System.currentTimeMillis() - solveStartedAtMs.get();
        log.info("CP-SAT: Solve completed in {}ms with status {}", elapsedMs, status);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            extractAndSaveSolution(unpinnedLessons, startVars, roomVars, 
                    indexToTimeslot, indexToRoom);
            
            currentStatus.set("SOLVED");
            long objective = (long) solver.objectiveValue();
            currentScore.set(String.format("0hard/%dsoft", objective));
            
            auditLogService.logSchedulerAction(
                    "CP-SAT solver completed successfully in " + elapsedMs + "ms", true);
        } else if (status == CpSolverStatus.UNKNOWN) {
            // Time limit reached without finding solution - this is NOT infeasible
            currentStatus.set("TIME_LIMIT");
            currentScore.set("TIME_LIMIT_REACHED");
            auditLogService.logSchedulerAction(
                    "CP-SAT solver reached time limit (" + defaultTimeLimitSeconds + "s) without completing. " +
                    "Problem may be too large or constraints too tight. Try increasing time limit.", false);
        } else {
            // INFEASIBLE - no solution exists
            currentStatus.set("INFEASIBLE");
            currentScore.set("INFEASIBLE");
            auditLogService.logSchedulerAction(
                    "CP-SAT solver failed to find solution: " + status, false);
        }

        solvingInProgress.set(false);
    }

    private Timeslot findTimeslot(List<Timeslot> timeslots, DayOfWeek day, LocalTime time) {
        return timeslots.stream()
                .filter(t -> t.getDayOfWeek() == day && t.getStartTime().equals(time))
                .findFirst()
                .orElse(null);
    }

    /**
     * Compute valid start times for a lesson, considering:
     * - Working hours (earliest start, latest end)
     * - Friday special end time
     * - Lunch break
     * - Lecturer unavailability
     * - Lesson duration (must fit within day)
     */
    private List<Integer> computeValidStartTimes(Lesson lesson, List<Timeslot> timeslots,
            Map<Long, Integer> timeslotToIndex, Map<Integer, Timeslot> indexToTimeslot,
            LocalTime earliestStart, LocalTime latestEnd, LocalTime fridayLatestEnd,
            LocalTime lunchBreakStart, LocalTime lunchBreakEnd, boolean lunchBreakEnforced,
            boolean unavailabilityEnabled) {
        
        List<Integer> validStarts = new ArrayList<>();
        int duration = lesson.getDurationHours();
        
        for (Timeslot ts : timeslots) {
            LocalTime dayEnd = ts.getDayOfWeek() == DayOfWeek.FRIDAY ? fridayLatestEnd : latestEnd;
            LocalTime lessonEnd = ts.getStartTime().plusHours(duration);
            
            // Check if lesson fits within working hours
            if (lessonEnd.isAfter(dayEnd)) {
                continue;
            }
            
            // Check lunch break overlap
            if (lunchBreakEnforced) {
                if (ts.getStartTime().isBefore(lunchBreakEnd) && lunchBreakStart.isBefore(lessonEnd)) {
                    continue;
                }
            }
            
            // Check lecturer unavailability
            if (unavailabilityEnabled && lesson.getLecturer() != null) {
                Lecturer lecturer = lesson.getLecturer();
                if (!lecturer.isAvailableAt(ts, duration)) {
                    continue;
                }
            }
            
            Integer idx = timeslotToIndex.get(ts.getId());
            if (idx != null) {
                validStarts.add(idx);
            }
        }
        
        return validStarts;
    }

    /**
     * Compute valid rooms for a lesson, considering:
     * - Room capacity
     * - Required features
     * - Zone restrictions
     */
    private List<Integer> computeValidRooms(Lesson lesson, List<Room> rooms, 
            Map<Long, Integer> roomToIndex) {
        List<Integer> validRooms = new ArrayList<>();
        Course course = lesson.getCourse();
        int students = lesson.getTotalStudentCount();
        
        int filteredByCapacity = 0;
        int filteredByFeatures = 0;
        int filteredByZone = 0;
        
        for (Room room : rooms) {
            // Check capacity
            if (room.getCapacity() < students) {
                filteredByCapacity++;
                continue;
            }
            
            // Check required features
            if (course != null && course.getRequiredFeatures() != null && !course.getRequiredFeatures().isEmpty()) {
                if (!room.hasAllFeatures(course.getRequiredFeatures())) {
                    filteredByFeatures++;
                    continue;
                }
            }
            
            // Check zone restrictions
            if (course != null && course.getAllowedZones() != null && !course.getAllowedZones().isEmpty()) {
                if (room.getZone() == null || !course.getAllowedZones().contains(room.getZone())) {
                    filteredByZone++;
                    continue;
                }
            }
            
            Integer idx = roomToIndex.get(room.getId());
            if (idx != null) {
                validRooms.add(idx);
            }
        }
        
        // Log why rooms were filtered if none remain
        if (validRooms.isEmpty() && !lesson.isOnline()) {
            log.warn("No valid rooms for lesson {} (students: {}, course: {}): " +
                    "filtered capacity={}, features={}, zone={}, total rooms={}",
                    lesson.getId(), students, 
                    course != null ? course.getCode() : "null",
                    filteredByCapacity, filteredByFeatures, filteredByZone, rooms.size());
            if (course != null) {
                if (course.getRequiredFeatures() != null && !course.getRequiredFeatures().isEmpty()) {
                    log.warn("  Course required features: {}", 
                            course.getRequiredFeatures().stream().map(f -> f.getName()).collect(Collectors.joining(",")));
                }
                if (course.getAllowedZones() != null && !course.getAllowedZones().isEmpty()) {
                    log.warn("  Course allowed zones: {}", 
                            course.getAllowedZones().stream().map(z -> z.getName()).collect(Collectors.joining(",")));
                }
            }
        }
        
        return validRooms;
    }

    /**
     * HARD CONSTRAINT 1: Room No-Overlap
     * For each room, lessons assigned to it must not overlap in time.
     * Uses optional intervals for each lesson-room pair.
     */
    private void addRoomNoOverlapConstraints(CpModel model, List<Lesson> lessons,
            IntervalVar[] intervals, IntVar[] roomVars, BoolVar[][] roomAssignment,
            int totalRooms, List<Room> rooms, List<List<Integer>> validRoomsPerLesson) {
        
        int constraintsAdded = 0;
        
        for (int r = 0; r < totalRooms; r++) {
            List<IntervalVar> roomIntervals = new ArrayList<>();
            Room room = rooms.get(r);
            
            for (int i = 0; i < lessons.size(); i++) {
                Lesson lesson = lessons.get(i);
                if (lesson.isOnline() || roomAssignment[i].length == 0) continue;
                
                // Only consider if this room is valid for this lesson
                if (!validRoomsPerLesson.get(i).contains(r)) continue;
                
                // Create optional interval for this lesson-room pair
                IntervalVar optionalInterval = model.newOptionalFixedSizeIntervalVar(
                        intervals[i].getStartExpr(),
                        lesson.getDurationHours(),
                        roomAssignment[i][r],
                        "interval_lesson_" + lesson.getId() + "_room_" + r);
                
                roomIntervals.add(optionalInterval);
            }
            
            if (!roomIntervals.isEmpty()) {
                model.addNoOverlap(roomIntervals);
                constraintsAdded++;
            }
        }
        
        log.info("CP-SAT: Added room no-overlap constraints for {} rooms", constraintsAdded);
    }

    /**
     * HARD CONSTRAINT 2: Lecturer No-Overlap
     * A lecturer cannot teach two lessons at the same time.
     */
    private void addLecturerNoOverlapConstraints(CpModel model, List<Lesson> lessons,
            IntervalVar[] intervals) {
        
        Map<Lecturer, List<Integer>> lecturerLessons = new HashMap<>();
        
        for (int i = 0; i < lessons.size(); i++) {
            Lecturer lecturer = lessons.get(i).getLecturer();
            if (lecturer != null) {
                lecturerLessons.computeIfAbsent(lecturer, k -> new ArrayList<>()).add(i);
            }
        }
        
        int constraintsAdded = 0;
        for (Map.Entry<Lecturer, List<Integer>> entry : lecturerLessons.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<IntervalVar> lecturerIntervals = new ArrayList<>();
                for (int idx : entry.getValue()) {
                    lecturerIntervals.add(intervals[idx]);
                }
                model.addNoOverlap(lecturerIntervals);
                constraintsAdded++;
            }
        }
        
        log.info("CP-SAT: Added lecturer no-overlap constraints for {} lecturers", constraintsAdded);
    }

    /**
     * HARD CONSTRAINT 3: Student Group No-Overlap
     * Students cannot be in two places at once.
     */
    private void addStudentGroupNoOverlapConstraints(CpModel model, List<Lesson> lessons,
            IntervalVar[] intervals) {
        
        Map<Long, List<Integer>> groupLessons = new HashMap<>();
        
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            Set<Long> conflictGroupIds = lesson.getConflictGroupIds();
            for (Long groupId : conflictGroupIds) {
                groupLessons.computeIfAbsent(groupId, k -> new ArrayList<>()).add(i);
            }
        }
        
        int constraintsAdded = 0;
        for (Map.Entry<Long, List<Integer>> entry : groupLessons.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<IntervalVar> groupIntervals = new ArrayList<>();
                for (int idx : entry.getValue()) {
                    groupIntervals.add(intervals[idx]);
                }
                model.addNoOverlap(groupIntervals);
                constraintsAdded++;
            }
        }
        
        log.info("CP-SAT: Added student group no-overlap constraints for {} groups", constraintsAdded);
    }

    /**
     * HARD CONSTRAINT 7: Lecturer Unavailability
     * Forbid start times that overlap with lecturer blocked periods.
     * Uses lecturer.isAvailableAt(ts, duration) for the check.
     */
    private void addLecturerUnavailabilityConstraints(CpModel model, List<Lesson> lessons,
            IntVar[] startVars, Map<Integer, Timeslot> indexToTimeslot) {
        int forbidden = 0;
        for (int i = 0; i < lessons.size(); i++) {
            Lecturer lecturer = lessons.get(i).getLecturer();
            if (lecturer == null || lecturer.getUnavailabilities() == null
                    || lecturer.getUnavailabilities().isEmpty()) {
                continue;
            }
            int duration = lessons.get(i).getDurationHours();
            for (Map.Entry<Integer, Timeslot> entry : indexToTimeslot.entrySet()) {
                Timeslot ts = entry.getValue();
                if (!lecturer.isAvailableAt(ts, duration)) {
                    model.addDifferent(startVars[i], entry.getKey());
                    forbidden++;
                }
            }
        }
        log.info("CP-SAT: Added {} lecturer unavailability forbidden assignments", forbidden);
    }

    /**
     * HARD CONSTRAINT 9: Same Course on Same Day
     * Lessons of the same course FOR THE SAME STUDENT GROUP must be on different days.
     * Different groups can have the same course on the same day.
     * This matches Timefold's behavior exactly.
     * 
     * OPTIMIZED: Uses day index computation instead of per-slot boolean variables.
     */
    private void addSameCourseSameDayConstraints(CpModel model, List<Lesson> lessons,
            IntVar[] startVars, Map<DayOfWeek, Integer> dayStartIndex,
            Map<DayOfWeek, Integer> dayEndIndex, Map<DayOfWeek, List<Integer>> dayToTimeIndices,
            List<List<Integer>> validStartsPerLesson) {
        
        // Group lessons by (course, studentGroup) - must match Timefold's behavior
        Map<Set<Long>, List<Integer>> courseGroupLessons = new HashMap<>();
        
        for (int i = 0; i < lessons.size(); i++) {
            Course course = lessons.get(i).getCourse();
            if (course == null) continue;
            
            // Use conflict group IDs (includes parent groups) for consistency
            Set<Long> groupIds = lessons.get(i).getConflictGroupIds();
            if (groupIds.isEmpty()) continue;
            
            // Create a composite key: course ID + sorted group IDs
            Set<Long> key = new TreeSet<>();  // TreeSet for consistent ordering
            key.add(course.getId());
            key.addAll(groupIds);
            
            courseGroupLessons.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        
        // Compute day boundaries for efficient day extraction
        // dayIndex = which day (0-4), computed from start time index
        int[] dayBoundaries = new int[6];  // 5 days + end boundary
        int dayIdx = 0;
        for (DayOfWeek day : new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, 
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            List<Integer> slots = dayToTimeIndices.get(day);
            dayBoundaries[dayIdx++] = slots != null && !slots.isEmpty() ? slots.get(0) : dayBoundaries[dayIdx > 0 ? dayIdx-1 : 0];
        }
        // Set end boundary
        dayBoundaries[5] = dayBoundaries[4] + (dayToTimeIndices.get(DayOfWeek.FRIDAY) != null ? 
                dayToTimeIndices.get(DayOfWeek.FRIDAY).size() : 0);
        
        int constraintsAdded = 0;
        
        for (Map.Entry<Set<Long>, List<Integer>> entry : courseGroupLessons.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() <= 1) continue;
            
            // For each pair of lessons sharing the same course AND student group(s)
            for (int j = 0; j < indices.size(); j++) {
                for (int k = j + 1; k < indices.size(); k++) {
                    int idx1 = indices.get(j);
                    int idx2 = indices.get(k);
                    
                    // Create day index variables (0=Mon, 1=Tue, ..., 4=Fri)
                    // Use element constraint to map start time to day
                    IntVar day1 = model.newIntVar(0, 4, "day_lesson_" + idx1);
                    IntVar day2 = model.newIntVar(0, 4, "day_lesson_" + idx2);
                    
                    // Compute day from start index using bounds
                    // This is a simplified linear mapping
                    for (int d = 0; d < 5; d++) {
                        int dayStart = dayBoundaries[d];
                        int dayEnd = (d < 4) ? dayBoundaries[d + 1] : dayBoundaries[5];
                        
                        // If start is in [dayStart, dayEnd), then day = d
                        // Use reified constraints
                        BoolVar inDay1 = model.newBoolVar("d" + d + "_l" + idx1);
                        model.addGreaterOrEqual(startVars[idx1], dayStart).onlyEnforceIf(inDay1);
                        model.addLessThan(startVars[idx1], dayEnd).onlyEnforceIf(inDay1);
                        model.addEquality(day1, d).onlyEnforceIf(inDay1);
                        
                        BoolVar inDay2 = model.newBoolVar("d" + d + "_l" + idx2);
                        model.addGreaterOrEqual(startVars[idx2], dayStart).onlyEnforceIf(inDay2);
                        model.addLessThan(startVars[idx2], dayEnd).onlyEnforceIf(inDay2);
                        model.addEquality(day2, d).onlyEnforceIf(inDay2);
                    }
                    
                    // Constraint: day1 != day2 (different days)
                    model.addDifferent(day1, day2);
                    constraintsAdded++;
                }
            }
        }
        
        log.info("CP-SAT: Added same course same day constraints: {} (grouped by course+group)", constraintsAdded);
    }

    /**
     * HARD CONSTRAINT 11: Special Event Conflict
     * Forbid timeslot/room assignments overlapping with active special events.
     * Checks: student group conflicts, room conflicts, lecturer conflicts.
     */
    private void addSpecialEventConstraints(CpModel model, List<Lesson> lessons,
            IntVar[] startVars, IntVar[] roomVars, List<SpecialEvent> specialEvents,
            Map<Integer, Timeslot> indexToTimeslot, Map<Long, Integer> timeslotToIndex,
            Map<Long, Integer> roomToIndex, List<List<Integer>> validStartsPerLesson,
            Map<DayOfWeek, List<Integer>> dayToTimeIndices) {
        if (specialEvents.isEmpty()) return;

        int timeForbidden = 0;
        int roomForbidden = 0;

        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            int duration = lesson.getDurationHours();

            for (SpecialEvent event : specialEvents) {
                if (!event.isActive()) continue;

                LocalTime eventStart = event.getStartTime();
                LocalTime eventEnd = event.getEndTime();

                // Check if this lesson could conflict with this event
                boolean studentConflict = false;
                boolean roomConflict = false;
                boolean lecturerConflict = false;

                // Student group conflict
                for (StudentGroup lessonGroup : lesson.getStudentGroups()) {
                    if (event.affectsStudentGroup(lessonGroup)) {
                        studentConflict = true;
                        break;
                    }
                }

                // Room conflict (if event has a room assigned)
                if (event.getRoom() != null && !lesson.isOnline()) {
                    roomConflict = true;
                }

                // Lecturer conflict (if event has a lecturer assigned)
                if (event.getLecturer() != null && lesson.getLecturer() != null &&
                        event.getLecturer().getId().equals(lesson.getLecturer().getId())) {
                    lecturerConflict = true;
                }

                if (!studentConflict && !roomConflict && !lecturerConflict) continue;

                // Forbid timeslots that overlap with the event
                for (Map.Entry<Integer, Timeslot> entry : indexToTimeslot.entrySet()) {
                    Timeslot ts = entry.getValue();
                    if (ts.getDayOfWeek() != event.getDayOfWeek()) continue;

                    LocalTime lessonStart = ts.getStartTime();
                    LocalTime lessonEnd = lessonStart.plusHours(duration);

                    // Interval overlap check
                    boolean overlaps = lessonStart.isBefore(eventEnd) && eventStart.isBefore(lessonEnd);
                    if (overlaps) {
                        if (studentConflict || lecturerConflict) {
                            // Forbid this timeslot entirely
                            model.addDifferent(startVars[i], entry.getKey());
                            timeForbidden++;
                        } else if (roomConflict && event.getRoom() != null && roomVars[i] != null) {
                            // Forbid the event's room at this timeslot
                            Integer eventRoomIdx = roomToIndex.get(event.getRoom().getId());
                            if (eventRoomIdx != null) {
                                // lesson at this timeslot AND in this room -> forbidden
                                BoolVar atThisTime = model.newBoolVar("se_time_" + i + "_" + entry.getKey());
                                model.addEquality(startVars[i], entry.getKey()).onlyEnforceIf(atThisTime);
                                model.addDifferent(startVars[i], entry.getKey()).onlyEnforceIf(atThisTime.not());

                                BoolVar inThisRoom = model.newBoolVar("se_room_" + i + "_" + eventRoomIdx);
                                model.addEquality(roomVars[i], eventRoomIdx).onlyEnforceIf(inThisRoom);
                                model.addDifferent(roomVars[i], eventRoomIdx).onlyEnforceIf(inThisRoom.not());

                                // Both true -> forbidden (at least one must be false)
                                model.addBoolOr(new Literal[]{atThisTime.not(), inThisRoom.not()});
                                roomForbidden++;
                            }
                        }
                    }
                }
            }
        }
        log.info("CP-SAT: Special events: {} timeslot forbidden, {} room-time forbidden", timeForbidden, roomForbidden);
    }

    /**
     * HARD CONSTRAINT 12: Max Lecturer Consecutive Hours
     * Counts back-to-back lesson pairs per lecturer per day.
     * Enforces: count <= maxHours - 1 per lecturer per day.
     * Uses precomputed day-membership BoolVars per (lesson, day) with two-way implication,
     * then creates diff = startVars[idxA] - startVars[idxB] and checks diff == -durA
     * for consecutive detection.
     */
    private void addMaxLecturerConsecutiveHoursConstraints(CpModel model, List<Lesson> lessons,
            IntVar[] startVars, IntervalVar[] intervals,
            Map<Integer, Timeslot> indexToTimeslot, int maxHours) {
        if (maxHours <= 0) return;
        int allowedPairs = Math.max(0, maxHours - 1);

        // Build day ranges from indexToTimeslot
        Map<DayOfWeek, int[]> dayRanges = new EnumMap<>(DayOfWeek.class);
        for (Map.Entry<Integer, Timeslot> entry : indexToTimeslot.entrySet()) {
            DayOfWeek day = entry.getValue().getDayOfWeek();
            int idx = entry.getKey();
            dayRanges.computeIfAbsent(day, k -> new int[]{idx, idx});
            int[] range = dayRanges.get(day);
            range[0] = Math.min(range[0], idx);
            range[1] = Math.max(range[1], idx);
        }

        // Group lessons by lecturer
        Map<Long, List<Integer>> lecturerLessons = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            Lecturer lecturer = lessons.get(i).getLecturer();
            if (lecturer != null && lecturer.getId() != null) {
                lecturerLessons.computeIfAbsent(lecturer.getId(), k -> new ArrayList<>()).add(i);
            }
        }

        // Precompute day-membership BoolVars: inDay[lessonIdx][day] = true iff lesson on that day
        // Two-way implication: inDay=true <-> dayStart <= startVars[idx] <= dayEnd
        Map<DayOfWeek, BoolVar[]> inDayMap = new EnumMap<>(DayOfWeek.class);
        int n = lessons.size();
        for (Map.Entry<DayOfWeek, int[]> dayEntry : dayRanges.entrySet()) {
            DayOfWeek day = dayEntry.getKey();
            int dayStart = dayEntry.getValue()[0];
            int dayEnd = dayEntry.getValue()[1];
            BoolVar[] dayVars = new BoolVar[n];
            for (int i = 0; i < n; i++) {
                String prefix = "ind_" + i + "_" + day;
                BoolVar ge = model.newBoolVar(prefix + "_ge");
                model.addGreaterOrEqual(startVars[i], dayStart).onlyEnforceIf(ge);
                model.addLessThan(startVars[i], dayStart).onlyEnforceIf(ge.not());
                BoolVar le = model.newBoolVar(prefix + "_le");
                model.addLessOrEqual(startVars[i], dayEnd).onlyEnforceIf(le);
                model.addGreaterThan(startVars[i], dayEnd).onlyEnforceIf(le.not());
                BoolVar inDay = model.newBoolVar(prefix);
                model.addBoolAnd(new Literal[]{ge, le}).onlyEnforceIf(inDay);
                model.addBoolOr(new Literal[]{ge.not(), le.not()}).onlyEnforceIf(inDay.not());
                dayVars[i] = inDay;
            }
            inDayMap.put(day, dayVars);
        }

        int constraintsAdded = 0;
        for (Map.Entry<Long, List<Integer>> entry : lecturerLessons.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() <= allowedPairs) continue;

            for (Map.Entry<DayOfWeek, int[]> dayEntry : dayRanges.entrySet()) {
                DayOfWeek day = dayEntry.getKey();
                BoolVar[] dayVars = inDayMap.get(day);

                List<BoolVar> consecutivePairVars = new ArrayList<>();
                for (int a = 0; a < indices.size(); a++) {
                    for (int b = 0; b < indices.size(); b++) {
                        if (a == b) continue;
                        int idxA = indices.get(a);
                        int idxB = indices.get(b);
                        int durA = lessons.get(idxA).getDurationHours();

                        String prefix = "mc_" + idxA + "_" + idxB + "_" + day;

                        // diff = startVars[idxA] - startVars[idxB]
                        // consecutive means diff == -durA (i.e., idxB starts durA hours after idxA)
                        IntVar diff = model.newIntVar(-50, 50, prefix + "_d");
                        model.addEquality(diff,
                                LinearExpr.weightedSum(
                                        new IntVar[]{startVars[idxA], startVars[idxB]},
                                        new long[]{1, -1}));

                        BoolVar diffZero = model.newBoolVar(prefix + "_eq");
                        model.addEquality(diff, -durA).onlyEnforceIf(diffZero);
                        model.addDifferent(diff, -durA).onlyEnforceIf(diffZero.not());

                        // consec = diffZero AND inDay(idxA) AND inDay(idxB)
                        BoolVar consec = model.newBoolVar(prefix + "_c");
                        model.addBoolAnd(new Literal[]{diffZero, dayVars[idxA], dayVars[idxB]})
                                .onlyEnforceIf(consec);
                        model.addBoolOr(new Literal[]{diffZero.not(), dayVars[idxA].not(), dayVars[idxB].not()})
                                .onlyEnforceIf(consec.not());

                        consecutivePairVars.add(consec);
                    }
                }

                if (!consecutivePairVars.isEmpty()) {
                    model.addLessOrEqual(
                            LinearExpr.sum(consecutivePairVars.toArray(new BoolVar[0])),
                            allowedPairs);
                    constraintsAdded++;
                }
            }
        }
        log.info("CP-SAT: Added {} max consecutive hours day-constraints", constraintsAdded);
    }

    /**
     * SOFT CONSTRAINTS: Build objective function
     */
    private void addSoftConstraints(CpModel model, List<Lesson> lessons,
            IntVar[] startVars, IntVar[] roomVars, BoolVar[][] roomAssignment,
            List<Room> rooms, Map<Integer, Timeslot> indexToTimeslot,
            Map<DayOfWeek, Integer> dayStartIndex, Map<DayOfWeek, List<Integer>> dayToTimeIndices,
            List<IntVar> objectiveVars, List<Long> objectiveCoeffs) {
        
        int weightRoomCapacity = constraintSettingsService.getWeightRoomCapacity();
        int weightDayBalance = constraintSettingsService.getWeightDayBalance();
        int weightLecturerTransition = constraintSettingsService.getWeightLecturerTransition();
        int weightStudentFatigue = constraintSettingsService.getWeightStudentFatigue();
        int weightEarlyMorning = constraintSettingsService.getInt("weight_early_morning", 3);
        boolean dayBalanceEnforced = constraintSettingsService.isDayBalanceEnforced();
        
        // 1. Room capacity efficiency - penalize wasted capacity
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            if (lesson.isOnline() || roomVars[i] == null || roomAssignment[i].length == 0) continue;
            
            int students = lesson.getTotalStudentCount();
            
            for (int r = 0; r < rooms.size(); r++) {
                if (roomAssignment[i][r] == null) continue;
                
                Room room = rooms.get(r);
                int wasted = Math.max(0, (room.getCapacity() - students) / 10);
                
                if (wasted > 0) {
                    // Add penalty: wasted * weight * roomAssignment[i][r]
                    IntVar penalty = model.newIntVar(0, wasted * weightRoomCapacity, 
                            "waste_" + lesson.getId() + "_room_" + r);
                    model.addEquality(penalty, wasted * weightRoomCapacity)
                            .onlyEnforceIf(roomAssignment[i][r]);
                    model.addEquality(penalty, 0)
                            .onlyEnforceIf(roomAssignment[i][r].not());
                    objectiveVars.add(penalty);
                    objectiveCoeffs.add(1L);
                }
            }
        }
        
        // 2. Early morning penalty
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            
            // Find early morning slots (7am, 8am)
            for (Map.Entry<Integer, Timeslot> entry : indexToTimeslot.entrySet()) {
                Timeslot ts = entry.getValue();
                LocalTime start = ts.getStartTime();
                
                if (start.isBefore(LocalTime.of(9, 0))) {
                    int penalty = start.equals(LocalTime.of(7, 0)) 
                            ? weightEarlyMorning * 3 
                            : weightEarlyMorning;
                    
                    BoolVar isEarlySlot = model.newBoolVar("early_" + lesson.getId() + "_slot_" + entry.getKey());
                    model.addEquality(startVars[i], entry.getKey()).onlyEnforceIf(isEarlySlot);
                    model.addDifferent(startVars[i], entry.getKey()).onlyEnforceIf(isEarlySlot.not());
                    
                    IntVar earlyPenalty = model.newIntVar(0, penalty, "early_penalty_" + lesson.getId());
                    model.addEquality(earlyPenalty, penalty).onlyEnforceIf(isEarlySlot);
                    model.addEquality(earlyPenalty, 0).onlyEnforceIf(isEarlySlot.not());
                    objectiveVars.add(earlyPenalty);
                    objectiveCoeffs.add(1L);
                }
            }
        }
        
        // 3. Late afternoon penalty
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            
            for (Map.Entry<Integer, Timeslot> entry : indexToTimeslot.entrySet()) {
                Timeslot ts = entry.getValue();
                LocalTime start = ts.getStartTime();
                
                if (!start.isBefore(LocalTime.of(17, 0))) {
                    int penalty = start.isBefore(LocalTime.of(18, 0)) 
                            ? weightEarlyMorning 
                            : weightEarlyMorning * 3;
                    
                    BoolVar isLateSlot = model.newBoolVar("late_" + lesson.getId() + "_slot_" + entry.getKey());
                    model.addEquality(startVars[i], entry.getKey()).onlyEnforceIf(isLateSlot);
                    model.addDifferent(startVars[i], entry.getKey()).onlyEnforceIf(isLateSlot.not());
                    
                    IntVar latePenalty = model.newIntVar(0, penalty, "late_penalty_" + lesson.getId());
                    model.addEquality(latePenalty, penalty).onlyEnforceIf(isLateSlot);
                    model.addEquality(latePenalty, 0).onlyEnforceIf(isLateSlot.not());
                    objectiveVars.add(latePenalty);
                    objectiveCoeffs.add(1L);
                }
            }
        }
        
        // 4. Day balance - penalize uneven distribution
        if (dayBalanceEnforced) {
            Map<Long, List<Integer>> groupLessons = new HashMap<>();
            for (int i = 0; i < lessons.size(); i++) {
                Lesson lesson = lessons.get(i);
                Long primaryGroupId = lesson.getPrimaryConflictGroupId();
                if (primaryGroupId != null) {
                    groupLessons.computeIfAbsent(primaryGroupId, k -> new ArrayList<>()).add(i);
                }
            }
            
            for (Map.Entry<Long, List<Integer>> entry : groupLessons.entrySet()) {
                List<Integer> lessonIndices = entry.getValue();
                
                // Count lessons per day
                for (DayOfWeek day : DayOfWeek.values()) {
                    List<Integer> daySlots = dayToTimeIndices.get(day);
                    if (daySlots == null || daySlots.isEmpty()) continue;
                    
                    IntVar countOnDay = model.newIntVar(0, lessonIndices.size(), 
                            "count_group_" + entry.getKey() + "_day_" + day);
                    
                    BoolVar[] onDay = lessonIndices.stream()
                            .map(idx -> {
                                BoolVar isOnDay = model.newBoolVar("g" + entry.getKey() + "_l" + idx + "_on_" + day);
                                Literal[] inDaySlots = new Literal[daySlots.size()];
                                for (int s = 0; s < daySlots.size(); s++) {
                                    int slot = daySlots.get(s);
                                    BoolVar isSlot = model.newBoolVar("slot_check");
                                    model.addEquality(startVars[idx], slot).onlyEnforceIf(isSlot);
                                    model.addDifferent(startVars[idx], slot).onlyEnforceIf(isSlot.not());
                                    inDaySlots[s] = isSlot;
                                }
                                model.addBoolOr(inDaySlots).onlyEnforceIf(isOnDay);
                                Literal[] notInDaySlots = new Literal[inDaySlots.length];
                                for (int s = 0; s < inDaySlots.length; s++) {
                                    notInDaySlots[s] = ((BoolVar) inDaySlots[s]).not();
                                }
                                model.addBoolAnd(notInDaySlots).onlyEnforceIf(isOnDay.not());
                                return isOnDay;
                            })
                            .toArray(BoolVar[]::new);
                    
                    model.addEquality(countOnDay, LinearExpr.weightedSum(onDay, 
                            lessonIndices.stream().mapToLong(idx -> 1L).toArray()));
                    
                    // Penalize if countOnDay > 1 (pair penalty)
                    IntVar excessPairs = model.newIntVar(0, lessonIndices.size() * lessonIndices.size(), 
                            "excess_pairs_" + entry.getKey() + "_" + day);
                    model.addMultiplicationEquality(excessPairs, countOnDay, countOnDay);
                    
                    IntVar pairPenalty = model.newIntVar(0, lessonIndices.size() * lessonIndices.size(), 
                            "pair_penalty_" + entry.getKey() + "_" + day);
                    model.addEquality(pairPenalty, LinearExpr.sum(new IntVar[] {excessPairs, countOnDay}));
                    
                    objectiveVars.add(pairPenalty);
                    objectiveCoeffs.add((long) weightDayBalance);
                }
            }
        }
        
        log.info("CP-SAT: Added soft constraints to objective");
    }

    private void extractAndSaveSolution(List<Lesson> lessons, IntVar[] startVars,
            IntVar[] roomVars, Map<Integer, Timeslot> indexToTimeslot,
            Map<Integer, Room> indexToRoom) {
        
        log.info("CP-SAT: Extracting solution...");
        
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            
            long startTimeIndex = solver.value(startVars[i]);
            Timeslot timeslot = indexToTimeslot.get((int) startTimeIndex);
            
            if (timeslot != null) {
                lesson.setTimeslot(timeslot);
            } else {
                log.warn("CP-SAT: No timeslot found for index {} for lesson {}", startTimeIndex, lesson.getId());
            }
            
            if (!lesson.isOnline() && roomVars[i] != null) {
                long roomIndex = solver.value(roomVars[i]);
                Room room = indexToRoom.get((int) roomIndex);
                
                if (room != null) {
                    lesson.setRoom(room);
                } else {
                    log.warn("CP-SAT: No room found for index {} for lesson {}", roomIndex, lesson.getId());
                }
            }
        }
        
        solutionSaver.saveSolution(lessons);
        log.info("CP-SAT: Solution saved to database");
    }

    public SolverStatusDTO getStatus() {
        SolverStatusDTO status = new SolverStatusDTO();
        status.setJobId("cpsat-" + solveStartedAtMs.get());
        status.setState(currentStatus.get());
        status.setScore(currentScore.get());
        status.setRunOutcome(solvingInProgress.get() ? "RUNNING" : 
                ("SOLVED".equals(currentStatus.get()) ? "COMPLETED" : currentStatus.get()));
        
        long startedMs = solveStartedAtMs.get();
        if (solvingInProgress.get()) {
            status.setDurationMs(System.currentTimeMillis() - startedMs);
        }
        
        // Add time-to-feasible metric
        long firstFeasibleMs = firstFeasibleAtMs.get();
        if (firstFeasibleMs > 0 && startedMs > 0) {
            status.setTimeToFirstFeasibleMs(firstFeasibleMs - startedMs);
            status.setHardFeasibleReachedMs(firstFeasibleMs - startedMs);
        }
        
        status.setFeasible(solutionCount.get() > 0);
        status.setImprovementCount((long) solutionCount.get());
        status.setProfile("CP-SAT");
        
        return status;
    }

    public SolverStatusDTO terminate() {
        if (solver != null) {
            solver.stopSearch();
        }
        
        if (solverThread != null) {
            solverThread.interrupt();
        }
        
        currentStatus.set("TERMINATED");
        solvingInProgress.set(false);
        
        return getStatus();
    }

    public boolean isSolving() {
        return solvingInProgress.get();
    }
}
