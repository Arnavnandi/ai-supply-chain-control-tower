package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class MultiEchelonInventoryRebalancingEngineTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private com.supplychain.controltower.repository.ProductRepository productRepository;

    @InjectMocks
    private MultiEchelonInventoryRebalancingEngine engine;

    @Test
    void testComputeMultiEchelonRebalancePlanNormalFlow() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        Warehouse w1 = new Warehouse();
        w1.setCode("WH-NORTH");
        w1.setName("Northern Logistics Hub");

        Warehouse w2 = new Warehouse();
        w2.setCode("WH-SOUTH");
        w2.setName("Southern Distribution Hub");
        w2.setTotalCapacityUnits(100000);
        w2.setUtilizationPercentage(new BigDecimal("40.0"));

        Warehouse w3 = new Warehouse();
        w3.setCode("WH-EAST");
        w3.setName("Eastern Freight Hub");
        w3.setTotalCapacityUnits(80000);
        w3.setUtilizationPercentage(new BigDecimal("30.0"));

        when(warehouseRepository.findByCompanyId(1L)).thenReturn(List.of(w1, w2, w3));
        when(productRepository.findByCompanyId(1L)).thenReturn(List.of(com.supplychain.controltower.entity.Product.builder().id(1L).sku("SKU-ELEC-001").name("Microcontroller").build()));

        MultiEchelonInventoryRebalancingEngine.RebalancingReport report =
                engine.computeMultiEchelonRebalancePlan("WH-NORTH", "SKU-ELEC-001");

        assertNotNull(report);
        assertEquals("WH-NORTH", report.getTargetWarehouseCode());
        assertEquals("SKU-ELEC-001", report.getSkuCode());
        assertEquals("BALANCED", report.getRebalancingStatus());
        assertEquals(350, report.getDeficitUnits());
        assertEquals(350, report.getTotalRebalancedUnits());
        assertFalse(report.getTransferOptions().isEmpty());

        MultiEchelonInventoryRebalancingEngine.InterHubTransferOption transfer = report.getTransferOptions().get(0);
        assertEquals("WH-SOUTH", transfer.getSourceWarehouseCode());
        assertEquals("WH-NORTH", transfer.getTargetWarehouseCode());
        assertEquals(250, transfer.getTransferQuantityUnits());
        assertNotNull(report.getExecutiveSummary());
        com.supplychain.controltower.security.TenantContext.clear();
    }

    @Test
    void testComputeMultiEchelonRebalancePlanWithEmptyDatabase() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());

        MultiEchelonInventoryRebalancingEngine.RebalancingReport report =
                engine.computeMultiEchelonRebalancePlan("WH-NORTH", "SKU-ELEC-001");

        assertNotNull(report);
        assertEquals("INSUFFICIENT_DATA", report.getRebalancingStatus());
        assertEquals(0, report.getTotalRebalancedUnits());
        assertTrue(report.getTransferOptions().isEmpty());
        com.supplychain.controltower.security.TenantContext.clear();
    }
}
