package com.university.timetable.controller;

import com.university.timetable.domain.StudentGroup;
import com.university.timetable.repository.StudentGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/student-groups")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StudentGroupController {

    private final StudentGroupRepository studentGroupRepository;

    @GetMapping
    public List<StudentGroupDTO> getAll() {
        return studentGroupRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentGroupDTO> getById(@PathVariable Long id) {
        return studentGroupRepository.findById(id)
                .map(g -> ResponseEntity.ok(toDTO(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public StudentGroupDTO create(@RequestBody StudentGroupCreateDTO dto) {
        StudentGroup group = new StudentGroup();
        group.setName(dto.name);
        group.setSize(dto.size);
        
        if (dto.parentGroupId != null) {
            studentGroupRepository.findById(dto.parentGroupId)
                    .ifPresent(group::setParentGroup);
        }
        
        return toDTO(studentGroupRepository.save(group));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentGroupDTO> update(@PathVariable Long id, @RequestBody StudentGroupCreateDTO dto) {
        return studentGroupRepository.findById(id)
                .map(group -> {
                    group.setName(dto.name);
                    group.setSize(dto.size);
                    
                    if (dto.parentGroupId != null) {
                        studentGroupRepository.findById(dto.parentGroupId)
                                .ifPresent(group::setParentGroup);
                    } else {
                        group.setParentGroup(null);
                    }
                    
                    return ResponseEntity.ok(toDTO(studentGroupRepository.save(group)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!studentGroupRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        studentGroupRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private StudentGroupDTO toDTO(StudentGroup group) {
        StudentGroupDTO dto = new StudentGroupDTO();
        dto.id = group.getId();
        dto.name = group.getName();
        dto.size = group.getSize();
        dto.parentGroupId = group.getParentGroup() != null ? group.getParentGroup().getId() : null;
        dto.parentGroupName = group.getParentGroup() != null ? group.getParentGroup().getName() : null;
        dto.childCount = group.getChildren() != null ? group.getChildren().size() : 0;
        return dto;
    }

    public static class StudentGroupDTO {
        public Long id;
        public String name;
        public Integer size;
        public Long parentGroupId;
        public String parentGroupName;
        public int childCount;
    }

    public static class StudentGroupCreateDTO {
        public String name;
        public Integer size;
        public Long parentGroupId;
    }
}

