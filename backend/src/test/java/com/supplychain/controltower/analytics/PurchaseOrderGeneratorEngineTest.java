package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.SupplierProduct;
import com.supplychain.controltower.repository.SupplierProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderGeneratorEngineTest {

    @Mock
    private SupplierProductRepository supplierProductRepository;

    @InjectMocks
    private PurchaseOrderGeneratorEngine poGeneratorEngine;

    private Product product;
    private Inventory inventory;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        product = Product.builder().id(1L).sku("SKU-TEST-001").name("Hydraulic Valve").price(BigDecimal.valueOf(100.0)).reorderLevel(150).safetyStock(50).build();
        inventory = Inventory.builder().id(10L).product(product).quantityAvailable(20).reorderLevel(150).safetyStock(50).build();
        supplier = Supplier.builder().id(2L).code("SUP-VALVE").name("Valve Master Corp").build();
    }

    @Test
    void testGenerateReplenishmentPayloadWithPreferredSupplier() {
        SupplierProduct sp = SupplierProduct.builder()
                .supplier(supplier)
                .product(product)
                .contractPrice(BigDecimal.valueOf(85.0))
                .isPreferredSupplier(true)
                .minimumOrderQuantity(100)
                .build();

        when(supplierProductRepository.findByCompanyIdAndProductId(1L, 1L)).thenReturn(List.of(sp));

        PurchaseOrderGeneratorEngine.PurchaseOrderPayload payload =
                poGeneratorEngine.generateReplenishmentPayload(product, inventory, 200);

        assertNotNull(payload);
        assertEquals("REORDER_STOCK", payload.getActionType());
        assertEquals("SKU-TEST-001", payload.getProductSku());
        assertEquals("SKU-TEST-001", payload.getSku());
        assertEquals(BigDecimal.valueOf(85.0), payload.getContractUnitPrice());
        assertEquals("Valve Master Corp", payload.getSupplierName());
        assertTrue(payload.getOrderQuantity() >= 100);
        assertTrue(payload.getReasoning().contains("Valve Master Corp"));

        // Verify JSON serialization includes both sku and productSku
        String json = poGeneratorEngine.convertPayloadToJson(payload);
        assertTrue(json.contains("\"sku\":\"SKU-TEST-001\""), "JSON should contain sku field");
        assertTrue(json.contains("\"productSku\":\"SKU-TEST-001\""), "JSON should contain productSku field");
    }

    @Test
    void testGenerateReplenishmentPayloadWithNullProduct() {
        PurchaseOrderGeneratorEngine.PurchaseOrderPayload payload =
                poGeneratorEngine.generateReplenishmentPayload(null, inventory, 100);

        assertNotNull(payload);
        assertNull(payload.getSku(), "SKU should be null when product is null; do not fabricate");
        assertNull(payload.getProductSku(), "productSku should be null when product is null; do not fabricate");
        assertNull(payload.getProductId());
        assertEquals("Unknown Product", payload.getProductName());

        String json = poGeneratorEngine.convertPayloadToJson(payload);
        assertNotNull(json);
    }

    @Test
    void testGenerateReplenishmentPayloadWithNullOrBlankSku() {
        Product productWithoutSku = Product.builder()
                .id(99L)
                .sku("   ")
                .name("Uncataloged Item")
                .reorderLevel(100)
                .safetyStock(20)
                .build();

        PurchaseOrderGeneratorEngine.PurchaseOrderPayload payload =
                poGeneratorEngine.generateReplenishmentPayload(productWithoutSku, inventory, 50);

        assertNotNull(payload);
        assertNull(payload.getSku(), "Blank SKU must be normalized to null without fabricating values");
        assertNull(payload.getProductSku());
    }

    @Test
    void testCompatibilityGetters() {
        PurchaseOrderGeneratorEngine.PurchaseOrderPayload payloadWithSkuOnly =
                PurchaseOrderGeneratorEngine.PurchaseOrderPayload.builder()
                        .sku("SKU-ONLY-001")
                        .build();
        assertEquals("SKU-ONLY-001", payloadWithSkuOnly.getProductSku());
        assertEquals("SKU-ONLY-001", payloadWithSkuOnly.getSku());

        PurchaseOrderGeneratorEngine.PurchaseOrderPayload payloadWithProductSkuOnly =
                PurchaseOrderGeneratorEngine.PurchaseOrderPayload.builder()
                        .productSku("PROD-SKU-ONLY-002")
                        .build();
        assertEquals("PROD-SKU-ONLY-002", payloadWithProductSkuOnly.getSku());
        assertEquals("PROD-SKU-ONLY-002", payloadWithProductSkuOnly.getProductSku());
    }
}
