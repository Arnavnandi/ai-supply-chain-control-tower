package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.TelemetryEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisruptionMitigationPolicyEngine {

    private final DisruptionSimulationService disruptionSimulationService;
    private final TelemetryEventPublisher telemetryEventPublisher;
    private final DisruptionMitigationActionBridgeService actionBridgeService;

    public enum RiskBand {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum ExecutionMode {
        RECOMMENDATION_ONLY
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MitigationPolicyResult {
        private String simulationId;
        private DisruptionSimulationService.DisruptionType disruptionType;
        private String targetEntity;
        private Double overallRiskScore;
        private RiskBand riskBand;
        private String policyDecision;
        private List<String> recommendedActions;
        private ExecutionMode executionMode;
        private boolean telemetryPublished;
        private boolean proposalCreated;
        private Long recommendationId;
        @Builder.Default
        private String timestamp = LocalDateTime.now().toString();
    }

    public MitigationPolicyResult evaluateAndMitigate(
            DisruptionSimulationService.DisruptionType disruptionType, String targetEntity) {
        return evaluateAndMitigate(disruptionType, targetEntity, false);
    }

    public MitigationPolicyResult evaluateAndMitigate(
            DisruptionSimulationService.DisruptionType disruptionType, String targetEntity, boolean convertToProposal) {

        String entityName = (targetEntity != null && !targetEntity.isBlank()) ? targetEntity : "DEFAULT-TARGET";
        log.info("[POLICY ENGINE] Evaluating mitigation policy for disruptionType: {} | Target: {} | convertToProposal: {}",
                disruptionType, entityName, convertToProposal);

        // 1. Execute existing disruption simulation & multi-agent consensus pipeline
        DisruptionSimulationService.DisruptionSimulationResult simResult =
                disruptionSimulationService.simulateDisruption(disruptionType, entityName);

        double overallRiskScore = 50.0;
        if (simResult != null && simResult.getConsensusSynthesis() != null
                && simResult.getConsensusSynthesis().getOverallSystemRiskScore() != null) {
            overallRiskScore = simResult.getConsensusSynthesis().getOverallSystemRiskScore();
        }

        // 2. Classify risk band deterministically based on system risk score
        RiskBand riskBand = classifyRiskBand(overallRiskScore);

        // 3. Determine policy decision & ordered recommended actions based on disruption type
        String policyDecision = determinePolicyDecision(disruptionType);
        List<String> recommendedActions = generateRecommendedActions(disruptionType, entityName, riskBand);

        String simulationId = (simResult != null && simResult.getSimulationId() != null)
                ? simResult.getSimulationId()
                : "SIM-UNKNOWN";

        // 4. Optionally convert evaluated policy decision to persistent PENDING_APPROVAL Recommendation proposal
        boolean proposalCreated = false;
        Long recommendationId = null;
        if (convertToProposal) {
            try {
                var rec = actionBridgeService.convertPolicyToProposal(
                        simulationId, disruptionType, entityName, overallRiskScore, riskBand, policyDecision, recommendedActions);
                if (rec != null) {
                    proposalCreated = true;
                    recommendationId = rec.getId();
                }
            } catch (Exception ex) {
                log.warn("[POLICY ENGINE BRIDGE FAIL] Could not convert policy to proposal: {}", ex.getMessage());
            }
        }

        // 5. Publish mitigation telemetry event via existing pipeline
        publishMitigationTelemetry(disruptionType, entityName, simulationId, overallRiskScore, riskBand, policyDecision);

        log.info("[POLICY ENGINE COMPLETE] simId: {} | Decision: {} | RiskBand: {} | ProposalCreated: {} | RecId: {}",
                simulationId, policyDecision, riskBand, proposalCreated, recommendationId);

        return MitigationPolicyResult.builder()
                .simulationId(simulationId)
                .disruptionType(disruptionType)
                .targetEntity(entityName)
                .overallRiskScore(overallRiskScore)
                .riskBand(riskBand)
                .policyDecision(policyDecision)
                .recommendedActions(recommendedActions)
                .executionMode(ExecutionMode.RECOMMENDATION_ONLY)
                .telemetryPublished(true)
                .proposalCreated(proposalCreated)
                .recommendationId(recommendationId)
                .build();
    }

    private RiskBand classifyRiskBand(double score) {
        if (score >= 75.0) {
            return RiskBand.CRITICAL;
        } else if (score >= 50.0) {
            return RiskBand.HIGH;
        } else if (score >= 25.0) {
            return RiskBand.MEDIUM;
        } else {
            return RiskBand.LOW;
        }
    }

    private String determinePolicyDecision(DisruptionSimulationService.DisruptionType type) {
        return switch (type) {
            case SUPPLIER_DISRUPTION -> "ACTIVATE_SECONDARY_SUPPLIER_FAILOVER";
            case INVENTORY_SHORTAGE -> "EXPEDITE_REPLENISHMENT_AND_REBALANCE";
            case LOGISTICS_DELAY -> "CARRIER_REROUTE_AND_AIR_CARGO_ESCALATION";
            case WAREHOUSE_CAPACITY_OVERRUN -> "INTER_HUB_STOCK_TRANSFER_AND_RECEIPT_DEFERRAL";
        };
    }

    private List<String> generateRecommendedActions(
            DisruptionSimulationService.DisruptionType type, String targetEntity, RiskBand riskBand) {

        List<String> actions = new ArrayList<>();
        switch (type) {
            case SUPPLIER_DISRUPTION -> {
                actions.add("[POLICY] Activate approved backup vendor contract for entity: " + targetEntity);
                actions.add("[POLICY] Expedite & reprioritize pending purchase order delivery SLAs");
                actions.add("[POLICY] Obtain updated vendor arrival ETA & track OTIF compliance");
                if (riskBand == RiskBand.CRITICAL || riskBand == RiskBand.HIGH) {
                    actions.add("[POLICY] Escalate procurement vendor performance review to Executive Director");
                }
            }
            case INVENTORY_SHORTAGE -> {
                actions.add("[POLICY] Issue expedited purchase order for low-stock SKU: " + targetEntity);
                actions.add("[POLICY] Rebalance regional safety stock buffer across active warehouse hubs");
                actions.add("[POLICY] Prioritize stock allocation for critical downstream customer fulfillment");
                if (riskBand == RiskBand.CRITICAL || riskBand == RiskBand.HIGH) {
                    actions.add("[POLICY] Trigger automated emergency reorder point threshold adjustment");
                }
            }
            case LOGISTICS_DELAY -> {
                actions.add("[POLICY] Reroute delayed transit shipment " + targetEntity + " to priority air cargo carrier");
                actions.add("[POLICY] Request real-time GPS telemetry ping & updated delivery ETA from carrier");
                actions.add("[POLICY] Issue SLA breach notice to primary logistics provider");
                if (riskBand == RiskBand.CRITICAL || riskBand == RiskBand.HIGH) {
                    actions.add("[POLICY] Notify destination warehouse hub of revised arrival window & labor schedule");
                }
            }
            case WAREHOUSE_CAPACITY_OVERRUN -> {
                actions.add("[POLICY] Trigger inter-hub inventory rebalance from overrun facility: " + targetEntity);
                actions.add("[POLICY] Activate approved regional overflow storage facility");
                actions.add("[POLICY] Defer non-critical inbound supplier shipments by 48 hours");
                if (riskBand == RiskBand.CRITICAL || riskBand == RiskBand.HIGH) {
                    actions.add("[POLICY] Reallocate warehouse labor to priority outbound dispatch bays");
                }
            }
        }
        return actions;
    }

    private void publishMitigationTelemetry(
            DisruptionSimulationService.DisruptionType type,
            String targetEntity,
            String simulationId,
            double riskScore,
            RiskBand riskBand,
            String decision) {

        TelemetryEvent.EventType eventType = switch (type) {
            case INVENTORY_SHORTAGE -> TelemetryEvent.EventType.STOCKOUT_ALERT;
            case LOGISTICS_DELAY -> TelemetryEvent.EventType.SHIPMENT_DELAY;
            default -> TelemetryEvent.EventType.AGENT_EXECUTION;
        };

        TelemetryEvent.Severity severity = (riskBand == RiskBand.CRITICAL || riskBand == RiskBand.HIGH)
                ? TelemetryEvent.Severity.CRITICAL
                : TelemetryEvent.Severity.WARNING;

        try {
            telemetryEventPublisher.publish(TelemetryEvent.builder()
                    .eventType(eventType)
                    .severity(severity)
                    .sourceDomain("POLICY_ENGINE:" + type.name())
                    .entityId(targetEntity)
                    .message("[POLICY MITIGATION] Evaluated " + type + " | Decision: " + decision + " | RiskBand: " + riskBand)
                    .metadata(Map.of(
                            "simulationId", simulationId,
                            "riskScore", riskScore,
                            "riskBand", riskBand.name(),
                            "policyDecision", decision,
                            "executionMode", "RECOMMENDATION_ONLY"
                    ))
                    .build());
        } catch (Exception ex) {
            log.warn("[POLICY ENGINE TELEMETRY FAIL] Could not publish telemetry event: {}", ex.getMessage());
        }
    }
}
