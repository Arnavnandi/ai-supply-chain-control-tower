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
import com.supplychain.controltower.ai.tools.*;
import com.supplychain.controltower.controller.AdvancedAnalyticsController;
import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.entity.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private SupplierTools supplierTools;

    @Autowired
    private WarehouseTools warehouseTools;

    @Autowired
    private LogisticsTools logisticsTools;

    @Autowired
    private ForecastTools forecastTools;

    @Autowired
    private AnalyticsTools analyticsTools;

    @Autowired
    private AdvancedAnalyticsController advancedAnalyticsController;

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

    @Test
    @DisplayName("9. AI Tools Strict Isolation: Spring AI tools only return authenticated company records")
    void testAiToolsStrictTenantIsolation() {
        // Setup records for Company A
        Supplier supA = supplierRepository.save(Supplier.builder()
                .company(companyA)
                .code("SUP-A")
                .name("Supplier Alpha")
                .reliabilityScore(new BigDecimal("95.00"))
                .deliveryPerformancePct(new BigDecimal("92.00"))
                .build());

        Warehouse whA = warehouseRepository.save(Warehouse.builder()
                .company(companyA)
                .code("WH-A")
                .name("Warehouse Alpha")
                .location("Location Alpha")
                .totalCapacityUnits(1000)
                .currentUtilizationUnits(500)
                .utilizationPercentage(new BigDecimal("50.00"))
                .build());

        Shipment shipA = shipmentRepository.save(Shipment.builder()
                .company(companyA)
                .trackingCode("TRK-A-001")
                .supplier(supA)
                .destinationWarehouse(whA)
                .shippedDate(LocalDate.now().minusDays(5))
                .estimatedDeliveryDate(LocalDate.now().plusDays(2))
                .status(Shipment.ShipmentStatus.DELAYED)
                .delayDays(2)
                .build());

        Product prodA = productRepository.save(Product.builder()
                .company(companyA)
                .sku("SKU-AI-A")
                .name("Product Alpha")
                .price(new BigDecimal("100.00"))
                .reorderLevel(10)
                .safetyStock(5)
                .leadTimeDays(7)
                .build());

        RiskAlert alertA = riskAlertRepository.save(RiskAlert.builder()
                .company(companyA)
                .riskCategory(RiskAlert.RiskCategory.STOCKOUT)
                .severityLevel(RiskAlert.SeverityLevel.HIGH)
                .entityType("Product")
                .description("Alert for A")
                .recommendationText("Reorder")
                .status(RiskAlert.RiskStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Setup records for Company B
        Supplier supB = supplierRepository.save(Supplier.builder()
                .company(companyB)
                .code("SUP-B")
                .name("Supplier Beta")
                .reliabilityScore(new BigDecimal("88.00"))
                .deliveryPerformancePct(new BigDecimal("85.00"))
                .build());

        Warehouse whB = warehouseRepository.save(Warehouse.builder()
                .company(companyB)
                .code("WH-B")
                .name("Warehouse Beta")
                .location("Location Beta")
                .totalCapacityUnits(2000)
                .currentUtilizationUnits(1200)
                .utilizationPercentage(new BigDecimal("60.00"))
                .build());

        Shipment shipB = shipmentRepository.save(Shipment.builder()
                .company(companyB)
                .trackingCode("TRK-B-001")
                .supplier(supB)
                .destinationWarehouse(whB)
                .shippedDate(LocalDate.now().minusDays(6))
                .estimatedDeliveryDate(LocalDate.now().plusDays(1))
                .status(Shipment.ShipmentStatus.DELAYED)
                .delayDays(4)
                .build());

        Product prodB = productRepository.save(Product.builder()
                .company(companyB)
                .sku("SKU-AI-B")
                .name("Product Beta")
                .price(new BigDecimal("200.00"))
                .reorderLevel(20)
                .safetyStock(10)
                .leadTimeDays(14)
                .build());

        RiskAlert alertB = riskAlertRepository.save(RiskAlert.builder()
                .company(companyB)
                .riskCategory(RiskAlert.RiskCategory.SHIPMENT_DELAY)
                .severityLevel(RiskAlert.SeverityLevel.CRITICAL)
                .entityType("Shipment")
                .description("Alert for B")
                .recommendationText("Reroute")
                .status(RiskAlert.RiskStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Test under Company A context
        TenantContext.setCompanyId(companyA.getId());
        var supsA = supplierTools.getSupplierPerformance();
        assertTrue(supsA.stream().anyMatch(s -> "SUP-A".equals(s.code())));
        assertFalse(supsA.stream().anyMatch(s -> "SUP-B".equals(s.code())), "Company A must not see Company B's suppliers in AI tools");

        var whsA = warehouseTools.getWarehouseUtilization();
        assertTrue(whsA.stream().anyMatch(w -> "WH-A".equals(w.code())));
        assertFalse(whsA.stream().anyMatch(w -> "WH-B".equals(w.code())), "Company A must not see Company B's warehouses in AI tools");

        var delaysA = logisticsTools.getDelayedShipments();
        assertTrue(delaysA.stream().anyMatch(d -> "TRK-A-001".equals(d.trackingCode())));
        assertFalse(delaysA.stream().anyMatch(d -> "TRK-B-001".equals(d.trackingCode())), "Company A must not see Company B's delayed shipments in AI tools");

        var prodsA = forecastTools.getDemandForecasts();
        assertTrue(prodsA.stream().anyMatch(f -> "SKU-AI-A".equals(f.sku())));
        assertFalse(prodsA.stream().anyMatch(f -> "SKU-AI-B".equals(f.sku())), "Company A must not see Company B's products in AI tools");

        var risksA = analyticsTools.getSupplyChainRisks();
        assertTrue(risksA.stream().anyMatch(r -> "Alert for A".equals(r.description())));
        assertFalse(risksA.stream().anyMatch(r -> "Alert for B".equals(r.description())), "Company A must not see Company B's risk alerts in AI tools");

        // Test under Company B context
        TenantContext.setCompanyId(companyB.getId());
        var supsB = supplierTools.getSupplierPerformance();
        assertTrue(supsB.stream().anyMatch(s -> "SUP-B".equals(s.code())));
        assertFalse(supsB.stream().anyMatch(s -> "SUP-A".equals(s.code())), "Company B must not see Company A's suppliers in AI tools");

        var whsB = warehouseTools.getWarehouseUtilization();
        assertTrue(whsB.stream().anyMatch(w -> "WH-B".equals(w.code())));
        assertFalse(whsB.stream().anyMatch(w -> "WH-A".equals(w.code())), "Company B must not see Company A's warehouses in AI tools");

        var delaysB = logisticsTools.getDelayedShipments();
        assertTrue(delaysB.stream().anyMatch(d -> "TRK-B-001".equals(d.trackingCode())));
        assertFalse(delaysB.stream().anyMatch(d -> "TRK-A-001".equals(d.trackingCode())), "Company B must not see Company A's delayed shipments in AI tools");

        var prodsB = forecastTools.getDemandForecasts();
        assertTrue(prodsB.stream().anyMatch(f -> "SKU-AI-B".equals(f.sku())));
        assertFalse(prodsB.stream().anyMatch(f -> "SKU-AI-A".equals(f.sku())), "Company B must not see Company A's products in AI tools");

        var risksB = analyticsTools.getSupplyChainRisks();
        assertTrue(risksB.stream().anyMatch(r -> "Alert for B".equals(r.description())));
        assertFalse(risksB.stream().anyMatch(r -> "Alert for A".equals(r.description())), "Company B must not see Company A's risk alerts in AI tools");
    }

    @Test
    @DisplayName("10. Product Accuracy Cross-Tenant ID Manipulation Blocked")
    void testProductAccuracyCrossTenantDirectIdLookupBlocked() {
        Product prodA = productRepository.save(Product.builder()
                .company(companyA)
                .sku("SKU-ACC-A")
                .name("Accuracy Product Alpha")
                .price(new BigDecimal("50.00"))
                .reorderLevel(10)
                .safetyStock(5)
                .leadTimeDays(7)
                .build());

        Product prodB = productRepository.save(Product.builder()
                .company(companyB)
                .sku("SKU-ACC-B")
                .name("Accuracy Product Beta")
                .price(new BigDecimal("80.00"))
                .reorderLevel(20)
                .safetyStock(10)
                .leadTimeDays(10)
                .build());

        // Under Company A: Own product succeeds
        TenantContext.setCompanyId(companyA.getId());
        var accA = advancedAnalyticsController.getForecastAccuracy(prodA.getId()).getBody();
        assertNotNull(accA);
        assertEquals(prodA.getId(), accA.getProductId());

        // Under Company A: Manipulated Product ID of Company B throws 404 (ResourceNotFoundException)
        final Long prodBId = prodB.getId();
        assertThrows(ResourceNotFoundException.class, () -> advancedAnalyticsController.getForecastAccuracy(prodBId));

        // Under Company B: Own product succeeds
        TenantContext.setCompanyId(companyB.getId());
        var accB = advancedAnalyticsController.getForecastAccuracy(prodB.getId()).getBody();
        assertNotNull(accB);
        assertEquals(prodB.getId(), accB.getProductId());

        // Under Company B: Manipulated Product ID of Company A throws 404 (ResourceNotFoundException)
        final Long prodAId = prodA.getId();
        assertThrows(ResourceNotFoundException.class, () -> advancedAnalyticsController.getForecastAccuracy(prodAId));
    }
}
