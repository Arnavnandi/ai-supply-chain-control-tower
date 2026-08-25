package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeedDataService implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final RiskAlertRepository riskAlertRepository;
    private final RecommendationRepository recommendationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking system seed data...");

        // 1. Seed Users
        if (userRepository.count() == 0) {
            log.info("Seeding initial administrative users...");
            userRepository.save(User.builder()
                    .username("admin")
                    .email("admin@supplychain.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ROLE_ADMIN)
                    .build());

            userRepository.save(User.builder()
                    .username("manager")
                    .email("manager@supplychain.com")
                    .password(passwordEncoder.encode("manager123"))
                    .role(Role.ROLE_SUPPLY_CHAIN_MANAGER)
                    .build());
        }

        // 2. Seed Categories & Products
        if (productRepository.count() == 0) {
            log.info("Seeding supply chain categories, products, warehouses, and suppliers...");

            Category electronics = categoryRepository.save(Category.builder().name("Electronics & Components").description("Semiconductors and electronic parts").build());
            Category industrial = categoryRepository.save(Category.builder().name("Industrial Equipment").description("Motors, pumps, and mechanical tooling").build());
            Category packaging = categoryRepository.save(Category.builder().name("Packaging & Storage").description("Corrugated boxes and protective packaging").build());

            Product p1 = productRepository.save(Product.builder()
                    .sku("SKU-ELEC-001")
                    .name("Microcontroller Board v2")
                    .description("Dual-core ARM Cortex M4 microcontroller board")
                    .price(new BigDecimal("45.00"))
                    .category(electronics)
                    .reorderLevel(150)
                    .safetyStock(50)
                    .leadTimeDays(7)
                    .unitOfMeasure("Units")
                    .build());

            Product p2 = productRepository.save(Product.builder()
                    .sku("SKU-ELEC-002")
                    .name("Industrial Temperature Sensor")
                    .description("High-precision thermal probe sensor")
                    .price(new BigDecimal("120.00"))
                    .category(electronics)
                    .reorderLevel(80)
                    .safetyStock(30)
                    .leadTimeDays(10)
                    .unitOfMeasure("Units")
                    .build());

            Product p3 = productRepository.save(Product.builder()
                    .sku("SKU-IND-101")
                    .name("Hydraulic Pump Assembly 500W")
                    .description("Heavy-duty hydraulic fluid pump")
                    .price(new BigDecimal("850.00"))
                    .category(industrial)
                    .reorderLevel(20)
                    .safetyStock(5)
                    .leadTimeDays(14)
                    .unitOfMeasure("Units")
                    .build());

            Product p4 = productRepository.save(Product.builder()
                    .sku("SKU-PKG-201")
                    .name("Heavy Duty Storage Crate")
                    .description("Stackable high-density polyethylene crate")
                    .price(new BigDecimal("25.00"))
                    .category(packaging)
                    .reorderLevel(500)
                    .safetyStock(200)
                    .leadTimeDays(5)
                    .unitOfMeasure("Boxes")
                    .build());

            // 3. Seed Warehouses
            Warehouse w1 = warehouseRepository.save(Warehouse.builder()
                    .code("WH-NORTH")
                    .name("North Hub Warehouse")
                    .location("Chicago, IL")
                    .totalCapacityUnits(10000)
                    .currentUtilizationUnits(8400)
                    .utilizationPercentage(new BigDecimal("84.00"))
                    .managerName("John Doe")
                    .contactEmail("wh-north@supplychain.com")
                    .build());

            Warehouse w2 = warehouseRepository.save(Warehouse.builder()
                    .code("WH-SOUTH")
                    .name("South Regional Logistics Center")
                    .location("Dallas, TX")
                    .totalCapacityUnits(15000)
                    .currentUtilizationUnits(6200)
                    .utilizationPercentage(new BigDecimal("41.33"))
                    .managerName("Sarah Smith")
                    .contactEmail("wh-south@supplychain.com")
                    .build());

            Warehouse w3 = warehouseRepository.save(Warehouse.builder()
                    .code("WH-WEST")
                    .name("Pacific Coast Distribution")
                    .location("Oakland, CA")
                    .totalCapacityUnits(8000)
                    .currentUtilizationUnits(7600)
                    .utilizationPercentage(new BigDecimal("95.00"))
                    .managerName("Robert Chen")
                    .contactEmail("wh-west@supplychain.com")
                    .build());

            // 4. Seed Suppliers
            Supplier s1 = supplierRepository.save(Supplier.builder()
                    .code("SUP-TECH")
                    .name("TechComponents Global Ltd.")
                    .contactPerson("Alice Johnson")
                    .email("sales@techcomponents.com")
                    .phone("+1-555-0192")
                    .country("Taiwan")
                    .reliabilityScore(new BigDecimal("94.50"))
                    .deliveryPerformancePct(new BigDecimal("92.00"))
                    .averageLeadTimeDays(8.5)
                    .leadTimeVarianceDays(1.2)
                    .build());

            Supplier s2 = supplierRepository.save(Supplier.builder()
                    .code("SUP-HEAVY")
                    .name("Apex Heavy Dynamics")
                    .contactPerson("Marcus Vance")
                    .email("orders@apexdynamics.com")
                    .phone("+1-555-0144")
                    .country("Germany")
                    .reliabilityScore(new BigDecimal("78.00"))
                    .deliveryPerformancePct(new BigDecimal("75.50"))
                    .averageLeadTimeDays(16.0)
                    .leadTimeVarianceDays(4.5)
                    .build());

            // Supplier Product Contracts
            supplierProductRepository.save(SupplierProduct.builder()
                    .supplier(s1)
                    .product(p1)
                    .contractPrice(new BigDecimal("40.00"))
                    .leadTimeDays(7)
                    .minimumOrderQuantity(100)
                    .isPreferredSupplier(true)
                    .build());

            supplierProductRepository.save(SupplierProduct.builder()
                    .supplier(s2)
                    .product(p3)
                    .contractPrice(new BigDecimal("800.00"))
                    .leadTimeDays(14)
                    .minimumOrderQuantity(10)
                    .isPreferredSupplier(true)
                    .build());

            // 5. Seed Inventory Levels
            inventoryRepository.save(Inventory.builder()
                    .product(p1)
                    .warehouse(w1)
                    .quantityAvailable(45) // LOW STOCK RISK! Below reorder level of 150
                    .reservedQuantity(15)
                    .reorderLevel(150)
                    .safetyStock(50)
                    .lastRestockedAt(LocalDateTime.now().minusDays(12))
                    .build());

            inventoryRepository.save(Inventory.builder()
                    .product(p2)
                    .warehouse(w1)
                    .quantityAvailable(220)
                    .reservedQuantity(20)
                    .reorderLevel(80)
                    .safetyStock(30)
                    .lastRestockedAt(LocalDateTime.now().minusDays(5))
                    .build());

            inventoryRepository.save(Inventory.builder()
                    .product(p3)
                    .warehouse(w3)
                    .quantityAvailable(8) // CRITICAL STOCKOUT RISK! Below safety stock of 5
                    .reservedQuantity(4)
                    .reorderLevel(20)
                    .safetyStock(5)
                    .lastRestockedAt(LocalDateTime.now().minusDays(20))
                    .build());

            inventoryRepository.save(Inventory.builder()
                    .product(p4)
                    .warehouse(w2)
                    .quantityAvailable(2500) // OVERSTOCK! Reorder level is 500
                    .reservedQuantity(100)
                    .reorderLevel(500)
                    .safetyStock(200)
                    .lastRestockedAt(LocalDateTime.now().minusDays(2))
                    .build());

            // 6. Seed Customer Orders & Shipments
            CustomerOrder order1 = customerOrderRepository.save(CustomerOrder.builder()
                    .orderNumber("ORD-2026-001")
                    .customerName("Acme Automation Corp")
                    .orderDate(LocalDate.now().minusDays(3))
                    .expectedDeliveryDate(LocalDate.now().plusDays(2))
                    .status(CustomerOrder.OrderStatus.PROCESSING)
                    .totalAmount(new BigDecimal("4500.00"))
                    .build());

            orderItemRepository.save(OrderItem.builder()
                    .order(order1)
                    .product(p1)
                    .quantity(100)
                    .unitPrice(new BigDecimal("45.00"))
                    .build());

            shipmentRepository.save(Shipment.builder()
                    .trackingCode("TRK-88910-US")
                    .supplier(s2)
                    .destinationWarehouse(w3)
                    .order(order1)
                    .origin("Stuttgart, Germany")
                    .destination("Oakland, CA")
                    .shippedDate(LocalDate.now().minusDays(10))
                    .estimatedDeliveryDate(LocalDate.now().minusDays(1)) // DELAYED!
                    .status(Shipment.ShipmentStatus.DELAYED)
                    .delayDays(3)
                    .carrierName("Global Cargo Express")
                    .build());

            // 7. Seed Initial Risk Alerts & Recommendations
            riskAlertRepository.save(RiskAlert.builder()
                    .riskCategory(RiskAlert.RiskCategory.STOCKOUT)
                    .severityLevel(RiskAlert.SeverityLevel.CRITICAL)
                    .entityType("Product")
                    .entityId(p3.getId())
                    .description("Hydraulic Pump Assembly 500W at Pacific Coast Distribution (WH-WEST) has only 8 units remaining (Below safety threshold).")
                    .recommendationText("Expedite purchase order for 25 units from Apex Heavy Dynamics or reallocate stock from South Regional Hub.")
                    .status(RiskAlert.RiskStatus.ACTIVE)
                    .createdAt(LocalDateTime.now().minusHours(4))
                    .build());

            riskAlertRepository.save(RiskAlert.builder()
                    .riskCategory(RiskAlert.RiskCategory.SHIPMENT_DELAY)
                    .severityLevel(RiskAlert.SeverityLevel.HIGH)
                    .entityType("Shipment")
                    .entityId(1L)
                    .description("Shipment TRK-88910-US from Apex Heavy Dynamics is delayed by 3 days due to customs clearance.")
                    .recommendationText("Contact carrier Global Cargo Express and notify Acme Automation Corp of updated delivery schedule.")
                    .status(RiskAlert.RiskStatus.ACTIVE)
                    .createdAt(LocalDateTime.now().minusHours(2))
                    .build());

            recommendationRepository.save(Recommendation.builder()
                    .title("Reorder 200 units of Microcontroller Board v2")
                    .type(Recommendation.RecommendationType.REORDER_STOCK)
                    .actionPayloadJson("{\"productId\": " + p1.getId() + ", \"supplierId\": " + s1.getId() + ", \"targetWarehouseId\": " + w1.getId() + ", \"quantity\": 200}")
                    .reasoning("Current available inventory (45 units) is below reorder threshold (150 units). Lead time is 7 days.")
                    .status(Recommendation.ApprovalStatus.PENDING_APPROVAL)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build());

            log.info("Seed data creation completed successfully!");
        }
    }
}
