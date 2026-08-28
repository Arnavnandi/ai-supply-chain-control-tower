package com.supplychain.controltower.controller;

import com.supplychain.controltower.analytics.RiskAnalysisEngine;
import com.supplychain.controltower.service.SupplyChainIntelligenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
@Slf4j
public class IntelligenceController {

    private final SupplyChainIntelligenceService intelligenceService;
    private final RiskAnalysisEngine riskAnalysisEngine;

    @GetMapping("/summary")
    public ResponseEntity<SupplyChainIntelligenceService.IntelligenceSummaryDto> getControlTowerIntelligence() {
        log.info("[REST API] Request received for /api/intelligence/summary");
        return ResponseEntity.ok(intelligenceService.getControlTowerIntelligence());
    }

    @GetMapping("/risks")
    public ResponseEntity<RiskAnalysisEngine.ControlTowerRiskReport> getSystemRiskReport() {
        log.info("[REST API] Request received for /api/intelligence/risks");
        return ResponseEntity.ok(riskAnalysisEngine.evaluateSystemRisks());
    }
}
