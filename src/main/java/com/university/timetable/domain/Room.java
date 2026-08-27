package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.util.HashSet;
import java.util.Set;

/**
 * Room entity representing physical locations for lessons.
 * Has capacity, belongs to a zone, and can have multiple features.
 * 
 * Based on design.md Room Entity specification.
 */
@Entity
@Table(name = "room")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "room_feature",
        joinColumns = @JoinColumn(name = "room_id"),
        inverseJoinColumns = @JoinColumn(name = "feature_id")
    )
    private Set<Feature> features = new HashSet<>();

    public Room(String name, int capacity, Zone zone) {
        this.name = name;
        this.capacity = capacity;
        this.zone = zone;
    }

    /**
     * Check if room has a specific feature.
     */
    public boolean hasFeature(Feature feature) {
        return features.contains(feature);
    }

    /**
     * Check if room has all required features.
     * Used for room suitability constraint in Timefold Solver.
     */
    public boolean hasAllFeatures(Set<Feature> requiredFeatures) {
        if (requiredFeatures == null || requiredFeatures.isEmpty()) {
            return true;
        }
        return features.containsAll(requiredFeatures);
    }

    @Override
    public String toString() {
        return name + " (Cap: " + capacity + ")";
    }
}
