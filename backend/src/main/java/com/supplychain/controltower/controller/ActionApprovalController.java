package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.service.ActionApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionApprovalController {

    private final ActionApprovalService actionApprovalService;

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
