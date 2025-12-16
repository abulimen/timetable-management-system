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
 * Lecturer entity representing instructors.
 * Has unavailability periods that constrain scheduling.
 * 
 * Based on design.md Lecturer Entity with isAvailableAt() method.
 */
@Entity
@Table(name = "lecturer")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Lecturer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String email;

    @OneToMany(mappedBy = "lecturer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<LecturerUnavailability> unavailabilities = new ArrayList<>();

    public Lecturer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public Lecturer(String name) {
        this.name = name;
    }

    /**
     * Check if lecturer is available at a given timeslot for a lesson duration.
     * Returns false if any unavailability period overlaps.
     * 
     * Based on design.md: Hard Constraint for lecturer unavailability.
     */
    public boolean isAvailableAt(Timeslot timeslot, int durationHours) {
        if (timeslot == null) {
            return true; // Unassigned lessons are always "available"
        }
        var lessonEnd = timeslot.getStartTime().plusHours(durationHours);
        return unavailabilities.stream()
            .noneMatch(u -> u.overlaps(timeslot.getDayOfWeek(), timeslot.getStartTime(), lessonEnd));
    }

    public void addUnavailability(LecturerUnavailability unavailability) {
        unavailabilities.add(unavailability);
        unavailability.setLecturer(this);
    }

    @Override
    public String toString() {
        return name;
    }
}
