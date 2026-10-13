package com.supplychain.controltower.service;

import com.supplychain.controltower.ai.agents.SupervisorAgent;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisruptionSimulationServiceTest {

    @Mock
    private SupervisorAgent supervisorAgent;

    @Mock
    private TelemetryEventPublisher telemetryEventPublisher;

    @InjectMocks
    private DisruptionSimulationService simulationService;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(42L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSimulateDisruptionWithTenantEntity() {
        SupervisorAgent.SupervisorConsensusResponse mockConsensus =
                SupervisorAgent.SupervisorConsensusResponse.builder()
                        .consensusDecision("Mitigate supplier disruption")
                        .overallSystemRiskScore(75.0)
                        .domainFindings(Collections.emptyList())
                        .build();

        when(supervisorAgent.processMultiAgentQuery(anyString())).thenReturn(mockConsensus);

        DisruptionSimulationService.DisruptionSimulationResult result =
                simulationService.simulateDisruption(DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION, "SUP-TENANT-99");

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertTrue(result.getScenarioDescription().contains("SUP-TENANT-99"));
        assertFalse(result.getScenarioDescription().contains("SUP-ELEC-001"));

        ArgumentCaptor<TelemetryEvent> eventCaptor = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(telemetryEventPublisher).publish(eventCaptor.capture());
        assertEquals("SUP-TENANT-99", eventCaptor.getValue().getEntityId());
    }

    @Test
    void testSimulateDisruptionWithNullEntityDoesNotInventDemoCodes() {
        SupervisorAgent.SupervisorConsensusResponse mockConsensus =
                SupervisorAgent.SupervisorConsensusResponse.builder()
                        .consensusDecision("Evaluate generic inventory shortage")
                        .overallSystemRiskScore(60.0)
                        .domainFindings(Collections.emptyList())
                        .build();

        when(supervisorAgent.processMultiAgentQuery(anyString())).thenReturn(mockConsensus);

        DisruptionSimulationService.DisruptionSimulationResult result =
                simulationService.simulateDisruption(DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE, null);

        assertNotNull(result);
        assertFalse(result.getScenarioDescription().contains("SKU-ELEC-001"));
        assertTrue(result.getScenarioDescription().contains("the designated item"));
    }
}
