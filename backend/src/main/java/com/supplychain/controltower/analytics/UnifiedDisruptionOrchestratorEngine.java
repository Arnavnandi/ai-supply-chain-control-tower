package com.supplychain.controltower.analytics;

import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnifiedDisruptionOrchestratorEngine {

    private final AutoContainmentFailoverEngine failoverEngine;
    private final MultiEchelonInventoryRebalancingEngine rebalanceEngine;
    private final CostSlaOptimizationEngine costSlaEngine;
    private final PredictiveDisruptionEarlyWarningEngine earlyWarningEngine;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MasterOrchestrationReport {
        private String orchestrationPlanId;
        private String primaryTargetEntity;
        private String disruptionType;
        private String orchestrationStatus; // READY_FOR_MANAGER_APPROVAL
        private AutoContainmentFailoverEngine.ContainmentFailoverReport failoverContainment;
        private MultiEchelonInventoryRebalancingEngine.RebalancingReport multiEchelonRebalance;
        private CostSlaOptimizationEngine.CostSlaTradeoffReport costSlaOptimization;
        private PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport predictiveEarlyWarningScan;
        private double overallSystemicRiskScore;
        private double estimatedRecoveryTimeDays;
        private double totalEstimatedCost;
        private String masterExecutiveSummary;
        @Builder.Default
        private String timestamp = LocalDateTime.now().toString();
    }

    public MasterOrchestrationReport generateMasterOrchestrationPlan(String targetEntityInput, String warehouseInput) {
        Long companyId = TenantContext.getRequiredCompanyId();
        var suppliers = supplierRepository.findByCompanyId(companyId);
        var warehouses = warehouseRepository.findByCompanyId(companyId);
        var products = productRepository.findByCompanyId(companyId);

        String targetEntity = (targetEntityInput != null && !targetEntityInput.isBlank())
                ? targetEntityInput.trim()
                : (!suppliers.isEmpty() && suppliers.get(0).getCode() != null ? suppliers.get(0).getCode() : null);

        String warehouse = (warehouseInput != null && !warehouseInput.isBlank())
                ? warehouseInput.trim()
                : (!warehouses.isEmpty() && warehouses.get(0).getCode() != null ? warehouses.get(0).getCode() : null);

        String skuToRebalance = !products.isEmpty() && products.get(0).getSku() != null ? products.get(0).getSku() : null;

        String planId = "MASTER-ORCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[UNIFIED ORCHESTRATOR ENGINE] Synthesizing Master Disruption Containment & Recovery Blueprint for Target: {} | Warehouse: {}", targetEntity, warehouse);

        if (targetEntity == null || warehouse == null || skuToRebalance == null) {
            log.info("[UNIFIED ORCHESTRATOR ENGINE] Insufficient operational records (suppliers, warehouses, or products missing for companyId={})", companyId);
            return MasterOrchestrationReport.builder()
                    .orchestrationPlanId(planId)
                    .primaryTargetEntity(targetEntity != null ? targetEntity : "N/A")
                    .disruptionType("SUPPLIER_DISRUPTION")
                    .orchestrationStatus("INSUFFICIENT_DATA")
                    .failoverContainment(AutoContainmentFailoverEngine.ContainmentFailoverReport.builder()
                            .containmentPlanId("FAILOVER-EMPTY")
                            .containmentStatus("INSUFFICIENT_DATA")
                            .strategyExplanation("No operational suppliers or facilities found for this company.")
                            .allocations(Collections.emptyList())
                            .build())
                    .multiEchelonRebalance(MultiEchelonInventoryRebalancingEngine.RebalancingReport.builder()
                            .rebalancePlanId("REBAL-EMPTY")
                            .rebalancingStatus("INSUFFICIENT_DATA")
                            .executiveSummary("No operational warehouses or SKUs found for this company.")
                            .transferOptions(Collections.emptyList())
                            .build())
                    .costSlaOptimization(CostSlaOptimizationEngine.CostSlaTradeoffReport.builder()
                            .analysisId("COST-SLA-EMPTY")
                            .tradeoffs(Collections.emptyList())
                            .executiveRecommendationSummary("No operational entities available to compute Cost vs. SLA tradeoffs.")
                            .build())
                    .predictiveEarlyWarningScan(earlyWarningEngine.scanAndPredictEarlyWarnings(false))
                    .overallSystemicRiskScore(0.0)
                    .estimatedRecoveryTimeDays(0.0)
                    .totalEstimatedCost(0.0)
                    .masterExecutiveSummary("Unified Disruption Containment & Recovery Blueprint cannot be generated: Insufficient operational database records for suppliers, warehouses, or catalog products.")
                    .build();
        }

        // Fetch component reports safely
        AutoContainmentFailoverEngine.ContainmentFailoverReport failoverReport =
                failoverEngine.computeFailoverContainmentPlan(targetEntity, warehouse);

        MultiEchelonInventoryRebalancingEngine.RebalancingReport rebalanceReport =
                rebalanceEngine.computeMultiEchelonRebalancePlan(warehouse, skuToRebalance);

        CostSlaOptimizationEngine.CostSlaTradeoffReport costSlaReport =
                costSlaEngine.evaluateCostSlaTradeoff("SUPPLIER_DISRUPTION", targetEntity);

        PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport earlyWarningReport =
                earlyWarningEngine.scanAndPredictEarlyWarnings(false);

        boolean insufficientData = (failoverReport != null && "INSUFFICIENT_DATA".equals(failoverReport.getContainmentStatus()))
                || (rebalanceReport != null && "INSUFFICIENT_DATA".equals(rebalanceReport.getRebalancingStatus()));

        double systemicRisk = earlyWarningReport != null && earlyWarningReport.getHighestFailureProbability() > 0
                ? Math.round(earlyWarningReport.getHighestFailureProbability() * 100.0 * 10.0) / 10.0
                : (insufficientData ? 0.0 : 45.0);

        CostSlaOptimizationEngine.MitigationOptionTradeoff bestOption = costSlaReport != null && costSlaReport.getTradeoffs() != null
                ? costSlaReport.getTradeoffs().stream().filter(CostSlaOptimizationEngine.MitigationOptionTradeoff::isRecommendedChoice).findFirst()
                    .orElse(costSlaReport.getTradeoffs().isEmpty() ? null : costSlaReport.getTradeoffs().get(0))
                : null;

        double recoveryDays = bestOption != null
                ? Math.round(bestOption.getExpectedLeadTimeDays() * 10.0) / 10.0
                : (insufficientData ? 0.0 : 2.5);

        double totalCost = (bestOption != null ? bestOption.getEstimatedCostUsd() : 0.0)
                + (rebalanceReport != null && rebalanceReport.getTransferOptions() != null
                    ? rebalanceReport.getTransferOptions().stream().mapToDouble(MultiEchelonInventoryRebalancingEngine.InterHubTransferOption::getTransferCostEstimate).sum()
                    : 0.0);
        totalCost = Math.round(totalCost * 10.0) / 10.0;

        String status = insufficientData ? "INSUFFICIENT_DATA" : "READY_FOR_MANAGER_APPROVAL";
        String summary = insufficientData
                ? String.format("Unified Disruption Plan (%s) for %s cannot be completed: Insufficient operational database records for failover suppliers or warehouses.", planId, targetEntity)
                : String.format("Unified Disruption Containment & Recovery Plan (%s) Active for %s: Multi-supplier failover routing combined with inter-hub rebalancing reduces recovery lead time to %.1f days at estimated cost of $%.2f.",
                        planId, targetEntity, recoveryDays, totalCost);

        MasterOrchestrationReport report = MasterOrchestrationReport.builder()
                .orchestrationPlanId(planId)
                .primaryTargetEntity(targetEntity)
                .disruptionType("SUPPLIER_DISRUPTION")
                .orchestrationStatus(status)
                .failoverContainment(failoverReport)
                .multiEchelonRebalance(rebalanceReport)
                .costSlaOptimization(costSlaReport)
                .predictiveEarlyWarningScan(earlyWarningReport)
                .overallSystemicRiskScore(systemicRisk)
                .estimatedRecoveryTimeDays(recoveryDays)
                .totalEstimatedCost(totalCost)
                .masterExecutiveSummary(summary)
                .build();

        log.info("[UNIFIED ORCHESTRATOR COMPLETE] PlanId: {} | SystemicRisk: %.1f | EstRecoveryTime: %.1f days", planId, systemicRisk, recoveryDays);
        return report;
    }
}
