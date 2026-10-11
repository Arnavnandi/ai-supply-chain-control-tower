package com.supplychain.controltower.service;

import com.supplychain.controltower.entity.AuditLog;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActionApprovalService {

    private final RecommendationRepository recommendationRepository;
    private final AuditLogRepository auditLogRepository;
    private final CompanyRepository companyRepository;
    private final ActionExecutionEngine actionExecutionEngine;

    public List<Recommendation> getPendingRecommendations() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return recommendationRepository.findByCompanyIdAndStatus(companyId, Recommendation.ApprovalStatus.PENDING_APPROVAL);
    }

    public List<Recommendation> getRecommendationHistory() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return recommendationRepository.findByCompanyId(companyId).stream()
                .filter(r -> r.getStatus() != Recommendation.ApprovalStatus.PENDING_APPROVAL)
                .toList();
    }

    public List<AuditLog> getAuditLogs() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return auditLogRepository.findByCompanyIdOrderByTimestampDesc(companyId);
    }

    @Transactional
    public Recommendation approveAndExecute(Long recommendationId, String username) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found: " + companyId));

        log.info("[ACTION APPROVAL] User '{}' approving recommendation ID={} for companyId={}", username, recommendationId, companyId);

        Recommendation rec = recommendationRepository.findByCompanyIdAndId(companyId, recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + recommendationId));

        if (rec.getStatus() == Recommendation.ApprovalStatus.EXECUTED) {
            throw new RuntimeException("Recommendation has already been executed!");
        }

        // Execute inventory replenishment & PO generation
        String executionResult = actionExecutionEngine.executeApprovedAction(
                rec.getType().name(),
                rec.getActionPayloadJson(),
                username
        );

        rec.setStatus(Recommendation.ApprovalStatus.EXECUTED);
        rec.setExecutedAt(LocalDateTime.now());
        rec.setExecutedBy(username != null ? username : "Manager");

        Recommendation saved = recommendationRepository.save(rec);

        String detailsStr = "Title: " + rec.getTitle() + " | Result: " + executionResult + " | Payload: " + rec.getActionPayloadJson();
        if (detailsStr.length() > 1950) {
            detailsStr = detailsStr.substring(0, 1950) + "...";
        }

        // Save execution audit log
        auditLogRepository.save(AuditLog.builder()
                .company(company)
                .userId(1L)
                .username(username != null ? username : "Manager")
                .actionTaken("APPROVED_AND_EXECUTED_AI_RECOMMENDATION")
                .entityAffected("Recommendation")
                .entityId(rec.getId().toString())
                .details(detailsStr)
                .timestamp(LocalDateTime.now())
                .build());

        return saved;
    }

    @Transactional
    public Recommendation rejectRecommendation(Long recommendationId, String username) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found: " + companyId));

        log.info("[ACTION REJECTION] User '{}' rejecting recommendation ID={} for companyId={}", username, recommendationId, companyId);

        Recommendation rec = recommendationRepository.findByCompanyIdAndId(companyId, recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + recommendationId));

        rec.setStatus(Recommendation.ApprovalStatus.REJECTED);
        rec.setExecutedAt(LocalDateTime.now());
        rec.setExecutedBy(username != null ? username : "Manager");

        Recommendation saved = recommendationRepository.save(rec);

        auditLogRepository.save(AuditLog.builder()
                .company(company)
                .userId(1L)
                .username(username != null ? username : "Manager")
                .actionTaken("REJECTED_AI_RECOMMENDATION")
                .entityAffected("Recommendation")
                .entityId(rec.getId().toString())
                .details("Title: " + rec.getTitle() + " | Reason: Rejected by manager review")
                .timestamp(LocalDateTime.now())
                .build());

        return saved;
    }
}

