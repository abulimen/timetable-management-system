package com.university.timetable.repository;

import com.university.timetable.domain.SpecialEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface SpecialEventRepository extends JpaRepository<SpecialEvent, Long> {

    /**
     * Find all active special events.
     */
    List<SpecialEvent> findByActiveTrue();

    /**
     * Find active special events on a specific day.
     */
    List<SpecialEvent> findByDayOfWeekAndActiveTrue(DayOfWeek dayOfWeek);

    /**
     * Find all special events for a specific student group.
     */
    @Query("SELECT se FROM SpecialEvent se JOIN se.studentGroups sg WHERE sg.id = :groupId AND se.active = true")
    List<SpecialEvent> findActiveByStudentGroupId(@Param("groupId") Long groupId);

    /**
     * Find all special events in a specific room.
     */
    List<SpecialEvent> findByRoomIdAndActiveTrue(Long roomId);

    /**
     * Find all special events for a lecturer.
     */
    List<SpecialEvent> findByLecturerIdAndActiveTrue(Long lecturerId);
}
