package com.university.timetable.controller;

import com.university.timetable.domain.Zone;
import com.university.timetable.repository.ZoneRepository;
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
        return toDTO(zoneRepository.save(zone));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<ZoneDTO> update(@PathVariable Long id, @RequestBody ZoneCreateDTO dto) {
        return zoneRepository.findById(id)
                .map(zone -> {
                    zone.setName(dto.name);
                    return ResponseEntity.ok(toDTO(zoneRepository.save(zone)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!zoneRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        zoneRepository.deleteById(id);
        return ResponseEntity.noContent().build();
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
