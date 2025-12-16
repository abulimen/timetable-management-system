package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.optaplanner.core.api.domain.lookup.PlanningId;

/**
 * Feature entity representing room capabilities.
 * Examples: Projector, Wet Lab, Computers, etc.
 * 
 * Based on design.md Feature Entity specification.
 */
@Entity
@Table(name = "feature")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    public Feature(String name) {
        this.name = name;
    }
}
