package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.service.ForecastService;
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
public class InventoryOptimizationEngine {

    private final InventoryRepository inventoryRepository;
    private final ForecastService forecastService;

    @Data
    @Builder
    public static class SafetyStockOptimizationReport {
        private int totalItemsEvaluated;
        private int itemsWithDeficitCount;
        private int itemsWithExcessCount;
        private BigDecimal totalCapitalOptimizationPotential;
        private List<OptimizedInventoryItem> optimizedItems;
    }

    @Data
    @Builder
    public static class OptimizedInventoryItem {
        private Long inventoryId;
        private String productSku;
        private String productName;
        private String warehouseName;
        private Integer currentStock;
        private Integer currentSafetyStock;
        private Integer calculatedDynamicSafetyStock; // Formula SS = Z * sigma_d * sqrt(L)
        private Integer optimalReorderPoint;
        private String optimizationStatus; // DEFICIT_RISK, OPTIMAL, EXCESS_CAPITAL
        private Integer recommendedAdjustmentUnits;
    }

    public SafetyStockOptimizationReport optimizeSafetyStockLevels() {
        log.info("[OPTIMIZATION ENGINE] Executing dynamic safety stock computation across inventory...");

        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        List<Inventory> inventories = inventoryRepository.findByCompanyId(companyId);
        List<OptimizedInventoryItem> items = new ArrayList<>();

        int deficitCount = 0;
        int excessCount = 0;
        double zFactor = 1.65; // 95% Service Level Factor
        BigDecimal totalCapitalPotential = BigDecimal.ZERO;

        for (Inventory inv : inventories) {
            Product product = inv.getProduct();
            if (product == null) continue;

            List<Integer> salesHistory = forecastService.calculateMonthlySalesFromDatabase(companyId, product.getId());

            double stdDevDemand = 0.0;
            if (salesHistory != null && salesHistory.size() > 1) {
                double mean = salesHistory.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                double variance = salesHistory.stream().mapToDouble(q -> Math.pow(q - mean, 2)).sum() / (salesHistory.size() - 1);
                stdDevDemand = Math.sqrt(variance);
            }

            int leadTimeDays = product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 7;
            double leadTimeMonths = Math.max(0.2, leadTimeDays / 30.0);

            int currentSS = inv.getSafetyStock() != null ? inv.getSafetyStock() : (product.getSafetyStock() != null ? product.getSafetyStock() : 0);

            // Dynamic Safety Stock Formula: SS = Z * sigma_d * sqrt(L). If insufficient sales history (stdDev == 0), retain configured safety stock.
            int dynamicSafetyStock = stdDevDemand > 0
                    ? (int) Math.max(1, Math.round(zFactor * stdDevDemand * Math.sqrt(leadTimeMonths)))
                    : currentSS;
            int optimalReorderPoint = stdDevDemand > 0
                    ? (int) Math.round(dynamicSafetyStock + (stdDevDemand * leadTimeMonths * 1.5))
                    : (dynamicSafetyStock > 0 ? (int) Math.round(dynamicSafetyStock * 1.5) : 0);

            int currentStock = inv.getQuantityAvailable() != null ? inv.getQuantityAvailable() : 0;

            String status = "OPTIMAL";
            int adjustment = 0;

            if (dynamicSafetyStock > 0 && currentStock < dynamicSafetyStock) {
                status = "DEFICIT_RISK";
                deficitCount++;
                adjustment = dynamicSafetyStock - currentStock;
            } else if (optimalReorderPoint > 0 && currentStock > (optimalReorderPoint * 2.5)) {
                status = "EXCESS_CAPITAL";
                excessCount++;
                adjustment = (int) (currentStock - (optimalReorderPoint * 2.0));

                BigDecimal itemPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal excessValuation = itemPrice.multiply(BigDecimal.valueOf(adjustment));
                totalCapitalPotential = totalCapitalPotential.add(excessValuation);
            }

            items.add(OptimizedInventoryItem.builder()
                    .inventoryId(inv.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .warehouseName(inv.getWarehouse() != null ? inv.getWarehouse().getName() : "Unassigned Warehouse")
                    .currentStock(currentStock)
                    .currentSafetyStock(currentSS)
                    .calculatedDynamicSafetyStock(dynamicSafetyStock)
                    .optimalReorderPoint(optimalReorderPoint)
                    .optimizationStatus(status)
                    .recommendedAdjustmentUnits(adjustment)
                    .build());
        }

        return SafetyStockOptimizationReport.builder()
                .totalItemsEvaluated(inventories.size())
                .itemsWithDeficitCount(deficitCount)
                .itemsWithExcessCount(excessCount)
                .totalCapitalOptimizationPotential(totalCapitalPotential.setScale(2, RoundingMode.HALF_UP))
                .optimizedItems(items)
                .build();
    }
}
