package com.supplychain.controltower.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

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

    @InjectMocks
    private UnifiedDisruptionOrchestratorEngine orchestratorEngine;

    @Test
    void testGenerateMasterOrchestrationPlanNormalFlow() {
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
}
