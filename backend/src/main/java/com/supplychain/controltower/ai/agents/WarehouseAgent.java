package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.WarehouseTools;
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
public class WarehouseAgent {

    private final WarehouseTools warehouseTools;
    private final ChatClient chatClient;

    @CircuitBreaker(name = "llmService", fallbackMethod = "generateFallbackAnalysis")
    public String processQuery(String prompt) {
        log.info("[WAREHOUSE AGENT] Processing query: '{}'", prompt);
        String apiKey = System.getenv("GEMINI_API_KEY");
        boolean validKey = apiKey != null && !apiKey.isBlank() && !"unconfigured".equalsIgnoreCase(apiKey) && !apiKey.contains("your-api-key");
        if (!validKey) {
            return generateFallbackAnalysis(prompt);
        }
        Long callerCompanyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        try {
            return CompletableFuture.supplyAsync(() -> {
                com.supplychain.controltower.security.TenantContext.setCompanyId(callerCompanyId);
                try {
                    return chatClient.prompt()
                            .system("""
                                    You are the Specialized Warehouse Facilities Agent.
                                    Your responsibility is evaluating distribution hub storage capacity, space utilization %, and multi-facility balancing.
                                    Always ground your analysis in PostgreSQL database data via tool getWarehouseUtilization.
                                    If no warehouse records exist, state clearly that no facilities are registered.
                                    Never invent warehouses, capacities, or utilization figures.
                                    """)
                            .user(prompt)
                            .functions("getWarehouseUtilization")
                            .call()
                            .content();
                } finally {
                    com.supplychain.controltower.security.TenantContext.clear();
                }
            })
                    .orTimeout(2, TimeUnit.SECONDS)
                    .join();
        } catch (Exception ex) {
            log.warn("[WAREHOUSE AGENT FALLBACK] Executing data-grounded fallback: {}", ex.getMessage());
            com.supplychain.controltower.security.TenantContext.setCompanyId(callerCompanyId);
            try {
                return generateFallbackAnalysis(prompt);
            } finally {
                com.supplychain.controltower.security.TenantContext.clear();
            }
        }
    }

    public String generateFallbackAnalysis(String prompt, Throwable ex) {
        return generateFallbackAnalysis(prompt);
    }

    public String generateFallbackAnalysis(String prompt) {
        List<WarehouseTools.WarehouseUtilizationRecord> whs = warehouseTools.getWarehouseUtilization();

        StringBuilder sb = new StringBuilder();
        sb.append("### 🏬 Warehouse Facilities Agent Analysis\n");
        if (whs.isEmpty()) {
            sb.append("No warehouse facilities found in database for this company. Please upload warehouse CSV data to monitor storage capacity and space utilization.\n\n");
            sb.append("**Recommendation**: Import warehouse facilities dataset to activate storage telemetry.");
            return sb.toString();
        }

        sb.append("Storage capacity and utilization metrics across regional hubs:\n\n");
        for (var w : whs) {
            sb.append("- **").append(w.name()).append("** (Code: `").append(w.code()).append("`, ").append(w.location()).append(")\n");
            sb.append("  - Utilization: `").append(w.usedUnits()).append(" / ").append(w.totalCapacity())
                    .append(" units` (**").append(w.utilizationPct()).append("%**)\n\n");
        }
        sb.append("**Recommendation**: Balance inventory transfers away from hubs exceeding 85% capacity threshold.");
        return sb.toString();
    }
}
