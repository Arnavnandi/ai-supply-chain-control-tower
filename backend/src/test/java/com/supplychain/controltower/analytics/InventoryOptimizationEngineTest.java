package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.service.ForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryOptimizationEngineTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ForecastService forecastService;

    @InjectMocks
    private InventoryOptimizationEngine optimizationEngine;

    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(1L).sku("SKU-OPT-001").name("Control Board").leadTimeDays(7).safetyStock(50).reorderLevel(150).build();
        inventory = Inventory.builder().id(10L).product(product).quantityAvailable(30).safetyStock(50).reorderLevel(150).build();
    }

    @Test
    void testOptimizeSafetyStockLevelsCalculatesDynamicSafetyStock() {
        when(inventoryRepository.findAll()).thenReturn(List.of(inventory));
        when(forecastService.calculateMonthlySalesFromDatabase(1L)).thenReturn(List.of(100, 120, 140, 110, 130, 150));

        InventoryOptimizationEngine.SafetyStockOptimizationReport report = optimizationEngine.optimizeSafetyStockLevels();

        assertNotNull(report);
        assertEquals(1, report.getTotalItemsEvaluated());
        assertEquals(1, report.getOptimizedItems().size());
        assertTrue(report.getOptimizedItems().get(0).getCalculatedDynamicSafetyStock() > 0);
    }
}
