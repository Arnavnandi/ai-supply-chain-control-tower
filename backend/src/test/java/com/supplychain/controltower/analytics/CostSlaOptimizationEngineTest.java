package com.supplychain.controltower.analytics;

import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CostSlaOptimizationEngineTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private CostSlaOptimizationEngine engine;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

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
