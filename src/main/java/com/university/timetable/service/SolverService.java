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
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SolverService - manages asynchronous solving with Timefold's
 * SolverManager.
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
    private final ForwardCheckingConstructionService forwardCheckingConstructionService;

    @Value("${solver.persistence.checkpoint-enabled:false}")
    private boolean checkpointEnabledDefault;

    @Value("${solver.persistence.checkpoint-min-interval-ms:120000}")
    private long checkpointMinIntervalMsDefault;

    @Value("${solver.persistence.checkpoint-every-n-improvements:0}")
    private int checkpointEveryNImprovementsDefault;

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
    private volatile String currentStage = "IDLE";
    private volatile LocalDateTime currentStageStartedAt;
    private volatile Long stageOneDurationMs;
    private volatile Long stageTwoDurationMs;
    private volatile Long hardFeasibleReachedMs;
    private volatile String stageOneBestScore = "N/A";
    private volatile String stageTwoBestScore = "N/A";
    private volatile AdaptivePolicy activeAdaptivePolicy;
    private volatile AdaptivePolicy stageOneAdaptivePolicy;
    private volatile AdaptivePolicy stageTwoAdaptivePolicy;
    private volatile String lastAdaptiveTerminationReason;

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
    private final AtomicBoolean awaitingSecondStage = new AtomicBoolean(false);
    private final AtomicBoolean terminateRequested = new AtomicBoolean(false);
    private volatile HardSoftScore lastPersistedScore;
    private volatile TimeTable latestFeasibleBestSolution;
    private volatile TimeTable latestBestSolutionSnapshot;

    private enum DatasetBand {
        SMALL,
        MEDIUM,
        LARGE
    }

    private static final class AdaptivePolicy {
        private final SolverProfile profile;
        private final DatasetBand datasetBand;
        private final long maxRuntimeMs;
        private final long unimprovedMs;
        private final int acceptedCountLimit;

        private AdaptivePolicy(
                SolverProfile profile,
                DatasetBand datasetBand,
                long maxRuntimeMs,
                long unimprovedMs,
                int acceptedCountLimit) {
            this.profile = profile;
            this.datasetBand = datasetBand;
            this.maxRuntimeMs = maxRuntimeMs;
            this.unimprovedMs = unimprovedMs;
            this.acceptedCountLimit = acceptedCountLimit;
        }

        private String toSummary() {
            return "profile=" + profile
                    + ", band=" + datasetBand
                    + ", maxRuntimeMs=" + maxRuntimeMs
                    + ", unimprovedMs=" + unimprovedMs
                    + ", acceptedCountLimit=" + acceptedCountLimit;
        }
    }

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

        // Block solving if unavailability system is enabled but requests are still
        // open.
        if (constraintSettingsService.isUnavailabilitySystemEnabled() &&
                constraintSettingsService.isUnavailabilityRequestsOpen()) {
            throw new IllegalStateException(
                    "Cannot generate timetable while unavailability requests are still open. " +
                            "Please close the request period first.");
        }

        if (!runFinalized.get()) {
            finalizeRunIfNeeded("INTERRUPTED", "New solve run started before previous run finalized.");
        }

        // Set hard-only mode flag before solver is built
        boolean skipSoft = request != null && Boolean.TRUE.equals(request.getSkipSoftConstraints());
        com.university.timetable.solver.TimetableConstraintProvider.HARD_ONLY_MODE = skipSoft;
        if (skipSoft) {
            log.info("HARD-ONLY MODE: Soft constraints will be skipped");
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

        // Run forward-checking construction to produce a better initial solution
        // This gives Timefold a head start instead of starting from scratch
        long fcStart = System.nanoTime();
        problem = forwardCheckingConstructionService.construct(
                problem.getLessons(), problem.getTimeslots(), problem.getRooms());
        long fcMs = elapsedMs(fcStart);
        log.info("Forward-checking construction completed in {} ms", fcMs);

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
        return startSolvingFromSeed(request, problem);
    }

    private SolverStatusDTO startSolvingFromSeed(SolveRequestDTO request, TimeTable problem) {
        String mode = request != null ? request.getMode() : null;
        String normalizedMode = (mode == null || mode.isBlank()) ? "FULL_REPLAN" : mode.trim().toUpperCase();
        SolverProfile profile = SolverProfile.fromNullable(request != null ? request.getProfile() : null);
        Map<Long, String> lockedAssignmentBaseline = Map.of();

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
        latestBestSolutionSnapshot = problem;
        currentStage = "QUEUED";
        currentStageStartedAt = LocalDateTime.now();
        stageOneDurationMs = null;
        stageTwoDurationMs = null;
        hardFeasibleReachedMs = null;
        stageOneBestScore = "N/A";
        stageTwoBestScore = "N/A";
        activeAdaptivePolicy = null;
        stageOneAdaptivePolicy = null;
        stageTwoAdaptivePolicy = null;
        lastAdaptiveTerminationReason = null;
        awaitingSecondStage.set(false);
        terminateRequested.set(false);

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

        boolean useTwoStageQuality = shouldUseTwoStageQuality(normalizedMode, profile);
        log.info("Starting async solver with problem ID: {} (twoStageQuality={})", PROBLEM_ID, useTwoStageQuality);
        if (useTwoStageQuality) {
            awaitingSecondStage.set(true);
            currentStage = "STAGE_A_FEASIBILITY";
            currentStageStartedAt = LocalDateTime.now();
            stageOneAdaptivePolicy = resolveAdaptivePolicy(SolverProfile.BALANCED, currentLessonsCount, true);
            activeAdaptivePolicy = stageOneAdaptivePolicy;
            log.info("QUALITY profile two-stage run started: Stage A (BALANCED feasibility) -> Stage B (QUALITY)");
            SolverJob<TimeTable, Long> stageOneJob = startSolveJob(problem,
                    stageOneAdaptivePolicy, lockedAssignmentBaseline, "STAGE_A_FEASIBILITY");
            startTwoStageWatcher(stageOneJob, lockedAssignmentBaseline);
        } else {
            currentStage = "SINGLE_STAGE";
            currentStageStartedAt = LocalDateTime.now();
            activeAdaptivePolicy = resolveAdaptivePolicy(profile, currentLessonsCount, false);
            SolverJob<TimeTable, Long> job = startSolveJob(problem, activeAdaptivePolicy,
                    lockedAssignmentBaseline, "SINGLE_STAGE");
            startSingleStageWatcher(job);
        }

        log.info("Solver started with job ID: {}", currentJobId);
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

    private SolverJob<TimeTable, Long> startSolveJob(
            TimeTable problem,
            AdaptivePolicy adaptivePolicy,
            Map<Long, String> lockedAssignmentBaseline,
            String stageLabel) {
        AtomicBoolean fastFeasibleTerminationRequested = new AtomicBoolean(false);
        if (adaptivePolicy != null) {
            log.info("[{}] Applying adaptive runtime policy: {}", stageLabel, adaptivePolicy.toSummary());
        }
        return solverManager.solveAndListen(PROBLEM_ID,
                id -> {
                    log.info("Problem factory called for ID: {} [{}]", id, stageLabel);
                    return problem;
                },
                bestSolution -> onBestSolution(bestSolution, lockedAssignmentBaseline,
                        fastFeasibleTerminationRequested, stageLabel),
                (problemId, exception) -> {
                    String errorMessage = exception != null ? exception.getMessage() : "Unknown solver exception";
                    currentRunError = errorMessage;
                    log.error("Solver exception for problem {} [{}] after {} ms and {} improvements: {}",
                            problemId,
                            stageLabel,
                            System.currentTimeMillis() - solveStartedAtMs.get(),
                            bestSolutionCount.get(),
                            errorMessage,
                            exception);
                    finalizeRunIfNeeded("FAILED", errorMessage);
                });
    }

    private void onBestSolution(
            TimeTable bestSolution,
            Map<Long, String> lockedAssignmentBaseline,
            AtomicBoolean fastFeasibleTerminationRequested,
            String stageLabel) {
        long callbackNow = System.currentTimeMillis();
        long improvementIndex = bestSolutionCount.incrementAndGet();

        if (firstBestAtMs.get() == 0L) {
            firstBestAtMs.compareAndSet(0L, callbackNow);
        }
        long previousBest = lastBestAtMs.getAndSet(callbackNow);
        long solveElapsedMs = callbackNow - solveStartedAtMs.get();
        long sinceLastImprovementMs = previousBest == 0L ? solveElapsedMs : callbackNow - previousBest;

        HardSoftScore score = bestSolution.getScore();
        latestBestSolutionSnapshot = bestSolution;
        if (score != null) {
            currentBestHardScore = score.hardScore();
            currentBestSoftScore = score.softScore();

            // Update adaptive soft weight multiplier based on hard violation count
            // This makes the adaptive weighting system actually functional
            int hardViolations = Math.abs(score.hardScore());
            double ratio = Math.min(1.0, (double) hardViolations / 1000.0);
            double multiplier = 0.3 + (1.0 - ratio) * 0.7;
            com.university.timetable.solver.AdaptiveSolverListener.SOFT_WEIGHT_MULTIPLIER.set(multiplier);

            if (score.hardScore() >= 0) {
                feasibleBestCount.incrementAndGet();
                if (firstFeasibleAtMs.get() == 0L) {
                    firstFeasibleAtMs.compareAndSet(0L, callbackNow);
                    hardFeasibleReachedMs = solveElapsedMs;
                }
                latestFeasibleBestSolution = bestSolution;
                if (awaitingSecondStage.get() &&
                        fastFeasibleTerminationRequested.compareAndSet(false, true)) {
                    if (awaitingSecondStage.get()) {
                        log.info(
                                "{} reached first hard-feasible score ({}). Waiting for Stage A controller to terminate and hand off to Stage B.",
                                stageLabel, score);
                    } else {
                        log.info("{} reached first hard-feasible score ({}). Terminating current stage early.",
                                stageLabel, score);
                        solverManager.terminateEarly(PROBLEM_ID);
                    }
                }
            }
        }
        currentBestScore = String.valueOf(score);
        int assignedLessons = countAssignedLessons(bestSolution);

        if (improvementIndex == 1) {
            log.info("[{}] First best solution in {} ms | score={} | assignedLessons={}/{}",
                    stageLabel, solveElapsedMs, currentBestScore, assignedLessons, bestSolution.getLessons().size());
        } else if (improvementIndex <= 5 || improvementIndex % 10 == 0) {
            log.info("[{}] Best solution #{} at {} ms (+{} ms) | score={} | assignedLessons={}/{}",
                    stageLabel, improvementIndex, solveElapsedMs, sinceLastImprovementMs, currentBestScore,
                    assignedLessons, bestSolution.getLessons().size());
        } else {
            log.debug("[{}] Best solution #{} at {} ms (+{} ms) | score={} | assignedLessons={}/{}",
                    stageLabel, improvementIndex, solveElapsedMs, sinceLastImprovementMs, currentBestScore,
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
                log.debug("[{}] Checkpoint persisted best solution #{} in {} ms", stageLabel, improvementIndex,
                        saveMs);
            } catch (Exception e) {
                log.error("Failed to save solution: {}", e.getMessage(), e);
            }
        } else {
            if (score != null && score.hardScore() < 0) {
                log.debug("[{}] Skipped checkpoint for best solution #{} (hard-infeasible score={})",
                        stageLabel, improvementIndex, score);
            } else {
                log.debug("[{}] Skipped checkpoint for best solution #{} (disabled/throttled)",
                        stageLabel, improvementIndex);
            }
        }
    }

    private void startSingleStageWatcher(SolverJob<TimeTable, Long> job) {
        Thread watcher = new Thread(() -> {
            try {
                TimeTable finalBest = awaitJobWithAdaptivePolicy(job, activeAdaptivePolicy, "SINGLE_STAGE", false);
                captureFeasibleSnapshot(finalBest);
                stageOneBestScore = resolveBestScoreFromLatestFeasible();
                if (currentRunStartedAt != null) {
                    stageOneDurationMs = Math.max(0L,
                            System.currentTimeMillis() - toEpochMs(currentRunStartedAt));
                }
                completeRunFromCurrentState();
            } catch (Exception e) {
                if (!runFinalized.get()) {
                    String message = "Single-stage watcher failed: " + e.getMessage();
                    currentRunError = message;
                    log.error(message, e);
                    finalizeRunIfNeeded("FAILED", message);
                }
            }
        }, "solver-single-stage-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void startTwoStageWatcher(
            SolverJob<TimeTable, Long> stageOneJob,
            Map<Long, String> lockedAssignmentBaseline) {
        Thread watcher = new Thread(() -> {
            try {
                TimeTable stageOneBest = awaitJobWithAdaptivePolicy(
                        stageOneJob,
                        stageOneAdaptivePolicy,
                        "STAGE_A_FEASIBILITY",
                        true);
                captureFeasibleSnapshot(stageOneBest);
                TimeTable stageOneFinal = latestFeasibleBestSolution;
                if (stageOneFinal == null) {
                    HardSoftScore stageOneBestScoreCandidate = stageOneBest != null ? stageOneBest.getScore() : null;
                    if (stageOneBestScoreCandidate != null && stageOneBestScoreCandidate.hardScore() >= 0) {
                        stageOneFinal = stageOneBest;
                        latestFeasibleBestSolution = stageOneBest;
                        currentBestScore = stageOneBestScoreCandidate.toString();
                        currentBestHardScore = stageOneBestScoreCandidate.hardScore();
                        currentBestSoftScore = stageOneBestScoreCandidate.softScore();
                    }
                }
                stageOneDurationMs = currentStageStartedAt == null
                        ? null
                        : Math.max(0L, System.currentTimeMillis() - toEpochMs(currentStageStartedAt));
                stageOneBestScore = resolveBestScoreFromLatestFeasible();

                if (terminateRequested.get() || runFinalized.get()) {
                    awaitingSecondStage.set(false);
                    return;
                }

                HardSoftScore stageOneScore = stageOneFinal != null ? stageOneFinal.getScore() : null;
                if (stageOneScore == null || stageOneScore.hardScore() < 0) {
                    awaitingSecondStage.set(false);
                    currentRunError = "Two-stage solve failed to reach hard-feasible timetable in Stage A.";
                    finalizeRunIfNeeded("FAILED", currentRunError);
                    return;
                }

                currentStage = "STAGE_B_QUALITY_OPTIMIZATION";
                currentStageStartedAt = LocalDateTime.now();
                stageTwoAdaptivePolicy = resolveAdaptivePolicy(SolverProfile.QUALITY, currentLessonsCount, true);
                activeAdaptivePolicy = stageTwoAdaptivePolicy;
                lastAdaptiveTerminationReason = null;
                log.info("Two-stage solve entering Stage B with seed score={}", stageOneScore);
                awaitingSecondStage.set(false);
                SolverJob<TimeTable, Long> stageTwoJob = startSolveJob(stageOneFinal,
                        stageTwoAdaptivePolicy, lockedAssignmentBaseline, "STAGE_B_QUALITY_OPTIMIZATION");
                TimeTable stageTwoBest = awaitJobWithAdaptivePolicy(
                        stageTwoJob,
                        stageTwoAdaptivePolicy,
                        "STAGE_B_QUALITY_OPTIMIZATION",
                        false);
                captureFeasibleSnapshot(stageTwoBest);
                stageTwoDurationMs = currentStageStartedAt == null
                        ? null
                        : Math.max(0L, System.currentTimeMillis() - toEpochMs(currentStageStartedAt));
                stageTwoBestScore = resolveBestScoreFromLatestFeasible();

                if (!runFinalized.get()) {
                    completeRunFromCurrentState();
                }
            } catch (Exception e) {
                awaitingSecondStage.set(false);
                if (!runFinalized.get()) {
                    String message = "Two-stage watcher failed: " + e.getMessage();
                    currentRunError = message;
                    log.error(message, e);
                    finalizeRunIfNeeded("FAILED", message);
                }
            }
        }, "solver-two-stage-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private TimeTable awaitJobFinalBest(SolverJob<TimeTable, Long> job) throws InterruptedException {
        try {
            return job.getFinalBestSolution();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Solver job failed while waiting for completion.", e);
        }
    }

    private TimeTable awaitJobWithAdaptivePolicy(
            SolverJob<TimeTable, Long> job,
            AdaptivePolicy policy,
            String stageLabel,
            boolean terminateOnFirstFeasible) throws InterruptedException {
        AtomicBoolean terminationRequestedForStage = new AtomicBoolean(false);
        long stageStartedAtMs = System.currentTimeMillis();
        while (job.getSolverStatus() == SolverStatus.SOLVING_ACTIVE && !runFinalized.get()) {
            if (!terminationRequestedForStage.get() && terminateRequested.get()) {
                terminationRequestedForStage.set(true);
                job.terminateEarly();
                break;
            }

            if (!terminationRequestedForStage.get() && terminateOnFirstFeasible && hasHardFeasibleSnapshot()) {
                terminationRequestedForStage.set(true);
                lastAdaptiveTerminationReason = stageLabel + ": first hard-feasible snapshot reached";
                log.info("{} controller terminating after first hard-feasible snapshot.", stageLabel);
                job.terminateEarly();
            }

            String policyTerminationReason = !terminationRequestedForStage.get()
                    ? evaluatePolicyTermination(policy, stageStartedAtMs)
                    : null;
            if (policyTerminationReason != null) {
                terminationRequestedForStage.set(true);
                lastAdaptiveTerminationReason = policyTerminationReason;
                log.info("{} controller terminating by adaptive policy: {}", stageLabel, policyTerminationReason);
                job.terminateEarly();
            }

            Thread.sleep(200L);
        }

        return awaitJobFinalBest(job);
    }

    private String evaluatePolicyTermination(AdaptivePolicy policy, long stageStartedAtMs) {
        if (policy == null || terminateRequested.get()) {
            return null;
        }
        long nowMs = System.currentTimeMillis();
        long stageElapsedMs = Math.max(0L, nowMs - stageStartedAtMs);

        // Hard max-runtime always applies (failsafe)
        if (policy.maxRuntimeMs > 0 && stageElapsedMs >= policy.maxRuntimeMs) {
            return "Reached stage runtime limit (" + policy.maxRuntimeMs + " ms)";
        }

        // No-improvement check: ONLY after at least one best-solution callback.
        // During construction heuristic, the solver hasn't reported any best solution
        // yet,
        // so we must not count that time as "no improvement" — the solver is still
        // building the initial solution. Without this guard, large datasets (1000+
        // lessons)
        // get killed during construction because CH takes longer than unimprovedMs.
        long bestCount = bestSolutionCount.get();
        if (bestCount == 0) {
            // Construction heuristic still running — let it finish
            return null;
        }

        long sinceLastImprovementMs;
        long lastBest = lastBestAtMs.get();
        if (lastBest > 0L) {
            sinceLastImprovementMs = nowMs - lastBest;
        } else {
            // Fallback: should not happen when bestCount > 0, but safe guard
            sinceLastImprovementMs = stageElapsedMs;
        }

        if (policy.unimprovedMs > 0 && sinceLastImprovementMs >= policy.unimprovedMs) {
            return "No score improvement for " + sinceLastImprovementMs + " ms (limit=" + policy.unimprovedMs + " ms)";
        }
        return null;
    }

    private boolean hasHardFeasibleSnapshot() {
        TimeTable feasible = latestFeasibleBestSolution;
        HardSoftScore feasibleScore = feasible != null ? feasible.getScore() : null;
        return feasibleScore != null && feasibleScore.hardScore() >= 0;
    }

    private void captureFeasibleSnapshot(TimeTable candidate) {
        HardSoftScore score = candidate != null ? candidate.getScore() : null;
        if (score == null || score.hardScore() < 0) {
            return;
        }
        latestFeasibleBestSolution = candidate;
        currentBestScore = score.toString();
        currentBestHardScore = score.hardScore();
        currentBestSoftScore = score.softScore();
    }

    private String resolveBestScoreFromLatestFeasible() {
        if (latestFeasibleBestSolution != null && latestFeasibleBestSolution.getScore() != null) {
            return latestFeasibleBestSolution.getScore().toString();
        }
        return currentBestScore;
    }

    private boolean shouldUseTwoStageQuality(String mode, SolverProfile profile) {
        return "FULL_REPLAN".equalsIgnoreCase(mode) && profile == SolverProfile.QUALITY;
    }

    private AdaptivePolicy resolveAdaptivePolicy(SolverProfile profile, int lessonCount, boolean isTwoStageRun) {
        int boundedLessons = Math.max(0, lessonCount);
        DatasetBand band = classifyDatasetBand(boundedLessons);
        boolean adaptiveLimitsEnabled = constraintSettingsService.isSolverAdaptiveLimitsEnabled();
        boolean adaptiveSearchBreadthEnabled = constraintSettingsService.isSolverAdaptiveSearchBreadthEnabled();
        boolean runtimeLimitEnabled = constraintSettingsService.isSolverRuntimeLimitEnabled();

        int baseMinutes = Math.max(1, constraintSettingsService.getSolverMinutesSpentLimit());
        int baseUnimprovedSeconds = Math.max(5, constraintSettingsService.getSolverUnimprovedSecondsSpentLimit());
        int baseAcceptedCount = Math.max(1, constraintSettingsService.getSolverForagerAcceptedCountLimit());

        double runtimeFactor;
        double unimprovedFactor;
        double acceptedFactor;
        switch (profile) {
            case QUALITY -> {
                runtimeFactor = switch (band) {
                    case SMALL -> 0.95;
                    case MEDIUM -> 1.00;
                    case LARGE -> 1.00;
                };
                unimprovedFactor = switch (band) {
                    case SMALL -> 1.10;
                    case MEDIUM -> 1.20;
                    case LARGE -> 1.35;
                };
                acceptedFactor = switch (band) {
                    case SMALL -> 1.10;
                    case MEDIUM -> 1.25;
                    case LARGE -> 1.40;
                };
            }
            case BALANCED -> {
                runtimeFactor = switch (band) {
                    case SMALL -> 0.70;
                    case MEDIUM -> 0.90;
                    case LARGE -> 1.00;
                };
                unimprovedFactor = switch (band) {
                    case SMALL -> 0.75;
                    case MEDIUM -> 0.90;
                    case LARGE -> 1.00;
                };
                acceptedFactor = switch (band) {
                    case SMALL -> 0.85;
                    case MEDIUM -> 1.00;
                    case LARGE -> 1.10;
                };
            }
            default -> throw new IllegalStateException("Unexpected profile: " + profile);
        }

        if (isTwoStageRun && profile == SolverProfile.BALANCED) {
            runtimeFactor = Math.min(runtimeFactor, 0.35);
            unimprovedFactor = Math.min(unimprovedFactor, 0.35);
            acceptedFactor = Math.min(acceptedFactor, 0.65);
        }

        long maxRuntimeMs;
        long unimprovedMs;
        long rawUnimprovedMs = Math.round(baseUnimprovedSeconds * 1_000.0 * unimprovedFactor);
        long effectiveFloorMs = 0L;
        if (adaptiveLimitsEnabled) {
            maxRuntimeMs = runtimeLimitEnabled
                    ? Math.max(30_000L, Math.round(baseMinutes * 60_000.0 * runtimeFactor))
                    : 0L;

            long bandFloorUnimprovedMs = switch (band) {
                case SMALL -> 60_000L;
                case MEDIUM -> 180_000L;
                case LARGE -> 360_000L;
            };
            long twoStageFloorMs = (isTwoStageRun && profile == SolverProfile.BALANCED) ? 240_000L : 0L;
            long configuredFloorMs = Math.max(30_000L,
                    constraintSettingsService.getInt("solver_adaptive_min_unimproved_seconds", 180) * 1_000L);
            effectiveFloorMs = Math.max(configuredFloorMs, Math.max(bandFloorUnimprovedMs, twoStageFloorMs));
            unimprovedMs = Math.max(effectiveFloorMs, rawUnimprovedMs);
        } else {
            maxRuntimeMs = runtimeLimitEnabled ? Math.max(30_000L, baseMinutes * 60_000L) : 0L;
            unimprovedMs = Math.max(5_000L, baseUnimprovedSeconds * 1_000L);
        }

        int acceptedCountLimit;
        if (adaptiveSearchBreadthEnabled) {
            acceptedCountLimit = Math.max(
                    1,
                    (int) Math.round(baseAcceptedCount * acceptedFactor));
        } else {
            acceptedCountLimit = Math.max(1, baseAcceptedCount);
        }

        log.info("Adaptive policy inputs: profile={}, band={}, twoStage={}, adaptiveLimitsEnabled={}, "
                + "adaptiveSearchBreadthEnabled={}, runtimeLimitEnabled={}, baseMinutes={}, baseUnimprovedSeconds={}, "
                + "runtimeFactor={}, unimprovedFactor={}, rawUnimprovedMs={}, floorMs={}, finalMaxRuntimeMs={}, "
                + "finalUnimprovedMs={}, acceptedCountLimit={}",
                profile, band, isTwoStageRun, adaptiveLimitsEnabled, adaptiveSearchBreadthEnabled, runtimeLimitEnabled,
                baseMinutes, baseUnimprovedSeconds, runtimeFactor, unimprovedFactor, rawUnimprovedMs,
                effectiveFloorMs, maxRuntimeMs, unimprovedMs, acceptedCountLimit);

        return new AdaptivePolicy(profile, band, maxRuntimeMs, unimprovedMs, acceptedCountLimit);
    }

    private DatasetBand classifyDatasetBand(int lessonCount) {
        int smallThreshold = Math.max(50,
                constraintSettingsService.getInt("solver_adaptive_small_dataset_threshold", 200));
        int largeThreshold = Math.max(smallThreshold + 1,
                constraintSettingsService.getInt("solver_adaptive_large_dataset_threshold", 800));
        if (lessonCount <= smallThreshold) {
            return DatasetBand.SMALL;
        }
        if (lessonCount >= largeThreshold) {
            return DatasetBand.LARGE;
        }
        return DatasetBand.MEDIUM;
    }

    private void completeRunFromCurrentState() {
        if (currentRunError == null && currentBestHardScore != null && currentBestHardScore < 0) {
            currentRunError = "Solver finished without a hard-feasible timetable. No invalid assignments were applied.";
        }
        String finalStatus = currentRunError == null ? "COMPLETED" : "FAILED";
        finalizeRunIfNeeded(finalStatus, currentRunError);
    }

    private long toEpochMs(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Get current solver status.
     */
    public SolverStatusDTO getStatus() {
        SolverStatus status = solverManager.getSolverStatus(PROBLEM_ID);
        boolean transitioningToStageTwo = status == SolverStatus.NOT_SOLVING
                && awaitingSecondStage.get()
                && !runFinalized.get();
        if (transitioningToStageTwo) {
            status = SolverStatus.SOLVING_ACTIVE;
        }
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
        terminateRequested.set(true);
        awaitingSecondStage.set(false);
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

    public SolverStatusDTO resume() {
        SolverStatus currentStatus = solverManager.getSolverStatus(PROBLEM_ID);
        if (currentStatus == SolverStatus.SOLVING_ACTIVE) {
            throw new IllegalStateException("Solver is already running.");
        }
        if (latestBestSolutionSnapshot == null) {
            throw new IllegalStateException("No saved solver progress is available to resume.");
        }

        SolveRequestDTO request = new SolveRequestDTO();
        request.setMode(currentMode);
        request.setProfile(currentProfile);
        request.setSkipFeasibility(true);
        return startSolvingFromSeed(request, latestBestSolutionSnapshot);
    }

    /**
     * Load the problem from database.
     */
    @Transactional(readOnly = true)
    public TimeTable loadProblem() {
        List<Lesson> lessons = lessonRepository.findAllWithCourseAndLecturer();
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
        awaitingSecondStage.set(false);
        if (hardFeasibleReachedMs == null && firstFeasibleAtMs.get() > 0L) {
            hardFeasibleReachedMs = Math.max(0L, firstFeasibleAtMs.get() - startedMs);
        }
        Long firstBestDelay = firstBestAtMs.get() > 0L ? Math.max(0L, firstBestAtMs.get() - startedMs) : null;

        if (("COMPLETED".equalsIgnoreCase(status)
                || "TERMINATED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status))
                && latestBestSolutionSnapshot != null) {
            TimeTable snapshotToPersist = latestFeasibleBestSolution != null
                    ? latestFeasibleBestSolution
                    : latestBestSolutionSnapshot;
            if (snapshotToPersist != null) {
                long saveStart = System.nanoTime();
                try {
                    solutionSaver.saveSolution(snapshotToPersist);
                    long saveMs = elapsedMs(saveStart);
                    persistedBestCount.incrementAndGet();
                    persistedBestTotalMs.addAndGet(saveMs);
                    lastPersistedAtMs.set(System.currentTimeMillis());
                    if (snapshotToPersist.getScore() != null) {
                        lastPersistedScore = snapshotToPersist.getScore();
                    }
                    log.info("Persisted solver progress snapshot in {} ms (status={}, hardScore={})",
                            saveMs, status, currentBestHardScore);
                } catch (Exception e) {
                    log.error("Failed to persist solver progress snapshot: {}", e.getMessage(), e);
                    if (reason == null || reason.isBlank()) {
                        reason = "Progress persistence failed: " + e.getMessage();
                    }
                }
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
                "Solver run finalized: runId={}, status={}, mode={}, stage={}, durationMs={}, firstBestMs={}, hardFeasibleMs={}, stageOneMs={}, stageTwoMs={}, improvements={}, persistenceCount={}, bestScore={}",
                currentJobId, status, currentMode, currentStage, durationMs, firstBestDelay, hardFeasibleReachedMs,
                stageOneDurationMs, stageTwoDurationMs, bestSolutionCount.get(),
                persistenceCount, currentBestScore);

        if ("COMPLETED".equals(status)) {
            timetableChangeTrackerService.clear("Timetable solved (" + currentMode + ")");
            timetableChangeTrackerService
                    .lockEditing("Timetable generated. Editing is now locked until admin enables editing mode.");
        }
        currentStage = "IDLE";
        currentStageStartedAt = null;
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
        status.setStage(currentStage);
        status.setStageStartedAt(currentStageStartedAt);
        status.setStageOneDurationMs(stageOneDurationMs);
        status.setStageTwoDurationMs(stageTwoDurationMs);
        status.setHardFeasibleReachedMs(hardFeasibleReachedMs);
        status.setStageOneBestScore(stageOneBestScore);
        status.setStageTwoBestScore(stageTwoBestScore);
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
        boolean adaptiveLimitsEnabled = constraintSettingsService.isSolverAdaptiveLimitsEnabled();
        boolean adaptiveSearchBreadthEnabled = constraintSettingsService.isSolverAdaptiveSearchBreadthEnabled();
        status.setAdaptiveLimitsEnabled(adaptiveLimitsEnabled);
        status.setAdaptiveSearchBreadthEnabled(adaptiveSearchBreadthEnabled);
        AdaptivePolicy policy = activeAdaptivePolicy;
        status.setAdaptiveMaxRuntimeMs(policy != null && policy.maxRuntimeMs > 0 ? policy.maxRuntimeMs : null);
        status.setAdaptiveUnimprovedMs(policy != null ? policy.unimprovedMs : null);
        status.setAdaptiveAcceptedCountLimit(policy != null ? policy.acceptedCountLimit : null);
        status.setAdaptiveDatasetBand(adaptiveLimitsEnabled && policy != null ? policy.datasetBand.name() : null);
        status.setAdaptiveTerminationReason(lastAdaptiveTerminationReason);
        status.setResumeAvailable(!isSolving() && latestBestSolutionSnapshot != null);
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
