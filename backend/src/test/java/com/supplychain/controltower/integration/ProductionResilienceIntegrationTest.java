package com.supplychain.controltower.integration;

import com.supplychain.controltower.analytics.AutoContainmentFailoverEngine;
import com.supplychain.controltower.analytics.ExecutiveCommandCenterEngine;
import com.supplychain.controltower.analytics.MultiEchelonInventoryRebalancingEngine;
import com.supplychain.controltower.analytics.UnifiedDisruptionOrchestratorEngine;
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

    @InjectMocks
    private AutoContainmentFailoverEngine failoverEngine;

    @InjectMocks
    private MultiEchelonInventoryRebalancingEngine rebalanceEngine;

    @Test
    void testFailoverEngineResilienceWithNullAndEmptyInputs() {
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());
        when(warehouseRepository.findAll()).thenReturn(Collections.emptyList());

        AutoContainmentFailoverEngine.ContainmentFailoverReport report =
                failoverEngine.computeFailoverContainmentPlan(null, null);

        assertNotNull(report);
        assertEquals("CONTAINED", report.getContainmentStatus());
        assertEquals(60.0, report.getPrimaryAllocationPct());
        assertEquals(40.0, report.getFallbackAllocationPct());
        assertFalse(Double.isNaN(report.getAlternateWarehouseAvailableCapacityUnits()));
        assertFalse(Double.isInfinite(report.getAlternateWarehouseAvailableCapacityUnits()));
    }

    @Test
    void testMultiEchelonRebalanceResilienceWithBlankInputs() {
        when(warehouseRepository.findAll()).thenReturn(Collections.emptyList());

        MultiEchelonInventoryRebalancingEngine.RebalancingReport report =
                rebalanceEngine.computeMultiEchelonRebalancePlan("   ", "");

        assertNotNull(report);
        assertEquals("BALANCED", report.getRebalancingStatus());
        assertEquals(350, report.getTotalRebalancedUnits());
        assertFalse(Double.isNaN(report.getTotalCostSavingsVsNewPurchase()));
        assertFalse(Double.isInfinite(report.getTotalCostSavingsVsNewPurchase()));
    }
}
