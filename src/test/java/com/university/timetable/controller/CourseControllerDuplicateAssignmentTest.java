package com.university.timetable.controller;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.repository.CourseRepository;
import com.university.timetable.repository.FeatureRepository;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.StudentGroupRepository;
import com.university.timetable.repository.ZoneRepository;
import com.university.timetable.service.AuditLogService;
import com.university.timetable.service.LessonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseControllerDuplicateAssignmentTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private StudentGroupRepository studentGroupRepository;
    @Mock
    private FeatureRepository featureRepository;
    @Mock
    private ZoneRepository zoneRepository;
    @Mock
    private LessonService lessonService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CourseController courseController;

    @Test
    void createRejectsDuplicateCourseGroupAssignment() {
        StudentGroup groupA = group(1L, "ACCT 100 LEVEL (GRP A)");
        Course existing = course(99L, "ACC 102", Set.of(groupA));

        when(studentGroupRepository.findById(1L)).thenReturn(Optional.of(groupA));
        when(courseRepository.findByCode("ACC 102")).thenReturn(List.of(existing));

        CourseController.CourseCreateDTO dto = new CourseController.CourseCreateDTO();
        dto.code = "acc 102";
        dto.name = "Intro Accounting II";
        dto.totalWeeklyHours = 3;
        dto.studentGroupIds = List.of(1L);

        ResponseEntity<?> response = courseController.create(dto);

        assertStatus(response, 400);
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        String message = String.valueOf(body.get("message"));
        assertTrue(message.contains("Duplicate course-group assignment"));
        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any(Course.class));
    }

    @Test
    void updateRejectsDuplicateCourseGroupAssignmentAgainstAnotherEntry() {
        StudentGroup groupA = group(1L, "ACCT 100 LEVEL (GRP A)");
        StudentGroup groupC = group(3L, "ACCT 100 LEVEL (GRP C)");

        Course target = course(2L, "ACC 102", Set.of(groupC));
        Course existingOther = course(1L, "ACC 102", Set.of(groupA));

        when(courseRepository.findById(2L)).thenReturn(Optional.of(target));
        when(studentGroupRepository.findById(1L)).thenReturn(Optional.of(groupA));
        when(courseRepository.findByCode("ACC 102")).thenReturn(List.of(existingOther, target));

        CourseController.CourseCreateDTO dto = new CourseController.CourseCreateDTO();
        dto.code = "ACC 102";
        dto.name = "Intro Accounting II";
        dto.totalWeeklyHours = 3;
        dto.studentGroupIds = List.of(1L);

        ResponseEntity<?> response = courseController.update(2L, dto);

        assertStatus(response, 400);
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        String message = String.valueOf(body.get("message"));
        assertTrue(message.contains("Duplicate course-group assignment"));
        verify(courseRepository, never()).save(target);
    }

    private static void assertStatus(ResponseEntity<?> response, int expected) {
        HttpStatusCode status = response.getStatusCode();
        assertEquals(expected, status.value());
    }

    private static StudentGroup group(Long id, String name) {
        StudentGroup g = new StudentGroup();
        g.setId(id);
        g.setName(name);
        g.setSize(40);
        return g;
    }

    private static Course course(Long id, String code, Set<StudentGroup> groups) {
        Course c = new Course();
        c.setId(id);
        c.setCode(code);
        c.setName("Course " + code);
        c.setTotalWeeklyHours(3);
        c.setStudentGroups(groups);
        c.setStudentGroup(groups.iterator().next());
        return c;
    }
}

