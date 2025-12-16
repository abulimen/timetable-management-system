package com.university.timetable.controller;

import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.LecturerUnavailability;
import com.university.timetable.repository.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/lecturers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LecturerController {

    private final LecturerRepository lecturerRepository;

    @GetMapping
    public List<LecturerDTO> getAll() {
        return lecturerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LecturerDTO> getById(@PathVariable Long id) {
        return lecturerRepository.findById(id)
                .map(l -> ResponseEntity.ok(toDTO(l)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public LecturerDTO create(@RequestBody LecturerCreateDTO dto) {
        Lecturer lecturer = new Lecturer();
        lecturer.setName(dto.name);
        lecturer.setEmail(dto.email);
        return toDTO(lecturerRepository.save(lecturer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LecturerDTO> update(@PathVariable Long id, @RequestBody LecturerCreateDTO dto) {
        return lecturerRepository.findById(id)
                .map(lecturer -> {
                    lecturer.setName(dto.name);
                    lecturer.setEmail(dto.email);
                    return ResponseEntity.ok(toDTO(lecturerRepository.save(lecturer)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!lecturerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        lecturerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unavailability")
    public ResponseEntity<LecturerDTO> addUnavailability(@PathVariable Long id, @RequestBody UnavailabilityDTO dto) {
        return lecturerRepository.findById(id)
                .map(lecturer -> {
                    LecturerUnavailability unavailability = new LecturerUnavailability();
                    unavailability.setLecturer(lecturer);
                    unavailability.setDayOfWeek(DayOfWeek.valueOf(dto.dayOfWeek.toUpperCase()));
                    unavailability.setStartTime(LocalTime.parse(dto.startTime));
                    unavailability.setEndTime(LocalTime.parse(dto.endTime));
                    lecturer.getUnavailabilities().add(unavailability);
                    return ResponseEntity.ok(toDTO(lecturerRepository.save(lecturer)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/unavailability/{unavailabilityId}")
    public ResponseEntity<LecturerDTO> removeUnavailability(@PathVariable Long id, @PathVariable Long unavailabilityId) {
        return lecturerRepository.findById(id)
                .map(lecturer -> {
                    lecturer.getUnavailabilities().removeIf(u -> u.getId().equals(unavailabilityId));
                    return ResponseEntity.ok(toDTO(lecturerRepository.save(lecturer)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private LecturerDTO toDTO(Lecturer lecturer) {
        LecturerDTO dto = new LecturerDTO();
        dto.id = lecturer.getId();
        dto.name = lecturer.getName();
        dto.email = lecturer.getEmail();
        dto.unavailabilities = lecturer.getUnavailabilities() != null
                ? lecturer.getUnavailabilities().stream().map(u -> {
                    UnavailabilityDTO uDto = new UnavailabilityDTO();
                    uDto.id = u.getId();
                    uDto.dayOfWeek = u.getDayOfWeek().toString();
                    uDto.startTime = u.getStartTime().toString();
                    uDto.endTime = u.getEndTime().toString();
                    return uDto;
                }).collect(Collectors.toList())
                : List.of();
        return dto;
    }

    public static class LecturerDTO {
        public Long id;
        public String name;
        public String email;
        public List<UnavailabilityDTO> unavailabilities;
    }

    public static class LecturerCreateDTO {
        public String name;
        public String email;
    }

    public static class UnavailabilityDTO {
        public Long id;
        public String dayOfWeek;
        public String startTime;
        public String endTime;
    }
}
