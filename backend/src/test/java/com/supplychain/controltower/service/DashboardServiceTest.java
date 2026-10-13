package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.dashboard.DashboardSummaryDto;
import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private ProductRepository productRepository;
    private InventoryRepository inventoryRepository;
    private WarehouseRepository warehouseRepository;
    private SupplierRepository supplierRepository;
    private CustomerOrderRepository orderRepository;
    private ShipmentRepository shipmentRepository;
    private RiskAlertRepository riskAlertRepository;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        warehouseRepository = Mockito.mock(WarehouseRepository.class);
        supplierRepository = Mockito.mock(SupplierRepository.class);
        orderRepository = Mockito.mock(CustomerOrderRepository.class);
        shipmentRepository = Mockito.mock(ShipmentRepository.class);
        riskAlertRepository = Mockito.mock(RiskAlertRepository.class);

        dashboardService = new DashboardService(
                productRepository,
                inventoryRepository,
                warehouseRepository,
                supplierRepository,
                orderRepository,
                shipmentRepository,
                riskAlertRepository
        );
    }

    @Test
    void testGetDashboardSummaryWithDatabaseData() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        Product mockProduct = Product.builder().id(1L).sku("MOCK-1").build();
        when(productRepository.findByCompanyId(1L)).thenReturn(List.of(mockProduct, mockProduct, mockProduct, mockProduct, mockProduct));

        Warehouse wh1 = Warehouse.builder().code("WH-NORTH").name("North Warehouse").totalCapacityUnits(10000).currentUtilizationUnits(8000).utilizationPercentage(new BigDecimal("80.00")).build();
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(List.of(wh1));

        Inventory inv1 = Inventory.builder().warehouse(wh1).quantityAvailable(500).reservedQuantity(50).safetyStock(100).build();
        when(inventoryRepository.findByCompanyId(1L)).thenReturn(List.of(inv1));
        when(inventoryRepository.calculateTotalInventoryValue(org.mockito.ArgumentMatchers.anyLong())).thenReturn(22500.0);
        when(inventoryRepository.findLowStockInventory(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        when(inventoryRepository.findOverstockedInventory(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        CustomerOrder o1 = CustomerOrder.builder().orderNumber("ORD-1").orderDate(LocalDate.now().minusDays(10)).build();
        OrderItem oi1 = OrderItem.builder().order(o1).quantity(100).build();
        o1.setItems(List.of(oi1));
        when(orderRepository.findByCompanyIdOrderByOrderDateDesc(1L)).thenReturn(List.of(o1));
        when(orderRepository.findByCompanyIdAndStatus(1L, CustomerOrder.OrderStatus.PENDING)).thenReturn(List.of());
        when(orderRepository.findByCompanyIdAndStatus(1L, CustomerOrder.OrderStatus.PROCESSING)).thenReturn(List.of());
        when(shipmentRepository.findByCompanyIdAndStatus(1L, Shipment.ShipmentStatus.DELAYED)).thenReturn(List.of());

        Supplier s1 = Supplier.builder().name("TechComp").reliabilityScore(new BigDecimal("95.0")).build();
        when(supplierRepository.findByCompanyId(1L)).thenReturn(List.of(s1));

        DashboardSummaryDto summary = dashboardService.getDashboardSummary();

        assertNotNull(summary);
        assertEquals(5L, summary.getTotalProducts());
        assertEquals(500L, summary.getTotalInventoryUnits());
        assertEquals(new BigDecimal("22500.00"), summary.getTotalInventoryValue());
        assertFalse(summary.getDemandTrends().isEmpty());
        assertFalse(summary.getInventoryTrends().isEmpty());
    }

    @Test
    void testGetDashboardSummaryEmptyDatabaseReturnsEmptyTrendsWithoutCrashing() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        when(productRepository.findByCompanyId(1L)).thenReturn(List.of());
        when(inventoryRepository.findByCompanyId(1L)).thenReturn(List.of());
        when(inventoryRepository.calculateTotalInventoryValue(org.mockito.ArgumentMatchers.anyLong())).thenReturn(0.0);
        when(orderRepository.findByCompanyIdOrderByOrderDateDesc(1L)).thenReturn(List.of());
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(List.of());
        when(supplierRepository.findByCompanyId(1L)).thenReturn(List.of());

        DashboardSummaryDto summary = dashboardService.getDashboardSummary();

        assertNotNull(summary);
        assertEquals(0L, summary.getTotalProducts());
        assertTrue(summary.getDemandTrends().isEmpty());
        assertTrue(summary.getInventoryTrends().isEmpty());
        assertNull(summary.getOverallSupplierReliabilityPct());
        assertNull(summary.getSupplyChainRiskScore());
        assertFalse(summary.getHasOperationalData());
        assertEquals("UNPOPULATED", summary.getDataStatus());
        assertTrue(summary.getSupplierPerformance().isEmpty());
        assertTrue(summary.getWarehouseUtilization().isEmpty());
    }
}
