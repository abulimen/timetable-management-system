package com.university.timetable.controller;

import com.university.timetable.domain.AvailabilityChangeRequest;
import com.university.timetable.domain.AvailabilityChangeRequest.AvailabilityStatus;
import com.university.timetable.domain.AvailabilityChangeRequest.RequestStatus;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.User;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.UserRepository;
import com.university.timetable.service.AvailabilityChangeRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for managing availability change requests.
 * Implements the approval workflow per USER_AUTH_REQUIREMENTS.md Section 3.4.
 */
@RestController
@RequestMapping("/api/v1/availability-requests")
@RequiredArgsConstructor
@Slf4j
public class AvailabilityChangeRequestController {

    private final AvailabilityChangeRequestService requestService;
    private final LecturerRepository lecturerRepository;
    private final UserRepository userRepository;

    /**
     * Submit a new availability change request.
     * Lecturers can submit requests for their own availability.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR', 'LECTURER')")
    public ResponseEntity<?> submitRequest(
            @RequestBody AvailabilityChangeRequestDTO dto,
            Authentication authentication) {

        User currentUser = getCurrentUser(authentication);
        Lecturer lecturer = getLecturer(dto.lecturerId);

        // Lecturers can only submit requests for themselves
        if (currentUser.getRole().name().equals("LECTURER")) {
            if (currentUser.getLecturer() == null ||
                    !currentUser.getLecturer().getId().equals(lecturer.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "FORBIDDEN",
                        "message", "Lecturers can only submit requests for their own availability"));
            }
        }

        try {
            AvailabilityChangeRequest request = requestService.createRequest(
                    currentUser,
                    lecturer,
                    DayOfWeek.valueOf(dto.dayOfWeek.toUpperCase()),
                    LocalTime.parse(dto.startTime),
                    LocalTime.parse(dto.endTime),
                    AvailabilityStatus.valueOf(dto.newStatus.toUpperCase()),
                    dto.reason);

            return ResponseEntity.ok(toDTO(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "VALIDATION_ERROR",
                    "message", e.getMessage()));
        }
    }

    /**
     * Get all pending requests (for admin/coordinator review).
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public List<AvailabilityChangeRequestResponseDTO> getPendingRequests() {
        return requestService.getPendingRequests().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get pending request count (for dashboard badge).
     */
    @GetMapping("/pending/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public Map<String, Long> getPendingCount() {
        return Map.of("count", requestService.countPendingRequests());
    }

    /**
     * Get my requests (for lecturer view).
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR', 'LECTURER')")
    public ResponseEntity<?> getMyRequests(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        if (currentUser.getLecturer() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<AvailabilityChangeRequestResponseDTO> requests = requestService
                .getRequestsForLecturer(currentUser.getLecturer())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(requests);
    }

    /**
     * Check if current user can directly edit their availability.
     */
    @GetMapping("/can-edit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR', 'LECTURER')")
    public ResponseEntity<Map<String, Object>> canEditAvailability(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);

        // Admins and coordinators can always edit
        if (!currentUser.getRole().name().equals("LECTURER")) {
            return ResponseEntity.ok(Map.of(
                    "canEdit", true,
                    "reason", "Administrative privileges"));
        }

        if (currentUser.getLecturer() == null) {
            return ResponseEntity.ok(Map.of(
                    "canEdit", false,
                    "reason", "No linked lecturer account"));
        }

        boolean canEdit = requestService.canDirectlyEditAvailability(currentUser.getLecturer());
        return ResponseEntity.ok(Map.of(
                "canEdit", canEdit,
                "reason", canEdit ? "Before deadline" : "Deadline has passed - submit a request"));
    }

    /**
     * Approve a request.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDTO dto,
            Authentication authentication) {

        User reviewer = getCurrentUser(authentication);
        String notes = dto != null ? dto.notes : null;

        try {
            AvailabilityChangeRequest request = requestService.approveRequest(id, reviewer, notes);
            return ResponseEntity.ok(toDTO(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "APPROVAL_FAILED",
                    "message", e.getMessage()));
        }
    }

    /**
     * Reject a request.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> rejectRequest(
            @PathVariable Long id,
            @RequestBody ReviewDTO dto,
            Authentication authentication) {

        if (dto == null || dto.notes == null || dto.notes.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "VALIDATION_ERROR",
                    "message", "Rejection reason is required"));
        }

        User reviewer = getCurrentUser(authentication);

        try {
            AvailabilityChangeRequest request = requestService.rejectRequest(id, reviewer, dto.notes);
            return ResponseEntity.ok(toDTO(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "REJECTION_FAILED",
                    "message", e.getMessage()));
        }
    }

    /**
     * Return a request for more information.
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> returnRequest(
            @PathVariable Long id,
            @RequestBody ReviewDTO dto,
            Authentication authentication) {

        if (dto == null || dto.notes == null || dto.notes.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "VALIDATION_ERROR",
                    "message", "Return reason is required"));
        }

        User reviewer = getCurrentUser(authentication);

        try {
            AvailabilityChangeRequest request = requestService.returnRequest(id, reviewer, dto.notes);
            return ResponseEntity.ok(toDTO(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "RETURN_FAILED",
                    "message", e.getMessage()));
        }
    }

    // ==================== Helper Methods ====================

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("User not found: " + email));
    }

    private Lecturer getLecturer(Long lecturerId) {
        return lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new IllegalArgumentException("Lecturer not found: " + lecturerId));
    }

    private AvailabilityChangeRequestResponseDTO toDTO(AvailabilityChangeRequest request) {
        AvailabilityChangeRequestResponseDTO dto = new AvailabilityChangeRequestResponseDTO();
        dto.id = request.getId();
        dto.lecturerId = request.getLecturer().getId();
        dto.lecturerName = request.getLecturer().getName();
        dto.requestedByEmail = request.getRequestedBy().getEmail();
        dto.dayOfWeek = request.getDayOfWeek().name();
        dto.startTime = request.getStartTime().toString();
        dto.endTime = request.getEndTime().toString();
        dto.newStatus = request.getNewStatus().name();
        dto.reason = request.getReason();
        dto.status = request.getStatus().name();
        dto.affectedLessonsCount = request.getAffectedLessonsCount();
        dto.createdAt = request.getCreatedAt().toString();

        if (request.getReviewedBy() != null) {
            dto.reviewedByEmail = request.getReviewedBy().getEmail();
            dto.reviewedAt = request.getReviewedAt().toString();
            dto.reviewNotes = request.getReviewNotes();
        }

        return dto;
    }

    // ==================== DTO Classes ====================

    public static class AvailabilityChangeRequestDTO {
        public Long lecturerId;
        public String dayOfWeek;
        public String startTime;
        public String endTime;
        public String newStatus;
        public String reason;
    }

    public static class ReviewDTO {
        public String notes;
    }

    public static class AvailabilityChangeRequestResponseDTO {
        public Long id;
        public Long lecturerId;
        public String lecturerName;
        public String requestedByEmail;
        public String dayOfWeek;
        public String startTime;
        public String endTime;
        public String newStatus;
        public String reason;
        public String status;
        public Integer affectedLessonsCount;
        public String createdAt;
        public String reviewedByEmail;
        public String reviewedAt;
        public String reviewNotes;
    }
}
