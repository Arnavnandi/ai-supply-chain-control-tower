package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.InventoryDto;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private ProductRepository productRepository;
    private WarehouseRepository warehouseRepository;
    private CompanyRepository companyRepository;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        productRepository = Mockito.mock(ProductRepository.class);
        warehouseRepository = Mockito.mock(WarehouseRepository.class);
        companyRepository = Mockito.mock(CompanyRepository.class);

        inventoryService = new InventoryService(
                inventoryRepository,
                productRepository,
                warehouseRepository,
                companyRepository
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAllInventory_withCompanyCodeInTenantContext_doesNotQueryCompanyRepositoryOrTraverseLazyCompany() {
        TenantContext.setCompanyId(20L);
        TenantContext.setCompanyCode("ACME");

        Company lazyCompanyMock = Mockito.mock(Company.class);
        when(lazyCompanyMock.getCode()).thenThrow(new IllegalStateException("Lazy Company should NOT be traversed during DTO mapping!"));

        Product product1 = Product.builder()
                .id(101L)
                .sku("SKU-PROD-001")
                .name("Standard Widget")
                .price(new BigDecimal("45.00"))
                .build();

        Warehouse warehouse1 = Warehouse.builder()
                .id(201L)
                .code("WH-EAST")
                .name("East Warehouse")
                .build();

        Inventory inv1 = Inventory.builder()
                .id(1L)
                .product(product1)
                .warehouse(warehouse1)
                .company(lazyCompanyMock)
                .quantityAvailable(10)
                .reservedQuantity(2)
                .reorderLevel(50)
                .safetyStock(20)
                .lastRestockedAt(LocalDateTime.of(2026, 1, 15, 10, 0))
                .build();

        Product product2 = Product.builder()
                .id(102L)
                .sku("CUSTOM-SKU-99")
                .name("Custom Part")
                .price(new BigDecimal("150.00"))
                .build();

        Warehouse warehouse2 = Warehouse.builder()
                .id(202L)
                .code("WH-WEST")
                .name("West Warehouse")
                .build();

        Inventory inv2 = Inventory.builder()
                .id(2L)
                .product(product2)
                .warehouse(warehouse2)
                .company(lazyCompanyMock)
                .quantityAvailable(200)
                .reservedQuantity(10)
                .reorderLevel(30)
                .safetyStock(15)
                .build();

        when(inventoryRepository.findByCompanyId(20L)).thenReturn(List.of(inv1, inv2));

        List<InventoryDto> result = inventoryService.getAllInventory();

        assertEquals(2, result.size());

        InventoryDto dto1 = result.get(0);
        assertEquals(1L, dto1.getId());
        assertEquals(101L, dto1.getProductId());
        assertEquals("SKU-PROD-001", dto1.getProductSku());
        assertEquals("Standard Widget", dto1.getProductName());
        assertEquals(new BigDecimal("45.00"), dto1.getProductPrice());
        assertEquals(201L, dto1.getWarehouseId());
        assertEquals("WH-EAST", dto1.getWarehouseCode());
        assertEquals("East Warehouse", dto1.getWarehouseName());
        assertEquals(10, dto1.getQuantityAvailable());
        assertEquals(2, dto1.getReservedQuantity());
        assertEquals(50, dto1.getReorderLevel());
        assertEquals(20, dto1.getSafetyStock());
        assertEquals("CRITICAL", dto1.getStatus(), "quantity (10) <= safetyStock (20) should be CRITICAL");
        assertTrue(dto1.getIsDemo(), "SKU-PROD- prefix should have isDemo=true");

        InventoryDto dto2 = result.get(1);
        assertEquals("OVERSTOCK", dto2.getStatus(), "quantity (200) > reorderLevel (30)*3 should be OVERSTOCK");
        assertFalse(dto2.getIsDemo(), "Non-demo SKU with ACME tenant should have isDemo=false");

        verify(companyRepository, never()).findById(any());
        verify(lazyCompanyMock, never()).getCode();
    }

    @Test
    void getAllInventory_withoutCompanyCodeInTenantContext_resolvesCompanyOnceAcrossAllItems() {
        TenantContext.setCompanyId(20L); // No companyCode set

        Company company = Company.builder().id(20L).code("ACME").name("Acme Corp").build();
        when(companyRepository.findById(20L)).thenReturn(Optional.of(company));

        Product product = Product.builder().id(101L).sku("CUSTOM-1").name("Item 1").price(BigDecimal.ONE).build();
        Warehouse warehouse = Warehouse.builder().id(201L).code("WH-1").name("Warehouse 1").build();

        Inventory inv1 = Inventory.builder().id(1L).product(product).warehouse(warehouse).quantityAvailable(40).reorderLevel(50).safetyStock(20).build();
        Inventory inv2 = Inventory.builder().id(2L).product(product).warehouse(warehouse).quantityAvailable(100).reorderLevel(50).safetyStock(20).build();
        Inventory inv3 = Inventory.builder().id(3L).product(product).warehouse(warehouse).quantityAvailable(5).reorderLevel(50).safetyStock(20).build();

        when(inventoryRepository.findByCompanyId(20L)).thenReturn(List.of(inv1, inv2, inv3));

        List<InventoryDto> result = inventoryService.getAllInventory();

        assertEquals(3, result.size());
        assertEquals("LOW_STOCK", result.get(0).getStatus());
        assertEquals("OPTIMAL", result.get(1).getStatus());
        assertEquals("CRITICAL", result.get(2).getStatus());

        // Verifies companyRepository.findById was called exactly ONCE, NOT 3 times
        verify(companyRepository, times(1)).findById(20L);
    }

    @Test
    void getLowStockInventory_usesResolvedCompanyCode() {
        TenantContext.setCompanyId(20L);
        TenantContext.setCompanyCode("DEMO");

        Product product = Product.builder().id(101L).sku("ANY-SKU").name("Item").price(BigDecimal.ONE).build();
        Warehouse warehouse = Warehouse.builder().id(201L).code("WH-1").name("Warehouse 1").build();
        Inventory inv = Inventory.builder().id(1L).product(product).warehouse(warehouse).quantityAvailable(10).reorderLevel(20).safetyStock(5).build();

        when(inventoryRepository.findLowStockInventory(20L)).thenReturn(List.of(inv));

        List<InventoryDto> result = inventoryService.getLowStockInventory();

        assertEquals(1, result.size());
        assertEquals("LOW_STOCK", result.get(0).getStatus());
        assertTrue(result.get(0).getIsDemo(), "DEMO tenant should have isDemo=true");
        verify(companyRepository, never()).findById(any());
    }

    @Test
    void getOverstockedInventory_usesResolvedCompanyCode() {
        TenantContext.setCompanyId(20L);
        TenantContext.setCompanyCode("DEMO");

        Product product = Product.builder().id(101L).sku("ANY-SKU").name("Item").price(BigDecimal.ONE).build();
        Warehouse warehouse = Warehouse.builder().id(201L).code("WH-1").name("Warehouse 1").build();
        Inventory inv = Inventory.builder().id(1L).product(product).warehouse(warehouse).quantityAvailable(150).reorderLevel(20).safetyStock(5).build();

        when(inventoryRepository.findOverstockedInventory(20L)).thenReturn(List.of(inv));

        List<InventoryDto> result = inventoryService.getOverstockedInventory();

        assertEquals(1, result.size());
        assertEquals("OVERSTOCK", result.get(0).getStatus());
        assertTrue(result.get(0).getIsDemo());
        verify(companyRepository, never()).findById(any());
    }
}
