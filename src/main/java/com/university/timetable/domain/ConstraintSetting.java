package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * ConstraintSetting entity for storing configurable constraint parameters.
 * Allows admins to adjust constraint settings without code changes.
 */
@Entity
@Table(name = "constraint_setting")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    @Column(name = "data_type", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    @Enumerated(EnumType.STRING)
    private DataType dataType;

    @Column(name = "category", nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DataType {
        STRING, INTEGER, TIME, BOOLEAN
    }

    public enum Category {
        TIMING, LIMITS, WEIGHTS, FEATURES, SYSTEM
    }

    public ConstraintSetting(String settingKey, String settingValue, DataType dataType, Category category,
            String description) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.dataType = dataType;
        this.category = category;
        this.description = description;
    }
}
