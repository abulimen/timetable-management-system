package com.university.timetable.service;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.SolverRunMetric;
import com.university.timetable.domain.SpecialEvent;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.domain.TimeTable;
import com.university.timetable.domain.Timeslot;
import com.university.timetable.dto.SolveRequestDTO;
import com.university.timetable.dto.SolverProfile;
import com.university.timetable.dto.SolverRuntimeDiagnosticsDTO;
import com.university.timetable.dto.SolverStatusDTO;
import com.university.timetable.repository.CourseRepository;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.SpecialEventRepository;
import com.university.timetable.repository.StudentGroupRepository;
import com.university.timetable.repository.TimeslotRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolverStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SolverService - manages asynchronous solving with OptaPlanner's SolverManager.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolverService {

    private final SolverManager<TimeTable, Long> solverManager;
    private final LessonRepository lessonRepository;
    private final TimeslotRepository timeslotRepository;
    private final RoomRepository roomRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final SpecialEventRepository specialEventRepository;
    private final CourseRepository courseRepository;
    private final TimeslotService timeslotService;
    private final LessonService lessonService;
    private final SolutionSaver solutionSaver;
    private final ConstraintSettingsService constraintSettingsService;
    private final SolverRunMetricsService solverRunMetricsService;
    private final TimetableChangeTrackerService timetableChangeTrackerService;
    private final AuditLogService auditLogService;
    private final SolverRuntimeDiagnosticsDTO runtimeDiagnostics;

    @Value("${solver.persistence.checkpoint-enabled:false}")
    private boolean checkpointEnabledDefault;

    @Value("${solver.persistence.checkpoint-min-interval-ms:120000}")
    private long checkpointMinIntervalMsDefault;

    @Value("${solver.persistence.checkpoint-every-n-improvements:0}")
    private int checkpointEveryNImprovementsDefault;

    @Value("${solver.scoped.max-impact-ratio:0.25}")
    private double scopedMaxImpactRatio;

    private static final Long PROBLEM_ID = 1L;

    private String currentJobId;
    private volatile String currentMode = "FULL_REPLAN";
    private volatile String currentProfile = SolverProfile.BALANCED.name();
    private volatile String currentBestScore = "N/A";
    private volatile Integer currentBestHardScore;
    private volatile Integer currentBestSoftScore;
    private volatile String currentRunError;
    private volatile int currentLessonsCount;
    private volatile int currentTimeslotsCount;
    private volatile int currentRoomsCount;
    private volatile int currentImpactedLessonsCount;
    private volatile int currentLockedLessonsCount;
    private volatile int currentChangedLockedLessonsCount;
    private volatile LocalDateTime currentRunStartedAt;
    private volatile Long lastRunDurationMs;

    private final AtomicLong solveStartedAtMs = new AtomicLong(0L);
    private final AtomicLong bestSolutionCount = new AtomicLong(0L);
    private final AtomicLong firstBestAtMs = new AtomicLong(0L);
    private final AtomicLong firstFeasibleAtMs = new AtomicLong(0L);
    private final AtomicLong lastBestAtMs = new AtomicLong(0L);
    private final AtomicLong lastPersistedAtMs = new AtomicLong(0L);
    private final AtomicLong persistedBestCount = new AtomicLong(0L);
    private final AtomicLong persistedBestTotalMs = new AtomicLong(0L);
    private final AtomicLong feasibleBestCount = new AtomicLong(0L);
    private final AtomicBoolean runFinalized = new AtomicBoolean(true);
    private volatile HardSoftScore lastPersistedScore;
    private volatile TimeTable latestFeasibleBestSolution;

    @PostConstruct
    public void init() {
        log.info("SolverService initialized with SolverManager: {} | runtimeDiagnostics={}",
                solverManager, runtimeDiagnostics);
    }

    /**
     * Start the solver with specified mode.
     */
    public SolverStatusDTO startSolving(String mode) {
        SolveRequestDTO request = new SolveRequestDTO();
        request.setMode(mode);
        return startSolving(request);
    }

    /**
     * Start the solver with request payload.
     */
    public SolverStatusDTO startSolving(SolveRequestDTO request) {
        long startNanos = System.nanoTime();
        String mode = request != null ? request.getMode() : null;
        String normalizedMode = (mode == null || mode.isBlank()) ? "FULL_REPLAN" : mode.trim().toUpperCase();
        SolverProfile profile = SolverProfile.fromNullable(request != null ? request.getProfile() : null);
        log.info("Starting solver in {} mode with profile {} (threads={}, env={}, processors={})",
                normalizedMode, profile,
                runtimeDiagnostics.getMoveThreadCount(),
                runtimeDiagnostics.getEnvironmentMode(),
                runtimeDiagnostics.getAvailableProcessors());
        if ("SCOPED_REPLAN".equalsIgnoreCase(normalizedMode)) {
            throw new IllegalStateException(
                    "Scoped replan is discontinued. Enable editing mode, apply your changes, then run FULL_REPLAN.");
        }

        SolverStatus currentStatus = solverManager.getSolverStatus(PROBLEM_ID);
        if (currentStatus == SolverStatus.SOLVING_ACTIVE) {
            throw new IllegalStateException("Solver is already running.");
        }

        // Block solving if unavailability system is enabled but requests are still open.
        if (constraintSettingsService.isUnavailabilitySystemEnabled() &&
                constraintSettingsService.isUnavailabilityRequestsOpen()) {
            throw new IllegalStateException(
                    "Cannot generate timetable while unavailability requests are still open. " +
                            "Please close the request period first.");
        }

        if (!runFinalized.get()) {
            finalizeRunIfNeeded("INTERRUPTED", "New solve run started before previous run finalized.");
        }

        // Ensure timeslots exist.
        if (!timeslotService.hasTimeslots()) {
            long timeslotStart = System.nanoTime();
            timeslotService.generateTimeslots();
            log.debug("Generated timeslots before solve in {} ms", elapsedMs(timeslotStart));
        }
        ensureLessonsExistForCourses();

        long loadStart = System.nanoTime();
        TimeTable problem = loadProblem();
        long loadMs = elapsedMs(loadStart);

        currentLessonsCount = problem.getLessons().size();
        currentTimeslotsCount = problem.getTimeslots().size();
        currentRoomsCount = problem.getRooms().size();
        currentImpactedLessonsCount = 0;
        currentLockedLessonsCount = 0;
        currentChangedLockedLessonsCount = 0;

        log.info("Loaded problem: {} lessons, {} timeslots, {} rooms",
                currentLessonsCount, currentTimeslotsCount, currentRoomsCount);
        log.info("Solver pre-flight completed in {} ms (problem load: {} ms)",
                elapsedMs(startNanos), loadMs);

        if (problem.getLessons().isEmpty()) {
            throw new IllegalStateException("No lessons to schedule. Import data first.");
        }
        if (problem.getTimeslots().isEmpty()) {
            throw new IllegalStateException("No timeslots available.");
        }
        if (problem.getRooms().isEmpty()) {
            throw new IllegalStateException("No rooms available.");
        }

        if ("STABILITY".equalsIgnoreCase(normalizedMode)) {
            prepareStabilityMode(problem);
        }
        Map<Long, String> lockedAssignmentBaseline = Map.of();
        Set<Long> scopedImpactedIds = Set.of();
        if ("SCOPED_REPLAN".equalsIgnoreCase(normalizedMode)) {
            SolveRequestDTO.SolveScopeDTO scope = request != null ? request.getScope() : null;
            boolean allowLargeScope = request != null && Boolean.TRUE.equals(request.getAllowLargeScope());
            scopedImpactedIds = applyScopedReplanMode(problem, scope, allowLargeScope);
            lockedAssignmentBaseline = captureLockedAssignments(problem, scopedImpactedIds);
            currentImpactedLessonsCount = scopedImpactedIds.size();
            currentLockedLessonsCount = Math.max(0, problem.getLessons().size() - scopedImpactedIds.size());
            log.info("Scoped replan prepared: impacted={}, locked={}", currentImpactedLessonsCount, currentLockedLessonsCount);
        }

        currentJobId = UUID.randomUUID().toString();
        currentMode = normalizedMode;
        currentProfile = profile.name();
        currentRunStartedAt = LocalDateTime.now();
        currentRunError = null;
        currentBestScore = "N/A";
        currentBestHardScore = null;
        currentBestSoftScore = null;
        lastRunDurationMs = null;
        latestFeasibleBestSolution = null;
        AtomicBoolean fastFeasibleTerminationRequested = new AtomicBoolean(false);

        long nowMs = System.currentTimeMillis();
        solveStartedAtMs.set(nowMs);
        bestSolutionCount.set(0L);
        firstBestAtMs.set(0L);
        firstFeasibleAtMs.set(0L);
        lastBestAtMs.set(0L);
        lastPersistedAtMs.set(0L);
        persistedBestCount.set(0L);
        persistedBestTotalMs.set(0L);
        feasibleBestCount.set(0L);
        lastPersistedScore = null;
        runFinalized.set(false);
        timetableChangeTrackerService.lockEditing("Solver run in progress. Editing is locked.");

        final Map<Long, String> finalLockedAssignmentBaseline = lockedAssignmentBaseline;
        final Set<Long> finalScopedImpactedIds = scopedImpactedIds;
        log.info("Starting async solver with problem ID: {}", PROBLEM_ID);
        solverManager.solveAndListen(PROBLEM_ID,
                id -> {
                    log.info("Problem factory called for ID: {}", id);
                    return problem;
                },
                bestSolution -> {
                    long callbackNow = System.currentTimeMillis();
                    long improvementIndex = bestSolutionCount.incrementAndGet();

                    if (firstBestAtMs.get() == 0L) {
                        firstBestAtMs.compareAndSet(0L, callbackNow);
                    }
                    long previousBest = lastBestAtMs.getAndSet(callbackNow);
                    long solveElapsedMs = callbackNow - solveStartedAtMs.get();
                    long sinceLastImprovementMs = previousBest == 0L ? solveElapsedMs : callbackNow - previousBest;

                    HardSoftScore score = bestSolution.getScore();
                    if (score != null) {
                        currentBestHardScore = score.hardScore();
                        currentBestSoftScore = score.softScore();
                        if (score.hardScore() >= 0) {
                            feasibleBestCount.incrementAndGet();
                            if (firstFeasibleAtMs.get() == 0L) {
                                firstFeasibleAtMs.compareAndSet(0L, callbackNow);
                            }
                            latestFeasibleBestSolution = bestSolution;
                            if (profile == SolverProfile.FAST_FEASIBLE &&
                                    fastFeasibleTerminationRequested.compareAndSet(false, true)) {
                                log.info("FAST_FEASIBLE reached first hard-feasible score ({}). Terminating early.",
                                        score);
                                solverManager.terminateEarly(PROBLEM_ID);
                            }
                        }
                    }
                    currentBestScore = String.valueOf(score);
                    int assignedLessons = countAssignedLessons(bestSolution);

                    if ("SCOPED_REPLAN".equalsIgnoreCase(currentMode) && !finalLockedAssignmentBaseline.isEmpty()) {
                        long changedLocked = countChangedLockedAssignments(bestSolution, finalLockedAssignmentBaseline);
                        currentChangedLockedLessonsCount = (int) changedLocked;
                        if (changedLocked > 0) {
                            currentRunError = "Scoped replan modified locked lessons (" + changedLocked + ").";
                            log.error("Scoped replan breach: {} locked lessons changed. Terminating run.", changedLocked);
                            solverManager.terminateEarly(PROBLEM_ID);
                            return;
                        }
                    }

                    if (improvementIndex == 1) {
                        log.info("First best solution in {} ms | score={} | assignedLessons={}/{}",
                                solveElapsedMs, currentBestScore, assignedLessons, bestSolution.getLessons().size());
                    } else if (improvementIndex <= 5 || improvementIndex % 10 == 0) {
                        log.info("Best solution #{} at {} ms (+{} ms) | score={} | assignedLessons={}/{}",
                                improvementIndex, solveElapsedMs, sinceLastImprovementMs, currentBestScore,
                                assignedLessons, bestSolution.getLessons().size());
                    } else {
                        log.debug("Best solution #{} at {} ms (+{} ms) | score={} | assignedLessons={}/{}",
                                improvementIndex, solveElapsedMs, sinceLastImprovementMs, currentBestScore,
                                assignedLessons, bestSolution.getLessons().size());
                    }

                    if (isCheckpointEnabled() && shouldPersistCheckpoint(improvementIndex, score, callbackNow)) {
                        long saveStart = System.nanoTime();
                        try {
                            solutionSaver.saveSolution(bestSolution);
                            long saveMs = elapsedMs(saveStart);
                            persistedBestCount.incrementAndGet();
                            persistedBestTotalMs.addAndGet(saveMs);
                            lastPersistedAtMs.set(callbackNow);
                            lastPersistedScore = score;
                            log.debug("Checkpoint persisted best solution #{} in {} ms", improvementIndex, saveMs);
                        } catch (Exception e) {
                            log.error("Failed to save solution: {}", e.getMessage(), e);
                        }
                    } else {
                        if (score != null && score.hardScore() < 0) {
                            log.debug("Skipped checkpoint for best solution #{} (hard-infeasible score={})",
                                    improvementIndex, score);
                        } else {
                            log.debug("Skipped checkpoint for best solution #{} (disabled/throttled)", improvementIndex);
                        }
                    }
                },
                (problemId, exception) -> {
                    String errorMessage = exception != null ? exception.getMessage() : "Unknown solver exception";
                    currentRunError = errorMessage;
                    log.error("Solver exception for problem {} after {} ms and {} improvements: {}",
                            problemId,
                            System.currentTimeMillis() - solveStartedAtMs.get(),
                            bestSolutionCount.get(),
                            errorMessage,
                            exception);
                    finalizeRunIfNeeded("FAILED", errorMessage);
                });

        log.info("Solver started with job ID: {}", currentJobId);
        if ("SCOPED_REPLAN".equalsIgnoreCase(currentMode)) {
            auditLogService.logSystemAction(
                    "SCOPED_REPLAN_STARTED: runId=" + currentJobId
                            + ", impacted=" + currentImpactedLessonsCount
                            + ", locked=" + currentLockedLessonsCount
                            + ", reason=" + (request != null && request.getScope() != null ? request.getScope().getReason() : "n/a"),
                    true,
                    null);
        }
        SolverStatusDTO status = new SolverStatusDTO(currentJobId, "SOLVING", currentBestScore, null);
        status.setRunOutcome("RUNNING");
        status.setBestHardScore(currentBestHardScore);
        status.setBestSoftScore(currentBestSoftScore);
        status.setFeasible(currentBestHardScore != null && currentBestHardScore >= 0);
        status.setProfile(currentProfile);
        status.setImpactedLessonsCount(currentImpactedLessonsCount);
        status.setLockedLessonsCount(currentLockedLessonsCount);
        status.setChangedLockedLessonsCount(currentChangedLockedLessonsCount);
        enrichRuntimeDetails(status);
        return enrichWithPendingChangeStatus(status);
    }

    /**
     * Get current solver status.
     */
    public SolverStatusDTO getStatus() {
        SolverStatus status = solverManager.getSolverStatus(PROBLEM_ID);
        if (status == SolverStatus.NOT_SOLVING) {
            if (currentRunError == null && currentBestHardScore != null && currentBestHardScore < 0) {
                currentRunError = "Solver finished without a hard-feasible timetable. No invalid assignments were applied.";
            }
            String finalStatus = currentRunError == null ? "COMPLETED" : "FAILED";
            finalizeRunIfNeeded(finalStatus, currentRunError);
        }

        String score = currentBestScore;
        if (status == SolverStatus.NOT_SOLVING && bestSolutionCount.get() > 0) {
            score = currentBestScore + " (last best)";
        }
        Long durationMs = null;
        if (status == SolverStatus.NOT_SOLVING) {
            durationMs = lastRunDurationMs;
        } else if (solveStartedAtMs.get() > 0L) {
            durationMs = Math.max(0L, System.currentTimeMillis() - solveStartedAtMs.get());
        }
        SolverStatusDTO dto = new SolverStatusDTO(currentJobId, status.name(), score, durationMs);
        dto.setRunOutcome(status == SolverStatus.NOT_SOLVING
                ? (currentRunError == null ? "COMPLETED" : "FAILED")
                : "RUNNING");
        dto.setBestHardScore(currentBestHardScore);
        dto.setBestSoftScore(currentBestSoftScore);
        dto.setFeasible(currentBestHardScore != null && currentBestHardScore >= 0);
        dto.setProfile(currentProfile);
        dto.setImpactedLessonsCount(currentImpactedLessonsCount);
        dto.setLockedLessonsCount(currentLockedLessonsCount);
        dto.setChangedLockedLessonsCount(currentChangedLockedLessonsCount);
        enrichRuntimeDetails(dto);
        return enrichWithPendingChangeStatus(dto);
    }

    /**
     * Terminate solver early.
     */
    public SolverStatusDTO terminate() {
        log.info("Terminating solver early");
        solverManager.terminateEarly(PROBLEM_ID);
        finalizeRunIfNeeded("TERMINATED", "Manual termination requested.");

        long elapsed = solveStartedAtMs.get() == 0L ? 0L : (System.currentTimeMillis() - solveStartedAtMs.get());
        log.info("Solver terminated after {} ms with {} improvements; latest score={}",
                elapsed, bestSolutionCount.get(), currentBestScore);
        SolverStatusDTO dto = new SolverStatusDTO(currentJobId, "TERMINATED", currentBestScore, elapsed);
        dto.setRunOutcome("TERMINATED");
        dto.setBestHardScore(currentBestHardScore);
        dto.setBestSoftScore(currentBestSoftScore);
        dto.setFeasible(currentBestHardScore != null && currentBestHardScore >= 0);
        dto.setProfile(currentProfile);
        dto.setImpactedLessonsCount(currentImpactedLessonsCount);
        dto.setLockedLessonsCount(currentLockedLessonsCount);
        dto.setChangedLockedLessonsCount(currentChangedLockedLessonsCount);
        enrichRuntimeDetails(dto);
        return enrichWithPendingChangeStatus(dto);
    }

    /**
     * Load the problem from database.
     */
    @Transactional(readOnly = true)
    public TimeTable loadProblem() {
        List<Lesson> lessons = lessonRepository.findAll();
        List<Timeslot> timeslots = timeslotRepository.findAll();
        List<Room> rooms = roomRepository.findAll();
        List<Lecturer> lecturers = lecturerRepository.findAll();
        List<StudentGroup> studentGroups = studentGroupRepository.findAll();
        List<SpecialEvent> specialEvents = specialEventRepository.findByActiveTrue();

        log.info("Loaded: {} lessons, {} timeslots, {} rooms, {} lecturers, {} groups, {} special events",
                lessons.size(), timeslots.size(), rooms.size(), lecturers.size(), studentGroups.size(),
                specialEvents.size());

        return new TimeTable(lessons, timeslots, rooms, lecturers, studentGroups, specialEvents);
    }

    /**
     * Prepare stability mode: pin all existing assignments.
     */
    private void prepareStabilityMode(TimeTable problem) {
        log.info("Preparing stability mode - pinning existing assignments");
        for (Lesson lesson : problem.getLessons()) {
            if (lesson.getTimeslot() != null && lesson.getRoom() != null) {
                lesson.setPinned(true);
            }
        }
    }

    /**
     * Check if solver is currently running.
     */
    public boolean isSolving() {
        SolverStatus status = solverManager.getSolverStatus(PROBLEM_ID);
        return status == SolverStatus.SOLVING_ACTIVE;
    }

    /**
     * Clear all current timetable assignments (timeslot/room) and unpin lessons.
     * Keeps imported entities (courses, rooms, lecturers, groups) intact.
     */
    @Transactional
    public int clearCurrentTimetable() {
        if (isSolving()) {
            solverManager.terminateEarly(PROBLEM_ID);
            finalizeRunIfNeeded("TERMINATED", "Terminated due to timetable clear operation.");
        }
        int updated = lessonRepository.clearAllAssignmentsAndPins();
        timetableChangeTrackerService.markDirty("Manual timetable clear");
        log.warn("Cleared timetable assignments for {} lesson(s)", updated);
        return updated;
    }

    private void ensureLessonsExistForCourses() {
        if (lessonRepository.count() > 0) {
            return;
        }
        List<Course> courses = courseRepository.findAll();
        int generated = 0;
        for (Course course : courses) {
            if (course.getTotalWeeklyHours() <= 0) {
                continue;
            }
            lessonService.generateLessons(course);
            generated++;
        }
        if (generated > 0) {
            log.info("Auto-generated lessons for {} course(s) before solving", generated);
        }
    }

    private Set<Long> applyScopedReplanMode(
            TimeTable problem,
            SolveRequestDTO.SolveScopeDTO scope,
            boolean allowLargeScope) {
        if (scope == null || scope.getImpactedLessonIds() == null || scope.getImpactedLessonIds().isEmpty()) {
            throw new IllegalStateException("Scoped replan requires non-empty scope.impactedLessonIds.");
        }

        Set<Long> impacted = scope.getImpactedLessonIds().stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (scope.getExcludedLessonIds() != null) {
            impacted.removeAll(scope.getExcludedLessonIds().stream().filter(Objects::nonNull).toList());
        }
        if (impacted.isEmpty()) {
            throw new IllegalStateException("Scoped replan scope is empty after exclusions.");
        }

        Set<Long> availableIds = problem.getLessons().stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> missingIds = impacted.stream()
                .filter(id -> !availableIds.contains(id))
                .sorted()
                .toList();
        if (!missingIds.isEmpty()) {
            throw new IllegalStateException("Scoped replan contains invalid lesson IDs: " + missingIds);
        }

        int total = Math.max(1, problem.getLessons().size());
        double ratio = (double) impacted.size() / (double) total;
        if (!allowLargeScope && ratio > scopedMaxImpactRatio) {
            throw new IllegalStateException(String.format(
                    "Scoped replan too large: %d/%d lessons (%.1f%%). Use full replan or allowLargeScope=true.",
                    impacted.size(), total, ratio * 100.0));
        }

        for (Lesson lesson : problem.getLessons()) {
            Long lessonId = lesson.getId();
            boolean isImpacted = lessonId != null && impacted.contains(lessonId);
            lesson.setPinned(!isImpacted);
        }

        return impacted;
    }

    private Map<Long, String> captureLockedAssignments(TimeTable problem, Set<Long> impactedIds) {
        Map<Long, String> baseline = new HashMap<>();
        for (Lesson lesson : problem.getLessons()) {
            if (lesson.getId() == null || impactedIds.contains(lesson.getId())) {
                continue;
            }
            baseline.put(lesson.getId(), assignmentSignature(lesson));
        }
        return baseline;
    }

    private long countChangedLockedAssignments(TimeTable solution, Map<Long, String> baseline) {
        long changed = 0L;
        for (Lesson lesson : solution.getLessons()) {
            if (lesson.getId() == null || !baseline.containsKey(lesson.getId())) {
                continue;
            }
            String expected = baseline.get(lesson.getId());
            String actual = assignmentSignature(lesson);
            if (!Objects.equals(expected, actual)) {
                changed++;
            }
        }
        return changed;
    }

    private String assignmentSignature(Lesson lesson) {
        Long timeslotId = lesson.getTimeslot() != null ? lesson.getTimeslot().getId() : null;
        Long roomId = lesson.getRoom() != null ? lesson.getRoom().getId() : null;
        return timeslotId + "|" + roomId;
    }

    private boolean shouldPersistCheckpoint(long improvementIndex, HardSoftScore score, long nowMs) {
        if (score != null && score.hardScore() < 0) {
            return false;
        }
        if (improvementIndex == 1) {
            return true;
        }
        if (score != null && lastPersistedScore != null && score.hardScore() > lastPersistedScore.hardScore()) {
            return true;
        }
        if (score != null && lastPersistedScore == null) {
            return true;
        }
        int checkpointEveryNImprovements = getCheckpointEveryNImprovements();
        if (checkpointEveryNImprovements > 0 && improvementIndex % checkpointEveryNImprovements == 0) {
            return true;
        }
        return nowMs - lastPersistedAtMs.get() >= Math.max(0L, getCheckpointMinIntervalMs());
    }

    private boolean isCheckpointEnabled() {
        return constraintSettingsService.getBoolean("solver_checkpoint_enabled", checkpointEnabledDefault);
    }

    private long getCheckpointMinIntervalMs() {
        return constraintSettingsService.getInt(
                "solver_checkpoint_min_interval_ms",
                (int) Math.max(0L, checkpointMinIntervalMsDefault));
    }

    private int getCheckpointEveryNImprovements() {
        return constraintSettingsService.getInt(
                "solver_checkpoint_every_n_improvements",
                checkpointEveryNImprovementsDefault);
    }

    private void finalizeRunIfNeeded(String status, String reason) {
        if (runFinalized.get()) {
            return;
        }
        if (!runFinalized.compareAndSet(false, true)) {
            return;
        }
        if (currentJobId == null || solveStartedAtMs.get() == 0L) {
            return;
        }

        long startedMs = solveStartedAtMs.get();
        long finishedMs = System.currentTimeMillis();
        long durationMs = Math.max(0L, finishedMs - startedMs);
        lastRunDurationMs = durationMs;
        Long firstBestDelay = firstBestAtMs.get() > 0L ? Math.max(0L, firstBestAtMs.get() - startedMs) : null;

        if ("COMPLETED".equalsIgnoreCase(status) && currentBestHardScore != null && currentBestHardScore >= 0) {
            TimeTable finalFeasible = latestFeasibleBestSolution;
            if (finalFeasible != null) {
                long saveStart = System.nanoTime();
                try {
                    solutionSaver.saveSolution(finalFeasible);
                    long saveMs = elapsedMs(saveStart);
                    persistedBestCount.incrementAndGet();
                    persistedBestTotalMs.addAndGet(saveMs);
                    lastPersistedAtMs.set(System.currentTimeMillis());
                    if (finalFeasible.getScore() != null) {
                        lastPersistedScore = finalFeasible.getScore();
                    }
                    log.info("Persisted final feasible timetable snapshot in {} ms (hardScore={})",
                            saveMs, currentBestHardScore);
                } catch (Exception e) {
                    log.error("Failed to persist final feasible timetable snapshot: {}", e.getMessage(), e);
                    if (reason == null || reason.isBlank()) {
                        reason = "Final solution persistence failed: " + e.getMessage();
                    }
                }
            } else {
                log.warn("Solver completed but no feasible best solution snapshot was captured for final persistence.");
            }
        }

        long persistenceCount = persistedBestCount.get();
        Long avgPersistence = persistenceCount > 0 ? persistedBestTotalMs.get() / persistenceCount : null;

        SolverRunMetric runMetric = new SolverRunMetric();
        runMetric.setRunId(currentJobId);
        runMetric.setMode(currentMode);
        runMetric.setProfile(currentProfile);
        runMetric.setStatus(status);
        runMetric.setBestScore(currentBestScore);
        runMetric.setBestHardScore(currentBestHardScore);
        runMetric.setBestSoftScore(currentBestSoftScore);
        runMetric.setLessonsCount(currentLessonsCount);
        runMetric.setTimeslotsCount(currentTimeslotsCount);
        runMetric.setRoomsCount(currentRoomsCount);
        runMetric.setImpactedLessonsCount(currentImpactedLessonsCount);
        runMetric.setLockedLessonsCount(currentLockedLessonsCount);
        runMetric.setChangedLessonsCount(currentChangedLockedLessonsCount);
        runMetric.setImprovementCount(bestSolutionCount.get());
        runMetric.setPersistenceCount(persistenceCount);
        runMetric.setAvgPersistenceMs(avgPersistence);
        runMetric.setDurationMs(durationMs);
        runMetric.setTimeToFirstBestMs(firstBestDelay);
        runMetric.setErrorMessage(reason);
        runMetric.setMoveThreadCount(runtimeDiagnostics.getMoveThreadCount());
        runMetric.setEnvironmentMode(runtimeDiagnostics.getEnvironmentMode());
        runMetric.setParallelSolverCount(runtimeDiagnostics.getParallelSolverCount());
        runMetric.setAvailableProcessors(runtimeDiagnostics.getAvailableProcessors());
        runMetric.setStartedAt(currentRunStartedAt != null ? currentRunStartedAt : toLocalDateTime(startedMs));
        runMetric.setFinishedAt(LocalDateTime.now());

        try {
            solverRunMetricsService.recordRun(runMetric);
        } catch (Exception e) {
            log.error("Failed to persist solver run metric for run {}: {}", currentJobId, e.getMessage(), e);
        }

        log.info(
                "Solver run finalized: runId={}, status={}, mode={}, durationMs={}, firstBestMs={}, improvements={}, persistenceCount={}, bestScore={}",
                currentJobId, status, currentMode, durationMs, firstBestDelay, bestSolutionCount.get(),
                persistenceCount, currentBestScore);

        if ("SCOPED_REPLAN".equalsIgnoreCase(currentMode)) {
            boolean success = "COMPLETED".equalsIgnoreCase(status);
            String eventType = success ? "SCOPED_REPLAN_COMPLETED" : "SCOPED_REPLAN_FAILED";
            auditLogService.logSystemAction(
                    eventType + ": runId=" + currentJobId
                            + ", status=" + status
                            + ", impacted=" + currentImpactedLessonsCount
                            + ", locked=" + currentLockedLessonsCount
                            + ", changedLocked=" + currentChangedLockedLessonsCount
                            + ", durationMs=" + durationMs,
                    success,
                    success ? null : reason);
        }

        if ("COMPLETED".equals(status)) {
            timetableChangeTrackerService.clear("Timetable solved (" + currentMode + ")");
            timetableChangeTrackerService
                    .lockEditing("Timetable generated. Editing is now locked until admin enables editing mode.");
        }
    }

    private SolverStatusDTO enrichWithPendingChangeStatus(SolverStatusDTO status) {
        var changeStatus = timetableChangeTrackerService.getStatus();
        status.setPendingChanges(changeStatus.isPendingChanges());
        status.setPendingChangeReason(changeStatus.getReason());
        status.setPendingChangeSince(changeStatus.getChangedAt());
        return status;
    }

    private void enrichRuntimeDetails(SolverStatusDTO status) {
        long startedMs = solveStartedAtMs.get();
        status.setRunStartedAt(currentRunStartedAt);
        status.setLastImprovementAt(lastBestAtMs.get() > 0 ? toLocalDateTime(lastBestAtMs.get()) : null);
        status.setTimeToFirstBestMs(firstBestAtMs.get() > 0 && startedMs > 0
                ? Math.max(0L, firstBestAtMs.get() - startedMs)
                : null);
        status.setTimeToFirstFeasibleMs(firstFeasibleAtMs.get() > 0 && startedMs > 0
                ? Math.max(0L, firstFeasibleAtMs.get() - startedMs)
                : null);
        status.setImprovementCount(bestSolutionCount.get());
        long persistenceCount = persistedBestCount.get();
        status.setPersistenceCount(persistenceCount);
        status.setAvgPersistenceMs(persistenceCount > 0 ? persistedBestTotalMs.get() / persistenceCount : null);
        status.setLessonsCount(currentLessonsCount);
        status.setTimeslotsCount(currentTimeslotsCount);
        status.setRoomsCount(currentRoomsCount);
        status.setMoveThreadCount(runtimeDiagnostics.getMoveThreadCount());
        status.setEnvironmentMode(runtimeDiagnostics.getEnvironmentMode());
        status.setParallelSolverCount(runtimeDiagnostics.getParallelSolverCount());
        status.setAvailableProcessors(runtimeDiagnostics.getAvailableProcessors());
    }

    private LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    private int countAssignedLessons(TimeTable solution) {
        int assigned = 0;
        for (Lesson lesson : solution.getLessons()) {
            if (lesson.getTimeslot() == null) {
                continue;
            }
            if (lesson.isOnline() || lesson.getRoom() != null) {
                assigned++;
            }
        }
        return assigned;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
