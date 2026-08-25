package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.AuditLog;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ActionApprovalService {

    private final RecommendationRepository recommendationRepository;
    private final AuditLogRepository auditLogRepository;
    private final InventoryService inventoryService;

    @Transactional
    public Recommendation approveAndExecute(Long recommendationId, String username) {
        Recommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new RuntimeException("Recommendation not found: " + recommendationId));

        if (rec.getStatus() == Recommendation.ApprovalStatus.EXECUTED) {
            throw new RuntimeException("Recommendation has already been executed!");
        }

        rec.setStatus(Recommendation.ApprovalStatus.EXECUTED);
        rec.setExecutedAt(LocalDateTime.now());
        rec.setExecutedBy(username != null ? username : "Manager");

        // Save execution audit log
        auditLogRepository.save(AuditLog.builder()
                .userId(1L)
                .username(username != null ? username : "Manager")
                .actionTaken("APPROVED_AND_EXECUTED_AI_RECOMMENDATION")
                .entityAffected("Recommendation")
                .entityId(rec.getId().toString())
                .details("Action: " + rec.getTitle() + " | Payload: " + rec.getActionPayloadJson())
                .timestamp(LocalDateTime.now())
                .build());

        return recommendationRepository.save(rec);
    }

    @Transactional
    public Recommendation rejectRecommendation(Long recommendationId, String username) {
        Recommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new RuntimeException("Recommendation not found: " + recommendationId));

        rec.setStatus(Recommendation.ApprovalStatus.REJECTED);
        return recommendationRepository.save(rec);
    }
}
