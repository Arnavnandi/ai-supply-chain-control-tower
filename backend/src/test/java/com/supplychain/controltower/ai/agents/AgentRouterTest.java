package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AgentRouterTest {

    private InventoryTools inventoryTools;
    private SupplierTools supplierTools;
    private LogisticsTools logisticsTools;
    private WarehouseTools warehouseTools;
    private AnalyticsTools analyticsTools;
    private ChatClient chatClient;

    private AgentRouter agentRouter;

    @BeforeEach
    void setUp() {
        inventoryTools = Mockito.mock(InventoryTools.class);
        supplierTools = Mockito.mock(SupplierTools.class);
        logisticsTools = Mockito.mock(LogisticsTools.class);
        warehouseTools = Mockito.mock(WarehouseTools.class);
        analyticsTools = Mockito.mock(AnalyticsTools.class);
        chatClient = Mockito.mock(ChatClient.class);

        when(inventoryTools.getLowStockProducts()).thenReturn(List.of());
        when(inventoryTools.getOverstockedProducts()).thenReturn(List.of());
        when(supplierTools.getSupplierPerformance()).thenReturn(List.of());
        when(logisticsTools.getDelayedShipments()).thenReturn(List.of());
        when(warehouseTools.getWarehouseUtilization()).thenReturn(List.of());
        when(analyticsTools.getSupplyChainRisks()).thenReturn(List.of());

        InventoryAgent inventoryAgent = new InventoryAgent(inventoryTools, chatClient);
        SupplierAgent supplierAgent = new SupplierAgent(supplierTools, chatClient);
        LogisticsAgent logisticsAgent = new LogisticsAgent(logisticsTools, chatClient);
        WarehouseAgent warehouseAgent = new WarehouseAgent(warehouseTools, chatClient);
        RiskTools riskTools = Mockito.mock(RiskTools.class);
        ForecastTools forecastTools = Mockito.mock(ForecastTools.class);
        RiskAgent riskAgent = new RiskAgent(analyticsTools, inventoryTools, logisticsTools, riskTools, forecastTools, chatClient);

        agentRouter = new AgentRouter(
                inventoryAgent,
                supplierAgent,
                logisticsAgent,
                warehouseAgent,
                riskAgent,
                chatClient
        );
    }

    @Test
    void testRoutingToInventoryAgent() {
        AgentRouter.AgentResponse response = agentRouter.routeQuery("Which products are low on stock?", "INVENTORY");
        assertNotNull(response);
        assertEquals("INVENTORY_AGENT", response.agentUsed());
        assertTrue(response.response().contains("Inventory Control Agent"));
    }

    @Test
    void testRoutingToSupplierAgent() {
        AgentRouter.AgentResponse response = agentRouter.routeQuery("Show supplier reliability scores", "SUPPLIER");
        assertNotNull(response);
        assertEquals("SUPPLIER_AGENT", response.agentUsed());
        assertTrue(response.response().contains("Supplier Intelligence Agent"));
    }

    @Test
    void testRoutingToLogisticsAgent() {
        AgentRouter.AgentResponse response = agentRouter.routeQuery("Are there any delayed shipments?", "LOGISTICS");
        assertNotNull(response);
        assertEquals("LOGISTICS_AGENT", response.agentUsed());
        assertTrue(response.response().contains("Logistics & Shipment Tracking Agent"));
    }

    @Test
    void testRoutingToWarehouseAgent() {
        AgentRouter.AgentResponse response = agentRouter.routeQuery("What is warehouse capacity utilization?", "WAREHOUSE");
        assertNotNull(response);
        assertEquals("WAREHOUSE_AGENT", response.agentUsed());
        assertTrue(response.response().contains("Warehouse Facilities Agent"));
    }

    @Test
    void testRoutingToRiskAgent() {
        AgentRouter.AgentResponse response = agentRouter.routeQuery("Synthesize operational risks", "RISK");
        assertNotNull(response);
        assertEquals("RISK_AGENT", response.agentUsed());
        assertTrue(response.response().contains("Operational Risk Analysis Agent"));
    }
}
