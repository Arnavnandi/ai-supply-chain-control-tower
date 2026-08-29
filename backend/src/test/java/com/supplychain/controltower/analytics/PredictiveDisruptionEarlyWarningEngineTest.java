package com.supplychain.controltower.analytics;

import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.service.DisruptionMitigationPolicyEngine;
import com.supplychain.controltower.service.TelemetryEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictiveDisruptionEarlyWarningEngineTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DisruptionMitigationPolicyEngine policyEngine;

    @Mock
    private TelemetryEventPublisher telemetryPublisher;

    @InjectMocks
    private PredictiveDisruptionEarlyWarningEngine engine;

    @Test
    void testScanAndPredictEarlyWarningsWithSupplierAnomaly() {
        Supplier supplier = new Supplier();
        supplier.setId(10L);
        supplier.setName("Acme Chipsets");
        supplier.setCode("SUP-ACME-001");
        supplier.setReliabilityScore(new BigDecimal("70.0"));

        when(supplierRepository.findAll()).thenReturn(List.of(supplier));
        when(inventoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(warehouseRepository.findAll()).thenReturn(Collections.emptyList());

        PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport report =
                engine.scanAndPredictEarlyWarnings(false);

        assertNotNull(report);
        assertTrue(report.getTotalAnomaliesDetected() >= 1);
        assertTrue(report.getHighestFailureProbability() > 0.5);
        assertFalse(report.getEarlyWarnings().isEmpty());

        PredictiveDisruptionEarlyWarningEngine.PredictiveEarlyWarningNode warn = report.getEarlyWarnings().get(0);
        assertEquals("SUPPLIER", warn.getDomain());
        assertEquals("SUP-ACME-001", warn.getTargetEntity());
        assertEquals(4, warn.getEstimatedDaysToImpact());
        assertTrue(warn.getAnomalyExplanation().contains("Acme Chipsets"));

        ArgumentCaptor<TelemetryEvent> eventCaptor = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(telemetryPublisher, times(1)).publish(eventCaptor.capture());
        TelemetryEvent published = eventCaptor.getValue();
        assertEquals(TelemetryEvent.EventType.STOCKOUT_ALERT, published.getEventType());
        assertEquals(TelemetryEvent.Severity.WARNING, published.getSeverity());
    }

    @Test
    void testScanAndPredictEarlyWarningsWithActionProposals() {
        Supplier supplier = new Supplier();
        supplier.setId(10L);
        supplier.setCode("SUP-ACME-001");
        supplier.setReliabilityScore(new BigDecimal("60.0"));

        when(supplierRepository.findAll()).thenReturn(List.of(supplier));

        DisruptionMitigationPolicyEngine.MitigationPolicyResult polRes =
                DisruptionMitigationPolicyEngine.MitigationPolicyResult.builder()
                        .recommendationId(205L)
                        .policyDecision("PROACTIVE_FAILOVER")
                        .build();

        when(policyEngine.evaluateAndMitigate(any(), any(), eq(true))).thenReturn(polRes);

        PredictiveDisruptionEarlyWarningEngine.EarlyWarningRadarReport report =
                engine.scanAndPredictEarlyWarnings(true);

        assertNotNull(report);
        assertTrue(report.isProactiveProposalsGenerated());
        assertTrue(report.getGeneratedRecommendationIds().contains(205L));
    }
}
