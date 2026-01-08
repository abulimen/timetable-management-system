package com.university.timetable.controller;

import com.university.timetable.domain.Feature;
import com.university.timetable.repository.FeatureRepository;
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
        return featureRepository.save(feature);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<Feature> update(@PathVariable Long id, @RequestBody Feature feature) {
        if (!featureRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        feature.setId(id);
        return ResponseEntity.ok(featureRepository.save(feature));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!featureRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        featureRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
