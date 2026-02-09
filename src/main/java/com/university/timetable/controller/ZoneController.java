package com.university.timetable.controller;

import com.university.timetable.domain.AuditAction;
import com.university.timetable.domain.Zone;
import com.university.timetable.repository.ZoneRepository;
import com.university.timetable.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ZoneController {

    private final ZoneRepository zoneRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ZoneDTO> getAll() {
        return zoneRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ZoneDTO> getById(@PathVariable Long id) {
        return zoneRepository.findById(id)
                .map(z -> ResponseEntity.ok(toDTO(z)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ZoneDTO create(@RequestBody ZoneCreateDTO dto) {
        Zone zone = new Zone();
        zone.setName(dto.name);
        Zone saved = zoneRepository.save(zone);

        auditLogService.logAction(AuditAction.CREATE, "Zone", saved.getId().toString(),
                saved.getName(), null, toDTO(saved), "Created zone " + saved.getName());

        return toDTO(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<ZoneDTO> update(@PathVariable Long id, @RequestBody ZoneCreateDTO dto) {
        return zoneRepository.findById(id)
                .map(zone -> {
                    ZoneDTO previousState = toDTO(zone);
                    zone.setName(dto.name);
                    Zone updated = zoneRepository.save(zone);

                    auditLogService.logAction(AuditAction.UPDATE, "Zone", updated.getId().toString(),
                            updated.getName(), previousState, toDTO(updated), "Updated zone " + updated.getName());

                    return ResponseEntity.ok(toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return zoneRepository.findById(id)
                .map(zone -> {
                    ZoneDTO previousState = toDTO(zone);
                    zoneRepository.deleteById(id);

                    auditLogService.logAction(AuditAction.DELETE, "Zone", id.toString(),
                            zone.getName(), previousState, null, "Deleted zone " + zone.getName());

                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ZoneDTO toDTO(Zone zone) {
        ZoneDTO dto = new ZoneDTO();
        dto.id = zone.getId();
        dto.name = zone.getName();
        return dto;
    }

    public static class ZoneDTO {
        public Long id;
        public String name;
    }

    public static class ZoneCreateDTO {
        public String name;
    }
}
