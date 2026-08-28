package com.supplychain.controltower.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryEvent {

    public enum EventType {
        REQUEST_STARTED,
        REQUEST_COMPLETED,
        REQUEST_FAILED,
        RAG_RETRIEVAL,
        AGENT_EXECUTION,
        STOCKOUT_ALERT,
        SHIPMENT_DELAY,
        SYSTEM_ERROR
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    private String correlationId;
    private EventType eventType;
    private Severity severity;
    private String sourceDomain;
    private String entityId;
    private String message;
    private Map<String, Object> metadata;

    @Builder.Default
    private String timestamp = LocalDateTime.now().toString();
}
