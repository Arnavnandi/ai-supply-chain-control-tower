package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostRecoveryRiskEvaluationEngineTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private PostRecoveryRiskEvaluationEngine engine;

    private Product mockProduct;

    @BeforeEach
    void setUp() {
        mockProduct = Product.builder()
                .id(1L)
                .sku("SKU-ELEC-001")
                .name("Control Tower Module")
                .price(BigDecimal.valueOf(120.0))
                .build();
    }

    @Test
    void testEvaluateInventoryShortageStockReplenished() {
        when(productRepository.findBySku("SKU-ELEC-001")).thenReturn(Optional.of(mockProduct));
        Inventory inv = Inventory.builder().id(10L).quantityAvailable(120).build();
        when(inventoryRepository.findByProductId(1L)).thenReturn(List.of(inv));

        PostRecoveryRiskEvaluationEngine.PostRecoveryRiskResult result =
                engine.evaluatePostExecutionRisk("INVENTORY_SHORTAGE", "SKU-ELEC-001", 70.0, "HIGH");

        assertNotNull(result);
        assertEquals(15.0, result.getPostRecoveryRiskScore());
        assertEquals(55.0, result.getRiskReductionDelta());
        assertEquals("LOW", result.getResidualRiskBand());
        assertTrue(result.getEvaluationSummary().contains("Stock replenished to 120 units"));
    }

    @Test
    void testEvaluateSupplierDisruptionFailoverActive() {
        Supplier supplier = Supplier.builder()
                .id(5L)
                .code("SUP-TECH-001")
                .name("Alpha Tech Components")
                .reliabilityScore(BigDecimal.valueOf(90.0))
                .build();
        when(supplierRepository.findByCode("SUP-TECH-001")).thenReturn(Optional.of(supplier));

        PostRecoveryRiskEvaluationEngine.PostRecoveryRiskResult result =
                engine.evaluatePostExecutionRisk("SUPPLIER_DISRUPTION", "SUP-TECH-001", 80.0, "CRITICAL");

        assertNotNull(result);
        assertEquals(10.0, result.getPostRecoveryRiskScore());
        assertEquals(70.0, result.getRiskReductionDelta());
        assertEquals("LOW", result.getResidualRiskBand());
    }

    @Test
    void testEvaluateWarehouseCapacityRebalanced() {
        Warehouse warehouse = Warehouse.builder()
                .id(2L)
                .code("WH-NORTH-01")
                .name("North Regional Warehouse")
                .utilizationPercentage(BigDecimal.valueOf(60.0))
                .build();
        when(warehouseRepository.findByCode("WH-NORTH-01")).thenReturn(Optional.of(warehouse));

        PostRecoveryRiskEvaluationEngine.PostRecoveryRiskResult result =
                engine.evaluatePostExecutionRisk("WAREHOUSE_CAPACITY_OVERRUN", "WH-NORTH-01", 75.0, "HIGH");

        assertNotNull(result);
        assertEquals(18.0, result.getPostRecoveryRiskScore());
        assertEquals(57.0, result.getRiskReductionDelta());
        assertEquals("LOW", result.getResidualRiskBand());
    }

    @Test
    void testEvaluateLogisticsDelayRerouted() {
        PostRecoveryRiskEvaluationEngine.PostRecoveryRiskResult result =
                engine.evaluatePostExecutionRisk("LOGISTICS_DELAY", "TRK-ROUTE-009", 65.0, "MEDIUM");

        assertNotNull(result);
        assertEquals(18.0, result.getPostRecoveryRiskScore());
        assertEquals(47.0, result.getRiskReductionDelta());
        assertEquals("LOW", result.getResidualRiskBand());
    }
}
