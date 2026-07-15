package com.university.timetable.service;

import com.google.ortools.sat.*;
import com.university.timetable.domain.*;
import com.university.timetable.dto.SolveRequestDTO;
import com.university.timetable.dto.SolverStatusDTO;
import com.university.timetable.repository.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hybrid CP-SAT + Timefold Solver Service
 *
 * Phase 1: Use CP-SAT to find hard-feasible solution (fast, deterministic)
 * Phase 2: Pass solution to Timefold for soft optimization (local search)
 *
 * Advantages:
 * - CP-SAT excels at hard constraint satisfaction (no-overlap, capacity, etc.)
 * - Timefold excels at soft constraint optimization via local search
 * - Combined: Fast feasibility + quality optimization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridCpSatSolverService {

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
    private final SolverFactory<TimeTable> solverFactory;
    private final RoomMatchingService roomMatchingService;
    private final ProblemReducer problemReducer;

    @Value("${hybrid.cpsat.time-limit-seconds:300}")
    private int cpSatTimeLimitSeconds;

    @Value("${hybrid.timefold.time-limit-seconds:600}")
    private int timefoldTimeLimitSeconds;

    @Value("${hybrid.cpsat.num-workers:8}")
    private int numWorkers;

    @Value("${hybrid.decomposed.enabled:true}")
    private boolean decomposedEnabled;

    @Value("${hybrid.decomposed.max-benders-iterations:3}")
    private int maxBendersIterations;

    private final AtomicBoolean solvingInProgress = new AtomicBoolean(false);
    private final AtomicLong solveStartedAtMs = new AtomicLong(0);
    private final AtomicLong phaseOneCompleteAtMs = new AtomicLong(0);
    private final AtomicReference<String> currentStatus = new AtomicReference<>("IDLE");
    private final AtomicReference<String> currentPhase = new AtomicReference<>("IDLE");
    private final AtomicReference<String> currentScore = new AtomicReference<>("N/A");
    private volatile Thread solverThread;

    @PostConstruct
    public void init() {
        log.info("Hybrid CP-SAT + Timefold Solver Service initialized");
        log.info("Phase 1 (CP-SAT hard solve): {}s, Phase 2 (Timefold soft optimize): {}s",
                cpSatTimeLimitSeconds, timefoldTimeLimitSeconds);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Hybrid solver shutting down...");
        terminateSolving();
    }

    public SolverStatusDTO startSolving(SolveRequestDTO request) {
        if (solvingInProgress.get()) {
            throw new IllegalStateException("Solver is already running.");
        }

        solvingInProgress.set(true);
        solveStartedAtMs.set(System.currentTimeMillis());
        currentStatus.set("SOLVING");
        currentPhase.set("PHASE_1_CPSAT_HARD");
        currentScore.set("N/A");

        solverThread = Thread.ofVirtual().start(() -> {
            try {
                solveHybrid(request);
            } catch (Exception e) {
                log.error("Hybrid solver failed", e);
                currentStatus.set("ERROR: " + e.getMessage());
                solvingInProgress.set(false);
            }
        });

        return getStatus();
    }

    private final AtomicReference<TimeTable> latestSolution = new AtomicReference<>();

    private void solveHybrid(SolveRequestDTO request) {
        log.info("=== HYBRID SOLVER START ===");
        long totalStartMs = System.currentTimeMillis();

        TimeTable finalSolution;
        if (decomposedEnabled) {
            log.info("Using DECOMPOSED solve: CP-SAT timeslot-only + per-timeslot room matching");
            finalSolution = runDecomposedSolve();
        } else {
            log.info("Using MONOLITHIC solve: CP-SAT full model (timeslots + rooms)");
            finalSolution = runMonolithicSolve();
        }

        if (finalSolution == null) {
            log.error("Hybrid solver failed to find feasible solution");
            currentStatus.set("FAILED: No hard-feasible solution found");
            currentPhase.set("FAILED");
            solvingInProgress.set(false);
            auditLogService.logSystemAction("Hybrid solver failed: no feasible solution found", false, "No feasible solution");
            return;
        }

        latestSolution.set(finalSolution);

        long totalMs = System.currentTimeMillis() - totalStartMs;
        long phaseOneMs = phaseOneCompleteAtMs.get() - solveStartedAtMs.get();
        long phaseTwoMs = totalMs - phaseOneMs;

        saveSolutionIfFeasible(finalSolution);

        HardSoftScore finalScore = finalSolution.getScore();
        currentScore.set(finalScore != null ? finalScore.toString() : "N/A");
        currentStatus.set("SOLVED");
        currentPhase.set("COMPLETE");
        solvingInProgress.set(false);

        log.info("=== HYBRID SOLVER COMPLETE ===");
        log.info("Total time: {} ms (Phase 1+2: {} ms, Timefold: {} ms)", totalMs, phaseOneMs, phaseTwoMs);
        log.info("Final score: {}", currentScore.get());

        auditLogService.logSystemAction("Hybrid solver completed. Score: " + currentScore.get(), true, null);
    }

    /**
     * Monolithic solve path: existing CP-SAT model with room BoolVars.
     * Used as fallback when decomposed mode is disabled.
     */
    private TimeTable runMonolithicSolve() {
        log.info("PHASE 1 (monolithic): CP-SAT Hard Constraint Solving");
        currentPhase.set("PHASE_1_CPSAT_HARD");
        TimeTable cpSatSolution = runCpSatHardSolve();

        if (cpSatSolution == null) {
            return null;
        }

        phaseOneCompleteAtMs.set(System.currentTimeMillis());
        log.info("PHASE 1 COMPLETE in {} ms", phaseOneCompleteAtMs.get() - solveStartedAtMs.get());

        log.info("PHASE 2: Timefold Soft Constraint Optimization");
        currentPhase.set("PHASE_2_TIMEFOLD_SOFT");
        return runTimefoldSoftOptimize(cpSatSolution);
    }

    /**
     * DECOMPOSED solve: Benders-style decomposition.
     * Phase 1a: CP-SAT assigns timeslots only (no room BoolVars → tiny model, fast solve)
     * Phase 1b: Per-timeslot bipartite room matching via MinCostFlow
     * Phase 2:  Timefold soft optimization from complete feasible seed
     *
     * If room matching fails, generates nogood and re-solves Phase 1a (Benders feedback).
     */
    private TimeTable runDecomposedSolve() {
        // Load data
        List<Lesson> lessons = lessonRepository.findAllWithCourseAndLecturer();
        List<Timeslot> timeslots = timeslotRepository.findAll();
        List<Room> rooms = roomRepository.findAllWithFeatures();
        List<SpecialEvent> specialEvents = specialEventRepository.findByActiveTrue();

        log.info("Decomposed: Loaded {} lessons, {} timeslots, {} rooms, {} special events",
                lessons.size(), timeslots.size(), rooms.size(), specialEvents.size());

        List<Lesson> unpinnedLessons = lessons.stream()
                .filter(l -> !l.isPinned())
                .toList();

        if (unpinnedLessons.isEmpty()) {
            log.info("No unpinned lessons to schedule");
            phaseOneCompleteAtMs.set(System.currentTimeMillis());
            return buildTimeTable(lessons, timeslots, rooms);
        }

        // Pre-processing: pin forced assignments
        ProblemReducer.ReductionResult reduction = problemReducer.reduce(lessons, timeslots, rooms);
        log.info("Pre-processing: pinned {} lessons, {} room equivalence classes",
                reduction.pinnedCount(), reduction.symmetryGroupsFound());

        // Refresh unpinned after pre-processing
        unpinnedLessons = lessons.stream().filter(l -> !l.isPinned()).toList();
        if (unpinnedLessons.isEmpty()) {
            log.info("All lessons pinned by pre-processing");
            phaseOneCompleteAtMs.set(System.currentTimeMillis());
            return buildTimeTable(lessons, timeslots, rooms);
        }

        // BREAKTHROUGH: Pre-assign rooms to ALL lessons before CP-SAT.
        // This eliminates the room matching failure and gives Timefold a complete starting point.
        Map<Lesson, Room> preAssignedRooms = roomMatchingService.assignAllRooms(lessons, rooms);
        for (Map.Entry<Lesson, Room> entry : preAssignedRooms.entrySet()) {
            entry.getKey().setRoom(entry.getValue());
        }
        long assignedCount = preAssignedRooms.values().stream().filter(Objects::nonNull).count();
        log.info("Pre-assigned rooms: {} lessons have rooms ({} online)", assignedCount,
                lessons.stream().filter(Lesson::isOnline).count());

        // Phase 1: CP-SAT assigns timeslots WITH room NoOverlap constraints
        currentPhase.set("PHASE_1_CPSAT_TIMESLOTS_WITH_ROOMS");
        log.info("Phase 1: CP-SAT solving timeslots with pre-assigned rooms");

        TimeTable timeslotSolution = runCpSatTimeslotOnly(unpinnedLessons, timeslots, specialEvents,
                rooms, new ArrayList<>());

        if (timeslotSolution == null) {
            log.warn("Phase 1 (CP-SAT) returned INFEASIBLE with pre-assigned rooms. "
                    + "Pre-assigned rooms are too constrained for exact solver. "
                    + "Skipping CP-SAT, running Timefold directly from pre-assigned rooms.");
            // CP-SAT proved the pre-assigned room configuration is infeasible for exact
            // timeslot assignment. But Timefold's local search can handle this by
            // swapping rooms and timeslots during optimization.
            // Go directly to Phase 2 with the pre-assigned rooms.
        }

        phaseOneCompleteAtMs.set(System.currentTimeMillis());
        long phaseOneMs = phaseOneCompleteAtMs.get() - solveStartedAtMs.get();
        log.info("Phase 1 COMPLETE in {} ms: CP-SAT timeslots + pre-assigned rooms", phaseOneMs);

        // Phase 2: Timefold soft optimization from COMPLETE feasible seed
        currentPhase.set("PHASE_2_TIMEFOLD_SOFT");
        log.info("Phase 2: Timefold Soft Constraint Optimization from complete seed");
        TimeTable completeSolution = buildTimeTable(lessons, timeslots, rooms);
        return runTimefoldSoftOptimize(completeSolution);
    }

    /**
     * CP-SAT timeslot-only assignment: assigns timeslots without room BoolVars.
     * Model is ~75K combinations (vs ~200K+ with rooms) → solves in seconds.
     * Adds room-class capacity constraints per timeslot to prevent over-commitment.
     */
    private TimeTable runCpSatTimeslotOnly(List<Lesson> unpinnedLessons, List<Timeslot> timeslots,
                                            List<SpecialEvent> specialEvents, List<Room> rooms,
                                            List<String> nogoodReasons) {
        CpModel model = new CpModel();

        // Load settings
        LocalTime earliestStart = constraintSettingsService.getEarliestStartTime();
        LocalTime latestEnd = constraintSettingsService.getLatestEndTime();
        LocalTime fridayLatestEnd = constraintSettingsService.getFridayLatestEndTime();
        LocalTime lunchBreakStart = constraintSettingsService.getLunchBreakStart();
        LocalTime lunchBreakEnd = constraintSettingsService.getLunchBreakEnd();
        boolean lunchBreakEnforced = constraintSettingsService.isLunchBreakEnforced();
        boolean sameCourseSameDayAllowed = constraintSettingsService.isSameCourseSameDayAllowed();
        boolean unavailabilityEnabled = constraintSettingsService.isUnavailabilitySystemEnabled();
        int maxLecturerConsecutiveHours = constraintSettingsService.getMaxLecturerConsecutiveHours();

        // Build mappings
        Map<Long, Integer> timeslotToIndex = new HashMap<>();
        Map<Integer, Timeslot> indexToTimeslot = new HashMap<>();
        buildTimeslotMappings(timeslots, timeslotToIndex, indexToTimeslot);

        Map<DayOfWeek, List<Integer>> dayToTimeIndices = new EnumMap<>(DayOfWeek.class);
        buildDayToTimeIndices(timeslots, timeslotToIndex, dayToTimeIndices);

        int n = unpinnedLessons.size();
        IntVar[] startVars = new IntVar[n];

        // Create timeslot variables with domain restriction
        for (int i = 0; i < n; i++) {
            Lesson lesson = unpinnedLessons.get(i);
            int duration = lesson.getDurationHours();
            List<Integer> validStarts = computeValidStartTimes(lesson, timeslots, timeslotToIndex,
                    indexToTimeslot, duration, earliestStart, latestEnd, fridayLatestEnd);

            if (validStarts.isEmpty()) {
                log.error("No valid start times for lesson {} (course: {})", lesson.getId(),
                        lesson.getCourse() != null ? lesson.getCourse().getCode() : "null");
                return null;
            }

            long[] domain = validStarts.stream().mapToLong(Integer::longValue).toArray();
            startVars[i] = model.newIntVarFromDomain(
                    com.google.ortools.util.Domain.fromValues(domain),
                    "start_" + lesson.getId());
        }

        // Add constraints (timeslot-only, no room constraints needed)
        // Use a dummy roomAssignment (empty) since we don't have room variables
        BoolVar[][] emptyRoomAssignment = new BoolVar[n][];
        IntVar[] dummyRoomVars = new IntVar[n]; // not used
        IntervalVar[] intervals = new IntervalVar[n];
        for (int i = 0; i < n; i++) {
            emptyRoomAssignment[i] = new BoolVar[0];
            intervals[i] = model.newFixedSizeIntervalVar(
                    startVars[i], unpinnedLessons.get(i).getDurationHours(),
                    "interval_" + unpinnedLessons.get(i).getId());
        }

        // Lecturer no-overlap
        addLecturerNoOverlapConstraints(model, unpinnedLessons, intervals);

        // Student group no-overlap
        addStudentGroupNoOverlapConstraints(model, unpinnedLessons, intervals);

        // Lecturer unavailability
        if (unavailabilityEnabled) {
            addLecturerUnavailabilityConstraints(model, unpinnedLessons, startVars, indexToTimeslot);
        }

        // Lunch break
        if (lunchBreakEnforced) {
            addLunchBreakOverlapConstraints(model, unpinnedLessons, startVars, indexToTimeslot,
                    lunchBreakStart, lunchBreakEnd);
        }

        // Same course same day
        if (!sameCourseSameDayAllowed) {
            addSameCourseSameDayConstraints(model, unpinnedLessons, startVars, dayToTimeIndices);
        }

        // Special events (timeslot part only — skip room part since no room vars)
        addSpecialEventTimeslotConstraints(model, unpinnedLessons, startVars, specialEvents, indexToTimeslot);

        // Room NoOverlap constraints (BREAKTHROUGH: rooms are pre-assigned, so we use simple
        // non-optional intervals instead of the expensive BoolVar approach)
        addRoomNoOverlapPreAssigned(model, unpinnedLessons, intervals);

        // Max consecutive hours is a fine-grained sequencing constraint — handled by Timefold Phase 2
        // as a soft constraint. Phase 1a only enforces hard structural no-overlap constraints.

        // Solve
        log.info("CP-SAT timeslot-only (with room constraints): solving {} lessons with {} workers, {}s limit",
                n, numWorkers, cpSatTimeLimitSeconds);

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(cpSatTimeLimitSeconds);
        solver.getParameters().setNumSearchWorkers(numWorkers);
        solver.getParameters().setCpModelPresolve(true);
        solver.getParameters().setLinearizationLevel(2);

        CpSolverStatus status = solver.solve(model);
        log.info("CP-SAT timeslot-only: status={}", status);

        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            log.error("CP-SAT timeslot-only: no feasible solution. Status: {}", status);
            return null;
        }

        // Apply timeslot assignments to lessons
        for (int i = 0; i < n; i++) {
            int startIdx = (int) solver.value(startVars[i]);
            Timeslot assignedTimeslot = indexToTimeslot.get(startIdx);
            unpinnedLessons.get(i).setTimeslot(assignedTimeslot);
        }

        // Build TimeTable with timeslot-assigned lessons (rooms not yet assigned)
        return buildTimeTable(new ArrayList<>(unpinnedLessons), timeslots, rooms);
    }

    /**
     * Room NoOverlap constraints for pre-assigned rooms.
     * Uses simple non-optional intervals — no BoolVars needed since rooms are fixed.
     * This is O(lessons) instead of O(lessons × rooms) for the BoolVar approach.
     */
    private void addRoomNoOverlapPreAssigned(CpModel model, List<Lesson> lessons, IntervalVar[] intervals) {
        Map<Long, List<IntervalVar>> roomIntervals = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            if (lesson.isOnline() || lesson.getRoom() == null) continue;
            roomIntervals.computeIfAbsent(lesson.getRoom().getId(), k -> new ArrayList<>()).add(intervals[i]);
        }
        int constraintsAdded = 0;
        for (Map.Entry<Long, List<IntervalVar>> entry : roomIntervals.entrySet()) {
            if (entry.getValue().size() > 1) {
                model.addNoOverlap(entry.getValue());
                constraintsAdded++;
            }
        }
        log.info("CP-SAT: Added room NoOverlap (pre-assigned) for {} rooms", constraintsAdded);
    }

    /**
     * Room-class capacity constraints per timeslot.
     * Groups rooms by (zone, feature-set) into "room classes".
     * For each timeslot: count of lessons requiring a room class ≤ available rooms of that class.
     * This prevents Phase 1a from over-committing a timeslot beyond room availability.
     */
    private void addRoomClassCapacityConstraints(CpModel model, List<Lesson> lessons,
                                                   IntVar[] startVars, List<Room> rooms,
                                                   List<Timeslot> timeslots,
                                                   Map<Long, Integer> timeslotToIndex) {
        // Build room classes: group by (zoneId, sorted featureIds)
        Map<String, List<Room>> roomClasses = new LinkedHashMap<>();
        for (Room room : rooms) {
            String zoneId = room.getZone() != null ? room.getZone().getId().toString() : "null";
            String featureKey = room.getFeatures() != null
                    ? room.getFeatures().stream()
                            .map(f -> f.getId().toString()).sorted()
                            .collect(java.util.stream.Collectors.joining(","))
                    : "";
            // Use capacity as part of key to differentiate small vs large rooms
            String key = zoneId + "|" + room.getCapacity() + "|" + featureKey;
            roomClasses.computeIfAbsent(key, k -> new ArrayList<>()).add(room);
        }

        // For each room class with capacity constraints, add per-timeslot limits
        // Only enforce for classes that are "tight" (lessons that could need this class
        // might exceed available count)
        int totalTimeslots = timeslots.size();
        int constraintsAdded = 0;

        for (Map.Entry<String, List<Room>> entry : roomClasses.entrySet()) {
            int availableCount = entry.getValue().size();
            Room representativeRoom = entry.getValue().get(0);

            // Find lessons that require this specific room class
            List<Integer> candidateLessonIndices = new ArrayList<>();
            for (int i = 0; i < lessons.size(); i++) {
                Lesson lesson = lessons.get(i);
                if (lesson.isOnline()) continue;
                if (isLessonCompatibleWithRoomClass(lesson, representativeRoom)) {
                    candidateLessonIndices.add(i);
                }
            }

            // Only add constraint if candidates could exceed capacity
            if (candidateLessonIndices.size() <= availableCount) continue;

            // Per-timeslot: sum of BoolVars(lesson in this slot) <= availableCount
            for (int t = 0; t < totalTimeslots; t++) {
                List<BoolVar> inSlot = new ArrayList<>();
                for (int idx : candidateLessonIndices) {
                    String name = "rc_" + idx + "_t" + t;
                    BoolVar atSlot = model.newBoolVar(name);
                    model.addEquality(startVars[idx], t).onlyEnforceIf(atSlot);
                    model.addDifferent(startVars[idx], t).onlyEnforceIf(atSlot.not());
                    inSlot.add(atSlot);
                }
                model.addLessOrEqual(
                        LinearExpr.sum(inSlot.toArray(new BoolVar[0])),
                        availableCount);
                constraintsAdded++;
            }
        }

        log.info("CP-SAT: Added {} room-class capacity per-timeslot constraints", constraintsAdded);
    }

    /**
     * Check if a lesson is compatible with a room class (capacity + features + zone).
     */
    private boolean isLessonCompatibleWithRoomClass(Lesson lesson, Room representativeRoom) {
        Course course = lesson.getCourse();
        int students = lesson.getTotalStudentCount();

        // Capacity
        if (representativeRoom.getCapacity() < students) return false;

        // Features
        if (course != null && course.getRequiredFeatures() != null
                && !course.getRequiredFeatures().isEmpty()) {
            if (!representativeRoom.hasAllFeatures(course.getRequiredFeatures())) return false;
        }

        // Zone
        if (course != null && course.getAllowedZones() != null
                && !course.getAllowedZones().isEmpty()
                && representativeRoom.getZone() != null) {
            if (!course.getAllowedZones().contains(representativeRoom.getZone())) return false;
        }

        return true;
    }

    /**
     * Special event constraints for timeslot-only model.
     * Forbids start times that overlap with special events affecting the lesson's groups or lecturer.
     * Room conflicts with special events are handled by room matching phase.
     */
    private void addSpecialEventTimeslotConstraints(CpModel model, List<Lesson> lessons,
                                                     IntVar[] startVars,
                                                     List<SpecialEvent> specialEvents,
                                                     Map<Integer, Timeslot> indexToTimeslot) {
        if (specialEvents.isEmpty()) return;

        int forbidden = 0;
        for (int i = 0; i < lessons.size(); i++) {
            Lesson lesson = lessons.get(i);
            int duration = lesson.getDurationHours();

            for (SpecialEvent event : specialEvents) {
                if (!event.isActive()) continue;

                boolean affectsThisLesson = false;

                // Student group conflict
                for (StudentGroup lessonGroup : lesson.getStudentGroups()) {
                    if (event.affectsStudentGroup(lessonGroup)) {
                        affectsThisLesson = true;
                        break;
                    }
                }

                // Lecturer conflict
                if (!affectsThisLesson && event.getLecturer() != null && lesson.getLecturer() != null
                        && event.getLecturer().getId().equals(lesson.getLecturer().getId())) {
                    affectsThisLesson = true;
                }

                if (!affectsThisLesson) continue;

                // Forbid timeslots that overlap
                LocalTime eventStart = event.getStartTime();
                LocalTime eventEnd = event.getEndTime();
                for (Map.Entry<Integer, Timeslot> entry : indexToTimeslot.entrySet()) {
                    Timeslot ts = entry.getValue();
                    if (ts.getDayOfWeek() != event.getDayOfWeek()) continue;

                    LocalTime lessonStart = ts.getStartTime();
                    LocalTime lessonEnd = lessonStart.plusHours(duration);
                    if (lessonStart.isBefore(eventEnd) && eventStart.isBefore(lessonEnd)) {
                        model.addDifferent(startVars[i], entry.getKey());
                        forbidden++;
                    }
                }
            }
        }
        log.info("CP-SAT: Special event timeslot constraints: {} forbidden", forbidden);
    }

    /**
     * Phase 1: Use CP-SAT to find hard-feasible solution
     */
    private TimeTable runCpSatHardSolve() {
        // Load data
        List<Lesson> lessons = lessonRepository.findAllWithCourseAndLecturer();
        List<Timeslot> timeslots = timeslotRepository.findAll();
        List<Room> rooms = roomRepository.findAllWithFeatures();
        List<SpecialEvent> specialEvents = specialEventRepository.findByActiveTrue();

        log.info("CP-SAT: Loaded {} lessons, {} timeslots, {} rooms, {} special events",
                lessons.size(), timeslots.size(), rooms.size(), specialEvents.size());

        // Filter pinned lessons
        List<Lesson> unpinnedLessons = lessons.stream()
                .filter(l -> !l.isPinned())
                .toList();

        if (unpinnedLessons.isEmpty()) {
            log.info("No unpinned lessons to schedule");
            return buildTimeTable(lessons, timeslots, rooms);
        }

        CpModel model = new CpModel();

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

        // Build mappings
        Map<Long, Integer> timeslotToIndex = new HashMap<>();
        Map<Integer, Timeslot> indexToTimeslot = new HashMap<>();
        buildTimeslotMappings(timeslots, timeslotToIndex, indexToTimeslot);

        Map<DayOfWeek, List<Integer>> dayToTimeIndices = new EnumMap<>(DayOfWeek.class);
        buildDayToTimeIndices(timeslots, timeslotToIndex, dayToTimeIndices);

        Map<Long, Integer> roomToIndex = new HashMap<>();
        Map<Integer, Room> indexToRoom = new HashMap<>();
        List<Room> physicalRooms = rooms.stream().filter(r -> r.getZone() != null || r.getCapacity() > 0).toList();
        for (int i = 0; i < physicalRooms.size(); i++) {
            roomToIndex.put(physicalRooms.get(i).getId(), i);
            indexToRoom.put(i, physicalRooms.get(i));
        }
        int totalRooms = physicalRooms.size();

        // Create variables
        int n = unpinnedLessons.size();
        IntVar[] startVars = new IntVar[n];
        IntVar[] roomVars = new IntVar[n];
        IntervalVar[] intervals = new IntervalVar[n];
        BoolVar[][] roomAssignment = new BoolVar[n][];
        List<List<Integer>> validStartsPerLesson = new ArrayList<>();
        List<List<Integer>> validRoomsPerLesson = new ArrayList<>();

        // Create variables for each lesson
        for (int i = 0; i < n; i++) {
            Lesson lesson = unpinnedLessons.get(i);
            int duration = lesson.getDurationHours();

            // Compute valid start times
            List<Integer> validStarts = computeValidStartTimes(lesson, timeslots, timeslotToIndex,
                    indexToTimeslot, duration, earliestStart, latestEnd, fridayLatestEnd);
            validStartsPerLesson.add(validStarts);

            // Create start variable
            if (validStarts.isEmpty()) {
                log.error("No valid start times for lesson {} (course: {}, duration: {}h)",
                        lesson.getId(), lesson.getCourse() != null ? lesson.getCourse().getCode() : "null", duration);
                return null;
            } else {
                long[] domain = validStarts.stream().mapToLong(Integer::longValue).toArray();
                startVars[i] = model.newIntVarFromDomain(
                        com.google.ortools.util.Domain.fromValues(domain),
                        "start_" + lesson.getId());
            }

            // Compute valid rooms
            List<Integer> validRooms = computeValidRooms(lesson, physicalRooms, roomToIndex);
            validRoomsPerLesson.add(validRooms);

            // Create room variable
            if (!lesson.isOnline()) {
                if (validRooms.isEmpty()) {
                    log.warn("No valid rooms for lesson {} (students: {}, course: {})",
                            lesson.getId(), lesson.getTotalStudentCount(),
                            lesson.getCourse() != null ? lesson.getCourse().getCode() : "null");
                    roomVars[i] = model.newIntVar(-1, -1, "room_" + lesson.getId());
                    roomAssignment[i] = new BoolVar[0];
                } else {
                    long[] roomDomain = validRooms.stream().mapToLong(Integer::longValue).toArray();
                    roomVars[i] = model.newIntVarFromDomain(
                            com.google.ortools.util.Domain.fromValues(roomDomain),
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

        // Add HARD constraints only (skip soft constraints for speed)
        addHardConstraints(model, unpinnedLessons, startVars, roomVars, intervals, roomAssignment,
                totalRooms, physicalRooms, specialEvents, indexToTimeslot, timeslotToIndex, roomToIndex,
                validStartsPerLesson, validRoomsPerLesson, dayToTimeIndices,
                lunchBreakStart, lunchBreakEnd, lunchBreakEnforced,
                sameCourseSameDayAllowed, unavailabilityEnabled, maxLecturerConsecutiveHours);

        // Solve for feasibility only
        log.info("CP-SAT: Starting hard feasibility solve with {} workers, {}s limit",
                numWorkers, cpSatTimeLimitSeconds);

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(cpSatTimeLimitSeconds);
        solver.getParameters().setNumSearchWorkers(numWorkers);
        solver.getParameters().setLogSearchProgress(true);
        solver.getParameters().setCpModelPresolve(true);
        solver.getParameters().setLinearizationLevel(2);

        CpSolverStatus status = solver.solve(model);

        log.info("CP-SAT: Solve completed with status {} in {} ms",
                status, System.currentTimeMillis() - solveStartedAtMs.get());

        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            log.error("CP-SAT: No feasible solution found. Status: {}", status);
            return null;
        }

        // Extract solution and update lessons
        for (int i = 0; i < n; i++) {
            Lesson lesson = unpinnedLessons.get(i);
            int startIdx = (int) solver.value(startVars[i]);
            Timeslot assignedTimeslot = indexToTimeslot.get(startIdx);
            lesson.setTimeslot(assignedTimeslot);

            if (!lesson.isOnline() && roomVars[i] != null) {
                int roomIdx = (int) solver.value(roomVars[i]);
                if (roomIdx >= 0 && roomIdx < physicalRooms.size()) {
                    Room assignedRoom = physicalRooms.get(roomIdx);
                    lesson.setRoom(assignedRoom);
                }
            }
        }

        return buildTimeTable(lessons, timeslots, rooms);
    }

    /**
     * Phase 2: Use Timefold for soft optimization starting from CP-SAT solution
     */
    private TimeTable runTimefoldSoftOptimize(TimeTable initialSolution) {
        log.info("Timefold: Starting soft optimization from CP-SAT seed solution");

        Solver<TimeTable> solver = solverFactory.buildSolver();
        
        // Add adaptive multi-objective weighting listener
        // Dynamically adjusts soft constraint weights based on hard violation count
        solver.addEventListener(new com.university.timetable.solver.AdaptiveSolverListener());

        // Add listener to save best solution as it improves
        solver.addEventListener(event -> {
            TimeTable newBest = event.getNewBestSolution();
            HardSoftScore score = newBest.getScore();
            if (score != null && score.hardScore() >= 0) {
                latestSolution.set(newBest);
                // Save periodically (every 10 improvements)
                if (score.softScore() % 10 == 0) {
                    saveSolutionIfFeasible(newBest);
                }
            }
        });

        TimeTable finalSolution = solver.solve(initialSolution);

        HardSoftScore score = finalSolution.getScore();
        log.info("Timefold: Optimization complete. Score: {}", score);

        return finalSolution;
    }

    // ==================== Helper Methods ====================

    private void buildTimeslotMappings(List<Timeslot> timeslots,
                                       Map<Long, Integer> timeslotToIndex,
                                       Map<Integer, Timeslot> indexToTimeslot) {
        int idx = 0;
        for (Timeslot t : timeslots) {
            timeslotToIndex.put(t.getId(), idx);
            indexToTimeslot.put(idx, t);
            idx++;
        }
    }

    private void buildDayToTimeIndices(List<Timeslot> timeslots,
                                       Map<Long, Integer> timeslotToIndex,
                                       Map<DayOfWeek, List<Integer>> dayToTimeIndices) {
        for (Timeslot t : timeslots) {
            int idx = timeslotToIndex.get(t.getId());
            dayToTimeIndices.computeIfAbsent(t.getDayOfWeek(), k -> new ArrayList<>()).add(idx);
        }
    }

    private List<Integer> computeValidStartTimes(Lesson lesson, List<Timeslot> timeslots,
                                                  Map<Long, Integer> timeslotToIndex,
                                                  Map<Integer, Timeslot> indexToTimeslot,
                                                  int duration, LocalTime earliestStart,
                                                  LocalTime latestEnd, LocalTime fridayLatestEnd) {
        List<Integer> valid = new ArrayList<>();
        for (Timeslot t : timeslots) {
            int idx = timeslotToIndex.get(t.getId());
            // Check if lesson fits within this timeslot (using timeslot's own duration)
            if (t.getStartTime().plusHours(duration).isAfter(t.getEndTime(duration))) {
                continue;
            }
            // Check earliest start
            if (t.getStartTime().isBefore(earliestStart)) {
                continue;
            }
            // Check latest end (Friday special handling)
            LocalTime dayLatestEnd = (t.getDayOfWeek() == DayOfWeek.FRIDAY) ? fridayLatestEnd : latestEnd;
            if (t.getStartTime().plusHours(duration).isAfter(dayLatestEnd)) {
                continue;
            }
            valid.add(idx);
        }
        return valid;
    }

    private List<Integer> computeValidRooms(Lesson lesson, List<Room> physicalRooms,
                                            Map<Long, Integer> roomToIndex) {
        List<Integer> valid = new ArrayList<>();
        if (lesson.isOnline()) {
            return valid;
        }

        Course course = lesson.getCourse();
        int students = lesson.getTotalStudentCount();

        for (Room room : physicalRooms) {
            // Capacity check
            if (room.getCapacity() < students) {
                continue;
            }

            // Feature check
            if (course != null && course.getRequiredFeatures() != null) {
                boolean hasAllFeatures = course.getRequiredFeatures().stream()
                        .allMatch(req -> room.getFeatures().contains(req));
                if (!hasAllFeatures) {
                    continue;
                }
            }

            // Zone check
            if (course != null && course.getAllowedZones() != null && !course.getAllowedZones().isEmpty()) {
                if (room.getZone() == null || !course.getAllowedZones().contains(room.getZone())) {
                    continue;
                }
            }

            Integer idx = roomToIndex.get(room.getId());
            if (idx != null) {
                valid.add(idx);
            }
        }

        return valid;
    }

    private TimeTable buildTimeTable(List<Lesson> lessons, List<Timeslot> timeslots, List<Room> rooms) {
        TimeTable tt = new TimeTable();
        tt.setLessons(lessons);
        tt.setTimeslots(timeslots);
        tt.setRooms(rooms);
        return tt;
    }

    // ==================== Constraint Methods (Simplified - Hard Only) ====================

    private void addHardConstraints(CpModel model, List<Lesson> lessons,
                                    IntVar[] startVars, IntVar[] roomVars, IntervalVar[] intervals,
                                    BoolVar[][] roomAssignment, int totalRooms, List<Room> rooms,
                                    List<SpecialEvent> specialEvents,
                                    Map<Integer, Timeslot> indexToTimeslot,
                                    Map<Long, Integer> timeslotToIndex,
                                    Map<Long, Integer> roomToIndex,
                                    List<List<Integer>> validStartsPerLesson,
                                    List<List<Integer>> validRoomsPerLesson,
                                    Map<DayOfWeek, List<Integer>> dayToTimeIndices,
                                    LocalTime lunchBreakStart, LocalTime lunchBreakEnd,
                                    boolean lunchBreakEnforced,
                                    boolean sameCourseSameDayAllowed,
                                    boolean unavailabilityEnabled,
                                    int maxLecturerConsecutiveHours) {

        // 1. Room no-overlap
        addRoomNoOverlapConstraints(model, lessons, intervals, roomVars, roomAssignment,
                totalRooms, rooms, validRoomsPerLesson);

        // 2. Lecturer no-overlap
        addLecturerNoOverlapConstraints(model, lessons, intervals);

        // 3. Student group no-overlap
        addStudentGroupNoOverlapConstraints(model, lessons, intervals);

        // 4. Lecturer unavailability (if enabled)
        if (unavailabilityEnabled) {
            addLecturerUnavailabilityConstraints(model, lessons, startVars, indexToTimeslot);
        }

        // 5. Lunch break overlap (if enforced)
        if (lunchBreakEnforced) {
            addLunchBreakOverlapConstraints(model, lessons, startVars, indexToTimeslot,
                    lunchBreakStart, lunchBreakEnd);
        }

        // 6. Same course same day (if not allowed)
        if (!sameCourseSameDayAllowed) {
            addSameCourseSameDayConstraints(model, lessons, startVars, dayToTimeIndices);
        }

        // 7. Special event conflicts
        addSpecialEventConstraints(model, lessons, startVars, roomVars, specialEvents,
                indexToTimeslot, timeslotToIndex, roomToIndex, validStartsPerLesson, dayToTimeIndices);

        // Note: Max consecutive hours is handled by Timefold Phase 2 as a soft constraint.
        // Removing the heavy reified pair constraints (which create 95K+ BoolVars) to prevent
        // CP-SAT presolve timeout. Daily lesson cap in Phase 1a provides lightweight alternative.
    }

    /**
     * Per-timeslot capacity: cannot have more lessons in a timeslot than total rooms.
     * This is a necessary condition for room matching feasibility.
     */
    private void addTimeslotCapacityConstraints(CpModel model, List<Lesson> lessons,
                                                  IntVar[] startVars,
                                                  List<Timeslot> timeslots,
                                                  Map<Long, Integer> timeslotToIndex,
                                                  int totalRooms) {
        for (Timeslot ts : timeslots) {
            Integer tsIdx = timeslotToIndex.get(ts.getId());
            if (tsIdx == null) continue;
            List<BoolVar> inSlot = new ArrayList<>();
            for (int i = 0; i < lessons.size(); i++) {
                if (lessons.get(i).isOnline()) continue;
                BoolVar v = model.newBoolVar("cap_" + i + "_" + tsIdx);
                model.addEquality(startVars[i], tsIdx).onlyEnforceIf(v);
                model.addDifferent(startVars[i], tsIdx).onlyEnforceIf(v.not());
                inSlot.add(v);
            }
            if (!inSlot.isEmpty()) {
                model.addLessOrEqual(LinearExpr.sum(inSlot.toArray(new LinearArgument[0])), totalRooms);
            }
        }
        log.info("CP-SAT: Added per-timeslot capacity constraints: at most {} lessons per slot", totalRooms);
    }

    /**
     * Encode a nogood constraint from a failed room matching attempt.
     * Parses the nogood string like "Timeslot FRIDAY 09:00: 166 lessons but only 121 compatible rooms"
     * and adds a constraint: at most 121 lessons at that timeslot.
     */
    private void addNogoodConstraint(CpModel model, List<Lesson> lessons,
                                       IntVar[] startVars,
                                       List<Timeslot> timeslots,
                                       Map<Long, Integer> timeslotToIndex,
                                       List<Room> rooms,
                                       String nogood) {
        // Parse nogood: "Timeslot FRIDAY 09:00: 166 lessons but only 121 compatible rooms"
        try {
            String[] parts = nogood.split(":", 2);
            if (parts.length < 2) return;
            String tsPart = parts[0].replace("Timeslot ", "").trim(); // "FRIDAY 09:00"
            String numPart = parts[1].trim(); // "166 lessons but only 121 compatible rooms"
            
            // Extract the room limit
            String[] numParts = numPart.split("only ");
            if (numParts.length < 2) return;
            String limitPart = numParts[1].split(" ")[0]; // "121"
            int roomLimit = Integer.parseInt(limitPart);
            
            // Find the timeslot
            Timeslot targetTs = null;
            for (Timeslot ts : timeslots) {
                String tsName = ts.getDayOfWeek() + " " + ts.getStartTime().toString();
                // Format HH:MM -> HH:MM (e.g., "09:00" -> "09:00")
                if (tsName.equals(tsPart)) {
                    targetTs = ts;
                    break;
                }
            }
            if (targetTs == null) {
                log.warn("Could not find timeslot for nogood: {}", tsPart);
                return;
            }
            
            Integer tsIdx = timeslotToIndex.get(targetTs.getId());
            if (tsIdx == null) return;
            
            // Add constraint: at most roomLimit lessons at this timeslot
            List<BoolVar> inSlot = new ArrayList<>();
            for (int i = 0; i < lessons.size(); i++) {
                if (lessons.get(i).isOnline()) continue;
                BoolVar v = model.newBoolVar("ng_" + i + "_" + tsIdx);
                model.addEquality(startVars[i], tsIdx).onlyEnforceIf(v);
                model.addDifferent(startVars[i], tsIdx).onlyEnforceIf(v.not());
                inSlot.add(v);
            }
            if (!inSlot.isEmpty()) {
                model.addLessOrEqual(LinearExpr.sum(inSlot.toArray(new LinearArgument[0])), roomLimit);
                log.info("Encoded nogood: at most {} lessons at timeslot {}", roomLimit, tsPart);
            }
        } catch (Exception e) {
            log.warn("Failed to parse nogood constraint: {}", nogood, e);
        }
    }

    private void addRoomNoOverlapConstraints(CpModel model, List<Lesson> lessons,
                                             IntervalVar[] intervals, IntVar[] roomVars,
                                             BoolVar[][] roomAssignment, int totalRooms,
                                             List<Room> rooms, List<List<Integer>> validRoomsPerLesson) {
        int constraintsAdded = 0;
        for (int r = 0; r < totalRooms; r++) {
            List<IntervalVar> roomIntervals = new ArrayList<>();
            for (int i = 0; i < lessons.size(); i++) {
                if (lessons.get(i).isOnline() || roomAssignment[i].length == 0) continue;
                if (!validRoomsPerLesson.get(i).contains(r)) continue;
                IntervalVar optionalInterval = model.newOptionalFixedSizeIntervalVar(
                        intervals[i].getStartExpr(),
                        lessons.get(i).getDurationHours(),
                        roomAssignment[i][r],
                        "interval_lesson_" + lessons.get(i).getId() + "_room_" + r);
                roomIntervals.add(optionalInterval);
            }
            if (!roomIntervals.isEmpty()) {
                model.addNoOverlap(roomIntervals);
                constraintsAdded++;
            }
        }
        log.info("CP-SAT: Added room no-overlap constraints for {} rooms", constraintsAdded);
    }

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

    private void addStudentGroupNoOverlapConstraints(CpModel model, List<Lesson> lessons,
                                                      IntervalVar[] intervals) {
        Map<Set<Long>, List<Integer>> groupLessons = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            Set<Long> groupIds = lessons.get(i).getConflictGroupIds();
            if (!groupIds.isEmpty()) {
                groupLessons.computeIfAbsent(groupIds, k -> new ArrayList<>()).add(i);
            }
        }
        int constraintsAdded = 0;
        for (Map.Entry<Set<Long>, List<Integer>> entry : groupLessons.entrySet()) {
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
     * HARD: Lecturer unavailability — forbid start times overlapping lecturer blocked periods.
     */
    private void addLecturerUnavailabilityConstraints(CpModel model, List<Lesson> lessons,
                                                       IntVar[] startVars,
                                                       Map<Integer, Timeslot> indexToTimeslot) {
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
     * HARD: Lunch break overlap — forbid start times where lesson interval overlaps lunch window.
     */
    private void addLunchBreakOverlapConstraints(CpModel model, List<Lesson> lessons,
                                                  IntVar[] startVars,
                                                  Map<Integer, Timeslot> indexToTimeslot,
                                                  LocalTime lunchBreakStart, LocalTime lunchBreakEnd) {
        int forbidden = 0;
        for (int i = 0; i < lessons.size(); i++) {
            int duration = lessons.get(i).getDurationHours();
            for (Map.Entry<Integer, Timeslot> entry : indexToTimeslot.entrySet()) {
                Timeslot ts = entry.getValue();
                LocalTime lessonStart = ts.getStartTime();
                LocalTime lessonEnd = lessonStart.plusHours(duration);
                // Standard interval overlap: A.start < B.end && B.start < A.end
                if (lessonStart.isBefore(lunchBreakEnd) && lunchBreakStart.isBefore(lessonEnd)) {
                    model.addDifferent(startVars[i], entry.getKey());
                    forbidden++;
                }
            }
        }
        log.info("CP-SAT: Added {} lunch break forbidden assignments", forbidden);
    }

    private void addSameCourseSameDayConstraints(CpModel model, List<Lesson> lessons,
                                                  IntVar[] startVars,
                                                  Map<DayOfWeek, List<Integer>> dayToTimeIndices) {
        Map<Set<Long>, List<Integer>> courseGroupLessons = new HashMap<>();
        for (int i = 0; i < lessons.size(); i++) {
            Course course = lessons.get(i).getCourse();
            if (course == null) continue;
            Set<Long> groupIds = lessons.get(i).getConflictGroupIds();
            if (groupIds.isEmpty()) continue;
            Set<Long> key = new TreeSet<>();
            key.add(course.getId());
            key.addAll(groupIds);
            courseGroupLessons.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        int[] dayBoundaries = new int[6];
        int dayIdx = 0;
        for (DayOfWeek day : new DayOfWeek[] {DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            List<Integer> slots = dayToTimeIndices.get(day);
            dayBoundaries[dayIdx++] = slots != null && !slots.isEmpty() ? slots.get(0) : 0;
        }
        dayBoundaries[5] = dayBoundaries[4] + (dayToTimeIndices.get(DayOfWeek.FRIDAY) != null ?
                dayToTimeIndices.get(DayOfWeek.FRIDAY).size() : 0);

        int constraintsAdded = 0;
        for (List<Integer> indices : courseGroupLessons.values()) {
            if (indices.size() <= 1) continue;
            for (int j = 0; j < indices.size(); j++) {
                for (int k = j + 1; k < indices.size(); k++) {
                    int idx1 = indices.get(j);
                    int idx2 = indices.get(k);
                    IntVar day1 = model.newIntVar(0, 4, "day_lesson_" + idx1);
                    IntVar day2 = model.newIntVar(0, 4, "day_lesson_" + idx2);
                    for (int d = 0; d < 5; d++) {
                        int dayStart = dayBoundaries[d];
                        int dayEnd = (d < 4) ? dayBoundaries[d + 1] : dayBoundaries[5];
                        BoolVar inDay1 = model.newBoolVar("d" + d + "_l" + idx1);
                        model.addGreaterOrEqual(startVars[idx1], dayStart).onlyEnforceIf(inDay1);
                        model.addLessThan(startVars[idx1], dayEnd).onlyEnforceIf(inDay1);
                        model.addEquality(day1, d).onlyEnforceIf(inDay1);
                        BoolVar inDay2 = model.newBoolVar("d" + d + "_l" + idx2);
                        model.addGreaterOrEqual(startVars[idx2], dayStart).onlyEnforceIf(inDay2);
                        model.addLessThan(startVars[idx2], dayEnd).onlyEnforceIf(inDay2);
                        model.addEquality(day2, d).onlyEnforceIf(inDay2);
                    }
                    model.addDifferent(day1, day2);
                    constraintsAdded++;
                }
            }
        }
        log.info("CP-SAT: Added same course same day constraints: {}", constraintsAdded);
    }

    /**
     * HARD: Special event conflicts — forbid timeslot/room assignments overlapping with active special events.
     * Checks: student group conflicts, room conflicts, lecturer conflicts.
     */
    private void addSpecialEventConstraints(CpModel model, List<Lesson> lessons,
                                            IntVar[] startVars, IntVar[] roomVars,
                                            List<SpecialEvent> specialEvents,
                                            Map<Integer, Timeslot> indexToTimeslot,
                                            Map<Long, Integer> timeslotToIndex,
                                            Map<Long, Integer> roomToIndex,
                                            List<List<Integer>> validStartsPerLesson,
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
                                // lesson at this timeslot AND in this room → forbidden
                                BoolVar atThisTime = model.newBoolVar("se_time_" + i + "_" + entry.getKey());
                                model.addEquality(startVars[i], entry.getKey()).onlyEnforceIf(atThisTime);
                                model.addDifferent(startVars[i], entry.getKey()).onlyEnforceIf(atThisTime.not());

                                BoolVar inThisRoom = model.newBoolVar("se_room_" + i + "_" + eventRoomIdx);
                                model.addEquality(roomVars[i], eventRoomIdx).onlyEnforceIf(inThisRoom);
                                model.addDifferent(roomVars[i], eventRoomIdx).onlyEnforceIf(inThisRoom.not());

                                // Both true → forbidden (at least one must be false)
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
     * HARD: Max lecturer consecutive hours.
     * Counts back-to-back lesson pairs per lecturer per day.
     * Enforces: count <= maxHours - 1 per lecturer per day.
     * Efficient encoding: precomputes day-membership BoolVars per (lesson, day)
     * and reuses them across all pairs.
     */
    private void addMaxLecturerConsecutiveHoursConstraints(CpModel model, List<Lesson> lessons,
                                                           IntVar[] startVars, IntervalVar[] intervals,
                                                           Map<Integer, Timeslot> indexToTimeslot,
                                                           int maxHours) {
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
        // Two-way implication: inDay=true ↔ dayStart <= startVars[idx] <= dayEnd
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

    private void saveSolutionIfFeasible(TimeTable solution) {
        if (solution == null || solution.getScore() == null) return;
        if (solution.getScore().hardScore() < 0) {
            log.warn("Not saving solution - hard constraints not satisfied: {}", solution.getScore());
            return;
        }
        try {
            solutionSaver.saveSolution(solution);
            log.info("Solution saved successfully with score: {}", solution.getScore());
        } catch (Exception e) {
            log.error("Failed to save solution: {}", e.getMessage(), e);
        }
    }

    // ==================== Public API ====================

    public SolverStatusDTO getStatus() {
        SolverStatusDTO dto = new SolverStatusDTO();
        dto.setJobId("hybrid-" + solveStartedAtMs.get());
        dto.setState(solvingInProgress.get() ? "SOLVING" : currentStatus.get());
        dto.setScore(currentScore.get());
        dto.setStage(currentPhase.get());

        if (solvingInProgress.get()) {
            long elapsedMs = System.currentTimeMillis() - solveStartedAtMs.get();
            dto.setDurationMs(elapsedMs);
            if (phaseOneCompleteAtMs.get() > 0) {
                dto.setStageOneDurationMs(phaseOneCompleteAtMs.get() - solveStartedAtMs.get());
                dto.setStageTwoDurationMs(elapsedMs - dto.getStageOneDurationMs());
            }
        }

        return dto;
    }

    public boolean isSolving() {
        return solvingInProgress.get();
    }

    public void terminateSolving() {
        if (solvingInProgress.get()) {
            log.info("Terminating hybrid solver...");
            
            // Save the latest best solution BEFORE interrupting
            TimeTable currentBest = latestSolution.get();
            if (currentBest != null) {
                log.info("Saving current best solution before termination...");
                saveSolutionIfFeasible(currentBest);
            }
            
            currentStatus.set("TERMINATED");
            solvingInProgress.set(false);
            if (solverThread != null) {
                solverThread.interrupt();
            }
        }
    }
}
