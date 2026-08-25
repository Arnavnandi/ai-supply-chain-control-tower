package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.InventoryDto;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public List<InventoryDto> getAllInventory() {
        return inventoryRepository.findAll().stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getLowStockInventory() {
        return inventoryRepository.findLowStockInventory().stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getOverstockedInventory() {
        return inventoryRepository.findOverstockedInventory().stream().map(this::mapToDto).toList();
    }

    @Transactional
    public InventoryDto adjustStock(Long productId, Long warehouseId, Integer adjustmentQty) {
        Inventory inv = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
                    Warehouse warehouse = warehouseRepository.findById(warehouseId)
                            .orElseThrow(() -> new RuntimeException("Warehouse not found: " + warehouseId));
                    return Inventory.builder()
                            .product(product)
                            .warehouse(warehouse)
                            .quantityAvailable(0)
                            .reservedQuantity(0)
                            .reorderLevel(product.getReorderLevel())
                            .safetyStock(product.getSafetyStock())
                            .build();
                });

        int newQty = Math.max(0, inv.getQuantityAvailable() + adjustmentQty);
        inv.setQuantityAvailable(newQty);
        if (adjustmentQty > 0) {
            inv.setLastRestockedAt(LocalDateTime.now());
        }

        return mapToDto(inventoryRepository.save(inv));
    }

    private InventoryDto mapToDto(Inventory inv) {
        String status = "OPTIMAL";
        if (inv.getQuantityAvailable() <= inv.getSafetyStock()) {
            status = "CRITICAL";
        } else if (inv.getQuantityAvailable() <= inv.getReorderLevel()) {
            status = "LOW_STOCK";
        } else if (inv.getQuantityAvailable() > inv.getReorderLevel() * 3) {
            status = "OVERSTOCK";
        }

        return InventoryDto.builder()
                .id(inv.getId())
                .productId(inv.getProduct().getId())
                .productSku(inv.getProduct().getSku())
                .productName(inv.getProduct().getName())
                .productPrice(inv.getProduct().getPrice())
                .warehouseId(inv.getWarehouse().getId())
                .warehouseCode(inv.getWarehouse().getCode())
                .warehouseName(inv.getWarehouse().getName())
                .quantityAvailable(inv.getQuantityAvailable())
                .reservedQuantity(inv.getReservedQuantity())
                .reorderLevel(inv.getReorderLevel())
                .safetyStock(inv.getSafetyStock())
                .lastRestockedAt(inv.getLastRestockedAt())
                .status(status)
                .build();
    }
}
