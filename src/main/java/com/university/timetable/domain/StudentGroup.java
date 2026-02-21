package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;

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

    /**
     * The base name without level/group suffix (e.g., "Computer Science").
     */
    @Column(nullable = false)
    private String baseName;

    /**
     * Level indicator (100, 200, 300, 400, 500, 600).
     */
    @Column(nullable = false)
    private Integer level;

    /**
     * Optional group notation (A, B, C, DE, etc.). Null for parent groups.
     */
    @Column
    private String groupNotation;

    /**
     * Computed full name = baseName + " " + level + groupNotation.
     * Stored for efficient querying.
     */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int size;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_group_id")
    private StudentGroup parentGroup;

    @OneToMany(mappedBy = "parentGroup", fetch = FetchType.EAGER)
    private List<StudentGroup> children = new ArrayList<>();

    public StudentGroup(String baseName, Integer level, String groupNotation, int size) {
        this.baseName = baseName;
        this.level = level;
        this.groupNotation = groupNotation;
        this.name = computeName(baseName, level, groupNotation);
        this.size = size;
    }

    public StudentGroup(String baseName, Integer level, String groupNotation, int size, StudentGroup parentGroup) {
        this.baseName = baseName;
        this.level = level;
        this.groupNotation = groupNotation;
        this.name = computeName(baseName, level, groupNotation);
        this.size = size;
        this.parentGroup = parentGroup;
    }

    /**
     * Compute the full name from components.
     * Format: "baseName level[groupNotation]" e.g., "Computer Science 100A"
     */
    public static String computeName(String baseName, Integer level, String groupNotation) {
        StringBuilder sb = new StringBuilder();
        if (baseName != null) {
            sb.append(baseName);
        }
        if (level != null) {
            sb.append(" ").append(level).append(" LEVEL");
        }
        if (groupNotation != null && !groupNotation.trim().isEmpty()) {
            sb.append(" (GRP ").append(groupNotation.trim()).append(")");
        }
        return sb.toString().trim();
    }

    /**
     * Update name when fields change.
     */
    public void updateComputedName() {
        this.name = computeName(this.baseName, this.level, this.groupNotation);
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
