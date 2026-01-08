package com.university.timetable.dto;

import com.university.timetable.domain.UserRole;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating an existing user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Email(message = "Invalid email format")
    private String email;

    private String firstName;

    private String lastName;

    private String phone;

    private String department;

    private UserRole role;

    private Long lecturerId;

    private Boolean active;
}
