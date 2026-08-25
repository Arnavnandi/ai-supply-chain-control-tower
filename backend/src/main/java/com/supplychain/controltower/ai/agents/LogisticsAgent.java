package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.LogisticsTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogisticsAgent {

    private final LogisticsTools logisticsTools;
    private final ChatClient chatClient;

    public String processQuery(String prompt) {
        log.info("[LOGISTICS AGENT] Processing query: '{}'", prompt);
        try {
            return chatClient.prompt()
                    .system("""
                            You are the Specialized Logistics & Transit Tracking Agent.
                            Your responsibility is monitoring in-transit shipments, carrier delays, shipment routes, and delivery estimates.
                            Always ground your analysis in PostgreSQL database data via getDelayedShipments.
                            """)
                    .user(prompt)
                    .functions("getDelayedShipments")
                    .call()
                    .content();
        } catch (Exception ex) {
            log.warn("[LOGISTICS AGENT FALLBACK] Executing data-grounded fallback: {}", ex.getMessage());
            return generateFallbackAnalysis(prompt);
        }
    }

    public String generateFallbackAnalysis(String prompt) {
        List<LogisticsTools.DelayedShipmentRecord> delays = logisticsTools.getDelayedShipments();

        StringBuilder sb = new StringBuilder();
        sb.append("### 🚚 Logistics & Shipment Tracking Agent Analysis\n");
        sb.append("Identified **").append(delays.size()).append(" active delayed shipment(s)** in transit:\n\n");
        for (var d : delays) {
            sb.append("- Tracking Code: `").append(d.trackingCode()).append("`\n");
            sb.append("  - Carrier: **").append(d.carrier()).append("** | Delay: `").append(d.delayDays()).append(" day(s)`\n");
            sb.append("  - Route: ").append(d.origin()).append(" ➔ ").append(d.destination()).append("\n\n");
        }
        if (delays.isEmpty()) {
            sb.append("All shipments are currently operating on-schedule without transit delays.\n");
        } else {
            sb.append("**Recommendation**: Contact carriers for updated ETA and alert destination distribution centers.");
        }
        return sb.toString();
    }
}
