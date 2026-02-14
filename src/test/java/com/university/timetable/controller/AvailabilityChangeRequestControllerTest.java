package com.university.timetable.controller;

import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.User;
import com.university.timetable.domain.UserRole;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.UserRepository;
import com.university.timetable.service.AuditLogService;
import com.university.timetable.service.AvailabilityChangeRequestService;
import com.university.timetable.service.ConstraintSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityChangeRequestControllerTest {

    @Mock
    private AvailabilityChangeRequestService requestService;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConstraintSettingsService constraintSettingsService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private AvailabilityChangeRequestController controller;

    @Test
    void submitRequestReturnsBadRequestWhenRequestsClosed() {
        AvailabilityChangeRequestController.AvailabilityChangeRequestDTO dto = new AvailabilityChangeRequestController.AvailabilityChangeRequestDTO();
        dto.lecturerId = 7L;
        dto.dayOfWeek = "MONDAY";
        dto.startTime = "08:00";
        dto.endTime = "10:00";
        dto.newStatus = "UNAVAILABLE";
        dto.reason = "This is a sufficiently long reason for validation.";

        when(constraintSettingsService.isUnavailabilityRequestsOpen()).thenReturn(false);

        var response = controller.submitRequest(dto, authentication);

        assertEquals(400, response.getStatusCode().value());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("REQUESTS_CLOSED", body.get("error"));
        verify(requestService, never()).createRequest(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitRequestReturns403WhenLecturerSubmitsForAnotherLecturer() {
        Lecturer ownLecturer = lecturer(1L, "Own Lecturer");
        Lecturer targetLecturer = lecturer(2L, "Other Lecturer");

        User currentUser = new User();
        currentUser.setEmail("lecturer@example.com");
        currentUser.setRole(UserRole.LECTURER);
        currentUser.setLecturer(ownLecturer);

        AvailabilityChangeRequestController.AvailabilityChangeRequestDTO dto = new AvailabilityChangeRequestController.AvailabilityChangeRequestDTO();
        dto.lecturerId = 2L;
        dto.dayOfWeek = "MONDAY";
        dto.startTime = "08:00";
        dto.endTime = "10:00";
        dto.newStatus = "UNAVAILABLE";
        dto.reason = "This is a sufficiently long reason for validation.";

        when(constraintSettingsService.isUnavailabilityRequestsOpen()).thenReturn(true);
        when(authentication.getName()).thenReturn("lecturer@example.com");
        when(userRepository.findByEmailIgnoreCase("lecturer@example.com")).thenReturn(Optional.of(currentUser));
        when(lecturerRepository.findById(2L)).thenReturn(Optional.of(targetLecturer));

        var response = controller.submitRequest(dto, authentication);

        assertEquals(403, response.getStatusCode().value());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("FORBIDDEN", body.get("error"));
        verify(requestService, never()).createRequest(any(), any(), any(), any(), any(), any(), any());
    }

    private static Lecturer lecturer(Long id, String name) {
        Lecturer lecturer = new Lecturer();
        lecturer.setId(id);
        lecturer.setName(name);
        lecturer.setEmail(name.toLowerCase().replace(" ", ".") + "@example.com");
        return lecturer;
    }
}

