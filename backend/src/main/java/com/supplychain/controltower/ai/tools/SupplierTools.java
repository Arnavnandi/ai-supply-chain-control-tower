package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.SupplierProduct;
import com.supplychain.controltower.repository.SupplierProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierTools {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;

    @Description("Retrieves supplier performance metrics including reliability scores, delivery rates, and average lead times.")
    public List<SupplierPerformanceRecord> getSupplierPerformance() {
        log.info("[SPRING AI TOOL EXECUTING] getSupplierPerformance() querying PostgreSQL database...");
        List<SupplierPerformanceRecord> results = supplierRepository.findAll().stream().map(s ->
                new SupplierPerformanceRecord(
                        s.getId(),
                        s.getCode(),
                        s.getName(),
                        s.getReliabilityScore(),
                        s.getDeliveryPerformancePct(),
                        s.getAverageLeadTimeDays()
                )
        ).toList();
        log.info("[SPRING AI TOOL COMPLETE] getSupplierPerformance() returned {} suppliers.", results.size());
        return results;
    }

    @Description("Compares supplier pricing, lead times, and reliability scores for a specific product ID.")
    public List<SupplierComparisonRecord> getSuppliersForProduct(Long productId) {
        log.info("[SPRING AI TOOL EXECUTING] getSuppliersForProduct(productId={}) querying PostgreSQL database...", productId);
        if (productId == null) return List.of();
        List<SupplierComparisonRecord> results = supplierProductRepository.findByProductId(productId).stream().map(sp ->
                new SupplierComparisonRecord(
                        sp.getSupplier().getId(),
                        sp.getSupplier().getName(),
                        sp.getContractPrice(),
                        sp.getLeadTimeDays(),
                        sp.getSupplier().getReliabilityScore(),
                        sp.getIsPreferredSupplier()
                )
        ).toList();
        log.info("[SPRING AI TOOL COMPLETE] getSuppliersForProduct(productId={}) returned {} records.", productId, results.size());
        return results;
    }

    public record SupplierPerformanceRecord(
            Long id,
            String code,
            String name,
            BigDecimal reliabilityScore,
            BigDecimal deliveryPerformancePct,
            Double averageLeadTimeDays
    ) {}

    public record SupplierComparisonRecord(
            Long supplierId,
            String supplierName,
            BigDecimal contractPrice,
            Integer leadTimeDays,
            BigDecimal reliabilityScore,
            Boolean isPreferred
    ) {}
}

