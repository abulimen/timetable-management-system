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
import java.util.Map;
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
    private final EmailService emailService;

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
        AvailabilityChangeRequest saved = requestRepository.save(request);
        notifyLecturer(lecturer, "CREATED", dayOfWeek, startTime, endTime, "Request submitted for review.");
        return saved;
    }

    /**
     * Approve a change request and apply the availability change.
     */
    public AvailabilityChangeRequest approveRequest(Long requestId, User reviewer, String reviewNotes) {
        AvailabilityChangeRequest request = requestRepository.findByIdWithDetails(requestId)
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

        AvailabilityChangeRequest saved = requestRepository.save(request);
        notifyLecturer(request.getLecturer(), "UPDATED", request.getDayOfWeek(), request.getStartTime(),
                request.getEndTime(), "Request approved. Unavailability is active.");
        return saved;
    }

    /**
     * Reject a change request.
     */
    public AvailabilityChangeRequest rejectRequest(Long requestId, User reviewer, String reviewNotes) {
        AvailabilityChangeRequest request = requestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending: " + request.getStatus());
        }

        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNotes(reviewNotes);

        log.info("Rejected availability change request {} by {}: {}", requestId, reviewer.getEmail(), reviewNotes);

        AvailabilityChangeRequest saved = requestRepository.save(request);
        notifyLecturer(request.getLecturer(), "UPDATED", request.getDayOfWeek(), request.getStartTime(),
                request.getEndTime(), "Request rejected. " + safeNotes(reviewNotes));
        return saved;
    }

    /**
     * Return a request for more information.
     */
    public AvailabilityChangeRequest returnRequest(Long requestId, User reviewer, String reviewNotes) {
        AvailabilityChangeRequest request = requestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending: " + request.getStatus());
        }

        request.setStatus(RequestStatus.RETURNED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNotes(reviewNotes);

        log.info("Returned availability change request {} for more info by {}", requestId, reviewer.getEmail());

        AvailabilityChangeRequest saved = requestRepository.save(request);
        notifyLecturer(request.getLecturer(), "UPDATED", request.getDayOfWeek(), request.getStartTime(),
                request.getEndTime(), "Request returned for more information. " + safeNotes(reviewNotes));
        return saved;
    }

    /**
     * Revoke an approved request (change to rejected).
     * Only works for APPROVED requests.
     */
    public AvailabilityChangeRequest revokeRequest(Long requestId, User reviewer, String reviewNotes) {
        AvailabilityChangeRequest request = requestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only approved requests can be revoked. Current status: " + request.getStatus());
        }

        // TODO: Add deadline check here if needed
        // For now, allowing revocation at any time before semester ends

        request.setStatus(RequestStatus.REJECTED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNotes("REVOKED: " + reviewNotes);

        removeAppliedAvailabilitySlot(request);

        log.info("Revoked approval for request {} by {}: {}", requestId, reviewer.getEmail(), reviewNotes);
        AvailabilityChangeRequest saved = requestRepository.save(request);
        notifyLecturer(request.getLecturer(), "REVOKED", request.getDayOfWeek(), request.getStartTime(),
                request.getEndTime(), "Approved request was revoked. Unavailability slot removed. " + safeNotes(reviewNotes));
        return saved;
    }

    /**
     * Resubmit a returned request with updated information.
     * Only works for requests with RETURNED status.
     */
    public AvailabilityChangeRequest resubmitRequest(
            Long requestId,
            User currentUser,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            AvailabilityStatus newStatus,
            String reason) {

        AvailabilityChangeRequest request = requestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        // Check request is in RETURNED status
        if (request.getStatus() != RequestStatus.RETURNED) {
            throw new IllegalStateException(
                    "Only returned requests can be resubmitted. Current status: " + request.getStatus());
        }

        // Verify the current user is the original requester (for lecturers)
        if (currentUser.getRole().name().equals("LECTURER")) {
            if (currentUser.getLecturer() == null ||
                    !currentUser.getLecturer().getId().equals(request.getLecturer().getId())) {
                throw new IllegalStateException("You can only resubmit your own requests");
            }
        }

        // Validate reason length
        if (reason == null || reason.trim().length() < 20) {
            throw new IllegalArgumentException("Reason must be at least 20 characters");
        }

        // Recalculate affected lessons
        List<Lesson> affectedLessons = findAffectedLessons(request.getLecturer(), dayOfWeek, startTime, endTime);
        String affectedLessonIds = affectedLessons.stream()
                .map(l -> l.getId().toString())
                .collect(java.util.stream.Collectors.joining(","));

        // Update the request
        request.setDayOfWeek(dayOfWeek);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setNewStatus(newStatus);
        request.setReason(reason.trim());
        request.setStatus(RequestStatus.PENDING);
        request.setAffectedLessonsCount(affectedLessons.size());
        request.setAffectedLessonIds(affectedLessonIds.isEmpty() ? null : affectedLessonIds);
        request.setReviewedBy(null);
        request.setReviewedAt(null);
        request.setReviewNotes(null);

        log.info("Resubmitted availability change request {} for lecturer {}", requestId,
                request.getLecturer().getName());

        AvailabilityChangeRequest saved = requestRepository.save(request);
        notifyLecturer(request.getLecturer(), "UPDATED", request.getDayOfWeek(), request.getStartTime(),
                request.getEndTime(), "Request resubmitted and is pending review.");
        return saved;
    }

    /**
     * Get all pending requests for review.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityChangeRequest> getPendingRequests() {
        return requestRepository.findPending();
    }

    /**
     * Get all requests (history).
     */
    @Transactional(readOnly = true)
    public List<AvailabilityChangeRequest> getAllRequests() {
        return requestRepository.findAllWithDetails();
    }

    /**
     * Get requests for a specific lecturer.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityChangeRequest> getRequestsForLecturer(Lecturer lecturer) {
        return requestRepository.findByLecturerOrderByCreatedAtDesc(lecturer);
    }

    /**
     * Get request statistics for a lecturer.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getLecturerRequestStats(Lecturer lecturer) {
        List<AvailabilityChangeRequest> requests = requestRepository.findByLecturerOrderByCreatedAtDesc(lecturer);

        long approved = requests.stream().filter(r -> r.getStatus() == RequestStatus.APPROVED).count();
        long pending = requests.stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
        long rejected = requests.stream().filter(r -> r.getStatus() == RequestStatus.REJECTED).count();
        long total = requests.size();

        Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("approved", approved);
        stats.put("pending", pending);
        stats.put("rejected", rejected);
        stats.put("total", total);
        return stats;
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
        }

        log.info("Applied availability change for lecturer {}: {} {}-{} = {}",
                lecturer.getName(), request.getDayOfWeek(),
                request.getStartTime(), request.getEndTime(), request.getNewStatus());
    }

    private void removeAppliedAvailabilitySlot(AvailabilityChangeRequest request) {
        Lecturer lecturer = request.getLecturer();
        int before = lecturer.getUnavailabilities().size();
        lecturer.getUnavailabilities().removeIf(u ->
                u.getDayOfWeek() == request.getDayOfWeek()
                        && u.getStartTime().equals(request.getStartTime())
                        && u.getEndTime().equals(request.getEndTime()));
        int removed = before - lecturer.getUnavailabilities().size();
        if (removed > 0) {
            lecturerRepository.save(lecturer);
            log.info("Removed {} applied unavailability slot(s) for lecturer {} during revocation of request {}",
                    removed, lecturer.getName(), request.getId());
        } else {
            log.info("No matching unavailability slot found to remove for request {}", request.getId());
        }
    }

    private void notifyLecturer(Lecturer lecturer, String action, DayOfWeek dayOfWeek, LocalTime startTime,
            LocalTime endTime, String notes) {
        if (lecturer == null || lecturer.getEmail() == null || lecturer.getEmail().isBlank()) {
            return;
        }
        emailService.sendAvailabilityNotification(
                lecturer.getEmail(),
                lecturer.getName(),
                action,
                dayOfWeek,
                startTime != null ? startTime.toString() : null,
                endTime != null ? endTime.toString() : null,
                notes);
    }

    private String safeNotes(String notes) {
        return notes != null ? notes : "";
    }
}
