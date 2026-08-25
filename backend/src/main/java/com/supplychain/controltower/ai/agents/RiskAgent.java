package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.AnalyticsTools;
import com.supplychain.controltower.ai.tools.InventoryTools;
import com.supplychain.controltower.ai.tools.LogisticsTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RiskAgent {

    private final AnalyticsTools analyticsTools;
    private final InventoryTools inventoryTools;
    private final LogisticsTools logisticsTools;
    private final ChatClient chatClient;

    public String processQuery(String prompt) {
        log.info("[RISK AGENT] Processing query: '{}'", prompt);
        try {
            return chatClient.prompt()
                    .system("""
                            You are the Specialized Operational Risk Analysis Agent.
                            Your responsibility is synthesizing risk alerts across stockouts, shipment delays, supplier disruptions, and capacity overruns.
                            Always ground your analysis in PostgreSQL database data via getSupplyChainRisks, getLowStockProducts, and getDelayedShipments.
                            """)
                    .user(prompt)
                    .functions("getSupplyChainRisks", "getLowStockProducts", "getDelayedShipments")
                    .call()
                    .content();
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
