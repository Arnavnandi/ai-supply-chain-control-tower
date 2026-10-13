package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedDisruptionOrchestratorEngineTest {

    @Mock
    private AutoContainmentFailoverEngine failoverEngine;

    @Mock
    private MultiEchelonInventoryRebalancingEngine rebalanceEngine;

    @Mock
    private CostSlaOptimizationEngine costSlaEngine;

    @Mock
    private PredictiveDisruptionEarlyWarningEngine earlyWarningEngine;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private UnifiedDisruptionOrchestratorEngine orchestratorEngine;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testGenerateMasterOrchestrationPlanNormalFlow() {
        Supplier s = Supplier.builder().id(1L).code("SUP-TECH-001").name("Tech Supplier").build();
        Warehouse w = Warehouse.builder().id(1L).code("WH-NORTH").name("North Warehouse").build();
        Product p = Product.builder().id(1L).sku("SKU-ELEC-001").name("Microcontroller").build();

        when(supplierRepository.findByCompanyId(1L)).thenReturn(List.of(s));
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(List.of(w));
        when(productRepository.findByCompanyId(1L)).thenReturn(List.of(p));

        AutoContainmentFailoverEngine.ContainmentFailoverReport failoverReport =
                AutoContainmentFailoverEngine.ContainmentFailoverReport.builder()
                        .containmentPlanId("FAILOVER-001")
                        .containmentStatus("CONTAINED")
                        .primaryAllocationPct(60.0)
                        .fallbackAllocationPct(40.0)
                        .allocations(Collections.emptyList())
                        .build();

        MultiEchelonInventoryRebalancingEngine.RebalancingReport rebalanceReport =
                MultiEchelonInventoryRebalancingEngine.RebalancingReport.builder()
                        .rebalancePlanId("REBALANCE-001")
                        .rebalancingStatus("BALANCED")
                        .totalRebalancedUnits(350)
                        .transferOptions(Collections.emptyList())
                        .build();

        CostSlaOptimizationEngine.CostSlaTradeoffReport costSlaReport =
                CostSlaOptimizationEngine.CostSlaTradeoffReport.builder()
                        .analysisId("TRADE-001")
                        .tradeoffs(Collections.emptyList())
                        .build();

        PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport radarReport =
                PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport.builder()
                        .scanId("RADAR-001")
                        .totalAnomaliesDetected(1)
                        .earlyWarnings(Collections.emptyList())
                        .build();

        when(failoverEngine.computeFailoverContainmentPlan(anyString(), anyString())).thenReturn(failoverReport);
        when(rebalanceEngine.computeMultiEchelonRebalancePlan(anyString(), anyString())).thenReturn(rebalanceReport);
        when(costSlaEngine.evaluateCostSlaTradeoff(anyString(), anyString())).thenReturn(costSlaReport);
        when(earlyWarningEngine.scanAndPredictEarlyWarnings(anyBoolean())).thenReturn(radarReport);

        UnifiedDisruptionOrchestratorEngine.MasterOrchestrationReport report =
                orchestratorEngine.generateMasterOrchestrationPlan("SUP-TECH-001", "WH-NORTH");

        assertNotNull(report);
        assertEquals("SUP-TECH-001", report.getPrimaryTargetEntity());
        assertEquals("READY_FOR_MANAGER_APPROVAL", report.getOrchestrationStatus());
        assertNotNull(report.getFailoverContainment());
        assertNotNull(report.getMultiEchelonRebalance());
        assertNotNull(report.getCostSlaOptimization());
        assertNotNull(report.getPredictiveEarlyWarningScan());
        assertTrue(report.getOverallSystemicRiskScore() > 0);
        assertNotNull(report.getMasterExecutiveSummary());
    }

    @Test
    void testGenerateMasterOrchestrationPlan_InsufficientDataWhenEmpty() {
        when(supplierRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(productRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());

        UnifiedDisruptionOrchestratorEngine.MasterOrchestrationReport report =
                orchestratorEngine.generateMasterOrchestrationPlan(null, null);

        assertNotNull(report);
        assertEquals("INSUFFICIENT_DATA", report.getOrchestrationStatus());
        assertEquals(0.0, report.getOverallSystemicRiskScore());
        assertTrue(report.getMasterExecutiveSummary().contains("Insufficient operational database records"));
    }
}
