package com.university.timetable.controller;

import com.university.timetable.domain.AuditAction;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.Feature;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.FeatureRepository;
import com.university.timetable.repository.ZoneRepository;
import com.university.timetable.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoomController {

    private final RoomRepository roomRepository;
    private final ZoneRepository zoneRepository;
    private final FeatureRepository featureRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<RoomDTO> getAll() {
        return roomRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RoomDTO> getById(@PathVariable Long id) {
        return roomRepository.findById(id)
                .map(r -> ResponseEntity.ok(toDTO(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<RoomDTO> create(@RequestBody RoomCreateDTO dto) {
        Room room = new Room();
        room.setName(dto.name);
        room.setCapacity(dto.capacity);

        if (dto.zoneId != null) {
            zoneRepository.findById(dto.zoneId).ifPresent(room::setZone);
        }

        Set<Feature> features = resolveFeatures(dto.featureIds, dto.featureNames);
        if (!features.isEmpty()) {
            room.setFeatures(features);
        }

        Room saved = roomRepository.save(room);

        auditLogService.logAction(AuditAction.CREATE, "Room", saved.getId().toString(),
                saved.getName(), null, toDTO(saved), "Created room " + saved.getName());

        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<RoomDTO> update(@PathVariable Long id, @RequestBody RoomCreateDTO dto) {
        return roomRepository.findById(id)
                .map(room -> {
                    RoomDTO previousState = toDTO(room);
                    room.setName(dto.name);
                    room.setCapacity(dto.capacity);

                    if (dto.zoneId != null) {
                        zoneRepository.findById(dto.zoneId).ifPresent(room::setZone);
                    }

                    Set<Feature> features = resolveFeatures(dto.featureIds, dto.featureNames);
                    room.setFeatures(features);

                    Room updated = roomRepository.save(room);

                    auditLogService.logAction(AuditAction.UPDATE, "Room", updated.getId().toString(),
                            updated.getName(), previousState, toDTO(updated), "Updated room " + updated.getName());

                    return ResponseEntity.ok(toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return roomRepository.findById(id)
                .map(room -> {
                    RoomDTO previousState = toDTO(room);
                    roomRepository.deleteById(id);

                    auditLogService.logAction(AuditAction.DELETE, "Room", id.toString(),
                            room.getName(), previousState, null, "Deleted room " + room.getName());

                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private RoomDTO toDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.id = room.getId();
        dto.name = room.getName();
        dto.capacity = room.getCapacity();
        dto.zoneName = room.getZone() != null ? room.getZone().getName() : null;
        dto.zoneId = room.getZone() != null ? room.getZone().getId() : null;
        dto.features = room.getFeatures() != null
                ? room.getFeatures().stream().map(Feature::getName).collect(Collectors.toList())
                : List.of();
        dto.featureIds = room.getFeatures() != null
                ? room.getFeatures().stream().map(Feature::getId).collect(Collectors.toList())
                : List.of();
        return dto;
    }

    private Set<Feature> resolveFeatures(List<Long> featureIds, List<String> featureNames) {
        Set<Feature> features = new java.util.HashSet<>();
        
        // Resolve by IDs
        if (featureIds != null && !featureIds.isEmpty()) {
            for (Long id : featureIds) {
                featureRepository.findById(id).ifPresent(features::add);
            }
        }
        
        // Resolve by names
        if (featureNames != null && !featureNames.isEmpty()) {
            for (String name : featureNames) {
                featureRepository.findByName(name).ifPresent(features::add);
            }
        }
        
        return features;
    }

    public static class RoomDTO {
        public Long id;
        public String name;
        public Integer capacity;
        public String zoneName;
        public Long zoneId;
        public List<String> features;
        public List<Long> featureIds;
    }

    public static class RoomCreateDTO {
        public String name;
        public Integer capacity;
        public Long zoneId;
        public List<Long> featureIds;
        public List<String> featureNames;
    }
}
