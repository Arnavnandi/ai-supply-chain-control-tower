package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.InventoryRepository;
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
public class ExecutiveCommandCenterEngine {

    private final SupplierRepository supplierRepository;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final HistoricalMitigationEfficacyEngine efficacyEngine;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutiveScorecardReport {
        private String reportId;
        private double overallResiliencyIndex; // 0.0 - 100.0
        private String resiliencyStatusBand; // OPTIMAL, HEALTHY, WARNING, CRITICAL
        private double supplierOtifComponentScore;
        private double inventoryBufferComponentScore;
        private double warehouseCapacityComponentScore;
        private double historicalEfficacyComponentScore;
        private String overallPortfolioStatus;
        private List<String> majorRiskHighlights;
        private List<String> recommendedExecutiveAttentionAreas;
        private String executiveBriefingSummary;
        @Builder.Default
        private String timestamp = LocalDateTime.now().toString();
    }

    public ExecutiveScorecardReport generateExecutiveCommandCenterReport() {
        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        String reportId = "EXEC-CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[EXECUTIVE COMMAND CENTER] Computing overall Supply Chain Resiliency Index across operational domains for companyId={}. ReportId: {}", companyId, reportId);

        // 1. Component 1: Supplier OTIF Health (30% weight)
        List<Supplier> suppliers = supplierRepository.findByCompanyId(companyId);
        Double supplierOtif = null;
        if (!suppliers.isEmpty()) {
            OptionalDouble avgRel = suppliers.stream()
                    .map(Supplier::getReliabilityScore)
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average();
            if (avgRel.isPresent()) {
                supplierOtif = Math.min(100.0, Math.max(0.0, avgRel.getAsDouble()));
            }
        }

        // 2. Component 2: Inventory Buffer Safety (25% weight)
        List<Inventory> inventories = inventoryRepository.findByCompanyId(companyId);
        Double inventoryScore = null;
        if (!inventories.isEmpty()) {
            long total = inventories.size();
            long healthy = inventories.stream()
                    .filter(i -> i.getQuantityAvailable() != null && i.getQuantityAvailable() >= i.getSafetyStock())
                    .count();
            inventoryScore = Math.min(100.0, Math.max(0.0, (healthy * 100.0) / total));
        }

        // 3. Component 3: Warehouse Storage Capacity Health (25% weight)
        List<Warehouse> warehouses = warehouseRepository.findByCompanyId(companyId);
        Double warehouseScore = null;
        if (!warehouses.isEmpty()) {
            OptionalDouble avgUtil = warehouses.stream()
                    .map(Warehouse::getUtilizationPercentage)
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .average();
            if (avgUtil.isPresent()) {
                double util = avgUtil.getAsDouble();
                warehouseScore = Math.min(100.0, Math.max(0.0, 100.0 - (util > 70.0 ? (util - 70.0) * 2.5 : 0.0)));
            }
        }

        // 4. Component 4: Historical Mitigation Efficacy (20% weight)
        Double efficacyScore = null;
        try {
            HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport eff = efficacyEngine.calculateHistoricalEfficacy();
            if (eff != null && eff.getTotalHistoricalExecutions() > 0) {
                efficacyScore = Math.min(100.0, Math.max(0.0, eff.getOverallSuccessRatePct()));
            }
        } catch (Exception ex) {
            log.warn("[EXECUTIVE CC] Historical efficacy calculation error: {}", ex.getMessage());
        }

        // Calculate dynamic weighted resiliency index across available components
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        if (supplierOtif != null) {
            weightedSum += 0.30 * supplierOtif;
            totalWeight += 0.30;
        }
        if (inventoryScore != null) {
            weightedSum += 0.25 * inventoryScore;
            totalWeight += 0.25;
        }
        if (warehouseScore != null) {
            weightedSum += 0.25 * warehouseScore;
            totalWeight += 0.25;
        }
        if (efficacyScore != null) {
            weightedSum += 0.20 * efficacyScore;
            totalWeight += 0.20;
        }

        double totalResiliencyIndex = totalWeight > 0 ? (weightedSum / totalWeight) : 0.0;
        totalResiliencyIndex = Math.min(100.0, Math.max(0.0, totalResiliencyIndex));

        String band;
        if (totalWeight == 0.0) {
            band = "INSUFFICIENT_DATA";
        } else if (totalResiliencyIndex >= 88.0) {
            band = "OPTIMAL";
        } else if (totalResiliencyIndex >= 75.0) {
            band = "HEALTHY";
        } else if (totalResiliencyIndex >= 60.0) {
            band = "WARNING";
        } else {
            band = "CRITICAL";
        }

        List<String> riskHighlights = new ArrayList<>();
        if (totalWeight == 0.0) {
            riskHighlights.add("No operational records available. Suppliers, inventory, and warehouses must be populated to compute resiliency.");
        } else {
            if (inventoryScore != null && inventoryScore < 85.0) {
                riskHighlights.add("Inventory deficit detected: several SKUs are currently operating below safety stock levels.");
            }
            if (warehouseScore != null && warehouseScore < 85.0) {
                riskHighlights.add("Warehouse utilization spike: storage capacity at primary distribution hubs is approaching threshold limits.");
            }
            if (supplierOtif != null && supplierOtif < 85.0) {
                riskHighlights.add("Supplier OTIF degradation: key tier-1 chip/component vendors show delivery delays.");
            }
            if (riskHighlights.isEmpty()) {
                riskHighlights.add(String.format("Operational parameters within nominal tolerances across %d regional facilities.", warehouses.size()));
            }
        }

        List<String> attentionAreas = List.of(
                "Review pending action proposals in Action Center requiring human manager JWT authorization.",
                "Inspect predictive early-warning radar for impending 2-to-5 day stockout signals.",
                "Evaluate Cost vs SLA speed tradeoffs for expedited air freight options."
        );

        String briefing;
        if (totalWeight == 0.0) {
            briefing = "Executive Briefing: Insufficient operational telemetry. Resilience Index cannot be calculated without active supplier, warehouse, or inventory records.";
        } else {
            briefing = String.format("Executive Briefing: Overall Resiliency Index stands at %.1f/100 (%s). Supplier OTIF: %s, Inventory Safety: %s, Warehouse Health: %s, Historical Efficacy: %s.",
                    totalResiliencyIndex, band,
                    supplierOtif != null ? String.format("%.1f%%", supplierOtif) : "N/A",
                    inventoryScore != null ? String.format("%.1f%%", inventoryScore) : "N/A",
                    warehouseScore != null ? String.format("%.1f%%", warehouseScore) : "N/A",
                    efficacyScore != null ? String.format("%.1f%%", efficacyScore) : "N/A");
        }

        ExecutiveScorecardReport report = ExecutiveScorecardReport.builder()
                .reportId(reportId)
                .overallResiliencyIndex(totalResiliencyIndex)
                .resiliencyStatusBand(band)
                .supplierOtifComponentScore(supplierOtif != null ? supplierOtif : 0.0)
                .inventoryBufferComponentScore(inventoryScore != null ? inventoryScore : 0.0)
                .warehouseCapacityComponentScore(warehouseScore != null ? warehouseScore : 0.0)
                .historicalEfficacyComponentScore(efficacyScore != null ? efficacyScore : 0.0)
                .overallPortfolioStatus(totalWeight == 0.0
                        ? "Control Tower operational state is uninitialized (Insufficient Data)."
                        : String.format("Control Tower operational state is %s with an overall Resiliency Index of %.1f/100.", band, totalResiliencyIndex))
                .majorRiskHighlights(riskHighlights)
                .recommendedExecutiveAttentionAreas(attentionAreas)
                .executiveBriefingSummary(briefing)
                .build();

        log.info("[EXECUTIVE CC COMPLETE] ReportId: {} | ResiliencyIndex: %.1f (%s)", reportId, totalResiliencyIndex, band);
        return report;
    }
}
