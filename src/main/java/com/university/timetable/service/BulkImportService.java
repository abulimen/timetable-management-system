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
            case "rooms" ->
                "name,capacity,zone_name,features\nRoom A101,50,Building A,Projector|Whiteboard\nRoom B202,100,Building B,Projector\nLab C301,30,Building C,Computers|Lab Equipment";
            case "student-groups", "studentgroups" ->
                "name,size,parent_group_name\nComputer Science Year 1,,\nCSC 1A,40,Computer Science Year 1\nCSC 1B,40,Computer Science Year 1\nCSC 1C,40,Computer Science Year 1";
            case "zones" -> "name\nBuilding A\nBuilding B\nLaboratory Wing\nScience Complex";
            case "features" -> "name\nProjector\nWhiteboard\nComputers\nLab Equipment\nWet Lab";
            case "courses" ->
                "code,name,weekly_hours,lecturer_email,student_group_names,is_online\nCOSC101,Introduction to Programming,3,john.smith@university.edu,COSC_1A,false\nCOSC102,Data Structures,2,jane.doe@university.edu,COSC_1B,false\nONL101,Introduction to Online Learning,2,john.smith@university.edu,COSC_1A,true\nSEM200,Interdisciplinary Seminar,2,jane.doe@university.edu,CS_200|IT_200|SE_200,false";
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    /**
     * Exception thrown when bulk import validation fails.
     * Triggers transaction rollback.
     */
    public static class BulkImportException extends RuntimeException {
        private final List<String> errors;

        public BulkImportException(List<String> errors) {
            super("Import validation failed with " + errors.size() + " error(s)");
            this.errors = errors;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Import lecturers from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importLecturers(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<Lecturer> validLecturers = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2; // +2 for header and 0-index

            // Validate required fields
            if (row.length < 1 || row[0].trim().isEmpty()) {
                errors.add("Row " + rowNum + ": Name is required");
                continue;
            }

            String name = sanitize(row[0].trim());
            String email = row.length > 1 ? sanitize(row[1].trim()) : null;

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                continue;
            }
            if (!ALPHANUMERIC_PATTERN.matcher(name.replaceAll("[.,']", "")).matches()) {
                errors.add("Row " + rowNum + ": Name contains invalid characters");
                continue;
            }

            // Validate email if provided
            if (email != null && !email.isEmpty()) {
                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    errors.add("Row " + rowNum + ": Invalid email format '" + email + "'");
                    continue;
                }
                if (seenEmails.contains(email.toLowerCase())) {
                    errors.add("Row " + rowNum + ": Duplicate email in CSV '" + email + "'");
                    continue;
                }
                if (lecturerRepository.findByEmail(email).isPresent()) {
                    errors.add("Row " + rowNum + ": Email already exists '" + email + "'");
                    continue;
                }
                seenEmails.add(email.toLowerCase());
            }

            // Check duplicate name in CSV
            if (seenNames.contains(name.toLowerCase())) {
                errors.add("Row " + rowNum + ": Duplicate name in CSV '" + name + "'");
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Strict mode: existing records are errors, not skips
            if (lecturerRepository.findByName(name).isPresent()) {
                errors.add("Row " + rowNum + ": Lecturer '" + name + "' already exists in database");
                continue;
            }

            // Build valid lecturer for later save
            Lecturer lecturer = new Lecturer();
            lecturer.setName(name);
            lecturer.setEmail(email);
            validLecturers.add(lecturer);
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!errors.isEmpty()) {
            log.error("Bulk import validation failed with {} errors", errors.size());
            throw new BulkImportException(errors);
        }

        // PHASE 3: Save all valid entries atomically
        for (Lecturer lecturer : validLecturers) {
            lecturerRepository.save(lecturer);
        }

        log.info("Imported {} lecturers atomically", validLecturers.size());
        return Map.of("created", validLecturers.size(), "skipped", 0, "errors", List.of());
    }

    /**
     * Import rooms from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importRooms(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        List<Room> validRooms = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;

            if (row.length < 2 || row[0].trim().isEmpty()) {
                errors.add("Row " + rowNum + ": Name and capacity are required");
                continue;
            }

            String name = sanitize(row[0].trim());
            String capacityStr = row[1].trim();
            String zoneName = row.length > 2 ? sanitize(row[2].trim()) : null;
            String featuresStr = row.length > 3 ? row[3].trim() : null;

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                continue;
            }

            // Validate capacity
            int capacity;
            try {
                capacity = Integer.parseInt(capacityStr);
            } catch (NumberFormatException e) {
                errors.add("Row " + rowNum + ": Capacity must be a number, got '" + capacityStr + "'");
                continue;
            }
            if (capacity <= 0 || capacity > MAX_CAPACITY) {
                errors.add("Row " + rowNum + ": Capacity must be between 1 and " + MAX_CAPACITY);
                continue;
            }

            // Check zone exists if specified
            Zone zone = null;
            if (zoneName != null && !zoneName.isEmpty()) {
                Optional<Zone> zoneOpt = zoneRepository.findByName(zoneName);
                if (zoneOpt.isEmpty()) {
                    errors.add("Row " + rowNum + ": Zone '" + zoneName + "' not found - import zones first");
                    continue;
                }
                zone = zoneOpt.get();
            }

            // Check duplicates
            if (seenNames.contains(name.toLowerCase())) {
                errors.add("Row " + rowNum + ": Duplicate room name in CSV '" + name + "'");
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Strict mode: existing records are errors, not skips
            if (roomRepository.findByName(name).isPresent()) {
                errors.add("Row " + rowNum + ": Room '" + name + "' already exists in database");
                continue;
            }

            // Build valid room for later save
            Room room = new Room();
            room.setName(name);
            room.setCapacity(capacity);
            room.setZone(zone);

            // Parse and assign features (pipe-separated)
            if (featuresStr != null && !featuresStr.isEmpty()) {
                Set<Feature> roomFeatures = new HashSet<>();
                String[] featureNames = featuresStr.split("\\|");
                for (String featureName : featureNames) {
                    String trimmedName = featureName.trim();
                    if (!trimmedName.isEmpty()) {
                        Optional<Feature> featureOpt = featureRepository.findByName(trimmedName);
                        if (featureOpt.isPresent()) {
                            roomFeatures.add(featureOpt.get());
                        } else {
                            errors.add("Row " + rowNum + ": Feature '" + trimmedName
                                    + "' not found - import features first");
                        }
                    }
                }
                room.setFeatures(roomFeatures);
            }
            validRooms.add(room);
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!errors.isEmpty()) {
            log.error("Bulk import validation failed with {} errors", errors.size());
            throw new BulkImportException(errors);
        }

        // PHASE 3: Save all valid entries atomically
        for (Room room : validRooms) {
            roomRepository.save(room);
        }

        log.info("Imported {} rooms atomically", validRooms.size());
        return Map.of("created", validRooms.size(), "skipped", 0, "errors", List.of());
    }

    /**
     * Import student groups from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importStudentGroups(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Map<String, Integer> validGroups = new LinkedHashMap<>(); // name -> size
        Map<String, String> parentRelations = new LinkedHashMap<>(); // child -> parent

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;

            if (row.length < 1 || row[0].trim().isEmpty()) {
                errors.add("Row " + rowNum + ": Name is required");
                continue;
            }

            String name = sanitize(row[0].trim());
            String sizeStr = row.length > 1 ? row[1].trim() : "";
            String parentName = row.length > 2 ? sanitize(row[2].trim()) : null;

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                continue;
            }

            // Parse size - allow empty for parent groups (size will be 0)
            int size = 0;
            if (!sizeStr.isEmpty()) {
                try {
                    size = Integer.parseInt(sizeStr);
                } catch (NumberFormatException e) {
                    errors.add("Row " + rowNum + ": Size must be a number or empty, got '" + sizeStr + "'");
                    continue;
                }
                if (size < 0 || size > MAX_GROUP_SIZE) {
                    errors.add("Row " + rowNum + ": Size must be between 0 and " + MAX_GROUP_SIZE);
                    continue;
                }
            }

            // Check duplicates in CSV
            if (seenNames.contains(name.toLowerCase())) {
                errors.add("Row " + rowNum + ": Duplicate group name in CSV '" + name + "'");
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Strict mode: existing records are errors, not skips
            if (studentGroupRepository.findByName(name).isPresent()) {
                errors.add("Row " + rowNum + ": Student group '" + name + "' already exists in database");
                continue;
            }

            validGroups.put(name, size);

            // Store parent relation for validation
            if (parentName != null && !parentName.isEmpty()) {
                parentRelations.put(name, parentName);
            }
        }

        // Validate all parent references exist (either in CSV or DB)
        for (Map.Entry<String, String> entry : parentRelations.entrySet()) {
            String childName = entry.getKey();
            String parentName = entry.getValue();
            boolean parentInCSV = seenNames.contains(parentName.toLowerCase());
            boolean parentInDB = studentGroupRepository.findByName(parentName).isPresent();
            if (!parentInCSV && !parentInDB) {
                errors.add("Parent group '" + parentName + "' not found for '" + childName + "'");
            }
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!errors.isEmpty()) {
            log.error("Bulk import validation failed with {} errors", errors.size());
            throw new BulkImportException(errors);
        }

        // PHASE 3: Save all valid entries atomically (parents first by ordering)
        for (Map.Entry<String, Integer> entry : validGroups.entrySet()) {
            String name = entry.getKey();
            int size = entry.getValue();
            StudentGroup group = new StudentGroup();
            group.setName(name);
            group.setSize(size);
            studentGroupRepository.save(group);
        }

        // Set parent relationships
        for (Map.Entry<String, String> entry : parentRelations.entrySet()) {
            String childName = entry.getKey();
            String parentName = entry.getValue();
            Optional<StudentGroup> childOpt = studentGroupRepository.findByName(childName);
            Optional<StudentGroup> parentOpt = studentGroupRepository.findByName(parentName);
            if (childOpt.isPresent() && parentOpt.isPresent()) {
                StudentGroup child = childOpt.get();
                child.setParentGroup(parentOpt.get());
                studentGroupRepository.save(child);
            }
        }

        log.info("Imported {} student groups atomically", validGroups.size());
        return Map.of("created", validGroups.size(), "skipped", 0, "errors", List.of());
    }

    /**
     * Import zones from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importZones(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        List<String> validZoneNames = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;

            if (row.length < 1 || row[0].trim().isEmpty()) {
                errors.add("Row " + rowNum + ": Name is required");
                continue;
            }

            String name = sanitize(row[0].trim());

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                continue;
            }

            // Check duplicates
            if (seenNames.contains(name.toLowerCase())) {
                errors.add("Row " + rowNum + ": Duplicate zone name in CSV '" + name + "'");
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Strict mode: existing records are errors, not skips
            if (zoneRepository.findByName(name).isPresent()) {
                errors.add("Row " + rowNum + ": Zone '" + name + "' already exists in database");
                continue;
            }

            validZoneNames.add(name);
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!errors.isEmpty()) {
            log.error("Bulk import validation failed with {} errors", errors.size());
            throw new BulkImportException(errors);
        }

        // PHASE 3: Save all valid entries atomically
        for (String name : validZoneNames) {
            Zone zone = new Zone();
            zone.setName(name);
            zoneRepository.save(zone);
        }

        log.info("Imported {} zones atomically", validZoneNames.size());
        return Map.of("created", validZoneNames.size(), "skipped", 0, "errors", List.of());
    }

    /**
     * Import features from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFeatures(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        List<String> validFeatureNames = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;

            if (row.length < 1 || row[0].trim().isEmpty()) {
                errors.add("Row " + rowNum + ": Name is required");
                continue;
            }

            String name = sanitize(row[0].trim());

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                continue;
            }

            // Check duplicates
            if (seenNames.contains(name.toLowerCase())) {
                errors.add("Row " + rowNum + ": Duplicate feature name in CSV '" + name + "'");
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Strict mode: existing records are errors, not skips
            if (featureRepository.findByName(name).isPresent()) {
                errors.add("Row " + rowNum + ": Feature '" + name + "' already exists in database");
                continue;
            }

            validFeatureNames.add(name);
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!errors.isEmpty()) {
            log.error("Bulk import validation failed with {} errors", errors.size());
            throw new BulkImportException(errors);
        }

        // PHASE 3: Save all valid entries atomically
        for (String name : validFeatureNames) {
            Feature feature = new Feature();
            feature.setName(name);
            featureRepository.save(feature);
        }

        log.info("Imported {} features atomically", validFeatureNames.size());
        return Map.of("created", validFeatureNames.size(), "skipped", 0, "errors", List.of());
    }

    /**
     * Import courses from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importCourses(MultipartFile file) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        List<String> errors = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        List<Course> validCourses = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;

            if (row.length < 3 || row[0].trim().isEmpty()) {
                errors.add("Row " + rowNum + ": Code, name, and weekly hours are required");
                continue;
            }

            String code = sanitize(row[0].trim()).toUpperCase();
            String name = sanitize(row[1].trim());
            String hoursStr = row[2].trim();
            String lecturerEmail = row.length > 3 ? sanitize(row[3].trim()) : null;
            String isOnlineStr = row.length > 5 ? row[5].trim().toLowerCase() : "false";

            // Validate code
            if (code.length() > MAX_CODE_LENGTH) {
                errors.add("Row " + rowNum + ": Code exceeds " + MAX_CODE_LENGTH + " characters");
                continue;
            }
            if (!code.matches("^[A-Z0-9_\\-]+$")) {
                errors.add("Row " + rowNum + ": Code must be alphanumeric (uppercase)");
                continue;
            }

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                errors.add("Row " + rowNum + ": Name exceeds " + MAX_NAME_LENGTH + " characters");
                continue;
            }

            // Validate weekly hours
            int weeklyHours;
            try {
                weeklyHours = Integer.parseInt(hoursStr);
            } catch (NumberFormatException e) {
                errors.add("Row " + rowNum + ": Weekly hours must be a number, got '" + hoursStr + "'");
                continue;
            }
            if (weeklyHours <= 0 || weeklyHours > MAX_WEEKLY_HOURS) {
                errors.add("Row " + rowNum + ": Weekly hours must be between 1 and " + MAX_WEEKLY_HOURS);
                continue;
            }

            // Parse is_online flag
            boolean isOnline = "true".equals(isOnlineStr) || "yes".equals(isOnlineStr) || "1".equals(isOnlineStr);

            // Validate lecturer exists if specified
            Lecturer lecturer = null;
            if (lecturerEmail != null && !lecturerEmail.isEmpty()) {
                Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(lecturerEmail);
                if (lecturerOpt.isEmpty()) {
                    errors.add("Row " + rowNum + ": Lecturer email '" + lecturerEmail
                            + "' not found in database. Please check for typos and ensure this email exists in the lecturers list before importing courses.");
                    continue;
                }
                lecturer = lecturerOpt.get();
            }

            // Validate student groups exist if specified (pipe-separated for multiple)
            Set<StudentGroup> studentGroups = new HashSet<>();
            String studentGroupNamesStr = row.length > 4 ? sanitize(row[4].trim()) : null;
            if (studentGroupNamesStr != null && !studentGroupNamesStr.isEmpty()) {
                String[] groupNames = studentGroupNamesStr.split("\\|");
                boolean allGroupsFound = true;
                for (String groupName : groupNames) {
                    String trimmedGroupName = groupName.trim();
                    if (!trimmedGroupName.isEmpty()) {
                        Optional<StudentGroup> groupOpt = studentGroupRepository.findByName(trimmedGroupName);
                        if (groupOpt.isEmpty()) {
                            errors.add("Row " + rowNum + ": Student group '" + trimmedGroupName
                                    + "' not found - import student groups first");
                            allGroupsFound = false;
                        } else {
                            studentGroups.add(groupOpt.get());
                        }
                    }
                }
                if (!allGroupsFound) {
                    continue;
                }
            }

            // Check duplicates in CSV
            if (seenCodes.contains(code)) {
                errors.add("Row " + rowNum + ": Duplicate course code in CSV '" + code + "'");
                continue;
            }
            seenCodes.add(code);

            // Strict mode: existing records are errors, not skips
            if (courseRepository.findByCode(code).isPresent()) {
                errors.add("Row " + rowNum + ": Course with code '" + code + "' already exists in database");
                continue;
            }

            // Build valid course for later save
            Course course = new Course();
            course.setCode(code);
            course.setName(name);
            course.setTotalWeeklyHours(weeklyHours);
            course.setLecturer(lecturer);
            course.setOnline(isOnline);

            // Set student groups
            if (!studentGroups.isEmpty()) {
                course.setStudentGroups(studentGroups);
                course.setStudentGroup(studentGroups.iterator().next());
            }
            validCourses.add(course);
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!errors.isEmpty()) {
            log.error("Bulk import validation failed with {} errors", errors.size());
            throw new BulkImportException(errors);
        }

        // PHASE 3: Save all valid entries atomically
        for (Course course : validCourses) {
            courseRepository.save(course);
        }

        log.info("Imported {} courses atomically", validCourses.size());
        return Map.of("created", validCourses.size(), "skipped", 0, "errors", List.of());
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
        if (input == null)
            return null;
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
