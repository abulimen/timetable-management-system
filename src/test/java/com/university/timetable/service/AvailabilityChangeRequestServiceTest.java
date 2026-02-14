package com.university.timetable.service;

import com.university.timetable.domain.AvailabilityChangeRequest;
import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.User;
import com.university.timetable.domain.UserRole;
import com.university.timetable.repository.AvailabilityChangeRequestRepository;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.LessonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityChangeRequestServiceTest {

    @Mock
    private AvailabilityChangeRequestRepository requestRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private ConstraintSettingsService settingsService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AvailabilityChangeRequestService service;

    @Test
    void createRequestRejectsShortReason() {
        User requester = user("lecturer@example.com");
        Lecturer lecturer = lecturer(10L, "Lecturer One");

        assertThrows(IllegalArgumentException.class, () -> service.createRequest(
                requester,
                lecturer,
                DayOfWeek.MONDAY,
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                AvailabilityChangeRequest.AvailabilityStatus.UNAVAILABLE,
                "too short"));

        verify(requestRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void revokeRequestRejectsWhenRequestNotApproved() {
        User reviewer = user("admin@example.com");
        AvailabilityChangeRequest request = AvailabilityChangeRequest.builder()
                .id(55L)
                .status(AvailabilityChangeRequest.RequestStatus.PENDING)
                .lecturer(lecturer(20L, "Lecturer Two"))
                .build();

        when(requestRepository.findByIdWithDetails(55L)).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class, () -> service.revokeRequest(55L, reviewer, "reason"));

        verify(requestRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private static Lecturer lecturer(Long id, String name) {
        Lecturer lecturer = new Lecturer();
        lecturer.setId(id);
        lecturer.setName(name);
        lecturer.setEmail(name.toLowerCase().replace(" ", ".") + "@example.com");
        return lecturer;
    }
}

