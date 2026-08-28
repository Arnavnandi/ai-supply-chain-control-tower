package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "severity", nullable = false, length = 50)
    private String severity;

    @Column(name = "source_domain", length = 100)
    private String sourceDomain;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
