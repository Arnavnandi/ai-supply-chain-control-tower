package com.supplychain.controltower.service;

import com.supplychain.controltower.ai.agents.SupervisorAgent;
import com.supplychain.controltower.dto.TelemetryEvent;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisruptionSimulationService {

    private final SupervisorAgent supervisorAgent;
    private final TelemetryEventPublisher telemetryEventPublisher;

    public enum DisruptionType {
        SUPPLIER_DISRUPTION,
        INVENTORY_SHORTAGE,
        LOGISTICS_DELAY,
        WAREHOUSE_CAPACITY_OVERRUN
    }

    @Data
    @Builder
    public static class DisruptionSimulationResult {
        private String simulationId;
        private DisruptionType disruptionType;
        private String scenarioDescription;
        private SupervisorAgent.SupervisorConsensusResponse consensusSynthesis;
        private boolean telemetryPublished;
        private String status;
    }

    public DisruptionSimulationResult simulateDisruption(DisruptionType type, String targetEntity) {
        String simulationId = "SIM-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[DISRUPTION SIMULATION] Initiating simulationId: {} | Type: {} | Target: {}",
                simulationId, type, targetEntity);

        String prompt = buildPromptForDisruption(type, targetEntity);

        telemetryEventPublisher.publish(TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.SYSTEM_ERROR)
                .severity(TelemetryEvent.Severity.WARNING)
                .sourceDomain(type.name())
                .entityId(targetEntity != null ? targetEntity : simulationId)
                .message("[SIMULATED DISRUPTION] Triggered scenario: " + type + " for target: " + targetEntity)
                .metadata(Map.of("simulationId", simulationId, "disruptionType", type.name()))
                .build());

        SupervisorAgent.SupervisorConsensusResponse consensus = supervisorAgent.processMultiAgentQuery(prompt);

        return DisruptionSimulationResult.builder()
                .simulationId(simulationId)
                .disruptionType(type)
                .scenarioDescription(prompt)
                .consensusSynthesis(consensus)
                .telemetryPublished(true)
                .status("COMPLETED")
                .build();
    }

    private String buildPromptForDisruption(DisruptionType type, String targetEntity) {
        return switch (type) {
            case SUPPLIER_DISRUPTION -> "Analyze supplier reliability risks and OTIF delivery performance for vendor " +
                    (targetEntity != null ? targetEntity : "SUP-ELEC-001");
            case INVENTORY_SHORTAGE -> "Evaluate stockout risk, safety stock depletion, and PO replenishment for SKU " +
                    (targetEntity != null ? targetEntity : "SKU-ELEC-001");
            case LOGISTICS_DELAY -> "Track delayed transit shipments and carrier delivery bottlenecks on route " +
                    (targetEntity != null ? targetEntity : "Stuttgart to Oakland");
            case WAREHOUSE_CAPACITY_OVERRUN -> "Inspect storage capacity utilization and hub overruns across regional warehouses " +
                    (targetEntity != null ? targetEntity : "WH-WEST");
        };
    }
}
