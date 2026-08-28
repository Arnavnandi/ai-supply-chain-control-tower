package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.service.RagRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupervisorAgentTest {

    @Mock
    private InventoryAgent inventoryAgent;
    @Mock
    private SupplierAgent supplierAgent;
    @Mock
    private LogisticsAgent logisticsAgent;
    @Mock
    private WarehouseAgent warehouseAgent;
    @Mock
    private RiskAgent riskAgent;
    @Mock
    private RagRetrievalService ragRetrievalService;
    @Mock
    private ChatClient chatClient;

    @InjectMocks
    private SupervisorAgent supervisorAgent;

    @BeforeEach
    void setUp() {
        when(inventoryAgent.processQuery(anyString())).thenReturn("Inventory Analysis: 2 low stock SKUs.");
        when(supplierAgent.processQuery(anyString())).thenReturn("Supplier Analysis: High risk supplier detected.");
        when(logisticsAgent.processQuery(anyString())).thenReturn("Logistics Analysis: 1 shipment delayed.");
        when(warehouseAgent.processQuery(anyString())).thenReturn("Warehouse Analysis: Utilization at 82%.");
        when(riskAgent.processQuery(anyString())).thenReturn("Risk Analysis: Overall risk moderate.");
    }

    @Test
    void processMultiAgentQuery_ShouldDelegateToSelectedDomainAgents() {
        SupervisorAgent.SupervisorConsensusResponse response = supervisorAgent.processMultiAgentQuery("What are the stockout and shipment delay risks?");

        assertNotNull(response);
        assertEquals("What are the stockout and shipment delay risks?", response.getOriginalQuery());
        assertTrue(response.getParticipatingAgents().contains("INVENTORY"));
        assertTrue(response.getParticipatingAgents().contains("LOGISTICS"));
        assertFalse(response.getDomainFindings().isEmpty());
        assertNotNull(response.getConsensusDecision());
    }

    @Test
    void processMultiAgentQuery_ShouldHandleDomainAgentFailuresGracefully() {
        when(inventoryAgent.processQuery(anyString())).thenThrow(new RuntimeException("Simulated Inventory DB failure"));

        SupervisorAgent.SupervisorConsensusResponse response = supervisorAgent.processMultiAgentQuery("Check stock and suppliers");

        assertNotNull(response);
        assertTrue(response.isOfflineFallbackActive());
        assertNotNull(response.getSupervisorSynthesis());
        assertTrue(response.getDomainFindings().stream().anyMatch(SupervisorAgent.DomainFinding::isFallbackUsed));
    }

    @Test
    void processMultiAgentQuery_ShouldExecuteOfflineFallbackWhenLLMUnavailable() {
        SupervisorAgent.SupervisorConsensusResponse response = supervisorAgent.processMultiAgentQuery("Comprehensive supply chain disruption audit");

        assertNotNull(response);
        assertTrue(response.getSupervisorSynthesis().contains("Executive Multi-Agent Collaborative Control Tower Synthesis"));
        assertNotNull(response.getPrioritizedMitigationActions());
        assertFalse(response.getPrioritizedMitigationActions().isEmpty());
    }
}
