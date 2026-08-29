package com.supplychain.controltower.analytics;

import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.repository.*;
import com.supplychain.controltower.service.DisruptionMitigationPolicyEngine;
import com.supplychain.controltower.service.DisruptionSimulationService;
import com.supplychain.controltower.service.TelemetryEventPublisher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CascadingDisruptionCorrelationEngine {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final DisruptionMitigationPolicyEngine policyEngine;
    private final TelemetryEventPublisher telemetryPublisher;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CascadeNode {
        private int hopLevel;
        private String domain; // SUPPLIER, INVENTORY, LOGISTICS, WAREHOUSE
        private String targetEntity;
        private double nodeRiskScore;
        private String riskBand;
        private String propagationReasoning;
        private String recommendedMitigation;
        private Long recommendationId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CascadingDisruptionResult {
        private String simulationId;
        private String primaryDisruption;
        private String primaryTarget;
        private double cumulativeRiskScore;
        private String cumulativeRiskBand;
        private int impactedDomainsCount;
        private List<CascadeNode> cascadeNodes;
        private boolean chainedActionProposalsCreated;
        private List<Long> generatedRecommendationIds;
        @Builder.Default
        private String timestamp = LocalDateTime.now().toString();
    }

    public CascadingDisruptionResult analyzeCascadingDisruption(
            String primaryDisruptionStr, String primaryTarget, boolean convertToActionProposals) {

        String target = (primaryTarget != null && !primaryTarget.isBlank()) ? primaryTarget : "SKU-ELEC-001";
        String simulationId = "SIM-CASCADE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("[CASCADE CORRELATION ENGINE] Analyzing multi-disruption cascade. Primary: {} | Target: {} | ConvertToProposals: {}",
                primaryDisruptionStr, target, convertToActionProposals);

        DisruptionSimulationService.DisruptionType primaryType;
        try {
            primaryType = DisruptionSimulationService.DisruptionType.valueOf(primaryDisruptionStr.toUpperCase());
        } catch (Exception ex) {
            primaryType = DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
        }

        List<CascadeNode> nodes = new ArrayList<>();
        Set<String> visitedEntities = new HashSet<>();
        List<Long> recommendationIds = new ArrayList<>();

        // Hop 0: Primary Originating Disruption Node
        visitedEntities.add(primaryType.name() + ":" + target);
        double primaryScore = 75.0;
        String primaryBand = "HIGH";

        CascadeNode node0 = CascadeNode.builder()
                .hopLevel(0)
                .domain(getDomainFromDisruption(primaryType))
                .targetEntity(target)
                .nodeRiskScore(primaryScore)
                .riskBand(primaryBand)
                .propagationReasoning(String.format("Originating disruption node: %s targeting %s.", primaryType, target))
                .recommendedMitigation("Initiate primary containment SLA and activate operational failure protocol.")
                .build();

        if (convertToActionProposals) {
            try {
                DisruptionMitigationPolicyEngine.MitigationPolicyResult polRes =
                        policyEngine.evaluateAndMitigate(primaryType, target, true);
                if (polRes.getRecommendationId() != null) {
                    node0.setRecommendationId(polRes.getRecommendationId());
                    recommendationIds.add(polRes.getRecommendationId());
                }
            } catch (Exception ex) {
                log.warn("[CASCADE PROPAGATION WARN] Could not create proposal for hop 0: {}", ex.getMessage());
            }
        }
        nodes.add(node0);

        // Hop 1: Direct 1st-hop Downstream Entity Impact
        DisruptionSimulationService.DisruptionType hop1Type = getCascadingDisruptionType(primaryType, 1);
        String hop1Target = resolveCascadingTarget(primaryType, target, 1);
        String entityKey1 = hop1Type.name() + ":" + hop1Target;

        if (!visitedEntities.contains(entityKey1)) {
            visitedEntities.add(entityKey1);
            double hop1Score = 65.0;
            CascadeNode node1 = CascadeNode.builder()
                    .hopLevel(1)
                    .domain(getDomainFromDisruption(hop1Type))
                    .targetEntity(hop1Target)
                    .nodeRiskScore(hop1Score)
                    .riskBand("HIGH")
                    .propagationReasoning(String.format("Direct 1st-hop cascade: %s failure propagates into %s at target %s.",
                            primaryType, hop1Type, hop1Target))
                    .recommendedMitigation(String.format("Execute secondary mitigation policy for %s on entity %s.", hop1Type, hop1Target))
                    .build();

            if (convertToActionProposals) {
                try {
                    DisruptionMitigationPolicyEngine.MitigationPolicyResult polRes1 =
                            policyEngine.evaluateAndMitigate(hop1Type, hop1Target, true);
                    if (polRes1.getRecommendationId() != null) {
                        node1.setRecommendationId(polRes1.getRecommendationId());
                        recommendationIds.add(polRes1.getRecommendationId());
                    }
                } catch (Exception ex) {
                    log.warn("[CASCADE PROPAGATION WARN] Could not create proposal for hop 1: {}", ex.getMessage());
                }
            }
            nodes.add(node1);
        }

        // Hop 2: Secondary 2nd-hop Operational Bottleneck Impact
        DisruptionSimulationService.DisruptionType hop2Type = getCascadingDisruptionType(primaryType, 2);
        String hop2Target = resolveCascadingTarget(primaryType, target, 2);
        String entityKey2 = hop2Type.name() + ":" + hop2Target;

        if (!visitedEntities.contains(entityKey2)) {
            visitedEntities.add(entityKey2);
            double hop2Score = 55.0;
            CascadeNode node2 = CascadeNode.builder()
                    .hopLevel(2)
                    .domain(getDomainFromDisruption(hop2Type))
                    .targetEntity(hop2Target)
                    .nodeRiskScore(hop2Score)
                    .riskBand("MEDIUM")
                    .propagationReasoning(String.format("Secondary 2nd-hop cascade: Cumulative failure propagates to %s bottleneck at %s.",
                            hop2Type, hop2Target))
                    .recommendedMitigation(String.format("Rebalance buffer capacity and activate rerouting for %s.", hop2Target))
                    .build();

            if (convertToActionProposals) {
                try {
                    DisruptionMitigationPolicyEngine.MitigationPolicyResult polRes2 =
                            policyEngine.evaluateAndMitigate(hop2Type, hop2Target, true);
                    if (polRes2.getRecommendationId() != null) {
                        node2.setRecommendationId(polRes2.getRecommendationId());
                        recommendationIds.add(polRes2.getRecommendationId());
                    }
                } catch (Exception ex) {
                    log.warn("[CASCADE PROPAGATION WARN] Could not create proposal for hop 2: {}", ex.getMessage());
                }
            }
            nodes.add(node2);
        }

        // Calculate Cumulative Cascading Risk Score (Bounded & Deterministic)
        double cumulativeScore = Math.min(100.0, nodes.stream().mapToDouble(CascadeNode::getNodeRiskScore).sum() / (1.5));
        String cumulativeBand = (cumulativeScore >= 80.0) ? "CRITICAL" : (cumulativeScore >= 60.0) ? "HIGH" : "MEDIUM";

        // Broadcast DISRUPTION_CASCADE Telemetry Event
        try {
            telemetryPublisher.publish(TelemetryEvent.builder()
                    .eventType(TelemetryEvent.EventType.STOCKOUT_ALERT)
                    .severity(TelemetryEvent.Severity.CRITICAL)
                    .sourceDomain("CASCADE_CORRELATION_ENGINE:" + primaryType.name())
                    .entityId(target)
                    .message(String.format("[DISRUPTION CASCADE DETECTED] Multi-domain failure chain: %s -> %d impacted nodes. Cumulative Risk: %.1f (%s).",
                            primaryType, nodes.size(), cumulativeScore, cumulativeBand))
                    .metadata(Map.ofEntries(
                            Map.entry("simulationId", simulationId),
                            Map.entry("primaryDisruption", primaryType.name()),
                            Map.entry("primaryTarget", target),
                            Map.entry("cumulativeRiskScore", cumulativeScore),
                            Map.entry("cumulativeRiskBand", cumulativeBand),
                            Map.entry("impactedDomainsCount", nodes.size()),
                            Map.entry("recommendationIds", recommendationIds.toString()),
                            Map.entry("status", "CASCADE_ANALYZED")
                    ))
                    .build());
        } catch (Exception ex) {
            log.warn("[CASCADE TELEMETRY WARN] Could not publish cascade telemetry: {}", ex.getMessage());
        }

        CascadingDisruptionResult result = CascadingDisruptionResult.builder()
                .simulationId(simulationId)
                .primaryDisruption(primaryType.name())
                .primaryTarget(target)
                .cumulativeRiskScore(cumulativeScore)
                .cumulativeRiskBand(cumulativeBand)
                .impactedDomainsCount(nodes.size())
                .cascadeNodes(nodes)
                .chainedActionProposalsCreated(!recommendationIds.isEmpty())
                .generatedRecommendationIds(recommendationIds)
                .build();

        log.info("[CASCADE CORRELATION COMPLETE] SimId: {} | ChainedNodes: {} | CumulativeRisk: {} | Band: {}",
                simulationId, nodes.size(), cumulativeScore, cumulativeBand);

        return result;
    }

    private String getDomainFromDisruption(DisruptionSimulationService.DisruptionType type) {
        return switch (type) {
            case SUPPLIER_DISRUPTION -> "SUPPLIER";
            case INVENTORY_SHORTAGE -> "INVENTORY";
            case LOGISTICS_DELAY -> "LOGISTICS";
            case WAREHOUSE_CAPACITY_OVERRUN -> "WAREHOUSE";
        };
    }

    private DisruptionSimulationService.DisruptionType getCascadingDisruptionType(
            DisruptionSimulationService.DisruptionType primary, int hop) {
        if (hop == 1) {
            return switch (primary) {
                case SUPPLIER_DISRUPTION -> DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
                case INVENTORY_SHORTAGE -> DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY;
                case LOGISTICS_DELAY -> DisruptionSimulationService.DisruptionType.WAREHOUSE_CAPACITY_OVERRUN;
                case WAREHOUSE_CAPACITY_OVERRUN -> DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
            };
        } else {
            return switch (primary) {
                case SUPPLIER_DISRUPTION -> DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY;
                case INVENTORY_SHORTAGE -> DisruptionSimulationService.DisruptionType.WAREHOUSE_CAPACITY_OVERRUN;
                case LOGISTICS_DELAY -> DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
                case WAREHOUSE_CAPACITY_OVERRUN -> DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY;
            };
        }
    }

    private String resolveCascadingTarget(DisruptionSimulationService.DisruptionType primary, String primaryTarget, int hop) {
        if (hop == 1) {
            if (primary == DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION) {
                return "SKU-ELEC-001";
            } else if (primary == DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE) {
                return "TRK-ROUTE-009";
            } else {
                return "WH-NORTH-01";
            }
        } else {
            if (primary == DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION) {
                return "TRK-ROUTE-009";
            } else {
                return "WH-NORTH-01";
            }
        }
    }
}
