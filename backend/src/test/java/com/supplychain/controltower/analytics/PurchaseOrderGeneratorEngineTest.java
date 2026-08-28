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

        when(supplierProductRepository.findByProductId(1L)).thenReturn(List.of(sp));

        PurchaseOrderGeneratorEngine.PurchaseOrderPayload payload =
                poGeneratorEngine.generateReplenishmentPayload(product, inventory, 200);

        assertNotNull(payload);
        assertEquals("REORDER_STOCK", payload.getActionType());
        assertEquals("SKU-TEST-001", payload.getProductSku());
        assertEquals(BigDecimal.valueOf(85.0), payload.getContractUnitPrice());
        assertEquals("Valve Master Corp", payload.getSupplierName());
        assertTrue(payload.getOrderQuantity() >= 100);
        assertTrue(payload.getReasoning().contains("Valve Master Corp"));
    }
}
