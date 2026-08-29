package com.supplychain.controltower.analytics;

import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.service.DisruptionMitigationPolicyEngine;
import com.supplychain.controltower.service.DisruptionSimulationService;
import com.supplychain.controltower.service.TelemetryEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CascadingDisruptionCorrelationEngineTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private DisruptionMitigationPolicyEngine policyEngine;

    @Mock
    private TelemetryEventPublisher telemetryPublisher;

    @InjectMocks
    private CascadingDisruptionCorrelationEngine engine;

    @Test
    void testAnalyzeCascadingDisruptionSupplierDisruption() {
        CascadingDisruptionCorrelationEngine.CascadingDisruptionResult result =
                engine.analyzeCascadingDisruption("SUPPLIER_DISRUPTION", "SUP-TECH-001", false);

        assertNotNull(result);
        assertEquals("SUPPLIER_DISRUPTION", result.getPrimaryDisruption());
        assertEquals("SUP-TECH-001", result.getPrimaryTarget());
        assertTrue(result.getCumulativeRiskScore() > 60.0);
        assertEquals(3, result.getImpactedDomainsCount());
        assertEquals(3, result.getCascadeNodes().size());

        CascadingDisruptionCorrelationEngine.CascadeNode node0 = result.getCascadeNodes().get(0);
        assertEquals(0, node0.getHopLevel());
        assertEquals("SUPPLIER", node0.getDomain());
        assertEquals("SUP-TECH-001", node0.getTargetEntity());

        CascadingDisruptionCorrelationEngine.CascadeNode node1 = result.getCascadeNodes().get(1);
        assertEquals(1, node1.getHopLevel());
        assertEquals("INVENTORY", node1.getDomain());

        ArgumentCaptor<TelemetryEvent> eventCaptor = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(telemetryPublisher, times(1)).publish(eventCaptor.capture());
        TelemetryEvent published = eventCaptor.getValue();
        assertEquals(TelemetryEvent.EventType.STOCKOUT_ALERT, published.getEventType());
        assertTrue(published.getMessage().contains("[DISRUPTION CASCADE DETECTED]"));
    }

    @Test
    void testAnalyzeCascadingDisruptionWithActionProposals() {
        DisruptionMitigationPolicyEngine.MitigationPolicyResult polRes =
                DisruptionMitigationPolicyEngine.MitigationPolicyResult.builder()
                        .recommendationId(101L)
                        .policyDecision("TEST_DECISION")
                        .build();

        when(policyEngine.evaluateAndMitigate(any(), any(), eq(true))).thenReturn(polRes);

        CascadingDisruptionCorrelationEngine.CascadingDisruptionResult result =
                engine.analyzeCascadingDisruption("INVENTORY_SHORTAGE", "SKU-ELEC-001", true);

        assertNotNull(result);
        assertTrue(result.isChainedActionProposalsCreated());
        assertFalse(result.getGeneratedRecommendationIds().isEmpty());
        verify(policyEngine, times(3)).evaluateAndMitigate(any(), any(), eq(true));
    }
}
