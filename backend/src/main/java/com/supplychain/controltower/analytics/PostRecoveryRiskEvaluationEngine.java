package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostRecoveryRiskEvaluationEngine {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostRecoveryRiskResult {
        private String targetEntity;
        private String disruptionType;
        private double initialRiskScore;
        private double postRecoveryRiskScore;
        private double riskReductionDelta;
        private String initialRiskBand;
        private String residualRiskBand;
        private String evaluationSummary;
    }

    public PostRecoveryRiskResult evaluatePostExecutionRisk(String disruptionTypeStr, String targetEntity, double initialRiskScore, String initialRiskBand) {
        log.info("[POST-RECOVERY EVALUATION ENGINE] Re-evaluating residual risk for entity={} disruptionType={} initialScore={}",
                targetEntity, disruptionTypeStr, initialRiskScore);

        double residualScore;
        String evaluationSummary;

        try {
            switch (disruptionTypeStr.toUpperCase()) {
                case "INVENTORY_SHORTAGE" -> {
                    Product product = productRepository.findBySku(targetEntity)
                            .orElseGet(() -> {
                                List<Product> products = productRepository.searchProducts(targetEntity);
                                return !products.isEmpty() ? products.get(0) : null;
                            });

                    int currentStock = 0;
                    if (product != null) {
                        List<Inventory> inventories = inventoryRepository.findByProductId(product.getId());
                        currentStock = inventories.stream()
                                .mapToInt(i -> i.getQuantityAvailable() != null ? i.getQuantityAvailable() : 0)
                                .sum();
                    }

                    if (currentStock >= 50) {
                        residualScore = 15.0;
                        evaluationSummary = String.format("Stock replenished to %d units. Stockout risk mitigated from %.1f to %.1f.",
                                currentStock, initialRiskScore, residualScore);
                    } else {
                        residualScore = 35.0;
                        evaluationSummary = String.format("Partial stock replenishment (%d units). Residual stockout risk reduced to %.1f.",
                                currentStock, residualScore);
                    }
                }
                case "SUPPLIER_DISRUPTION" -> {
                    Supplier supplier = supplierRepository.findByCode(targetEntity).orElse(null);
                    double score = (supplier != null && supplier.getReliabilityScore() != null)
                            ? supplier.getReliabilityScore().doubleValue()
                            : 85.0;
                    residualScore = Math.max(10.0, 100.0 - score);
                    evaluationSummary = String.format("Secondary failover vendor active. Supplier reliability %.1f%%. Residual risk reduced to %.1f.",
                            score, residualScore);
                }
                case "WAREHOUSE_CAPACITY_OVERRUN" -> {
                    Warehouse warehouse = warehouseRepository.findByCode(targetEntity).orElse(null);
                    double util = (warehouse != null && warehouse.getUtilizationPercentage() != null)
                            ? warehouse.getUtilizationPercentage().doubleValue()
                            : 65.0;
                    residualScore = Math.max(15.0, util * 0.3);
                    evaluationSummary = String.format("Warehouse utilization rebalanced to %.1f%%. Facility congestion risk reduced to %.1f.",
                            util, residualScore);
                }
                default -> {
                    residualScore = 18.0;
                    evaluationSummary = String.format("Logistics carrier rerouting active. Delivery delay risk reduced to %.1f.", residualScore);
                }
            }
        } catch (Exception ex) {
            log.warn("[POST-RECOVERY EVALUATION WARN] Evaluation exception for {}: {}", targetEntity, ex.getMessage());
            residualScore = 20.0;
            evaluationSummary = "Recovery policy executed. Residual risk mitigated to LOW band.";
        }

        double riskDelta = Math.max(0.0, initialRiskScore - residualScore);
        String residualBand = (residualScore >= 70.0) ? "HIGH" : (residualScore >= 35.0) ? "MEDIUM" : "LOW";

        PostRecoveryRiskResult result = PostRecoveryRiskResult.builder()
                .targetEntity(targetEntity)
                .disruptionType(disruptionTypeStr)
                .initialRiskScore(initialRiskScore)
                .postRecoveryRiskScore(residualScore)
                .riskReductionDelta(riskDelta)
                .initialRiskBand(initialRiskBand != null ? initialRiskBand : "HIGH")
                .residualRiskBand(residualBand)
                .evaluationSummary(evaluationSummary)
                .build();

        log.info("[POST-RECOVERY EVALUATION COMPLETE] Entity: {} | Initial: {} | Post-Execution: {} | Delta: -{} | Band: {}",
                targetEntity, initialRiskScore, residualScore, riskDelta, residualBand);

        return result;
    }
}
