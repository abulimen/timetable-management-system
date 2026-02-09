package com.university.timetable.repository;

import com.university.timetable.domain.AuditAction;
import com.university.timetable.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for audit log queries.
 * Note: Audit logs are immutable - no update/delete methods exposed.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find logs within a date range.
     */
    Page<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find logs by entity type.
     */
    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    /**
     * Find logs by action type.
     */
    Page<AuditLog> findByAction(AuditAction action, Pageable pageable);

    /**
     * Find logs by actor ID.
     */
    Page<AuditLog> findByActorId(String actorId, Pageable pageable);

    /**
     * Find logs for a specific entity.
     */
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    /**
     * Complex query with multiple optional filters.
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
            "(:endDate IS NULL OR a.timestamp <= :endDate) AND " +
            "(:entityTypes IS NULL OR a.entityType IN :entityTypes) AND " +
            "(:actions IS NULL OR a.action IN :actions) AND " +
            "(:actorId IS NULL OR a.actorId = :actorId) AND " +
            "(:success IS NULL OR a.success = :success)")
    Page<AuditLog> findWithFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("entityTypes") List<String> entityTypes,
            @Param("actions") List<AuditAction> actions,
            @Param("actorId") String actorId,
            @Param("success") Boolean success,
            Pageable pageable);

    /**
     * Get action counts grouped by action type.
     */
    @Query("SELECT a.action, COUNT(a) FROM AuditLog a " +
            "WHERE (:startDate IS NULL OR a.timestamp >= :startDate) " +
            "GROUP BY a.action")
    List<Object[]> countByAction(@Param("startDate") LocalDateTime startDate);

    /**
     * Get counts grouped by entity type.
     */
    @Query("SELECT a.entityType, COUNT(a) FROM AuditLog a " +
            "WHERE (:startDate IS NULL OR a.timestamp >= :startDate) " +
            "GROUP BY a.entityType")
    List<Object[]> countByEntityType(@Param("startDate") LocalDateTime startDate);

    /**
     * Get distinct entity types for filter dropdown.
     */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a WHERE a.entityType IS NOT NULL ORDER BY a.entityType")
    List<String> findDistinctEntityTypes();

    /**
     * Get distinct actor IDs for filter dropdown.
     */
    @Query("SELECT DISTINCT a.actorId FROM AuditLog a WHERE a.actorId IS NOT NULL ORDER BY a.actorId")
    List<String> findDistinctActorIds();
}
