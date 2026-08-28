package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.SupplierTools;
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
public class SupplierAgent {

    private final SupplierTools supplierTools;
    private final ChatClient chatClient;

    @CircuitBreaker(name = "llmService", fallbackMethod = "generateFallbackAnalysis")
    public String processQuery(String prompt) {
        log.info("[SUPPLIER AGENT] Processing query: '{}'", prompt);
        String apiKey = System.getenv("GEMINI_API_KEY");
        boolean validKey = apiKey != null && !apiKey.isBlank() && !"unconfigured".equalsIgnoreCase(apiKey) && !apiKey.contains("your-api-key");
        if (!validKey) {
            return generateFallbackAnalysis(prompt);
        }
        try {
            return CompletableFuture.supplyAsync(() -> chatClient.prompt()
                    .system("""
                            You are the Specialized Supplier Intelligence Agent.
                            Your responsibility is evaluating vendor reliability ratings, lead times, delivery performance %, and contract pricing.
                            Always ground your analysis in PostgreSQL database data via tools getSupplierPerformance and getSuppliersForProduct.
                            """)
                    .user(prompt)
                    .functions("getSupplierPerformance", "getSuppliersForProduct")
                    .call()
                    .content())
                    .orTimeout(2, TimeUnit.SECONDS)
                    .join();
        } catch (Exception ex) {
            log.warn("[SUPPLIER AGENT FALLBACK] Executing data-grounded fallback: {}", ex.getMessage());
            return generateFallbackAnalysis(prompt);
        }
    }

    public String generateFallbackAnalysis(String prompt) {
        List<SupplierTools.SupplierPerformanceRecord> suppliers = supplierTools.getSupplierPerformance();

        StringBuilder sb = new StringBuilder();
        sb.append("### 🏭 Supplier Intelligence Agent Analysis\n");
        sb.append("Evaluated **").append(suppliers.size()).append(" active vendor partner(s)** in PostgreSQL:\n\n");
        for (var s : suppliers) {
            sb.append("- **").append(s.name()).append("** (Code: `").append(s.code()).append("`)\n");
            sb.append("  - Reliability Index: `").append(s.reliabilityScore()).append("%`\n");
            sb.append("  - On-Time Delivery Rate: `").append(s.deliveryPerformancePct()).append("%`\n");
            sb.append("  - Avg Lead Time: `").append(s.averageLeadTimeDays()).append(" days`\n\n");
        }
        sb.append("**Recommendation**: Prioritize orders with preferred vendors maintaining >90% reliability scores.");
        return sb.toString();
    }
}
