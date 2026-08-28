package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Data
    @Builder
    public static class ImportResult {
        private boolean success;
        private String entityType;
        private int totalRowsProcessed;
        private int recordsImported;
        private int recordsFailed;
        private List<String> errors;
        private String message;
    }

    @Transactional
    public ImportResult importSampleDataset() {
        log.info("[CSV IMPORT ENGINE] Starting sample 12-month synthetic supply-chain dataset import...");
        List<String> aggregatedErrors = new ArrayList<>();
        int totalImported = 0;

        String[] filesInOrder = {
                "categories", "products", "suppliers", "supplier_products",
                "warehouses", "inventories", "orders", "order_items", "shipments"
        };

        for (String entityType : filesInOrder) {
            try {
                ClassPathResource resource = new ClassPathResource("datasets/" + entityType + ".csv");
                if (!resource.exists()) {
                    aggregatedErrors.add("Classpath dataset resource missing: datasets/" + entityType + ".csv");
                    continue;
                }
                try (InputStream is = resource.getInputStream()) {
                    ImportResult res = importSingleEntityFile(entityType, is);
                    totalImported += res.getRecordsImported();
                    if (!res.getErrors().isEmpty()) {
                        aggregatedErrors.addAll(res.getErrors());
                    }
                }
            } catch (Exception ex) {
                log.error("[CSV IMPORT FAIL] Failed to import dataset '{}': {}", entityType, ex.getMessage(), ex);
                aggregatedErrors.add("Error importing " + entityType + ": " + ex.getMessage());
            }
        }

        boolean overallSuccess = aggregatedErrors.isEmpty() || totalImported > 0;
        return ImportResult.builder()
                .success(overallSuccess)
                .entityType("FULL_SAMPLE_DATASET")
                .totalRowsProcessed(totalImported)
                .recordsImported(totalImported)
                .recordsFailed(aggregatedErrors.size())
                .errors(aggregatedErrors)
                .message("Sample dataset import completed. Total records loaded: " + totalImported)
                .build();
    }

    @Transactional
    public ImportResult importSingleEntityFile(String entityType, InputStream inputStream) {
        String normalizedType = entityType != null ? entityType.trim().toLowerCase() : "";
        switch (normalizedType) {
            case "categories":
            case "category":
                return importCategories(inputStream);
            case "products":
            case "product":
                return importProducts(inputStream);
            case "suppliers":
            case "supplier":
                return importSuppliers(inputStream);
            case "supplier_products":
            case "supplierproduct":
            case "supplier_product":
                return importSupplierProducts(inputStream);
            case "warehouses":
            case "warehouse":
                return importWarehouses(inputStream);
            case "inventories":
            case "inventory":
                return importInventories(inputStream);
            case "orders":
            case "customer_orders":
            case "order":
                return importOrders(inputStream);
            case "order_items":
            case "orderitem":
            case "order_item":
                return importOrderItems(inputStream);
            case "shipments":
            case "shipment":
                return importShipments(inputStream);
            default:
                return ImportResult.builder()
                        .success(false)
                        .entityType(entityType)
                        .errors(List.of("Unsupported entity type: '" + entityType + "'. Supported types: categories, products, suppliers, supplier_products, warehouses, inventories, orders, order_items, shipments."))
                        .message("Invalid entity type")
                        .build();
        }
    }

    @Transactional
    public ImportResult importCategories(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String name = getRequiredString(record, "name", errors);
                    String description = record.isSet("description") ? record.get("description") : null;

                    if (name == null) continue;

                    Optional<Category> existing = categoryRepository.findByName(name);
                    Category category = existing.orElseGet(() -> Category.builder().build());
                    category.setName(name);
                    category.setDescription(description);

                    categoryRepository.save(category);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("categories", processed, imported, errors);
    }

    @Transactional
    public ImportResult importProducts(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String sku = getRequiredString(record, "sku", errors);
                    String name = getRequiredString(record, "name", errors);
                    BigDecimal price = parseBigDecimal(record, "price", errors);
                    Integer reorderLevel = parseInteger(record, "reorder_level", errors);
                    Integer safetyStock = parseInteger(record, "safety_stock", errors);
                    Integer leadTimeDays = parseInteger(record, "lead_time_days", errors);
                    String description = record.isSet("description") ? record.get("description") : null;
                    String uom = record.isSet("unit_of_measure") ? record.get("unit_of_measure") : "Units";
                    Long categoryId = parseLong(record, "category_id", errors);

                    if (sku == null || name == null || price == null) continue;

                    Category category = null;
                    if (categoryId != null) {
                        category = categoryRepository.findById(categoryId)
                                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));
                    }

                    Optional<Product> existing = productRepository.findBySku(sku);
                    Product product = existing.orElseGet(() -> Product.builder().build());
                    product.setSku(sku);
                    product.setName(name);
                    product.setDescription(description);
                    product.setPrice(price);
                    product.setReorderLevel(reorderLevel != null ? reorderLevel : 0);
                    product.setSafetyStock(safetyStock != null ? safetyStock : 0);
                    product.setLeadTimeDays(leadTimeDays != null ? leadTimeDays : 7);
                    product.setUnitOfMeasure(uom);
                    product.setCategory(category);

                    productRepository.save(product);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("products", processed, imported, errors);
    }

    @Transactional
    public ImportResult importSuppliers(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String code = getRequiredString(record, "code", errors);
                    String name = getRequiredString(record, "name", errors);
                    if (code == null || name == null) continue;

                    BigDecimal reliabilityScore = parseBigDecimal(record, "reliability_score", errors);
                    BigDecimal deliveryPerformance = parseBigDecimal(record, "delivery_performance_pct", errors);
                    Double leadTime = parseDouble(record, "average_lead_time_days", errors);
                    Double leadTimeVariance = parseDouble(record, "lead_time_variance_days", errors);

                    Optional<Supplier> existing = supplierRepository.findByCode(code);
                    Supplier supplier = existing.orElseGet(() -> Supplier.builder().build());
                    supplier.setCode(code);
                    supplier.setName(name);
                    supplier.setContactPerson(record.isSet("contact_person") ? record.get("contact_person") : null);
                    supplier.setEmail(record.isSet("email") ? record.get("email") : null);
                    supplier.setPhone(record.isSet("phone") ? record.get("phone") : null);
                    supplier.setCountry(record.isSet("country") ? record.get("country") : null);
                    supplier.setReliabilityScore(reliabilityScore != null ? reliabilityScore : new BigDecimal("90.00"));
                    supplier.setDeliveryPerformancePct(deliveryPerformance != null ? deliveryPerformance : new BigDecimal("90.00"));
                    supplier.setAverageLeadTimeDays(leadTime != null ? leadTime : 7.0);
                    supplier.setLeadTimeVarianceDays(leadTimeVariance != null ? leadTimeVariance : 1.0);

                    supplierRepository.save(supplier);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("suppliers", processed, imported, errors);
    }

    @Transactional
    public ImportResult importSupplierProducts(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    Long supplierId = parseLong(record, "supplier_id", errors);
                    Long productId = parseLong(record, "product_id", errors);
                    BigDecimal contractPrice = parseBigDecimal(record, "contract_price", errors);
                    Integer leadTimeDays = parseInteger(record, "lead_time_days", errors);

                    if (supplierId == null || productId == null || contractPrice == null) continue;

                    Supplier supplier = supplierRepository.findById(supplierId)
                            .orElseThrow(() -> new IllegalArgumentException("Supplier not found with id: " + supplierId));
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

                    Boolean preferred = record.isSet("is_preferred_supplier") ? Boolean.parseBoolean(record.get("is_preferred_supplier")) : false;
                    Integer moq = parseInteger(record, "minimum_order_quantity", errors);

                    List<SupplierProduct> existing = supplierProductRepository.findBySupplierIdAndProductId(supplierId, productId);
                    SupplierProduct sp = existing.isEmpty() ? SupplierProduct.builder().build() : existing.get(0);
                    sp.setSupplier(supplier);
                    sp.setProduct(product);
                    sp.setContractPrice(contractPrice);
                    sp.setLeadTimeDays(leadTimeDays != null ? leadTimeDays : 7);
                    sp.setMinimumOrderQuantity(moq != null ? moq : 1);
                    sp.setIsPreferredSupplier(preferred);

                    supplierProductRepository.save(sp);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("supplier_products", processed, imported, errors);
    }

    @Transactional
    public ImportResult importWarehouses(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String code = getRequiredString(record, "code", errors);
                    String name = getRequiredString(record, "name", errors);
                    Integer totalCapacity = parseInteger(record, "total_capacity_units", errors);

                    if (code == null || name == null || totalCapacity == null) continue;

                    Integer usedCapacity = parseInteger(record, "current_utilization_units", errors);
                    BigDecimal pct = parseBigDecimal(record, "utilization_percentage", errors);

                    Optional<Warehouse> existing = warehouseRepository.findByCode(code);
                    Warehouse warehouse = existing.orElseGet(() -> Warehouse.builder().build());
                    warehouse.setCode(code);
                    warehouse.setName(name);
                    warehouse.setLocation(record.isSet("location") ? record.get("location") : null);
                    warehouse.setTotalCapacityUnits(totalCapacity);
                    warehouse.setCurrentUtilizationUnits(usedCapacity != null ? usedCapacity : 0);
                    warehouse.setUtilizationPercentage(pct != null ? pct : BigDecimal.ZERO);
                    warehouse.setManagerName(record.isSet("manager_name") ? record.get("manager_name") : null);
                    warehouse.setContactEmail(record.isSet("contact_email") ? record.get("contact_email") : null);

                    warehouseRepository.save(warehouse);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("warehouses", processed, imported, errors);
    }

    @Transactional
    public ImportResult importInventories(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    Long productId = parseLong(record, "product_id", errors);
                    Long warehouseId = parseLong(record, "warehouse_id", errors);
                    Integer qtyAvail = parseInteger(record, "quantity_available", errors);

                    if (productId == null || warehouseId == null || qtyAvail == null) continue;

                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));
                    Warehouse warehouse = warehouseRepository.findById(warehouseId)
                            .orElseThrow(() -> new IllegalArgumentException("Warehouse not found with id: " + warehouseId));

                    Integer reserved = parseInteger(record, "reserved_quantity", errors);
                    Integer reorderLevel = parseInteger(record, "reorder_level", errors);
                    Integer safetyStock = parseInteger(record, "safety_stock", errors);
                    LocalDateTime lastRestock = parseDateTime(record, "last_restocked_at", errors);

                    Optional<Inventory> existing = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId);
                    Inventory inventory = existing.orElseGet(() -> Inventory.builder().build());
                    inventory.setProduct(product);
                    inventory.setWarehouse(warehouse);
                    inventory.setQuantityAvailable(qtyAvail);
                    inventory.setReservedQuantity(reserved != null ? reserved : 0);
                    inventory.setReorderLevel(reorderLevel != null ? reorderLevel : product.getReorderLevel());
                    inventory.setSafetyStock(safetyStock != null ? safetyStock : product.getSafetyStock());
                    inventory.setLastRestockedAt(lastRestock != null ? lastRestock : LocalDateTime.now());

                    inventoryRepository.save(inventory);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("inventories", processed, imported, errors);
    }

    @Transactional
    public ImportResult importOrders(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String orderNumber = getRequiredString(record, "order_number", errors);
                    String customerName = getRequiredString(record, "customer_name", errors);
                    LocalDate orderDate = parseDate(record, "order_date", errors);

                    if (orderNumber == null || customerName == null || orderDate == null) continue;

                    LocalDate expectedDelivery = parseDate(record, "expected_delivery_date", errors);
                    BigDecimal totalAmount = parseBigDecimal(record, "total_amount", errors);
                    String statusStr = record.isSet("status") ? record.get("status").toUpperCase() : "PROCESSING";

                    CustomerOrder.OrderStatus status;
                    try {
                        status = CustomerOrder.OrderStatus.valueOf(statusStr);
                    } catch (Exception ex) {
                        status = CustomerOrder.OrderStatus.PROCESSING;
                    }

                    Optional<CustomerOrder> existing = customerOrderRepository.findByOrderNumber(orderNumber);
                    CustomerOrder order = existing.orElseGet(() -> CustomerOrder.builder().build());
                    order.setOrderNumber(orderNumber);
                    order.setCustomerName(customerName);
                    order.setOrderDate(orderDate);
                    order.setExpectedDeliveryDate(expectedDelivery);
                    order.setStatus(status);
                    order.setTotalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO);

                    customerOrderRepository.save(order);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("orders", processed, imported, errors);
    }

    @Transactional
    public ImportResult importOrderItems(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    Long orderId = parseLong(record, "order_id", errors);
                    Long productId = parseLong(record, "product_id", errors);
                    Integer quantity = parseInteger(record, "quantity", errors);
                    BigDecimal unitPrice = parseBigDecimal(record, "unit_price", errors);

                    if (orderId == null || productId == null || quantity == null || unitPrice == null) continue;

                    CustomerOrder order = customerOrderRepository.findById(orderId)
                            .orElseThrow(() -> new IllegalArgumentException("CustomerOrder not found with id: " + orderId));
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

                    List<OrderItem> existing = orderItemRepository.findByOrderIdAndProductId(orderId, productId);
                    OrderItem item = existing.isEmpty() ? OrderItem.builder().build() : existing.get(0);
                    item.setOrder(order);
                    item.setProduct(product);
                    item.setQuantity(quantity);
                    item.setUnitPrice(unitPrice);

                    orderItemRepository.save(item);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("order_items", processed, imported, errors);
    }

    @Transactional
    public ImportResult importShipments(InputStream is) {
        List<String> errors = new ArrayList<>();
        int imported = 0, processed = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String trackingCode = getRequiredString(record, "tracking_code", errors);
                    if (trackingCode == null) continue;

                    Long supplierId = parseLong(record, "supplier_id", errors);
                    Long warehouseId = parseLong(record, "destination_warehouse_id", errors);
                    Long orderId = parseLong(record, "order_id", errors);

                    Supplier supplier = supplierId != null ? supplierRepository.findById(supplierId).orElse(null) : null;
                    Warehouse warehouse = warehouseId != null ? warehouseRepository.findById(warehouseId).orElse(null) : null;
                    CustomerOrder order = orderId != null ? customerOrderRepository.findById(orderId).orElse(null) : null;

                    LocalDate shippedDate = parseDate(record, "shipped_date", errors);
                    LocalDate estDelivery = parseDate(record, "estimated_delivery_date", errors);
                    LocalDate actDelivery = parseDate(record, "actual_delivery_date", errors);
                    Integer delayDays = parseInteger(record, "delay_days", errors);
                    String statusStr = record.isSet("status") ? record.get("status").toUpperCase() : "IN_TRANSIT";

                    Shipment.ShipmentStatus status;
                    try {
                        status = Shipment.ShipmentStatus.valueOf(statusStr);
                    } catch (Exception ex) {
                        status = Shipment.ShipmentStatus.IN_TRANSIT;
                    }

                    Optional<Shipment> existing = shipmentRepository.findByTrackingCode(trackingCode);
                    Shipment shipment = existing.orElseGet(() -> Shipment.builder().build());
                    shipment.setTrackingCode(trackingCode);
                    shipment.setSupplier(supplier);
                    shipment.setDestinationWarehouse(warehouse);
                    shipment.setOrder(order);
                    shipment.setOrigin(record.isSet("origin") ? record.get("origin") : null);
                    shipment.setDestination(record.isSet("destination") ? record.get("destination") : null);
                    shipment.setShippedDate(shippedDate);
                    shipment.setEstimatedDeliveryDate(estDelivery);
                    shipment.setActualDeliveryDate(actDelivery);
                    shipment.setStatus(status);
                    shipment.setDelayDays(delayDays != null ? delayDays : 0);
                    shipment.setCarrierName(record.isSet("carrier_name") ? record.get("carrier_name") : null);

                    shipmentRepository.save(shipment);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        return buildResult("shipments", processed, imported, errors);
    }

    // Helper Parsing & Validation Methods

    private String getRequiredString(CSVRecord record, String column, List<String> errors) {
        if (!record.isSet(column) || record.get(column).isBlank()) {
            errors.add("Row " + record.getRecordNumber() + ": Missing required column '" + column + "'");
            return null;
        }
        return record.get(column).trim();
    }

    private Integer parseInteger(CSVRecord record, String column, List<String> errors) {
        if (!record.isSet(column) || record.get(column).isBlank()) return null;
        try {
            return Integer.parseInt(record.get(column).trim());
        } catch (NumberFormatException ex) {
            errors.add("Row " + record.getRecordNumber() + ": Invalid integer format for column '" + column + "': '" + record.get(column) + "'");
            return null;
        }
    }

    private Long parseLong(CSVRecord record, String column, List<String> errors) {
        if (!record.isSet(column) || record.get(column).isBlank()) return null;
        try {
            return Long.parseLong(record.get(column).trim());
        } catch (NumberFormatException ex) {
            errors.add("Row " + record.getRecordNumber() + ": Invalid long format for column '" + column + "': '" + record.get(column) + "'");
            return null;
        }
    }

    private Double parseDouble(CSVRecord record, String column, List<String> errors) {
        if (!record.isSet(column) || record.get(column).isBlank()) return null;
        try {
            return Double.parseDouble(record.get(column).trim());
        } catch (NumberFormatException ex) {
            errors.add("Row " + record.getRecordNumber() + ": Invalid double format for column '" + column + "': '" + record.get(column) + "'");
            return null;
        }
    }

    private BigDecimal parseBigDecimal(CSVRecord record, String column, List<String> errors) {
        if (!record.isSet(column) || record.get(column).isBlank()) return null;
        try {
            return new BigDecimal(record.get(column).trim());
        } catch (NumberFormatException ex) {
            errors.add("Row " + record.getRecordNumber() + ": Invalid decimal format for column '" + column + "': '" + record.get(column) + "'");
            return null;
        }
    }

    private LocalDate parseDate(CSVRecord record, String column, List<String> errors) {
        if (!record.isSet(column) || record.get(column).isBlank()) return null;
        try {
            return LocalDate.parse(record.get(column).trim(), DATE_FORMATTER);
        } catch (Exception ex) {
            errors.add("Row " + record.getRecordNumber() + ": Invalid date format (expected yyyy-MM-dd) for column '" + column + "': '" + record.get(column) + "'");
            return null;
        }
    }

    private LocalDateTime parseDateTime(CSVRecord record, String column, List<String> errors) {
        if (!record.isSet(column) || record.get(column).isBlank()) return null;
        try {
            String val = record.get(column).trim();
            if (val.length() == 10) {
                return LocalDate.parse(val, DATE_FORMATTER).atStartOfDay();
            }
            return LocalDateTime.parse(val, DATETIME_FORMATTER);
        } catch (Exception ex) {
            errors.add("Row " + record.getRecordNumber() + ": Invalid datetime format for column '" + column + "': '" + record.get(column) + "'");
            return null;
        }
    }

    private ImportResult buildResult(String entityType, int processed, int imported, List<String> errors) {
        boolean success = errors.isEmpty() && imported > 0;
        String msg = success ? "Successfully imported " + imported + " " + entityType + " records."
                             : "Import finished with " + errors.size() + " validation errors (" + imported + "/" + processed + " records imported).";
        return ImportResult.builder()
                .success(success)
                .entityType(entityType)
                .totalRowsProcessed(processed)
                .recordsImported(imported)
                .recordsFailed(errors.size())
                .errors(errors)
                .message(msg)
                .build();
    }
}
