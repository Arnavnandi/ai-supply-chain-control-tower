package com.supplychain.controltower.controller;

import com.supplychain.controltower.service.DisruptionMitigationPolicyEngine;
import com.supplychain.controltower.service.DisruptionSimulationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/simulation/disruption")
@RequiredArgsConstructor
@Slf4j
public class DisruptionMitigationController {

    private final DisruptionMitigationPolicyEngine mitigationPolicyEngine;

    @Data
    public static class MitigationRequest {
        private String type;
        private String targetEntity;
        private Boolean convertToActionProposal;
        private Boolean convertToProposal;
    }

    @PostMapping("/mitigation")
    public ResponseEntity<DisruptionMitigationPolicyEngine.MitigationPolicyResult> evaluateMitigation(
            @RequestBody(required = false) MitigationRequest request,
            @RequestParam(name = "convertToActionProposal", required = false, defaultValue = "false") boolean queryConvertParam,
            @RequestParam(name = "convertToProposal", required = false, defaultValue = "false") boolean queryConvertAltParam) {

        String typeStr = request != null && request.getType() != null ? request.getType() : "INVENTORY_SHORTAGE";
        String targetEntity = request != null ? request.getTargetEntity() : null;

        boolean convertToProposal = queryConvertParam || queryConvertAltParam
                || (request != null && Boolean.TRUE.equals(request.getConvertToActionProposal()))
                || (request != null && Boolean.TRUE.equals(request.getConvertToProposal()));

        DisruptionSimulationService.DisruptionType disruptionType;
        try {
            disruptionType = DisruptionSimulationService.DisruptionType.valueOf(typeStr.toUpperCase());
        } catch (Exception ex) {
            disruptionType = DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
        }

        log.info("[MITIGATION CONTROLLER] Evaluating policy for type: {} | Target: {} | ConvertToProposal: {}",
                disruptionType, targetEntity, convertToProposal);
        DisruptionMitigationPolicyEngine.MitigationPolicyResult result =
                mitigationPolicyEngine.evaluateAndMitigate(disruptionType, targetEntity, convertToProposal);

        return ResponseEntity.ok(result);
    }
}
