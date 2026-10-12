package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.dashboard.DashboardSummaryDto;
import com.supplychain.controltower.entity.CustomerOrder;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerOrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final RiskAlertRepository riskAlertRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary() {
        Long companyId = TenantContext.getRequiredCompanyId();
        long totalProducts = productRepository.findByCompanyId(companyId).size();
        List<Inventory> allInventory = inventoryRepository.findByCompanyId(companyId);
        long totalUnits = allInventory.stream().mapToLong(Inventory::getQuantityAvailable).sum();

        Double totalVal = inventoryRepository.calculateTotalInventoryValue(companyId);
        BigDecimal totalInventoryValue = totalVal != null ? BigDecimal.valueOf(totalVal).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        long lowStockCount = inventoryRepository.findLowStockInventory(companyId).size();
        long overstockCount = inventoryRepository.findOverstockedInventory(companyId).size();
        long pendingOrdersCount = orderRepository.findByCompanyIdAndStatus(companyId, CustomerOrder.OrderStatus.PENDING).size() +
                orderRepository.findByCompanyIdAndStatus(companyId, CustomerOrder.OrderStatus.PROCESSING).size();
        long delayedShipmentsCount = shipmentRepository.findByCompanyIdAndStatus(companyId, Shipment.ShipmentStatus.DELAYED).size();

        // Calculate average supplier reliability
        List<com.supplychain.controltower.entity.Supplier> companySuppliers = supplierRepository.findByCompanyId(companyId);
        OptionalDouble avgRelOpt = companySuppliers.stream()
                .map(com.supplychain.controltower.entity.Supplier::getReliabilityScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        BigDecimal avgRel = avgRelOpt.isPresent()
                ? BigDecimal.valueOf(avgRelOpt.getAsDouble()).setScale(2, RoundingMode.HALF_UP)
                : null;

        // Calculate average warehouse utilization
        List<com.supplychain.controltower.entity.Warehouse> companyWarehouses = warehouseRepository.findByCompanyId(companyId);
        OptionalDouble avgUtilOpt = companyWarehouses.stream()
                .map(com.supplychain.controltower.entity.Warehouse::getUtilizationPercentage)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        BigDecimal avgUtil = avgUtilOpt.isPresent()
                ? BigDecimal.valueOf(avgUtilOpt.getAsDouble()).setScale(2, RoundingMode.HALF_UP)
                : null;

        // Calculate overall Supply Chain Risk Score (0-100) based on stockouts, delays, and overutilization
        double riskScore = 0.0;
        if (totalProducts > 0 || !allInventory.isEmpty() || !companySuppliers.isEmpty() || !companyWarehouses.isEmpty()) {
            double warehousePenalty = (avgUtilOpt.isPresent() && avgUtilOpt.getAsDouble() > 90.0) ? 25.0 : 0.0;
            riskScore = Math.min(100.0, (lowStockCount * 15.0) + (delayedShipmentsCount * 20.0) + warehousePenalty);
        }

        // Calculate real demand trends from PostgreSQL Customer Orders & Order Items
        List<CustomerOrder> allOrders = orderRepository.findByCompanyIdOrderByOrderDateDesc(companyId);
        List<Map<String, Object>> demandTrends = new ArrayList<>();
        if (!allOrders.isEmpty()) {
            List<CustomerOrder> sortedOrders = allOrders.stream()
                    .filter(o -> o.getOrderDate() != null)
                    .sorted(Comparator.comparing(CustomerOrder::getOrderDate))
                    .toList();

            Map<String, Integer> monthlyDemandMap = new LinkedHashMap<>();
            for (CustomerOrder order : sortedOrders) {
                String monthLabel = order.getOrderDate().getMonth().name().substring(0, 1) +
                        order.getOrderDate().getMonth().name().substring(1, 3).toLowerCase();
                int totalQty = order.getItems() != null ?
                        order.getItems().stream().mapToInt(oi -> oi.getQuantity() != null ? oi.getQuantity() : 0).sum() : 0;
                monthlyDemandMap.put(monthLabel, monthlyDemandMap.getOrDefault(monthLabel, 0) + totalQty);
            }

            List<Integer> previousDemands = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : monthlyDemandMap.entrySet()) {
                int actual = entry.getValue();
                Integer forecastedVal = null;
                if (!previousDemands.isEmpty()) {
                    double avg = previousDemands.stream().mapToInt(Integer::intValue).average().orElse(actual);
                    forecastedVal = (int) Math.round(avg);
                }
                previousDemands.add(actual);

                Map<String, Object> trendPoint = new HashMap<>();
                trendPoint.put("month", entry.getKey());
                trendPoint.put("actualDemand", actual);
                trendPoint.put("forecasted", forecastedVal != null ? forecastedVal : actual);
                demandTrends.add(trendPoint);
            }
        }

        // Calculate real multi-warehouse inventory stock distribution from PostgreSQL
        List<Map<String, Object>> inventoryTrends = new ArrayList<>();
        if (!allInventory.isEmpty()) {
            Map<String, int[]> warehouseStockMap = new LinkedHashMap<>();
            for (Inventory inv : allInventory) {
                String label = inv.getWarehouse() != null ? inv.getWarehouse().getCode() : "HUB";
                int[] stockMetrics = warehouseStockMap.computeIfAbsent(label, k -> new int[3]);
                stockMetrics[0] += inv.getQuantityAvailable() != null ? inv.getQuantityAvailable() : 0;
                stockMetrics[1] += inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;
                stockMetrics[2] += inv.getSafetyStock() != null ? inv.getSafetyStock() : 0;
            }

            for (Map.Entry<String, int[]> entry : warehouseStockMap.entrySet()) {
                Map<String, Object> point = new HashMap<>();
                point.put("month", entry.getKey());
                point.put("available", entry.getValue()[0]);
                point.put("reserved", entry.getValue()[1]);
                point.put("safety", entry.getValue()[2]);
                inventoryTrends.add(point);
            }
        }

        List<Map<String, Object>> supplierPerformance = companySuppliers.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", s.getName());
            map.put("reliability", s.getReliabilityScore());
            map.put("deliveryRate", s.getDeliveryPerformancePct());
            map.put("leadTime", s.getAverageLeadTimeDays());
            return map;
        }).toList();

        List<Map<String, Object>> warehouseUtilization = companyWarehouses.stream().map(w -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", w.getName());
            map.put("capacity", w.getTotalCapacityUnits());
            map.put("used", w.getCurrentUtilizationUnits());
            map.put("pct", w.getUtilizationPercentage());
            return map;
        }).toList();

        return DashboardSummaryDto.builder()
                .totalProducts(totalProducts)
                .totalInventoryUnits(totalUnits)
                .totalInventoryValue(totalInventoryValue)
                .lowStockProductsCount(lowStockCount)
                .overstockedProductsCount(overstockCount)
                .pendingOrdersCount(pendingOrdersCount)
                .delayedShipmentsCount(delayedShipmentsCount)
                .overallSupplierReliabilityPct(avgRel)
                .averageWarehouseUtilizationPct(avgUtil)
                .supplyChainRiskScore(BigDecimal.valueOf(riskScore).setScale(2, RoundingMode.HALF_UP))
                .inventoryTrends(inventoryTrends)
                .demandTrends(demandTrends)
                .supplierPerformance(supplierPerformance)
                .warehouseUtilization(warehouseUtilization)
                .build();
    }
}

