package com.university.timetable.repository;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCode(String code);
    List<Course> findByLecturer(Lecturer lecturer);
    List<Course> findByStudentGroup(StudentGroup studentGroup);
    boolean existsByCode(String code);
}
