package com.supplychain.controltower.integration;

import com.supplychain.controltower.entity.TelemetryEventEntity;
import com.supplychain.controltower.repository.TelemetryEventRepository;
import com.supplychain.controltower.service.DisruptionSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EndToEndSupplyChainDisruptionTest {

    @Autowired
    private DisruptionSimulationService disruptionSimulationService;

    @Autowired
    private TelemetryEventRepository telemetryEventRepository;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    void testSupplierDisruptionScenario() {
        var result = disruptionSimulationService.simulateDisruption(
                DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION, "SUP-ELEC-001");

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertTrue(result.isTelemetryPublished());
        assertNotNull(result.getConsensusSynthesis());
        assertFalse(result.getConsensusSynthesis().getDomainFindings().isEmpty());
    }

    @Test
    void testInventoryShortageScenario() {
        var result = disruptionSimulationService.simulateDisruption(
                DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE, "SKU-ELEC-001");

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getConsensusSynthesis());
        assertTrue(result.getConsensusSynthesis().getPrioritizedMitigationActions().size() > 0);
    }

    @Test
    void testLogisticsDisruptionScenario() {
        var result = disruptionSimulationService.simulateDisruption(
                DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY, "Stuttgart to Oakland");

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getConsensusSynthesis());
    }

    @Test
    void testWarehouseDisruptionScenario() {
        var result = disruptionSimulationService.simulateDisruption(
                DisruptionSimulationService.DisruptionType.WAREHOUSE_CAPACITY_OVERRUN, "WH-WEST");

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getConsensusSynthesis());
    }

    @Test
    void testCorrelationIdAndTelemetryPropagation() {
        String testCorrelationId = "corr-test-disruption-999";
        MDC.put("correlationId", testCorrelationId);

        try {
            var result = disruptionSimulationService.simulateDisruption(
                    DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE, "SKU-TEST-CORR");

            assertNotNull(result);

            List<TelemetryEventEntity> events = telemetryEventRepository.findAll();
            boolean foundCorrelation = events.stream()
                    .anyMatch(e -> testCorrelationId.equals(e.getCorrelationId()));

            assertTrue(foundCorrelation, "Telemetry event must inherit correlation ID from MDC context");
        } finally {
            MDC.clear();
        }
    }
}
