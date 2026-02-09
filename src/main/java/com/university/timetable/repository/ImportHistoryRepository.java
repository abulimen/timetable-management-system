package com.university.timetable.repository;

import com.university.timetable.domain.ImportHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ImportHistory entities.
 */
@Repository
public interface ImportHistoryRepository extends JpaRepository<ImportHistory, Long> {

    /**
     * Get all import history ordered by most recent first, with user eagerly
     * loaded.
     */
    @Query("SELECT h FROM ImportHistory h LEFT JOIN FETCH h.user LEFT JOIN FETCH h.rolledBackBy ORDER BY h.timestamp DESC")
    List<ImportHistory> findAllWithUserOrderByTimestampDesc();

    /**
     * Get import history for a specific entity type.
     */
    List<ImportHistory> findByEntityTypeOrderByTimestampDesc(String entityType);

    /**
     * Find history entry that can be rolled back.
     */
    @Query("SELECT h FROM ImportHistory h WHERE h.id = :id AND h.canRollback = true AND h.rolledBack = false")
    Optional<ImportHistory> findRollbackCandidate(Long id);

    /**
     * Get recent imports (last 30 days).
     */
    @Query("SELECT h FROM ImportHistory h WHERE h.timestamp > CURRENT_TIMESTAMP - 30 DAY ORDER BY h.timestamp DESC")
    List<ImportHistory> findRecentImports();
}
