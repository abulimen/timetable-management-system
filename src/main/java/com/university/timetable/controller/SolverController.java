package com.university.timetable.controller;

import com.university.timetable.dto.*;
import com.university.timetable.service.AuditLogService;
import com.university.timetable.service.ConstraintJustificationService;
import com.university.timetable.service.InfeasibilityChecker;
import com.university.timetable.service.SolverRunMetricsService;
import com.university.timetable.service.SolverBenchmarkService;
import com.university.timetable.service.SolverService;
import com.university.timetable.service.TimetableChangeTrackerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * SolverController - manages solver operations.
 * 
 * Based on design.md API Specification:
 * POST /api/v1/solver/solve - Start solver
 * GET /api/v1/solver/status - Get status
 * POST /api/v1/solver/terminate - Stop solver
 * GET /api/v1/solver/feasibility - Pre-solve check
 * GET /api/v1/solver/analysis - Constraint violations
 */
@RestController
@RequestMapping("/api/v1/solver")
@RequiredArgsConstructor
@Slf4j
public class SolverController {

    private final SolverService solverService;
    private final InfeasibilityChecker infeasibilityChecker;
    private final ConstraintJustificationService justificationService;
    private final AuditLogService auditLogService;
    private final SolverRunMetricsService solverRunMetricsService;
    private final SolverBenchmarkService solverBenchmarkService;
    private final TimetableChangeTrackerService timetableChangeTrackerService;
    private final SolverRuntimeDiagnosticsDTO runtimeDiagnostics;

    @Value("${solver.feasibility.run-on-dirty-only:false}")
    private boolean runFeasibilityOnDirtyOnly;

    /**
     * POST /api/v1/solver/solve
     * Body: {"mode": "FULL_REPLAN"} or {"mode": "STABILITY"}
     * 
     * AUTOMATICALLY CHECKS FEASIBILITY FIRST.
     * If blocking issues are found, solver will NOT start and will return error.
     */
    @PostMapping("/solve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> startSolving(@RequestBody(required = false) SolveRequestDTO request) {

        long requestStart = System.nanoTime();
        String mode = (request != null && request.getMode() != null)
                ? request.getMode()
                : "FULL_REPLAN";
        SolverProfile profile = SolverProfile.fromNullable(request != null ? request.getProfile() : null);
        boolean skipFeasibilityRequested = request != null && Boolean.TRUE.equals(request.getSkipFeasibility());
        boolean hasPendingChanges = timetableChangeTrackerService.getStatus().isPendingChanges();

        log.info("Solve requested with mode={}, profile={}, skipFeasibility={}, runOnDirtyOnly={}, pendingChanges={}",
                mode, profile, skipFeasibilityRequested, runFeasibilityOnDirtyOnly, hasPendingChanges);

        boolean shouldRunFeasibility = !skipFeasibilityRequested;
        if (runFeasibilityOnDirtyOnly && !hasPendingChanges) {
            shouldRunFeasibility = false;
        }

        int timeslotCountForAudit = 0;
        if (shouldRunFeasibility) {
            // STEP 1: Run feasibility check (this also ensures timeslots align with settings)
            long feasibilityStart = System.nanoTime();
            InfeasibilityReport feasibilityReport = infeasibilityChecker.checkFeasibility();
            long feasibilityMs = elapsedMs(feasibilityStart);
            log.info("Feasibility check request duration: {} ms", feasibilityMs);

            timeslotCountForAudit = feasibilityReport.getTimeslotCount();

            // STEP 2: Check for blocking issues
            if (!feasibilityReport.isFeasible()) {
                log.warn("Solver NOT started - {} blocking issues found", feasibilityReport.getBlockingCount());
                return ResponseEntity.badRequest().body(java.util.Map.of(
                        "error", "FEASIBILITY_FAILED",
                        "message", "Cannot start solver - blocking issues found. Fix the issues and try again.",
                        "blockingCount", feasibilityReport.getBlockingCount(),
                        "issues", feasibilityReport.getIssues()));
            }

            log.info("Feasibility check passed ({} slots, {} warnings). Starting solver...",
                    feasibilityReport.getTimeslotCount(), feasibilityReport.getWarningCount());
        } else {
            log.info("Skipping feasibility check before solve (skipFeasibility={}, runOnDirtyOnly={}, pendingChanges={})",
                    skipFeasibilityRequested, runFeasibilityOnDirtyOnly, hasPendingChanges);
        }

        // STEP 3: Start solver
        try {
            long solveStart = System.nanoTime();
            SolverStatusDTO status = solverService.startSolving(request);
            status.setProfile(profile.name());
            log.info("Solver start API call completed in {} ms (total request {} ms)",
                    elapsedMs(solveStart), elapsedMs(requestStart));

            // Audit logging
            auditLogService.logSchedulerAction(
                    "Solver started in " + mode + " mode with profile " + profile +
                            (timeslotCountForAudit > 0 ? (" and " + timeslotCountForAudit + " timeslots") : ""),
                    true);

            return ResponseEntity.ok(status);
        } catch (IllegalStateException e) {
            log.error("Cannot start solver", e);
            auditLogService.logSchedulerAction("Solver start failed: " + e.getMessage(), false);
            return ResponseEntity.badRequest()
                    .body(new SolverStatusDTO(null, "ERROR", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/solver/status
     * Response: {"state": "SOLVING", "score": "0hard/-100soft"}
     */
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SolverStatusDTO> getStatus() {
        SolverStatusDTO status = solverService.getStatus();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/change-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TimetableChangeStatusDTO> getChangeStatus() {
        return ResponseEntity.ok(timetableChangeTrackerService.getStatus());
    }

    @PostMapping("/editing/enable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> enableEditingMode(@RequestBody(required = false) Map<String, String> body) {
        String token = body != null ? body.get("confirmationToken") : null;
        if (!"ENABLE_EDITING_FULL_REPLAN_REQUIRED".equals(token)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CONFIRMATION_REQUIRED",
                    "message",
                    "confirmationToken='ENABLE_EDITING_FULL_REPLAN_REQUIRED' is required to enable editing mode."));
        }
        timetableChangeTrackerService.enableEditing(
                "Editing mode enabled by admin. Any data updates now require FULL_REPLAN before final use.");
        auditLogService.logSchedulerAction("Editing mode enabled", true);
        return ResponseEntity.ok(timetableChangeTrackerService.getStatus());
    }

    @PostMapping("/editing/disable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<TimetableChangeStatusDTO> disableEditingMode() {
        timetableChangeTrackerService.lockEditing("Editing mode disabled by admin.");
        auditLogService.logSchedulerAction("Editing mode disabled", true);
        return ResponseEntity.ok(timetableChangeTrackerService.getStatus());
    }

    /**
     * POST /api/v1/solver/terminate
     * Action: Early termination of the solver.
     */
    @PostMapping("/terminate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<SolverStatusDTO> terminate() {
        log.info("Termination requested");
        SolverStatusDTO status = solverService.terminate();

        // Audit logging
        auditLogService.logSchedulerAction(
                "Solver manually terminated. Final score: " + (status.getScore() != null ? status.getScore() : "N/A"),
                true);

        return ResponseEntity.ok(status);
    }

    @PostMapping("/resume")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> resume() {
        log.info("Resume requested");
        try {
            SolverStatusDTO status = solverService.resume();
            auditLogService.logSchedulerAction("Solver resumed from last saved progress.", true);
            return ResponseEntity.ok(status);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "RESUME_FAILED",
                    "message", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/solver/clear-timetable
     * Clears all current lesson assignments (timeslot/room) and unpins lessons.
     * Requires double confirmation tokens in request body.
     */
    @PostMapping("/clear-timetable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> clearCurrentTimetable(@RequestBody Map<String, String> body) {
        String firstToken = body != null ? body.get("confirmationToken") : null;
        String secondToken = body != null ? body.get("secondConfirmationToken") : null;

        if (!"CLEAR_TIMETABLE".equals(firstToken) || !"CONFIRM_CLEAR".equals(secondToken)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CONFIRMATION_REQUIRED",
                    "message",
                    "Double confirmation required: confirmationToken='CLEAR_TIMETABLE' and secondConfirmationToken='CONFIRM_CLEAR'"));
        }

        int cleared = solverService.clearCurrentTimetable();
        auditLogService.logSchedulerAction(
                "Cleared current timetable assignments for " + cleared + " lessons",
                true);

        return ResponseEntity.ok(Map.of(
                "status", "CLEARED",
                "message", "Current timetable assignments cleared successfully",
                "lessonsCleared", cleared));
    }

    /**
     * GET /api/v1/solver/feasibility
     * Pre-solve check to identify impossible constraints.
     * Run this BEFORE solving to catch obvious issues early.
     */
    @GetMapping("/feasibility")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InfeasibilityReport> checkFeasibility() {
        log.info("Running pre-solve feasibility check");
        InfeasibilityReport report = infeasibilityChecker.checkFeasibility();
        return ResponseEntity.ok(report);
    }

    /**
     * GET /api/v1/solver/analysis
     * Analyze current solution and show constraint violations.
     * Run this AFTER solving to understand why score is not 0hard.
     */
    @GetMapping("/analysis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoreAnalysisDTO> getScoreAnalysis() {
        log.info("Analyzing current solution for constraint violations");
        ScoreAnalysisDTO analysis = justificationService.analyzeCurrentSolution();
        return ResponseEntity.ok(analysis);
    }

    /**
     * GET /api/v1/solver/metrics
     * Returns run-history metrics including p50/p95 durations.
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> getRunMetrics(
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(solverRunMetricsService.getMetrics(mode, limit));
    }

    @GetMapping("/runtime")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SolverRuntimeDiagnosticsDTO> getRuntimeDiagnostics() {
        return ResponseEntity.ok(runtimeDiagnostics);
    }

    @PostMapping("/benchmark")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<SolverBenchmarkResultDTO> runBenchmark(
            @RequestBody(required = false) SolverBenchmarkRequestDTO request) {
        SolverBenchmarkRequestDTO effective = request == null ? new SolverBenchmarkRequestDTO() : request;
        return ResponseEntity.ok(solverBenchmarkService.runBenchmark(effective));
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
