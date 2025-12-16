package com.university.timetable.controller;

import com.university.timetable.domain.SemesterArchive;
import com.university.timetable.dto.ArchiveRequestDTO;
import com.university.timetable.dto.SemesterArchiveDTO;
import com.university.timetable.dto.TimetableViewDTO;
import com.university.timetable.service.SemesterArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for semester archive operations.
 * 
 * Endpoints:
 * - GET /api/v1/semesters/archives - List all archives
 * - POST /api/v1/semesters/archive - Archive current semester
 * - GET /api/v1/semesters/{code}/timetable - View archived timetable
 * - DELETE /api/v1/semesters/{code} - Delete archive
 */
@RestController
@RequestMapping("/api/v1/semesters")
@RequiredArgsConstructor
@Slf4j
public class SemesterArchiveController {

    private final SemesterArchiveService archiveService;

    /**
     * GET /semesters/archives
     * List all archived semesters.
     */
    @GetMapping("/archives")
    public ResponseEntity<List<SemesterArchiveDTO>> listArchives() {
        log.info("Listing all semester archives");
        List<SemesterArchiveDTO> archives = archiveService.getAllArchives()
            .stream()
            .map(SemesterArchiveDTO::fromEntity)
            .collect(Collectors.toList());
        return ResponseEntity.ok(archives);
    }

    /**
     * POST /semesters/archive
     * Archive current semester data to prefixed tables.
     * 
     * Body: {"code": "2024_2025_S1", "name": "2024/2025 1st Semester"}
     */
    @PostMapping("/archive")
    public ResponseEntity<?> archiveCurrentSemester(@RequestBody ArchiveRequestDTO request) {
        log.info("Archiving current semester as: {}", request.getCode());
        
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Code is required"));
        }
        
        // Validate code format (alphanumeric and underscores only)
        if (!request.getCode().matches("^[a-zA-Z0-9_]+$")) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Code must be alphanumeric with underscores only"));
        }
        
        try {
            SemesterArchive archive = archiveService.archiveCurrentSemester(request);
            return ResponseEntity.ok(Map.of(
                "message", "Semester archived successfully",
                "archive", SemesterArchiveDTO.fromEntity(archive)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /semesters/{code}
     * Get archive metadata by code.
     */
    @GetMapping("/{code}")
    public ResponseEntity<?> getArchive(@PathVariable String code) {
        try {
            SemesterArchive archive = archiveService.getArchive(code);
            return ResponseEntity.ok(SemesterArchiveDTO.fromEntity(archive));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /semesters/{code}/timetable
     * View timetable from an archived semester.
     */
    @GetMapping("/{code}/timetable")
    public ResponseEntity<?> getArchivedTimetable(@PathVariable String code) {
        log.info("Fetching archived timetable for: {}", code);
        
        try {
            List<TimetableViewDTO> timetable = archiveService.getArchivedTimetable(code);
            return ResponseEntity.ok(timetable);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching archived timetable", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to fetch archived timetable: " + e.getMessage()));
        }
    }

    /**
     * DELETE /semesters/{code}
     * Delete an archive and drop its tables.
     */
    @DeleteMapping("/{code}")
    public ResponseEntity<?> deleteArchive(@PathVariable String code) {
        log.info("Deleting archive: {}", code);
        
        try {
            archiveService.deleteArchive(code);
            return ResponseEntity.ok(Map.of(
                "message", "Archive deleted successfully",
                "code", code
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
