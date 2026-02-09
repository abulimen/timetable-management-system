package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for audit log entries tracking all data changes in the system.
 * Audit logs are immutable - no UPDATE or DELETE operations allowed.
 */
@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // Actor Information
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ActorType actorType;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_ip_address", length = 45)
    private String actorIpAddress;

    // Action Information
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private AuditAction action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "entity_name")
    private String entityName;

    // Change Details (stored as JSON strings)
    @Column(name = "previous_value", columnDefinition = "JSON")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "JSON")
    private String newValue;

    @Column(name = "changed_fields", length = 1000)
    private String changedFields;

    // Context
    @Column(length = 500)
    private String description;

    @Column(name = "request_id", length = 50)
    private String requestId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    // Metadata
    @Builder.Default
    private Boolean success = true;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
