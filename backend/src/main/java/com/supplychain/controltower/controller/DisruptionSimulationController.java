package com.supplychain.controltower.controller;

import com.supplychain.controltower.service.DisruptionSimulationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/simulation")
@RequiredArgsConstructor
@Slf4j
public class DisruptionSimulationController {

    private final DisruptionSimulationService simulationService;

    @Data
    public static class SimulationRequest {
        private String type;
        private String targetEntity;
    }

    @PostMapping("/disruption")
    public ResponseEntity<DisruptionSimulationService.DisruptionSimulationResult> runSimulation(
            @RequestBody(required = false) SimulationRequest request) {

        String typeStr = request != null && request.getType() != null ? request.getType() : "INVENTORY_SHORTAGE";
        String targetEntity = request != null ? request.getTargetEntity() : null;

        DisruptionSimulationService.DisruptionType disruptionType;
        try {
            disruptionType = DisruptionSimulationService.DisruptionType.valueOf(typeStr.toUpperCase());
        } catch (Exception ex) {
            disruptionType = DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE;
        }

        log.info("[SIMULATION CONTROLLER] Running disruption simulation type: {}", disruptionType);
        DisruptionSimulationService.DisruptionSimulationResult result =
                simulationService.simulateDisruption(disruptionType, targetEntity);

        return ResponseEntity.ok(result);
    }
}
