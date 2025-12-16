package com.university.timetable.repository;

import com.university.timetable.domain.ConstraintSetting;
import com.university.timetable.domain.ConstraintSetting.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConstraintSetting entity.
 */
@Repository
public interface ConstraintSettingRepository extends JpaRepository<ConstraintSetting, Long> {

    Optional<ConstraintSetting> findBySettingKey(String settingKey);

    List<ConstraintSetting> findByCategory(Category category);

    boolean existsBySettingKey(String settingKey);
}
