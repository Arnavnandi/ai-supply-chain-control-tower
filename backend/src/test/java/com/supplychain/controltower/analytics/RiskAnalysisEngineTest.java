package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ShipmentRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskAnalysisEngineTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private RiskAnalysisEngine riskAnalysisEngine;

    private Product sampleProduct;
    private Warehouse sampleWarehouse;
    private Supplier sampleSupplier;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder().id(1L).sku("SKU-001").name("Microcontroller").build();
        sampleWarehouse = Warehouse.builder().id(1L).name("North Hub").build();
        sampleSupplier = Supplier.builder()
                .id(1L)
                .code("SUP-01")
                .name("Unreliable Supplier")
                .reliabilityScore(BigDecimal.valueOf(70.0))
                .deliveryPerformancePct(BigDecimal.valueOf(65.0))
                .leadTimeVarianceDays(4.5)
                .build();
    }

    @Test
    void testEvaluateSystemRisksDetectsLowStockAndUnreliableSupplier() {
        Inventory lowStock = Inventory.builder()
                .id(1L)
                .product(sampleProduct)
                .warehouse(sampleWarehouse)
                .quantityAvailable(5)
                .safetyStock(50)
                .reorderLevel(100)
                .build();

        Shipment delayedShipment = Shipment.builder()
                .id(1L)
                .trackingCode("TRK-100")
                .origin("Tokyo")
                .destination("Chicago")
                .carrierName("Pacific Shipping")
                .status(Shipment.ShipmentStatus.DELAYED)
                .delayDays(6)
                .estimatedDeliveryDate(LocalDate.now().minusDays(2))
                .build();

        when(inventoryRepository.findLowStockInventory()).thenReturn(List.of(lowStock));
        when(inventoryRepository.findOverstockedInventory()).thenReturn(List.of());
        when(supplierRepository.findAll()).thenReturn(List.of(sampleSupplier));
        when(shipmentRepository.findAll()).thenReturn(List.of(delayedShipment));

        RiskAnalysisEngine.ControlTowerRiskReport report = riskAnalysisEngine.evaluateSystemRisks();

        assertNotNull(report);
        assertTrue(report.getOverallRiskScore() > 0);
        assertEquals(3, report.getRiskItems().size());
        assertTrue(report.getCriticalRisksCount() > 0);

        RiskAnalysisEngine.ExplainableRiskItem invRisk = report.getRiskItems().stream()
                .filter(r -> "INVENTORY".equals(r.getCategory()))
                .findFirst().orElseThrow();

        assertNotNull(invRisk.getProblemDetected());
        assertNotNull(invRisk.getDataCause());
        assertNotNull(invRisk.getActionRecommended());
        assertTrue(invRisk.getDataCause().contains("Safety Stock"));
    }
}
