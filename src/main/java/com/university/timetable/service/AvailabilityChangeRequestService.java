package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.domain.AvailabilityChangeRequest.AvailabilityStatus;
import com.university.timetable.domain.AvailabilityChangeRequest.RequestStatus;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing availability change requests.
 * Implements the approval workflow per USER_AUTH_REQUIREMENTS.md Section 3.4.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AvailabilityChangeRequestService {

    private final AvailabilityChangeRequestRepository requestRepository;
    private final LecturerRepository lecturerRepository;
    private final LessonRepository lessonRepository;
    private final ConstraintSettingsService settingsService;

    /**
     * Create a new availability change request.
     * Calculates affected lessons and validates the request.
     */
    public AvailabilityChangeRequest createRequest(
            User requestedBy,
            Lecturer lecturer,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            AvailabilityStatus newStatus,
            String reason) {

        // Validate reason length
        if (reason == null || reason.trim().length() < 20) {
            throw new IllegalArgumentException("Reason must be at least 20 characters");
        }

        // Find affected lessons
        List<Lesson> affectedLessons = findAffectedLessons(lecturer, dayOfWeek, startTime, endTime);
        String affectedLessonIds = affectedLessons.stream()
                .map(l -> l.getId().toString())
                .collect(Collectors.joining(","));

        // Create the request
        AvailabilityChangeRequest request = AvailabilityChangeRequest.builder()
                .lecturer(lecturer)
                .requestedBy(requestedBy)
                .dayOfWeek(dayOfWeek)
                .startTime(startTime)
                .endTime(endTime)
                .newStatus(newStatus)
                .reason(reason.trim())
                .status(RequestStatus.PENDING)
                .affectedLessonsCount(affectedLessons.size())
                .affectedLessonIds(affectedLessonIds.isEmpty() ? null : affectedLessonIds)
                .build();

        log.info("Created availability change request for lecturer {}: {} {}-{} -> {}, {} affected lessons",
                lecturer.getName(), dayOfWeek, startTime, endTime, newStatus, affectedLessons.size());

        return requestRepository.save(request);
    }

    /**
     * Approve a change request and apply the availability change.
     */
    public AvailabilityChangeRequest approveRequest(Long requestId, User reviewer, String reviewNotes) {
        AvailabilityChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending: " + request.getStatus());
        }

        // Update request status
        request.setStatus(RequestStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNotes(reviewNotes);

        // Apply the availability change
        applyAvailabilityChange(request);

        log.info("Approved availability change request {} by {}", requestId, reviewer.getEmail());

        return requestRepository.save(request);
    }

    /**
     * Reject a change request.
     */
    public AvailabilityChangeRequest rejectRequest(Long requestId, User reviewer, String reviewNotes) {
        AvailabilityChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending: " + request.getStatus());
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNotes(reviewNotes);

        log.info("Rejected availability change request {} by {}: {}", requestId, reviewer.getEmail(), reviewNotes);

        return requestRepository.save(request);
    }

    /**
     * Return a request for more information.
     */
    public AvailabilityChangeRequest returnRequest(Long requestId, User reviewer, String reviewNotes) {
        AvailabilityChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending: " + request.getStatus());
        }

        request.setStatus(RequestStatus.RETURNED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNotes(reviewNotes);

        log.info("Returned availability change request {} for more info by {}", requestId, reviewer.getEmail());

        return requestRepository.save(request);
    }

    /**
     * Get all pending requests for review.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityChangeRequest> getPendingRequests() {
        return requestRepository.findPending();
    }

    /**
     * Get requests for a specific lecturer.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityChangeRequest> getRequestsForLecturer(Lecturer lecturer) {
        return requestRepository.findByLecturerOrderByCreatedAtDesc(lecturer);
    }

    /**
     * Count pending requests (for dashboard badge).
     */
    @Transactional(readOnly = true)
    public long countPendingRequests() {
        return requestRepository.countAllPending();
    }

    /**
     * Check if a lecturer can directly edit their availability.
     * Returns true if deadline has not passed or deadline enforcement is disabled.
     */
    @Transactional(readOnly = true)
    public boolean canDirectlyEditAvailability(Lecturer lecturer) {
        boolean deadlineEnabled = settingsService.getBooleanSetting("availability_deadline_enabled", true);
        if (!deadlineEnabled) {
            return true; // No deadline enforcement
        }

        String deadlineDateStr = settingsService.getStringSetting("availability_deadline_date", null);
        if (deadlineDateStr == null || deadlineDateStr.isEmpty()) {
            return true; // No deadline set
        }

        try {
            java.time.LocalDate deadline = java.time.LocalDate.parse(deadlineDateStr);
            return java.time.LocalDate.now().isBefore(deadline);
        } catch (Exception e) {
            log.warn("Invalid deadline date format: {}", deadlineDateStr);
            return true; // Invalid date format, allow editing
        }
    }

    // ==================== Private Methods ====================

    /**
     * Find lessons that would be affected by an availability change.
     */
    private List<Lesson> findAffectedLessons(
            Lecturer lecturer,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime) {

        return lessonRepository.findByLecturer(lecturer).stream()
                .filter(lesson -> lesson.getTimeslot() != null)
                .filter(lesson -> lesson.getTimeslot().getDayOfWeek() == dayOfWeek)
                .filter(lesson -> {
                    LocalTime lessonStart = lesson.getTimeslot().getStartTime();
                    LocalTime lessonEnd = lesson.getEndTime();
                    // Check for overlap
                    return lessonStart.isBefore(endTime) && lessonEnd.isAfter(startTime);
                })
                .collect(Collectors.toList());
    }

    /**
     * Apply an approved availability change to the lecturer.
     */
    private void applyAvailabilityChange(AvailabilityChangeRequest request) {
        Lecturer lecturer = request.getLecturer();

        if (request.getNewStatus() == AvailabilityStatus.UNAVAILABLE) {
            // Add unavailability entry
            LecturerUnavailability unavailability = new LecturerUnavailability();
            unavailability.setLecturer(lecturer);
            unavailability.setDayOfWeek(request.getDayOfWeek());
            unavailability.setStartTime(request.getStartTime());
            unavailability.setEndTime(request.getEndTime());
            lecturer.getUnavailabilities().add(unavailability);
            lecturerRepository.save(lecturer);
        } else if (request.getNewStatus() == AvailabilityStatus.AVAILABLE) {
            // Remove matching unavailability entries
            lecturer.getUnavailabilities().removeIf(u -> u.getDayOfWeek() == request.getDayOfWeek() &&
                    u.getStartTime().equals(request.getStartTime()) &&
                    u.getEndTime().equals(request.getEndTime()));
            lecturerRepository.save(lecturer);
        }

        log.info("Applied availability change for lecturer {}: {} {}-{} = {}",
                lecturer.getName(), request.getDayOfWeek(),
                request.getStartTime(), request.getEndTime(), request.getNewStatus());
    }
}
