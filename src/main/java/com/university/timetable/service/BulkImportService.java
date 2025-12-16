package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for bulk importing data from CSV files with advanced validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportService {

    private final LecturerRepository lecturerRepository;
    private final RoomRepository roomRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final ZoneRepository zoneRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final FeatureRepository featureRepository;

    // Validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-\\s]+$");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_CODE_LENGTH = 20;
    private static final int MAX_CAPACITY = 10000;
    private static final int MAX_GROUP_SIZE = 5000;
    private static final int MAX_WEEKLY_HOURS = 40;

    /**
     * Get CSV template content for an entity type.
     */
    public String getTemplate(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "lecturers" -> "name,email\nJohn Smith,john.smith@university.edu\nJane Doe,jane.doe@university.edu";
            case "rooms" -> "name,capacity,zone_name\nRoom A101,50,Building A\nRoom B202,100,Building B\nLab C301,30,Building C";
            case "student-groups", "studentgroups" -> "name,size,parent_group_name\nCOSC_Year1,120,\nCOSC_1A,40,COSC_Year1\nCOSC_1B,40,COSC_Year1\nCOSC_1C,40,COSC_Year1";
            case "zones" -> "name\nBuilding A\nBuilding B\nLaboratory Wing\nScience Complex";
            case "features" -> "name\nProjector\nWhiteboard\nComputers\nLab Equipment\nWet Lab";
            case "courses" -> "code,name,weekly_hours,lecturer_email,student_group_name,is_online\nCOSC101,Introduction to Programming,3,john.smith@university.edu,COSC_1A,false\nCOSC102,Data Structures,2,jane.doe@university.edu,COSC_1B,false\nONL101,Introduction to Online Learning,2,john.smith@university.edu,COSC_1A,true";
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    /**
     * Import lecturers from CSV with validation.
     */
    @Transactional
    public Map<String, Object> importLecturers(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2; // +2 for header and 0-index
            try {
                // Validate required fields
                if (row.length < 1 || row[0].trim().isEmpty()) {
                    errors.add("Row " + rowNum + ": Name is required");
                    skipped++;
                    continue;
                }
                
                String name = sanitize(row[0].trim());
                String email = row.length > 1 ? sanitize(row[1].trim()) : null;

                // Validate name
                if (name.length() > MAX_NAME_LENGTH) {
                    errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                    skipped++;
                    continue;
                }
                if (!ALPHANUMERIC_PATTERN.matcher(name.replaceAll("[.,']", "")).matches()) {
                    errors.add("Row " + rowNum + ": Name contains invalid characters");
                    skipped++;
                    continue;
                }

                // Validate email if provided
                if (email != null && !email.isEmpty()) {
                    if (!EMAIL_PATTERN.matcher(email).matches()) {
                        errors.add("Row " + rowNum + ": Invalid email format '" + email + "'");
                        skipped++;
                        continue;
                    }
                    if (seenEmails.contains(email.toLowerCase())) {
                        errors.add("Row " + rowNum + ": Duplicate email in CSV '" + email + "'");
                        skipped++;
                        continue;
                    }
                    if (lecturerRepository.findByEmail(email).isPresent()) {
                        errors.add("Row " + rowNum + ": Email already exists '" + email + "'");
                        skipped++;
                        continue;
                    }
                    seenEmails.add(email.toLowerCase());
                }

                // Check duplicate name in CSV
                if (seenNames.contains(name.toLowerCase())) {
                    errors.add("Row " + rowNum + ": Duplicate name in CSV '" + name + "'");
                    skipped++;
                    continue;
                }
                seenNames.add(name.toLowerCase());

                // Check if already exists in DB
                if (lecturerRepository.findByName(name).isPresent()) {
                    skipped++;
                    continue;
                }

                Lecturer lecturer = new Lecturer();
                lecturer.setName(name);
                lecturer.setEmail(email);
                lecturerRepository.save(lecturer);
                created++;
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                skipped++;
            }
        }

        log.info("Imported {} lecturers, skipped {}, errors {}", created, skipped, errors.size());
        return Map.of("created", created, "skipped", skipped, "errors", errors);
    }

    /**
     * Import rooms from CSV with validation.
     */
    @Transactional
    public Map<String, Object> importRooms(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            try {
                if (row.length < 2 || row[0].trim().isEmpty()) {
                    errors.add("Row " + rowNum + ": Name and capacity are required");
                    skipped++;
                    continue;
                }
                
                String name = sanitize(row[0].trim());
                String capacityStr = row[1].trim();
                String zoneName = row.length > 2 ? sanitize(row[2].trim()) : null;

                // Validate name
                if (name.length() > MAX_NAME_LENGTH) {
                    errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                    skipped++;
                    continue;
                }

                // Validate capacity
                int capacity;
                try {
                    capacity = Integer.parseInt(capacityStr);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": Capacity must be a number, got '" + capacityStr + "'");
                    skipped++;
                    continue;
                }
                if (capacity <= 0 || capacity > MAX_CAPACITY) {
                    errors.add("Row " + rowNum + ": Capacity must be between 1 and " + MAX_CAPACITY);
                    skipped++;
                    continue;
                }

                // Check zone exists if specified
                Zone zone = null;
                if (zoneName != null && !zoneName.isEmpty()) {
                    Optional<Zone> zoneOpt = zoneRepository.findByName(zoneName);
                    if (zoneOpt.isEmpty()) {
                        errors.add("Row " + rowNum + ": Zone '" + zoneName + "' not found - import zones first");
                        skipped++;
                        continue;
                    }
                    zone = zoneOpt.get();
                }

                // Check duplicates
                if (seenNames.contains(name.toLowerCase())) {
                    errors.add("Row " + rowNum + ": Duplicate room name in CSV '" + name + "'");
                    skipped++;
                    continue;
                }
                seenNames.add(name.toLowerCase());

                if (roomRepository.findByName(name).isPresent()) {
                    skipped++;
                    continue;
                }

                Room room = new Room();
                room.setName(name);
                room.setCapacity(capacity);
                room.setZone(zone);
                roomRepository.save(room);
                created++;
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                skipped++;
            }
        }

        log.info("Imported {} rooms, skipped {}, errors {}", created, skipped, errors.size());
        return Map.of("created", created, "skipped", skipped, "errors", errors);
    }

    /**
     * Import student groups from CSV with validation.
     */
    @Transactional
    public Map<String, Object> importStudentGroups(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        // First pass: create groups without parents
        Map<String, String> pendingParents = new LinkedHashMap<>();
        
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            try {
                if (row.length < 2 || row[0].trim().isEmpty()) {
                    errors.add("Row " + rowNum + ": Name and size are required");
                    skipped++;
                    continue;
                }
                
                String name = sanitize(row[0].trim());
                String sizeStr = row[1].trim();
                String parentName = row.length > 2 ? sanitize(row[2].trim()) : null;

                // Validate name
                if (name.length() > MAX_NAME_LENGTH) {
                    errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                    skipped++;
                    continue;
                }

                // Validate size
                int size;
                try {
                    size = Integer.parseInt(sizeStr);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": Size must be a number, got '" + sizeStr + "'");
                    skipped++;
                    continue;
                }
                if (size <= 0 || size > MAX_GROUP_SIZE) {
                    errors.add("Row " + rowNum + ": Size must be between 1 and " + MAX_GROUP_SIZE);
                    skipped++;
                    continue;
                }

                // Check duplicates
                if (seenNames.contains(name.toLowerCase())) {
                    errors.add("Row " + rowNum + ": Duplicate group name in CSV '" + name + "'");
                    skipped++;
                    continue;
                }
                seenNames.add(name.toLowerCase());

                if (studentGroupRepository.findByName(name).isPresent()) {
                    skipped++;
                    continue;
                }

                StudentGroup group = new StudentGroup();
                group.setName(name);
                group.setSize(size);
                studentGroupRepository.save(group);
                created++;
                
                // Remember parent for second pass
                if (parentName != null && !parentName.isEmpty()) {
                    pendingParents.put(name, parentName);
                }
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                skipped++;
            }
        }

        // Second pass: set parent relationships
        for (Map.Entry<String, String> entry : pendingParents.entrySet()) {
            String childName = entry.getKey();
            String parentName = entry.getValue();
            Optional<StudentGroup> childOpt = studentGroupRepository.findByName(childName);
            Optional<StudentGroup> parentOpt = studentGroupRepository.findByName(parentName);
            if (childOpt.isPresent() && parentOpt.isPresent()) {
                StudentGroup child = childOpt.get();
                child.setParentGroup(parentOpt.get());
                studentGroupRepository.save(child);
            } else if (parentOpt.isEmpty()) {
                errors.add("Warning: Parent group '" + parentName + "' not found for '" + childName + "'");
            }
        }

        log.info("Imported {} student groups, skipped {}, errors {}", created, skipped, errors.size());
        return Map.of("created", created, "skipped", skipped, "errors", errors);
    }

    /**
     * Import zones from CSV with validation.
     */
    @Transactional
    public Map<String, Object> importZones(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            try {
                if (row.length < 1 || row[0].trim().isEmpty()) {
                    errors.add("Row " + rowNum + ": Name is required");
                    skipped++;
                    continue;
                }
                
                String name = sanitize(row[0].trim());

                // Validate name
                if (name.length() > MAX_NAME_LENGTH) {
                    errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                    skipped++;
                    continue;
                }

                // Check duplicates
                if (seenNames.contains(name.toLowerCase())) {
                    errors.add("Row " + rowNum + ": Duplicate zone name in CSV '" + name + "'");
                    skipped++;
                    continue;
                }
                seenNames.add(name.toLowerCase());

                if (zoneRepository.findByName(name).isPresent()) {
                    skipped++;
                    continue;
                }

                Zone zone = new Zone();
                zone.setName(name);
                zoneRepository.save(zone);
                created++;
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                skipped++;
            }
        }

        log.info("Imported {} zones, skipped {}, errors {}", created, skipped, errors.size());
        return Map.of("created", created, "skipped", skipped, "errors", errors);
    }

    /**
     * Import features from CSV with validation.
     * Format: name
     */
    @Transactional
    public Map<String, Object> importFeatures(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            try {
                if (row.length < 1 || row[0].trim().isEmpty()) {
                    errors.add("Row " + rowNum + ": Name is required");
                    skipped++;
                    continue;
                }
                
                String name = sanitize(row[0].trim());

                // Validate name
                if (name.length() > MAX_NAME_LENGTH) {
                    errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                    skipped++;
                    continue;
                }

                // Check duplicates
                if (seenNames.contains(name.toLowerCase())) {
                    errors.add("Row " + rowNum + ": Duplicate feature name in CSV '" + name + "'");
                    skipped++;
                    continue;
                }
                seenNames.add(name.toLowerCase());

                if (featureRepository.findByName(name).isPresent()) {
                    skipped++;
                    continue;
                }

                Feature feature = new Feature();
                feature.setName(name);
                featureRepository.save(feature);
                created++;
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                skipped++;
            }
        }

        log.info("Imported {} features, skipped {}, errors {}", created, skipped, errors.size());
        return Map.of("created", created, "skipped", skipped, "errors", errors);
    }

    /**
     * Import courses from CSV with validation.
     */
    @Transactional
    public Map<String, Object> importCourses(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        int created = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            try {
                if (row.length < 3 || row[0].trim().isEmpty()) {
                    errors.add("Row " + rowNum + ": Code, name, and weekly hours are required");
                    skipped++;
                    continue;
                }
                
                String code = sanitize(row[0].trim()).toUpperCase();
                String name = sanitize(row[1].trim());
                String hoursStr = row[2].trim();
                String lecturerEmail = row.length > 3 ? sanitize(row[3].trim()) : null;
                String studentGroupName = row.length > 4 ? sanitize(row[4].trim()) : null;
                String isOnlineStr = row.length > 5 ? row[5].trim().toLowerCase() : "false";

                // Validate code
                if (code.length() > MAX_CODE_LENGTH) {
                    errors.add("Row " + rowNum + ": Code exceeds " + MAX_CODE_LENGTH + " characters");
                    skipped++;
                    continue;
                }
                if (!code.matches("^[A-Z0-9_\\-]+$")) {
                    errors.add("Row " + rowNum + ": Code must be alphanumeric (uppercase)");
                    skipped++;
                    continue;
                }

                // Validate name
                if (name.length() > MAX_NAME_LENGTH) {
                    errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                    skipped++;
                    continue;
                }

                // Validate weekly hours
                int weeklyHours;
                try {
                    weeklyHours = Integer.parseInt(hoursStr);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": Weekly hours must be a number, got '" + hoursStr + "'");
                    skipped++;
                    continue;
                }
                if (weeklyHours <= 0 || weeklyHours > MAX_WEEKLY_HOURS) {
                    errors.add("Row " + rowNum + ": Weekly hours must be between 1 and " + MAX_WEEKLY_HOURS);
                    skipped++;
                    continue;
                }

                // Parse is_online flag
                boolean isOnline = "true".equals(isOnlineStr) || "yes".equals(isOnlineStr) || "1".equals(isOnlineStr);

                // Validate lecturer exists if specified
                Lecturer lecturer = null;
                if (lecturerEmail != null && !lecturerEmail.isEmpty()) {
                    Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(lecturerEmail);
                    if (lecturerOpt.isEmpty()) {
                        errors.add("Row " + rowNum + ": Lecturer with email '" + lecturerEmail + "' not found - import lecturers first");
                        skipped++;
                        continue;
                    }
                    lecturer = lecturerOpt.get();
                }

                // Validate student group exists if specified
                StudentGroup studentGroup = null;
                if (studentGroupName != null && !studentGroupName.isEmpty()) {
                    Optional<StudentGroup> groupOpt = studentGroupRepository.findByName(studentGroupName);
                    if (groupOpt.isEmpty()) {
                        errors.add("Row " + rowNum + ": Student group '" + studentGroupName + "' not found - import student groups first");
                        skipped++;
                        continue;
                    }
                    studentGroup = groupOpt.get();
                }

                // Check duplicates
                if (seenCodes.contains(code)) {
                    errors.add("Row " + rowNum + ": Duplicate course code in CSV '" + code + "'");
                    skipped++;
                    continue;
                }
                seenCodes.add(code);

                if (courseRepository.findByCode(code).isPresent()) {
                    skipped++;
                    continue;
                }

                Course course = new Course();
                course.setCode(code);
                course.setName(name);
                course.setTotalWeeklyHours(weeklyHours);
                course.setLecturer(lecturer);
                course.setStudentGroup(studentGroup);
                course.setOnline(isOnline);
                courseRepository.save(course);
                created++;
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                skipped++;
            }
        }

        log.info("Imported {} courses, skipped {}, errors {}", created, skipped, errors.size());
        return Map.of("created", created, "skipped", skipped, "errors", errors);
    }

    /**
     * Validate file before processing.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("File must be a CSV file");
        }
        if (file.getSize() > 5 * 1024 * 1024) { // 5MB limit
            throw new IllegalArgumentException("File size exceeds 5MB limit");
        }
    }

    /**
     * Sanitize input to prevent XSS/injection.
     */
    private String sanitize(String input) {
        if (input == null) return null;
        // Remove potentially dangerous characters
        return input
            .replaceAll("[<>\"'`;]", "")
            .replaceAll("[\r\n]", " ")
            .trim();
    }

    /**
     * Parse CSV file into list of rows.
     */
    private List<String[]> parseCsv(MultipartFile file) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isFirstLine = true;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                // Skip header row
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                // Limit rows for safety
                if (rows.size() >= 10000) {
                    throw new IllegalArgumentException("CSV exceeds maximum of 10,000 rows");
                }
                // CSV parsing with quote handling
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                // Clean values
                for (int i = 0; i < values.length; i++) {
                    values[i] = values[i].replaceAll("^\"|\"$", "").trim();
                }
                rows.add(values);
            }
        }
        return rows;
    }
}
