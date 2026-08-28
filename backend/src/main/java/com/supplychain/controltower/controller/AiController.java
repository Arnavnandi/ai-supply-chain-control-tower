package com.supplychain.controltower.controller;

import com.supplychain.controltower.ai.agents.AgentRouter;
import com.supplychain.controltower.ai.agents.SupervisorAgent;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.RecommendationRepository;
import com.supplychain.controltower.service.RagEvaluationService;
import com.supplychain.controltower.service.RagKnowledgeIngestionService;
import com.supplychain.controltower.service.RagRetrievalService;
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

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> processAiQuery(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String prompt = request.get("prompt");
        String agentType = request.getOrDefault("agentType", "EXECUTIVE");

        String username = userDetails != null ? userDetails.getUsername() : "anonymous";
        log.info("[AI QUERY INITIATED] User: '{}' | AgentType: '{}' | Prompt: '{}'", username, agentType, prompt);

        AgentRouter.AgentResponse response = agentRouter.routeQuery(prompt, agentType);

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
        return ResponseEntity.ok(supervisorAgent.processMultiAgentQuery(prompt));
    }

    @PostMapping("/rag/query")
    public ResponseEntity<RagRetrievalService.RagQueryResult> processRagQuery(
            @RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", request.get("query"));
        log.info("[AI RAG CONTROLLER] Received RAG knowledge query: '{}'", question);
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
