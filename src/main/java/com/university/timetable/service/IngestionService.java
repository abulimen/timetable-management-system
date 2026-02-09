package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.dto.ImportResultDTO;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

/**
 * IngestionService - handles Excel file parsing and data import.
 * 
 * Based on design.md IngestionService specification:
 * - Parse Excel via Apache POI
 * - Run Validation Logic
 * - Save Entities
 * - Call LessonService.generateLessons(course) immediately
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final ValidationService validationService;
    private final LessonService lessonService;
    private final ZoneRepository zoneRepository;
    private final FeatureRepository featureRepository;
    private final RoomRepository roomRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final CourseRepository courseRepository;

    /**
     * Import data from an Excel file.
     * Expected sheets: Zones, Features, Rooms, Lecturers, StudentGroups, Courses
     */
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file) throws IOException {
        log.info("Starting Excel import: {}", file.getOriginalFilename());
        ImportResultDTO result = new ImportResultDTO();

        try (InputStream is = file.getInputStream();
                Workbook workbook = new XSSFWorkbook(is)) {

            // Process sheets in dependency order
            processZonesSheet(workbook.getSheet("Zones"), result);
            processFeaturesSheet(workbook.getSheet("Features"), result);
            processRoomsSheet(workbook.getSheet("Rooms"), result);
            processLecturersSheet(workbook.getSheet("Lecturers"), result);
            processStudentGroupsSheet(workbook.getSheet("StudentGroups"), result);
            processCoursesSheet(workbook.getSheet("Courses"), result);
        }

        log.info("Import complete: {} total, {} success, {} errors, {} warnings",
                result.getTotalRows(), result.getSuccessfulImports(),
                result.getErrors().size(), result.getWarnings().size());

        return result;
    }

    private void processZonesSheet(Sheet sheet, ImportResultDTO result) {
        if (sheet == null) {
            log.warn("No 'Zones' sheet found in Excel file");
            return;
        }

        log.info("Processing Zones sheet...");
        for (Row row : sheet) {
            if (row.getRowNum() == 0)
                continue; // Skip header
            result.incrementTotal();

            String name = getCellStringValue(row.getCell(0));
            if (name == null || name.isEmpty())
                continue;

            name = validationService.sanitizeString(name);

            if (!zoneRepository.existsByName(name)) {
                Zone zone = new Zone(name);
                zoneRepository.save(zone);
                result.incrementSuccess();
            }
        }
    }

    private void processFeaturesSheet(Sheet sheet, ImportResultDTO result) {
        if (sheet == null) {
            log.warn("No 'Features' sheet found in Excel file");
            return;
        }

        log.info("Processing Features sheet...");
        for (Row row : sheet) {
            if (row.getRowNum() == 0)
                continue;
            result.incrementTotal();

            String name = getCellStringValue(row.getCell(0));
            if (name == null || name.isEmpty())
                continue;

            name = validationService.sanitizeString(name);

            if (!featureRepository.existsByName(name)) {
                Feature feature = new Feature(name);
                featureRepository.save(feature);
                result.incrementSuccess();
            }
        }
    }

    private void processRoomsSheet(Sheet sheet, ImportResultDTO result) {
        if (sheet == null) {
            log.warn("No 'Rooms' sheet found in Excel file");
            return;
        }

        log.info("Processing Rooms sheet...");
        for (Row row : sheet) {
            if (row.getRowNum() == 0)
                continue;
            result.incrementTotal();

            String name = getCellStringValue(row.getCell(0));
            Integer capacity = getCellIntValue(row.getCell(1));
            String zoneName = getCellStringValue(row.getCell(2));
            String featuresCsv = getCellStringValue(row.getCell(3));

            // Validate
            boolean valid = true;
            if (!validationService.validateRequired(name, "name", result, row.getRowNum())) {
                valid = false;
            }
            if (!validationService.validatePositiveInteger(capacity, "capacity", result, row.getRowNum())) {
                valid = false;
            }
            if (zoneName != null && !zoneName.isEmpty()) {
                if (!validationService.validateZonesExist(List.of(zoneName), result, row.getRowNum())) {
                    valid = false;
                }
            }

            if (!valid)
                continue;

            name = validationService.sanitizeString(name);
            Zone zone = zoneName != null
                    ? zoneRepository.findByName(validationService.sanitizeString(zoneName)).orElse(null)
                    : null;

            Room room = new Room(name, capacity, zone);

            // Add features
            if (featuresCsv != null && !featuresCsv.isEmpty()) {
                for (String featureName : featuresCsv.split(",")) {
                    featureRepository.findByName(validationService.sanitizeString(featureName.trim()))
                            .ifPresent(room.getFeatures()::add);
                }
            }

            roomRepository.save(room);
            result.incrementSuccess();
        }
    }

    private void processLecturersSheet(Sheet sheet, ImportResultDTO result) {
        if (sheet == null) {
            log.warn("No 'Lecturers' sheet found in Excel file");
            return;
        }

        log.info("Processing Lecturers sheet...");
        for (Row row : sheet) {
            if (row.getRowNum() == 0)
                continue;
            result.incrementTotal();

            String name = getCellStringValue(row.getCell(0));
            String email = getCellStringValue(row.getCell(1));
            String unavailabilityCsv = getCellStringValue(row.getCell(2)); // Format: "MON:07:00-11:00,TUE:13:00-15:00"

            if (!validationService.validateRequired(name, "name", result, row.getRowNum())) {
                continue;
            }

            name = validationService.sanitizeString(name);

            Lecturer lecturer = new Lecturer(name, email);

            // Parse unavailability
            if (unavailabilityCsv != null && !unavailabilityCsv.isEmpty()) {
                for (String period : unavailabilityCsv.split(",")) {
                    try {
                        String[] parts = period.trim().split(":");
                        if (parts.length >= 3) {
                            DayOfWeek day = parseDayOfWeek(parts[0].trim());
                            String[] times = (parts[1] + ":" + parts[2]).split("-");
                            if (times.length == 2) {
                                LocalTime start = LocalTime.parse(times[0].trim());
                                LocalTime end = LocalTime.parse(times[1].trim());
                                LecturerUnavailability unavail = new LecturerUnavailability(day, start, end);
                                lecturer.addUnavailability(unavail);
                            }
                        }
                    } catch (Exception e) {
                        result.addWarning(row.getRowNum(), "unavailability",
                                period, null, "Could not parse unavailability period: " + e.getMessage());
                    }
                }
            }

            lecturerRepository.save(lecturer);
            result.incrementSuccess();
        }
    }

    private void processStudentGroupsSheet(Sheet sheet, ImportResultDTO result) {
        if (sheet == null) {
            log.warn("No 'StudentGroups' sheet found in Excel file");
            return;
        }

        log.info("Processing StudentGroups sheet...");
        // First pass: create all groups without parents
        Map<String, StudentGroup> groupMap = new HashMap<>();

        for (Row row : sheet) {
            if (row.getRowNum() == 0)
                continue;
            result.incrementTotal();

            String name = getCellStringValue(row.getCell(0));
            Integer size = getCellIntValue(row.getCell(1));

            if (!validationService.validateRequired(name, "name", result, row.getRowNum())) {
                continue;
            }
            if (!validationService.validatePositiveInteger(size, "size", result, row.getRowNum())) {
                continue;
            }

            name = validationService.sanitizeString(name);
            StudentGroup group = new StudentGroup();
            group.setName(name);
            group.setSize(size);
            group = studentGroupRepository.save(group);
            groupMap.put(name, group);
            result.incrementSuccess();
        }

        // Second pass: set parent relationships
        for (Row row : sheet) {
            if (row.getRowNum() == 0)
                continue;

            String name = getCellStringValue(row.getCell(0));
            String parentName = getCellStringValue(row.getCell(2));

            if (name == null || parentName == null || parentName.isEmpty())
                continue;

            name = validationService.sanitizeString(name);
            parentName = validationService.sanitizeString(parentName);

            StudentGroup group = groupMap.get(name);
            StudentGroup parent = groupMap.get(parentName);

            if (group != null && parent != null) {
                group.setParentGroup(parent);
                studentGroupRepository.save(group);
            }
        }
    }

    private void processCoursesSheet(Sheet sheet, ImportResultDTO result) {
        if (sheet == null) {
            log.warn("No 'Courses' sheet found in Excel file");
            return;
        }

        log.info("Processing Courses sheet...");
        for (Row row : sheet) {
            if (row.getRowNum() == 0)
                continue;
            result.incrementTotal();

            String code = getCellStringValue(row.getCell(0));
            String name = getCellStringValue(row.getCell(1));
            Integer weeklyHours = getCellIntValue(row.getCell(2));
            String lecturerName = getCellStringValue(row.getCell(3));
            String studentGroupName = getCellStringValue(row.getCell(4));
            String allowedZonesCsv = getCellStringValue(row.getCell(5));
            String requiredFeaturesCsv = getCellStringValue(row.getCell(6));

            // Validate
            boolean valid = true;
            if (!validationService.validateCourseCode(code, result, row.getRowNum())) {
                valid = false;
            }
            if (!validationService.validateRequired(name, "name", result, row.getRowNum())) {
                valid = false;
            }
            if (weeklyHours != null && !validationService.validateWeeklyHours(weeklyHours, result, row.getRowNum())) {
                valid = false;
            }

            if (!valid)
                continue;

            // Check for duplicate course code
            if (courseRepository.existsByCode(code)) {
                result.addError(row.getRowNum(), "code",
                        "Course code '" + code + "' already exists", "DUPLICATE_CODE");
                continue;
            }

            Course course = new Course(code, validationService.sanitizeString(name),
                    weeklyHours != null ? weeklyHours : 0);

            // Set lecturer
            if (lecturerName != null && !lecturerName.isEmpty()) {
                lecturerRepository.findByName(validationService.sanitizeString(lecturerName))
                        .ifPresent(course::setLecturer);
            }

            // Set student group
            if (studentGroupName != null && !studentGroupName.isEmpty()) {
                studentGroupRepository.findByName(validationService.sanitizeString(studentGroupName))
                        .ifPresent(course::setStudentGroup);
            }

            // Set allowed zones
            if (allowedZonesCsv != null && !allowedZonesCsv.isEmpty()) {
                for (String zoneName : allowedZonesCsv.split(",")) {
                    zoneRepository.findByName(validationService.sanitizeString(zoneName.trim()))
                            .ifPresent(course.getAllowedZones()::add);
                }
            }

            // Set required features
            if (requiredFeaturesCsv != null && !requiredFeaturesCsv.isEmpty()) {
                for (String featureName : requiredFeaturesCsv.split(",")) {
                    featureRepository.findByName(validationService.sanitizeString(featureName.trim()))
                            .ifPresent(course.getRequiredFeatures()::add);
                }
            }

            course = courseRepository.save(course);

            // CRITICAL: Generate lessons immediately after saving course
            lessonService.generateLessons(course);

            result.incrementSuccess();
        }
    }

    // Helper methods
    private String getCellStringValue(Cell cell) {
        if (cell == null)
            return null;
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue();
    }

    private Integer getCellIntValue(Cell cell) {
        if (cell == null)
            return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                return Integer.parseInt(cell.getStringCellValue().trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private DayOfWeek parseDayOfWeek(String day) {
        return switch (day.toUpperCase().substring(0, 3)) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Unknown day: " + day);
        };
    }
}
