package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.dto.SolverStatusDTO;
import com.university.timetable.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolverStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    private final TimeslotService timeslotService;
    private final SolutionSaver solutionSaver;

    private static final Long PROBLEM_ID = 1L;
    private String currentJobId;

    @PostConstruct
    public void init() {
        log.info("SolverService initialized with SolverManager: {}", solverManager);
    }

    /**
     * Start the solver with specified mode.
     */
    public SolverStatusDTO startSolving(String mode) {
        log.info("Starting solver in {} mode", mode);
        
        // Ensure timeslots exist
        if (!timeslotService.hasTimeslots()) {
            timeslotService.generateTimeslots();
        }
        
        // Load the problem
        TimeTable problem = loadProblem();
        
        log.info("Loaded problem: {} lessons, {} timeslots, {} rooms",
            problem.getLessons().size(),
            problem.getTimeslots().size(),
            problem.getRooms().size());
        
        if (problem.getLessons().isEmpty()) {
            throw new IllegalStateException("No lessons to schedule. Import data first.");
        }
        if (problem.getTimeslots().isEmpty()) {
            throw new IllegalStateException("No timeslots available.");
        }
        if (problem.getRooms().isEmpty()) {
            throw new IllegalStateException("No rooms available.");
        }
        
        // Apply stability mode if requested
        if ("STABILITY".equalsIgnoreCase(mode)) {
            prepareStabilityMode(problem);
        }
        
        currentJobId = UUID.randomUUID().toString();
        
        // Start async solving with consumer callback
        log.info("Starting async solver with problem ID: {}", PROBLEM_ID);
        solverManager.solveAndListen(PROBLEM_ID,
            id -> {
                log.info("Problem factory called for ID: {}", id);
                return problem;
            },
            bestSolution -> {
                log.info("New best solution found with score: {}", bestSolution.getScore());
                try {
                    solutionSaver.saveSolution(bestSolution);
                } catch (Exception e) {
                    log.error("Failed to save solution: {}", e.getMessage(), e);
                }
            },
            (problemId, exception) -> {
                log.error("Solver exception for problem {}: {}", problemId, exception.getMessage(), exception);
            });
        
        log.info("Solver started with job ID: {}", currentJobId);
        return new SolverStatusDTO(currentJobId, "SOLVING", "N/A");
    }

    /**
     * Get current solver status.
     */
    public SolverStatusDTO getStatus() {
        SolverStatus status = solverManager.getSolverStatus(PROBLEM_ID);
        return new SolverStatusDTO(currentJobId, status.name(), "N/A");
    }

    /**
     * Terminate solver early.
     */
    public SolverStatusDTO terminate() {
        log.info("Terminating solver early");
        solverManager.terminateEarly(PROBLEM_ID);
        return new SolverStatusDTO(currentJobId, "TERMINATED", "Final solution saved");
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
        
        log.info("Loaded: {} lessons, {} timeslots, {} rooms, {} lecturers, {} groups",
            lessons.size(), timeslots.size(), rooms.size(), lecturers.size(), studentGroups.size());
        
        return new TimeTable(lessons, timeslots, rooms, lecturers, studentGroups);
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
}
