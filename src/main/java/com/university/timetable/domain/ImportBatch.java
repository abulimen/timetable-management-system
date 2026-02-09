package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Represents a batch of data uploaded for import, waiting for approval.
 * Stores the raw CSV data as a BLOB.
 */
@Entity
@Table(name = "import_batch")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String entityType; // e.g., "COURSES", "LECTURERS"

    @Column(nullable = false)
    private String originalFilename;

    @Lob
    @Basic(fetch = FetchType.EAGER)
    @Column(columnDefinition = "MEDIUMBLOB", nullable = false)
    private byte[] fileData;

    @Column(columnDefinition = "TEXT")
    private String submissionNote;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private ImportStatus status;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    private LocalDateTime approvalDate;

    // Link to the history record of the actual import
    private Long importHistoryId;

    public enum ImportStatus {
        DRAFT,
        PENDING,
        APPROVED,
        REJECTED
    }
}
