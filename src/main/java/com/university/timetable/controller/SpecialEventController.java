package com.university.timetable.controller;

import com.university.timetable.domain.*;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/special-events")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SpecialEventController {

    private final SpecialEventRepository specialEventRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final RoomRepository roomRepository;
    private final LecturerRepository lecturerRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SpecialEventDTO> getAll() {
        return specialEventRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public List<SpecialEventDTO> getActive() {
        return specialEventRepository.findByActiveTrue().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SpecialEventDTO> getById(@PathVariable Long id) {
        return specialEventRepository.findById(id)
                .map(e -> ResponseEntity.ok(toDTO(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<SpecialEventDTO> create(@RequestBody SpecialEventCreateDTO dto) {
        SpecialEvent event = new SpecialEvent();
        updateFromDTO(event, dto);
        SpecialEvent saved = specialEventRepository.save(event);
        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<SpecialEventDTO> update(@PathVariable Long id, @RequestBody SpecialEventCreateDTO dto) {
        return specialEventRepository.findById(id)
                .map(event -> {
                    updateFromDTO(event, dto);
                    return ResponseEntity.ok(toDTO(specialEventRepository.save(event)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!specialEventRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        specialEventRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<SpecialEventDTO> toggleActive(@PathVariable Long id) {
        return specialEventRepository.findById(id)
                .map(event -> {
                    event.setActive(!event.isActive());
                    return ResponseEntity.ok(toDTO(specialEventRepository.save(event)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void updateFromDTO(SpecialEvent event, SpecialEventCreateDTO dto) {
        event.setName(dto.name);
        event.setDescription(dto.description);
        event.setDayOfWeek(DayOfWeek.valueOf(dto.dayOfWeek.toUpperCase()));
        event.setStartTime(LocalTime.parse(dto.startTime));
        event.setDurationHours(dto.durationHours != null ? dto.durationHours : 2);
        event.setOnline(dto.online != null && dto.online);
        event.setActive(dto.active == null || dto.active);

        if (dto.roomId != null) {
            roomRepository.findById(dto.roomId).ifPresent(event::setRoom);
        } else {
            event.setRoom(null);
        }

        if (dto.lecturerId != null) {
            lecturerRepository.findById(dto.lecturerId).ifPresent(event::setLecturer);
        } else {
            event.setLecturer(null);
        }

        if (dto.studentGroupIds != null && !dto.studentGroupIds.isEmpty()) {
            Set<StudentGroup> groups = dto.studentGroupIds.stream()
                    .map(studentGroupRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toSet());
            event.setStudentGroups(groups);
        } else {
            event.setStudentGroups(Set.of());
        }
    }

    private SpecialEventDTO toDTO(SpecialEvent event) {
        SpecialEventDTO dto = new SpecialEventDTO();
        dto.id = event.getId();
        dto.name = event.getName();
        dto.description = event.getDescription();
        dto.dayOfWeek = event.getDayOfWeek().name();
        dto.startTime = event.getStartTime().toString();
        dto.endTime = event.getEndTime().toString();
        dto.durationHours = event.getDurationHours();
        dto.roomId = event.getRoom() != null ? event.getRoom().getId() : null;
        dto.roomName = event.getRoom() != null ? event.getRoom().getName() : null;
        dto.lecturerId = event.getLecturer() != null ? event.getLecturer().getId() : null;
        dto.lecturerName = event.getLecturer() != null ? event.getLecturer().getName() : null;
        dto.online = event.isOnline();
        dto.active = event.isActive();
        dto.studentGroupIds = event.getStudentGroups().stream()
                .map(StudentGroup::getId)
                .collect(Collectors.toList());
        dto.studentGroupNames = event.getStudentGroups().stream()
                .map(StudentGroup::getName)
                .collect(Collectors.toList());
        return dto;
    }

    public static class SpecialEventDTO {
        public Long id;
        public String name;
        public String description;
        public String dayOfWeek;
        public String startTime;
        public String endTime;
        public Integer durationHours;
        public Long roomId;
        public String roomName;
        public Long lecturerId;
        public String lecturerName;
        public Boolean online;
        public Boolean active;
        public List<Long> studentGroupIds;
        public List<String> studentGroupNames;
    }

    public static class SpecialEventCreateDTO {
        public String name;
        public String description;
        public String dayOfWeek;
        public String startTime;
        public Integer durationHours;
        public Long roomId;
        public Long lecturerId;
        public Boolean online;
        public Boolean active;
        public List<Long> studentGroupIds;
    }
}
