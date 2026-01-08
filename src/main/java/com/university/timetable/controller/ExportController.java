package com.university.timetable.controller;

import com.university.timetable.domain.StudentGroup;
import com.university.timetable.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for timetable exports (PDF and Excel).
 */
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ExportController {

    private final ExportService exportService;

    /**
     * Export timetable to Excel (.xlsx) format.
     */
    @PostMapping("/excel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportExcel(@RequestBody ExportRequestDTO request) {
        try {
            byte[] data = exportService.exportToExcel(request.groupIds, request.title);
            String filename = generateFilename("timetable", "xlsx");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            log.error("Error exporting to Excel", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export timetable to PDF format.
     */
    @PostMapping("/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportPdf(@RequestBody ExportRequestDTO request) {
        try {
            byte[] data = exportService.exportToPdf(request.groupIds, request.title);
            String filename = generateFilename("timetable", "pdf");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(data);
        } catch (Exception e) {
            log.error("Error exporting to PDF", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get departments (parent groups) for the export selection UI.
     */
    @GetMapping("/departments")
    @PreAuthorize("isAuthenticated()")
    public List<DepartmentDTO> getDepartments() {
        return exportService.getDepartments().stream()
                .map(this::toDepartmentDTO)
                .toList();
    }

    private String generateFilename(String prefix, String extension) {
        return prefix + "_" + LocalDate.now().toString() + "." + extension;
    }

    private DepartmentDTO toDepartmentDTO(StudentGroup group) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.id = group.getId();
        dto.name = group.getName();
        dto.size = group.getSize();
        dto.childCount = group.getChildren() != null ? group.getChildren().size() : 0;
        dto.isParent = group.getParentGroup() == null;
        return dto;
    }

    public static class ExportRequestDTO {
        public List<Long> groupIds; // Empty = all groups
        public String title;
    }

    public static class DepartmentDTO {
        public Long id;
        public String name;
        public int size;
        public int childCount;
        public boolean isParent;
    }
}
