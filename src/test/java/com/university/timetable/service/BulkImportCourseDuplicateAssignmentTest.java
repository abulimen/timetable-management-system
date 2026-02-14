package com.university.timetable.service;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.dto.ImportConflictDTO;
import com.university.timetable.dto.ImportWithResolutionsRequest;
import com.university.timetable.repository.CourseRepository;
import com.university.timetable.repository.FeatureRepository;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.StudentGroupRepository;
import com.university.timetable.repository.UserRepository;
import com.university.timetable.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkImportCourseDuplicateAssignmentTest {

    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private StudentGroupRepository studentGroupRepository;
    @Mock
    private ZoneRepository zoneRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private FeatureRepository featureRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private ImportHistoryService importHistoryService;

    @InjectMocks
    private BulkImportService bulkImportService;

    @Test
    void importWithResolutionRejectsCreateNewWhenGroupAlreadyAssignedForSameCode() {
        StudentGroup groupA = group(1L, "ACCT 100 LEVEL (GRP A)");
        Course existing = course(10L, "ACC 102", Set.of(groupA));

        when(studentGroupRepository.findByName("ACCT 100 LEVEL (GRP A)")).thenReturn(Optional.of(groupA));
        when(courseRepository.findFirstByCodeOrderByIdAsc("ACC 102")).thenReturn(Optional.of(existing));
        when(courseRepository.findByCode("ACC 102")).thenReturn(List.of(existing));

        Map<String, String> row = Map.of(
                "code", "ACC 102",
                "name", "Introduction to Financial Accounting II",
                "weeklyHours", "3",
                "lecturerEmail", "",
                "isOnline", "false",
                "studentGroupNames", "ACCT 100 LEVEL (GRP A)");

        ImportWithResolutionsRequest request = ImportWithResolutionsRequest.builder()
                .rows(List.of(row))
                .resolutions(Map.of(2, ImportConflictDTO.ConflictResolution.CREATE_NEW))
                .build();

        BulkImportService.BulkImportException ex = assertThrows(
                BulkImportService.BulkImportException.class,
                () -> bulkImportService.importCoursesWithResolutions(request));

        assertTrue(ex.getResult().getRowErrors().stream()
                .anyMatch(err -> err.getMessage().contains("Duplicate course-group assignment")));
        verify(courseRepository, never()).saveAll(anyList());
    }

    @Test
    void importRejectsDuplicateCourseGroupAcrossPendingRows() {
        StudentGroup groupA = group(1L, "ACCT 100 LEVEL (GRP A)");

        when(studentGroupRepository.findByName("ACCT 100 LEVEL (GRP A)")).thenReturn(Optional.of(groupA));
        when(courseRepository.findFirstByCodeOrderByIdAsc("ACC 102")).thenReturn(Optional.empty());
        when(courseRepository.findByCode("ACC 102")).thenReturn(List.of());

        Map<String, String> row1 = Map.of(
                "code", "ACC 102",
                "name", "Introduction to Financial Accounting II",
                "weeklyHours", "3",
                "lecturerEmail", "",
                "isOnline", "false",
                "studentGroupNames", "ACCT 100 LEVEL (GRP A)");
        Map<String, String> row2 = Map.of(
                "code", "ACC 102",
                "name", "Introduction to Financial Accounting II",
                "weeklyHours", "3",
                "lecturerEmail", "",
                "isOnline", "false",
                "studentGroupNames", "ACCT 100 LEVEL (GRP A)");

        ImportWithResolutionsRequest request = ImportWithResolutionsRequest.builder()
                .rows(List.of(row1, row2))
                .resolutions(Map.of())
                .build();

        BulkImportService.BulkImportException ex = assertThrows(
                BulkImportService.BulkImportException.class,
                () -> bulkImportService.importCoursesWithResolutions(request));

        assertTrue(ex.getResult().getRowErrors().stream()
                .anyMatch(err -> err.getMessage().contains("Duplicate course-group assignment")));
        verify(courseRepository, never()).saveAll(anyList());
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

