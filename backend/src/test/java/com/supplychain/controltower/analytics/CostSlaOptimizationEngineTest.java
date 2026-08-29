package com.supplychain.controltower.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CostSlaOptimizationEngineTest {

    @InjectMocks
    private CostSlaOptimizationEngine engine;

    @Test
    void testEvaluateCostSlaTradeoff() {
        CostSlaOptimizationEngine.CostSlaTradeoffReport report =
                engine.evaluateCostSlaTradeoff("INVENTORY_SHORTAGE", "SKU-ELEC-001");

        assertNotNull(report);
        assertEquals("INVENTORY_SHORTAGE", report.getTargetDisruptionType());
        assertEquals("SKU-ELEC-001", report.getTargetEntity());
        assertEquals("OPT-EXPEDITED-AIR", report.getOptimalStrategyId());
        assertEquals(3, report.getTradeoffs().size());

        CostSlaOptimizationEngine.MitigationOptionTradeoff optA = report.getTradeoffs().get(0);
        assertTrue(optA.isRecommendedChoice());
        assertTrue(optA.getEstimatedCostUsd() > 0);
        assertTrue(optA.getSlaCustomerProtectionPct() > 90.0);
    }
}
