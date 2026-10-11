package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.TelemetryEventEntity;
import com.supplychain.controltower.repository.TelemetryEventRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
@Slf4j
public class TelemetryController {

    private final TelemetryEventRepository telemetryEventRepository;

    @GetMapping("/events")
    public ResponseEntity<List<TelemetryEventEntity>> getRecentEvents(
            @RequestParam(defaultValue = "50") int limit) {
        Long companyId = TenantContext.getRequiredCompanyId();
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        log.info("[TELEMETRY CONTROLLER] Fetching recent telemetry events for companyId={} (limit={})", companyId, boundedLimit);
        List<TelemetryEventEntity> events = telemetryEventRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, boundedLimit));
        return ResponseEntity.ok(events);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<TelemetryEventEntity>> getActiveAlerts(
            @RequestParam(defaultValue = "50") int limit) {
        Long companyId = TenantContext.getRequiredCompanyId();
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        log.info("[TELEMETRY CONTROLLER] Fetching active telemetry alerts for companyId={} (limit={})", companyId, boundedLimit);
        List<TelemetryEventEntity> alerts = telemetryEventRepository.findActiveAlerts(companyId, PageRequest.of(0, boundedLimit));
        return ResponseEntity.ok(alerts);
    }
}

