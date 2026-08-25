package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryTools {

    private final InventoryRepository inventoryRepository;

    @Description("Retrieves all products that are at risk of stockout or currently below safety stock / reorder levels.")
    public List<InventoryItemRecord> getLowStockProducts() {
        log.info("[SPRING AI TOOL EXECUTING] getLowStockProducts() querying PostgreSQL database...");
        List<InventoryItemRecord> results = inventoryRepository.findLowStockInventory().stream().map(i ->
                new InventoryItemRecord(
                        i.getProduct().getId(),
                        i.getProduct().getSku(),
                        i.getProduct().getName(),
                        i.getWarehouse().getName(),
                        i.getQuantityAvailable(),
                        i.getReorderLevel(),
                        i.getSafetyStock()
                )
        ).toList();
        log.info("[SPRING AI TOOL COMPLETE] getLowStockProducts() returned {} items.", results.size());
        return results;
    }

    @Description("Retrieves products that are overstocked (inventory exceeds 3x reorder level).")
    public List<InventoryItemRecord> getOverstockedProducts() {
        log.info("[SPRING AI TOOL EXECUTING] getOverstockedProducts() querying PostgreSQL database...");
        List<InventoryItemRecord> results = inventoryRepository.findOverstockedInventory().stream().map(i ->
                new InventoryItemRecord(
                        i.getProduct().getId(),
                        i.getProduct().getSku(),
                        i.getProduct().getName(),
                        i.getWarehouse().getName(),
                        i.getQuantityAvailable(),
                        i.getReorderLevel(),
                        i.getSafetyStock()
                )
        ).toList();
        log.info("[SPRING AI TOOL COMPLETE] getOverstockedProducts() returned {} items.", results.size());
        return results;
    }

    public record InventoryItemRecord(
            Long productId,
            String sku,
            String name,
            String warehouse,
            Integer availableQty,
            Integer reorderLevel,
            Integer safetyStock
    ) {}
}

