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
        private String productName;
        private Long warehouseId;
        private String warehouseName;
        private Long supplierId;
        private String supplierName;
        private Integer orderQuantity;
        private BigDecimal contractUnitPrice;
        private BigDecimal totalCost;
        private String reasoning;
    }

    public PurchaseOrderPayload generateReplenishmentPayload(Product product, Inventory inventory, int projectedDemand30Day) {
        log.info("[PO GENERATOR ENGINE] Generating replenishment order payload for SKU={} at warehouse={}",
                product.getSku(), inventory.getWarehouse() != null ? inventory.getWarehouse().getName() : "Unknown");

        // 1. Calculate Target Replenishment Quantity
        int currentStock = inventory.getQuantityAvailable() != null ? inventory.getQuantityAvailable() : 0;
        int reorderLevel = inventory.getReorderLevel() != null ? inventory.getReorderLevel() : product.getReorderLevel();
        int safetyStock = inventory.getSafetyStock() != null ? inventory.getSafetyStock() : product.getSafetyStock();

        // Required Qty = (Reorder Level - Current Stock) + Safety Stock + (30-day forecast / 2)
        int targetQty = Math.max((reorderLevel - currentStock) + safetyStock + (projectedDemand30Day / 2), 50);

        // 2. Select Supplier & Contract Price from database
        List<SupplierProduct> supplierProducts = supplierProductRepository.findByProductId(product.getId());

        Supplier selectedSupplier = null;
        BigDecimal contractPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.valueOf(50.0);

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

        String reasoning = String.format(
                "Automated Replenishment Proposal: Current stock (%d units) is below safety stock (%d units) and reorder level (%d units). " +
                "Projected 30-day demand is %d units. Proposed purchase order of %d units from preferred supplier '%s' " +
                "at contract price $%s/unit (Total Investment: $%s).",
                currentStock, safetyStock, reorderLevel, projectedDemand30Day, targetQty,
                selectedSupplier != null ? selectedSupplier.getName() : "Primary Vendor",
                contractPrice, totalCost
        );

        return PurchaseOrderPayload.builder()
                .actionType("REORDER_STOCK")
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .warehouseId(inventory.getWarehouse() != null ? inventory.getWarehouse().getId() : null)
                .warehouseName(inventory.getWarehouse() != null ? inventory.getWarehouse().getName() : "Central Warehouse")
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
