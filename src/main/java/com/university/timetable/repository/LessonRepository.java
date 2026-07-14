package com.university.timetable.repository;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.domain.Timeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByCourse(Course course);
    List<Lesson> findByCourseId(Long courseId);

    List<Lesson> findByLecturer(Lecturer lecturer);

    List<Lesson> findByRoom(Room room);

    List<Lesson> findByTimeslot(Timeslot timeslot);

    // Find lessons by student group (through course's many-to-many studentGroups)
    @Query("SELECT DISTINCT l FROM Lesson l JOIN l.course.studentGroups sg WHERE sg = :studentGroup")
    List<Lesson> findByStudentGroup(@Param("studentGroup") StudentGroup studentGroup);

    @Query("SELECT DISTINCT l FROM Lesson l JOIN l.course.studentGroups sg WHERE sg IN :studentGroups")
    List<Lesson> findByAnyStudentGroups(@Param("studentGroups") Set<StudentGroup> studentGroups);

    // Find lessons that are not pinned
    List<Lesson> findByPinnedFalse();

    // Find lessons that have been scheduled (have timeslot and room)
    @Query("SELECT l FROM Lesson l WHERE l.timeslot IS NOT NULL AND l.room IS NOT NULL")
    List<Lesson> findScheduledLessons();

    // Find unscheduled lessons
    @Query("SELECT l FROM Lesson l WHERE l.timeslot IS NULL OR l.room IS NULL")
    List<Lesson> findUnscheduledLessons();

    // Count lessons for a course
    long countByCourse(Course course);
    long deleteByCourse(Course course);
    long deleteByCourseId(Long courseId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Lesson l SET l.timeslot = null, l.room = null, l.pinned = false")
    int clearAllAssignmentsAndPins();

    // Fetch all lessons with course and lecturer eagerly loaded
    @Query("SELECT l FROM Lesson l LEFT JOIN FETCH l.course c LEFT JOIN FETCH c.studentGroups LEFT JOIN FETCH c.allowedZones LEFT JOIN FETCH c.requiredFeatures LEFT JOIN FETCH l.lecturer")
    List<Lesson> findAllWithCourseAndLecturer();
}
