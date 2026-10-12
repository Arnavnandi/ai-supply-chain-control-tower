package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CsvImportServiceTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private SupplierRepository supplierRepository;
    private SupplierProductRepository supplierProductRepository;
    private WarehouseRepository warehouseRepository;
    private InventoryRepository inventoryRepository;
    private CustomerOrderRepository customerOrderRepository;
    private OrderItemRepository orderItemRepository;
    private ShipmentRepository shipmentRepository;
    private CompanyRepository companyRepository;
    private CsvImportService csvImportService;

    private Company demoCompany;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        supplierProductRepository = mock(SupplierProductRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        customerOrderRepository = mock(CustomerOrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        shipmentRepository = mock(ShipmentRepository.class);
        companyRepository = mock(CompanyRepository.class);

        demoCompany = Company.builder().id(1L).code("DEMO").name("Demo Corp").status("ACTIVE").build();
        when(companyRepository.findById(1L)).thenReturn(Optional.of(demoCompany));

        csvImportService = new CsvImportService(
                categoryRepository,
                productRepository,
                supplierRepository,
                supplierProductRepository,
                warehouseRepository,
                inventoryRepository,
                customerOrderRepository,
                orderItemRepository,
                shipmentRepository,
                companyRepository
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. Valid Categories CSV import succeeds and saves entities")
    void testImportCategoriesValidCsv() {
        String csvData = "id,name,description\n" +
                "1,Electronics,Microcontrollers and sensors\n" +
                "2,Industrial,Motors and pumps\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(categoryRepository.findByCompanyIdAndName(eq(1L), anyString())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CsvImportService.ImportResult result = csvImportService.importCategories(is);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalRowsProcessed());
        assertEquals(2, result.getRecordsImported());
        assertEquals(0, result.getRecordsFailed());
        assertEquals(0, result.getRecordsRejected());
        verify(categoryRepository, times(2)).save(any(Category.class));
    }

    @Test
    @DisplayName("2. Categories CSV with duplicate names fails and rolls back")
    void testImportCategoriesDuplicateNames() {
        String csvData = "id,name,description\n" +
                "1,Electronics,Microcontrollers and sensors\n" +
                "2,electronics,Duplicate lower case name\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importCategories(is);

        assertFalse(result.isSuccess());
        assertEquals(2, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertEquals(2, result.getRecordsRejected());
        assertEquals(1, result.getRecordsFailed());
        assertTrue(result.getErrors().get(0).contains("Duplicate category name"));
    }

    @Test
    @DisplayName("3. Valid Products CSV import succeeds and saves entities")
    void testImportProductsValidCsv() {
        String csvData = "sku,name,price,reorder_level,safety_stock,lead_time_days\n" +
                "SKU-TEST-001,High Tech Sensor,125.50,10,5,7\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(productRepository.findByCompanyIdAndSku(eq(1L), eq("SKU-TEST-001"))).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        CsvImportService.ImportResult result = csvImportService.importProducts(is);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(1, result.getRecordsImported());
        assertEquals(0, result.getRecordsFailed());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    @DisplayName("4. Products CSV missing required SKU fails and rolls back")
    void testImportProductsMissingRequiredSku() {
        String csvData = "id,name,price\n" +
                "1,Invalid Product,45.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importProducts(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertEquals(1, result.getRecordsRejected());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("sku"));
    }

    @Test
    @DisplayName("5. Products CSV with duplicate SKU in file fails and rolls back")
    void testImportProductsDuplicateSkuInFile() {
        String csvData = "sku,name,price\n" +
                "SKU-DUP-01,Sensor Alpha,45.00\n" +
                "SKU-DUP-01,Sensor Beta,50.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importProducts(is);

        assertFalse(result.isSuccess());
        assertEquals(2, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertEquals(2, result.getRecordsRejected());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Duplicate SKU 'SKU-DUP-01'")));
    }

    @Test
    @DisplayName("6. Products CSV with negative price fails and rolls back")
    void testImportProductsNegativePrice() {
        String csvData = "sku,name,price\n" +
                "SKU-NEG,Negative Price Widget,-15.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importProducts(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("price cannot be negative"));
    }

    @Test
    @DisplayName("7. Products CSV referencing non-existent category_id fails and rolls back")
    void testImportProductsCategoryNotFound() {
        String csvData = "sku,name,price,category_id\n" +
                "SKU-CAT-ERR,Gadget,99.00,9999\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(categoryRepository.findByCompanyIdAndId(eq(1L), eq(9999L))).thenReturn(Optional.empty());

        CsvImportService.ImportResult result = csvImportService.importProducts(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("Category not found with id: 9999"));
    }

    @Test
    @DisplayName("8. Suppliers CSV with invalid reliability score (>100) fails and rolls back")
    void testImportSuppliersInvalidReliabilityScore() {
        String csvData = "code,name,reliability_score\n" +
                "SUP-INVALID,Faulty Supplier,150.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importSuppliers(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("reliability_score must be between 0.00 and 100.00"));
    }

    @Test
    @DisplayName("9. Warehouses CSV with total capacity zero fails and rolls back")
    void testImportWarehousesZeroCapacity() {
        String csvData = "code,name,total_capacity_units\n" +
                "WH-ZERO,Zero Warehouse,0\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importWarehouses(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("total_capacity_units must be greater than zero"));
    }

    @Test
    @DisplayName("10. Inventories CSV product not found foreign key error rolls back")
    void testImportInventoriesProductNotFoundForeignKeyError() {
        String csvData = "product_id,warehouse_id,quantity_available\n" +
                "999,1,100\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(productRepository.findByCompanyIdAndId(eq(1L), eq(999L))).thenReturn(Optional.empty());

        CsvImportService.ImportResult result = csvImportService.importInventories(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertEquals(1, result.getRecordsFailed());
        assertTrue(result.getErrors().get(0).contains("Product not found with id: 999"));
    }

    @Test
    @DisplayName("11. Inventories CSV warehouse not found foreign key error rolls back")
    void testImportInventoriesWarehouseNotFoundForeignKeyError() {
        String csvData = "product_id,warehouse_id,quantity_available\n" +
                "10,8888,100\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(productRepository.findByCompanyIdAndId(eq(1L), eq(10L))).thenReturn(Optional.of(Product.builder().id(10L).sku("SKU-10").build()));
        when(warehouseRepository.findByCompanyIdAndId(eq(1L), eq(8888L))).thenReturn(Optional.empty());

        CsvImportService.ImportResult result = csvImportService.importInventories(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("Warehouse not found with id: 8888"));
    }

    @Test
    @DisplayName("12. Inventories CSV duplicate (product, warehouse) pair fails and rolls back")
    void testImportInventoriesDuplicatePair() {
        String csvData = "product_id,warehouse_id,quantity_available\n" +
                "10,1,100\n" +
                "10,1,200\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(productRepository.findByCompanyIdAndId(eq(1L), eq(10L))).thenReturn(Optional.of(Product.builder().id(10L).sku("SKU-10").build()));
        when(warehouseRepository.findByCompanyIdAndId(eq(1L), eq(1L))).thenReturn(Optional.of(Warehouse.builder().id(1L).code("WH-1").build()));

        CsvImportService.ImportResult result = csvImportService.importInventories(is);

        assertFalse(result.isSuccess());
        assertEquals(2, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("Duplicate inventory record"));
    }

    @Test
    @DisplayName("13. Orders CSV invalid date format fails and rolls back")
    void testImportOrdersInvalidDateFormat() {
        String csvData = "id,order_number,customer_name,order_date,expected_delivery_date,status,total_amount\n" +
                "1,ORD-001,Acme Corp,invalid-date,2026-05-10,PROCESSING,500.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importOrders(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("Invalid date format"));
    }

    @Test
    @DisplayName("14. Orders CSV expected delivery before order date fails and rolls back")
    void testImportOrdersExpectedDeliveryBeforeOrderDate() {
        String csvData = "order_number,customer_name,order_date,expected_delivery_date,status,total_amount\n" +
                "ORD-DATE-ERR,Acme Corp,2026-06-15,2026-06-10,PROCESSING,500.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importOrders(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("expected_delivery_date cannot be earlier than order_date"));
    }

    @Test
    @DisplayName("15. Order Items CSV with quantity zero fails and rolls back")
    void testImportOrderItemsQuantityZero() {
        String csvData = "order_id,product_id,quantity,unit_price\n" +
                "1,2,0,50.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importOrderItems(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("quantity must be greater than zero"));
    }

    @Test
    @DisplayName("16. Shipments CSV with invalid status enum fails and rolls back")
    void testImportShipmentsInvalidStatus() {
        String csvData = "tracking_code,status\n" +
                "TRK-INVALID-STAT,FLYING_IN_SPACE\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importShipments(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("Invalid shipment status 'FLYING_IN_SPACE'"));
    }

    @Test
    @DisplayName("17. Empty CSV file returns clear row error and 0 records")
    void testImportEmptyFileReturnsError() {
        String csvData = "";
        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importProducts(is);

        assertFalse(result.isSuccess());
        assertEquals(0, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertTrue(result.getErrors().get(0).contains("no data rows"));
    }

    @Test
    @DisplayName("18. Unsupported entity type returns clear error")
    void testImportUnsupportedEntityType() {
        InputStream is = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importSingleEntityFile("unknown_entity", is);

        assertFalse(result.isSuccess());
        assertEquals("unknown_entity", result.getEntityType());
        assertTrue(result.getErrors().get(0).contains("Unsupported entity type"));
    }

    @Test
    @DisplayName("19. Missing TenantContext throws TenantContextMissingException preventing unauthorized import")
    void testImportUnauthorizedMissingTenantContext() {
        TenantContext.clear();
        InputStream is = new ByteArrayInputStream("sku,name,price\nSKU-1,Prod,10.00\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(TenantContextMissingException.class, () -> csvImportService.importProducts(is));
    }

    @Test
    @DisplayName("20. Cross-company reference attempt is rejected and rolls back")
    void testImportCrossCompanyReferencePrevented() {
        // Company 2 is authenticated
        TenantContext.setCompanyId(2L);
        Company company2 = Company.builder().id(2L).code("COMP2").name("Company 2").build();
        when(companyRepository.findById(2L)).thenReturn(Optional.of(company2));

        // Company 2 attempts to import inventory referencing Product ID 99 which belongs to Company 1
        String csvData = "product_id,warehouse_id,quantity_available\n" +
                "99,1,50\n";
        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        // ProductRepository scoped check for Company 2 returns empty (because 99 belongs to Company 1)
        when(productRepository.findByCompanyIdAndId(eq(2L), eq(99L))).thenReturn(Optional.empty());

        CsvImportService.ImportResult result = csvImportService.importInventories(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertEquals(1, result.getRecordsRejected());
        assertTrue(result.getErrors().get(0).contains("Product not found with id: 99 for this company"));
        verify(inventoryRepository, never()).save(any());
    }
}
