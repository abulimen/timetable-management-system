package com.university.timetable.controller;

import com.university.timetable.domain.AuditAction;
import com.university.timetable.domain.Feature;
import com.university.timetable.repository.FeatureRepository;
import com.university.timetable.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeatureController {

    private final FeatureRepository featureRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Feature> getAll() {
        return featureRepository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Feature> getById(@PathVariable Long id) {
        return featureRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public Feature create(@RequestBody Feature feature) {
        Feature saved = featureRepository.save(feature);

        auditLogService.logAction(AuditAction.CREATE, "Feature", saved.getId().toString(),
                saved.getName(), null, saved, "Created feature " + saved.getName());

        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<Feature> update(@PathVariable Long id, @RequestBody Feature feature) {
        return featureRepository.findById(id)
                .map(existing -> {
                    Feature previousState = new Feature();
                    previousState.setId(existing.getId());
                    previousState.setName(existing.getName());

                    feature.setId(id);
                    Feature updated = featureRepository.save(feature);

                    auditLogService.logAction(AuditAction.UPDATE, "Feature", updated.getId().toString(),
                            updated.getName(), previousState, updated, "Updated feature " + updated.getName());

                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return featureRepository.findById(id)
                .map(feature -> {
                    featureRepository.deleteById(id);

                    auditLogService.logAction(AuditAction.DELETE, "Feature", id.toString(),
                            feature.getName(), feature, null, "Deleted feature " + feature.getName());

                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
