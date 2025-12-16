package com.university.timetable.repository;

import com.university.timetable.domain.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {
    Optional<StudentGroup> findByName(String name);
    
    // Find all root groups (no parent)
    List<StudentGroup> findByParentGroupIsNull();
    
    // Find all child groups of a parent
    List<StudentGroup> findByParentGroup(StudentGroup parentGroup);
    
    // Find all groups that are not children of any group (for timetable views)
    @Query("SELECT sg FROM StudentGroup sg WHERE sg.parentGroup IS NULL")
    List<StudentGroup> findAllRootGroups();
}
