package com.supplychain.controltower.dto.dashboard;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryDto {
    private Long totalProducts;
    private Long totalInventoryUnits;
    private BigDecimal totalInventoryValue;
    private Long lowStockProductsCount;
    private Long overstockedProductsCount;
    private Long pendingOrdersCount;
    private Long delayedShipmentsCount;
    private BigDecimal overallSupplierReliabilityPct;
    private BigDecimal averageWarehouseUtilizationPct;
    private BigDecimal supplyChainRiskScore; // 0-100 score

    private List<Map<String, Object>> inventoryTrends;
    private List<Map<String, Object>> demandTrends;
    private List<Map<String, Object>> supplierPerformance;
    private List<Map<String, Object>> warehouseUtilization;
}
