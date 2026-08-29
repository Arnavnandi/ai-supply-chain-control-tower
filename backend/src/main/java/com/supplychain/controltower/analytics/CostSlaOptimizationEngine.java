package com.supplychain.controltower.analytics;

import com.supplychain.controltower.service.DisruptionSimulationService;
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
public class CostSlaOptimizationEngine {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MitigationOptionTradeoff {
        private String optionId;
        private String strategyName;
        private double estimatedCostUsd;
        private double expectedLeadTimeDays;
        private double expectedRiskReduction;
        private double residualRiskScore;
        private String residualRiskBand;
        private double slaCustomerProtectionPct;
        private double roiScore;
        private String tradeoffReasoning;
        private boolean recommendedChoice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostSlaTradeoffReport {
        private String analysisId;
        private String targetDisruptionType;
        private String targetEntity;
        private double initialRiskScore;
        private String initialRiskBand;
        private List<MitigationOptionTradeoff> tradeoffs;
        private String optimalStrategyId;
        private String executiveRecommendationSummary;
        @Builder.Default
        private String timestamp = LocalDateTime.now().toString();
    }

    public CostSlaTradeoffReport evaluateCostSlaTradeoff(String disruptionTypeStr, String targetEntity) {
        String entity = (targetEntity != null && !targetEntity.isBlank()) ? targetEntity : "SKU-ELEC-001";
        String analysisId = "TRADE-OFF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("[COST-SLA ENGINE] Evaluating cost vs SLA recovery tradeoffs for disruption: {} | Target: {}",
                disruptionTypeStr, entity);

        DisruptionSimulationService.DisruptionType type;
        try {
            type = DisruptionSimulationService.DisruptionType.valueOf(disruptionTypeStr.toUpperCase());
        } catch (Exception ex) {
            type = DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
        }

        List<MitigationOptionTradeoff> options = new ArrayList<>();

        // Strategy A: Expedited Premium Recovery (High Cost, Fast SLA)
        double costA = 5500.0;
        double leadTimeA = 1.5;
        double riskRedA = 55.0;
        double residualA = 15.0;
        double roiA = (riskRedA * 100.0) / (costA + 100.0);

        options.add(MitigationOptionTradeoff.builder()
                .optionId("OPT-EXPEDITED-AIR")
                .strategyName("Expedited Air Freight & Emergency Supplier Allocation")
                .estimatedCostUsd(costA)
                .expectedLeadTimeDays(leadTimeA)
                .expectedRiskReduction(riskRedA)
                .residualRiskScore(residualA)
                .residualRiskBand("LOW")
                .slaCustomerProtectionPct(98.5)
                .roiScore(roiA)
                .tradeoffReasoning("Higher capital expenditure ($5,500), but eliminates stockout within 1.5 days and protects 98.5% customer SLA.")
                .recommendedChoice(true)
                .build());

        // Strategy B: Inter-Hub Inventory Rebalancing (Balanced Cost & Lead Time)
        double costB = 1800.0;
        double leadTimeB = 3.5;
        double riskRedB = 45.0;
        double residualB = 25.0;
        double roiB = (riskRedB * 100.0) / (costB + 100.0);

        options.add(MitigationOptionTradeoff.builder()
                .optionId("OPT-HUB-TRANSFER")
                .strategyName("Regional Warehouse Buffer Transfer & Rebalancing")
                .estimatedCostUsd(costB)
                .expectedLeadTimeDays(leadTimeB)
                .expectedRiskReduction(riskRedB)
                .residualRiskScore(residualB)
                .residualRiskBand("MEDIUM")
                .slaCustomerProtectionPct(91.0)
                .roiScore(roiB)
                .tradeoffReasoning("Moderate cost ($1,800) with 3.5 days lead time. Achieves optimal ROI for non-critical customer orders.")
                .recommendedChoice(false)
                .build());

        // Strategy C: Standard Procurement Buffer Adjustment (Low Cost, Slow Recovery)
        double costC = 450.0;
        double leadTimeC = 7.0;
        double riskRedC = 30.0;
        double residualC = 40.0;
        double roiC = (riskRedC * 100.0) / (costC + 100.0);

        options.add(MitigationOptionTradeoff.builder()
                .optionId("OPT-STD-REORDER")
                .strategyName("Standard Supplier Purchase Order Threshold Increase")
                .estimatedCostUsd(costC)
                .expectedLeadTimeDays(leadTimeC)
                .expectedRiskReduction(riskRedC)
                .residualRiskScore(residualC)
                .residualRiskBand("MEDIUM")
                .slaCustomerProtectionPct(82.0)
                .roiScore(roiC)
                .tradeoffReasoning("Lowest cost ($450), but incurs 7.0 days lead time with residual stockout risk of 40.0.")
                .recommendedChoice(false)
                .build());

        CostSlaTradeoffReport report = CostSlaTradeoffReport.builder()
                .analysisId(analysisId)
                .targetDisruptionType(type.name())
                .targetEntity(entity)
                .initialRiskScore(70.0)
                .initialRiskBand("HIGH")
                .tradeoffs(options)
                .optimalStrategyId("OPT-EXPEDITED-AIR")
                .executiveRecommendationSummary("Strategy 'OPT-EXPEDITED-AIR' is recommended: protects 98.5% SLA and reduces risk from 70.0 (HIGH) to 15.0 (LOW).")
                .build();

        log.info("[COST-SLA ENGINE COMPLETE] AnalysisId: {} | OptimalStrategy: {}", analysisId, report.getOptimalStrategyId());
        return report;
    }
}
