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
import java.util.List;
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

    @Value("${solver.persistence.min-interval-ms:5000}")
    private long persistenceMinIntervalMs;

    @Value("${solver.persistence.every-n-improvements:10}")
    private int persistenceEveryNImprovements;

    private static final Long PROBLEM_ID = 1L;

    private String currentJobId;
    private volatile String currentMode = "FULL_REPLAN";
    private volatile String currentBestScore = "N/A";
    private volatile Integer currentBestHardScore;
    private volatile Integer currentBestSoftScore;
    private volatile String currentRunError;
    private volatile int currentLessonsCount;
    private volatile int currentTimeslotsCount;
    private volatile int currentRoomsCount;
    private volatile LocalDateTime currentRunStartedAt;
    private volatile Long lastRunDurationMs;

    private final AtomicLong solveStartedAtMs = new AtomicLong(0L);
    private final AtomicLong bestSolutionCount = new AtomicLong(0L);
    private final AtomicLong firstBestAtMs = new AtomicLong(0L);
    private final AtomicLong lastBestAtMs = new AtomicLong(0L);
    private final AtomicLong lastPersistedAtMs = new AtomicLong(0L);
    private final AtomicLong persistedBestCount = new AtomicLong(0L);
    private final AtomicLong persistedBestTotalMs = new AtomicLong(0L);
    private final AtomicBoolean runFinalized = new AtomicBoolean(true);
    private volatile HardSoftScore lastPersistedScore;

    @PostConstruct
    public void init() {
        log.info("SolverService initialized with SolverManager: {}", solverManager);
    }

    /**
     * Start the solver with specified mode.
     */
    public SolverStatusDTO startSolving(String mode) {
        long startNanos = System.nanoTime();
        String normalizedMode = (mode == null || mode.isBlank()) ? "FULL_REPLAN" : mode.trim().toUpperCase();
        log.info("Starting solver in {} mode", normalizedMode);

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

        currentJobId = UUID.randomUUID().toString();
        currentMode = normalizedMode;
        currentRunStartedAt = LocalDateTime.now();
        currentRunError = null;
        currentBestScore = "N/A";
        currentBestHardScore = null;
        currentBestSoftScore = null;
        lastRunDurationMs = null;

        long nowMs = System.currentTimeMillis();
        solveStartedAtMs.set(nowMs);
        bestSolutionCount.set(0L);
        firstBestAtMs.set(0L);
        lastBestAtMs.set(0L);
        lastPersistedAtMs.set(0L);
        persistedBestCount.set(0L);
        persistedBestTotalMs.set(0L);
        lastPersistedScore = null;
        runFinalized.set(false);

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
                    }
                    currentBestScore = String.valueOf(score);
                    int assignedLessons = countAssignedLessons(bestSolution);

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

                    if (shouldPersistBestSolution(improvementIndex, score, callbackNow)) {
                        long saveStart = System.nanoTime();
                        try {
                            solutionSaver.saveSolution(bestSolution);
                            long saveMs = elapsedMs(saveStart);
                            persistedBestCount.incrementAndGet();
                            persistedBestTotalMs.addAndGet(saveMs);
                            lastPersistedAtMs.set(callbackNow);
                            lastPersistedScore = score;
                            log.debug("Persisted best solution #{} in {} ms", improvementIndex, saveMs);
                        } catch (Exception e) {
                            log.error("Failed to save solution: {}", e.getMessage(), e);
                        }
                    } else {
                        log.debug("Skipped persistence for best solution #{} (throttled)", improvementIndex);
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
        return new SolverStatusDTO(currentJobId, "SOLVING", currentBestScore, null);
    }

    /**
     * Get current solver status.
     */
    public SolverStatusDTO getStatus() {
        SolverStatus status = solverManager.getSolverStatus(PROBLEM_ID);
        if (status == SolverStatus.NOT_SOLVING) {
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
        return new SolverStatusDTO(currentJobId, status.name(), score, durationMs);
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
        return new SolverStatusDTO(currentJobId, "TERMINATED", currentBestScore, elapsed);
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

    private boolean shouldPersistBestSolution(long improvementIndex, HardSoftScore score, long nowMs) {
        if (improvementIndex == 1) {
            return true;
        }
        if (score != null && lastPersistedScore != null && score.hardScore() > lastPersistedScore.hardScore()) {
            return true;
        }
        if (score != null && lastPersistedScore == null) {
            return true;
        }
        if (persistenceEveryNImprovements > 0 && improvementIndex % persistenceEveryNImprovements == 0) {
            return true;
        }
        return nowMs - lastPersistedAtMs.get() >= Math.max(0L, persistenceMinIntervalMs);
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
        long persistenceCount = persistedBestCount.get();
        Long avgPersistence = persistenceCount > 0 ? persistedBestTotalMs.get() / persistenceCount : null;

        SolverRunMetric runMetric = new SolverRunMetric();
        runMetric.setRunId(currentJobId);
        runMetric.setMode(currentMode);
        runMetric.setStatus(status);
        runMetric.setBestScore(currentBestScore);
        runMetric.setBestHardScore(currentBestHardScore);
        runMetric.setBestSoftScore(currentBestSoftScore);
        runMetric.setLessonsCount(currentLessonsCount);
        runMetric.setTimeslotsCount(currentTimeslotsCount);
        runMetric.setRoomsCount(currentRoomsCount);
        runMetric.setImprovementCount(bestSolutionCount.get());
        runMetric.setPersistenceCount(persistenceCount);
        runMetric.setAvgPersistenceMs(avgPersistence);
        runMetric.setDurationMs(durationMs);
        runMetric.setTimeToFirstBestMs(firstBestDelay);
        runMetric.setErrorMessage(reason);
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
