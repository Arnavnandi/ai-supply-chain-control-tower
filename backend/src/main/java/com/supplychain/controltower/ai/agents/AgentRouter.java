package com.supplychain.controltower.ai.agents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentRouter {

    private final InventoryAgent inventoryAgent;
    private final SupplierAgent supplierAgent;
    private final LogisticsAgent logisticsAgent;
    private final WarehouseAgent warehouseAgent;
    private final RiskAgent riskAgent;
    private final SupervisorAgent supervisorAgent;
    private final ChatClient chatClient;

    public record AgentResponse(
            String agentUsed,
            String response,
            String timestamp
    ) {}

    public AgentResponse routeQuery(String prompt, String requestedAgentType) {
        String agentType = determineAgentType(prompt, requestedAgentType);
        log.info("[AGENT ROUTER] Routing query to Agent Architecture: '{}'", agentType);

        String result;
        switch (agentType) {
            case "INVENTORY":
                result = inventoryAgent.processQuery(prompt);
                break;
            case "SUPPLIER":
                result = supplierAgent.processQuery(prompt);
                break;
            case "LOGISTICS":
                result = logisticsAgent.processQuery(prompt);
                break;
            case "WAREHOUSE":
                result = warehouseAgent.processQuery(prompt);
                break;
            case "RISK":
                result = riskAgent.processQuery(prompt);
                break;
            case "SUPERVISOR":
            case "EXECUTIVE":
            default:
                var consensus = supervisorAgent.processMultiAgentQuery(prompt);
                result = consensus.getSupervisorSynthesis();
                break;
        }

        return new AgentResponse(
                agentType + "_AGENT",
                result,
                java.time.LocalDateTime.now().toString()
        );
    }

    private String determineAgentType(String prompt, String requestedAgentType) {
        if (requestedAgentType != null && !requestedAgentType.isBlank() && !"EXECUTIVE".equalsIgnoreCase(requestedAgentType)) {
            return requestedAgentType.toUpperCase();
        }

        String lower = prompt != null ? prompt.toLowerCase() : "";
        if (lower.contains("stock") || lower.contains("inventory") || lower.contains("sku") || lower.contains("reorder")) {
            return "INVENTORY";
        }
        if (lower.contains("supplier") || lower.contains("vendor") || lower.contains("lead time") || lower.contains("price")) {
            return "SUPPLIER";
        }
        if (lower.contains("shipment") || lower.contains("delay") || lower.contains("tracking") || lower.contains("carrier")) {
            return "LOGISTICS";
        }
        if (lower.contains("warehouse") || lower.contains("capacity") || lower.contains("facility") || lower.contains("hub")) {
            return "WAREHOUSE";
        }
        if (lower.contains("risk") || lower.contains("alert") || lower.contains("critical") || lower.contains("disruption")) {
            return "RISK";
        }

        return "EXECUTIVE";
    }

    private String processExecutiveQuery(String prompt) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.warn("[EXECUTIVE AGENT ROUTER FALLBACK] Multi-agent executive synthesis fallback: {}", ex.getMessage());
            StringBuilder sb = new StringBuilder();
            sb.append("### 🛸 Executive Control Tower Intelligence Briefing\n");
            sb.append("Synthesizing telemetry from specialized sub-agents:\n\n");
            sb.append("1. **Inventory Agent**: Evaluates stockout risks and reorder levels.\n");
            sb.append("2. **Supplier Agent**: Monitors vendor reliability ratings and contract pricing.\n");
            sb.append("3. **Logistics Agent**: Tracks active in-transit shipments and carrier delays.\n");
            sb.append("4. **Warehouse Agent**: Monitors distribution hub capacity utilization.\n");
            sb.append("5. **Risk Agent**: Synthesizes critical operational risk alerts.\n\n");
            sb.append("How can I assist you with specific operational analysis?");
            return sb.toString();
        }
    }
}
