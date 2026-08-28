package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.AuditLog;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.service.ActionApprovalService;
import com.supplychain.controltower.service.ReplenishmentProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionApprovalController {

    private final ActionApprovalService actionApprovalService;
    private final ReplenishmentProposalService replenishmentProposalService;

    @GetMapping("/pending")
    public ResponseEntity<List<Recommendation>> getPendingActions() {
        return ResponseEntity.ok(actionApprovalService.getPendingRecommendations());
    }

    @GetMapping("/history")
    public ResponseEntity<List<Recommendation>> getActionHistory() {
        return ResponseEntity.ok(actionApprovalService.getRecommendationHistory());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(actionApprovalService.getAuditLogs());
    }

    @PostMapping("/generate-replenishments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPLY_CHAIN_MANAGER')")
    public ResponseEntity<List<Recommendation>> generateReplenishmentProposals() {
        return ResponseEntity.ok(replenishmentProposalService.generateProposalsFromDatabaseStockouts());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPLY_CHAIN_MANAGER')")
    public ResponseEntity<Recommendation> approveAction(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "Manager";
        return ResponseEntity.ok(actionApprovalService.approveAndExecute(id, username));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPLY_CHAIN_MANAGER')")
    public ResponseEntity<Recommendation> rejectAction(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "Manager";
        return ResponseEntity.ok(actionApprovalService.rejectRecommendation(id, username));
    }
}
