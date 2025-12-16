package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.optaplanner.core.api.domain.lookup.PlanningId;

import java.util.ArrayList;
import java.util.List;

/**
 * StudentGroup entity with hierarchical parent-child relationships.
 * Supports batch lectures (parent) and sub-group labs (children).
 * 
 * Based on design.md StudentGroup Entity:
 * - CS_Year1 (Parent) contains CS_Year1_A and CS_Year1_B (Children)
 * - Parent lecture blocks all children
 * - Child lesson blocks parent but not siblings
 */
@Entity
@Table(name = "student_group")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StudentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int size;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_group_id")
    private StudentGroup parentGroup;

    @OneToMany(mappedBy = "parentGroup", fetch = FetchType.EAGER)
    private List<StudentGroup> children = new ArrayList<>();

    public StudentGroup(String name, int size) {
        this.name = name;
        this.size = size;
    }

    public StudentGroup(String name, int size, StudentGroup parentGroup) {
        this.name = name;
        this.size = size;
        this.parentGroup = parentGroup;
    }

    /**
     * Check if this group is the parent of another group.
     */
    public boolean isParentOf(StudentGroup other) {
        if (other == null || children == null) {
            return false;
        }
        return children.contains(other);
    }

    /**
     * Check if this group is a child of another group.
     */
    public boolean isChildOf(StudentGroup other) {
        if (other == null || parentGroup == null) {
            return false;
        }
        return parentGroup.equals(other);
    }

    /**
     * Check if this group has a scheduling conflict with another group.
     * Conflict exists if:
     * - Same group
     * - This is parent of other (parent lecture blocks child)
     * - Other is parent of this (child lesson blocks parent)
     * 
     * Based on design.md conflict logic:
     * GroupA == GroupB OR GroupA.isParentOf(GroupB) OR GroupB.isParentOf(GroupA)
     */
    public boolean hasConflictWith(StudentGroup other) {
        if (other == null) {
            return false;
        }
        return this.equals(other) 
            || this.isParentOf(other) 
            || other.isParentOf(this);
    }

    @Override
    public String toString() {
        return name + " (" + size + " students)";
    }
}
