package com.supplychain.controltower.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyActionExecutionBridgeServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private CustomerOrderRepository customerOrderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private TelemetryEventPublisher telemetryEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PolicyActionExecutionBridgeService policyBridgeService;
    private ActionExecutionEngine actionExecutionEngine;

    @BeforeEach
    void setUp() {
        policyBridgeService = new PolicyActionExecutionBridgeService(
                productRepository,
                inventoryRepository,
                supplierRepository,
                warehouseRepository,
                customerOrderRepository,
                orderItemRepository,
                telemetryEventPublisher,
                objectMapper
        );

        actionExecutionEngine = new ActionExecutionEngine(
                inventoryRepository,
                productRepository,
                customerOrderRepository,
                orderItemRepository,
                policyBridgeService
        );
    }

    @Test
    void testExecuteInventoryShortageRecovery() {
        String payloadJson = "{\"disruptionType\":\"INVENTORY_SHORTAGE\",\"targetEntity\":\"SKU-ELEC-001\",\"policyDecision\":\"EXPEDITE_REPLENISHMENT_AND_REBALANCE\",\"simulationId\":\"SIM-001\",\"riskBand\":\"HIGH\"}";

        Product mockProduct = Product.builder().id(10L).sku("SKU-ELEC-001").price(BigDecimal.valueOf(50.0)).leadTimeDays(5).build();
        Inventory mockInv = Inventory.builder().id(100L).product(mockProduct).quantityAvailable(20).build();

        when(productRepository.findBySku("SKU-ELEC-001")).thenReturn(Optional.of(mockProduct));
        when(inventoryRepository.findByProductId(10L)).thenReturn(List.of(mockInv));
        when(customerOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String result = policyBridgeService.executePolicyAction("REORDER_STOCK", payloadJson, "testUser");

        assertNotNull(result);
        assertTrue(result.contains("EXPEDITE_REPLENISHMENT_AND_REBALANCE"));
        assertTrue(result.contains("SKU-ELEC-001"));
        assertEquals(120, mockInv.getQuantityAvailable());

        ArgumentCaptor<TelemetryEvent> eventCaptor = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(telemetryEventPublisher, times(1)).publish(eventCaptor.capture());
        TelemetryEvent publishedEvent = eventCaptor.getValue();

        assertEquals(TelemetryEvent.EventType.AGENT_EXECUTION, publishedEvent.getEventType());
        assertEquals(TelemetryEvent.Severity.INFO, publishedEvent.getSeverity());
        assertTrue(publishedEvent.getMessage().contains("EXPEDITE_REPLENISHMENT_AND_REBALANCE"));
    }

    @Test
    void testExecuteSupplierDisruptionRecovery() {
        String payloadJson = "{\"disruptionType\":\"SUPPLIER_DISRUPTION\",\"targetEntity\":\"SUP-ELEC-001\",\"policyDecision\":\"ACTIVATE_SECONDARY_SUPPLIER_FAILOVER\",\"simulationId\":\"SIM-002\",\"riskBand\":\"CRITICAL\"}";

        Supplier mockSupplier = Supplier.builder().id(5L).code("SUP-ELEC-001").name("Tech Components Ltd").reliabilityScore(BigDecimal.valueOf(85.0)).build();
        when(supplierRepository.findByCode("SUP-ELEC-001")).thenReturn(Optional.of(mockSupplier));

        String result = policyBridgeService.executePolicyAction("CHANGE_SUPPLIER", payloadJson, "testUser");

        assertNotNull(result);
        assertTrue(result.contains("ACTIVATE_SECONDARY_SUPPLIER_FAILOVER"));
        assertTrue(result.contains("Tech Components Ltd"));
        assertEquals(BigDecimal.valueOf(90.0), mockSupplier.getReliabilityScore());

        verify(supplierRepository, times(1)).save(mockSupplier);
        verify(telemetryEventPublisher, times(1)).publish(any());
    }

    @Test
    void testExecuteLogisticsDelayRecovery() {
        String payloadJson = "{\"disruptionType\":\"LOGISTICS_DELAY\",\"targetEntity\":\"Stuttgart to Oakland\",\"policyDecision\":\"CARRIER_REROUTE_AND_AIR_CARGO_ESCALATION\",\"simulationId\":\"SIM-003\",\"riskBand\":\"HIGH\"}";

        String result = policyBridgeService.executePolicyAction("EXPEDITE_SHIPMENT", payloadJson, "testUser");

        assertNotNull(result);
        assertTrue(result.contains("CARRIER_REROUTE_AND_AIR_CARGO_ESCALATION"));
        assertTrue(result.contains("Stuttgart to Oakland"));
        verify(telemetryEventPublisher, times(1)).publish(any());
    }

    @Test
    void testExecuteWarehouseCapacityRecovery() {
        String payloadJson = "{\"disruptionType\":\"WAREHOUSE_CAPACITY_OVERRUN\",\"targetEntity\":\"WH-WEST\",\"policyDecision\":\"INTER_HUB_STOCK_TRANSFER_AND_RECEIPT_DEFERRAL\",\"simulationId\":\"SIM-004\",\"riskBand\":\"HIGH\"}";

        Warehouse mockWarehouse = Warehouse.builder().id(2L).code("WH-WEST").name("West Coast Hub").utilizationPercentage(BigDecimal.valueOf(95.0)).build();
        when(warehouseRepository.findByCode("WH-WEST")).thenReturn(Optional.of(mockWarehouse));

        String result = policyBridgeService.executePolicyAction("REALLOCATE_INVENTORY", payloadJson, "testUser");

        assertNotNull(result);
        assertTrue(result.contains("INTER_HUB_STOCK_TRANSFER_AND_RECEIPT_DEFERRAL"));
        assertTrue(result.contains("West Coast Hub"));
        assertEquals(BigDecimal.valueOf(80.0), mockWarehouse.getUtilizationPercentage());

        verify(warehouseRepository, times(1)).save(mockWarehouse);
    }

    @Test
    void testActionExecutionEngineDelegation() {
        String payloadJson = "{\"disruptionType\":\"INVENTORY_SHORTAGE\",\"targetEntity\":\"SKU-ELEC-001\",\"policyDecision\":\"EXPEDITE_REPLENISHMENT_AND_REBALANCE\"}";

        String result = actionExecutionEngine.executeApprovedAction("REORDER_STOCK", payloadJson, "testUser");

        assertNotNull(result);
        verify(productRepository, times(1)).findBySku("SKU-ELEC-001");
    }
}
