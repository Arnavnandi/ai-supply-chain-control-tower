package com.supplychain.controltower.service;

import com.supplychain.controltower.analytics.InventoryOptimizationEngine;
import com.supplychain.controltower.analytics.LogisticsAnalyticsEngine;
import com.supplychain.controltower.analytics.RiskAnalysisEngine;
import com.supplychain.controltower.analytics.SupplierAnalyticsEngine;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutiveReportService {

    private final RiskAnalysisEngine riskAnalysisEngine;
    private final InventoryOptimizationEngine inventoryOptimizationEngine;
    private final SupplierAnalyticsEngine supplierAnalyticsEngine;
    private final LogisticsAnalyticsEngine logisticsAnalyticsEngine;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final RecommendationRepository recommendationRepository;

    @Data
    @Builder
    public static class ExecutiveControlReport {
        private String reportTitle;
        private LocalDateTime generatedAt;
        private Integer systemRiskScore;
        private String systemRiskStatus;
        private Integer totalCatalogProducts;
        private Integer totalCustomerOrders;
        private Integer totalInventoryRecords;
        private BigDecimal totalExcessCapitalPotential;
        private BigDecimal averageSupplierOtifPct;
        private Integer activeDelayedShipments;
        private Integer pendingActionRecommendationsCount;
        private Integer executedActionsCount;
        private Integer rejectedActionsCount;
        private String executiveVerdict;
    }

    public ExecutiveControlReport generateExecutiveReport() {
        log.info("[EXECUTIVE REPORT SERVICE] Compiling executive control tower briefing from PostgreSQL data...");

        RiskAnalysisEngine.ControlTowerRiskReport riskReport = riskAnalysisEngine.evaluateSystemRisks();
        InventoryOptimizationEngine.SafetyStockOptimizationReport optSummary = inventoryOptimizationEngine.optimizeSafetyStockLevels();
        SupplierAnalyticsEngine.SupplierAnalyticsSummary supplierSummary = supplierAnalyticsEngine.analyzeSupplierPerformance();
        LogisticsAnalyticsEngine.LogisticsAnalyticsSummary logisticsSummary = logisticsAnalyticsEngine.analyzeLogisticsPerformance();

        long pendingCount = recommendationRepository.countByStatus(Recommendation.ApprovalStatus.PENDING_APPROVAL);
        long executedCount = recommendationRepository.countByStatus(Recommendation.ApprovalStatus.APPROVED);
        long rejectedCount = recommendationRepository.countByStatus(Recommendation.ApprovalStatus.REJECTED);

        String verdict = "System operates with verified data-driven statistical control. " +
                riskReport.getRiskItems().size() + " active operational risk(s) identified across inventory, suppliers, and shipments. " +
                pendingCount + " replenishment recommendation(s) awaiting manager HITL sign-off.";

        return ExecutiveControlReport.builder()
                .reportTitle("AI Supply Chain Control Tower — Executive Audit Briefing")
                .generatedAt(LocalDateTime.now())
                .systemRiskScore((int) Math.round(riskReport.getOverallRiskScore()))
                .systemRiskStatus(riskReport.getRiskLevel())
                .totalCatalogProducts((int) productRepository.count())
                .totalCustomerOrders((int) customerOrderRepository.count())
                .totalInventoryRecords(optSummary.getTotalItemsEvaluated())
                .totalExcessCapitalPotential(optSummary.getTotalCapitalOptimizationPotential())
                .averageSupplierOtifPct(supplierSummary.getAverageSystemOtifPct())
                .activeDelayedShipments(logisticsSummary.getActiveDelayedShipments())
                .pendingActionRecommendationsCount((int) pendingCount)
                .executedActionsCount((int) executedCount)
                .rejectedActionsCount((int) rejectedCount)
                .executiveVerdict(verdict)
                .build();
    }
}
