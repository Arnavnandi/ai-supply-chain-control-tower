package com.supplychain.controltower.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.SupplierProductRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderGeneratorEngine {

    private final SupplierProductRepository supplierProductRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class PurchaseOrderPayload {
        private String actionType; // REORDER_STOCK
        private Long productId;
        private String productSku;
        private String sku;
        private String productName;
        private Long warehouseId;
        private String warehouseName;
        private Long supplierId;
        private String supplierName;
        private Integer orderQuantity;
        private BigDecimal contractUnitPrice;
        private BigDecimal totalCost;
        private String reasoning;

        public String getProductSku() {
            return productSku != null ? productSku : sku;
        }

        public String getSku() {
            return sku != null ? sku : productSku;
        }
    }

    public PurchaseOrderPayload generateReplenishmentPayload(Product product, Inventory inventory, int projectedDemand30Day) {
        String rawSku = product != null ? product.getSku() : null;
        String resolvedSku = (rawSku != null && !rawSku.isBlank()) ? rawSku.trim() : null;

        log.info("[PO GENERATOR ENGINE] Generating replenishment order payload for SKU={} at warehouse={}",
                resolvedSku != null ? resolvedSku : "null",
                (inventory != null && inventory.getWarehouse() != null) ? inventory.getWarehouse().getName() : "Unknown");

        // 1. Calculate Target Replenishment Quantity
        int currentStock = (inventory != null && inventory.getQuantityAvailable() != null) ? inventory.getQuantityAvailable() : 0;
        int reorderLevel = (inventory != null && inventory.getReorderLevel() != null)
                ? inventory.getReorderLevel()
                : (product != null && product.getReorderLevel() != null ? product.getReorderLevel() : 100);
        int safetyStock = (inventory != null && inventory.getSafetyStock() != null)
                ? inventory.getSafetyStock()
                : (product != null && product.getSafetyStock() != null ? product.getSafetyStock() : 50);

        // Required Qty = (Reorder Level - Current Stock) + Safety Stock + (30-day forecast / 2)
        int targetQty = Math.max((reorderLevel - currentStock) + safetyStock + (projectedDemand30Day / 2), 50);

        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        List<SupplierProduct> supplierProducts = (product != null && product.getId() != null)
                ? supplierProductRepository.findByCompanyIdAndProductId(companyId, product.getId())
                : List.of();

        Supplier selectedSupplier = null;
        BigDecimal contractPrice = (product != null && product.getPrice() != null) ? product.getPrice() : BigDecimal.valueOf(50.0);

        if (supplierProducts != null && !supplierProducts.isEmpty()) {
            SupplierProduct preferred = supplierProducts.stream()
                    .filter(sp -> Boolean.TRUE.equals(sp.getIsPreferredSupplier()))
                    .findFirst()
                    .orElse(supplierProducts.get(0));

            selectedSupplier = preferred.getSupplier();
            if (preferred.getContractPrice() != null) {
                contractPrice = preferred.getContractPrice();
            }
            if (preferred.getMinimumOrderQuantity() != null && preferred.getMinimumOrderQuantity() > targetQty) {
                targetQty = preferred.getMinimumOrderQuantity();
            }
        }

        BigDecimal totalCost = contractPrice.multiply(BigDecimal.valueOf(targetQty)).setScale(2, RoundingMode.HALF_UP);

        String productName = (product != null && product.getName() != null) ? product.getName() : "Unknown Product";

        String reasoning = String.format(
                "Automated Replenishment Proposal: Current stock (%d units) is below safety stock (%d units) and reorder level (%d units). " +
                "Projected 30-day demand is %d units. Proposed purchase order of %d units from preferred supplier '%s' " +
                "at contract price $%s/unit (Total Investment: $%s).",
                currentStock, safetyStock, reorderLevel, projectedDemand30Day, targetQty,
                selectedSupplier != null ? selectedSupplier.getName() : "Primary Vendor",
                contractPrice, totalCost
        );

        Warehouse warehouse = inventory != null ? inventory.getWarehouse() : null;

        return PurchaseOrderPayload.builder()
                .actionType("REORDER_STOCK")
                .productId(product != null ? product.getId() : null)
                .productSku(resolvedSku)
                .sku(resolvedSku)
                .productName(productName)
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : "Central Warehouse")
                .supplierId(selectedSupplier != null ? selectedSupplier.getId() : null)
                .supplierName(selectedSupplier != null ? selectedSupplier.getName() : "Primary Vendor")
                .orderQuantity(targetQty)
                .contractUnitPrice(contractPrice)
                .totalCost(totalCost)
                .reasoning(reasoning)
                .build();
    }

    public String convertPayloadToJson(PurchaseOrderPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.error("[PO GENERATOR] Failed to serialize payload to JSON: {}", ex.getMessage());
            return "{}";
        }
    }
}
