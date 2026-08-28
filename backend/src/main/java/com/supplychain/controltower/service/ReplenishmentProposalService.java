package com.supplychain.controltower.service;

import com.supplychain.controltower.analytics.DemandForecastingEngine;
import com.supplychain.controltower.analytics.PurchaseOrderGeneratorEngine;
import com.supplychain.controltower.entity.AuditLog;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplenishmentProposalService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final ForecastService forecastService;
    private final PurchaseOrderGeneratorEngine poGeneratorEngine;
    private final RecommendationRepository recommendationRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public List<Recommendation> generateProposalsFromDatabaseStockouts() {
        log.info("[REPLENISHMENT PROPOSAL SERVICE] Evaluating database stockouts for purchase order recommendations...");

        List<Inventory> allInventory = inventoryRepository.findAll();
        List<Recommendation> createdProposals = new ArrayList<>();

        for (Inventory inv : allInventory) {
            Product product = inv.getProduct();
            if (product == null) continue;

            int stock = inv.getQuantityAvailable() != null ? inv.getQuantityAvailable() : 0;
            int safetyStock = inv.getSafetyStock() != null ? inv.getSafetyStock() : (product.getSafetyStock() != null ? product.getSafetyStock() : 50);

            if (stock < safetyStock) {
                // Fetch demand forecast
                int forecastDemand = 100;
                try {
                    DemandForecastingEngine.ForecastResult fc = forecastService.getDemandForecast(product.getId());
                    if (fc != null && fc.getProjected30DayDemand() > 0) {
                        forecastDemand = fc.getProjected30DayDemand();
                    }
                } catch (Exception ex) {
                    log.warn("[REPLENISHMENT PROPOSAL] Forecast lookup fallback for Product ID {}: {}", product.getId(), ex.getMessage());
                }

                // Check if active PENDING_APPROVAL recommendation already exists for this product
                List<Recommendation> existingPending = recommendationRepository.findByStatus(Recommendation.ApprovalStatus.PENDING_APPROVAL);
                boolean exists = existingPending.stream().anyMatch(r ->
                        r.getActionPayloadJson() != null && r.getActionPayloadJson().contains("\"productId\":" + product.getId())
                );

                if (!exists) {
                    PurchaseOrderGeneratorEngine.PurchaseOrderPayload payload =
                            poGeneratorEngine.generateReplenishmentPayload(product, inv, forecastDemand);

                    String payloadJson = poGeneratorEngine.convertPayloadToJson(payload);

                    Recommendation recommendation = Recommendation.builder()
                            .title(String.format("Automated Replenishment PO: %s (%d units)", payload.getProductName(), payload.getOrderQuantity()))
                            .type(Recommendation.RecommendationType.REORDER_STOCK)
                            .actionPayloadJson(payloadJson)
                            .reasoning(payload.getReasoning())
                            .status(Recommendation.ApprovalStatus.PENDING_APPROVAL)
                            .createdAt(LocalDateTime.now())
                            .build();

                    Recommendation saved = recommendationRepository.save(recommendation);
                    createdProposals.add(saved);

                    auditLogRepository.save(AuditLog.builder()
                            .userId(1L)
                            .username("SYSTEM_AI_ENGINE")
                            .actionTaken("GENERATED_PURCHASE_ORDER_RECOMMENDATION")
                            .entityAffected("Recommendation")
                            .entityId(saved.getId().toString())
                            .details("Generated PENDING_APPROVAL PO recommendation for SKU '" + payload.getProductSku() + "'")
                            .timestamp(LocalDateTime.now())
                            .build());

                    log.info("[REPLENISHMENT PROPOSAL CREATED] Created recommendation ID={} for SKU={}", saved.getId(), payload.getProductSku());
                }
            }
        }

        return createdProposals;
    }
}
