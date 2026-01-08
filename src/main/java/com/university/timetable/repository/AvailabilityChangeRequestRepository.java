package com.university.timetable.repository;

import com.university.timetable.domain.AvailabilityChangeRequest;
import com.university.timetable.domain.AvailabilityChangeRequest.RequestStatus;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for AvailabilityChangeRequest entity.
 */
@Repository
public interface AvailabilityChangeRequestRepository extends JpaRepository<AvailabilityChangeRequest, Long> {

    /**
     * Find all requests for a specific lecturer.
     */
    List<AvailabilityChangeRequest> findByLecturerOrderByCreatedAtDesc(Lecturer lecturer);

    /**
     * Find all requests with a specific status.
     */
    List<AvailabilityChangeRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    /**
     * Find all pending requests.
     */
    default List<AvailabilityChangeRequest> findPending() {
        return findByStatusOrderByCreatedAtDesc(RequestStatus.PENDING);
    }

    /**
     * Find requests submitted by a specific user.
     */
    List<AvailabilityChangeRequest> findByRequestedByOrderByCreatedAtDesc(User requestedBy);

    /**
     * Count pending requests for a lecturer.
     */
    @Query("SELECT COUNT(r) FROM AvailabilityChangeRequest r WHERE r.lecturer = :lecturer AND r.status = 'PENDING'")
    long countPendingByLecturer(@Param("lecturer") Lecturer lecturer);

    /**
     * Count all pending requests (for dashboard).
     */
    @Query("SELECT COUNT(r) FROM AvailabilityChangeRequest r WHERE r.status = 'PENDING'")
    long countAllPending();

    /**
     * Find requests with pagination and filtering by status.
     */
    Page<AvailabilityChangeRequest> findByStatus(RequestStatus status, Pageable pageable);
}
