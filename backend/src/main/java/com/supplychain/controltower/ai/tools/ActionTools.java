package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.entity.Recommendation;
import com.supplychain.controltower.service.ActionApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ActionTools {

    private final ActionApprovalService actionApprovalService;

    public record ActionRequest(String statusFilter) {}

    @Bean
    @Description("Fetch all pending AI purchase order recommendations and human-in-the-loop approval actions requiring manager sign-off.")
    public Function<ActionRequest, List<Recommendation>> getPendingActionRecommendations() {
        return request -> {
            log.info("[SPRING AI TOOL] Executing getPendingActionRecommendations tool...");
            return actionApprovalService.getPendingRecommendations();
        };
    }
}
