package com.supplychain.controltower.controller;

import com.supplychain.controltower.ai.agents.AgentRouter;
import com.supplychain.controltower.ai.agents.SupervisorAgent;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.RecommendationRepository;
import com.supplychain.controltower.dto.TelemetryEvent;
import com.supplychain.controltower.service.RagEvaluationService;
import com.supplychain.controltower.service.RagKnowledgeIngestionService;
import com.supplychain.controltower.service.RagRetrievalService;
import com.supplychain.controltower.service.TelemetryEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AgentRouter agentRouter;
    private final SupervisorAgent supervisorAgent;
    private final RecommendationRepository recommendationRepository;
    private final RagRetrievalService ragRetrievalService;
    private final RagKnowledgeIngestionService ragKnowledgeIngestionService;
    private final RagEvaluationService ragEvaluationService;
    private final TelemetryEventPublisher telemetryEventPublisher;

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> processAiQuery(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String prompt = request.get("prompt");
        String agentType = request.getOrDefault("agentType", "EXECUTIVE");

        String username = userDetails != null ? userDetails.getUsername() : "anonymous";
        log.info("[AI QUERY INITIATED] User: '{}' | AgentType: '{}' | Prompt: '{}'", username, agentType, prompt);

        telemetryEventPublisher.publish(TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.REQUEST_STARTED)
                .severity(TelemetryEvent.Severity.INFO)
                .sourceDomain("AI_CONTROLLER")
                .message("Processing AI query for agent: " + agentType)
                .metadata(Map.of("prompt", prompt != null ? prompt : "", "user", username))
                .build());

        AgentRouter.AgentResponse response = agentRouter.routeQuery(prompt, agentType);

        telemetryEventPublisher.publish(TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.REQUEST_COMPLETED)
                .severity(TelemetryEvent.Severity.INFO)
                .sourceDomain("AI_CONTROLLER")
                .message("AI query completed using agent: " + response.agentUsed())
                .metadata(Map.of("agentUsed", response.agentUsed()))
                .build());

        return ResponseEntity.ok(Map.of(
                "response", response.response(),
                "agentUsed", response.agentUsed(),
                "timestamp", response.timestamp()
        ));
    }

    @PostMapping("/supervisor/query")
    public ResponseEntity<SupervisorAgent.SupervisorConsensusResponse> processSupervisorQuery(
            @RequestBody Map<String, String> request) {
        String prompt = request.getOrDefault("prompt", request.get("query"));
        log.info("[SUPERVISOR CONTROLLER] Processing multi-agent consensus query: '{}'", prompt);

        telemetryEventPublisher.publish(TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.AGENT_EXECUTION)
                .severity(TelemetryEvent.Severity.INFO)
                .sourceDomain("SUPERVISOR_AGENT")
                .message("Executing multi-agent consensus query: " + prompt)
                .build());

        SupervisorAgent.SupervisorConsensusResponse consensus = supervisorAgent.processMultiAgentQuery(prompt);

        telemetryEventPublisher.publish(TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.REQUEST_COMPLETED)
                .severity(TelemetryEvent.Severity.INFO)
                .sourceDomain("SUPERVISOR_AGENT")
                .message("Consensus decision reached: " + consensus.getConsensusDecision())
                .metadata(Map.of("overallRisk", consensus.getOverallSystemRiskScore()))
                .build());

        return ResponseEntity.ok(consensus);
    }

    @PostMapping("/rag/query")
    public ResponseEntity<RagRetrievalService.RagQueryResult> processRagQuery(
            @RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", request.get("query"));
        log.info("[AI RAG CONTROLLER] Received RAG knowledge query: '{}'", question);

        telemetryEventPublisher.publish(TelemetryEvent.builder()
                .eventType(TelemetryEvent.EventType.RAG_RETRIEVAL)
                .severity(TelemetryEvent.Severity.INFO)
                .sourceDomain("RAG_SERVICE")
                .message("Executing RAG retrieval for question: " + question)
                .build());

        return ResponseEntity.ok(ragRetrievalService.queryKnowledgeBase(question));
    }

    @GetMapping("/rag/evaluate")
    public ResponseEntity<RagEvaluationService.RagEvaluationReport> evaluateRagRetrieval(
            @RequestParam(defaultValue = "4") int topK) {
        log.info("[AI RAG CONTROLLER] Executing RAG retrieval benchmark evaluation (topK={})", topK);
        return ResponseEntity.ok(ragEvaluationService.evaluateRagRetrieval(topK));
    }

    @GetMapping("/rag/sources")
    public ResponseEntity<Map<String, Object>> getRagKnowledgeSources() {
        List<RagKnowledgeIngestionService.KnowledgeChunk> chunks = ragKnowledgeIngestionService.getInMemoryChunks();
        return ResponseEntity.ok(Map.of(
                "totalChunksIndexed", chunks.size(),
                "knowledgeChunks", chunks
        ));
    }

    @PostMapping("/rag/reindex")
    public ResponseEntity<Map<String, Object>> reindexRagKnowledge() {
        int indexedCount = ragKnowledgeIngestionService.ingestProjectKnowledge();
        return ResponseEntity.ok(Map.of(
                "message", "Knowledge base successfully re-indexed!",
                "totalChunksIndexed", indexedCount
        ));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<Recommendation>> getRecommendations() {
        return ResponseEntity.ok(recommendationRepository.findByOrderByCreatedAtDesc());
    }
}
