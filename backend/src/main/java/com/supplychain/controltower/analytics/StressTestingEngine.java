package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.InventoryRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StressTestingEngine {

    private final InventoryRepository inventoryRepository;
    private final RiskAnalysisEngine riskAnalysisEngine;

    @Data
    @Builder
    public static class StressTestRequest {
        private Double demandSurgePercentage; // e.g. 30.0 = +30% demand surge
        private Integer supplierLeadTimeDelayDays; // e.g. 5 = +5 days delay across suppliers
        private Double freightDelayPercentage; // e.g. 25.0 = +25% transit delay
    }

    @Data
    @Builder
    public static class StressTestSimulationResult {
        private Double baselineRiskScore;
        private Double simulatedRiskScore;
        private String simulatedRiskLevel; // CRITICAL, HIGH, MODERATE, LOW
        private Integer baselineStockoutCount;
        private Integer simulatedStockoutCount;
        private BigDecimal projectedFinancialRiskExposure;
        private List<SimulatedStockoutItem> projectedStockouts;
        private String executiveSummary;
    }

    @Data
    @Builder
    public static class SimulatedStockoutItem {
        private String productSku;
        private String productName;
        private String warehouseName;
        private Integer currentStock;
        private Integer simulatedDemand30Day;
        private Integer projectedDeficitUnits;
        private String timeToStockoutDays;
    }

    public StressTestSimulationResult runWhatIfSimulation(StressTestRequest request) {
        double demandMultiplier = 1.0 + (request.getDemandSurgePercentage() != null ? request.getDemandSurgePercentage() / 100.0 : 0.0);
        int leadTimeDelay = request.getSupplierLeadTimeDelayDays() != null ? request.getSupplierLeadTimeDelayDays() : 0;

        log.info("[STRESS TESTING ENGINE] Running What-If Simulation: DemandSurge=+{}% LeadTimeDelay=+{} days",
                request.getDemandSurgePercentage(), leadTimeDelay);

        // Fetch baseline risk score
        RiskAnalysisEngine.ControlTowerRiskReport baselineReport = riskAnalysisEngine.evaluateSystemRisks();
        double baselineScore = baselineReport.getOverallRiskScore();

        List<Inventory> inventories = inventoryRepository.findAll();
        List<SimulatedStockoutItem> simulatedStockouts = new ArrayList<>();

        int simulatedStockoutCount = 0;
        BigDecimal totalFinancialExposure = BigDecimal.ZERO;

        double simulatedRiskScore = baselineScore + (request.getDemandSurgePercentage() != null ? request.getDemandSurgePercentage() * 0.4 : 0.0)
                + (leadTimeDelay * 2.5);
        simulatedRiskScore = Math.min(100.0, Math.round(simulatedRiskScore * 10.0) / 10.0);

        for (Inventory inv : inventories) {
            Product product = inv.getProduct();
            if (product == null) continue;

            int stock = inv.getQuantityAvailable() != null ? inv.getQuantityAvailable() : 0;
            int normal30DayDemand = (int) Math.round(product.getReorderLevel() != null ? product.getReorderLevel() * 1.2 : 120);

            int simulated30DayDemand = (int) Math.round(normal30DayDemand * demandMultiplier);

            int effectiveSafetyStock = (int) Math.round((product.getSafetyStock() != null ? product.getSafetyStock() : 50) * (1.0 + (leadTimeDelay / 10.0)));

            if (stock < (simulated30DayDemand / 2) || stock < effectiveSafetyStock) {
                simulatedStockoutCount++;

                int deficit = Math.max(50, simulated30DayDemand - stock);
                double dailyBurn = simulated30DayDemand / 30.0;
                int daysToStockout = dailyBurn > 0 ? (int) Math.max(1, Math.floor(stock / dailyBurn)) : 0;

                BigDecimal productPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.valueOf(50.0);
                BigDecimal deficitFinancialExposure = productPrice.multiply(BigDecimal.valueOf(deficit));
                totalFinancialExposure = totalFinancialExposure.add(deficitFinancialExposure);

                simulatedStockouts.add(SimulatedStockoutItem.builder()
                        .productSku(product.getSku())
                        .productName(product.getName())
                        .warehouseName(inv.getWarehouse() != null ? inv.getWarehouse().getName() : "Central Warehouse")
                        .currentStock(stock)
                        .simulatedDemand30Day(simulated30DayDemand)
                        .projectedDeficitUnits(deficit)
                        .timeToStockoutDays(daysToStockout + " Days")
                        .build());
            }
        }

        String simulatedLevel = simulatedRiskScore >= 80.0 ? "CRITICAL" : (simulatedRiskScore >= 50.0 ? "HIGH" : "MODERATE");

        String summary = String.format(
                "Stress-Test Simulation Result: Under a +%.1f%% demand surge and +%d days supplier lead-time delay, " +
                "system risk score escalates from %.1f to %.1f (%s RISK). " +
                "Projected stockout incidents increase from %d to %d items with a total financial risk exposure of $%s.",
                request.getDemandSurgePercentage() != null ? request.getDemandSurgePercentage() : 0.0,
                leadTimeDelay,
                baselineScore,
                simulatedRiskScore,
                simulatedLevel,
                baselineReport.getCriticalRisksCount(),
                simulatedStockoutCount,
                totalFinancialExposure.setScale(2, RoundingMode.HALF_UP).toString()
        );

        return StressTestSimulationResult.builder()
                .baselineRiskScore(baselineScore)
                .simulatedRiskScore(simulatedRiskScore)
                .simulatedRiskLevel(simulatedLevel)
                .baselineStockoutCount(baselineReport.getCriticalRisksCount())
                .simulatedStockoutCount(simulatedStockoutCount)
                .projectedFinancialRiskExposure(totalFinancialExposure.setScale(2, RoundingMode.HALF_UP))
                .projectedStockouts(simulatedStockouts)
                .executiveSummary(summary)
                .build();
    }
}
