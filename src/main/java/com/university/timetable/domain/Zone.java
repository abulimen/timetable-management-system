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
 * Zone entity representing building/block groupings.
 * Used for location governance - courses can be restricted to specific zones.
 * 
 * Based on design.md: "Organize Rooms into physical Zones (Buildings/Blocks)"
 */
@Entity
@Table(name = "zone")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "zone")
    private Set<Room> rooms = new HashSet<>();

    public Zone(String name) {
        this.name = name;
    }
}
