package com.supplychain.controltower.controller;

import com.supplychain.controltower.dto.domain.InventoryDto;
import com.supplychain.controltower.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<List<InventoryDto>> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryDto>> getLowStockInventory() {
        return ResponseEntity.ok(inventoryService.getLowStockInventory());
    }

    @GetMapping("/overstock")
    public ResponseEntity<List<InventoryDto>> getOverstockedInventory() {
        return ResponseEntity.ok(inventoryService.getOverstockedInventory());
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPLY_CHAIN_MANAGER', 'INVENTORY_MANAGER')")
    public ResponseEntity<InventoryDto> adjustStock(@RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(body.get("productId").toString());
        Long warehouseId = Long.valueOf(body.get("warehouseId").toString());
        Integer adjustmentQty = Integer.valueOf(body.get("adjustmentQty").toString());

        return ResponseEntity.ok(inventoryService.adjustStock(productId, warehouseId, adjustmentQty));
    }
}
