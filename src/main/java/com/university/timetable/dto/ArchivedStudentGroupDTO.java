package com.university.timetable.dto;

import lombok.Data;

@Data
public class ArchivedStudentGroupDTO {
    private Long id;
    private String name;
    private Integer size;
    private Long parentGroupId;
    private String parentGroupName;
    private Integer childCount;
}
