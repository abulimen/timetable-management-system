package com.university.timetable.repository;

import com.university.timetable.domain.SolverRunMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolverRunMetricRepository extends JpaRepository<SolverRunMetric, Long> {

    Page<SolverRunMetric> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<SolverRunMetric> findByModeIgnoreCaseOrderByStartedAtDesc(String mode, Pageable pageable);
}
