package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.Category;
import com.supplychain.controltower.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        supplierRepository = mock(SupplierRepository.class);
        supplierProductRepository = mock(SupplierProductRepository.class);
        warehouseRepository = mock(WarehouseRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        customerOrderRepository = mock(CustomerOrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        shipmentRepository = mock(ShipmentRepository.class);

        csvImportService = new CsvImportService(
                categoryRepository,
                productRepository,
                supplierRepository,
                supplierProductRepository,
                warehouseRepository,
                inventoryRepository,
                customerOrderRepository,
                orderItemRepository,
                shipmentRepository
        );
    }

    @Test
    void testImportCategoriesValidCsv() {
        String csvData = "id,name,description\n" +
                "1,Electronics,Microcontrollers and sensors\n" +
                "2,Industrial,Motors and pumps\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(categoryRepository.findByName(anyString())).thenReturn(Optional.empty());

        CsvImportService.ImportResult result = csvImportService.importCategories(is);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getTotalRowsProcessed());
        assertEquals(2, result.getRecordsImported());
        assertEquals(0, result.getRecordsFailed());
        verify(categoryRepository, times(2)).save(any(Category.class));
    }

    @Test
    void testImportProductsMissingRequiredField() {
        String csvData = "id,name,price\n" +
                "1,Invalid Product,45.00\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importProducts(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("sku"));
    }

    @Test
    void testImportInventoriesProductNotFoundForeignKeyError() {
        String csvData = "id,product_id,warehouse_id,quantity_available,reserved_quantity,reorder_level,safety_stock\n" +
                "1,999,1,100,10,50,20\n";

        InputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        CsvImportService.ImportResult result = csvImportService.importInventories(is);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getTotalRowsProcessed());
        assertEquals(0, result.getRecordsImported());
        assertEquals(1, result.getRecordsFailed());
        assertTrue(result.getErrors().get(0).contains("Product not found with id: 999"));
    }

    @Test
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
    void testImportUnsupportedEntityType() {
        InputStream is = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));

        CsvImportService.ImportResult result = csvImportService.importSingleEntityFile("unknown_entity", is);

        assertFalse(result.isSuccess());
        assertEquals("unknown_entity", result.getEntityType());
        assertTrue(result.getErrors().get(0).contains("Unsupported entity type"));
    }
}
