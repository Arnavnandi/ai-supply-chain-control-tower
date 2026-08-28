package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.AnalyticsTools;
import com.supplychain.controltower.ai.tools.InventoryTools;
import com.supplychain.controltower.ai.tools.LogisticsTools;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskAgent {

    private final AnalyticsTools analyticsTools;
    private final InventoryTools inventoryTools;
    private final LogisticsTools logisticsTools;
    private final com.supplychain.controltower.ai.tools.RiskTools riskTools;
    private final com.supplychain.controltower.ai.tools.ForecastTools forecastTools;
    private final ChatClient chatClient;

    @CircuitBreaker(name = "llmService", fallbackMethod = "generateFallbackAnalysis")
    public String processQuery(String prompt) {
        log.info("[RISK AGENT] Processing query: '{}'", prompt);
        String apiKey = System.getenv("GEMINI_API_KEY");
        boolean validKey = apiKey != null && !apiKey.isBlank() && !"unconfigured".equalsIgnoreCase(apiKey) && !apiKey.contains("your-api-key");
        if (!validKey) {
            return generateFallbackAnalysis(prompt);
        }
        try {
            return CompletableFuture.supplyAsync(() -> chatClient.prompt()
                    .system("""
                            You are the Specialized Operational Risk Analysis Agent.
                            Your responsibility is synthesizing risk alerts across stockouts, shipment delays, supplier disruptions, and capacity overruns.
                            Always ground your analysis in PostgreSQL database data via getActiveSupplyChainRisks, getDemandForecasts, getLowStockProducts, and getDelayedShipments.
                            Provide clear explainability: problem detected, raw metrics cause, and recommended action.
                            """)
                    .user(prompt)
                    .functions("getActiveSupplyChainRisks", "getDemandForecasts", "getLowStockProducts", "getDelayedShipments")
                    .call()
                    .content())
                    .orTimeout(2, TimeUnit.SECONDS)
                    .join();
        } catch (Exception ex) {
            log.warn("[RISK AGENT FALLBACK] Executing data-grounded fallback: {}", ex.getMessage());
            return generateFallbackAnalysis(prompt);
        }
    }

    public String generateFallbackAnalysis(String prompt) {
        List<AnalyticsTools.RiskAlertRecord> risks = analyticsTools.getSupplyChainRisks();
        List<InventoryTools.InventoryItemRecord> lowStock = inventoryTools.getLowStockProducts();
        List<LogisticsTools.DelayedShipmentRecord> delays = logisticsTools.getDelayedShipments();

        StringBuilder sb = new StringBuilder();
        sb.append("### 🛡️ Operational Risk Analysis Agent Briefing\n");
        sb.append("Active supply chain risk alerts synthesized from PostgreSQL database:\n\n");
        for (var r : risks) {
            sb.append("- **[").append(r.severity()).append("] ").append(r.category()).append("** (Entity: ").append(r.entityType()).append(")\n");
            sb.append("  - ").append(r.description()).append("\n");
            sb.append("  - *Action*: ").append(r.recommendation()).append("\n\n");
        }
        sb.append("**Summary Telemetry**:\n");
        sb.append("- Stockout Risk Items: `").append(lowStock.size()).append("`\n");
        sb.append("- Delayed Transit Shipments: `").append(delays.size()).append("`\n");
        return sb.toString();
    }
}
