package com.supplychain.controltower.controller;

import com.supplychain.controltower.entity.RiskAlert;
import com.supplychain.controltower.repository.RiskAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/risks")
@RequiredArgsConstructor
public class RiskController {

    private final RiskAlertRepository riskAlertRepository;

    @GetMapping
    public ResponseEntity<List<RiskAlert>> getAllRisks() {
        return ResponseEntity.ok(riskAlertRepository.findByOrderByCreatedAtDesc());
    }

    @GetMapping("/active")
    public ResponseEntity<List<RiskAlert>> getActiveRisks() {
        return ResponseEntity.ok(riskAlertRepository.findByStatus(RiskAlert.RiskStatus.ACTIVE));
    }
}
