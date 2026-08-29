package com.supplychain.controltower.controller;

import com.supplychain.controltower.analytics.AutoContainmentFailoverEngine;
import com.supplychain.controltower.analytics.CascadingDisruptionCorrelationEngine;
import com.supplychain.controltower.analytics.CostSlaOptimizationEngine;
import com.supplychain.controltower.analytics.ExecutiveCommandCenterEngine;
import com.supplychain.controltower.analytics.HistoricalMitigationEfficacyEngine;
import com.supplychain.controltower.analytics.MultiEchelonInventoryRebalancingEngine;
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
    private final CostSlaOptimizationEngine costSlaEngine;
    private final HistoricalMitigationEfficacyEngine efficacyEngine;
    private final ExecutiveCommandCenterEngine commandCenterEngine;
    private final AutoContainmentFailoverEngine failoverEngine;
    private final MultiEchelonInventoryRebalancingEngine rebalanceEngine;

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

    @GetMapping("/analytics/cost-sla-tradeoff")
    public ResponseEntity<CostSlaOptimizationEngine.CostSlaTradeoffReport> getCostSlaTradeoff(
            @RequestParam(name = "type", required = false, defaultValue = "INVENTORY_SHORTAGE") String type,
            @RequestParam(name = "targetEntity", required = false, defaultValue = "SKU-ELEC-001") String targetEntity) {

        log.info("[SIMULATION CONTROLLER] Evaluating cost-SLA tradeoff: type={} target={}", type, targetEntity);
        CostSlaOptimizationEngine.CostSlaTradeoffReport report =
                costSlaEngine.evaluateCostSlaTradeoff(type, targetEntity);

        return ResponseEntity.ok(report);
    }

    @GetMapping("/analytics/historical-efficacy")
    public ResponseEntity<HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport> getHistoricalEfficacy() {
        log.info("[SIMULATION CONTROLLER] Retrieving historical mitigation efficacy analytics report...");
        HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport report =
                efficacyEngine.calculateHistoricalEfficacy();

        return ResponseEntity.ok(report);
    }

    @GetMapping("/executive/command-center")
    public ResponseEntity<ExecutiveCommandCenterEngine.ExecutiveScorecardReport> getExecutiveCommandCenterReport() {
        log.info("[SIMULATION CONTROLLER] Generating Executive Command Center Resiliency Scorecard report...");
        ExecutiveCommandCenterEngine.ExecutiveScorecardReport report =
                commandCenterEngine.generateExecutiveCommandCenterReport();

        return ResponseEntity.ok(report);
    }

    @GetMapping("/analytics/failover-containment")
    public ResponseEntity<AutoContainmentFailoverEngine.ContainmentFailoverReport> getFailoverContainment(
            @RequestParam(name = "failedSupplierCode", required = false, defaultValue = "SUP-TECH-001") String failedSupplierCode,
            @RequestParam(name = "primaryWarehouseCode", required = false, defaultValue = "WH-NORTH") String primaryWarehouseCode) {

        log.info("[SIMULATION CONTROLLER] Computing failover containment: failedSupplier={} primaryWarehouse={}",
                failedSupplierCode, primaryWarehouseCode);
        AutoContainmentFailoverEngine.ContainmentFailoverReport report =
                failoverEngine.computeFailoverContainmentPlan(failedSupplierCode, primaryWarehouseCode);

        return ResponseEntity.ok(report);
    }

    @GetMapping("/analytics/multi-echelon-rebalance")
    public ResponseEntity<MultiEchelonInventoryRebalancingEngine.RebalancingReport> getMultiEchelonRebalance(
            @RequestParam(name = "targetWarehouseCode", required = false, defaultValue = "WH-NORTH") String targetWarehouseCode,
            @RequestParam(name = "skuCode", required = false, defaultValue = "SKU-ELEC-001") String skuCode) {

        log.info("[SIMULATION CONTROLLER] Computing multi-echelon rebalancing: targetWarehouse={} sku={}",
                targetWarehouseCode, skuCode);
        MultiEchelonInventoryRebalancingEngine.RebalancingReport report =
                rebalanceEngine.computeMultiEchelonRebalancePlan(targetWarehouseCode, skuCode);

        return ResponseEntity.ok(report);
    }
}
