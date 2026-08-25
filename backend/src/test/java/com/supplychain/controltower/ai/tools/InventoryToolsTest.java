package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryToolsTest {

    private InventoryRepository inventoryRepository;
    private InventoryTools inventoryTools;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        inventoryTools = new InventoryTools(inventoryRepository);
    }

    @Test
    void testGetLowStockProducts() {
        Product p = Product.builder().id(1L).sku("SKU-LOW-01").name("Low Stock Sensor").build();
        Warehouse w = Warehouse.builder().name("North Hub").build();
        Inventory inv = Inventory.builder()
                .product(p)
                .warehouse(w)
                .quantityAvailable(15)
                .reorderLevel(100)
                .safetyStock(30)
                .build();

        when(inventoryRepository.findLowStockInventory()).thenReturn(List.of(inv));

        List<InventoryTools.InventoryItemRecord> results = inventoryTools.getLowStockProducts();
        assertEquals(1, results.size());
        assertEquals("SKU-LOW-01", results.get(0).sku());
        assertEquals("North Hub", results.get(0).warehouse());
        assertEquals(15, results.get(0).availableQty());
        verify(inventoryRepository, times(1)).findLowStockInventory();
    }

    @Test
    void testGetOverstockedProducts() {
        Product p = Product.builder().id(2L).sku("SKU-OVER-02").name("Overstocked Crate").build();
        Warehouse w = Warehouse.builder().name("South Hub").build();
        Inventory inv = Inventory.builder()
                .product(p)
                .warehouse(w)
                .quantityAvailable(3000)
                .reorderLevel(500)
                .safetyStock(200)
                .build();

        when(inventoryRepository.findOverstockedInventory()).thenReturn(List.of(inv));

        List<InventoryTools.InventoryItemRecord> results = inventoryTools.getOverstockedProducts();
        assertEquals(1, results.size());
        assertEquals("SKU-OVER-02", results.get(0).sku());
        assertEquals(3000, results.get(0).availableQty());
        verify(inventoryRepository, times(1)).findOverstockedInventory();
    }
}
