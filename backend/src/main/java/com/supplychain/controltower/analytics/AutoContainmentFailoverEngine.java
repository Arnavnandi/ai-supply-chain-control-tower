package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.SupplierRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoContainmentFailoverEngine {

    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailoverAllocation {
        private String pathType; // PRIMARY_DEGRADED vs FALLBACK_TIER2
        private String supplierCode;
        private String supplierName;
        private double allocationPercentage; // 60.0 vs 40.0
        private double reliabilityScore;
        private String logisticsRoute;
        private String targetWarehouseCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContainmentFailoverReport {
        private String containmentPlanId;
        private String failedSupplierCode;
        private String failedSupplierName;
        private String primaryWarehouseCode;
        private String containmentStatus; // CONTAINED, PARTIAL_CONTAINMENT, ESCALATED
        private double primaryAllocationPct; // 60.0
        private double fallbackAllocationPct; // 40.0
        private List<FailoverAllocation> allocations;
        private String alternateWarehouseCode;
        private double alternateWarehouseAvailableCapacityUnits;
        private int recommendedSafetyBufferUnits;
        private String strategyExplanation;
        @Builder.Default
        private String timestamp = LocalDateTime.now().toString();
    }

    public ContainmentFailoverReport computeFailoverContainmentPlan(String failedSupplierCodeInput, String primaryWarehouseCodeInput) {
        String planId = "FAILOVER-PLAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[AUTO CONTAINMENT ENGINE] Computing multi-supplier 60/40 failover routing for supplier input: {} | Primary Hub: {}", failedSupplierCodeInput, primaryWarehouseCodeInput);

        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        List<Supplier> suppliers = supplierRepository.findByCompanyId(companyId);
        List<Warehouse> warehouses = warehouseRepository.findByCompanyId(companyId);

        if (suppliers.isEmpty() || warehouses.isEmpty()) {
            return ContainmentFailoverReport.builder()
                    .containmentPlanId(planId)
                    .failedSupplierCode(failedSupplierCodeInput != null && !failedSupplierCodeInput.isBlank() ? failedSupplierCodeInput.trim() : "N/A")
                    .failedSupplierName("N/A")
                    .primaryWarehouseCode(primaryWarehouseCodeInput != null && !primaryWarehouseCodeInput.isBlank() ? primaryWarehouseCodeInput.trim() : "N/A")
                    .containmentStatus("INSUFFICIENT_DATA")
                    .primaryAllocationPct(0.0)
                    .fallbackAllocationPct(0.0)
                    .allocations(Collections.emptyList())
                    .alternateWarehouseCode(null)
                    .alternateWarehouseAvailableCapacityUnits(0.0)
                    .recommendedSafetyBufferUnits(0)
                    .strategyExplanation("Insufficient operational records: Company has no registered suppliers or warehouses to compute failover containment.")
                    .build();
        }

        String failedCode = (failedSupplierCodeInput != null && !failedSupplierCodeInput.isBlank())
                ? failedSupplierCodeInput.trim()
                : (suppliers.get(0).getCode() != null ? suppliers.get(0).getCode() : "SUP-1");
        String primaryWarehouse = (primaryWarehouseCodeInput != null && !primaryWarehouseCodeInput.isBlank())
                ? primaryWarehouseCodeInput.trim()
                : (warehouses.get(0).getCode() != null ? warehouses.get(0).getCode() : "WH-1");

        Supplier failedSupplier = suppliers.stream()
                .filter(s -> s.getCode() != null && s.getCode().equalsIgnoreCase(failedCode))
                .findFirst()
                .orElse(suppliers.get(0));

        String failedName = failedSupplier.getName() != null
                ? failedSupplier.getName()
                : "Primary Vendor (" + failedCode + ")";

        // Filter out failed supplier to select best fallback supplier
        Supplier fallbackSupplier = suppliers.stream()
                .filter(s -> s.getCode() != null && !s.getCode().equalsIgnoreCase(failedCode))
                .max(Comparator.comparing(s -> s.getReliabilityScore() != null ? s.getReliabilityScore().doubleValue() : 70.0))
                .orElse(null);

        String fallbackCode = (fallbackSupplier != null && fallbackSupplier.getCode() != null) ? fallbackSupplier.getCode() : "N/A";
        String fallbackName = (fallbackSupplier != null && fallbackSupplier.getName() != null) ? fallbackSupplier.getName() : "No Secondary Vendor Registered";
        double fallbackReliability = (fallbackSupplier != null && fallbackSupplier.getReliabilityScore() != null)
                ? fallbackSupplier.getReliabilityScore().doubleValue()
                : 0.0;

        Warehouse altWarehouse = warehouses.stream()
                .filter(w -> w.getCode() != null && !w.getCode().equalsIgnoreCase(primaryWarehouse))
                .findFirst()
                .orElse(null);

        String altWarehouseCode = altWarehouse != null ? altWarehouse.getCode() : null;
        double availableCapacity = 0.0;
        if (altWarehouse != null && altWarehouse.getTotalCapacityUnits() != null && altWarehouse.getUtilizationPercentage() != null) {
            double cap = altWarehouse.getTotalCapacityUnits().doubleValue();
            double util = altWarehouse.getUtilizationPercentage().doubleValue();
            availableCapacity = Math.max(0.0, cap * ((100.0 - util) / 100.0));
        }

        List<FailoverAllocation> allocations = new ArrayList<>();
        allocations.add(FailoverAllocation.builder()
                .pathType("PRIMARY_DEGRADED")
                .supplierCode(failedCode)
                .supplierName(failedName)
                .allocationPercentage(fallbackSupplier != null ? 60.0 : 100.0)
                .reliabilityScore(failedSupplier.getReliabilityScore() != null ? failedSupplier.getReliabilityScore().doubleValue() : 75.0)
                .logisticsRoute("Direct Freight Corridor Alpha")
                .targetWarehouseCode(primaryWarehouse)
                .build());

        if (fallbackSupplier != null) {
            allocations.add(FailoverAllocation.builder()
                    .pathType("FALLBACK_TIER2")
                    .supplierCode(fallbackCode)
                    .supplierName(fallbackName)
                    .allocationPercentage(40.0)
                    .reliabilityScore(fallbackReliability)
                    .logisticsRoute("Expedited Regional Bypass Route Beta")
                    .targetWarehouseCode(altWarehouseCode != null ? altWarehouseCode : primaryWarehouse)
                    .build());
        }

        ContainmentFailoverReport report = ContainmentFailoverReport.builder()
                .containmentPlanId(planId)
                .failedSupplierCode(failedCode)
                .failedSupplierName(failedName)
                .primaryWarehouseCode(primaryWarehouse)
                .containmentStatus(fallbackSupplier != null ? "CONTAINED" : "PARTIAL_CONTAINMENT")
                .primaryAllocationPct(fallbackSupplier != null ? 60.0 : 100.0)
                .fallbackAllocationPct(fallbackSupplier != null ? 40.0 : 0.0)
                .allocations(allocations)
                .alternateWarehouseCode(altWarehouseCode)
                .alternateWarehouseAvailableCapacityUnits(availableCapacity)
                .recommendedSafetyBufferUnits(120)
                .strategyExplanation(String.format("Multi-Supplier 60/40 Failover Active: 60%% order volume retained on %s with expedited buffer, 40%% re-allocated to fallback vendor %s (%s) routed to %s.",
                        failedCode, fallbackCode, fallbackName, altWarehouseCode))
                .build();

        log.info("[AUTO CONTAINMENT COMPLETE] PlanId: {} | Fallback: {} | AltHub: {}", planId, fallbackCode, altWarehouseCode);
        return report;
    }
}
