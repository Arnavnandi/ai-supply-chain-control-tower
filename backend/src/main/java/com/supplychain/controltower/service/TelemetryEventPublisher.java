package com.supplychain.controltower.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.websocket.TelemetryWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryEventPublisher {

    private final TelemetryWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public void publish(TelemetryEvent event) {
        if (event == null) return;

        log.info("[TELEMETRY EVENT PUBLISHED] Type: {} | Severity: {} | Source: {} | Message: {}",
                event.getEventType(), event.getSeverity(), event.getSourceDomain(), event.getMessage());

        try {
            String json = objectMapper.writeValueAsString(event);
            webSocketHandler.broadcastMessage(json);
        } catch (Exception ex) {
            log.warn("[TELEMETRY SERIALIZATION FAIL] Failed to serialize telemetry event: {}", ex.getMessage());
        }
    }

    public int getConnectedClientCount() {
        return webSocketHandler.getActiveSessionCount();
    }
}
