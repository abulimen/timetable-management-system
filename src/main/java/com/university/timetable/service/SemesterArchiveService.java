package com.university.timetable.service;

import com.university.timetable.domain.SemesterArchive;
import com.university.timetable.dto.ArchiveRequestDTO;
import com.university.timetable.dto.TimetableViewDTO;
import com.university.timetable.repository.SemesterArchiveRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing semester archives.
 * Handles archiving current data to prefixed tables and querying archived data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterArchiveService {

    private final SemesterArchiveRepository archiveRepository;
    private final JdbcTemplate jdbcTemplate;
    
    @PersistenceContext
    private EntityManager entityManager;

    // Tables to archive (semester-specific data)
    private static final String[] TABLES_TO_ARCHIVE = {
        "course", "lesson", "student_group", "lecturer",
        "lecturer_unavailability", "course_allowed_zone", 
        "course_feature", "course_student_group"
    };

    /**
     * Get all archived semesters.
     */
    public List<SemesterArchive> getAllArchives() {
        return archiveRepository.findAllByOrderByArchivedAtDesc();
    }

    /**
     * Get a specific archive by code.
     */
    public SemesterArchive getArchive(String code) {
        return archiveRepository.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Archive not found: " + code));
    }

    /**
     * Archive current semester data to prefixed tables, then clear main tables.
     */
    @Transactional
    public SemesterArchive archiveCurrentSemester(ArchiveRequestDTO request) {
        String code = request.getCode();
        String prefix = code.replace("/", "_").replace("-", "_");
        
        log.info("Archiving current semester as: {}", code);
        
        // Validate code doesn't exist
        if (archiveRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Archive already exists: " + code);
        }
        
        // Count current data
        int courseCount = countTable("course");
        int lessonCount = countTable("lesson");
        int studentGroupCount = countTable("student_group");
        int lecturerCount = countTable("lecturer");
        
        if (courseCount == 0 && lessonCount == 0) {
            throw new IllegalArgumentException("No data to archive");
        }
        
        // Create archive tables and copy data
        for (String table : TABLES_TO_ARCHIVE) {
            String archiveTable = prefix + "_" + table;
            createArchiveTable(table, archiveTable);
        }
        
        // Clear main tables (in correct order due to foreign keys)
        clearMainTables();
        
        // Save archive metadata
        SemesterArchive archive = new SemesterArchive();
        archive.setCode(code);
        archive.setName(request.getName() != null ? request.getName() : code);
        archive.setAcademicYear(request.getAcademicYear());
        archive.setSemesterNumber(request.getSemesterNumber());
        archive.setTablesPrefix(prefix);
        archive.setArchivedAt(LocalDateTime.now());
        archive.setCourseCount(courseCount);
        archive.setLessonCount(lessonCount);
        archive.setStudentGroupCount(studentGroupCount);
        archive.setLecturerCount(lecturerCount);
        
        SemesterArchive saved = archiveRepository.save(archive);
        log.info("Archived {} courses, {} lessons, {} groups, {} lecturers",
            courseCount, lessonCount, studentGroupCount, lecturerCount);
        
        return saved;
    }

    /**
     * Delete an archive and its tables.
     */
    @Transactional
    public void deleteArchive(String code) {
        SemesterArchive archive = getArchive(code);
        String prefix = archive.getTablesPrefix();
        
        log.info("Deleting archive: {}", code);
        
        // Drop archive tables
        for (String table : TABLES_TO_ARCHIVE) {
            String archiveTable = prefix + "_" + table;
            dropTableIfExists(archiveTable);
        }
        
        // Delete metadata
        archiveRepository.delete(archive);
        log.info("Archive deleted: {}", code);
    }

    /**
     * Get timetable from an archived semester.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<TimetableViewDTO> getArchivedTimetable(String code) {
        SemesterArchive archive = getArchive(code);
        String prefix = archive.getTablesPrefix();
        
        String sql = """
            SELECT 
                l.id as lesson_id,
                l.part_number,
                l.duration_hours,
                l.is_pinned as pinned,
                c.code as course_code,
                c.name as course_name,
                t.day_of_week,
                t.start_time,
                r.id as room_id,
                r.name as room_name,
                r.capacity as room_capacity,
                lec.id as lecturer_id,
                lec.name as lecturer_name,
                sg.id as student_group_id,
                sg.name as student_group_name,
                sg.size as student_group_size
            FROM %s_lesson l
            LEFT JOIN %s_course c ON l.course_id = c.id
            LEFT JOIN timeslot t ON l.assigned_timeslot_id = t.id
            LEFT JOIN room r ON l.assigned_room_id = r.id
            LEFT JOIN %s_lecturer lec ON l.lecturer_id = lec.id
            LEFT JOIN %s_student_group sg ON c.student_group_id = sg.id
            WHERE l.assigned_timeslot_id IS NOT NULL
            ORDER BY t.day_of_week, t.start_time
            """.formatted(prefix, prefix, prefix, prefix);
        
        List<Object[]> results = entityManager.createNativeQuery(sql).getResultList();
        List<TimetableViewDTO> timetable = new ArrayList<>();
        
        for (Object[] row : results) {
            TimetableViewDTO dto = new TimetableViewDTO();
            dto.setLessonId(((Number) row[0]).longValue());
            dto.setPartNumber((Integer) row[1]);
            dto.setDurationHours((Integer) row[2]);
            dto.setPinned(row[3] != null && (Boolean) row[3]);
            dto.setCourseCode((String) row[4]);
            dto.setCourseName((String) row[5]);
            if (row[6] != null) {
                dto.setDayOfWeek(java.time.DayOfWeek.valueOf((String) row[6]));
            }
            if (row[7] != null) {
                dto.setStartTime(((java.sql.Time) row[7]).toLocalTime());
            }
            if (row[8] != null) dto.setRoomId(((Number) row[8]).longValue());
            dto.setRoomName((String) row[9]);
            if (row[10] != null) dto.setRoomCapacity((Integer) row[10]);
            if (row[11] != null) dto.setLecturerId(((Number) row[11]).longValue());
            dto.setLecturerName((String) row[12]);
            if (row[13] != null) dto.setStudentGroupId(((Number) row[13]).longValue());
            dto.setStudentGroupName((String) row[14]);
            if (row[15] != null) dto.setStudentGroupSize((Integer) row[15]);
            dto.setScheduled(dto.getStartTime() != null && dto.getRoomId() != null);
            
            timetable.add(dto);
        }
        
        return timetable;
    }

    // === Private Helper Methods ===

    private int countTable(String table) {
        String sql = "SELECT COUNT(*) FROM " + table;
        Number count = (Number) entityManager.createNativeQuery(sql).getSingleResult();
        return count.intValue();
    }

    private void createArchiveTable(String sourceTable, String archiveTable) {
        // Check if archive table already exists
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM " + archiveTable + " LIMIT 1", Integer.class);
            log.warn("Archive table already exists: {}", archiveTable);
            return;
        } catch (Exception e) {
            // Table doesn't exist, create it
        }
        
        String sql = "CREATE TABLE " + archiveTable + " AS SELECT * FROM " + sourceTable;
        jdbcTemplate.execute(sql);
        log.info("Created archive table: {}", archiveTable);
    }

    private void clearMainTables() {
        // Disable foreign key checks temporarily
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        
        // Clear in reverse order (children first)
        String[] clearOrder = {
            "course_student_group", "course_feature", "course_allowed_zone",
            "lecturer_unavailability", "lesson", "course", 
            "student_group", "lecturer"
        };
        
        for (String table : clearOrder) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
                log.info("Cleared table: {}", table);
            } catch (Exception e) {
                log.warn("Could not clear table {}: {}", table, e.getMessage());
            }
        }
        
        // Re-enable foreign key checks
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private void dropTableIfExists(String table) {
        try {
            String sql = "DROP TABLE IF EXISTS " + table;
            jdbcTemplate.execute(sql);
            log.info("Dropped table: {}", table);
        } catch (Exception e) {
            log.warn("Could not drop table {}: {}", table, e.getMessage());
        }
    }
}
