package com.university.timetable.controller;

import com.university.timetable.domain.Room;
import com.university.timetable.domain.Feature;
import com.university.timetable.domain.Zone;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.FeatureRepository;
import com.university.timetable.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public List<RoomDTO> getAll() {
        return roomRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomDTO> getById(@PathVariable Long id) {
        return roomRepository.findById(id)
                .map(r -> ResponseEntity.ok(toDTO(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RoomDTO> create(@RequestBody RoomCreateDTO dto) {
        Room room = new Room();
        room.setName(dto.name);
        room.setCapacity(dto.capacity);
        
        if (dto.zoneId != null) {
            zoneRepository.findById(dto.zoneId).ifPresent(room::setZone);
        }
        
        if (dto.featureIds != null && !dto.featureIds.isEmpty()) {
            Set<Feature> features = dto.featureIds.stream()
                    .map(featureRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toSet());
            room.setFeatures(features);
        }
        
        return ResponseEntity.ok(toDTO(roomRepository.save(room)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoomDTO> update(@PathVariable Long id, @RequestBody RoomCreateDTO dto) {
        return roomRepository.findById(id)
                .map(room -> {
                    room.setName(dto.name);
                    room.setCapacity(dto.capacity);
                    
                    if (dto.zoneId != null) {
                        zoneRepository.findById(dto.zoneId).ifPresent(room::setZone);
                    }
                    
                    if (dto.featureIds != null) {
                        Set<Feature> features = dto.featureIds.stream()
                                .map(featureRepository::findById)
                                .filter(java.util.Optional::isPresent)
                                .map(java.util.Optional::get)
                                .collect(Collectors.toSet());
                        room.setFeatures(features);
                    }
                    
                    return ResponseEntity.ok(toDTO(roomRepository.save(room)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!roomRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        roomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
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
    }
}
