package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.security.TenantContext;
import lombok.AllArgsConstructor;
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
import org.springframework.transaction.interceptor.TransactionAspectSupport;

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
    private final CompanyRepository companyRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Data
    @Builder
    public static class ImportResult {
        private boolean success;
        private String entityType;
        private int totalRowsProcessed;
        private int recordsImported;
        private int recordsSkipped;
        private int recordsRejected;
        private int recordsFailed;
        private List<String> errors;
        private String message;
    }

    @Data
    @AllArgsConstructor
    private static class PreparedItem<T> {
        private T entity;
        private Long rawSyntheticId;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSampleDataset() {
        Long companyId = TenantContext.getRequiredCompanyId();
        log.info("[CSV IMPORT ENGINE] Starting sample 12-month synthetic supply-chain dataset import for companyId: {}", companyId);

        List<String> aggregatedErrors = new ArrayList<>();
        int totalImported = 0;

        Map<Long, Long> categoryIdMap = new HashMap<>();
        Map<Long, Long> productIdMap = new HashMap<>();
        Map<Long, Long> supplierIdMap = new HashMap<>();
        Map<Long, Long> warehouseIdMap = new HashMap<>();
        Map<Long, Long> orderIdMap = new HashMap<>();

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
                    ImportResult res = importSingleEntityFileWithMapping(
                            entityType, is,
                            categoryIdMap, productIdMap, supplierIdMap, warehouseIdMap, orderIdMap
                    );
                    if (!res.isSuccess() || !res.getErrors().isEmpty()) {
                        aggregatedErrors.addAll(res.getErrors());
                        log.error("[SAMPLE DATASET FAIL] Failed importing entity '{}': {}", entityType, res.getErrors());
                        break;
                    }
                    totalImported += res.getRecordsImported();
                }
            } catch (Exception ex) {
                log.error("[CSV IMPORT FAIL] Failed to import dataset '{}': {}", entityType, ex.getMessage(), ex);
                aggregatedErrors.add("Error importing " + entityType + ": " + ex.getMessage());
                break;
            }
        }

        if (!aggregatedErrors.isEmpty()) {
            markRollbackOnly();
            return ImportResult.builder()
                    .success(false)
                    .entityType("FULL_SAMPLE_DATASET")
                    .totalRowsProcessed(totalImported)
                    .recordsImported(0)
                    .recordsSkipped(0)
                    .recordsRejected(totalImported)
                    .recordsFailed(aggregatedErrors.size())
                    .errors(aggregatedErrors)
                    .message("Sample dataset import failed with " + aggregatedErrors.size() + " errors. All changes rolled back.")
                    .build();
        }

        return ImportResult.builder()
                .success(true)
                .entityType("FULL_SAMPLE_DATASET")
                .totalRowsProcessed(totalImported)
                .recordsImported(totalImported)
                .recordsSkipped(0)
                .recordsRejected(0)
                .recordsFailed(0)
                .errors(Collections.emptyList())
                .message("Sample dataset import completed successfully. Total records loaded: " + totalImported)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSingleEntityFile(String entityType, InputStream inputStream) {
        return importSingleEntityFileWithMapping(entityType, inputStream, null, null, null, null, null);
    }

    private ImportResult importSingleEntityFileWithMapping(
            String entityType,
            InputStream inputStream,
            Map<Long, Long> categoryIdMap,
            Map<Long, Long> productIdMap,
            Map<Long, Long> supplierIdMap,
            Map<Long, Long> warehouseIdMap,
            Map<Long, Long> orderIdMap) {

        String normalizedType = entityType != null ? entityType.trim().toLowerCase() : "";
        switch (normalizedType) {
            case "categories":
            case "category":
                return importCategoriesInternal(inputStream, categoryIdMap);
            case "products":
            case "product":
                return importProductsInternal(inputStream, categoryIdMap, productIdMap);
            case "suppliers":
            case "supplier":
                return importSuppliersInternal(inputStream, supplierIdMap);
            case "supplier_products":
            case "supplierproduct":
            case "supplier_product":
                return importSupplierProductsInternal(inputStream, supplierIdMap, productIdMap);
            case "warehouses":
            case "warehouse":
                return importWarehousesInternal(inputStream, warehouseIdMap);
            case "inventories":
            case "inventory":
                return importInventoriesInternal(inputStream, productIdMap, warehouseIdMap);
            case "orders":
            case "customer_orders":
            case "order":
                return importOrdersInternal(inputStream, orderIdMap);
            case "order_items":
            case "orderitem":
            case "order_item":
                return importOrderItemsInternal(inputStream, orderIdMap, productIdMap);
            case "shipments":
            case "shipment":
                return importShipmentsInternal(inputStream, supplierIdMap, warehouseIdMap, orderIdMap);
            default:
                return ImportResult.builder()
                        .success(false)
                        .entityType(entityType)
                        .totalRowsProcessed(0)
                        .recordsImported(0)
                        .recordsSkipped(0)
                        .recordsRejected(0)
                        .recordsFailed(1)
                        .errors(List.of("Unsupported entity type: '" + entityType + "'. Supported types: categories, products, suppliers, supplier_products, warehouses, inventories, orders, order_items, shipments."))
                        .message("Invalid entity type")
                        .build();
        }
    }

    // 1. Categories Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importCategories(InputStream is) {
        return importCategoriesInternal(is, null);
    }

    private ImportResult importCategoriesInternal(InputStream is, Map<Long, Long> categoryIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<PreparedItem<Category>> preparedList = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String name = getRequiredString(record, "name", errors);
                    if (name == null) continue;

                    if (!seenNames.add(name.toLowerCase())) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate category name '" + name + "' in CSV file.");
                        continue;
                    }

                    String description = record.isSet("description") ? record.get("description") : null;
                    Long rawId = parseLongOptional(record, "id");

                    Optional<Category> existing = categoryRepository.findByCompanyIdAndName(companyId, name);
                    Category category = existing.orElseGet(() -> Category.builder().company(company).build());
                    category.setCompany(company);
                    category.setName(name);
                    category.setDescription(description);

                    preparedList.add(new PreparedItem<>(category, rawId));
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("categories", processed, errors);
        }

        for (PreparedItem<Category> item : preparedList) {
            Category saved = categoryRepository.save(item.getEntity());
            if (categoryIdMap != null && item.getRawSyntheticId() != null) {
                categoryIdMap.put(item.getRawSyntheticId(), saved.getId());
            }
        }

        return buildSuccessResult("categories", processed, preparedList.size());
    }

    // 2. Products Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importProducts(InputStream is) {
        return importProductsInternal(is, null, null);
    }

    private ImportResult importProductsInternal(InputStream is, Map<Long, Long> categoryIdMap, Map<Long, Long> productIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<PreparedItem<Product>> preparedList = new ArrayList<>();
        Set<String> seenSkus = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String sku = getRequiredString(record, "sku", errors);
                    String name = getRequiredString(record, "name", errors);
                    BigDecimal price = record.isSet("price") ? parseBigDecimal(record, "price", errors)
                            : (record.isSet("unit_price") ? parseBigDecimal(record, "unit_price", errors) : null);
                    if (price == null && !record.isSet("price") && !record.isSet("unit_price")) {
                        errors.add("Row " + record.getRecordNumber() + ": Missing required column 'price'");
                    }

                    if (sku == null || name == null || price == null) continue;

                    if (!seenSkus.add(sku.toUpperCase())) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate SKU '" + sku + "' in CSV file.");
                        continue;
                    }

                    if (price.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": Product price cannot be negative (" + price + ").");
                        continue;
                    }

                    Integer reorderLevel = record.isSet("reorder_level") ? parseInteger(record, "reorder_level", errors)
                            : (record.isSet("reorder_point") ? parseInteger(record, "reorder_point", errors) : null);
                    Integer safetyStock = record.isSet("safety_stock") ? parseInteger(record, "safety_stock", errors)
                            : (record.isSet("target_stock_level") ? parseInteger(record, "target_stock_level", errors) : null);
                    Integer leadTimeDays = parseInteger(record, "lead_time_days", errors);
                    String description = record.isSet("description") ? record.get("description") : null;
                    String uom = record.isSet("unit_of_measure") && !record.get("unit_of_measure").isBlank() ? record.get("unit_of_measure") : "Units";

                    if (reorderLevel != null && reorderLevel < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": reorder_level cannot be negative (" + reorderLevel + ").");
                    }
                    if (safetyStock != null && safetyStock < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": safety_stock cannot be negative (" + safetyStock + ").");
                    }
                    if (leadTimeDays != null && leadTimeDays < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": lead_time_days cannot be negative (" + leadTimeDays + ").");
                    }

                    // Foreign key resolution: category
                    Category category = null;
                    Long rawCatId = parseLongOptional(record, "category_id");
                    if (rawCatId != null) {
                        Long resolvedCatId = categoryIdMap != null && categoryIdMap.containsKey(rawCatId)
                                ? categoryIdMap.get(rawCatId)
                                : rawCatId;
                        Optional<Category> catOpt = categoryRepository.findByCompanyIdAndId(companyId, resolvedCatId);
                        if (catOpt.isPresent()) {
                            category = catOpt.get();
                        } else {
                            errors.add("Row " + record.getRecordNumber() + ": Category not found with id: " + rawCatId + " for this company.");
                        }
                    } else if (record.isSet("category_name") && !record.get("category_name").isBlank()) {
                        String catName = record.get("category_name").trim();
                        Optional<Category> catOpt = categoryRepository.findByCompanyIdAndName(companyId, catName);
                        if (catOpt.isPresent()) {
                            category = catOpt.get();
                        } else {
                            errors.add("Row " + record.getRecordNumber() + ": Category not found with name: '" + catName + "' for this company.");
                        }
                    }

                    Long rawProductId = parseLongOptional(record, "id");

                    Optional<Product> existing = productRepository.findByCompanyIdAndSku(companyId, sku);
                    Product product = existing.orElseGet(() -> Product.builder().company(company).build());
                    product.setCompany(company);
                    product.setSku(sku);
                    product.setName(name);
                    product.setDescription(description);
                    product.setPrice(price);
                    product.setReorderLevel(reorderLevel != null ? reorderLevel : 0);
                    product.setSafetyStock(safetyStock != null ? safetyStock : 0);
                    product.setLeadTimeDays(leadTimeDays != null ? leadTimeDays : 7);
                    product.setUnitOfMeasure(uom);
                    product.setCategory(category);

                    preparedList.add(new PreparedItem<>(product, rawProductId));
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("products", processed, errors);
        }

        for (PreparedItem<Product> item : preparedList) {
            Product saved = productRepository.save(item.getEntity());
            if (productIdMap != null && item.getRawSyntheticId() != null) {
                productIdMap.put(item.getRawSyntheticId(), saved.getId());
            }
        }

        return buildSuccessResult("products", processed, preparedList.size());
    }

    // 3. Suppliers Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSuppliers(InputStream is) {
        return importSuppliersInternal(is, null);
    }

    private ImportResult importSuppliersInternal(InputStream is, Map<Long, Long> supplierIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<PreparedItem<Supplier>> preparedList = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String code = getRequiredString(record, "code", errors);
                    String name = getRequiredString(record, "name", errors);
                    if (code == null || name == null) continue;

                    if (!seenCodes.add(code.toUpperCase())) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate supplier code '" + code + "' in CSV file.");
                        continue;
                    }

                    BigDecimal reliabilityScore = parseBigDecimal(record, "reliability_score", errors);
                    BigDecimal deliveryPerformance = parseBigDecimal(record, "delivery_performance_pct", errors);
                    Double leadTime = parseDouble(record, "average_lead_time_days", errors);
                    Double leadTimeVariance = parseDouble(record, "lead_time_variance_days", errors);

                    if (reliabilityScore != null && (reliabilityScore.compareTo(BigDecimal.ZERO) < 0 || reliabilityScore.compareTo(new BigDecimal("100.00")) > 0)) {
                        errors.add("Row " + record.getRecordNumber() + ": reliability_score must be between 0.00 and 100.00.");
                    }
                    if (deliveryPerformance != null && (deliveryPerformance.compareTo(BigDecimal.ZERO) < 0 || deliveryPerformance.compareTo(new BigDecimal("100.00")) > 0)) {
                        errors.add("Row " + record.getRecordNumber() + ": delivery_performance_pct must be between 0.00 and 100.00.");
                    }
                    if (leadTime != null && leadTime < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": average_lead_time_days cannot be negative.");
                    }
                    if (leadTimeVariance != null && leadTimeVariance < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": lead_time_variance_days cannot be negative.");
                    }

                    Long rawId = parseLongOptional(record, "id");

                    Optional<Supplier> existing = supplierRepository.findByCompanyIdAndCode(companyId, code);
                    Supplier supplier = existing.orElseGet(() -> Supplier.builder().company(company).build());
                    supplier.setCompany(company);
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

                    preparedList.add(new PreparedItem<>(supplier, rawId));
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("suppliers", processed, errors);
        }

        for (PreparedItem<Supplier> item : preparedList) {
            Supplier saved = supplierRepository.save(item.getEntity());
            if (supplierIdMap != null && item.getRawSyntheticId() != null) {
                supplierIdMap.put(item.getRawSyntheticId(), saved.getId());
            }
        }

        return buildSuccessResult("suppliers", processed, preparedList.size());
    }

    // 4. Supplier Products Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSupplierProducts(InputStream is) {
        return importSupplierProductsInternal(is, null, null);
    }

    private ImportResult importSupplierProductsInternal(InputStream is, Map<Long, Long> supplierIdMap, Map<Long, Long> productIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<SupplierProduct> preparedList = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    Long rawSupplierId = parseLongOptional(record, "supplier_id");
                    String supplierCode = record.isSet("supplier_code") ? record.get("supplier_code").trim() : null;

                    Long rawProductId = parseLongOptional(record, "product_id");
                    String productSku = record.isSet("product_sku") ? record.get("product_sku").trim()
                            : (record.isSet("sku") ? record.get("sku").trim() : null);

                    BigDecimal contractPrice = record.isSet("contract_price") ? parseBigDecimal(record, "contract_price", errors)
                            : (record.isSet("price") ? parseBigDecimal(record, "price", errors) : null);
                    if (contractPrice == null && !record.isSet("contract_price") && !record.isSet("price")) {
                        errors.add("Row " + record.getRecordNumber() + ": Missing required column 'contract_price'");
                    }
                    if (contractPrice == null) continue;

                    if (contractPrice.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": contract_price cannot be negative (" + contractPrice + ").");
                        continue;
                    }

                    // Resolve Supplier
                    Supplier supplier = null;
                    if (rawSupplierId != null) {
                        Long resolvedSuppId = supplierIdMap != null && supplierIdMap.containsKey(rawSupplierId)
                                ? supplierIdMap.get(rawSupplierId)
                                : rawSupplierId;
                        supplier = supplierRepository.findByCompanyIdAndId(companyId, resolvedSuppId).orElse(null);
                    } else if (supplierCode != null && !supplierCode.isBlank()) {
                        supplier = supplierRepository.findByCompanyIdAndCode(companyId, supplierCode).orElse(null);
                    }
                    if (supplier == null) {
                        errors.add("Row " + record.getRecordNumber() + ": Supplier not found with id/code '" + (rawSupplierId != null ? rawSupplierId : supplierCode) + "' for this company.");
                        continue;
                    }

                    // Resolve Product
                    Product product = null;
                    if (rawProductId != null) {
                        Long resolvedProdId = productIdMap != null && productIdMap.containsKey(rawProductId)
                                ? productIdMap.get(rawProductId)
                                : rawProductId;
                        product = productRepository.findByCompanyIdAndId(companyId, resolvedProdId).orElse(null);
                    } else if (productSku != null && !productSku.isBlank()) {
                        product = productRepository.findByCompanyIdAndSku(companyId, productSku).orElse(null);
                    }
                    if (product == null) {
                        errors.add("Row " + record.getRecordNumber() + ": Product not found with id/sku '" + (rawProductId != null ? rawProductId : productSku) + "' for this company.");
                        continue;
                    }

                    String pairKey = supplier.getId() + ":" + product.getId();
                    if (!seenPairs.add(pairKey)) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate supplier-product contract pair for supplier " + supplier.getCode() + " and product " + product.getSku() + " in CSV file.");
                        continue;
                    }

                    Integer leadTimeDays = parseInteger(record, "lead_time_days", errors);
                    Integer moq = parseInteger(record, "minimum_order_quantity", errors);
                    Boolean preferred = record.isSet("is_preferred_supplier") && Boolean.parseBoolean(record.get("is_preferred_supplier"));

                    if (moq != null && moq < 1) {
                        errors.add("Row " + record.getRecordNumber() + ": minimum_order_quantity must be at least 1.");
                    }
                    if (leadTimeDays != null && leadTimeDays < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": lead_time_days cannot be negative.");
                    }

                    List<SupplierProduct> existing = supplierProductRepository.findByCompanyIdAndSupplierIdAndProductId(companyId, supplier.getId(), product.getId());
                    SupplierProduct sp = existing.isEmpty() ? SupplierProduct.builder().company(company).build() : existing.get(0);
                    sp.setCompany(company);
                    sp.setSupplier(supplier);
                    sp.setProduct(product);
                    sp.setContractPrice(contractPrice);
                    sp.setLeadTimeDays(leadTimeDays != null ? leadTimeDays : 7);
                    sp.setMinimumOrderQuantity(moq != null ? moq : 1);
                    sp.setIsPreferredSupplier(preferred);

                    preparedList.add(sp);
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("supplier_products", processed, errors);
        }

        for (SupplierProduct sp : preparedList) {
            supplierProductRepository.save(sp);
        }

        return buildSuccessResult("supplier_products", processed, preparedList.size());
    }

    // 5. Warehouses Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importWarehouses(InputStream is) {
        return importWarehousesInternal(is, null);
    }

    private ImportResult importWarehousesInternal(InputStream is, Map<Long, Long> warehouseIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<PreparedItem<Warehouse>> preparedList = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String code = getRequiredString(record, "code", errors);
                    String name = getRequiredString(record, "name", errors);
                    Integer totalCapacity = parseInteger(record, "total_capacity_units", errors);

                    if (code == null || name == null || totalCapacity == null) continue;

                    if (!seenCodes.add(code.toUpperCase())) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate warehouse code '" + code + "' in CSV file.");
                        continue;
                    }

                    if (totalCapacity <= 0) {
                        errors.add("Row " + record.getRecordNumber() + ": total_capacity_units must be greater than zero (" + totalCapacity + ").");
                        continue;
                    }

                    Integer usedCapacity = parseInteger(record, "current_utilization_units", errors);
                    BigDecimal pct = parseBigDecimal(record, "utilization_percentage", errors);

                    if (usedCapacity != null && usedCapacity < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": current_utilization_units cannot be negative.");
                    }
                    if (pct != null && (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(new BigDecimal("100.00")) > 0)) {
                        errors.add("Row " + record.getRecordNumber() + ": utilization_percentage must be between 0.00 and 100.00.");
                    }

                    Long rawId = parseLongOptional(record, "id");

                    Optional<Warehouse> existing = warehouseRepository.findByCompanyIdAndCode(companyId, code);
                    Warehouse warehouse = existing.orElseGet(() -> Warehouse.builder().company(company).build());
                    warehouse.setCompany(company);
                    warehouse.setCode(code);
                    warehouse.setName(name);
                    warehouse.setLocation(record.isSet("location") ? record.get("location") : null);
                    warehouse.setTotalCapacityUnits(totalCapacity);
                    warehouse.setCurrentUtilizationUnits(usedCapacity != null ? usedCapacity : 0);
                    warehouse.setUtilizationPercentage(pct != null ? pct : BigDecimal.ZERO);
                    warehouse.setManagerName(record.isSet("manager_name") ? record.get("manager_name") : null);
                    warehouse.setContactEmail(record.isSet("contact_email") ? record.get("contact_email") : null);

                    preparedList.add(new PreparedItem<>(warehouse, rawId));
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("warehouses", processed, errors);
        }

        for (PreparedItem<Warehouse> item : preparedList) {
            Warehouse saved = warehouseRepository.save(item.getEntity());
            if (warehouseIdMap != null && item.getRawSyntheticId() != null) {
                warehouseIdMap.put(item.getRawSyntheticId(), saved.getId());
            }
        }

        return buildSuccessResult("warehouses", processed, preparedList.size());
    }

    // 6. Inventories Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importInventories(InputStream is) {
        return importInventoriesInternal(is, null, null);
    }

    private ImportResult importInventoriesInternal(InputStream is, Map<Long, Long> productIdMap, Map<Long, Long> warehouseIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<Inventory> preparedList = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    Long rawProductId = parseLongOptional(record, "product_id");
                    String productSku = record.isSet("product_sku") ? record.get("product_sku").trim()
                            : (record.isSet("sku") ? record.get("sku").trim() : null);

                    Long rawWarehouseId = parseLongOptional(record, "warehouse_id");
                    String warehouseCode = record.isSet("warehouse_code") ? record.get("warehouse_code").trim()
                            : (record.isSet("code") ? record.get("code").trim() : null);

                    Integer qtyAvail = record.isSet("quantity_available") ? parseInteger(record, "quantity_available", errors)
                            : (record.isSet("quantity") ? parseInteger(record, "quantity", errors) : null);
                    if (qtyAvail == null && !record.isSet("quantity_available") && !record.isSet("quantity")) {
                        errors.add("Row " + record.getRecordNumber() + ": Missing required column 'quantity_available'");
                    }
                    if (qtyAvail == null) continue;

                    if (qtyAvail < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": quantity_available cannot be negative (" + qtyAvail + ").");
                        continue;
                    }

                    // Resolve Product
                    Product product = null;
                    if (rawProductId != null) {
                        Long resolvedProdId = productIdMap != null && productIdMap.containsKey(rawProductId)
                                ? productIdMap.get(rawProductId)
                                : rawProductId;
                        product = productRepository.findByCompanyIdAndId(companyId, resolvedProdId).orElse(null);
                    } else if (productSku != null && !productSku.isBlank()) {
                        product = productRepository.findByCompanyIdAndSku(companyId, productSku).orElse(null);
                    }
                    if (product == null) {
                        errors.add("Row " + record.getRecordNumber() + ": Product not found with id: " + (rawProductId != null ? rawProductId : productSku) + " for this company.");
                        continue;
                    }

                    // Resolve Warehouse
                    Warehouse warehouse = null;
                    if (rawWarehouseId != null) {
                        Long resolvedWhId = warehouseIdMap != null && warehouseIdMap.containsKey(rawWarehouseId)
                                ? warehouseIdMap.get(rawWarehouseId)
                                : rawWarehouseId;
                        warehouse = warehouseRepository.findByCompanyIdAndId(companyId, resolvedWhId).orElse(null);
                    } else if (warehouseCode != null && !warehouseCode.isBlank()) {
                        warehouse = warehouseRepository.findByCompanyIdAndCode(companyId, warehouseCode).orElse(null);
                    }
                    if (warehouse == null) {
                        errors.add("Row " + record.getRecordNumber() + ": Warehouse not found with id: " + (rawWarehouseId != null ? rawWarehouseId : warehouseCode) + " for this company.");
                        continue;
                    }

                    String pairKey = product.getId() + ":" + warehouse.getId();
                    if (!seenPairs.add(pairKey)) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate inventory record for product " + product.getSku() + " and warehouse " + warehouse.getCode() + " in CSV file.");
                        continue;
                    }

                    Integer reserved = parseInteger(record, "reserved_quantity", errors);
                    Integer reorderLevel = record.isSet("reorder_level") ? parseInteger(record, "reorder_level", errors)
                            : (record.isSet("reorder_point") ? parseInteger(record, "reorder_point", errors) : null);
                    Integer safetyStock = record.isSet("safety_stock") ? parseInteger(record, "safety_stock", errors)
                            : (record.isSet("target_stock_level") ? parseInteger(record, "target_stock_level", errors) : null);
                    LocalDateTime lastRestock = parseDateTime(record, "last_restocked_at", errors);

                    if (reserved != null && reserved < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": reserved_quantity cannot be negative.");
                    }
                    if (reorderLevel != null && reorderLevel < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": reorder_level cannot be negative.");
                    }
                    if (safetyStock != null && safetyStock < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": safety_stock cannot be negative.");
                    }

                    Optional<Inventory> existing = inventoryRepository.findByCompanyIdAndProductIdAndWarehouseId(companyId, product.getId(), warehouse.getId());
                    Inventory inventory = existing.orElseGet(() -> Inventory.builder().company(company).build());
                    inventory.setCompany(company);
                    inventory.setProduct(product);
                    inventory.setWarehouse(warehouse);
                    inventory.setQuantityAvailable(qtyAvail);
                    inventory.setReservedQuantity(reserved != null ? reserved : 0);
                    inventory.setReorderLevel(reorderLevel != null ? reorderLevel : product.getReorderLevel());
                    inventory.setSafetyStock(safetyStock != null ? safetyStock : product.getSafetyStock());
                    inventory.setLastRestockedAt(lastRestock != null ? lastRestock : LocalDateTime.now());

                    preparedList.add(inventory);
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("inventories", processed, errors);
        }

        for (Inventory inv : preparedList) {
            inventoryRepository.save(inv);
        }

        return buildSuccessResult("inventories", processed, preparedList.size());
    }

    // 7. Orders Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importOrders(InputStream is) {
        return importOrdersInternal(is, null);
    }

    private ImportResult importOrdersInternal(InputStream is, Map<Long, Long> orderIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<PreparedItem<CustomerOrder>> preparedList = new ArrayList<>();
        Set<String> seenOrderNumbers = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String orderNumber = getRequiredString(record, "order_number", errors);
                    String customerName = getRequiredString(record, "customer_name", errors);
                    LocalDate orderDate = parseDate(record, "order_date", errors);

                    if (orderNumber == null || customerName == null || orderDate == null) continue;

                    if (!seenOrderNumbers.add(orderNumber.toUpperCase())) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate order_number '" + orderNumber + "' in CSV file.");
                        continue;
                    }

                    LocalDate expectedDelivery = parseDate(record, "expected_delivery_date", errors);
                    if (expectedDelivery != null && expectedDelivery.isBefore(orderDate)) {
                        errors.add("Row " + record.getRecordNumber() + ": expected_delivery_date cannot be earlier than order_date.");
                    }

                    BigDecimal totalAmount = parseBigDecimal(record, "total_amount", errors);
                    if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": total_amount cannot be negative.");
                    }

                    String statusStr = record.isSet("status") ? record.get("status").trim().toUpperCase() : "PROCESSING";
                    CustomerOrder.OrderStatus status;
                    try {
                        status = CustomerOrder.OrderStatus.valueOf(statusStr);
                    } catch (Exception ex) {
                        errors.add("Row " + record.getRecordNumber() + ": Invalid order status '" + statusStr + "'. Allowed: PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED.");
                        continue;
                    }

                    Long rawId = parseLongOptional(record, "id");

                    Optional<CustomerOrder> existing = customerOrderRepository.findByCompanyIdAndOrderNumber(companyId, orderNumber);
                    CustomerOrder order = existing.orElseGet(() -> CustomerOrder.builder().company(company).build());
                    order.setCompany(company);
                    order.setOrderNumber(orderNumber);
                    order.setCustomerName(customerName);
                    order.setOrderDate(orderDate);
                    order.setExpectedDeliveryDate(expectedDelivery);
                    order.setStatus(status);
                    order.setTotalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO);

                    preparedList.add(new PreparedItem<>(order, rawId));
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("orders", processed, errors);
        }

        for (PreparedItem<CustomerOrder> item : preparedList) {
            CustomerOrder saved = customerOrderRepository.save(item.getEntity());
            if (orderIdMap != null && item.getRawSyntheticId() != null) {
                orderIdMap.put(item.getRawSyntheticId(), saved.getId());
            }
        }

        return buildSuccessResult("orders", processed, preparedList.size());
    }

    // 8. Order Items Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importOrderItems(InputStream is) {
        return importOrderItemsInternal(is, null, null);
    }

    private ImportResult importOrderItemsInternal(InputStream is, Map<Long, Long> orderIdMap, Map<Long, Long> productIdMap) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<OrderItem> preparedList = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    Long rawOrderId = parseLongOptional(record, "order_id");
                    String orderNumber = record.isSet("order_number") ? record.get("order_number").trim() : null;

                    Long rawProductId = parseLongOptional(record, "product_id");
                    String productSku = record.isSet("product_sku") ? record.get("product_sku").trim()
                            : (record.isSet("sku") ? record.get("sku").trim() : null);

                    Integer quantity = parseInteger(record, "quantity", errors);
                    BigDecimal unitPrice = record.isSet("unit_price") ? parseBigDecimal(record, "unit_price", errors)
                            : (record.isSet("price") ? parseBigDecimal(record, "price", errors) : null);
                    if (unitPrice == null && !record.isSet("unit_price") && !record.isSet("price")) {
                        errors.add("Row " + record.getRecordNumber() + ": Missing required column 'unit_price'");
                    }

                    if (quantity == null || unitPrice == null) continue;

                    if (quantity <= 0) {
                        errors.add("Row " + record.getRecordNumber() + ": quantity must be greater than zero (" + quantity + ").");
                        continue;
                    }
                    if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": unit_price cannot be negative (" + unitPrice + ").");
                        continue;
                    }

                    // Resolve CustomerOrder
                    CustomerOrder order = null;
                    if (rawOrderId != null) {
                        Long resolvedOrderId = orderIdMap != null && orderIdMap.containsKey(rawOrderId)
                                ? orderIdMap.get(rawOrderId)
                                : rawOrderId;
                        order = customerOrderRepository.findByCompanyIdAndId(companyId, resolvedOrderId).orElse(null);
                    } else if (orderNumber != null && !orderNumber.isBlank()) {
                        order = customerOrderRepository.findByCompanyIdAndOrderNumber(companyId, orderNumber).orElse(null);
                    }
                    if (order == null) {
                        errors.add("Row " + record.getRecordNumber() + ": CustomerOrder not found with id: " + (rawOrderId != null ? rawOrderId : orderNumber) + " for this company.");
                        continue;
                    }

                    // Resolve Product
                    Product product = null;
                    if (rawProductId != null) {
                        Long resolvedProdId = productIdMap != null && productIdMap.containsKey(rawProductId)
                                ? productIdMap.get(rawProductId)
                                : rawProductId;
                        product = productRepository.findByCompanyIdAndId(companyId, resolvedProdId).orElse(null);
                    } else if (productSku != null && !productSku.isBlank()) {
                        product = productRepository.findByCompanyIdAndSku(companyId, productSku).orElse(null);
                    }
                    if (product == null) {
                        errors.add("Row " + record.getRecordNumber() + ": Product not found with id: " + (rawProductId != null ? rawProductId : productSku) + " for this company.");
                        continue;
                    }

                    String pairKey = order.getId() + ":" + product.getId();
                    if (!seenPairs.add(pairKey)) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate order item for order " + order.getOrderNumber() + " and product " + product.getSku() + " in CSV file.");
                        continue;
                    }

                    List<OrderItem> existing = orderItemRepository.findByCompanyIdAndOrderIdAndProductId(companyId, order.getId(), product.getId());
                    OrderItem item = existing.isEmpty() ? OrderItem.builder().company(company).build() : existing.get(0);
                    item.setCompany(company);
                    item.setOrder(order);
                    item.setProduct(product);
                    item.setQuantity(quantity);
                    item.setUnitPrice(unitPrice);

                    preparedList.add(item);
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("order_items", processed, errors);
        }

        for (OrderItem item : preparedList) {
            orderItemRepository.save(item);
        }

        return buildSuccessResult("order_items", processed, preparedList.size());
    }

    // 9. Shipments Import
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importShipments(InputStream is) {
        return importShipmentsInternal(is, null, null, null);
    }

    private ImportResult importShipmentsInternal(
            InputStream is,
            Map<Long, Long> supplierIdMap,
            Map<Long, Long> warehouseIdMap,
            Map<Long, Long> orderIdMap) {

        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found with id: " + companyId));

        List<String> errors = new ArrayList<>();
        List<Shipment> preparedList = new ArrayList<>();
        Set<String> seenTrackingCodes = new HashSet<>();
        int processed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build().parse(reader)) {

            for (CSVRecord record : parser) {
                processed++;
                try {
                    String trackingCode = getRequiredString(record, "tracking_code", errors);
                    if (trackingCode == null) continue;

                    if (!seenTrackingCodes.add(trackingCode.toUpperCase())) {
                        errors.add("Row " + record.getRecordNumber() + ": Duplicate tracking_code '" + trackingCode + "' in CSV file.");
                        continue;
                    }

                    // Optional Foreign Key: Supplier
                    Supplier supplier = null;
                    Long rawSupplierId = parseLongOptional(record, "supplier_id");
                    String supplierCode = record.isSet("supplier_code") ? record.get("supplier_code").trim() : null;
                    if (rawSupplierId != null) {
                        Long resolvedSuppId = supplierIdMap != null && supplierIdMap.containsKey(rawSupplierId)
                                ? supplierIdMap.get(rawSupplierId)
                                : rawSupplierId;
                        Optional<Supplier> suppOpt = supplierRepository.findByCompanyIdAndId(companyId, resolvedSuppId);
                        if (suppOpt.isPresent()) supplier = suppOpt.get();
                        else errors.add("Row " + record.getRecordNumber() + ": Supplier not found with id: " + rawSupplierId + " for this company.");
                    } else if (supplierCode != null && !supplierCode.isBlank()) {
                        Optional<Supplier> suppOpt = supplierRepository.findByCompanyIdAndCode(companyId, supplierCode);
                        if (suppOpt.isPresent()) supplier = suppOpt.get();
                        else errors.add("Row " + record.getRecordNumber() + ": Supplier not found with code: '" + supplierCode + "' for this company.");
                    }

                    // Optional Foreign Key: Destination Warehouse
                    Warehouse warehouse = null;
                    Long rawWhId = parseLongOptional(record, "destination_warehouse_id");
                    String whCode = record.isSet("destination_warehouse_code") ? record.get("destination_warehouse_code").trim()
                            : (record.isSet("warehouse_code") ? record.get("warehouse_code").trim() : null);
                    if (rawWhId != null) {
                        Long resolvedWhId = warehouseIdMap != null && warehouseIdMap.containsKey(rawWhId)
                                ? warehouseIdMap.get(rawWhId)
                                : rawWhId;
                        Optional<Warehouse> whOpt = warehouseRepository.findByCompanyIdAndId(companyId, resolvedWhId);
                        if (whOpt.isPresent()) warehouse = whOpt.get();
                        else errors.add("Row " + record.getRecordNumber() + ": Destination warehouse not found with id: " + rawWhId + " for this company.");
                    } else if (whCode != null && !whCode.isBlank()) {
                        Optional<Warehouse> whOpt = warehouseRepository.findByCompanyIdAndCode(companyId, whCode);
                        if (whOpt.isPresent()) warehouse = whOpt.get();
                        else errors.add("Row " + record.getRecordNumber() + ": Destination warehouse not found with code: '" + whCode + "' for this company.");
                    }

                    // Optional Foreign Key: Order
                    CustomerOrder order = null;
                    Long rawOrderId = parseLongOptional(record, "order_id");
                    String orderNumber = record.isSet("order_number") ? record.get("order_number").trim() : null;
                    if (rawOrderId != null) {
                        Long resolvedOrderId = orderIdMap != null && orderIdMap.containsKey(rawOrderId)
                                ? orderIdMap.get(rawOrderId)
                                : rawOrderId;
                        Optional<CustomerOrder> orderOpt = customerOrderRepository.findByCompanyIdAndId(companyId, resolvedOrderId);
                        if (orderOpt.isPresent()) order = orderOpt.get();
                        else errors.add("Row " + record.getRecordNumber() + ": Order not found with id: " + rawOrderId + " for this company.");
                    } else if (orderNumber != null && !orderNumber.isBlank()) {
                        Optional<CustomerOrder> orderOpt = customerOrderRepository.findByCompanyIdAndOrderNumber(companyId, orderNumber);
                        if (orderOpt.isPresent()) order = orderOpt.get();
                        else errors.add("Row " + record.getRecordNumber() + ": Order not found with order_number: '" + orderNumber + "' for this company.");
                    }

                    LocalDate shippedDate = parseDate(record, "shipped_date", errors);
                    LocalDate estDelivery = parseDate(record, "estimated_delivery_date", errors);
                    LocalDate actDelivery = parseDate(record, "actual_delivery_date", errors);
                    Integer delayDays = parseInteger(record, "delay_days", errors);

                    if (delayDays != null && delayDays < 0) {
                        errors.add("Row " + record.getRecordNumber() + ": delay_days cannot be negative.");
                    }

                    String statusStr = record.isSet("status") ? record.get("status").trim().toUpperCase() : "IN_TRANSIT";
                    Shipment.ShipmentStatus status;
                    try {
                        status = Shipment.ShipmentStatus.valueOf(statusStr);
                    } catch (Exception ex) {
                        errors.add("Row " + record.getRecordNumber() + ": Invalid shipment status '" + statusStr + "'. Allowed: PENDING, IN_TRANSIT, DELIVERED, DELAYED, EXCEPTION, CANCELLED.");
                        continue;
                    }

                    Optional<Shipment> existing = shipmentRepository.findByCompanyIdAndTrackingCode(companyId, trackingCode);
                    Shipment shipment = existing.orElseGet(() -> Shipment.builder().company(company).build());
                    shipment.setCompany(company);
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

                    preparedList.add(shipment);
                } catch (Exception ex) {
                    errors.add("Row " + record.getRecordNumber() + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.add("CSV parsing error: " + ex.getMessage());
        }

        if (processed == 0 && errors.isEmpty()) {
            errors.add("CSV file contains no data rows.");
        }

        if (!errors.isEmpty()) {
            markRollbackOnly();
            return buildFailureResult("shipments", processed, errors);
        }

        for (Shipment s : preparedList) {
            shipmentRepository.save(s);
        }

        return buildSuccessResult("shipments", processed, preparedList.size());
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

    private Long parseLongOptional(CSVRecord record, String column) {
        if (!record.isSet(column) || record.get(column).isBlank()) return null;
        try {
            return Long.parseLong(record.get(column).trim());
        } catch (NumberFormatException ex) {
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

    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (Exception ignored) {
            // Safe when invoked outside Spring transaction boundary in unit tests
        }
    }

    private ImportResult buildFailureResult(String entityType, int processed, List<String> errors) {
        return ImportResult.builder()
                .success(false)
                .entityType(entityType)
                .totalRowsProcessed(processed)
                .recordsImported(0)
                .recordsSkipped(0)
                .recordsRejected(processed)
                .recordsFailed(errors.size())
                .errors(errors)
                .message("CSV import failed with " + errors.size() + " validation errors. All changes rolled back; 0 records written.")
                .build();
    }

    private ImportResult buildSuccessResult(String entityType, int processed, int imported) {
        return ImportResult.builder()
                .success(true)
                .entityType(entityType)
                .totalRowsProcessed(processed)
                .recordsImported(imported)
                .recordsSkipped(processed - imported)
                .recordsRejected(0)
                .recordsFailed(0)
                .errors(Collections.emptyList())
                .message("Successfully imported " + imported + " " + entityType + " records.")
                .build();
    }
}
