package com.university.timetable.repository;

import com.university.timetable.domain.SemesterArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for semester archive metadata.
 */
@Repository
public interface SemesterArchiveRepository extends JpaRepository<SemesterArchive, Long> {
    
    Optional<SemesterArchive> findByCode(String code);
    
    List<SemesterArchive> findAllByOrderByArchivedAtDesc();
    
    boolean existsByCode(String code);
}
