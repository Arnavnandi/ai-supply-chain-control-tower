package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "csv_import_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CsvImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(name = "records_imported", nullable = false)
    @Builder.Default
    private Integer recordsImported = 0;

    @Column(name = "records_failed", nullable = false)
    @Builder.Default
    private Integer recordsFailed = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @CreatedDate
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum ImportStatus {
        PENDING,
        IN_PROGRESS,
        IMPORTED,
        FAILED
    }
}
