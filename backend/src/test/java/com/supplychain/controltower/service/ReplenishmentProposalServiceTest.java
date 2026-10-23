package com.supplychain.controltower.service;

import com.supplychain.controltower.analytics.PurchaseOrderGeneratorEngine;
import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplenishmentProposalServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ForecastService forecastService;

    @Mock
    private PurchaseOrderGeneratorEngine poGeneratorEngine;

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private ReplenishmentProposalService replenishmentProposalService;

    private Company company;
    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        TenantContext.setCompanyId(1L);
        company = Company.builder().id(1L).code("DEMO").name("Demo Corp").build();
        product = Product.builder()
                .id(101L)
                .sku("SKU-CHIP-404")
                .name("Microcontroller v4")
                .safetyStock(50)
                .reorderLevel(100)
                .price(BigDecimal.valueOf(45.0))
                .build();
        inventory = Inventory.builder()
                .id(501L)
                .product(product)
                .company(company)
                .quantityAvailable(10) // below safety stock
                .safetyStock(50)
                .reorderLevel(100)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testGenerateProposalsIncludesProductSkuInPayloadAndAuditLog() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(inventoryRepository.findByCompanyId(1L)).thenReturn(List.of(inventory));
        when(recommendationRepository.findByCompanyIdAndStatus(1L, Recommendation.ApprovalStatus.PENDING_APPROVAL))
                .thenReturn(List.of());

        PurchaseOrderGeneratorEngine.PurchaseOrderPayload payload =
                PurchaseOrderGeneratorEngine.PurchaseOrderPayload.builder()
                        .actionType("REORDER_STOCK")
                        .productId(101L)
                        .productSku("SKU-CHIP-404")
                        .sku("SKU-CHIP-404")
                        .productName("Microcontroller v4")
                        .orderQuantity(120)
                        .contractUnitPrice(BigDecimal.valueOf(45.0))
                        .totalCost(BigDecimal.valueOf(5400.0))
                        .reasoning("Stock below threshold")
                        .build();

        when(poGeneratorEngine.generateReplenishmentPayload(eq(product), eq(inventory), anyInt()))
                .thenReturn(payload);
        when(poGeneratorEngine.convertPayloadToJson(payload))
                .thenReturn("{\"actionType\":\"REORDER_STOCK\",\"productId\":101,\"productSku\":\"SKU-CHIP-404\",\"sku\":\"SKU-CHIP-404\"}");

        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(inv -> {
            Recommendation r = inv.getArgument(0);
            r.setId(999L);
            return r;
        });

        List<Recommendation> proposals = replenishmentProposalService.generateProposalsFromDatabaseStockouts();

        assertNotNull(proposals);
        assertEquals(1, proposals.size());
        Recommendation created = proposals.get(0);
        assertEquals(Recommendation.RecommendationType.REORDER_STOCK, created.getType());
        assertEquals(Recommendation.ApprovalStatus.PENDING_APPROVAL, created.getStatus());

        // Validate action payload JSON contains the actual product SKU
        String json = created.getActionPayloadJson();
        assertNotNull(json);
        assertTrue(json.contains("\"sku\":\"SKU-CHIP-404\""), "Payload JSON must contain 'sku'");
        assertTrue(json.contains("\"productSku\":\"SKU-CHIP-404\""), "Payload JSON must contain 'productSku'");

        // Validate audit log details captured the SKU
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());
        AuditLog savedLog = logCaptor.getValue();
        assertEquals("GENERATED_PURCHASE_ORDER_RECOMMENDATION", savedLog.getActionTaken());
        assertTrue(savedLog.getDetails().contains("SKU-CHIP-404"), "Audit log details must reference the actual SKU");
    }

    @Test
    void testGenerateProposalsSafelySkipsInventoryWithNullProduct() {
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        Inventory invWithoutProduct = Inventory.builder()
                .id(502L)
                .product(null) // null product
                .quantityAvailable(0)
                .build();
        when(inventoryRepository.findByCompanyId(1L)).thenReturn(List.of(invWithoutProduct));

        List<Recommendation> proposals = replenishmentProposalService.generateProposalsFromDatabaseStockouts();

        assertNotNull(proposals);
        assertTrue(proposals.isEmpty(), "Inventories without products should be safely skipped");
        verify(recommendationRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }
}
