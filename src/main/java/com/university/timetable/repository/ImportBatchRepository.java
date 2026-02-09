package com.university.timetable.repository;

import com.university.timetable.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    // Find batches waiting for approval
    List<ImportBatch> findByStatusOrderByCreatedAtDesc(ImportBatch.ImportStatus status);

    // Find batches submitted by a specific user
    List<ImportBatch> findByCreatedBy_IdOrderByCreatedAtDesc(Long userId);

    // Find batch by linked history ID
    Optional<ImportBatch> findByImportHistoryId(Long importHistoryId);

    // Find drafts for a user
    List<ImportBatch> findByStatusAndCreatedBy_IdOrderByCreatedAtDesc(ImportBatch.ImportStatus status, Long userId);
}
