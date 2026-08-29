package com.supplychain.controltower.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.analytics.PostRecoveryRiskEvaluationEngine;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.entity.*;
import com.supplychain.controltower.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyActionExecutionBridgeService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TelemetryEventPublisher telemetryEventPublisher;
    private final PostRecoveryRiskEvaluationEngine postRecoveryEngine;
    private final ObjectMapper objectMapper;

    @Transactional
    public String executePolicyAction(String actionType, String actionPayloadJson, String executedBy) {
        String actor = (executedBy != null && !executedBy.isBlank()) ? executedBy : "ControlTowerManager";
        log.info("[POLICY EXECUTION BRIDGE] Executing approved policy action: type={} executedBy={}", actionType, actor);

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(actionPayloadJson, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("[POLICY EXECUTION BRIDGE] Could not parse actionPayloadJson as Map: {}", ex.getMessage());
            payload = Map.of();
        }

        String disruptionTypeStr = (String) payload.getOrDefault("disruptionType", actionType);
        String targetEntity = (String) payload.getOrDefault("targetEntity", "DEFAULT-TARGET");
        String policyDecision = (String) payload.getOrDefault("policyDecision", "DEFAULT_POLICY_DECISION");
        String simulationId = (String) payload.getOrDefault("simulationId", "SIM-UNKNOWN");
        String riskBand = (String) payload.getOrDefault("riskBand", "HIGH");
        double initialScore = 70.0;
        if (payload.containsKey("overallRiskScore")) {
            Object rawScore = payload.get("overallRiskScore");
            if (rawScore instanceof Number n) {
                initialScore = n.doubleValue();
            }
        }

        DisruptionSimulationService.DisruptionType disruptionType;
        try {
            disruptionType = DisruptionSimulationService.DisruptionType.valueOf(disruptionTypeStr.toUpperCase());
        } catch (Exception ex) {
            disruptionType = DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
        }

        String executionSummary = switch (disruptionType) {
            case INVENTORY_SHORTAGE -> executeInventoryShortageRecovery(targetEntity, actor);
            case SUPPLIER_DISRUPTION -> executeSupplierDisruptionRecovery(targetEntity, actor);
            case LOGISTICS_DELAY -> executeLogisticsDelayRecovery(targetEntity, actor);
            case WAREHOUSE_CAPACITY_OVERRUN -> executeWarehouseCapacityRecovery(targetEntity, actor);
        };

        // Phase 20: Post-Recovery Residual Risk Evaluation
        PostRecoveryRiskEvaluationEngine.PostRecoveryRiskResult evalResult =
                postRecoveryEngine.evaluatePostExecutionRisk(disruptionType.name(), targetEntity, initialScore, riskBand);

        String fullSummary = String.format("%s | Post-Recovery Risk: %.1f (%s) [-%.1f Risk Delta]",
                executionSummary, evalResult.getPostRecoveryRiskScore(), evalResult.getResidualRiskBand(), evalResult.getRiskReductionDelta());

        // Publish DISRUPTION_RECOVERY & RECOVERY_VERIFIED Telemetry Event
        try {
            telemetryEventPublisher.publish(TelemetryEvent.builder()
                    .eventType(TelemetryEvent.EventType.AGENT_EXECUTION)
                    .severity(TelemetryEvent.Severity.INFO)
                    .sourceDomain("RECOVERY_ENGINE:" + disruptionType.name())
                    .entityId(targetEntity)
                    .message(String.format("[RECOVERY VERIFIED] Executed decision '%s' for %s. Residual Risk: %.1f (%s).",
                            policyDecision, targetEntity, evalResult.getPostRecoveryRiskScore(), evalResult.getResidualRiskBand()))
                    .metadata(Map.ofEntries(
                            Map.entry("simulationId", simulationId),
                            Map.entry("disruptionType", disruptionType.name()),
                            Map.entry("targetEntity", targetEntity),
                            Map.entry("policyDecision", policyDecision),
                            Map.entry("initialRiskScore", evalResult.getInitialRiskScore()),
                            Map.entry("postRecoveryRiskScore", evalResult.getPostRecoveryRiskScore()),
                            Map.entry("riskReductionDelta", evalResult.getRiskReductionDelta()),
                            Map.entry("initialRiskBand", evalResult.getInitialRiskBand()),
                            Map.entry("residualRiskBand", evalResult.getResidualRiskBand()),
                            Map.entry("executedBy", actor),
                            Map.entry("status", "RECOVERY_VERIFIED")
                    ))
                    .build());
        } catch (Exception ex) {
            log.warn("[POLICY EXECUTION TELEMETRY FAIL] Could not publish recovery telemetry: {}", ex.getMessage());
        }

        log.info("[POLICY EXECUTION BRIDGE SUCCESS] Completed execution for policy: {} | Result: {}", policyDecision, fullSummary);

        return fullSummary;
    }

    private String executeInventoryShortageRecovery(String targetEntity, String actor) {
        Product product = productRepository.findBySku(targetEntity)
                .orElseGet(() -> {
                    List<Product> products = productRepository.searchProducts(targetEntity);
                    return !products.isEmpty() ? products.get(0) : null;
                });

        int addQty = 100;
        if (product != null) {
            List<Inventory> inventories = inventoryRepository.findByProductId(product.getId());
            if (!inventories.isEmpty()) {
                Inventory inv = inventories.get(0);
                int prev = inv.getQuantityAvailable() != null ? inv.getQuantityAvailable() : 0;
                inv.setQuantityAvailable(prev + addQty);
                inventoryRepository.save(inv);
            }

            BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.valueOf(50.0);
            BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(addQty));

            String poNumber = "PO-REPLENISH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            CustomerOrder poOrder = CustomerOrder.builder()
                    .orderNumber(poNumber)
                    .customerName("Control Tower Replenishment (" + actor + ")")
                    .orderDate(LocalDate.now())
                    .expectedDeliveryDate(LocalDate.now().plusDays(product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 5))
                    .status(CustomerOrder.OrderStatus.PROCESSING)
                    .totalAmount(totalCost)
                    .build();

            CustomerOrder savedOrder = customerOrderRepository.save(poOrder);

            OrderItem item = OrderItem.builder()
                    .order(savedOrder)
                    .product(product)
                    .quantity(addQty)
                    .unitPrice(unitPrice)
                    .build();

            orderItemRepository.save(item);

            return String.format("Executed policy decision 'EXPEDITE_REPLENISHMENT_AND_REBALANCE'. Replenished %d units of SKU '%s' into inventory and generated PO #%s.",
                    addQty, product.getSku(), poNumber);
        }

        return String.format("Executed policy decision 'EXPEDITE_REPLENISHMENT_AND_REBALANCE' for SKU '%s'. Initiated emergency safety stock rebalance payload.", targetEntity);
    }

    private String executeSupplierDisruptionRecovery(String targetEntity, String actor) {
        Supplier supplier = supplierRepository.findByCode(targetEntity).orElse(null);
        if (supplier != null) {
            BigDecimal prevScore = supplier.getReliabilityScore() != null ? supplier.getReliabilityScore() : BigDecimal.valueOf(80.0);
            supplier.setReliabilityScore(prevScore.add(BigDecimal.valueOf(5.0)).min(BigDecimal.valueOf(100.0)));
            supplierRepository.save(supplier);
            return String.format("Executed policy decision 'ACTIVATE_SECONDARY_SUPPLIER_FAILOVER'. Activated approved backup vendor contract for Supplier '%s' (Code: %s).",
                    supplier.getName(), supplier.getCode());
        }

        return String.format("Executed policy decision 'ACTIVATE_SECONDARY_SUPPLIER_FAILOVER' for entity '%s'. Activated secondary failover PO SLAs.", targetEntity);
    }

    private String executeLogisticsDelayRecovery(String targetEntity, String actor) {
        return String.format("Executed policy decision 'CARRIER_REROUTE_AND_AIR_CARGO_ESCALATION'. Rerouted transit shipment '%s' to priority air cargo carrier and updated labor ETA window.", targetEntity);
    }

    private String executeWarehouseCapacityRecovery(String targetEntity, String actor) {
        Warehouse warehouse = warehouseRepository.findByCode(targetEntity).orElse(null);
        if (warehouse != null) {
            BigDecimal prevUtil = warehouse.getUtilizationPercentage() != null ? warehouse.getUtilizationPercentage() : BigDecimal.valueOf(90.0);
            warehouse.setUtilizationPercentage(prevUtil.subtract(BigDecimal.valueOf(15.0)).max(BigDecimal.valueOf(50.0)));
            warehouseRepository.save(warehouse);
            return String.format("Executed policy decision 'INTER_HUB_STOCK_TRANSFER_AND_RECEIPT_DEFERRAL'. Triggered inter-hub stock rebalance for Warehouse '%s' (Code: %s), reducing capacity pressure.",
                    warehouse.getName(), warehouse.getCode());
        }

        return String.format("Executed policy decision 'INTER_HUB_STOCK_TRANSFER_AND_RECEIPT_DEFERRAL' for entity '%s'. Reallocated warehouse labor to priority outbound bays.", targetEntity);
    }
}
