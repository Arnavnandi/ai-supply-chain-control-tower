package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.ProductDto;
import com.supplychain.controltower.entity.Category;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.CategoryRepository;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private CompanyRepository companyRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        productRepository = Mockito.mock(ProductRepository.class);
        categoryRepository = Mockito.mock(CategoryRepository.class);
        companyRepository = Mockito.mock(CompanyRepository.class);

        productService = new ProductService(
                productRepository,
                categoryRepository,
                companyRepository
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAllProducts_withCompanyCodeInTenantContext_doesNotQueryCompanyRepositoryOrTraverseLazyCompany() {
        TenantContext.setCompanyId(10L);
        TenantContext.setCompanyCode("ACME");

        Company lazyCompanyMock = Mockito.mock(Company.class);
        when(lazyCompanyMock.getCode()).thenThrow(new IllegalStateException("Lazy Company should NOT be traversed during DTO mapping!"));

        Category cat = Category.builder().id(5L).name("Electronics").build();

        Product p1 = Product.builder()
                .id(1L)
                .sku("CUSTOM-1")
                .name("Custom Widget 1")
                .description("Desc 1")
                .price(new BigDecimal("99.99"))
                .category(cat)
                .reorderLevel(10)
                .safetyStock(20)
                .leadTimeDays(3)
                .unitOfMeasure("Units")
                .company(lazyCompanyMock)
                .build();

        Product p2 = Product.builder()
                .id(2L)
                .sku("SKU-ELEC-100")
                .name("Demo Widget 2")
                .description("Desc 2")
                .price(new BigDecimal("199.99"))
                .category(null)
                .reorderLevel(5)
                .safetyStock(10)
                .leadTimeDays(4)
                .unitOfMeasure("Boxes")
                .company(lazyCompanyMock)
                .build();

        when(productRepository.findByCompanyId(10L)).thenReturn(List.of(p1, p2));

        List<ProductDto> result = productService.getAllProducts(null);

        assertEquals(2, result.size());

        ProductDto dto1 = result.get(0);
        assertEquals(1L, dto1.getId());
        assertEquals("CUSTOM-1", dto1.getSku());
        assertEquals("Custom Widget 1", dto1.getName());
        assertEquals(5L, dto1.getCategoryId());
        assertEquals("Electronics", dto1.getCategoryName());
        assertFalse(dto1.getIsDemo(), "Non-demo SKU with ACME tenant should have isDemo=false");

        ProductDto dto2 = result.get(1);
        assertEquals(2L, dto2.getId());
        assertEquals("SKU-ELEC-100", dto2.getSku());
        assertNull(dto2.getCategoryId());
        assertEquals("Uncategorized", dto2.getCategoryName());
        assertTrue(dto2.getIsDemo(), "SKU-ELEC prefix should have isDemo=true");

        verify(companyRepository, never()).findById(any());
        verify(lazyCompanyMock, never()).getCode();
    }

    @Test
    void getAllProducts_withDemoTenantContext_marksAllAsDemo() {
        TenantContext.setCompanyId(10L);
        TenantContext.setCompanyCode("DEMO");

        Product p1 = Product.builder()
                .id(1L)
                .sku("CUSTOM-NON-DEMO-SKU")
                .name("Custom Item")
                .price(new BigDecimal("50.00"))
                .reorderLevel(10)
                .safetyStock(20)
                .leadTimeDays(2)
                .build();

        when(productRepository.findByCompanyId(10L)).thenReturn(List.of(p1));

        List<ProductDto> result = productService.getAllProducts(null);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsDemo(), "DEMO tenant should have isDemo=true for all items");
        verify(companyRepository, never()).findById(any());
    }

    @Test
    void getAllProducts_withoutCompanyCodeInTenantContext_resolvesCompanyOnceAcrossAllProducts() {
        TenantContext.setCompanyId(10L); // No companyCode set

        Company company = Company.builder().id(10L).code("ACME").name("Acme Corp").build();
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));

        Product p1 = Product.builder().id(1L).sku("CUSTOM-1").name("Item 1").price(BigDecimal.TEN).reorderLevel(5).safetyStock(5).leadTimeDays(1).build();
        Product p2 = Product.builder().id(2L).sku("CUSTOM-2").name("Item 2").price(BigDecimal.TEN).reorderLevel(5).safetyStock(5).leadTimeDays(1).build();
        Product p3 = Product.builder().id(3L).sku("CUSTOM-3").name("Item 3").price(BigDecimal.TEN).reorderLevel(5).safetyStock(5).leadTimeDays(1).build();

        when(productRepository.findByCompanyId(10L)).thenReturn(List.of(p1, p2, p3));

        List<ProductDto> result = productService.getAllProducts(null);

        assertEquals(3, result.size());
        assertFalse(result.get(0).getIsDemo());
        assertFalse(result.get(1).getIsDemo());
        assertFalse(result.get(2).getIsDemo());

        // Verifies companyRepository.findById was called exactly ONCE, NOT 3 times
        verify(companyRepository, times(1)).findById(10L);
    }

    @Test
    void getAllProducts_withSearchQuery_delegatesToSearchProducts() {
        TenantContext.setCompanyId(10L);
        TenantContext.setCompanyCode("ACME");

        Product p = Product.builder().id(1L).sku("WIDGET-1").name("Special Widget").price(BigDecimal.ONE).reorderLevel(1).safetyStock(1).leadTimeDays(1).build();
        when(productRepository.searchProducts(10L, "widget")).thenReturn(List.of(p));

        List<ProductDto> result = productService.getAllProducts("widget");

        assertEquals(1, result.size());
        assertEquals("WIDGET-1", result.get(0).getSku());
        verify(productRepository).searchProducts(10L, "widget");
        verify(productRepository, never()).findByCompanyId(any());
    }

    @Test
    void getProductById_withTenantContext_mapsCorrectlyWithoutCallingCompanyRepo() {
        TenantContext.setCompanyId(10L);
        TenantContext.setCompanyCode("ACME");

        Product p = Product.builder()
                .id(42L)
                .sku("SKU-PROD-99")
                .name("Production Widget")
                .price(new BigDecimal("12.50"))
                .reorderLevel(10)
                .safetyStock(20)
                .leadTimeDays(5)
                .unitOfMeasure("Kg")
                .build();

        when(productRepository.findByCompanyIdAndId(10L, 42L)).thenReturn(Optional.of(p));

        ProductDto dto = productService.getProductById(42L);

        assertNotNull(dto);
        assertEquals(42L, dto.getId());
        assertEquals("SKU-PROD-99", dto.getSku());
        assertTrue(dto.getIsDemo());
        verify(companyRepository, never()).findById(any());
    }

    @Test
    void createProduct_usesLoadedCompanyCodeDirectly() {
        TenantContext.setCompanyId(10L);

        Company company = Company.builder().id(10L).code("DEMO").name("Demo Corp").build();
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));

        ProductDto input = ProductDto.builder()
                .sku("ANY-SKU")
                .name("New Item")
                .price(new BigDecimal("25.00"))
                .reorderLevel(10)
                .safetyStock(15)
                .leadTimeDays(2)
                .unitOfMeasure("Units")
                .build();

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        ProductDto created = productService.createProduct(input);

        assertNotNull(created);
        assertEquals(99L, created.getId());
        assertTrue(created.getIsDemo(), "Should be demo because created with DEMO company");
        // Verify findById was called once for the creation lookup, and not again for mapping
        verify(companyRepository, times(1)).findById(10L);
    }
}
