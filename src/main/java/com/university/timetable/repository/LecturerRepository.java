package com.university.timetable.repository;

import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, Long> {
    Optional<Lecturer> findByName(String name);

    Optional<Lecturer> findByEmail(String email);

    Optional<Lecturer> findByUser(User user);
}
