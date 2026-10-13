package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.SupplierProduct;
import com.supplychain.controltower.repository.ShipmentRepository;
import com.supplychain.controltower.repository.SupplierProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
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
public class SupplierAnalyticsEngine {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ShipmentRepository shipmentRepository;

    @Data
    @Builder
    public static class SupplierPerformanceMetric {
        private Long supplierId;
        private String supplierCode;
        private String supplierName;
        private String country;
        private BigDecimal reliabilityScore;
        private BigDecimal deliveryPerformancePct;
        private Double averageLeadTimeDays;
        private Double leadTimeVarianceDays;
        private Integer totalShipmentsHandled;
        private Integer delayedShipmentsCount;
        private BigDecimal otifScorePct;
        private String riskClassification; // PREFERRED_LOW_RISK, MODERATE_RISK, HIGH_RISK_CRITICAL
        private List<String> contractedSkus;
    }

    @Data
    @Builder
    public static class SupplierAnalyticsSummary {
        private Integer totalSuppliers;
        private Integer lowRiskSuppliersCount;
        private Integer moderateRiskSuppliersCount;
        private Integer highRiskSuppliersCount;
        private BigDecimal averageSystemOtifPct;
        private List<SupplierPerformanceMetric> supplierMetrics;
    }

    public SupplierAnalyticsSummary analyzeSupplierPerformance() {
        log.info("[SUPPLIER ANALYTICS ENGINE] Evaluating OTIF performance and risk matrix across PostgreSQL suppliers...");

        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        List<Supplier> suppliers = supplierRepository.findByCompanyId(companyId);
        List<Shipment> allShipments = shipmentRepository.findByCompanyId(companyId);
        List<SupplierProduct> allContracts = supplierProductRepository.findByCompanyId(companyId);

        List<SupplierPerformanceMetric> metrics = new ArrayList<>();

        int lowRisk = 0;
        int modRisk = 0;
        int highRisk = 0;
        double sumOtif = 0.0;
        int otifCount = 0;

        for (Supplier supplier : suppliers) {
            List<Shipment> supplierShipments = allShipments.stream()
                    .filter(s -> s.getSupplier() != null && s.getSupplier().getId().equals(supplier.getId()))
                    .toList();

            int totalShipments = supplierShipments.size();
            int delayedCount = (int) supplierShipments.stream()
                    .filter(s -> s.getDelayDays() != null && s.getDelayDays() > 0)
                    .count();

            Double otifPct = null;
            if (totalShipments > 0) {
                otifPct = Math.max(0.0, ((double) (totalShipments - delayedCount) / totalShipments) * 100.0);
            } else if (supplier.getDeliveryPerformancePct() != null) {
                otifPct = supplier.getDeliveryPerformancePct().doubleValue();
            }

            // Risk Tier Classification
            String riskTier;
            Double reliability = supplier.getReliabilityScore() != null ? supplier.getReliabilityScore().doubleValue() : null;

            if (reliability != null && otifPct != null) {
                if (reliability >= 90.0 && otifPct >= 88.0) {
                    riskTier = "PREFERRED_LOW_RISK";
                    lowRisk++;
                } else if (reliability >= 75.0 && otifPct >= 70.0) {
                    riskTier = "MODERATE_RISK";
                    modRisk++;
                } else {
                    riskTier = "HIGH_RISK_CRITICAL";
                    highRisk++;
                }
            } else if (reliability != null) {
                if (reliability >= 90.0) {
                    riskTier = "PREFERRED_LOW_RISK";
                    lowRisk++;
                } else if (reliability >= 75.0) {
                    riskTier = "MODERATE_RISK";
                    modRisk++;
                } else {
                    riskTier = "HIGH_RISK_CRITICAL";
                    highRisk++;
                }
            } else {
                riskTier = "UNRATED";
            }

            List<String> contractedSkus = allContracts.stream()
                    .filter(sp -> sp.getSupplier() != null && sp.getSupplier().getId().equals(supplier.getId()))
                    .map(sp -> sp.getProduct() != null ? sp.getProduct().getSku() : "N/A")
                    .distinct()
                    .toList();

            BigDecimal otifBd = otifPct != null ? BigDecimal.valueOf(otifPct).setScale(1, RoundingMode.HALF_UP) : null;
            if (otifBd != null) {
                sumOtif += otifBd.doubleValue();
                otifCount++;
            }

            metrics.add(SupplierPerformanceMetric.builder()
                    .supplierId(supplier.getId())
                    .supplierCode(supplier.getCode())
                    .supplierName(supplier.getName())
                    .country(supplier.getCountry())
                    .reliabilityScore(supplier.getReliabilityScore())
                    .deliveryPerformancePct(supplier.getDeliveryPerformancePct())
                    .averageLeadTimeDays(supplier.getAverageLeadTimeDays())
                    .leadTimeVarianceDays(supplier.getLeadTimeVarianceDays())
                    .totalShipmentsHandled(totalShipments)
                    .delayedShipmentsCount(delayedCount)
                    .otifScorePct(otifBd)
                    .riskClassification(riskTier)
                    .contractedSkus(contractedSkus)
                    .build());
        }

        BigDecimal avgOtif = otifCount > 0 ?
                BigDecimal.valueOf(sumOtif / otifCount).setScale(1, RoundingMode.HALF_UP) : null;

        return SupplierAnalyticsSummary.builder()
                .totalSuppliers(suppliers.size())
                .lowRiskSuppliersCount(lowRisk)
                .moderateRiskSuppliersCount(modRisk)
                .highRiskSuppliersCount(highRisk)
                .averageSystemOtifPct(avgOtif)
                .supplierMetrics(metrics)
                .build();
    }
}
