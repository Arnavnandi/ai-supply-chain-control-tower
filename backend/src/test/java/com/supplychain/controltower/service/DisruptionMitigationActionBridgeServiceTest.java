package com.supplychain.controltower.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.entity.AuditLog;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.AuditLogRepository;
import com.supplychain.controltower.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisruptionMitigationActionBridgeServiceTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DisruptionMitigationActionBridgeService bridgeService;

    @Mock
    private DisruptionSimulationService disruptionSimulationService;

    @Mock
    private TelemetryEventPublisher telemetryEventPublisher;

    private DisruptionMitigationPolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        policyEngine = new DisruptionMitigationPolicyEngine(
                disruptionSimulationService,
                telemetryEventPublisher,
                bridgeService
        );
    }

    @Test
    void testMapDisruptionTypeToRecommendationTypeAllCategories() {
        assertEquals(Recommendation.RecommendationType.CHANGE_SUPPLIER,
                bridgeService.mapDisruptionTypeToRecommendationType(DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION));
        assertEquals(Recommendation.RecommendationType.REORDER_STOCK,
                bridgeService.mapDisruptionTypeToRecommendationType(DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE));
        assertEquals(Recommendation.RecommendationType.EXPEDITE_SHIPMENT,
                bridgeService.mapDisruptionTypeToRecommendationType(DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY));
        assertEquals(Recommendation.RecommendationType.REALLOCATE_INVENTORY,
                bridgeService.mapDisruptionTypeToRecommendationType(DisruptionSimulationService.DisruptionType.WAREHOUSE_CAPACITY_OVERRUN));
        assertEquals(Recommendation.RecommendationType.REORDER_STOCK,
                bridgeService.mapDisruptionTypeToRecommendationType(null));
    }

    @Test
    void testConvertPolicyToProposalSuccess() {
        Recommendation mockSaved = Recommendation.builder()
                .id(101L)
                .title("[POLICY PROPOSAL] ACTIVATE_SECONDARY_SUPPLIER_FAILOVER for SUP-TEST-001")
                .type(Recommendation.RecommendationType.CHANGE_SUPPLIER)
                .status(Recommendation.ApprovalStatus.PENDING_APPROVAL)
                .build();

        when(recommendationRepository.save(any(Recommendation.class))).thenReturn(mockSaved);

        Recommendation result = bridgeService.convertPolicyToProposal(
                "SIM-TEST-100",
                DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION,
                "SUP-TEST-001",
                65.0,
                DisruptionMitigationPolicyEngine.RiskBand.HIGH,
                "ACTIVATE_SECONDARY_SUPPLIER_FAILOVER",
                List.of("[POLICY] Activate approved backup vendor", "[POLICY] Expedite PO")
        );

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals(Recommendation.ApprovalStatus.PENDING_APPROVAL, result.getStatus());

        ArgumentCaptor<Recommendation> recCaptor = ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationRepository).save(recCaptor.capture());
        Recommendation capturedRec = recCaptor.getValue();

        assertTrue(capturedRec.getTitle().contains("ACTIVATE_SECONDARY_SUPPLIER_FAILOVER"));
        assertEquals(Recommendation.RecommendationType.CHANGE_SUPPLIER, capturedRec.getType());
        assertEquals(Recommendation.ApprovalStatus.PENDING_APPROVAL, capturedRec.getStatus());
        assertTrue(capturedRec.getActionPayloadJson().contains("RECOMMENDATION_ONLY"));
        assertTrue(capturedRec.getActionPayloadJson().contains("SUP-TEST-001"));

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testEvaluateAndMitigateWithProposalConversion() {
        com.supplychain.controltower.ai.agents.SupervisorAgent.SupervisorConsensusResponse mockConsensus =
                com.supplychain.controltower.ai.agents.SupervisorAgent.SupervisorConsensusResponse.builder()
                        .overallSystemRiskScore(80.0)
                        .build();

        DisruptionSimulationService.DisruptionSimulationResult mockSim =
                DisruptionSimulationService.DisruptionSimulationResult.builder()
                        .simulationId("SIM-PROPOSAL-001")
                        .disruptionType(DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE)
                        .scenarioDescription("Test scenario")
                        .consensusSynthesis(mockConsensus)
                        .build();

        when(disruptionSimulationService.simulateDisruption(any(), any())).thenReturn(mockSim);

        Recommendation mockSavedRec = Recommendation.builder()
                .id(202L)
                .status(Recommendation.ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(recommendationRepository.save(any(Recommendation.class))).thenReturn(mockSavedRec);

        DisruptionMitigationPolicyEngine.MitigationPolicyResult result =
                policyEngine.evaluateAndMitigate(
                        DisruptionSimulationService.DisruptionType.INVENTORY_SHORTAGE,
                        "SKU-PROPOSAL-999",
                        true
                );

        assertNotNull(result);
        assertTrue(result.isProposalCreated());
        assertEquals(202L, result.getRecommendationId());
        assertEquals(DisruptionMitigationPolicyEngine.RiskBand.CRITICAL, result.getRiskBand());
        assertEquals("EXPEDITE_REPLENISHMENT_AND_REBALANCE", result.getPolicyDecision());

        verify(telemetryEventPublisher, times(1)).publish(any(TelemetryEvent.class));
        verify(recommendationRepository, times(1)).save(any(Recommendation.class));
    }

    @Test
    void testRecommendationOnlyExecutionModeEnforced() {
        Recommendation mockSaved = Recommendation.builder().id(303L).build();
        when(recommendationRepository.save(any(Recommendation.class))).thenReturn(mockSaved);

        Recommendation result = bridgeService.convertPolicyToProposal(
                "SIM-TEST-300",
                DisruptionSimulationService.DisruptionType.LOGISTICS_DELAY,
                "Route A to B",
                40.0,
                DisruptionMitigationPolicyEngine.RiskBand.MEDIUM,
                "CARRIER_REROUTE_AND_AIR_CARGO_ESCALATION",
                List.of("[POLICY] Reroute shipment")
        );

        assertNotNull(result);

        ArgumentCaptor<Recommendation> recCaptor = ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationRepository).save(recCaptor.capture());
        Recommendation rec = recCaptor.getValue();

        // Enforce PENDING_APPROVAL status so execution cannot happen without human approval
        assertEquals(Recommendation.ApprovalStatus.PENDING_APPROVAL, rec.getStatus());
        assertNull(rec.getExecutedAt());
        assertNull(rec.getExecutedBy());
        assertTrue(rec.getActionPayloadJson().contains("\"executionMode\":\"RECOMMENDATION_ONLY\""));
    }

    @Test
    void testNullOrBlankInputsHandling() {
        Recommendation mockSaved = Recommendation.builder().id(404L).build();
        when(recommendationRepository.save(any(Recommendation.class))).thenReturn(mockSaved);

        Recommendation result = bridgeService.convertPolicyToProposal(
                null,
                DisruptionSimulationService.DisruptionType.WAREHOUSE_CAPACITY_OVERRUN,
                "",
                15.0,
                DisruptionMitigationPolicyEngine.RiskBand.LOW,
                "INTER_HUB_STOCK_TRANSFER_AND_RECEIPT_DEFERRAL",
                null
        );

        assertNotNull(result);
        ArgumentCaptor<Recommendation> captor = ArgumentCaptor.forClass(Recommendation.class);
        verify(recommendationRepository).save(captor.capture());
        assertTrue(captor.getValue().getTitle().contains("DEFAULT-TARGET"));
    }

    @Test
    void testCorrelationIdPropagation() {
        String testCorrId = "corr-bridge-test-12345";
        MDC.put("X-Correlation-ID", testCorrId);

        try {
            Recommendation mockSaved = Recommendation.builder().id(505L).build();
            when(recommendationRepository.save(any(Recommendation.class))).thenReturn(mockSaved);

            Recommendation result = bridgeService.convertPolicyToProposal(
                    "SIM-CORR-001",
                    DisruptionSimulationService.DisruptionType.SUPPLIER_DISRUPTION,
                    "SUP-CORR-001",
                    55.0,
                    DisruptionMitigationPolicyEngine.RiskBand.HIGH,
                    "ACTIVATE_SECONDARY_SUPPLIER_FAILOVER",
                    List.of("Action 1")
            );

            assertNotNull(result);
            assertEquals(testCorrId, MDC.get("X-Correlation-ID"));
        } finally {
            MDC.remove("X-Correlation-ID");
        }
    }
}
