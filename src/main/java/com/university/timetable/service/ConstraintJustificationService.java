package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatch;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service that uses Timefold's SolutionManager to explain constraint
 * violations.
 * Provides detailed breakdown of why a solution has violations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConstraintJustificationService {

    private final SolutionManager<TimeTable, HardSoftScore> solutionManager;
    private final SolverService solverService;

    /**
     * Analyze the current solution and return detailed constraint breakdown.
     */
    public ScoreAnalysisDTO analyzeCurrentSolution() {
        log.info("Analyzing current solution for constraint violations...");

        // Load current state
        TimeTable solution = solverService.loadProblem();

        return analyzeSolution(solution);
    }

    /**
     * Analyze a specific solution.
     */
    public ScoreAnalysisDTO analyzeSolution(TimeTable solution) {
        // Calculate score with explanation
        ScoreExplanation<TimeTable, HardSoftScore> explanation = solutionManager.explain(solution);
        HardSoftScore score = explanation.getScore();

        ScoreAnalysisDTO analysis = new ScoreAnalysisDTO();
        analysis.setScore(score.toString());
        analysis.setFeasible(score.hardScore() >= 0);
        analysis.setHardViolationCount(Math.abs(score.hardScore()));
        analysis.setSoftPenalty(Math.abs(score.softScore()));

        // Get constraint match totals for breakdown
        Map<String, ConstraintMatchTotal<HardSoftScore>> constraintMatchTotals = explanation
                .getConstraintMatchTotalMap();

        for (Map.Entry<String, ConstraintMatchTotal<HardSoftScore>> entry : constraintMatchTotals.entrySet()) {
            ConstraintMatchTotal<HardSoftScore> total = entry.getValue();
            HardSoftScore constraintScore = total.getScore();

            if (constraintScore.equals(HardSoftScore.ZERO)) {
                continue; // Skip constraints with no violations
            }

            ConstraintViolationDTO violation = new ConstraintViolationDTO();
            violation.setConstraintName(total.getConstraintName());
            violation.setMatchCount(total.getConstraintMatchCount());
            violation.setScoreImpact(constraintScore.toString());

            // Add details for each match (limited)
            for (ConstraintMatch<HardSoftScore> match : total.getConstraintMatchSet()) {
                ViolationDetailDTO detail = createViolationDetail(match);
                if (detail != null) {
                    violation.addDetail(detail);
                }
            }

            // Categorize as hard or soft
            if (constraintScore.hardScore() < 0) {
                analysis.getHardViolations().add(violation);
            } else if (constraintScore.softScore() < 0) {
                analysis.getSoftViolations().add(violation);
            }
        }

        // Sort by impact (most severe first)
        analysis.getHardViolations().sort((a, b) -> Integer.compare(b.getMatchCount(), a.getMatchCount()));
        analysis.getSoftViolations().sort((a, b) -> Integer.compare(b.getMatchCount(), a.getMatchCount()));

        log.info("Analysis complete: {} hard violations, {} soft penalty",
                analysis.getHardViolationCount(), analysis.getSoftPenalty());

        return analysis;
    }

    /**
     * Create a human-readable detail from a constraint match.
     */
    private ViolationDetailDTO createViolationDetail(ConstraintMatch<HardSoftScore> match) {
        List<Object> justificationList = match.getIndictedObjectList();
        if (justificationList.isEmpty()) {
            return null;
        }

        String constraintName = match.getConstraintName();
        StringBuilder description = new StringBuilder();
        String recommendation = "";
        String entity = "";

        // Build description based on constraint type
        for (Object obj : justificationList) {
            if (obj instanceof Lesson lesson) {
                entity = lesson.getDisplayName();

                switch (constraintName) {
                    case "Room capacity overflow" -> {
                        int students = lesson.getTotalStudentCount();
                        int capacity = lesson.getRoom() != null ? lesson.getRoom().getCapacity() : 0;
                        description.append(String.format("%d students in %s (capacity: %d)",
                                students, lesson.getRoom() != null ? lesson.getRoom().getName() : "?", capacity));
                        recommendation = "Assign to larger room or split group";
                    }
                    case "Room conflict" -> {
                        description.append(String.format("%s double-booked at %s",
                                lesson.getRoom() != null ? lesson.getRoom().getName() : "?",
                                lesson.getTimeslot() != null ? lesson.getTimeslot().toString() : "?"));
                        recommendation = "Reschedule one of the conflicting lessons";
                    }
                    case "Lecturer conflict" -> {
                        description.append(String.format("Lecturer %s has overlapping lessons",
                                lesson.getLecturer() != null ? lesson.getLecturer().getName() : "?"));
                        recommendation = "Change timeslot or assign different lecturer";
                    }
                    case "Student group conflict" -> {
                        description.append(String.format("Student group has overlapping lessons at %s",
                                lesson.getTimeslot() != null ? lesson.getTimeslot().toString() : "?"));
                        recommendation = "Reschedule to non-overlapping timeslot";
                    }
                    case "Room feature required" -> {
                        description.append(String.format("%s lacks required features for %s",
                                lesson.getRoom() != null ? lesson.getRoom().getName() : "?",
                                lesson.getCourse() != null ? lesson.getCourse().getCode() : "?"));
                        recommendation = "Assign to room with required features";
                    }
                    case "Lunch break overlap" -> {
                        description.append(String.format("Lesson scheduled during lunch break (%s)",
                                lesson.getTimeslot() != null ? lesson.getTimeslot().getStartTime() : "?"));
                        recommendation = "Move to before or after lunch period";
                    }
                    case "Lesson exceeds end time" -> {
                        description.append(String.format("Lesson ends at %s, exceeds limit",
                                lesson.getEndTime()));
                        recommendation = "Reschedule to earlier timeslot";
                    }
                    default -> {
                        description.append(String.format("%s at %s in %s",
                                entity,
                                lesson.getTimeslot() != null ? lesson.getTimeslot().toString() : "?",
                                lesson.getRoom() != null ? lesson.getRoom().getName() : "?"));
                        recommendation = "Review and adjust assignment";
                    }
                }
            }
        }

        if (description.isEmpty()) {
            description.append("Constraint violation detected");
            recommendation = "Review the affected entities";
        }

        return new ViolationDetailDTO(entity, description.toString(), recommendation);
    }
}
