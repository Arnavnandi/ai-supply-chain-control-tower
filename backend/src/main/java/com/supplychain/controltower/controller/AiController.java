package com.supplychain.controltower.controller;

import com.supplychain.controltower.ai.agents.AgentRouter;
import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.repository.RecommendationRepository;
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
    private final RecommendationRepository recommendationRepository;

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

    @GetMapping("/recommendations")
    public ResponseEntity<List<Recommendation>> getRecommendations() {
        return ResponseEntity.ok(recommendationRepository.findByOrderByCreatedAtDesc());
    }


}
