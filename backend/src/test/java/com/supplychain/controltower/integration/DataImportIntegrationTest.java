package com.supplychain.controltower.integration;

import com.supplychain.controltower.controller.DataImportController;
import com.supplychain.controltower.dto.dashboard.DashboardSummaryDto;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.security.TenantContext;
import com.supplychain.controltower.service.CsvImportService;
import com.supplychain.controltower.service.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DataImportIntegrationTest {

    @Autowired
    private DataImportController dataImportController;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private DashboardService dashboardService;

    private Company tenantAlpha;
    private Company tenantBeta;

    @BeforeEach
    void setUp() {
        tenantAlpha = companyRepository.save(Company.builder()
                .name("Alpha Supply " + UUID.randomUUID())
                .code("ALPHA-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .status("ACTIVE")
                .build());

        tenantBeta = companyRepository.save(Company.builder()
                .name("Beta Supply " + UUID.randomUUID())
                .code("BETA-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .status("ACTIVE")
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. New company starts with completely empty operational dataset")
    void testNewCompanyBeginsEmpty() {
        TenantContext.setCompanyId(tenantAlpha.getId());

        DashboardSummaryDto summary = dashboardService.getDashboardSummary();

        assertNotNull(summary);
        assertEquals(0L, summary.getTotalProducts());
        assertEquals(0L, summary.getTotalInventoryUnits());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalInventoryValue()));
        assertNull(summary.getOverallSupplierReliabilityPct());
        assertEquals(0L, summary.getDelayedShipmentsCount());
        assertTrue(summary.getWarehouseUtilization().isEmpty());
    }

    @Test
    @DisplayName("2. Unauthenticated request without tenant context throws TenantContextMissingException")
    void testUnauthenticatedRequestThrowsException() {
        TenantContext.clear();
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv",
                "sku,name,price\nSKU-1,Prod 1,50.00\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(TenantContextMissingException.class, () ->
                dataImportController.importFile(file, "products")
        );
    }

    @Test
    @DisplayName("3. Authenticated CSV upload creates records scoped strictly to authenticated tenant")
    void testAuthenticatedCsvUploadScopedToTenant() {
        TenantContext.setCompanyId(tenantAlpha.getId());

        String csvData = "sku,name,price,reorder_level,safety_stock,lead_time_days\n" +
                "SKU-ALPHA-100,Precision Microchip,99.50,50,20,5\n" +
                "SKU-ALPHA-200,Optical Sensor,145.00,30,10,7\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv",
                csvData.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response = dataImportController.importFile(file, "products");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof CsvImportService.ImportResult);
        CsvImportService.ImportResult result = (CsvImportService.ImportResult) response.getBody();

        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalRowsProcessed());
        assertEquals(2, result.getRecordsImported());
        assertEquals(0, result.getRecordsFailed());

        // Alpha sees its 2 products
        var alphaProducts = productRepository.findByCompanyId(tenantAlpha.getId());
        assertEquals(2, alphaProducts.size());

        // Beta sees ZERO products (strict isolation preserved)
        TenantContext.setCompanyId(tenantBeta.getId());
        var betaProducts = productRepository.findByCompanyId(tenantBeta.getId());
        assertEquals(0, betaProducts.size());
    }

    @Test
    @DisplayName("4. CSV with duplicate SKUs in file rolls back completely with zero records saved")
    void testDuplicateSkuCsvRollsBackCompletely() {
        TenantContext.setCompanyId(tenantAlpha.getId());

        String csvData = "sku,name,price\n" +
                "SKU-VALID-01,Valid Part,20.00\n" +
                "SKU-DUP-02,Duplicate Part,30.00\n" +
                "SKU-DUP-02,Duplicate Part Copy,35.00\n";
        MockMultipartFile file = new MockMultipartFile("file", "products.csv", "text/csv",
                csvData.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response = dataImportController.importFile(file, "products");

        assertEquals(400, response.getStatusCode().value());
        CsvImportService.ImportResult result = (CsvImportService.ImportResult) response.getBody();
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(0, result.getRecordsImported());
        assertEquals(3, result.getRecordsRejected());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate SKU 'SKU-DUP-02'")));

        // Zero records were saved to database due to atomic rollback
        var alphaProducts = productRepository.findByCompanyId(tenantAlpha.getId());
        assertEquals(0, alphaProducts.size());
    }

    @Test
    @DisplayName("5. Cross-company foreign key reference attempt is rejected and rolls back")
    void testCrossCompanyForeignKeyRejected() {
        // Create product belonging to Tenant Alpha
        TenantContext.setCompanyId(tenantAlpha.getId());
        Product alphaProduct = productRepository.save(Product.builder()
                .company(tenantAlpha)
                .sku("SKU-ALPHA-SECRET")
                .name("Alpha Proprietary Sensor")
                .price(new BigDecimal("500.00"))
                .leadTimeDays(7)
                .reorderLevel(10)
                .safetyStock(5)
                .build());

        // Switch authenticated context to Tenant Beta
        TenantContext.setCompanyId(tenantBeta.getId());

        // Tenant Beta attempts to create inventory referencing Alpha's product ID
        String csvData = "product_id,warehouse_id,quantity_available\n" +
                alphaProduct.getId() + ",1,100\n";
        MockMultipartFile file = new MockMultipartFile("file", "inventories.csv", "text/csv",
                csvData.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response = dataImportController.importFile(file, "inventories");

        assertEquals(400, response.getStatusCode().value());
        CsvImportService.ImportResult result = (CsvImportService.ImportResult) response.getBody();
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("Product not found with id: " + alphaProduct.getId() + " for this company"));

        // Verify zero inventory records saved for Beta
        var betaInventory = inventoryRepository.findByCompanyId(tenantBeta.getId());
        assertTrue(betaInventory.isEmpty());
    }
}
