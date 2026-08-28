package com.supplychain.controltower.controller;

import com.supplychain.controltower.ai.agents.AgentRouter;
import com.supplychain.controltower.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiControllerTest {

    private AgentRouter agentRouter;
    private com.supplychain.controltower.ai.agents.SupervisorAgent supervisorAgent;
    private RecommendationRepository recommendationRepository;
    private com.supplychain.controltower.service.RagRetrievalService ragRetrievalService;
    private com.supplychain.controltower.service.RagKnowledgeIngestionService ragKnowledgeIngestionService;
    private com.supplychain.controltower.service.RagEvaluationService ragEvaluationService;
    private com.supplychain.controltower.service.TelemetryEventPublisher telemetryEventPublisher;
    private AiController aiController;

    @BeforeEach
    void setUp() {
        agentRouter = mock(AgentRouter.class);
        supervisorAgent = mock(com.supplychain.controltower.ai.agents.SupervisorAgent.class);
        recommendationRepository = mock(RecommendationRepository.class);
        ragRetrievalService = mock(com.supplychain.controltower.service.RagRetrievalService.class);
        ragKnowledgeIngestionService = mock(com.supplychain.controltower.service.RagKnowledgeIngestionService.class);
        ragEvaluationService = mock(com.supplychain.controltower.service.RagEvaluationService.class);
        telemetryEventPublisher = mock(com.supplychain.controltower.service.TelemetryEventPublisher.class);

        aiController = new AiController(
                agentRouter,
                supervisorAgent,
                recommendationRepository,
                ragRetrievalService,
                ragKnowledgeIngestionService,
                ragEvaluationService,
                telemetryEventPublisher
        );
    }

    @Test
    void testProcessAiQuerySuccess() {
        AgentRouter.AgentResponse mockResponse = new AgentRouter.AgentResponse(
                "INVENTORY_AGENT",
                "AI Analysis: No critical stockouts found.",
                "2026-08-25T22:44:00"
        );
        when(agentRouter.routeQuery("What is stock status?", "INVENTORY")).thenReturn(mockResponse);

        Map<String, String> request = Map.of("prompt", "What is stock status?", "agentType", "INVENTORY");
        ResponseEntity<Map<String, Object>> response = aiController.processAiQuery(request, null);

        assertNotNull(response.getBody());
        assertEquals("INVENTORY_AGENT", response.getBody().get("agentUsed"));
        assertEquals("AI Analysis: No critical stockouts found.", response.getBody().get("response"));
    }
}
