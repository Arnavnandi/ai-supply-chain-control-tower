package com.supplychain.controltower.integration;

import com.supplychain.controltower.analytics.AutoContainmentFailoverEngine;
import com.supplychain.controltower.analytics.ExecutiveCommandCenterEngine;
import com.supplychain.controltower.analytics.MultiEchelonInventoryRebalancingEngine;
import com.supplychain.controltower.analytics.UnifiedDisruptionOrchestratorEngine;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionResilienceIntegrationTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private AutoContainmentFailoverEngine failoverEngine;

    @InjectMocks
    private MultiEchelonInventoryRebalancingEngine rebalanceEngine;

    @Test
    void testFailoverEngineResilienceWithNullAndEmptyInputs() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        when(supplierRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());

        AutoContainmentFailoverEngine.ContainmentFailoverReport report =
                failoverEngine.computeFailoverContainmentPlan(null, null);

        assertNotNull(report);
        assertEquals("INSUFFICIENT_DATA", report.getContainmentStatus());
        assertEquals(0.0, report.getPrimaryAllocationPct());
        assertEquals(0.0, report.getFallbackAllocationPct());
        assertTrue(report.getAllocations().isEmpty());
        assertFalse(Double.isNaN(report.getAlternateWarehouseAvailableCapacityUnits()));
        assertFalse(Double.isInfinite(report.getAlternateWarehouseAvailableCapacityUnits()));
    }

    @Test
    void testMultiEchelonRebalanceResilienceWithBlankInputs() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());

        MultiEchelonInventoryRebalancingEngine.RebalancingReport report =
                rebalanceEngine.computeMultiEchelonRebalancePlan("   ", "");

        assertNotNull(report);
        assertEquals("INSUFFICIENT_DATA", report.getRebalancingStatus());
        assertEquals(0, report.getTotalRebalancedUnits());
        assertTrue(report.getTransferOptions().isEmpty());
        assertFalse(Double.isNaN(report.getTotalCostSavingsVsNewPurchase()));
        assertFalse(Double.isInfinite(report.getTotalCostSavingsVsNewPurchase()));
    }
}
