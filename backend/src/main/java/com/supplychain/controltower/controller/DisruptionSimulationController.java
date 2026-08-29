package com.supplychain.controltower.controller;

import com.supplychain.controltower.analytics.CascadingDisruptionCorrelationEngine;
import com.supplychain.controltower.analytics.PredictiveDisruptionEarlyWarningEngine;
import com.supplychain.controltower.service.DisruptionSimulationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/simulation")
@RequiredArgsConstructor
@Slf4j
public class DisruptionSimulationController {

    private final DisruptionSimulationService simulationService;
    private final CascadingDisruptionCorrelationEngine cascadeEngine;
    private final PredictiveDisruptionEarlyWarningEngine earlyWarningEngine;

    @Data
    public static class SimulationRequest {
        private String type;
        private String targetEntity;
    }

    @Data
    public static class CascadeSimulationRequest {
        private String primaryDisruption;
        private String type;
        private String primaryTarget;
        private String targetEntity;
        private Boolean convertToActionProposal;
        private Boolean convertToProposal;
    }

    @PostMapping("/disruption")
    public ResponseEntity<DisruptionSimulationService.DisruptionSimulationResult> runSimulation(
            @RequestBody(required = false) SimulationRequest request) {

        String typeStr = request != null && request.getType() != null ? request.getType() : "INVENTORY_SHORTAGE";
        String targetEntity = request != null ? request.getTargetEntity() : null;

        DisruptionSimulationService.DisruptionType disruptionType;
        try {
            disruptionType = DisruptionSimulationService.DisruptionType.valueOf(typeStr.toUpperCase());
        } catch (Exception ex) {
            disruptionType = DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
        }

        log.info("[SIMULATION CONTROLLER] Running disruption simulation type: {}", disruptionType);
        DisruptionSimulationService.DisruptionSimulationResult result =
                simulationService.simulateDisruption(disruptionType, targetEntity);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/disruption/cascade")
    public ResponseEntity<CascadingDisruptionCorrelationEngine.CascadingDisruptionResult> runCascadeSimulation(
            @RequestBody(required = false) CascadeSimulationRequest request,
            @RequestParam(name = "convertToActionProposal", required = false, defaultValue = "false") boolean convertQueryParam) {

        String primaryType = (request != null && request.getPrimaryDisruption() != null)
                ? request.getPrimaryDisruption()
                : (request != null && request.getType() != null) ? request.getType() : "INVENTORY_SHORTAGE";

        String targetEntity = (request != null && request.getPrimaryTarget() != null)
                ? request.getPrimaryTarget()
                : (request != null && request.getTargetEntity() != null) ? request.getTargetEntity() : "SKU-ELEC-001";

        boolean convertToProposal = convertQueryParam
                || (request != null && Boolean.TRUE.equals(request.getConvertToActionProposal()))
                || (request != null && Boolean.TRUE.equals(request.getConvertToProposal()));

        log.info("[SIMULATION CONTROLLER] Running cascading disruption correlation analysis: type={} target={} convertToProposal={}",
                primaryType, targetEntity, convertToProposal);

        CascadingDisruptionCorrelationEngine.CascadingDisruptionResult result =
                cascadeEngine.analyzeCascadingDisruption(primaryType, targetEntity, convertToProposal);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/predictive/early-warnings")
    public ResponseEntity<PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport> getPredictiveEarlyWarnings(
            @RequestParam(name = "convertToActionProposal", required = false, defaultValue = "false") boolean convertToProposal) {

        log.info("[SIMULATION CONTROLLER] Executing predictive early-warning radar scan: convertToProposal={}", convertToProposal);
        PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport report =
                earlyWarningEngine.scanAndPredictEarlyWarnings(convertToProposal);

        return ResponseEntity.ok(report);
    }
}
