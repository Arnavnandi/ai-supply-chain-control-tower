package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.dashboard.DashboardSummaryDto;
import com.supplychain.controltower.entity.CustomerOrder;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.repository.*;
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
        long totalProducts = productRepository.count();
        List<Inventory> allInventory = inventoryRepository.findAll();
        long totalUnits = allInventory.stream().mapToLong(Inventory::getQuantityAvailable).sum();

        Double totalVal = inventoryRepository.calculateTotalInventoryValue();
        BigDecimal totalInventoryValue = totalVal != null ? BigDecimal.valueOf(totalVal).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        long lowStockCount = inventoryRepository.findLowStockInventory().size();
        long overstockCount = inventoryRepository.findOverstockedInventory().size();
        long pendingOrdersCount = orderRepository.findByStatus(CustomerOrder.OrderStatus.PENDING).size() +
                orderRepository.findByStatus(CustomerOrder.OrderStatus.PROCESSING).size();
        long delayedShipmentsCount = shipmentRepository.findByStatus(Shipment.ShipmentStatus.DELAYED).size();

        // Calculate average supplier reliability
        Double avgRel = supplierRepository.findAll().stream()
                .map(s -> s.getReliabilityScore() != null ? s.getReliabilityScore().doubleValue() : 80.0)
                .mapToDouble(Double::doubleValue).average().orElse(85.0);

        // Calculate average warehouse utilization
        Double avgUtil = warehouseRepository.findAll().stream()
                .map(w -> w.getUtilizationPercentage() != null ? w.getUtilizationPercentage().doubleValue() : 50.0)
                .mapToDouble(Double::doubleValue).average().orElse(65.0);

        // Calculate overall Supply Chain Risk Score (0-100) based on stockouts, delays, and overutilization
        double riskScore = Math.min(100.0, (lowStockCount * 15.0) + (delayedShipmentsCount * 20.0) + (avgUtil > 90 ? 25.0 : 5.0));

        // Calculate real demand trends from PostgreSQL Customer Orders & Order Items
        List<CustomerOrder> allOrders = orderRepository.findByOrderByOrderDateDesc();
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

        List<Map<String, Object>> supplierPerformance = supplierRepository.findAll().stream().map(s ->
                Map.of("name", (Object) s.getName(),
                        "reliability", s.getReliabilityScore() != null ? s.getReliabilityScore() : 80,
                        "deliveryRate", s.getDeliveryPerformancePct() != null ? s.getDeliveryPerformancePct() : 85,
                        "leadTime", s.getAverageLeadTimeDays() != null ? s.getAverageLeadTimeDays() : 7.0)
        ).toList();

        List<Map<String, Object>> warehouseUtilization = warehouseRepository.findAll().stream().map(w ->
                Map.of("name", (Object) w.getName(),
                        "capacity", w.getTotalCapacityUnits(),
                        "used", w.getCurrentUtilizationUnits(),
                        "pct", w.getUtilizationPercentage() != null ? w.getUtilizationPercentage() : 50)
        ).toList();

        return DashboardSummaryDto.builder()
                .totalProducts(totalProducts)
                .totalInventoryUnits(totalUnits)
                .totalInventoryValue(totalInventoryValue)
                .lowStockProductsCount(lowStockCount)
                .overstockedProductsCount(overstockCount)
                .pendingOrdersCount(pendingOrdersCount)
                .delayedShipmentsCount(delayedShipmentsCount)
                .overallSupplierReliabilityPct(BigDecimal.valueOf(avgRel).setScale(2, RoundingMode.HALF_UP))
                .averageWarehouseUtilizationPct(BigDecimal.valueOf(avgUtil).setScale(2, RoundingMode.HALF_UP))
                .supplyChainRiskScore(BigDecimal.valueOf(riskScore).setScale(2, RoundingMode.HALF_UP))
                .inventoryTrends(inventoryTrends)
                .demandTrends(demandTrends)
                .supplierPerformance(supplierPerformance)
                .warehouseUtilization(warehouseUtilization)
                .build();
    }
}
