package com.university.timetable.service;

import com.university.timetable.domain.SemesterArchive;
import com.university.timetable.dto.ArchivedSpecialEventDTO;
import com.university.timetable.dto.ArchivedStudentGroupDTO;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing semester archives.
 * Handles full-state snapshots and archived timetable access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterArchiveService {

    private final SemesterArchiveRepository archiveRepository;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // Tables to clear for semester rollover only (master entities remain persistent).
    private static final List<String> SEMESTER_RESET_TABLES = List.of(
            "course_student_group", "course_feature", "course_allowed_zone",
            "lesson", "special_event", "availability_change_requests",
            "lecturer_unavailability", "course", "student_group"
    );

    private static final Set<String> SNAPSHOT_EXCLUDED_TABLES = Set.of(
            "semester_archive", "flyway_schema_history"
    );

    // Keep snapshots bounded to real application tables only.
    private static final Set<String> SNAPSHOT_INCLUDED_TABLES = Set.of(
            "availability_change_requests",
            "audit_log",
            "constraint_setting",
            "course",
            "course_allowed_zone",
            "course_feature",
            "course_student_group",
            "feature",
            "import_batch",
            "import_history",
            "lecturer",
            "lecturer_unavailability",
            "lesson",
            "refresh_tokens",
            "room",
            "room_feature",
            "solver_run_metric",
            "special_event",
            "special_event_student_group",
            "student_group",
            "timeslot",
            "users",
            "zone"
    );

    private static final int MYSQL_MAX_IDENTIFIER_LENGTH = 64;
    private static final int ARCHIVE_PREFIX_MAX_LENGTH = 35;
    private static final int ARCHIVE_CODE_MAX_LENGTH = 20;

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
     * Archive current state to prefixed tables, then clear semester-only runtime tables.
     */
    @Transactional
    public SemesterArchive archiveCurrentSemester(ArchiveRequestDTO request) {
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Code is required");
        }
        code = normalizeArchiveCode(code);

        String prefix = toPrefix(code);
        log.info("Archiving current state as: {}", code);

        if (archiveRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Archive already exists: " + code);
        }

        int courseCount = countSafe("course");
        int lessonCount = countSafe("lesson");
        int studentGroupCount = countSafe("student_group");
        int lecturerCount = countSafe("lecturer");

        List<String> activeTables = getAllActiveTablesForSnapshot();
        if (activeTables.isEmpty()) {
            throw new IllegalArgumentException("No tables found to archive");
        }

        createSnapshotTables(prefix, activeTables);

        // Semester rollover: clear only semester-scoped data.
        clearSemesterTablesOnly();

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
        log.info("Archived snapshot {} with {} tables", code, activeTables.size());
        return saved;
    }

    /**
     * Restore an archive to active tables. Creates a backup snapshot first.
     */
    @Transactional
    public Map<String, Object> restoreArchive(String code) {
        SemesterArchive archive = getArchive(code);

        String backupCode = createAutomaticBackupCode();
        String backupName = "Auto backup before restoring " + code;
        archiveCurrentState(new ArchiveRequestDTO(backupCode, backupName, null, null), false);

        String prefix = archive.getTablesPrefix();
        List<String> allTables = listAllDatabaseTables();
        List<String> archiveTables = allTables.stream()
                .filter(table -> table.startsWith(prefix + "_"))
                .toList();

        if (archiveTables.isEmpty()) {
            throw new IllegalArgumentException("Archive tables not found for: " + code);
        }

        Set<String> currentTables = new HashSet<>(allTables);
        List<String> targetTables = archiveTables.stream()
                .map(t -> t.substring((prefix + "_").length()))
                .filter(currentTables::contains)
                .filter(t -> !SNAPSHOT_EXCLUDED_TABLES.contains(t))
                .sorted(Comparator.naturalOrder())
                .toList();

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            // Use DELETE instead of TRUNCATE - TRUNCATE causes implicit commit breaking transaction rollback
            for (String table : targetTables) {
                jdbcTemplate.execute("DELETE FROM " + quoteIdentifier(table));
            }
            for (String table : targetTables) {
                String archiveTable = prefix + "_" + table;
                restoreTableWithColumnMatching(table, archiveTable);
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }

        return Map.of(
                "restoredFrom", code,
                "backupCode", backupCode,
                "restoredTableCount", targetTables.size()
        );
    }

    /**
     * Delete an archive and its snapshot tables.
     */
    @Transactional
    public void deleteArchive(String code) {
        SemesterArchive archive = getArchive(code);
        String prefix = archive.getTablesPrefix();

        log.info("Deleting archive: {}", code);

        listAllDatabaseTables().stream()
                .filter(table -> table.startsWith(prefix + "_"))
                .forEach(this::dropTableIfExists);

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
                ar.id as room_id,
                ar.name as room_name,
                ar.capacity as room_capacity,
                lec.id as lecturer_id,
                lec.name as lecturer_name,
                COALESCE(MIN(sg.id), legacy_sg.id) as student_group_id,
                COALESCE(
                    NULLIF(GROUP_CONCAT(DISTINCT sg.name ORDER BY sg.name SEPARATOR '||'), ''),
                    legacy_sg.name
                ) as student_group_names,
                COALESCE(
                    CASE WHEN COUNT(sg.id) > 0 THEN SUM(sg.size) END,
                    legacy_sg.size,
                    0
                ) as total_student_count,
                CASE
                    WHEN COUNT(sg.id) > 1 THEN true
                    ELSE false
                END as combined
            FROM %s l
            LEFT JOIN %s c ON l.course_id = c.id
            LEFT JOIN %s t ON l.assigned_timeslot_id = t.id
            LEFT JOIN %s ar ON l.assigned_room_id = ar.id
            LEFT JOIN %s lec ON l.lecturer_id = lec.id
            LEFT JOIN %s csg ON c.id = csg.course_id
            LEFT JOIN %s sg ON csg.student_group_id = sg.id
            LEFT JOIN %s legacy_sg ON c.student_group_id = legacy_sg.id
            WHERE l.assigned_timeslot_id IS NOT NULL
            GROUP BY l.id, l.part_number, l.duration_hours, l.is_pinned, c.code, c.name, t.day_of_week, t.start_time,
                     ar.id, ar.name, ar.capacity, lec.id, lec.name, legacy_sg.id, legacy_sg.name, legacy_sg.size
            ORDER BY t.day_of_week, t.start_time
            """.formatted(
                qualifiedTable(prefix, "lesson"),
                qualifiedTable(prefix, "course"),
                qualifiedTable(prefix, "timeslot"),
                qualifiedTable(prefix, "room"),
                qualifiedTable(prefix, "lecturer"),
                qualifiedTable(prefix, "course_student_group"),
                qualifiedTable(prefix, "student_group"),
                qualifiedTable(prefix, "student_group")
        );

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
            if (row[13] != null) {
                dto.setStudentGroupId(((Number) row[13]).longValue());
            }

            String groupNamesRaw = (String) row[14];
            int totalStudentCount = row[15] != null ? ((Number) row[15]).intValue() : 0;
            boolean combined = false;
            if (row[16] instanceof Boolean b) {
                combined = b;
            } else if (row[16] instanceof Number n) {
                combined = n.intValue() != 0;
            }

            if (groupNamesRaw != null && !groupNamesRaw.isBlank()) {
                List<String> groupNames = Arrays.stream(groupNamesRaw.split("\\|\\|"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (!groupNames.isEmpty()) {
                    dto.setStudentGroupName(groupNames.get(0));
                    dto.setCombinedGroupNames(groupNames);
                    dto.setCombined(groupNames.size() > 1 || combined);
                } else {
                    dto.setCombined(false);
                    dto.setCombinedGroupNames(List.of());
                }
            } else {
                dto.setCombined(false);
                dto.setCombinedGroupNames(List.of());
            }

            dto.setStudentGroupSize(totalStudentCount);
            dto.setTotalStudentCount(totalStudentCount);
            dto.setOnline(dto.getRoomId() == null);
            dto.setScheduled(dto.getStartTime() != null && (dto.isOnline() || dto.getRoomId() != null));
            if (dto.getStartTime() != null) {
                dto.setEndTime(dto.getStartTime().plusHours(dto.getDurationHours()));
            }

            timetable.add(dto);
        }

        return timetable;
    }

    @Transactional(readOnly = true)
    public List<ArchivedStudentGroupDTO> getArchivedStudentGroups(String code) {
        SemesterArchive archive = getArchive(code);
        String prefix = archive.getTablesPrefix();

        String sql = """
            SELECT
                sg.id,
                sg.name,
                sg.size,
                sg.parent_group_id,
                p.name as parent_name
            FROM %s sg
            LEFT JOIN %s p ON sg.parent_group_id = p.id
            ORDER BY sg.name
            """.formatted(
                qualifiedTable(prefix, "student_group"),
                qualifiedTable(prefix, "student_group")
        );

        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        Map<Long, Integer> childCounts = new HashMap<>();
        for (Object[] row : rows) {
            if (row[3] != null) {
                Long parentId = ((Number) row[3]).longValue();
                childCounts.merge(parentId, 1, Integer::sum);
            }
        }

        List<ArchivedStudentGroupDTO> groups = new ArrayList<>();
        for (Object[] row : rows) {
            ArchivedStudentGroupDTO dto = new ArchivedStudentGroupDTO();
            dto.setId(((Number) row[0]).longValue());
            dto.setName((String) row[1]);
            dto.setSize(row[2] != null ? ((Number) row[2]).intValue() : 0);
            dto.setParentGroupId(row[3] != null ? ((Number) row[3]).longValue() : null);
            dto.setParentGroupName((String) row[4]);
            dto.setChildCount(childCounts.getOrDefault(dto.getId(), 0));
            groups.add(dto);
        }
        return groups;
    }

    @Transactional(readOnly = true)
    public List<ArchivedSpecialEventDTO> getArchivedSpecialEvents(String code) {
        SemesterArchive archive = getArchive(code);
        String prefix = archive.getTablesPrefix();

        String sql = """
            SELECT
                se.id,
                se.name,
                se.description,
                se.day_of_week,
                se.start_time,
                se.duration_hours,
                r.id as room_id,
                r.name as room_name,
                lec.id as lecturer_id,
                lec.name as lecturer_name,
                se.is_online,
                se.is_active,
                GROUP_CONCAT(DISTINCT sg.id ORDER BY sg.id SEPARATOR ',') as group_ids,
                GROUP_CONCAT(DISTINCT sg.name ORDER BY sg.name SEPARATOR '||') as group_names
            FROM %s se
            LEFT JOIN %s r ON se.room_id = r.id
            LEFT JOIN %s lec ON se.lecturer_id = lec.id
            LEFT JOIN %s sesg ON se.id = sesg.special_event_id
            LEFT JOIN %s sg ON sesg.student_group_id = sg.id
            WHERE se.is_active = true
            GROUP BY se.id, se.name, se.description, se.day_of_week, se.start_time, se.duration_hours,
                     r.id, r.name, lec.id, lec.name, se.is_online, se.is_active
            ORDER BY se.day_of_week, se.start_time
            """.formatted(
                qualifiedTable(prefix, "special_event"),
                qualifiedTable(prefix, "room"),
                qualifiedTable(prefix, "lecturer"),
                qualifiedTable(prefix, "special_event_student_group"),
                qualifiedTable(prefix, "student_group")
        );

        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        List<ArchivedSpecialEventDTO> events = new ArrayList<>();
        for (Object[] row : rows) {
            ArchivedSpecialEventDTO dto = new ArchivedSpecialEventDTO();
            dto.setId(((Number) row[0]).longValue());
            dto.setName((String) row[1]);
            dto.setDescription((String) row[2]);
            dto.setDayOfWeek((String) row[3]);
            if (row[4] != null) {
                java.time.LocalTime start = ((java.sql.Time) row[4]).toLocalTime();
                int duration = row[5] != null ? ((Number) row[5]).intValue() : 1;
                dto.setStartTime(start.toString());
                dto.setDurationHours(duration);
                dto.setEndTime(start.plusHours(duration).toString());
            } else {
                dto.setStartTime(null);
                dto.setDurationHours(1);
                dto.setEndTime(null);
            }
            dto.setRoomId(row[6] != null ? ((Number) row[6]).longValue() : null);
            dto.setRoomName((String) row[7]);
            dto.setLecturerId(row[8] != null ? ((Number) row[8]).longValue() : null);
            dto.setLecturerName((String) row[9]);
            dto.setOnline(row[10] != null && toBoolean(row[10]));
            dto.setActive(row[11] == null || toBoolean(row[11]));

            String groupIdsRaw = (String) row[12];
            String groupNamesRaw = (String) row[13];
            List<Long> groupIds = groupIdsRaw == null || groupIdsRaw.isBlank()
                    ? List.of()
                    : Arrays.stream(groupIdsRaw.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
            List<String> groupNames = groupNamesRaw == null || groupNamesRaw.isBlank()
                    ? List.of()
                    : Arrays.stream(groupNamesRaw.split("\\|\\|"))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.toList());
            dto.setStudentGroupIds(groupIds);
            dto.setStudentGroupNames(groupNames);
            events.add(dto);
        }
        return events;
    }

    private int countTable(String table) {
        String sql = "SELECT COUNT(*) FROM " + quoteIdentifier(table);
        Number count = (Number) entityManager.createNativeQuery(sql).getSingleResult();
        return count.intValue();
    }

    private int countSafe(String table) {
        try {
            return countTable(table);
        } catch (Exception e) {
            return 0;
        }
    }

    private void createArchiveTable(String sourceTable, String archiveTable) {
        String quotedSource = quoteIdentifier(sourceTable);
        String quotedArchive = quoteIdentifier(archiveTable);

        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM " + quotedArchive + " LIMIT 1", Integer.class);
            log.warn("Archive table already exists: {}", archiveTable);
            return;
        } catch (Exception e) {
            // Table does not exist.
        }

        String sql = "CREATE TABLE " + quotedArchive + " AS SELECT * FROM " + quotedSource;
        jdbcTemplate.execute(sql);
        log.info("Created archive table: {}", archiveTable);
    }

    private void clearSemesterTablesOnly() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : SEMESTER_RESET_TABLES) {
                try {
                    jdbcTemplate.execute("TRUNCATE TABLE " + quoteIdentifier(table));
                    log.info("Cleared table: {}", table);
                } catch (Exception e) {
                    log.warn("Could not clear table {}: {}", table, e.getMessage());
                }
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void createSnapshotTables(String prefix, List<String> sourceTables) {
        for (String table : sourceTables) {
            String archiveTable = buildArchiveTableName(prefix, table);
            createArchiveTable(table, archiveTable);
        }
    }

    private List<String> listAllDatabaseTables() {
        return jdbcTemplate.query("SHOW TABLES", (rs, rowNum) -> rs.getString(1));
    }

    private List<String> getAllActiveTablesForSnapshot() {
        Set<String> archivePrefixes = archiveRepository.findAll().stream()
                .map(SemesterArchive::getTablesPrefix)
                .collect(HashSet::new, Set::add, Set::addAll);

        return listAllDatabaseTables().stream()
                .filter(SNAPSHOT_INCLUDED_TABLES::contains)
                .filter(table -> !SNAPSHOT_EXCLUDED_TABLES.contains(table))
                .filter(table -> archivePrefixes.stream().noneMatch(prefix -> table.startsWith(prefix + "_")))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private void archiveCurrentState(ArchiveRequestDTO request, boolean clearAfterArchive) {
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Code is required");
        }
        code = normalizeArchiveCode(code);
        if (archiveRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Archive already exists: " + code);
        }

        String prefix = toPrefix(code);
        List<String> activeTables = getAllActiveTablesForSnapshot();
        createSnapshotTables(prefix, activeTables);

        SemesterArchive archive = new SemesterArchive();
        archive.setCode(code);
        archive.setName(request.getName() != null ? request.getName() : code);
        archive.setAcademicYear(request.getAcademicYear());
        archive.setSemesterNumber(request.getSemesterNumber());
        archive.setTablesPrefix(prefix);
        archive.setArchivedAt(LocalDateTime.now());
        archive.setCourseCount(countSafe("course"));
        archive.setLessonCount(countSafe("lesson"));
        archive.setStudentGroupCount(countSafe("student_group"));
        archive.setLecturerCount(countSafe("lecturer"));
        archiveRepository.save(archive);

        if (clearAfterArchive) {
            clearSemesterTablesOnly();
        }
    }

    private void dropTableIfExists(String table) {
        try {
            String sql = "DROP TABLE IF EXISTS " + quoteIdentifier(table);
            jdbcTemplate.execute(sql);
            log.info("Dropped table: {}", table);
        } catch (Exception e) {
            log.warn("Could not drop table {}: {}", table, e.getMessage());
        }
    }

    private String toPrefix(String code) {
        String normalized = code == null ? "" : code.trim().toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");

        if (normalized.isBlank()) {
            normalized = "archive";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = "a_" + normalized;
        }
        if (normalized.length() > ARCHIVE_PREFIX_MAX_LENGTH) {
            String hash = shortHash(normalized);
            int headLength = Math.max(1, ARCHIVE_PREFIX_MAX_LENGTH - hash.length() - 1);
            normalized = normalized.substring(0, headLength) + "_" + hash;
        }
        return normalized;
    }

    private String createAutomaticBackupCode() {
        int attempts = 0;
        while (attempts < 5) {
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyMMdd_HHmmss")
                    .format(LocalDateTime.now().withNano(0));
            String base = "ABK_" + timestamp; // length 17
            String candidate = normalizeArchiveCode(base);
            int suffix = 1;
            while (archiveRepository.existsByCode(candidate)) {
                if (suffix > 99) {
                    break;
                }
                candidate = base + "_" + suffix++;
            }
            if (!archiveRepository.existsByCode(candidate)) {
                return candidate;
            }
            attempts++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while generating backup code", ie);
            }
        }
        throw new IllegalStateException("Could not generate a unique backup code. Please retry.");
    }

    private String normalizeArchiveCode(String code) {
        String trimmed = code.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Code is required");
        }
        if (trimmed.length() > ARCHIVE_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException("Code must be at most " + ARCHIVE_CODE_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private String buildArchiveTableName(String prefix, String sourceTable) {
        String raw = prefix + "_" + sourceTable;
        if (raw.length() <= MYSQL_MAX_IDENTIFIER_LENGTH) {
            return raw;
        }

        String hash = shortHash(raw);
        int maxPrefixLength = Math.max(1, MYSQL_MAX_IDENTIFIER_LENGTH - 1 - sourceTable.length() - 1 - hash.length());
        String trimmedPrefix = prefix.substring(0, Math.min(prefix.length(), maxPrefixLength));
        return trimmedPrefix + "_" + sourceTable + "_" + hash;
    }

    private String qualifiedTable(String prefix, String baseTable) {
        return quoteIdentifier(prefix + "_" + baseTable);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Restore table data with column matching to handle schema differences.
     * Uses explicit column lists instead of SELECT * to handle cases where
     * the archive table has fewer columns than the current schema (migrations added columns).
     */
    private void restoreTableWithColumnMatching(String targetTable, String archiveTable) {
        // Get columns from both tables
        List<String> targetColumns = getTableColumns(targetTable);
        List<String> archiveColumns = getTableColumns(archiveTable);
        
        // Find common columns (intersection)
        List<String> commonColumns = targetColumns.stream()
                .filter(archiveColumns::contains)
                .toList();
        
        if (commonColumns.isEmpty()) {
            log.warn("No common columns between {} and {}, skipping restore", targetTable, archiveTable);
            return;
        }
        
        String columnList = commonColumns.stream()
                .map(this::quoteIdentifier)
                .collect(Collectors.joining(", "));
        
        String sql = String.format("INSERT INTO %s (%s) SELECT %s FROM %s",
                quoteIdentifier(targetTable), columnList, columnList, quoteIdentifier(archiveTable));
        
        jdbcTemplate.execute(sql);
        log.info("Restored table {} with {} common columns from {}", targetTable, commonColumns.size(), archiveTable);
    }
    
    /**
     * Get column names for a table from information_schema.
     */
    private List<String> getTableColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("COLUMN_NAME"), tableName);
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private String shortHash(String value) {
        String hex = Integer.toHexString(Math.abs(value.hashCode()));
        return hex.length() > 8 ? hex.substring(0, 8) : hex;
    }
}
