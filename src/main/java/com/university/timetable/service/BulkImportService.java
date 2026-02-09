package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.dto.BulkImportResult;
import com.university.timetable.dto.ImportConflictDTO;
import com.university.timetable.dto.ImportRowDetail;
import com.university.timetable.dto.ImportRowError;
import com.university.timetable.dto.ImportRowError;
import com.university.timetable.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ImportHistoryService importHistoryService;

    @PersistenceContext
    private EntityManager entityManager;

    // Validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-\\s]+$");
    private static final int MAX_NAME_LENGTH = 200;
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
                "base_name,is_parent,level,group,size,parent_group_name\nComputer Science,T,100,,,\nComputer Science,F,100,A,40,Computer Science 100 LEVEL\nComputer Science,F,100,B,40,Computer Science 100 LEVEL";
            case "zones" -> "name\nBuilding A\nBuilding B\nLaboratory Wing\nScience Complex";
            case "features" -> "name\nProjector\nWhiteboard\nComputers\nLab Equipment\nWet Lab";
            case "courses" ->
                "code,name,weekly_hours,lecturer_email,student_group_names,is_online,required_features,allowed_zones\nCOSC101,Introduction to Programming,3,john.smith@university.edu,COSC_1A,false,,\nCOSC102,Data Structures,2,jane.doe@university.edu,COSC_1B,false,Projector|Whiteboard,\nLAB101,Lab Session,2,john.smith@university.edu,COSC_1A,false,Computers|Lab Equipment,Science Building\nONL101,Introduction to Online Learning,2,john.smith@university.edu,COSC_1A,true,,";
            case "users" ->
                "email,first_name,last_name,role,department,phone\nadmin@university.edu,John,Admin,ADMIN,IT Department,+1234567890\ncoord@university.edu,Jane,Coordinator,COORDINATOR,Academic Affairs,\nlecturer@university.edu,Bob,Teacher,LECTURER,Computer Science,";
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    /**
     * Exception thrown when bulk import validation fails.
     * Triggers transaction rollback.
     */
    public static class BulkImportException extends RuntimeException {
        private final BulkImportResult result;

        public BulkImportException(BulkImportResult result) {
            super("Import validation failed with " + (result.getRowErrors().size() + result.getGlobalErrors().size())
                    + " error(s)");
            this.result = result;
        }

        @Deprecated
        public BulkImportException(List<String> errors) {
            super("Import validation failed with " + errors.size() + " error(s)");
            this.result = BulkImportResult.builder().globalErrors(errors).build();
        }

        public BulkImportResult getResult() {
            return result;
        }

        public List<String> getErrors() {
            List<String> allErrors = new ArrayList<>(result.getGlobalErrors());
            result.getRowErrors().forEach(e -> allErrors.add(e.getMessage()));
            return allErrors;
        }
    }

    /**
     * Import lecturers from CSV with validation.
     * REQUIRES: Each email must exist as a user with LECTURER, COORDINATOR, or
     * ADMIN role.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    /**
     * Import lecturers from CSV with validation.
     * REQUIRES: Each email must exist as a user with LECTURER, COORDINATOR, or
     * ADMIN role.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importLecturers(MultipartFile file, boolean dryRun) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        return importLecturers(rows, dryRun, file.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importLecturers(List<String[]> rows, boolean dryRun, String originalFilename)
            throws Exception {

        BulkImportResult result = new BulkImportResult();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<Lecturer> validLecturers = new ArrayList<>();

        // Valid roles for lecturers (can teach)
        Set<UserRole> validRoles = Set.of(UserRole.LECTURER, UserRole.COORDINATOR, UserRole.ADMIN,
                UserRole.SUPER_ADMIN);

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2; // +2 for header and 0-index
            Map<String, String> rowData = new HashMap<>();

            // Capture raw data for preview
            if (row.length > 0)
                rowData.put("name", row[0]);
            if (row.length > 1)
                rowData.put("email", row[1]);

            // Validate required fields - name AND email are now required
            if (row.length < 2 || row[0].trim().isEmpty() || row[1].trim().isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Both name and email are required")
                        .rawData(rowData)
                        .build());
                continue;
            }

            String name = sanitize(row[0].trim());
            String email = sanitize(row[1].trim().toLowerCase());
            rowData.put("name", name);
            rowData.put("email", email);

            boolean hasError = false;

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name exceeds " + MAX_NAME_LENGTH + " characters")
                        .rawData(rowData)
                        .build());
                hasError = true;
            } else if (!ALPHANUMERIC_PATTERN.matcher(name.replaceAll("[.,']", "")).matches()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name contains invalid characters")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Validate email format
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Invalid email format '" + email + "'")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Check duplicate email in CSV
            if (seenEmails.contains(email)) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Duplicate email in CSV '" + email + "'")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Check duplicate name in CSV
            if (seenNames.contains(name.toLowerCase())) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Duplicate name in CSV '" + name + "'")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            if (hasError) {
                continue;
            }

            seenEmails.add(email);
            seenNames.add(name.toLowerCase());

            // Check if lecturer already exists - detect CONFLICT for resolution
            Optional<Lecturer> existingLecturerOpt = lecturerRepository.findByEmail(email);
            if (existingLecturerOpt.isPresent()) {
                Lecturer existing = existingLecturerOpt.get();

                Map<String, Object> existingData = new HashMap<>();
                existingData.put("name", existing.getName());
                existingData.put("email", existing.getEmail());

                Map<String, Object> newData = new HashMap<>();
                newData.put("name", name);
                newData.put("email", email);

                List<String> conflictingFields = new ArrayList<>();
                if (!Objects.equals(existing.getName(), name))
                    conflictingFields.add("name");

                if (!conflictingFields.isEmpty()) {
                    result.getConflicts().add(ImportConflictDTO.builder()
                            .rowNumber(rowNum)
                            .key(email)
                            .keyType("email")
                            .existingId(existing.getId())
                            .existingData(existingData)
                            .newData(newData)
                            .conflictingFields(conflictingFields)
                            .build());
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getValidRows().add(ImportRowDetail.builder()
                            .rowNumber(rowNum)
                            .data(rowData)
                            .status("SKIPPED")
                            .message("Already exists (unchanged)")
                            .build());
                }
                continue;
            }

            if (lecturerRepository.findByName(name).isPresent()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Lecturer with name '" + name + "' already exists in database with a different email.")
                        .rawData(rowData)
                        .build());
                continue;
            }

            // CRITICAL: Validate user account exists with valid teaching role
            Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
            if (userOpt.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("No user account found for email '" + email + "'. Please register this user first.")
                        .rawData(rowData)
                        .build());
                continue;
            }

            User user = userOpt.get();
            if (!validRoles.contains(user.getRole())) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("User '" + email + "' has role " + user.getRole()
                                + ". Required: LECTURER, COORDINATOR, or ADMIN role.")
                        .rawData(rowData)
                        .build());
                continue;
            }

            if (!user.getActive()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("User '" + email + "' is inactive. Please activate the account first.")
                        .rawData(rowData)
                        .build());
                continue;
            }

            // Build valid lecturer for later save
            Lecturer lecturer = new Lecturer();
            lecturer.setName(name);
            lecturer.setEmail(email);
            lecturer.setUser(user); // Link to user account

            if (!dryRun) {
                validLecturers.add(lecturer);
            }

            result.getValidRows().add(ImportRowDetail.builder()
                    .rowNumber(rowNum)
                    .data(rowData)
                    .status("NEW")
                    .message("Ready to import")
                    .build());
        }

        result.setErrorCount(result.getRowErrors().size());

        // If dry run, return results without saving
        if (dryRun) {
            return result;
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!result.getRowErrors().isEmpty() || !result.getGlobalErrors().isEmpty()) {
            log.error("Lecturer import validation failed with {} errors", result.getErrorCount());
            throw new BulkImportException(result);
        }

        // PHASE 3: Save all valid entries atomically
        // PHASE 3: Save all valid entries atomically
        lecturerRepository.saveAll(validLecturers);

        // Record history
        result.setImportHistoryId(
                recordHistory("LECTURERS", originalFilename, validLecturers.stream().map(Lecturer::getId).toList()));

        log.info("Imported {} lecturers atomically (all linked to user accounts)", validLecturers.size());
        result.setCreatedCount(validLecturers.size());
        return result;
    }

    /**
     * Import rooms from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importUsers(MultipartFile file, boolean dryRun) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        return importUsers(rows, dryRun, file.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importUsers(List<String[]> rows, boolean dryRun, String originalFilename) throws Exception {
        BulkImportResult result = new BulkImportResult();
        List<User> validUsers = new ArrayList<>();
        List<String> generatedPasswords = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        int rowNum = 1; // Header is row 0

        // PHASE 1: Validate all rows
        for (String[] values : rows) {
            rowNum++;
            Map<String, String> rowData = new HashMap<>();

            // Capture raw data
            if (values.length > 0)
                rowData.put("email", values[0]);
            if (values.length > 1)
                rowData.put("firstName", values[1]);
            if (values.length > 2)
                rowData.put("lastName", values[2]);
            if (values.length > 3)
                rowData.put("role", values[3]);
            if (values.length > 4)
                rowData.put("department", values[4]);
            if (values.length > 5)
                rowData.put("phone", values[5]);

            if (values.length < 4) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Requires at least 4 columns (email,first_name,last_name,role)")
                        .rawData(rowData)
                        .build());
                continue;
            }

            String email = values[0].trim().toLowerCase();
            String firstName = values[1].trim();
            String lastName = values[2].trim();
            String roleStr = values[3].trim().toUpperCase();
            String department = values.length > 4 ? values[4].trim() : null;
            String phone = values.length > 5 ? values[5].trim() : null;

            rowData.put("email", email);
            rowData.put("firstName", firstName);
            rowData.put("lastName", lastName);
            rowData.put("role", roleStr);

            boolean hasError = false;

            // Validate email
            if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Invalid email '" + email + "'")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Check duplicate in CSV
            if (seenEmails.contains(email)) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Duplicate email in CSV '" + email + "'")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            if (hasError)
                continue;

            seenEmails.add(email);

            // Check if email already exists in database
            if (userRepository.existsByEmailIgnoreCase(email)) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Email already exists '" + email + "'")
                        .rawData(rowData)
                        .build());
                continue;
            }

            // Validate names
            if (firstName.isEmpty() || firstName.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Invalid first name")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }
            if (lastName.isEmpty() || lastName.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Invalid last name")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Validate role (SUPER_ADMIN not allowed)
            UserRole role = null;
            try {
                role = UserRole.valueOf(roleStr);
                if (role == UserRole.SUPER_ADMIN) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Cannot create SUPER_ADMIN users via import")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
            } catch (IllegalArgumentException e) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Invalid role '" + roleStr + "'. Valid: ADMIN, COORDINATOR, LECTURER, VIEWER")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            if (hasError)
                continue;

            // Generate password
            String password = generateSecurePassword();

            // Build user
            User user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName(firstName)
                    .lastName(lastName)
                    .phone(phone != null && !phone.isEmpty() ? phone : null)
                    .department(department != null && !department.isEmpty() ? department : null)
                    .role(role)
                    .active(true)
                    .emailVerified(false)
                    .mustChangePassword(true)
                    .build();

            if (!dryRun) {
                validUsers.add(user);
                generatedPasswords.add(password);
            }

            result.getValidRows().add(ImportRowDetail.builder()
                    .rowNumber(rowNum)
                    .data(rowData)
                    .status("NEW")
                    .message("Ready to import")
                    .build());
        }

        result.setErrorCount(result.getRowErrors().size());

        if (dryRun) {
            return result;
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!result.getRowErrors().isEmpty() || !result.getGlobalErrors().isEmpty()) {
            log.error("User bulk import validation failed with {} errors", result.getErrorCount());
            throw new BulkImportException(result);
        }

        // PHASE 3: Save all users and send emails
        int created = 0;
        for (int i = 0; i < validUsers.size(); i++) {
            User user = validUsers.get(i);
            String password = generatedPasswords.get(i);

            userRepository.save(user);
            created++;

            // Auto-create/link Lecturer entity if role is LECTURER
            if (user.getRole() == UserRole.LECTURER) {
                createOrLinkLecturerForUser(user);
            }

            // Send welcome email (async)
            emailService.sendWelcomeEmail(
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    password);
        }

        log.info("Bulk imported {} users", created);
        result.setCreatedCount(created);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importRooms(MultipartFile file, boolean dryRun) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        return importRooms(rows, dryRun, file.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importRooms(List<String[]> rows, boolean dryRun, String originalFilename) throws Exception {

        BulkImportResult result = new BulkImportResult();
        Set<String> seenNames = new HashSet<>();
        List<Room> validRooms = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            Map<String, String> rowData = new HashMap<>();

            if (row.length > 0)
                rowData.put("name", row[0]);
            if (row.length > 1)
                rowData.put("capacity", row[1]);
            if (row.length > 2)
                rowData.put("zoneName", row[2]);
            if (row.length > 3)
                rowData.put("features", row[3]);

            if (row.length < 2 || row[0].trim().isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name and capacity are required")
                        .rawData(rowData)
                        .build());
                continue;
            }

            String name = sanitize(row[0].trim());
            String capacityStr = row[1].trim();
            String zoneName = row.length > 2 ? sanitize(row[2].trim()) : null;
            String featuresStr = row.length > 3 ? row[3].trim() : null;

            rowData.put("name", name);
            rowData.put("capacity", capacityStr);

            boolean hasError = false;

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name exceeds " + MAX_NAME_LENGTH + " characters")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Validate capacity
            int capacity = 0;
            try {
                capacity = Integer.parseInt(capacityStr);
                if (capacity <= 0 || capacity > MAX_CAPACITY) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Capacity must be between 1 and " + MAX_CAPACITY)
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Capacity must be a number, got '" + capacityStr + "'")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            if (hasError)
                continue;

            // Check zone exists if specified
            Zone zone = null;
            if (zoneName != null && !zoneName.isEmpty()) {
                Optional<Zone> zoneOpt = zoneRepository.findByName(zoneName);
                if (zoneOpt.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Zone '" + zoneName + "' not found - import zones first")
                            .rawData(rowData)
                            .build());
                    continue;
                }
                zone = zoneOpt.get();
            }

            // Check duplicates
            if (seenNames.contains(name.toLowerCase())) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Duplicate room name in CSV '" + name + "'")
                        .rawData(rowData)
                        .build());
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Check if room already exists - detect CONFLICT for resolution
            Optional<Room> existingRoomOpt = roomRepository.findByName(name);
            if (existingRoomOpt.isPresent()) {
                Room existing = existingRoomOpt.get();

                String existingZoneName = existing.getZone() != null ? existing.getZone().getName() : "";
                String existingFeatures = existing.getFeatures().stream()
                        .map(Feature::getName)
                        .sorted()
                        .collect(Collectors.joining("|"));

                String newFeatures = featuresStr != null ? Arrays.stream(featuresStr.split("\\|"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .sorted()
                        .collect(Collectors.joining("|")) : "";

                Map<String, Object> existingData = new HashMap<>();
                existingData.put("name", existing.getName());
                existingData.put("capacity", existing.getCapacity());
                existingData.put("zoneName", existingZoneName);
                existingData.put("features", existingFeatures);

                Map<String, Object> newData = new HashMap<>();
                newData.put("name", name);
                newData.put("capacity", capacity);
                newData.put("zoneName", zoneName != null ? zoneName : "");
                newData.put("features", newFeatures);

                List<String> conflictingFields = new ArrayList<>();
                if (existing.getCapacity() != capacity)
                    conflictingFields.add("capacity");
                if (!Objects.equals(existingZoneName, zoneName != null ? zoneName : ""))
                    conflictingFields.add("zoneName");
                if (!Objects.equals(existingFeatures, newFeatures))
                    conflictingFields.add("features");

                if (!conflictingFields.isEmpty()) {
                    result.getConflicts().add(ImportConflictDTO.builder()
                            .rowNumber(rowNum)
                            .key(name)
                            .keyType("name")
                            .existingId(existing.getId())
                            .existingData(existingData)
                            .newData(newData)
                            .conflictingFields(conflictingFields)
                            .build());
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getValidRows().add(ImportRowDetail.builder()
                            .rowNumber(rowNum)
                            .data(rowData)
                            .status("SKIPPED")
                            .message("Already exists (unchanged)")
                            .build());
                }
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
                boolean allFeaturesFound = true;
                for (String featureName : featureNames) {
                    String trimmedName = featureName.trim();
                    if (!trimmedName.isEmpty()) {
                        Optional<Feature> featureOpt = featureRepository.findByName(trimmedName);
                        if (featureOpt.isPresent()) {
                            roomFeatures.add(featureOpt.get());
                        } else {
                            result.getRowErrors().add(ImportRowError.builder()
                                    .rowNumber(rowNum)
                                    .message("Feature '" + trimmedName + "' not found - import features first")
                                    .rawData(rowData)
                                    .build());
                            allFeaturesFound = false;
                        }
                    }
                }
                if (!allFeaturesFound)
                    continue;
                room.setFeatures(roomFeatures);
            }

            if (!dryRun) {
                validRooms.add(room);
            }

            result.getValidRows().add(ImportRowDetail.builder()
                    .rowNumber(rowNum)
                    .data(rowData)
                    .status("NEW")
                    .message("Ready to import")
                    .build());
        }

        result.setErrorCount(result.getRowErrors().size());

        if (dryRun) {
            return result;
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!result.getRowErrors().isEmpty() || !result.getGlobalErrors().isEmpty()) {
            log.error("Bulk import validation failed with {} errors", result.getErrorCount());
            throw new BulkImportException(result);
        }

        // PHASE 3: Save all valid entries atomically
        // PHASE 3: Save all valid entries atomically
        roomRepository.saveAll(validRooms);

        // Record history
        result.setImportHistoryId(
                recordHistory("ROOMS", originalFilename, validRooms.stream().map(Room::getId).toList()));

        log.info("Imported {} rooms atomically", validRooms.size());
        result.setCreatedCount(validRooms.size());
        return result;
    }

    /**
     * Import student groups from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importStudentGroups(MultipartFile file, boolean dryRun) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        return importStudentGroups(rows, dryRun, file.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importStudentGroups(List<String[]> rows, boolean dryRun, String originalFilename)
            throws Exception {

        BulkImportResult result = new BulkImportResult();
        Set<String> seenNames = new HashSet<>();
        // Store parsed group data: computedName -> {baseName, level, group, size}
        Map<String, int[]> validGroupsData = new LinkedHashMap<>(); // name -> [size, level]
        Map<String, String[]> validGroupsStrData = new LinkedHashMap<>(); // name -> [baseName, groupNotation]
        Map<String, String> parentRelations = new LinkedHashMap<>(); // child -> parent
        Set<Integer> validLevels = Set.of(100, 200, 300, 400, 500, 600);

        // PHASE 1: Validate ALL rows first
        // Expected columns: base_name, is_parent, level, group, size, parent_group_name
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            Map<String, String> rowData = new HashMap<>();

            String baseName;
            String isParentStr;
            String levelStr;
            String groupNotation;
            String sizeStr;
            String parentName;

            // Detect format
            // Standard: base_name, is_parent, level, group, size, parent_group_name
            // Legacy: name, size, parent_group_name

            String rawCol1 = row.length > 1 ? row[1].trim().toUpperCase() : "";
            String rawCol2 = row.length > 2 ? row[2].trim() : "";

            // Heuristic: If col2 is NOT a level number (e.g. it's a parent name string), OR
            // if col1 looks like a size (number > 1) instead of boolean flag
            boolean isLevelNumber = rawCol2.matches("^(100|200|300|400|500|600)$");
            boolean isSizeNumber = rawCol1.matches("^\\d+$") && !rawCol1.equals("0") && !rawCol1.equals("1");

            if (row.length <= 3 || (!rawCol2.isEmpty() && !isLevelNumber) || isSizeNumber) {
                // LEGACY FORMAT
                String name = row.length > 0 ? row[0].trim() : "";
                String size = row.length > 1 ? row[1].trim() : "0";
                String parent = row.length > 2 ? row[2].trim() : "";

                String[] parts = parseStudentGroupName(name);
                baseName = parts[0];
                levelStr = parts[1];
                groupNotation = parts[2];

                sizeStr = size;
                parentName = sanitize(parent);
                isParentStr = ""; // Default
            } else {
                // STANDARD FORMAT
                baseName = row.length > 0 ? row[0].trim() : "";
                isParentStr = rawCol1;
                levelStr = rawCol2;
                groupNotation = row.length > 3 ? row[3].trim() : "";
                sizeStr = row.length > 4 ? row[4].trim() : "";
                parentName = row.length > 5 ? sanitize(row[5].trim()) : null;
            }

            rowData.put("base_name", baseName);
            rowData.put("is_parent", isParentStr);
            rowData.put("level", levelStr);
            rowData.put("group", groupNotation);
            rowData.put("size", sizeStr);
            rowData.put("parent_group_name", parentName != null ? parentName : "");

            boolean hasError = false;

            // Determine if this is a parent group based on is_parent column
            boolean isParent = "T".equals(isParentStr) || "TRUE".equals(isParentStr) || "1".equals(isParentStr);

            // Validate base_name (required)
            if (baseName.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Base name is required")
                        .rawData(rowData)
                        .build());
                continue;
            }
            baseName = sanitize(baseName);

            if (baseName.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Base name exceeds " + MAX_NAME_LENGTH + " characters")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Validate level (required, must be 100/200/300/400/500/600)
            if (levelStr.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Level is required")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            int level = 0;
            if (!levelStr.isEmpty()) {
                try {
                    level = Integer.parseInt(levelStr);
                    if (!validLevels.contains(level)) {
                        result.getRowErrors().add(ImportRowError.builder()
                                .rowNumber(rowNum)
                                .message("Level must be one of: 100, 200, 300, 400, 500, 600")
                                .rawData(rowData)
                                .build());
                        hasError = true;
                    }
                } catch (NumberFormatException e) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Level must be a number (100, 200, 300, 400, 500, 600)")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
            }

            // Group notation is optional (for parent groups)
            // Just sanitize it if present
            if (!groupNotation.isEmpty()) {
                groupNotation = sanitize(groupNotation);
            }

            // Parse size - for parents it's ignored (auto-calculated), for children it's
            // required
            int size = 0;
            if (!isParent && !sizeStr.isEmpty()) {
                try {
                    size = Integer.parseInt(sizeStr);
                    if (size <= 0 || size > MAX_GROUP_SIZE) {
                        result.getRowErrors().add(ImportRowError.builder()
                                .rowNumber(rowNum)
                                .message("Size must be between 1 and " + MAX_GROUP_SIZE + " for child groups")
                                .rawData(rowData)
                                .build());
                        hasError = true;
                    }
                } catch (NumberFormatException e) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Size must be a number, got '" + sizeStr + "'")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
            }

            // Validation based on is_parent
            if (!isParent) {
                // Child Group Validation Rules
                if (groupNotation.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Group notation (e.g., A, B) is required for child groups")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
                if (parentName == null || parentName.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Parent group is required for child groups")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
                if (size <= 0 && sizeStr.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Size is required for child groups")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
            } else {
                // Parent Group Validation Rules
                if (!groupNotation.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Parent groups cannot have a group notation")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
                if (parentName != null && !parentName.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Parent groups cannot have a parent group")
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
            }

            if (hasError)
                continue;

            // Compute the final name
            String name = StudentGroup.computeName(baseName, level, groupNotation.isEmpty() ? null : groupNotation);

            // Check duplicates in CSV
            if (seenNames.contains(name.toLowerCase())) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Duplicate group name in CSV '" + name + "'")
                        .rawData(rowData)
                        .build());
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Check if group already exists - detect CONFLICT for resolution
            Optional<StudentGroup> existingGroupOpt = studentGroupRepository.findByName(name);
            if (existingGroupOpt.isPresent()) {
                StudentGroup existing = existingGroupOpt.get();
                String existingParent = existing.getParentGroup() != null ? existing.getParentGroup().getName() : "";

                Map<String, Object> existingData = new HashMap<>();
                existingData.put("name", existing.getName());
                existingData.put("size", existing.getSize());
                existingData.put("parentGroupName", existingParent);

                Map<String, Object> newData = new HashMap<>();
                newData.put("name", name);
                newData.put("size", size);
                newData.put("parentGroupName", parentName != null ? parentName : "");

                List<String> conflictingFields = new ArrayList<>();
                if (existing.getSize() != size)
                    conflictingFields.add("size");
                if (!Objects.equals(existingParent, parentName != null ? parentName : ""))
                    conflictingFields.add("parentGroupName");

                if (!conflictingFields.isEmpty()) {
                    if (dryRun) {
                        result.getConflicts().add(ImportConflictDTO.builder()
                                .rowNumber(rowNum)
                                .key(name)
                                .keyType("name")
                                .existingId(existing.getId())
                                .existingData(existingData)
                                .newData(newData)
                                .conflictingFields(conflictingFields)
                                .build());
                        continue;
                    }
                    // If !dryRun, proceed to update
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getValidRows().add(ImportRowDetail.builder()
                            .rowNumber(rowNum)
                            .data(rowData)
                            .status("SKIPPED")
                            .message("Already exists (unchanged)")
                            .build());
                    continue;
                }
            }

            validGroupsData.put(name, new int[] { size, level });
            validGroupsStrData.put(name, new String[] { baseName, groupNotation.isEmpty() ? null : groupNotation });

            // Store parent relation for validation
            if (parentName != null && !parentName.isEmpty()) {
                parentRelations.put(name, parentName);
            }

            result.getValidRows().add(ImportRowDetail.builder()
                    .rowNumber(rowNum)
                    .data(rowData)
                    .status("NEW")
                    .message("Ready to import")
                    .build());
        }

        // Validate all parent references exist (either in CSV or DB)
        for (Map.Entry<String, String> entry : parentRelations.entrySet()) {
            String childName = entry.getKey();
            String parentName = entry.getValue();
            boolean parentInCSV = seenNames.contains(parentName.toLowerCase());
            boolean parentInDB = studentGroupRepository.findByName(parentName).isPresent();
            if (!parentInCSV && !parentInDB) {
                // Add error to the parent reference - but we don't have row number easily here
                // We'll add it as a general error or try to find the row
                // Or just add to global errors
                result.getGlobalErrors().add("Parent group '" + parentName + "' not found for '" + childName + "'");
            }
        }

        result.setErrorCount(result.getRowErrors().size());

        if (dryRun) {
            return result;
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!result.getRowErrors().isEmpty() || !result.getGlobalErrors().isEmpty()) {
            log.error("Bulk import validation failed with {} errors", result.getErrorCount());
            throw new BulkImportException(result);
        }

        // PHASE 3: Save all valid entries atomically (parents first by ordering)
        List<StudentGroup> createdGroups = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;

        for (Map.Entry<String, int[]> entry : validGroupsData.entrySet()) {
            String name = entry.getKey();
            int[] intData = entry.getValue();
            String[] strData = validGroupsStrData.get(name);
            int size = intData[0];
            int level = intData[1];
            String baseName = strData[0];
            String groupNotation = strData[1];

            Optional<StudentGroup> existingOpt = studentGroupRepository.findByName(name);
            StudentGroup group;
            if (existingOpt.isPresent()) {
                group = existingOpt.get();
                updatedCount++;
            } else {
                group = new StudentGroup();
                createdCount++;
            }

            group.setBaseName(baseName);
            group.setLevel(level);
            group.setGroupNotation(groupNotation);
            group.setName(StudentGroup.computeName(baseName, level, groupNotation));
            group.setSize(size);
            createdGroups.add(studentGroupRepository.save(group));
        }

        // Record history
        result.setImportHistoryId(recordHistory("STUDENT_GROUPS", originalFilename,
                createdGroups.stream().map(StudentGroup::getId).toList()));

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

        // PHASE 4: Recalculate all parent groups' sizes based on their children
        recalculateAllParentSizes();

        log.info("Imported {} student groups atomically ({} created, {} updated)",
                validGroupsData.size(), createdCount, updatedCount);
        result.setCreatedCount(createdCount);
        result.setUpdatedCount(updatedCount);
        return result;
    }

    /**
     * Import zones from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importZones(MultipartFile file, boolean dryRun) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        return importZones(rows, dryRun, file.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importZones(List<String[]> rows, boolean dryRun, String originalFilename) throws Exception {

        BulkImportResult result = new BulkImportResult();
        Set<String> seenNames = new HashSet<>();
        List<String> validZoneNames = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            Map<String, String> rowData = new HashMap<>();

            if (row.length > 0)
                rowData.put("name", row[0]);

            if (row.length < 1 || row[0].trim().isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name is required")
                        .rawData(rowData)
                        .build());
                continue;
            }

            String name = sanitize(row[0].trim());
            rowData.put("name", name);

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name exceeds " + MAX_NAME_LENGTH + " characters")
                        .rawData(rowData)
                        .build());
                continue;
            }

            // Check duplicates
            if (seenNames.contains(name.toLowerCase())) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Duplicate zone name in CSV '" + name + "'")
                        .rawData(rowData)
                        .build());
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Check if zone already exists - detect CONFLICT for resolution
            Optional<Zone> existingZoneOpt = zoneRepository.findByName(name);
            if (existingZoneOpt.isPresent()) {
                Zone existing = existingZoneOpt.get();

                Map<String, Object> existingData = new HashMap<>();
                existingData.put("name", existing.getName());

                Map<String, Object> newData = new HashMap<>();
                newData.put("name", name);

                List<String> conflictingFields = new ArrayList<>();
                if (!Objects.equals(existing.getName(), name))
                    conflictingFields.add("name");

                if (!conflictingFields.isEmpty()) {
                    result.getConflicts().add(ImportConflictDTO.builder()
                            .rowNumber(rowNum)
                            .key(name)
                            .keyType("name")
                            .existingId(existing.getId())
                            .existingData(existingData)
                            .newData(newData)
                            .conflictingFields(conflictingFields)
                            .build());
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getValidRows().add(ImportRowDetail.builder()
                            .rowNumber(rowNum)
                            .data(rowData)
                            .status("SKIPPED")
                            .message("Already exists (unchanged)")
                            .build());
                }
                continue;
            }

            validZoneNames.add(name);

            result.getValidRows().add(ImportRowDetail.builder()
                    .rowNumber(rowNum)
                    .data(rowData)
                    .status("NEW")
                    .message("Ready to import")
                    .build());
        }

        result.setErrorCount(result.getRowErrors().size());

        if (dryRun) {
            return result;
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!result.getRowErrors().isEmpty() || !result.getGlobalErrors().isEmpty()) {
            log.error("Bulk import validation failed with {} errors", result.getErrorCount());
            throw new BulkImportException(result);
        }

        // PHASE 3: Save all valid entries atomically
        List<Zone> createdZones = new ArrayList<>();
        for (String name : validZoneNames) {
            Zone zone = new Zone();
            zone.setName(name);
            createdZones.add(zoneRepository.save(zone));
        }

        // Record history
        result.setImportHistoryId(
                recordHistory("ZONES", originalFilename, createdZones.stream().map(Zone::getId).toList()));

        log.info("Imported {} zones atomically", validZoneNames.size());
        result.setCreatedCount(validZoneNames.size());
        return result;
    }

    /**
     * Import features from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importFeatures(MultipartFile file, boolean dryRun) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        return importFeatures(rows, dryRun, file.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importFeatures(List<String[]> rows, boolean dryRun, String originalFilename)
            throws Exception {

        BulkImportResult result = new BulkImportResult();
        Set<String> seenNames = new HashSet<>();
        List<String> validFeatureNames = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;
            Map<String, String> rowData = new HashMap<>();

            if (row.length > 0)
                rowData.put("name", row[0]);

            if (row.length < 1 || row[0].trim().isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name is required")
                        .rawData(rowData)
                        .build());
                continue;
            }

            String name = sanitize(row[0].trim());
            rowData.put("name", name);

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name exceeds " + MAX_NAME_LENGTH + " characters")
                        .rawData(rowData)
                        .build());
                continue;
            }

            // Check duplicates
            if (seenNames.contains(name.toLowerCase())) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Duplicate feature name in CSV '" + name + "'")
                        .rawData(rowData)
                        .build());
                continue;
            }
            seenNames.add(name.toLowerCase());

            // Check if feature already exists - detect CONFLICT for resolution
            Optional<Feature> existingFeatureOpt = featureRepository.findByName(name);
            if (existingFeatureOpt.isPresent()) {
                Feature existing = existingFeatureOpt.get();

                Map<String, Object> existingData = new HashMap<>();
                existingData.put("name", existing.getName());

                Map<String, Object> newData = new HashMap<>();
                newData.put("name", name);

                List<String> conflictingFields = new ArrayList<>();
                if (!Objects.equals(existing.getName(), name))
                    conflictingFields.add("name");

                if (!conflictingFields.isEmpty()) {
                    result.getConflicts().add(ImportConflictDTO.builder()
                            .rowNumber(rowNum)
                            .key(name)
                            .keyType("name")
                            .existingId(existing.getId())
                            .existingData(existingData)
                            .newData(newData)
                            .conflictingFields(conflictingFields)
                            .build());
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getValidRows().add(ImportRowDetail.builder()
                            .rowNumber(rowNum)
                            .data(rowData)
                            .status("SKIPPED")
                            .message("Already exists (unchanged)")
                            .build());
                }
                continue;
            }

            validFeatureNames.add(name);

            result.getValidRows().add(ImportRowDetail.builder()
                    .rowNumber(rowNum)
                    .data(rowData)
                    .status("NEW")
                    .message("Ready to import")
                    .build());
        }

        result.setErrorCount(result.getRowErrors().size());

        if (dryRun) {
            return result;
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!result.getRowErrors().isEmpty() || !result.getGlobalErrors().isEmpty()) {
            log.error("Bulk import validation failed with {} errors", result.getErrorCount());
            throw new BulkImportException(result);
        }

        // PHASE 3: Save all valid entries atomically
        List<Feature> createdFeatures = new ArrayList<>();
        for (String name : validFeatureNames) {
            Feature feature = new Feature();
            feature.setName(name);
            createdFeatures.add(featureRepository.save(feature));
        }

        // Record history
        result.setImportHistoryId(
                recordHistory("FEATURES", originalFilename, createdFeatures.stream().map(Feature::getId).toList()));

        log.info("Imported {} features atomically", validFeatureNames.size());
        result.setCreatedCount(validFeatureNames.size());
        return result;
    }

    /**
     * Import courses from CSV with validation.
     * Uses atomic transactions - ALL rows must be valid or entire import fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importCourses(MultipartFile file, boolean dryRun) throws Exception {
        validateFile(file);
        List<String[]> rows = parseCsv(file);
        return importCourses(rows, dryRun, file.getOriginalFilename());
    }

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importCourses(List<String[]> rows, boolean dryRun, String originalFilename)
            throws Exception {

        BulkImportResult result = new BulkImportResult();
        Set<String> seenCodes = new HashSet<>();
        List<Course> validCourses = new ArrayList<>();

        // PHASE 1: Validate ALL rows first
        log.warn("========== STARTING COURSE VALIDATION FOR {} ROWS ==========", rows.size());
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNum = i + 2;

            Map<String, String> rowData = new HashMap<>();

            // Capture raw data
            if (row.length > 0)
                rowData.put("code", row[0]);
            if (row.length > 1)
                rowData.put("name", row[1]);
            if (row.length > 2)
                rowData.put("weeklyHours", row[2]);
            if (row.length > 3)
                rowData.put("lecturerEmail", row[3]);
            if (row.length > 4)
                rowData.put("studentGroupNames", row[4]);

            // Detect if we have an extra "size" column at index 5.
            // Heuristic: If column 5 is "true"/"false", it's the standard format
            // (isOnline).
            // If it's anything else (e.g. a number "1"), we assume it's the extra "Size"
            // column.
            String col5_detect = row.length > 5 ? row[5].trim().toLowerCase() : "";
            boolean col5IsBoolean_detect = col5_detect.equals("true") || col5_detect.equals("false");
            boolean hasExtraColumn_detect = !col5IsBoolean_detect && row.length > 5;

            int isOnlineIdx_detect = hasExtraColumn_detect ? 6 : 5;
            int featuresIdx_detect = hasExtraColumn_detect ? 7 : 6;
            int zonesIdx_detect = hasExtraColumn_detect ? 8 : 7;

            // Populate rowData for display using correct indices (calculated early)
            if (row.length > isOnlineIdx_detect)
                rowData.put("isOnline", row[isOnlineIdx_detect]);
            if (row.length > featuresIdx_detect)
                rowData.put("requiredFeatures", row[featuresIdx_detect]);
            if (row.length > zonesIdx_detect)
                rowData.put("allowedZones", row[zonesIdx_detect]);

            if (row.length < 3 || row[0].trim().isEmpty()) {
                log.warn("VALIDATION FAIL - Row {} skipped: length={}, code='{}'", rowNum, row.length,
                        row.length > 0 ? row[0] : "null");
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Code, name, and weekly hours are required")
                        .rawData(rowData)
                        .build());
                continue;
            }

            String code = sanitize(row[0].trim()).toUpperCase();
            String name = sanitize(row[1].trim());
            String hoursStr = row[2].trim();
            String lecturerEmail = row.length > 3 ? sanitize(row[3].trim()) : null;

            // Detect if we have an extra "size" column at index 5 (common in scraped data)
            // If row has >= 9 columns: 5=SIZE, 6=online, 7=features, 8=zones
            boolean hasExtraColumn = row.length >= 9;
            int isOnlineIdx = hasExtraColumn ? 6 : 5;
            int featuresIdx = hasExtraColumn ? 7 : 6;
            int zonesIdx = hasExtraColumn ? 8 : 7;

            String isOnlineStr = row.length > isOnlineIdx ? row[isOnlineIdx].trim().toLowerCase() : "false";

            rowData.put("code", code);
            rowData.put("name", name);

            boolean hasError = false;

            // Validate code
            if (code.length() > MAX_CODE_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Code exceeds " + MAX_CODE_LENGTH + " characters")
                        .rawData(rowData)
                        .build());
                hasError = true;
            } else if (!code.matches("^[A-Z0-9_\\- ]+$")) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Code must be alphanumeric (uppercase)")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Validate name
            if (name.length() > MAX_NAME_LENGTH) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Name exceeds " + MAX_NAME_LENGTH + " characters")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            // Validate weekly hours
            int weeklyHours = 0;
            try {
                weeklyHours = Integer.parseInt(hoursStr);
                if (weeklyHours <= 0 || weeklyHours > MAX_WEEKLY_HOURS) {
                    log.warn("VALIDATION FAIL - Row {} invalid hours: {}", rowNum, weeklyHours);
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Weekly hours must be between 1 and " + MAX_WEEKLY_HOURS)
                            .rawData(rowData)
                            .build());
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                log.warn("VALIDATION FAIL - Row {} hours format error: '{}'", rowNum, hoursStr);
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Weekly hours must be a number, got '" + hoursStr + "'")
                        .rawData(rowData)
                        .build());
                hasError = true;
            }

            if (hasError)
                continue;

            // Parse is_online flag
            boolean isOnline = "true".equals(isOnlineStr) || "yes".equals(isOnlineStr) || "1".equals(isOnlineStr);

            // Validate lecturer exists if specified
            Lecturer lecturer = null;
            if (lecturerEmail != null && !lecturerEmail.isEmpty()) {
                Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(lecturerEmail);
                if (lecturerOpt.isEmpty()) {
                    log.warn("VALIDATION FAIL - Lecturer not found: '{}' at row {}", lecturerEmail, rowNum);
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Lecturer email '" + lecturerEmail + "' not found. Check for typos.")
                            .rawData(rowData)
                            .build());
                    continue; // Stop processing this row
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
                            log.warn("VALIDATION FAIL - Student group not found: '{}' at row {}", trimmedGroupName,
                                    rowNum);
                            result.getRowErrors().add(ImportRowError.builder()
                                    .rowNumber(rowNum)
                                    .message("Student group '" + trimmedGroupName + "' not found")
                                    .rawData(rowData)
                                    .build());
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

            // Allow duplicate codes - will aggregate later
            // Just track them for aggregation
            seenCodes.add(code);

            // Check if course already exists in database - detect CONFLICT for resolution
            Optional<Course> existingCourseOpt = courseRepository.findByCode(code);
            if (existingCourseOpt.isPresent()) {
                Course existing = existingCourseOpt.get();

                // Build comparison maps
                Map<String, Object> existingData = new HashMap<>();
                existingData.put("code", existing.getCode());
                existingData.put("name", existing.getName());
                existingData.put("weeklyHours", existing.getTotalWeeklyHours());
                existingData.put("lecturerEmail",
                        existing.getLecturer() != null ? existing.getLecturer().getEmail() : null);
                existingData.put("isOnline", existing.isOnline());

                Map<String, Object> newData = new HashMap<>();
                newData.put("code", code);
                newData.put("name", name);
                newData.put("weeklyHours", weeklyHours);
                newData.put("lecturerEmail", lecturerEmail);
                newData.put("isOnline", isOnline);

                // Find which fields differ
                List<String> conflictingFields = new ArrayList<>();
                if (!Objects.equals(existing.getName(), name))
                    conflictingFields.add("name");
                if (!Objects.equals(existing.getTotalWeeklyHours(), weeklyHours))
                    conflictingFields.add("weeklyHours");
                String existingLecturerEmail = existing.getLecturer() != null ? existing.getLecturer().getEmail()
                        : null;
                if (!Objects.equals(existingLecturerEmail, lecturerEmail))
                    conflictingFields.add("lecturerEmail");
                if (existing.isOnline() != isOnline)
                    conflictingFields.add("isOnline");

                // Only create conflict if there are actual differences
                if (!conflictingFields.isEmpty()) {
                    result.getConflicts().add(ImportConflictDTO.builder()
                            .rowNumber(rowNum)
                            .key(code)
                            .keyType("code")
                            .existingId(existing.getId())
                            .existingData(existingData)
                            .newData(newData)
                            .conflictingFields(conflictingFields)
                            .build());
                } else {
                    // No differences - just skip as identical
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getValidRows().add(ImportRowDetail.builder()
                            .rowNumber(rowNum)
                            .data(rowData)
                            .status("SKIPPED")
                            .message("Already exists (unchanged)")
                            .build());
                }
                continue; // Don't add to validCourses - needs resolution
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

            // Parse and assign required features (pipe-separated)
            String requiredFeaturesStr = row.length > featuresIdx ? row[featuresIdx].trim() : null;
            if (requiredFeaturesStr != null && !requiredFeaturesStr.isEmpty()) {
                Set<Feature> courseFeatures = new HashSet<>();
                String[] featureNames = requiredFeaturesStr.split("\\|");
                boolean allFeaturesFound = true;
                for (String featureName : featureNames) {
                    String trimmedName = featureName.trim();
                    if (!trimmedName.isEmpty()) {
                        Optional<Feature> featureOpt = featureRepository.findByName(trimmedName);
                        if (featureOpt.isPresent()) {
                            courseFeatures.add(featureOpt.get());
                        } else {
                            log.warn("VALIDATION FAIL - Row {} missing feature: '{}'", rowNum, trimmedName);
                            result.getRowErrors().add(ImportRowError.builder()
                                    .rowNumber(rowNum)
                                    .message("Required feature '" + trimmedName + "' not found")
                                    .rawData(rowData)
                                    .build());
                            allFeaturesFound = false;
                        }
                    }
                }
                if (!allFeaturesFound)
                    continue;
                course.setRequiredFeatures(courseFeatures);
            }

            // Parse and assign allowed zones (pipe-separated)
            String allowedZonesStr = row.length > zonesIdx ? row[zonesIdx].trim() : null;
            if (allowedZonesStr != null && !allowedZonesStr.isEmpty()) {
                Set<Zone> courseZones = new HashSet<>();
                String[] zoneNames = allowedZonesStr.split("\\|");
                boolean allZonesFound = true;
                for (String zoneName : zoneNames) {
                    String trimmedName = zoneName.trim();
                    if (!trimmedName.isEmpty()) {
                        Optional<Zone> zoneOpt = zoneRepository.findByName(trimmedName);
                        if (zoneOpt.isPresent()) {
                            courseZones.add(zoneOpt.get());
                        } else {
                            log.warn("VALIDATION FAIL - Row {} missing zone: '{}'", rowNum, trimmedName);
                            result.getRowErrors().add(ImportRowError.builder()
                                    .rowNumber(rowNum)
                                    .message("Allowed zone '" + trimmedName + "' not found")
                                    .rawData(rowData)
                                    .build());
                            allZonesFound = false;
                        }
                    }
                }
                if (!allZonesFound)
                    continue;
                course.setAllowedZones(courseZones);
            }

            if (!dryRun) {
                validCourses.add(course);
            }

            result.getValidRows().add(ImportRowDetail.builder()
                    .rowNumber(rowNum)
                    .data(rowData)
                    .status("NEW")
                    .message("Ready to import")
                    .build());
        }

        result.setErrorCount(result.getRowErrors().size());

        if (dryRun) {
            return result;
        }

        // PHASE 2: If ANY errors, reject the entire import
        if (!result.getRowErrors().isEmpty() || !result.getGlobalErrors().isEmpty()) {
            log.error("Bulk import validation failed with {} errors", result.getErrorCount());
            throw new BulkImportException(result);
        }

        // PHASE 2.5: Aggregate courses with same code (merge groups, features, zones)
        Map<String, Course> aggregatedCourses = new HashMap<>();
        for (Course course : validCourses) {
            String code = course.getCode();
            if (!aggregatedCourses.containsKey(code)) {
                aggregatedCourses.put(code, course);
            } else {
                // Merge with existing
                Course existing = aggregatedCourses.get(code);

                // Merge student groups
                if (course.getStudentGroups() != null) {
                    if (existing.getStudentGroups() == null) {
                        existing.setStudentGroups(new HashSet<>());
                    }
                    existing.getStudentGroups().addAll(course.getStudentGroups());
                    // Update primary group to first one
                    if (!existing.getStudentGroups().isEmpty()) {
                        existing.setStudentGroup(existing.getStudentGroups().iterator().next());
                    }
                }

                // Merge required features
                if (course.getRequiredFeatures() != null) {
                    if (existing.getRequiredFeatures() == null) {
                        existing.setRequiredFeatures(new HashSet<>());
                    }
                    existing.getRequiredFeatures().addAll(course.getRequiredFeatures());
                }

                // Merge allowed zones
                if (course.getAllowedZones() != null) {
                    if (existing.getAllowedZones() == null) {
                        existing.setAllowedZones(new HashSet<>());
                    }
                    existing.getAllowedZones().addAll(course.getAllowedZones());
                }

                // Keep first lecturer (could be enhanced to track multiple)
                // The variety in CSV lecturers is preserved in the import history
            }
        }

        List<Course> finalCourses = new ArrayList<>(aggregatedCourses.values());
        log.info("Aggregated {} course rows into {} unique courses", validCourses.size(), finalCourses.size());

        // PHASE 3: Save all valid entries atomically
        if (!dryRun) {
            courseRepository.saveAll(finalCourses);
            log.info("Imported {} courses atomically", finalCourses.size());

            // Record history
            result.setImportHistoryId(
                    recordHistory("COURSES", originalFilename, finalCourses.stream().map(Course::getId).toList()));
        } else {
            log.warn("DRY RUN - Skipping save of {} courses", finalCourses.size());
        }

        result.setCreatedCount(finalCourses.size());
        if (result.getErrorCount() > 0) {
            log.warn("IMPORT COMPLETE with {} errors.", result.getErrorCount());
        } else {
            log.info("IMPORT COMPLETE. Success.");
        }
        return result;
    }

    /**
     * Import courses with user-provided conflict resolutions.
     * Processes each row and applies the resolution choice for conflicts.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importCoursesWithResolutions(
            com.university.timetable.dto.ImportWithResolutionsRequest request) {

        BulkImportResult result = new BulkImportResult();
        List<Course> toCreate = new ArrayList<>();
        List<Course> toUpdate = new ArrayList<>();
        Map<Integer, ImportConflictDTO.ConflictResolution> resolutions = request.getResolutions() != null
                ? request.getResolutions()
                : new HashMap<>();

        for (int i = 0; i < request.getRows().size(); i++) {
            Map<String, String> row = request.getRows().get(i);
            int rowNum = i + 2; // 1-indexed, skip header

            String code = row.getOrDefault("code", "").trim().toUpperCase();
            String name = row.getOrDefault("name", "").trim();
            String hoursStr = row.getOrDefault("weeklyHours", "0").trim();
            String lecturerEmail = row.getOrDefault("lecturerEmail", "").trim();
            String isOnlineStr = row.getOrDefault("isOnline", "false").trim().toLowerCase();

            if (code.isEmpty() || name.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Code and name are required")
                        .rawData(row)
                        .build());
                continue;
            }

            int weeklyHours;
            try {
                weeklyHours = Integer.parseInt(hoursStr);
            } catch (NumberFormatException e) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum)
                        .message("Invalid weekly hours: " + hoursStr)
                        .rawData(row)
                        .build());
                continue;
            }

            boolean isOnline = "true".equals(isOnlineStr) || "yes".equals(isOnlineStr);

            // Find lecturer if specified
            Lecturer lecturer = null;
            if (!lecturerEmail.isEmpty()) {
                Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(lecturerEmail);
                if (lecturerOpt.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum)
                            .message("Lecturer not found: " + lecturerEmail)
                            .rawData(row)
                            .build());
                    continue;
                }
                lecturer = lecturerOpt.get();
            }

            // Check for existing course
            Optional<Course> existingOpt = courseRepository.findByCode(code);

            if (existingOpt.isPresent()) {
                // Get the resolution for this row
                ImportConflictDTO.ConflictResolution resolution = resolutions.get(rowNum);

                if (resolution == null || resolution == ImportConflictDTO.ConflictResolution.SKIP
                        || resolution == ImportConflictDTO.ConflictResolution.KEEP_EXISTING) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                if (resolution == ImportConflictDTO.ConflictResolution.UPDATE) {
                    Course existing = existingOpt.get();
                    existing.setName(name);
                    existing.setTotalWeeklyHours(weeklyHours);
                    existing.setLecturer(lecturer);
                    existing.setOnline(isOnline);
                    toUpdate.add(existing);
                    continue;
                }

                if (resolution == ImportConflictDTO.ConflictResolution.CREATE_NEW) {
                    // Create with modified code (append _2, _3, etc.)
                    String newCode = code;
                    int suffix = 2;
                    while (courseRepository.findByCode(newCode).isPresent()) {
                        newCode = code + "_" + suffix++;
                    }
                    Course course = new Course();
                    course.setCode(newCode);
                    course.setName(name);
                    course.setTotalWeeklyHours(weeklyHours);
                    course.setLecturer(lecturer);
                    course.setOnline(isOnline);
                    toCreate.add(course);
                    continue;
                }
            } else {
                // New course - create it
                Course course = new Course();
                course.setCode(code);
                course.setName(name);
                course.setTotalWeeklyHours(weeklyHours);
                course.setLecturer(lecturer);
                course.setOnline(isOnline);
                toCreate.add(course);
            }
        }

        // Check for errors before saving
        if (!result.getRowErrors().isEmpty()) {
            throw new BulkImportException(result);
        }

        // Save all
        if (!toCreate.isEmpty()) {
            courseRepository.saveAll(toCreate);
            result.setCreatedCount(toCreate.size());
        }
        if (!toUpdate.isEmpty()) {
            courseRepository.saveAll(toUpdate);
            result.setUpdatedCount(toUpdate.size());
        }

        log.info("Imported {} courses with resolutions: {} created, {} updated, {} skipped",
                toCreate.size() + toUpdate.size(), toCreate.size(), toUpdate.size(), result.getSkippedCount());

        return result;
    }

    /**
     * Import zones with user-provided conflict resolutions.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importZonesWithResolutions(
            com.university.timetable.dto.ImportWithResolutionsRequest request) {

        BulkImportResult result = new BulkImportResult();
        List<Zone> toCreate = new ArrayList<>();
        List<Zone> toUpdate = new ArrayList<>();
        Map<Integer, ImportConflictDTO.ConflictResolution> resolutions = request.getResolutions() != null
                ? request.getResolutions()
                : new HashMap<>();

        for (int i = 0; i < request.getRows().size(); i++) {
            Map<String, String> row = request.getRows().get(i);
            int rowNum = i + 2;

            String name = row.getOrDefault("name", "").trim();
            if (name.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("Name is required").rawData(row).build());
                continue;
            }

            Optional<Zone> existingOpt = zoneRepository.findByName(name);
            if (existingOpt.isPresent()) {
                ImportConflictDTO.ConflictResolution resolution = resolutions.get(rowNum);
                if (resolution == null || resolution == ImportConflictDTO.ConflictResolution.SKIP
                        || resolution == ImportConflictDTO.ConflictResolution.KEEP_EXISTING) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.UPDATE) {
                    Zone existing = existingOpt.get();
                    existing.setName(name);
                    toUpdate.add(existing);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.CREATE_NEW) {
                    String newName = name;
                    int suffix = 2;
                    while (zoneRepository.findByName(newName).isPresent()) {
                        newName = name + "_" + suffix++;
                    }
                    Zone zone = new Zone();
                    zone.setName(newName);
                    toCreate.add(zone);
                    continue;
                }
            } else {
                Zone zone = new Zone();
                zone.setName(name);
                toCreate.add(zone);
            }
        }

        if (!result.getRowErrors().isEmpty())
            throw new BulkImportException(result);
        if (!toCreate.isEmpty()) {
            zoneRepository.saveAll(toCreate);
            result.setCreatedCount(toCreate.size());
        }
        if (!toUpdate.isEmpty()) {
            zoneRepository.saveAll(toUpdate);
            result.setUpdatedCount(toUpdate.size());
        }
        log.info("Imported zones with resolutions: {} created, {} updated", toCreate.size(), toUpdate.size());
        return result;
    }

    /**
     * Import features with user-provided conflict resolutions.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importFeaturesWithResolutions(
            com.university.timetable.dto.ImportWithResolutionsRequest request) {

        BulkImportResult result = new BulkImportResult();
        List<Feature> toCreate = new ArrayList<>();
        List<Feature> toUpdate = new ArrayList<>();
        Map<Integer, ImportConflictDTO.ConflictResolution> resolutions = request.getResolutions() != null
                ? request.getResolutions()
                : new HashMap<>();

        for (int i = 0; i < request.getRows().size(); i++) {
            Map<String, String> row = request.getRows().get(i);
            int rowNum = i + 2;

            String name = row.getOrDefault("name", "").trim();
            if (name.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("Name is required").rawData(row).build());
                continue;
            }

            Optional<Feature> existingOpt = featureRepository.findByName(name);
            if (existingOpt.isPresent()) {
                ImportConflictDTO.ConflictResolution resolution = resolutions.get(rowNum);
                if (resolution == null || resolution == ImportConflictDTO.ConflictResolution.SKIP
                        || resolution == ImportConflictDTO.ConflictResolution.KEEP_EXISTING) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.UPDATE) {
                    Feature existing = existingOpt.get();
                    existing.setName(name);
                    toUpdate.add(existing);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.CREATE_NEW) {
                    String newName = name;
                    int suffix = 2;
                    while (featureRepository.findByName(newName).isPresent()) {
                        newName = name + "_" + suffix++;
                    }
                    Feature feature = new Feature();
                    feature.setName(newName);
                    toCreate.add(feature);
                    continue;
                }
            } else {
                Feature feature = new Feature();
                feature.setName(name);
                toCreate.add(feature);
            }
        }

        if (!result.getRowErrors().isEmpty())
            throw new BulkImportException(result);
        if (!toCreate.isEmpty()) {
            featureRepository.saveAll(toCreate);
            result.setCreatedCount(toCreate.size());
        }
        if (!toUpdate.isEmpty()) {
            featureRepository.saveAll(toUpdate);
            result.setUpdatedCount(toUpdate.size());
        }
        log.info("Imported features with resolutions: {} created, {} updated", toCreate.size(), toUpdate.size());
        return result;
    }

    /**
     * Import rooms with user-provided conflict resolutions.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importRoomsWithResolutions(
            com.university.timetable.dto.ImportWithResolutionsRequest request) {

        BulkImportResult result = new BulkImportResult();
        List<Room> toCreate = new ArrayList<>();
        List<Room> toUpdate = new ArrayList<>();
        Map<Integer, ImportConflictDTO.ConflictResolution> resolutions = request.getResolutions() != null
                ? request.getResolutions()
                : new HashMap<>();

        for (int i = 0; i < request.getRows().size(); i++) {
            Map<String, String> row = request.getRows().get(i);
            int rowNum = i + 2;

            String name = row.getOrDefault("name", "").trim();
            String capacityStr = row.getOrDefault("capacity", "0").trim();
            String zoneName = row.getOrDefault("zoneName", "").trim();

            if (name.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("Name is required").rawData(row).build());
                continue;
            }

            int capacity;
            try {
                capacity = Integer.parseInt(capacityStr);
            } catch (Exception e) {
                capacity = 0;
            }

            Zone zone = null;
            if (!zoneName.isEmpty()) {
                Optional<Zone> zoneOpt = zoneRepository.findByName(zoneName);
                if (zoneOpt.isEmpty()) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum).message("Zone not found: " + zoneName).rawData(row).build());
                    continue;
                }
                zone = zoneOpt.get();
            }

            Optional<Room> existingOpt = roomRepository.findByName(name);
            if (existingOpt.isPresent()) {
                ImportConflictDTO.ConflictResolution resolution = resolutions.get(rowNum);
                if (resolution == null || resolution == ImportConflictDTO.ConflictResolution.SKIP
                        || resolution == ImportConflictDTO.ConflictResolution.KEEP_EXISTING) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.UPDATE) {
                    Room existing = existingOpt.get();
                    existing.setCapacity(capacity);
                    existing.setZone(zone);
                    toUpdate.add(existing);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.CREATE_NEW) {
                    String newName = name;
                    int suffix = 2;
                    while (roomRepository.findByName(newName).isPresent()) {
                        newName = name + "_" + suffix++;
                    }
                    Room room = new Room();
                    room.setName(newName);
                    room.setCapacity(capacity);
                    room.setZone(zone);
                    toCreate.add(room);
                    continue;
                }
            } else {
                Room room = new Room();
                room.setName(name);
                room.setCapacity(capacity);
                room.setZone(zone);
                toCreate.add(room);
            }
        }

        if (!result.getRowErrors().isEmpty())
            throw new BulkImportException(result);
        if (!toCreate.isEmpty()) {
            roomRepository.saveAll(toCreate);
            result.setCreatedCount(toCreate.size());
        }
        if (!toUpdate.isEmpty()) {
            roomRepository.saveAll(toUpdate);
            result.setUpdatedCount(toUpdate.size());
        }
        log.info("Imported rooms with resolutions: {} created, {} updated", toCreate.size(), toUpdate.size());
        return result;
    }

    /**
     * Import student groups with user-provided conflict resolutions.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importStudentGroupsWithResolutions(
            com.university.timetable.dto.ImportWithResolutionsRequest request) {

        BulkImportResult result = new BulkImportResult();
        List<StudentGroup> toCreate = new ArrayList<>();
        List<StudentGroup> toUpdate = new ArrayList<>();
        Map<Integer, ImportConflictDTO.ConflictResolution> resolutions = request.getResolutions() != null
                ? request.getResolutions()
                : new HashMap<>();

        for (int i = 0; i < request.getRows().size(); i++) {
            Map<String, String> row = request.getRows().get(i);
            int rowNum = i + 2;

            // Support both old format (name, size) and new format (base_name, level, group,
            // size)
            String baseName;
            String levelStr;
            String groupNotation;
            String sizeStr = row.getOrDefault("size", "0").trim();
            String parentGroupName = row.getOrDefault("parentGroupName", row.getOrDefault("parent_group_name", ""))
                    .trim();

            // Check if using old format (name column exists)
            String nameField = row.getOrDefault("name", "").trim();
            if (!nameField.isEmpty()) {
                // Parse from combined name: e.g., "CS 100 LEVEL" -> baseName="CS", level=100,
                // group=""
                // "CS 100 Group A" -> baseName="CS", level=100, group="A"
                String[] nameParts = parseStudentGroupName(nameField);
                baseName = nameParts[0];
                levelStr = nameParts[1];
                groupNotation = nameParts[2];
            } else {
                // Use new format fields
                baseName = row.getOrDefault("base_name", "").trim();
                levelStr = row.getOrDefault("level", "").trim();
                groupNotation = row.getOrDefault("group", "").trim();
            }

            // Validate required fields
            if (baseName.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("Base name is required (provide 'name' or 'base_name' column)")
                        .rawData(row).build());
                continue;
            }

            if (levelStr.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("Level is required").rawData(row).build());
                continue;
            }

            int level;
            try {
                level = Integer.parseInt(levelStr);
                if (!Set.of(100, 200, 300, 400, 500, 600).contains(level)) {
                    result.getRowErrors().add(ImportRowError.builder()
                            .rowNumber(rowNum).message("Level must be one of: 100, 200, 300, 400, 500, 600")
                            .rawData(row).build());
                    continue;
                }
            } catch (NumberFormatException e) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("Level must be a valid number").rawData(row).build());
                continue;
            }

            int size;
            try {
                size = Integer.parseInt(sizeStr);
            } catch (Exception e) {
                size = 0;
            }

            // Compute the full name for lookup using the proper method
            String computedName = StudentGroup.computeName(baseName, level, groupNotation);

            log.debug("Processing row i={}, rowNum={}, nameField='{}', computedName='{}', size={}",
                    i, rowNum, nameField, computedName, size);

            Optional<StudentGroup> existingOpt = studentGroupRepository.findByName(computedName);
            if (existingOpt.isPresent()) {
                ImportConflictDTO.ConflictResolution resolution = resolutions.get(rowNum);
                log.debug("  -> Found existing group! Checking resolution for rowNum {}: {}", rowNum, resolution);
                log.debug("  -> Resolutions map keys: {}", resolutions.keySet());

                if (resolution == null || resolution == ImportConflictDTO.ConflictResolution.SKIP
                        || resolution == ImportConflictDTO.ConflictResolution.KEEP_EXISTING) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.UPDATE) {
                    StudentGroup existing = existingOpt.get();
                    existing.setSize(size);
                    toUpdate.add(existing);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.CREATE_NEW) {
                    // Create with modified name
                    String newBaseName = baseName;
                    int suffix = 2;
                    String newComputedName = newBaseName + " " + level + (groupNotation.isEmpty() ? "" : groupNotation);
                    while (studentGroupRepository.findByName(newComputedName).isPresent()) {
                        newBaseName = baseName + "_" + suffix++;
                        newComputedName = newBaseName + " " + level + (groupNotation.isEmpty() ? "" : groupNotation);
                    }
                    StudentGroup group = new StudentGroup();
                    group.setBaseName(newBaseName);
                    group.setLevel(level);
                    group.setGroupNotation(groupNotation.isEmpty() ? null : groupNotation);
                    group.setName(newComputedName);
                    group.setSize(size);
                    toCreate.add(group);
                    continue;
                }
            } else {
                // Create new group
                StudentGroup group = new StudentGroup();
                group.setBaseName(baseName);
                group.setLevel(level);
                group.setGroupNotation(groupNotation.isEmpty() ? null : groupNotation);
                group.setName(computedName);
                group.setSize(size);
                toCreate.add(group);
            }
        }

        if (!result.getRowErrors().isEmpty())
            throw new BulkImportException(result);

        // PHASE 2: Persist all changes to database
        List<StudentGroup> savedGroups = new ArrayList<>();

        // Save new groups
        for (StudentGroup group : toCreate) {
            savedGroups.add(studentGroupRepository.save(group));
        }

        // Save updated groups
        for (StudentGroup group : toUpdate) {
            savedGroups.add(studentGroupRepository.save(group));
        }

        log.info("Persisted {} new and {} updated student groups", toCreate.size(), toUpdate.size());

        // Recalculate parent group sizes to match sum of children
        recalculateAllParentSizes();

        // Generate Clean CSV from the resolved data
        StringBuilder cleanCsv = new StringBuilder();
        // Header: base_name, is_parent, level, group, size, parent_group_name
        cleanCsv.append("base_name,is_parent,level,group,size,parent_group_name\n");

        // Add Created/Renamed items
        for (StudentGroup g : toCreate) {
            String parentName = g.getParentGroup() != null ? g.getParentGroup().getName() : "";
            // is_parent is left empty (defaults to false)
            cleanCsv.append(String.format("%s,,%d,%s,%d,%s\n",
                    g.getBaseName(), g.getLevel(), g.getGroupNotation() != null ? g.getGroupNotation() : "",
                    g.getSize(), parentName));
        }
        // Add Updated items
        for (StudentGroup g : toUpdate) {
            String parentName = g.getParentGroup() != null ? g.getParentGroup().getName() : "";
            // is_parent is left empty (defaults to false)
            cleanCsv.append(String.format("%s,,%d,%s,%d,%s\n",
                    g.getBaseName(), g.getLevel(), g.getGroupNotation() != null ? g.getGroupNotation() : "",
                    g.getSize(), parentName));
        }

        // We leverage the result object to pass back the CSV data (hacky but avoids
        // signature change for now)
        // Or better, we throw a special exception or return a different type?
        // No, let's use the createdCount to indicate success and store the CSV in the
        // result's "message" or similar if possible.
        // Actually, let's explicitly return the CSV string in the result.
        // I'll add a 'resolvedDataCsv' field to BulkImportResult, or use 'message'.

        result.setCreatedCount(toCreate.size());
        result.setUpdatedCount(toUpdate.size());

        // This method will now be used by the controller to get the data and submit to
        // staging.
        // I will add a new field to BulkImportResult: String generatedCsvAttributes;
        result.setGeneratedCsv(cleanCsv.toString());

        log.info("Resolved student groups conflicts. Generated CSV with {} rows.", toCreate.size() + toUpdate.size());
        return result;
    }

    /**
     * Import lecturers with user-provided conflict resolutions.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult importLecturersWithResolutions(
            com.university.timetable.dto.ImportWithResolutionsRequest request) {

        BulkImportResult result = new BulkImportResult();
        List<Lecturer> toCreate = new ArrayList<>();
        List<Lecturer> toUpdate = new ArrayList<>();
        Map<Integer, ImportConflictDTO.ConflictResolution> resolutions = request.getResolutions() != null
                ? request.getResolutions()
                : new HashMap<>();

        Set<UserRole> validRoles = Set.of(UserRole.LECTURER, UserRole.COORDINATOR, UserRole.ADMIN,
                UserRole.SUPER_ADMIN);

        for (int i = 0; i < request.getRows().size(); i++) {
            Map<String, String> row = request.getRows().get(i);
            int rowNum = i + 2;

            String name = row.getOrDefault("name", "").trim();
            String email = row.getOrDefault("email", "").trim().toLowerCase();

            if (name.isEmpty() || email.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("Name and email are required").rawData(row).build());
                continue;
            }

            // Check user account
            Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
            if (userOpt.isEmpty()) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("No user account for email: " + email).rawData(row).build());
                continue;
            }
            User user = userOpt.get();
            if (!validRoles.contains(user.getRole())) {
                result.getRowErrors().add(ImportRowError.builder()
                        .rowNumber(rowNum).message("User role must be LECTURER/COORDINATOR/ADMIN").rawData(row)
                        .build());
                continue;
            }

            Optional<Lecturer> existingOpt = lecturerRepository.findByEmail(email);
            if (existingOpt.isPresent()) {
                ImportConflictDTO.ConflictResolution resolution = resolutions.get(rowNum);
                if (resolution == null || resolution == ImportConflictDTO.ConflictResolution.SKIP
                        || resolution == ImportConflictDTO.ConflictResolution.KEEP_EXISTING) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }
                if (resolution == ImportConflictDTO.ConflictResolution.UPDATE) {
                    Lecturer existing = existingOpt.get();
                    existing.setName(name);
                    toUpdate.add(existing);
                    continue;
                }
                // CREATE_NEW doesn't make sense for lecturers (unique by email)
                result.setSkippedCount(result.getSkippedCount() + 1);
            } else {
                Lecturer lecturer = new Lecturer();
                lecturer.setName(name);
                lecturer.setEmail(email);
                lecturer.setUser(user);
                toCreate.add(lecturer);
            }
        }

        if (!result.getRowErrors().isEmpty())
            throw new BulkImportException(result);
        if (!toCreate.isEmpty()) {
            lecturerRepository.saveAll(toCreate);
            result.setCreatedCount(toCreate.size());
        }
        if (!toUpdate.isEmpty()) {
            lecturerRepository.saveAll(toUpdate);
            result.setUpdatedCount(toUpdate.size());
        }
        log.info("Imported lecturers with resolutions: {} created, {} updated", toCreate.size(), toUpdate.size());
        return result;
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
     * Recalculate parent group sizes to match the sum of their children's sizes.
     * Called after import to ensure parent sizes are consistent.
     */
    private void recalculateParentGroupSizes(List<StudentGroup> savedGroups) {
        // Collect all unique parent groups from the saved groups
        Set<StudentGroup> parentGroups = new HashSet<>();
        for (StudentGroup group : savedGroups) {
            if (group.getParentGroup() != null) {
                parentGroups.add(group.getParentGroup());
            }
        }

        // Recalculate and update each parent's size
        for (StudentGroup parent : parentGroups) {
            // Refresh the parent to get all its children from DB
            Optional<StudentGroup> parentOpt = studentGroupRepository.findById(parent.getId());
            if (parentOpt.isPresent()) {
                StudentGroup refreshedParent = parentOpt.get();
                int totalChildSize = refreshedParent.getChildren().stream()
                        .mapToInt(StudentGroup::getSize)
                        .sum();

                if (totalChildSize > 0 && refreshedParent.getSize() != totalChildSize) {
                    log.info("Updating parent group '{}' size from {} to {} (sum of {} children)",
                            refreshedParent.getName(), refreshedParent.getSize(), totalChildSize,
                            refreshedParent.getChildren().size());
                    refreshedParent.setSize(totalChildSize);
                    studentGroupRepository.save(refreshedParent);

                    // Force refresh to update children collection in persistence context
                    entityManager.flush();
                    entityManager.refresh(refreshedParent);
                }
            }
        }
    }

    /**
     * Parse CSV file into list of rows.
     */
    public List<String[]> parseCsv(MultipartFile file) throws Exception {
        return parseCsv(new BufferedReader(new InputStreamReader(file.getInputStream())));
    }

    public List<String[]> parseCsv(BufferedReader reader) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (reader) {
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

    /**
     * Generate a secure random password for bulk import.
     */
    private String generateSecurePassword() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    /**
     * Create or link a Lecturer entity for a user with LECTURER role.
     * Used during bulk import to ensure lecturer entities are created.
     * Updates BOTH sides of the bidirectional relationship.
     */
    private void createOrLinkLecturerForUser(User user) {
        // Check if lecturer already exists for this user
        Optional<Lecturer> existingLecturer = lecturerRepository.findByUser(user);
        if (existingLecturer.isPresent()) {
            // Ensure User.lecturer is also set
            if (user.getLecturer() == null) {
                user.setLecturer(existingLecturer.get());
                userRepository.save(user);
            }
            return;
        }

        // Check if there's a lecturer with matching email that can be linked
        Optional<Lecturer> lecturerByEmail = lecturerRepository.findByEmail(user.getEmail());
        if (lecturerByEmail.isPresent()) {
            Lecturer lecturer = lecturerByEmail.get();
            if (lecturer.getUser() == null) {
                // Link both sides
                lecturer.setUser(user);
                user.setLecturer(lecturer);
                lecturerRepository.save(lecturer);
                userRepository.save(user);
                log.info("Linked existing lecturer {} to user {}", lecturer.getName(), user.getEmail());
                return;
            }
        }

        // Create new Lecturer entity
        String fullName = user.getFirstName() + " " + user.getLastName();
        Lecturer newLecturer = new Lecturer();
        newLecturer.setName(fullName);
        newLecturer.setEmail(user.getEmail());
        newLecturer.setUser(user);
        Lecturer savedLecturer = lecturerRepository.save(newLecturer);

        // Link user to lecturer
        user.setLecturer(savedLecturer);
        userRepository.save(user);

        log.info("Created new lecturer {} for user {}", fullName, user.getEmail());
    }

    /**
     * Helper to record import history.
     */
    private Long recordHistory(String entityType, String fileName, List<Long> createdIds) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null) {
                User currentUser = userRepository.findByEmailIgnoreCase(auth.getName()).orElse(null);
                ImportHistory history = importHistoryService.recordImport(entityType, "STRICT", fileName, createdIds,
                        currentUser);
                return history.getId();
            }
        } catch (Exception e) {
            log.error("Failed to record import history for {}", entityType, e);
        }
        return null;
    }

    /**
     * Recalculate all parent group sizes based on sum of their children's sizes.
     * This ensures parent sizes are always correct after imports or updates.
     */
    @Transactional
    public void recalculateAllParentSizes() {
        // Get all groups that have children (i.e., are parents)
        List<StudentGroup> allGroups = studentGroupRepository.findAll();

        // Find parent groups (groups that have children pointing to them)
        Set<Long> parentIds = allGroups.stream()
                .filter(g -> g.getParentGroup() != null)
                .map(g -> g.getParentGroup().getId())
                .collect(java.util.stream.Collectors.toSet());

        for (Long parentId : parentIds) {
            StudentGroup parent = studentGroupRepository.findById(parentId).orElse(null);
            if (parent != null) {
                // Sum up all children's sizes
                int totalChildSize = allGroups.stream()
                        .filter(g -> g.getParentGroup() != null && g.getParentGroup().getId().equals(parentId))
                        .mapToInt(StudentGroup::getSize)
                        .sum();

                if (parent.getSize() != totalChildSize) {
                    log.info("Updating parent group '{}' size from {} to {} (sum of {} children)",
                            parent.getName(), parent.getSize(), totalChildSize,
                            allGroups.stream()
                                    .filter(g -> g.getParentGroup() != null
                                            && g.getParentGroup().getId().equals(parentId))
                                    .count());
                    parent.setSize(totalChildSize);
                    studentGroupRepository.save(parent);

                    // Force refresh to update children collection in persistence context
                    entityManager.flush();
                    entityManager.refresh(parent);
                }
            }
        }
    }

    /**
     * Parse a combined student group name into its components.
     * Examples:
     * "CS 100 LEVEL" -> ["CS", "100", ""]
     * "CS 100 Group A" -> ["CS", "100", "A"]
     * "Computer Science 200 LEVEL" -> ["Computer Science", "200", ""]
     * "CS 300" -> ["CS", "300", ""]
     * 
     * @param name The combined group name
     * @return String array with [baseName, level, groupNotation]
     */
    private String[] parseStudentGroupName(String name) {
        String baseName = "";
        String level = "";
        String groupNotation = "";

        if (name == null || name.isEmpty()) {
            return new String[] { baseName, level, groupNotation };
        }

        // Pattern: "BaseName Level LEVEL (GRP X)" or "BaseName Level LEVEL" or
        // "BaseName Level Group X"
        // Examples: "CS 100 LEVEL (GRP A)", "CS 100 LEVEL", "CS 100 Group A"

        // First, check for (GRP X) at the end
        java.util.regex.Pattern grpPattern = java.util.regex.Pattern.compile("\\(GRP\\s+([^)]+)\\)\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher grpMatcher = grpPattern.matcher(name);
        if (grpMatcher.find()) {
            groupNotation = grpMatcher.group(1).trim();
            // Remove the (GRP X) part from name for further processing
            name = name.substring(0, grpMatcher.start()).trim();
        }

        // Look for a level number (100, 200, 300, 400, 500, 600)
        java.util.regex.Pattern levelPattern = java.util.regex.Pattern.compile("\\b(100|200|300|400|500|600)\\b");
        java.util.regex.Matcher matcher = levelPattern.matcher(name);

        if (matcher.find()) {
            level = matcher.group(1);
            int levelStart = matcher.start();
            int levelEnd = matcher.end();

            // Everything before the level is the base name
            baseName = name.substring(0, levelStart).trim();

            // Everything after the level (should be "LEVEL" or empty after we removed GRP)
            String afterLevel = levelEnd < name.length() ? name.substring(levelEnd).trim() : "";

            // If we already extracted groupNotation from (GRP X), we're done
            // Otherwise check for "Group X" format
            if (groupNotation.isEmpty()) {
                if (afterLevel.equalsIgnoreCase("LEVEL") || afterLevel.isEmpty()) {
                    groupNotation = "";
                } else if (afterLevel.toUpperCase().startsWith("GROUP")) {
                    // Extract the group letter/number after "Group"
                    groupNotation = afterLevel.substring(5).trim(); // Remove "Group" prefix
                } else if (!afterLevel.equalsIgnoreCase("LEVEL")) {
                    // Just use whatever is there as group notation (but not "LEVEL" itself)
                    groupNotation = afterLevel;
                }
            }
        } else {
            // No level found, treat entire name as baseName
            baseName = name;
            level = "100"; // Default level
        }

        return new String[] { baseName, level, groupNotation };
    }
}
