package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Course entity representing an academic subject.
 * Has total weekly hours that get split into Lessons.
 * 
 * Based on design.md Course Entity:
 * - Has lecturer and student group relationships
 * - Has allowedZones for location governance
 * - Has requiredFeatures for room suitability
 */
@Entity
@Table(name = "course")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "total_weekly_hours", nullable = false)
    private int totalWeeklyHours;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    /**
     * Student groups taking this course.
     * Supports combined classes (e.g., Groups A+D+E for English).
     * For backward compatibility, also check the legacy studentGroup column.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "course_student_group",
        joinColumns = @JoinColumn(name = "course_id"),
        inverseJoinColumns = @JoinColumn(name = "student_group_id")
    )
    private Set<StudentGroup> studentGroups = new HashSet<>();

    /**
     * Legacy single student group field (for backward compatibility).
     * Prefer using studentGroups for new courses.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_group_id")
    private StudentGroup studentGroup;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "course_allowed_zone",
        joinColumns = @JoinColumn(name = "course_id"),
        inverseJoinColumns = @JoinColumn(name = "zone_id")
    )
    private Set<Zone> allowedZones = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "course_feature",
        joinColumns = @JoinColumn(name = "course_id"),
        inverseJoinColumns = @JoinColumn(name = "feature_id")
    )
    private Set<Feature> requiredFeatures = new HashSet<>();

    /**
     * Whether this course is delivered online.
     * Online courses don't require physical rooms and have no capacity limits.
     */
    @Column(name = "is_online")
    private boolean online = false;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lesson> lessons = new ArrayList<>();

    public Course(String code, String name, int totalWeeklyHours) {
        this.code = code;
        this.name = name;
        this.totalWeeklyHours = totalWeeklyHours;
    }

    /**
     * Get all student groups for this course.
     * Combines both the new studentGroups set and legacy studentGroup field.
     */
    public Set<StudentGroup> getAllStudentGroups() {
        Set<StudentGroup> allGroups = new HashSet<>(studentGroups);
        if (studentGroup != null) {
            allGroups.add(studentGroup);
        }
        return allGroups;
    }

    /**
     * Get total student count across all groups.
     * Used for room capacity constraint.
     */
    public int getTotalStudentCount() {
        return getAllStudentGroups().stream()
            .mapToInt(StudentGroup::getSize)
            .sum();
    }

    @Override
    public String toString() {
        return code + ": " + name;
    }
}
