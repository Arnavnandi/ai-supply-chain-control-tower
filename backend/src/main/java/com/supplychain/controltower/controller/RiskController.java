package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.RiskAlert;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.repository.RiskAlertRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/risks")
@RequiredArgsConstructor
@Slf4j
public class RiskController {

    private final RiskAlertRepository riskAlertRepository;

    @GetMapping
    public ResponseEntity<List<RiskAlert>> getAllRisks(@RequestParam(required = false) RiskAlert.RiskStatus status) {
        Long companyId = TenantContext.getRequiredCompanyId();
        log.info("[RISK CONTROLLER] Fetching risk alerts for companyId: {}, status: {}", companyId, status);
        if (status != null) {
            return ResponseEntity.ok(riskAlertRepository.findByCompanyIdAndStatus(companyId, status));
        }
        return ResponseEntity.ok(riskAlertRepository.findByCompanyIdOrderByCreatedAtDesc(companyId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<RiskAlert>> getActiveRisks() {
        Long companyId = TenantContext.getRequiredCompanyId();
        log.info("[RISK CONTROLLER] Fetching active risk alerts for companyId: {}", companyId);
        return ResponseEntity.ok(riskAlertRepository.findByCompanyIdAndStatus(companyId, RiskAlert.RiskStatus.ACTIVE));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RiskAlert> getRiskById(@PathVariable Long id) {
        Long companyId = TenantContext.getRequiredCompanyId();
        log.info("[RISK CONTROLLER] Fetching risk alert id: {} for companyId: {}", id, companyId);
        RiskAlert alert = riskAlertRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk alert not found with id: " + id));
        return ResponseEntity.ok(alert);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getRiskCount(@RequestParam(required = false) RiskAlert.RiskStatus status) {
        Long companyId = TenantContext.getRequiredCompanyId();
        long count = (status != null)
                ? riskAlertRepository.countByCompanyIdAndStatus(companyId, status)
                : riskAlertRepository.countByCompanyId(companyId);
        return ResponseEntity.ok(Map.of("count", count));
    }
}
