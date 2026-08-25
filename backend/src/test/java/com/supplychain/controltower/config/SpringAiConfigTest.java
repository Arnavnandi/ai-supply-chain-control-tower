package com.supplychain.controltower.config;

import com.supplychain.controltower.ai.tools.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpringAiConfigTest {

    private InventoryTools inventoryTools;
    private SupplierTools supplierTools;
    private LogisticsTools logisticsTools;
    private WarehouseTools warehouseTools;
    private AnalyticsTools analyticsTools;
    private SpringAiConfig config;

    @BeforeEach
    void setUp() {
        inventoryTools = mock(InventoryTools.class);
        supplierTools = mock(SupplierTools.class);
        logisticsTools = mock(LogisticsTools.class);
        warehouseTools = mock(WarehouseTools.class);
        analyticsTools = mock(AnalyticsTools.class);
        config = new SpringAiConfig();
    }

    @Test
    void testGetLowStockProductsFunctionRegistration() {
        when(inventoryTools.getLowStockProducts()).thenReturn(List.of(
                new InventoryTools.InventoryItemRecord(1L, "SKU-001", "Test Product", "WH-North", 10, 50, 20)
        ));

        var function = config.getLowStockProducts(inventoryTools);
        assertNotNull(function);

        var result = function.apply(new SpringAiConfig.EmptyRequest());
        assertEquals(1, result.size());
        assertEquals("SKU-001", result.get(0).sku());
        verify(inventoryTools, times(1)).getLowStockProducts();
    }

    @Test
    void testGetSupplierPerformanceFunctionRegistration() {
        when(supplierTools.getSupplierPerformance()).thenReturn(List.of());

        var function = config.getSupplierPerformance(supplierTools);
        assertNotNull(function);

        var result = function.apply(new SpringAiConfig.EmptyRequest());
        assertTrue(result.isEmpty());
        verify(supplierTools, times(1)).getSupplierPerformance();
    }

    @Test
    void testGetDelayedShipmentsFunctionRegistration() {
        when(logisticsTools.getDelayedShipments()).thenReturn(List.of());

        var function = config.getDelayedShipments(logisticsTools);
        assertNotNull(function);

        var result = function.apply(new SpringAiConfig.EmptyRequest());
        assertTrue(result.isEmpty());
        verify(logisticsTools, times(1)).getDelayedShipments();
    }
}
