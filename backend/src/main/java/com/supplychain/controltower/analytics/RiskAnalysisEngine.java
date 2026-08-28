package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.RiskAlert;
import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ShipmentRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskAnalysisEngine {

    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final ShipmentRepository shipmentRepository;

    @Data
    @Builder
    public static class ControlTowerRiskReport {
        private double overallRiskScore; // 0 (low risk) to 100 (critical risk)
        private String riskLevel; // LOW, MODERATE, HIGH, CRITICAL
        private int criticalRisksCount;
        private int highRisksCount;
        private int mediumRisksCount;
        private int lowRisksCount;
        private List<ExplainableRiskItem> riskItems;
    }

    @Data
    @Builder
    public static class ExplainableRiskItem {
        private String id;
        private String category; // INVENTORY, SUPPLIER, SHIPMENT
        private String title;
        private String severity; // CRITICAL, HIGH, MEDIUM, LOW
        private String problemDetected;
        private String dataCause;
        private String actionRecommended;
        private String status; // ACTIVE, RESOLVED
    }

    public ControlTowerRiskReport evaluateSystemRisks() {
        List<ExplainableRiskItem> risks = new ArrayList<>();

        // 1. Inventory Risk Detection (Stockout & Overstock)
        List<Inventory> lowStockItems = inventoryRepository.findLowStockInventory();
        for (Inventory item : lowStockItems) {
            String severity = (item.getQuantityAvailable() == 0 || item.getQuantityAvailable() < item.getSafetyStock() / 2)
                    ? "CRITICAL" : "HIGH";

            String problem = String.format("Stockout Risk for product '%s' at '%s'",
                    item.getProduct().getName(), item.getWarehouse().getName());

            String cause = String.format("Available Stock (%d units) is below Safety Stock (%d units) and Reorder Level (%d units).",
                    item.getQuantityAvailable(), item.getSafetyStock(), item.getReorderLevel());

            String action = String.format("Generate immediate Purchase Order for SKU '%s' from preferred supplier and expedite delivery to warehouse '%s'.",
                    item.getProduct().getSku(), item.getWarehouse().getName());

            risks.add(ExplainableRiskItem.builder()
                    .id("INV-" + item.getId())
                    .category("INVENTORY")
                    .title(problem)
                    .severity(severity)
                    .problemDetected(problem)
                    .dataCause(cause)
                    .actionRecommended(action)
                    .status("ACTIVE")
                    .build());
        }

        List<Inventory> overstockedItems = inventoryRepository.findOverstockedInventory();
        for (Inventory item : overstockedItems) {
            String problem = String.format("Excess Inventory Accumulation for '%s' at '%s'",
                    item.getProduct().getName(), item.getWarehouse().getName());

            String cause = String.format("Current Available Stock (%d units) exceeds 3x Reorder Level (%d units), tying up working capital.",
                    item.getQuantityAvailable(), item.getReorderLevel());

            String action = String.format("Pause replenishment orders for SKU '%s' and evaluate stock redistribution to high-demand regional warehouses.",
                    item.getProduct().getSku());

            risks.add(ExplainableRiskItem.builder()
                    .id("INV-OVER-" + item.getId())
                    .category("INVENTORY")
                    .title(problem)
                    .severity("MEDIUM")
                    .problemDetected(problem)
                    .dataCause(cause)
                    .actionRecommended(action)
                    .status("ACTIVE")
                    .build());
        }

        // 2. Supplier Performance Risk Detection
        List<Supplier> suppliers = supplierRepository.findAll();
        for (Supplier s : suppliers) {
            double rel = s.getReliabilityScore() != null ? s.getReliabilityScore().doubleValue() : 100.0;
            double del = s.getDeliveryPerformancePct() != null ? s.getDeliveryPerformancePct().doubleValue() : 100.0;

            if (rel < 85.0 || del < 85.0) {
                String severity = (rel < 75.0 || del < 75.0) ? "CRITICAL" : "HIGH";

                String problem = String.format("Supplier Delivery Unreliability for '%s' (%s)",
                        s.getName(), s.getCode());

                String cause = String.format("Reliability Score is %.1f%% and On-Time Delivery Performance is %.1f%% (Threshold: 85.0%%). Average lead time variance: %.1f days.",
                        rel, del, s.getLeadTimeVarianceDays() != null ? s.getLeadTimeVarianceDays() : 0.0);

                String action = String.format("Audit supplier '%s' performance, request SLA corrective action plan, and prepare secondary backup suppliers for contracted SKUs.",
                        s.getName());

                risks.add(ExplainableRiskItem.builder()
                        .id("SUP-" + s.getId())
                        .category("SUPPLIER")
                        .title(problem)
                        .severity(severity)
                        .problemDetected(problem)
                        .dataCause(cause)
                        .actionRecommended(action)
                        .status("ACTIVE")
                        .build());
            }
        }

        // 3. Shipment / Logistics Risk Detection
        List<Shipment> shipments = shipmentRepository.findAll();
        LocalDate today = LocalDate.now();
        for (Shipment sh : shipments) {
            boolean isDelayed = Shipment.ShipmentStatus.DELAYED.equals(sh.getStatus()) ||
                    (Shipment.ShipmentStatus.IN_TRANSIT.equals(sh.getStatus()) && sh.getEstimatedDeliveryDate() != null && sh.getEstimatedDeliveryDate().isBefore(today));

            if (isDelayed) {
                String severity = (sh.getDelayDays() != null && sh.getDelayDays() > 5) ? "CRITICAL" : "HIGH";

                String problem = String.format("Delayed Cargo Shipment tracking code '%s'", sh.getTrackingCode());

                String cause = String.format("Shipment from origin '%s' to '%s' via carrier '%s' is delayed by %d days (Est. Delivery was %s).",
                        sh.getOrigin(), sh.getDestination(), sh.getCarrierName() != null ? sh.getCarrierName() : "Standard Carrier",
                        sh.getDelayDays() != null ? sh.getDelayDays() : 1, sh.getEstimatedDeliveryDate());

                String action = String.format("Contact carrier '%s' for cargo location tracking and reroute priority logistics to destination '%s'.",
                        sh.getCarrierName() != null ? sh.getCarrierName() : "Carrier", sh.getDestination());

                risks.add(ExplainableRiskItem.builder()
                        .id("SHP-" + sh.getId())
                        .category("SHIPMENT")
                        .title(problem)
                        .severity(severity)
                        .problemDetected(problem)
                        .dataCause(cause)
                        .actionRecommended(action)
                        .status("ACTIVE")
                        .build());
            }
        }

        // Count severities
        int critical = 0, high = 0, medium = 0, low = 0;
        for (ExplainableRiskItem item : risks) {
            switch (item.getSeverity()) {
                case "CRITICAL" -> critical++;
                case "HIGH" -> high++;
                case "MEDIUM" -> medium++;
                default -> low++;
            }
        }

        // Calculate Overall System Risk Score (0 - 100)
        double score = Math.min(100.0, (critical * 25.0) + (high * 10.0) + (medium * 3.0) + (low * 1.0));
        String level = score >= 75.0 ? "CRITICAL" : score >= 50.0 ? "HIGH" : score >= 25.0 ? "MODERATE" : "LOW";

        return ControlTowerRiskReport.builder()
                .overallRiskScore(Math.round(score * 10.0) / 10.0)
                .riskLevel(level)
                .criticalRisksCount(critical)
                .highRisksCount(high)
                .mediumRisksCount(medium)
                .lowRisksCount(low)
                .riskItems(risks)
                .build();
    }
}
