package com.supplychain.controltower.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.TelemetryEventEntity;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.TelemetryEventRepository;
import com.supplychain.controltower.security.TenantContext;
import com.supplychain.controltower.websocket.TelemetryWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryEventPublisher {

    private final TelemetryWebSocketHandler webSocketHandler;
    private final TelemetryEventRepository telemetryEventRepository;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    public void publish(TelemetryEvent event) {
        if (event == null) return;

        if (event.getCorrelationId() == null || event.getCorrelationId().isBlank()) {
            String mdcCorrelationId = MDC.get("correlationId");
            if (mdcCorrelationId != null && !mdcCorrelationId.isBlank()) {
                event.setCorrelationId(mdcCorrelationId);
            }
        }

        log.info("[TELEMETRY EVENT PUBLISHED] CorrelationId: {} | Type: {} | Severity: {} | Source: {} | Message: {}",
                event.getCorrelationId(), event.getEventType(), event.getSeverity(), event.getSourceDomain(), event.getMessage());

        String json = null;
        try {
            json = objectMapper.writeValueAsString(event);
            Long companyId = TenantContext.getCompanyId();
            webSocketHandler.broadcastMessageToCompany(companyId, json);
        } catch (Exception ex) {
            log.warn("[TELEMETRY SERIALIZATION FAIL] Failed to serialize telemetry event: {}", ex.getMessage());
        }

        persistEventSafely(event, json);
    }

    private void persistEventSafely(TelemetryEvent event, String metadataJson) {
        try {
            Long companyId = TenantContext.getCompanyId();
            if (companyId == null) {
                log.debug("[TELEMETRY PERSISTENCE SKIP] Skipping persistence for event '{}' - no tenant context", event.getEventId());
                return;
            }

            Company company = companyRepository.findById(companyId).orElse(null);
            if (company == null) {
                log.warn("[TELEMETRY PERSISTENCE SKIP] Skipping persistence for event '{}' - company ID {} not found", event.getEventId(), companyId);
                return;
            }

            TelemetryEventEntity entity = TelemetryEventEntity.builder()
                    .company(company)
                    .eventId(event.getEventId())
                    .correlationId(event.getCorrelationId())
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

