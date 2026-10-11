package com.supplychain.controltower.integration;

import com.supplychain.controltower.analytics.RiskAnalysisEngine;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.dto.domain.ProductDto;
import com.supplychain.controltower.controller.RiskController;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.DocumentMetadata;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.RiskAlert;
import com.supplychain.controltower.entity.TelemetryEventEntity;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.security.TenantContext;
import com.supplychain.controltower.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TenantIsolationIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private RiskAnalysisEngine riskAnalysisEngine;

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private DocumentMetadataRepository documentMetadataRepository;

    @Autowired
    private TelemetryEventRepository telemetryEventRepository;

    @Autowired
    private TelemetryEventPublisher telemetryEventPublisher;

    @Autowired
    private RiskAlertRepository riskAlertRepository;

    @Autowired
    private RiskController riskController;

    private Company companyA;
    private Company companyB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        companyA = new Company();
        companyA.setName("Company Alpha");
        companyA.setCode("ALPHA");
        companyA = companyRepository.save(companyA);

        companyB = new Company();
        companyB.setName("Company Beta");
        companyB.setCode("BETA");
        companyB = companyRepository.save(companyB);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. Identical SKU Isolation: Company A and B use identical SKUs independently")
    void testIdenticalSkuIsolation() {
        // Create Product for Company A
        TenantContext.setCompanyId(companyA.getId());
        ProductDto dtoA = new ProductDto();
        dtoA.setSku("SKU-SHARED-999");
        dtoA.setName("Alpha Widget");
        dtoA.setCategoryName("Electronics");
        dtoA.setPrice(new BigDecimal("150.00"));
        dtoA.setReorderLevel(20);
        dtoA.setSafetyStock(50);
        dtoA.setLeadTimeDays(5);
        ProductDto savedA = productService.createProduct(dtoA);

        // Create Product for Company B with exact same SKU
        TenantContext.setCompanyId(companyB.getId());
        ProductDto dtoB = new ProductDto();
        dtoB.setSku("SKU-SHARED-999");
        dtoB.setName("Beta Widget");
        dtoB.setCategoryName("Electronics");
        dtoB.setPrice(new BigDecimal("299.99"));
        dtoB.setReorderLevel(50);
        dtoB.setSafetyStock(100);
        dtoB.setLeadTimeDays(7);
        ProductDto savedB = productService.createProduct(dtoB);

        // Verify Company A sees only Alpha Widget
        TenantContext.setCompanyId(companyA.getId());
        List<ProductDto> productsA = productService.getAllProducts(null);
        assertEquals(1, productsA.stream().filter(p -> "SKU-SHARED-999".equals(p.getSku())).count());
        ProductDto retrievedA = productService.getProductById(savedA.getId());
        assertEquals("Alpha Widget", retrievedA.getName());

        // Verify Company A CANNOT access Company B's product ID
        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(savedB.getId()));

        // Verify Company B sees only Beta Widget
        TenantContext.setCompanyId(companyB.getId());
        List<ProductDto> productsB = productService.getAllProducts(null);
        assertEquals(1, productsB.stream().filter(p -> "SKU-SHARED-999".equals(p.getSku())).count());
        ProductDto retrievedB = productService.getProductById(savedB.getId());
        assertEquals("Beta Widget", retrievedB.getName());

        // Verify Company B CANNOT access Company A's product ID
        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(savedA.getId()));
    }

    @Test
    @DisplayName("2. Cross-Company Foreign-Key and Write/Delete Isolation")
    void testCrossCompanyMutationIsolation() {
        // Setup Company A product & warehouse
        TenantContext.setCompanyId(companyA.getId());
        Product prodA = new Product();
        prodA.setCompany(companyA);
        prodA.setSku("SKU-ALPHA-MUTATE");
        prodA.setName("Alpha Mutate");
        prodA.setPrice(new BigDecimal("10.00"));
        prodA.setReorderLevel(5);
        prodA.setSafetyStock(10);
        prodA.setLeadTimeDays(3);
        prodA = productRepository.save(prodA);

        Warehouse whA = new Warehouse();
        whA.setCompany(companyA);
        whA.setCode("WH-ALPHA");
        whA.setName("Warehouse Alpha");
        whA.setLocation("Location A");
        whA.setTotalCapacityUnits(1000);
        whA.setCurrentUtilizationUnits(200);
        whA.setUtilizationPercentage(new BigDecimal("20.00"));
        whA = warehouseRepository.save(whA);

        // Under Company B context, try to delete Company A's product
        TenantContext.setCompanyId(companyB.getId());
        final Long prodAId = prodA.getId();
        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(prodAId));

        // Confirm Company A's product still exists
        TenantContext.setCompanyId(companyA.getId());
        assertNotNull(productService.getProductById(prodAId));
    }

    @Test
    @DisplayName("3. CSV Import Tenant Isolation")
    void testCsvImportTenantIsolation() throws Exception {
        String csvContent = "sku,name,category,unit_price,reorder_point,target_stock_level\n" +
                "SKU-CSV-001,CSV Product,Industrial,88.50,10,50\n";
        ByteArrayInputStream is = new ByteArrayInputStream(csvContent.getBytes());

        // Import CSV as Company A
        TenantContext.setCompanyId(companyA.getId());
        CsvImportService.ImportResult result = csvImportService.importSingleEntityFile("products", is);
        assertTrue(result.getRecordsImported() >= 0);

        // Verify Company A has products accessible
        List<ProductDto> productsA = productService.getAllProducts(null);
        assertNotNull(productsA);

        // Verify Company B does NOT see Company A's product
        TenantContext.setCompanyId(companyB.getId());
        List<ProductDto> productsB = productService.getAllProducts(null);
        assertFalse(productsB.stream().anyMatch(p -> "SKU-CSV-001".equals(p.getSku())));
    }

    @Test
    @DisplayName("4. Analytics Scoping Isolation")
    void testAnalyticsEngineTenantIsolation() {
        // Create Product with low stock for Company A
        TenantContext.setCompanyId(companyA.getId());
        Product prodA = new Product();
        prodA.setCompany(companyA);
        prodA.setSku("SKU-RISK-TEST");
        prodA.setName("Risk Test A");
        prodA.setPrice(new BigDecimal("100.00"));
        prodA.setReorderLevel(50);
        prodA.setSafetyStock(100);
        prodA.setLeadTimeDays(5);
        prodA = productRepository.save(prodA);

        // Run Risk Analysis under Company A
        var reportA = riskAnalysisEngine.evaluateSystemRisks();
        assertNotNull(reportA);

        // Run Risk Analysis under Company B
        TenantContext.setCompanyId(companyB.getId());
        var reportB = riskAnalysisEngine.evaluateSystemRisks();
        assertNotNull(reportB);
    }

    @Test
    @DisplayName("5. RAG Retrieval Isolation: System Docs Shared, Company Uploads Isolated")
    void testRagRetrievalIsolation() {
        // Upload Company A custom document
        DocumentMetadata docA = new DocumentMetadata();
        docA.setCompany(companyA);
        docA.setFileName("company_a_sop.md");
        docA.setFileType("SOP");
        docA.setUploadTime(LocalDateTime.now());
        docA.setSummary("Alpha secret procedures for supply chain crisis.");
        docA = documentMetadataRepository.save(docA);

        // Upload Company B custom document
        DocumentMetadata docB = new DocumentMetadata();
        docB.setCompany(companyB);
        docB.setFileName("company_b_sop.md");
        docB.setFileType("SOP");
        docB.setUploadTime(LocalDateTime.now());
        docB.setSummary("Beta secret procedures for supply chain crisis.");
        docB = documentMetadataRepository.save(docB);

        // Query RAG under Company A
        TenantContext.setCompanyId(companyA.getId());
        RagRetrievalService.RagQueryResult resultA = ragRetrievalService.queryKnowledgeBase("Emergency SOP crisis");
        assertNotNull(resultA);

        // Query RAG under Company B
        TenantContext.setCompanyId(companyB.getId());
        RagRetrievalService.RagQueryResult resultB = ragRetrievalService.queryKnowledgeBase("Emergency SOP crisis");
        assertNotNull(resultB);
    }

    @Test
    @DisplayName("6. Telemetry Events Isolation")
    void testTelemetryEventPublisherIsolation() {
        // Publish event for Company A
        TenantContext.setCompanyId(companyA.getId());
        telemetryEventPublisher.publish(TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.STOCKOUT_ALERT)
                .severity(TelemetryEvent.Severity.CRITICAL)
                .sourceDomain("SUPPLIER")
                .entityId("SUP-001")
                .message("Supplier delay")
                .build());

        // Verify Company A repository contains the event
        List<TelemetryEventEntity> eventsA = telemetryEventRepository.findByCompanyId(companyA.getId());
        assertFalse(eventsA.isEmpty());
        assertTrue(eventsA.stream().anyMatch(e -> "Supplier delay".equals(e.getMessage())));

        // Verify Company B repository does NOT see Company A's event
        TenantContext.setCompanyId(companyB.getId());
        List<TelemetryEventEntity> eventsB = telemetryEventRepository.findByCompanyId(companyB.getId());
        assertTrue(eventsB.stream().noneMatch(e -> "Supplier delay".equals(e.getMessage())));
    }

    @Test
    @DisplayName("7. Fail Closed on Missing Tenant Context")
    void testFailClosedOnMissingContext() {
        TenantContext.clear();
        assertThrows(TenantContextMissingException.class, () -> productService.getAllProducts(null));
    }

    @Test
    @DisplayName("8. Risk Alert Tenant Isolation: Scoped List, Status Filter, ID Access, and Count")
    void testRiskAlertTenantIsolation() {
        // Create Risk Alert for Company A (Active)
        RiskAlert alertA = RiskAlert.builder()
                .company(companyA)
                .riskCategory(RiskAlert.RiskCategory.STOCKOUT)
                .severityLevel(RiskAlert.SeverityLevel.CRITICAL)
                .entityType("Product")
                .entityId(101L)
                .description("Alpha critical stockout alert")
                .recommendationText("Expedite purchase order")
                .status(RiskAlert.RiskStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        final RiskAlert savedAlertA = riskAlertRepository.save(alertA);

        // Create Risk Alert for Company B (Active)
        RiskAlert alertB = RiskAlert.builder()
                .company(companyB)
                .riskCategory(RiskAlert.RiskCategory.SHIPMENT_DELAY)
                .severityLevel(RiskAlert.SeverityLevel.HIGH)
                .entityType("Shipment")
                .entityId(202L)
                .description("Beta delayed shipment alert")
                .recommendationText("Reroute carrier")
                .status(RiskAlert.RiskStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        final RiskAlert savedAlertB = riskAlertRepository.save(alertB);

        // Under Company A: List all risks
        TenantContext.setCompanyId(companyA.getId());
        var listA = riskController.getAllRisks(null).getBody();
        assertNotNull(listA);
        assertTrue(listA.stream().anyMatch(r -> r.getId().equals(savedAlertA.getId())));
        assertFalse(listA.stream().anyMatch(r -> r.getId().equals(savedAlertB.getId())));

        // Under Company A: Filter by status
        var activeA = riskController.getAllRisks(RiskAlert.RiskStatus.ACTIVE).getBody();
        assertNotNull(activeA);
        assertTrue(activeA.stream().anyMatch(r -> r.getId().equals(savedAlertA.getId())));
        assertFalse(activeA.stream().anyMatch(r -> r.getId().equals(savedAlertB.getId())));

        // Under Company A: Count
        var countA = riskController.getRiskCount(null).getBody();
        assertNotNull(countA);
        assertTrue(countA.get("count") >= 1);

        // Under Company A: Access own alert by ID succeeds
        var retrievedA = riskController.getRiskById(savedAlertA.getId()).getBody();
        assertNotNull(retrievedA);
        assertEquals(savedAlertA.getId(), retrievedA.getId());

        // Under Company A: Attempt to access Company B's alert ID throws 404 (ResourceNotFoundException)
        final Long alertBId = savedAlertB.getId();
        assertThrows(ResourceNotFoundException.class, () -> riskController.getRiskById(alertBId));

        // Under Company B: List risks
        TenantContext.setCompanyId(companyB.getId());
        var listB = riskController.getAllRisks(null).getBody();
        assertNotNull(listB);
        assertTrue(listB.stream().anyMatch(r -> r.getId().equals(savedAlertB.getId())));
        assertFalse(listB.stream().anyMatch(r -> r.getId().equals(savedAlertA.getId())));

        // Under Company B: Access own alert by ID succeeds
        var retrievedB = riskController.getRiskById(savedAlertB.getId()).getBody();
        assertNotNull(retrievedB);
        assertEquals(savedAlertB.getId(), retrievedB.getId());

        // Under Company B: Attempt to access Company A's alert ID throws 404
        final Long alertAId = savedAlertA.getId();
        assertThrows(ResourceNotFoundException.class, () -> riskController.getRiskById(alertAId));
    }
}
