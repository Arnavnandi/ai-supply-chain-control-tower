package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.TelemetryEventEntity;
import com.supplychain.controltower.repository.TelemetryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DisruptionMitigationPolicyEngineTest {

    @Autowired
    private DisruptionMitigationPolicyEngine policyEngine;

    @Autowired
    private TelemetryEventRepository telemetryEventRepository;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    void testSupplierDisruptionMitigationPolicy() {
        var result = policyEngine.evaluateAndMitigate(
                DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION, "SUP-ELEC-001");

        assertNotNull(result);
        assertNotNull(result.getSimulationId());
        assertEquals(DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION, result.getDisruptionType());
        assertEquals("ACTIVATE_SECONDARY_SUPPLIER_FAILOVER", result.getPolicyDecision());
        assertEquals(DisruptionMitigationPolicyEngine.ExecutionMode.RECOMMENDATION_ONLY, result.getExecutionMode());
        assertTrue(result.isTelemetryPublished());
        assertFalse(result.getRecommendedActions().isEmpty());
        assertTrue(result.getRecommendedActions().stream().anyMatch(a -> a.contains("backup vendor contract")));
    }

    @Test
    void testInventoryShortageMitigationPolicy() {
        var result = policyEngine.evaluateAndMitigate(
                DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE, "SKU-ELEC-001");

        assertNotNull(result);
        assertEquals(DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE, result.getDisruptionType());
        assertEquals("EXPEDITE_REPLENISHMENT_AND_REBALANCE", result.getPolicyDecision());
        assertEquals(DisruptionMitigationPolicyEngine.ExecutionMode.RECOMMENDATION_ONLY, result.getExecutionMode());
        assertTrue(result.isTelemetryPublished());
        assertFalse(result.getRecommendedActions().isEmpty());
        assertTrue(result.getRecommendedActions().stream().anyMatch(a -> a.contains("expedited purchase order")));
    }

    @Test
    void testLogisticsDelayMitigationPolicy() {
        var result = policyEngine.evaluateAndMitigate(
                DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY, "Stuttgart to Oakland");

        assertNotNull(result);
        assertEquals(DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY, result.getDisruptionType());
        assertEquals("CARRIER_REROUTE_AND_AIR_CARGO_ESCALATION", result.getPolicyDecision());
        assertEquals(DisruptionMitigationPolicyEngine.ExecutionMode.RECOMMENDATION_ONLY, result.getExecutionMode());
        assertTrue(result.isTelemetryPublished());
        assertFalse(result.getRecommendedActions().isEmpty());
        assertTrue(result.getRecommendedActions().stream().anyMatch(a -> a.contains("air cargo carrier")));
    }

    @Test
    void testWarehouseCapacityOverrunMitigationPolicy() {
        var result = policyEngine.evaluateAndMitigate(
                DisruptionSimulationService.DisruptionType.WAREHOUSE_CAPACITY_OVERRUN, "WH-WEST");

        assertNotNull(result);
        assertEquals(DisruptionSimulationService.DisruptionType.WAREHOUSE_CAPACITY_OVERRUN, result.getDisruptionType());
        assertEquals("INTER_HUB_STOCK_TRANSFER_AND_RECEIPT_DEFERRAL", result.getPolicyDecision());
        assertEquals(DisruptionMitigationPolicyEngine.ExecutionMode.RECOMMENDATION_ONLY, result.getExecutionMode());
        assertTrue(result.isTelemetryPublished());
        assertFalse(result.getRecommendedActions().isEmpty());
        assertTrue(result.getRecommendedActions().stream().anyMatch(a -> a.contains("inter-hub inventory rebalance")));
    }

    @Test
    void testRiskBandClassificationAndCorrelationIdPropagation() {
        String testCorrelationId = "corr-test-policy-mitigation-888";
        MDC.put("correlationId", testCorrelationId);

        try {
            var result = policyEngine.evaluateAndMitigate(
                    DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE, "SKU-TEST-CORR");

            assertNotNull(result);
            assertNotNull(result.getRiskBand());

            List<TelemetryEventEntity> events = telemetryEventRepository.findAll();
            boolean foundCorrelation = events.stream()
                    .anyMatch(e -> testCorrelationId.equals(e.getCorrelationId()));

            assertTrue(foundCorrelation, "Policy mitigation telemetry must inherit correlation ID from MDC context");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void testNullAndEmptyTargetEntityHandling() {
        var result = policyEngine.evaluateAndMitigate(
                DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE, null);

        assertNotNull(result);
        assertEquals("DEFAULT-TARGET", result.getTargetEntity());
        assertNotNull(result.getPolicyDecision());
        assertEquals(DisruptionMitigationPolicyEngine.ExecutionMode.RECOMMENDATION_ONLY, result.getExecutionMode());
    }
}
