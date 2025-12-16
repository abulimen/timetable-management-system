package com.university.timetable.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StudentGroup hierarchical conflict detection.
 * Based on specs.md Hierarchical Student Groups:
 * - Parent lecture blocks all children
 * - Child lesson blocks parent but not siblings
 */
class StudentGroupTest {

    @Test
    void hasConflictWith_sameGroup_returnsTrue() {
        StudentGroup group = new StudentGroup("CS Year 1", 100);
        
        assertThat(group.hasConflictWith(group)).isTrue();
    }

    @Test
    void hasConflictWith_parentAndChild_returnsTrue() {
        StudentGroup parent = new StudentGroup("CS Year 1", 100);
        StudentGroup child = new StudentGroup("CS Year 1 - Group A", 50, parent);
        parent.getChildren().add(child);
        
        // Parent has conflict with child
        assertThat(parent.hasConflictWith(child)).isTrue();
        // Child has conflict with parent
        assertThat(child.hasConflictWith(parent)).isTrue();
    }

    @Test
    void hasConflictWith_siblings_returnsFalse() {
        StudentGroup parent = new StudentGroup("CS Year 1", 100);
        StudentGroup childA = new StudentGroup("CS Year 1 - Group A", 50, parent);
        StudentGroup childB = new StudentGroup("CS Year 1 - Group B", 50, parent);
        parent.getChildren().add(childA);
        parent.getChildren().add(childB);
        
        // Siblings should NOT conflict
        assertThat(childA.hasConflictWith(childB)).isFalse();
        assertThat(childB.hasConflictWith(childA)).isFalse();
    }

    @Test
    void hasConflictWith_unrelatedGroups_returnsFalse() {
        StudentGroup csGroup = new StudentGroup("CS Year 1", 100);
        StudentGroup eeGroup = new StudentGroup("EE Year 1", 80);
        
        assertThat(csGroup.hasConflictWith(eeGroup)).isFalse();
    }

    @Test
    void hasConflictWith_null_returnsFalse() {
        StudentGroup group = new StudentGroup("CS Year 1", 100);
        
        assertThat(group.hasConflictWith(null)).isFalse();
    }

    @Test
    void isParentOf_directChild_returnsTrue() {
        StudentGroup parent = new StudentGroup("CS Year 1", 100);
        StudentGroup child = new StudentGroup("CS Year 1 - Group A", 50, parent);
        parent.getChildren().add(child);
        
        assertThat(parent.isParentOf(child)).isTrue();
    }

    @Test
    void isParentOf_notChild_returnsFalse() {
        StudentGroup parent = new StudentGroup("CS Year 1", 100);
        StudentGroup other = new StudentGroup("EE Year 1", 80);
        
        assertThat(parent.isParentOf(other)).isFalse();
    }
}
