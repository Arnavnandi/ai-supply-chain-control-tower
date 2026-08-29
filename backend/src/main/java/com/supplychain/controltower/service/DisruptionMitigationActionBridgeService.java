package com.supplychain.controltower.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.entity.AuditLog;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisruptionMitigationActionBridgeService {

    private final RecommendationRepository recommendationRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Recommendation convertPolicyToProposal(
            String simulationId,
            DisruptionSimulationService.DisruptionType disruptionType,
            String targetEntity,
            double riskScore,
            DisruptionMitigationPolicyEngine.RiskBand riskBand,
            String policyDecision,
            List<String> recommendedActions) {

        if (disruptionType == null) {
            throw new IllegalArgumentException("DisruptionType cannot be null for action proposal conversion");
        }
        String entityName = (targetEntity != null && !targetEntity.isBlank()) ? targetEntity : "DEFAULT-TARGET";

        log.info("[POLICY BRIDGE] Converting mitigation policy to persistent PENDING_APPROVAL proposal for {} | Entity: {}",
                disruptionType, entityName);

        Recommendation.RecommendationType recType = mapDisruptionTypeToRecommendationType(disruptionType);

        String title = String.format("[POLICY PROPOSAL] %s for %s", policyDecision, entityName);
        String reasoning = String.format("[RISK %s | Score %.1f] Policy Actions: %s",
                riskBand != null ? riskBand.name() : "UNKNOWN",
                riskScore,
                recommendedActions != null ? String.join(" | ", recommendedActions) : "No actions specified");

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("simulationId", simulationId != null ? simulationId : "SIM-UNKNOWN");
        payloadMap.put("disruptionType", disruptionType.name());
        payloadMap.put("targetEntity", entityName);
        payloadMap.put("overallRiskScore", riskScore);
        payloadMap.put("riskBand", riskBand != null ? riskBand.name() : "UNKNOWN");
        payloadMap.put("policyDecision", policyDecision);
        payloadMap.put("recommendedActions", recommendedActions != null ? recommendedActions : List.of());
        payloadMap.put("executionMode", "RECOMMENDATION_ONLY");

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception ex) {
            log.warn("[POLICY BRIDGE] Jackson serialization failed, fallback to plain string: {}", ex.getMessage());
            payloadJson = String.format("{\"simulationId\":\"%s\",\"targetEntity\":\"%s\",\"policyDecision\":\"%s\"}",
                    simulationId, entityName, policyDecision);
        }

        Recommendation recommendation = Recommendation.builder()
                .title(title)
                .type(recType)
                .actionPayloadJson(payloadJson)
                .reasoning(reasoning)
                .status(Recommendation.ApprovalStatus.PENDING_APPROVAL)
                .createdAt(LocalDateTime.now())
                .build();

        Recommendation saved = recommendationRepository.save(recommendation);

        try {
            auditLogRepository.save(AuditLog.builder()
                    .userId(1L)
                    .username("MitigationPolicyEngine")
                    .actionTaken("PROPOSED_AUTOMATED_DISRUPTION_MITIGATION_POLICY")
                    .entityAffected("Recommendation")
                    .entityId(saved.getId().toString())
                    .details(String.format("Policy decision '%s' for entity '%s' converted to action proposal ID=%d with status PENDING_APPROVAL",
                            policyDecision, entityName, saved.getId()))
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("[POLICY BRIDGE AUDIT FAIL] Could not save audit log: {}", ex.getMessage());
        }

        log.info("[POLICY BRIDGE COMPLETE] Created PENDING_APPROVAL Recommendation ID={} for Policy Decision: {}",
                saved.getId(), policyDecision);

        return saved;
    }

    public Recommendation.RecommendationType mapDisruptionTypeToRecommendationType(
            DisruptionSimulationService.DisruptionType disruptionType) {
        if (disruptionType == null) {
            return Recommendation.RecommendationType.REORDER_STOCK;
        }
        return switch (disruptionType) {
            case SUPPLIER_DISRUPTION -> Recommendation.RecommendationType.CHANGE_SUPPLIER;
            case INVENTORY_SHORTAGE -> Recommendation.RecommendationType.REORDER_STOCK;
            case LOGISTICS_DELAY -> Recommendation.RecommendationType.EXPEDITE_SHIPMENT;
            case WAREHOUSE_CAPACITY_OVERRUN -> Recommendation.RecommendationType.REALLOCATE_INVENTORY;
        };
    }
}
