package com.supplychain.controltower.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.entity.TelemetryEventEntity;
import com.supplychain.controltower.repository.TelemetryEventRepository;
import com.supplychain.controltower.websocket.TelemetryWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryEventPublisher {

    private final TelemetryWebSocketHandler webSocketHandler;
    private final TelemetryEventRepository telemetryEventRepository;
    private final ObjectMapper objectMapper;

    public void publish(TelemetryEvent event) {
        if (event == null) return;

        log.info("[TELEMETRY EVENT PUBLISHED] Type: {} | Severity: {} | Source: {} | Message: {}",
                event.getEventType(), event.getSeverity(), event.getSourceDomain(), event.getMessage());

        String json = null;
        try {
            json = objectMapper.writeValueAsString(event);
            webSocketHandler.broadcastMessage(json);
        } catch (Exception ex) {
            log.warn("[TELEMETRY SERIALIZATION FAIL] Failed to serialize telemetry event: {}", ex.getMessage());
        }

        persistEventSafely(event, json);
    }

    private void persistEventSafely(TelemetryEvent event, String metadataJson) {
        try {
            TelemetryEventEntity entity = TelemetryEventEntity.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType() != null ? event.getEventType().name() : "UNKNOWN")
                    .severity(event.getSeverity() != null ? event.getSeverity().name() : "INFO")
                    .sourceDomain(event.getSourceDomain())
                    .entityId(event.getEntityId())
                    .message(event.getMessage())
                    .metadataJson(metadataJson != null ? metadataJson : "")
                    .createdAt(LocalDateTime.now())
                    .build();
            telemetryEventRepository.save(entity);
        } catch (Exception ex) {
            log.warn("[TELEMETRY PERSISTENCE FAIL] Failed to persist telemetry event to PostgreSQL: {}", ex.getMessage());
        }
    }

    public int getConnectedClientCount() {
        return webSocketHandler.getActiveSessionCount();
    }
}
