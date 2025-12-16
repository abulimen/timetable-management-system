package com.university.timetable.dto;

import com.university.timetable.domain.ConstraintSetting;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for constraint setting API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintSettingDTO {

    private Long id;
    private String key;
    private String value;
    private String dataType;
    private String category;
    private String description;

    public static ConstraintSettingDTO fromEntity(ConstraintSetting entity) {
        return new ConstraintSettingDTO(
            entity.getId(),
            entity.getSettingKey(),
            entity.getSettingValue(),
            entity.getDataType().name(),
            entity.getCategory().name(),
            entity.getDescription()
        );
    }
}
