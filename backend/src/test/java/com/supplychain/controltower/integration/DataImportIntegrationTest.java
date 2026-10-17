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
import com.supplychain.controltower.dto.domain.CsvImportHistoryDto;
import com.supplychain.controltower.entity.CsvImportHistory;
import com.supplychain.controltower.security.UserPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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
    private com.supplychain.controltower.repository.CsvImportHistoryRepository csvImportHistoryRepository;

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
        csvImportHistoryRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        if (tenantAlpha != null && tenantAlpha.getId() != null) {
            companyRepository.deleteById(tenantAlpha.getId());
        }
        if (tenantBeta != null && tenantBeta.getId() != null) {
            companyRepository.deleteById(tenantBeta.getId());
        }
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

    @Test
    @DisplayName("6. History record persists with status FAILED when domain transaction rolls back")
    void testHistoryPersistsWhenDomainRollsBack() {
        TenantContext.setCompanyId(tenantAlpha.getId());

        String invalidCsv = "sku,name,price\n" +
                "SKU-GOOD,Good Product,50.00\n" +
                "SKU-DUP,Dup 1,10.00\n" +
                "SKU-DUP,Dup 2,15.00\n";
        MockMultipartFile file = new MockMultipartFile("file", "failed_products.csv", "text/csv",
                invalidCsv.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response = dataImportController.importFile(file, "products");
        assertEquals(400, response.getStatusCode().value());

        // Domain table is completely empty (rolled back)
        assertEquals(0, productRepository.findByCompanyId(tenantAlpha.getId()).size());

        // But History survived and was persisted
        var alphaHistory = csvImportHistoryRepository.findByCompanyIdOrderByStartedAtDesc(tenantAlpha.getId());
        assertEquals(1, alphaHistory.size());
        CsvImportHistory record = alphaHistory.get(0);
        assertEquals("failed_products.csv", record.getFilename());
        assertEquals("products", record.getEntityType());
        assertEquals(CsvImportHistory.ImportStatus.FAILED, record.getStatus());
        assertEquals(3, record.getTotalRows());
        assertEquals(0, record.getRecordsImported());
        assertTrue(record.getRecordsFailed() > 0);
        assertNotNull(record.getErrorSummary());
        assertTrue(record.getErrorSummary().contains("Duplicate SKU 'SKU-DUP'"));
    }

    @Test
    @DisplayName("7. Tenant isolation is enforced on import history query")
    void testTenantIsolationOnImportHistory() {
        // Alpha imports a file
        TenantContext.setCompanyId(tenantAlpha.getId());
        String alphaCsv = "sku,name,price\nSKU-A1,Alpha Part,10.00\n";
        MockMultipartFile fileA = new MockMultipartFile("file", "alpha.csv", "text/csv",
                alphaCsv.getBytes(StandardCharsets.UTF_8));
        dataImportController.importFile(fileA, "products");

        // Beta imports a file
        TenantContext.setCompanyId(tenantBeta.getId());
        String betaCsv = "sku,name,price\nSKU-B1,Beta Part,20.00\n";
        MockMultipartFile fileB = new MockMultipartFile("file", "beta.csv", "text/csv",
                betaCsv.getBytes(StandardCharsets.UTF_8));
        dataImportController.importFile(fileB, "products");

        // Alpha queries history
        UserPrincipal principalAlpha = new UserPrincipal(
                100L, "alpha_user", "alpha@test.com", "pw",
                tenantAlpha.getId(), tenantAlpha.getCode(), "ACTIVE",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );
        var resAlpha = dataImportController.getImportHistory(principalAlpha);
        assertEquals(200, resAlpha.getStatusCode().value());
        assertNotNull(resAlpha.getBody());
        assertEquals(1, resAlpha.getBody().size());
        assertEquals("alpha.csv", resAlpha.getBody().get(0).getOriginalFilename());

        // Beta queries history
        UserPrincipal principalBeta = new UserPrincipal(
                200L, "beta_user", "beta@test.com", "pw",
                tenantBeta.getId(), tenantBeta.getCode(), "ACTIVE",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );
        var resBeta = dataImportController.getImportHistory(principalBeta);
        assertEquals(200, resBeta.getStatusCode().value());
        assertNotNull(resBeta.getBody());
        assertEquals(1, resBeta.getBody().size());
        assertEquals("beta.csv", resBeta.getBody().get(0).getOriginalFilename());
    }

    @Test
    @DisplayName("8. Duplicate file content warns but does not block upsert processing")
    void testDuplicateFileContentWarning_PreservesUpsertSemantics() {
        TenantContext.setCompanyId(tenantAlpha.getId());

        String csvData = "sku,name,price,reorder_level,safety_stock,lead_time_days\n" +
                "SKU-IDEM-1,Micro Controller,45.00,20,5,3\n";
        MockMultipartFile firstUpload = new MockMultipartFile("file", "sensors.csv", "text/csv",
                csvData.getBytes(StandardCharsets.UTF_8));

        // First upload
        ResponseEntity<?> firstResp = dataImportController.importFile(firstUpload, "products");
        assertEquals(200, firstResp.getStatusCode().value());
        CsvImportService.ImportResult firstResult = (CsvImportService.ImportResult) firstResp.getBody();
        assertNotNull(firstResult);
        assertFalse(firstResult.isDuplicateDetected());

        // Second upload of identical content
        MockMultipartFile secondUpload = new MockMultipartFile("file", "sensors_copy.csv", "text/csv",
                csvData.getBytes(StandardCharsets.UTF_8));
        ResponseEntity<?> secondResp = dataImportController.importFile(secondUpload, "products");
        assertEquals(200, secondResp.getStatusCode().value());
        CsvImportService.ImportResult secondResult = (CsvImportService.ImportResult) secondResp.getBody();
        assertNotNull(secondResult);
        assertTrue(secondResult.isSuccess());
        assertTrue(secondResult.isDuplicateDetected());
        assertNotNull(secondResult.getDuplicateWarning());
        assertTrue(secondResult.getDuplicateWarning().contains("An identical CSV file was previously imported"));

        // Business-key upsert updated/retained record: still exactly 1 product with this SKU
        var products = productRepository.findByCompanyId(tenantAlpha.getId());
        assertEquals(1, products.size());
        assertEquals("SKU-IDEM-1", products.get(0).getSku());

        // Two history entries recorded; query history DTOs through controller to verify duplicateFile flag
        UserPrincipal principalAlpha = new UserPrincipal(
                100L, "alpha_user", "alpha@test.com", "pw",
                tenantAlpha.getId(), tenantAlpha.getCode(), "ACTIVE",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );
        var historyDtos = dataImportController.getImportHistory(principalAlpha).getBody();
        assertNotNull(historyDtos);
        assertEquals(2, historyDtos.size());
        assertTrue(historyDtos.get(0).isDuplicateFile());
        assertFalse(historyDtos.get(1).isDuplicateFile());
    }

    @Test
    @DisplayName("9. Successful import records complete history metadata")
    void testHistoryPersistsOnSuccessfulImport() {
        TenantContext.setCompanyId(tenantAlpha.getId());

        String csvData = "sku,name,price,reorder_level,safety_stock,lead_time_days\n" +
                "SKU-P1,Product 1,12.50,10,2,4\n" +
                "SKU-P2,Product 2,24.00,15,5,5\n";
        MockMultipartFile file = new MockMultipartFile("file", "parts.csv", "text/csv",
                csvData.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response = dataImportController.importFile(file, "products");
        assertEquals(200, response.getStatusCode().value());

        var historyList = csvImportHistoryRepository.findByCompanyIdOrderByStartedAtDesc(tenantAlpha.getId());
        assertEquals(1, historyList.size());
        CsvImportHistory h = historyList.get(0);
        assertEquals("parts.csv", h.getFilename());
        assertEquals("products", h.getEntityType());
        assertEquals(CsvImportHistory.ImportStatus.IMPORTED, h.getStatus());
        assertEquals(2, h.getTotalRows());
        assertEquals(2, h.getRecordsImported());
        assertEquals(0, h.getRecordsFailed());
        assertNotNull(h.getFileHash());
        assertEquals(64, h.getFileHash().length());
        assertNotNull(h.getStartedAt());
        assertNotNull(h.getCompletedAt());
    }
}
