package com.university.timetable.controller;

import com.university.timetable.domain.StudentGroup;
import com.university.timetable.repository.StudentGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("isAuthenticated()")
    public List<StudentGroupDTO> getAll() {
        return studentGroupRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StudentGroupDTO> getById(@PathVariable Long id) {
        return studentGroupRepository.findById(id)
                .map(g -> ResponseEntity.ok(toDTO(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public StudentGroupDTO create(@RequestBody StudentGroupCreateDTO dto) {
        StudentGroup group = new StudentGroup();
        group.setName(dto.name);
        group.setSize(dto.size != null ? dto.size : 0);

        if (dto.parentGroupId != null) {
            studentGroupRepository.findById(dto.parentGroupId)
                    .ifPresent(group::setParentGroup);
        }

        StudentGroup saved = studentGroupRepository.save(group);

        if (saved.getParentGroup() != null) {
            recalculateParentSize(saved.getParentGroup());
        }

        return toDTO(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<StudentGroupDTO> update(@PathVariable Long id, @RequestBody StudentGroupCreateDTO dto) {
        return studentGroupRepository.findById(id)
                .map(group -> {
                    StudentGroup oldParent = group.getParentGroup();

                    group.setName(dto.name);
                    group.setSize(dto.size != null ? dto.size : 0);

                    if (dto.parentGroupId != null) {
                        studentGroupRepository.findById(dto.parentGroupId)
                                .ifPresent(group::setParentGroup);
                    } else {
                        group.setParentGroup(null);
                    }

                    StudentGroup saved = studentGroupRepository.save(group);

                    if (oldParent != null && !oldParent.equals(saved.getParentGroup())) {
                        recalculateParentSize(oldParent);
                    }

                    if (saved.getParentGroup() != null) {
                        recalculateParentSize(saved.getParentGroup());
                    }

                    return ResponseEntity.ok(toDTO(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return studentGroupRepository.findById(id)
                .map(group -> {
                    StudentGroup parent = group.getParentGroup();
                    studentGroupRepository.deleteById(id);

                    if (parent != null) {
                        studentGroupRepository.findById(parent.getId())
                                .ifPresent(this::recalculateParentSize);
                    }

                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void recalculateParentSize(StudentGroup parent) {
        studentGroupRepository.findById(parent.getId()).ifPresent(p -> {
            if (p.getChildren() != null && !p.getChildren().isEmpty()) {
                int totalChildSize = p.getChildren().stream()
                        .map(StudentGroup::getSize)
                        .filter(size -> size != null)
                        .mapToInt(Integer::intValue)
                        .sum();
                p.setSize(totalChildSize);
                studentGroupRepository.save(p);

                if (p.getParentGroup() != null) {
                    recalculateParentSize(p.getParentGroup());
                }
            }
        });
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
