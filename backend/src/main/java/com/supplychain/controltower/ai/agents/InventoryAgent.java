package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.InventoryTools;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryAgent {

    private final InventoryTools inventoryTools;
    private final ChatClient chatClient;

    @Setter
    @Getter
    @Value("${app.ai.inventory-timeout-seconds:15}")
    private long timeoutSeconds = 15;

    @Setter
    @Getter
    @Value("${app.ai.inventory-max-attempts:3}")
    private int maxAttempts = 3;

    @Setter
    @Getter
    @Value("${app.ai.inventory-retry-backoff-ms:1000}")
    private long retryBackoffMs = 1000L;

    public static final String INVENTORY_AGENT_SYSTEM_PROMPT = """
            You are the Specialized Inventory Control Agent for an enterprise Supply Chain Intelligence Platform.

            RESPONSIBILITIES:
            1. Analyze SKU stock levels, safety stock thresholds, stockout risks, and overstocking across warehouses.
            2. Answer inventory-related calculations (e.g., reorder quantities, inventory turnover, stock-to-sales ratios, arithmetic on stock numbers), unit conversions, and general inventory-management principles.
            3. For live warehouse stock levels, stockout risks, or overstocked items, ALWAYS ground your analysis in PostgreSQL database data using the provided tools: getLowStockProducts and getOverstockedProducts.
            4. For general inventory questions or numerical calculations that do not require database state, answer directly, accurately, and concisely.
            5. If database tools return no items or data is empty, state clearly that no inventory records exist or no stockout issues are present.
            6. Never invent SKUs, quantities, or warehouse locations.
            """;

    @Setter
    private String apiKeyOverride = null;

    @CircuitBreaker(name = "llmService", fallbackMethod = "generateFallbackAnalysis")
    public String processQuery(String prompt) {
        log.info("[INVENTORY AGENT] Processing query: '{}'", prompt);
        String apiKey = apiKeyOverride != null ? apiKeyOverride : System.getenv("GEMINI_API_KEY");
        boolean validKey = apiKey != null && !apiKey.isBlank() && !"unconfigured".equalsIgnoreCase(apiKey) && !apiKey.contains("your-api-key");
        if (!validKey) {
            return generateFallbackAnalysis(prompt);
        }
        Long callerCompanyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        try {
            return CompletableFuture.supplyAsync(() -> {
                com.supplychain.controltower.security.TenantContext.setCompanyId(callerCompanyId);
                try {
                    return executePromptWithRetry(prompt);
                } finally {
                    com.supplychain.controltower.security.TenantContext.clear();
                }
            })
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (Exception ex) {
            log.warn("[INVENTORY AGENT FALLBACK] Executing data-grounded fallback: {}", ex.getMessage());
            com.supplychain.controltower.security.TenantContext.setCompanyId(callerCompanyId);
            return generateFallbackAnalysis(prompt);
        }
    }

    String executePromptWithRetry(String prompt) {
        long backoff = retryBackoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return chatClient.prompt()
                        .system(INVENTORY_AGENT_SYSTEM_PROMPT)
                        .user(prompt)
                        .functions("getLowStockProducts", "getOverstockedProducts")
                        .call()
                        .content();
            } catch (Exception ex) {
                boolean isTransient = isTransientLlmError(ex);
                if (isTransient && attempt < maxAttempts) {
                    log.warn("[INVENTORY AGENT RETRY] Transient error from LLM on attempt {}/{}: {}. Retrying in {}ms...",
                            attempt, maxAttempts, ex.getMessage(), backoff);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM retry interrupted", ie);
                    }
                    backoff *= 2;
                } else {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("LLM call failed after " + maxAttempts + " attempts");
    }

    boolean isTransientLlmError(Throwable ex) {
        if (ex == null) {
            return false;
        }
        if (ex instanceof org.springframework.web.client.HttpStatusCodeException sce) {
            int code = sce.getStatusCode().value();
            if (code == 503 || code == 502 || code == 504 || code == 429) {
                return true;
            }
        }
        if (ex instanceof org.springframework.web.client.ResourceAccessException) {
            return true;
        }
        if (ex instanceof java.net.SocketTimeoutException || ex instanceof java.net.ConnectException) {
            return true;
        }
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("503") || msg.contains("high demand") || msg.contains("unavailable")
                || msg.contains("temporarily") || msg.contains("rate limit") || msg.contains("too many requests")
                || msg.contains("timeout")) {
            return true;
        }
        return isTransientLlmError(ex.getCause());
    }

    public String generateFallbackAnalysis(String prompt, Throwable ex) {
        return generateFallbackAnalysis(prompt);
    }

    public boolean isInventoryAnalysisQuery(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase().trim();

        // Check if query is primarily arithmetic or general calculation
        boolean hasArithmeticIntent = lower.contains("calculate")
                || lower.contains("divide")
                || lower.contains("divided by")
                || lower.contains("multiply")
                || lower.contains("multiplied by")
                || lower.contains("subtract")
                || (lower.contains("what is") && lower.matches(".*\\d+\\s*[+\\-*/x]\\s*\\d+.*"))
                || lower.matches(".*\\b\\d+\\s*(/|\\+|-|\\*|x)\\s*\\d+\\b.*");

        if (hasArithmeticIntent) {
            return false;
        }

        // Domain-specific inventory analysis keywords
        return lower.contains("stock")
                || lower.contains("inventory")
                || lower.contains("sku")
                || lower.contains("reorder")
                || lower.contains("overstock")
                || lower.contains("shortage")
                || lower.contains("safety stock")
                || lower.contains("warehouse");
    }

    public String generateFallbackAnalysis(String prompt) {
        if (!isInventoryAnalysisQuery(prompt)) {
            return buildAiUnavailableMessage(prompt);
        }

        List<InventoryTools.InventoryItemRecord> lowStock = inventoryTools.getLowStockProducts();
        List<InventoryTools.InventoryItemRecord> overstock = inventoryTools.getOverstockedProducts();

        StringBuilder sb = new StringBuilder();
        sb.append("### 📦 Inventory Control Agent Analysis\n");
        if (lowStock.isEmpty() && overstock.isEmpty()) {
            sb.append("No stockout risks or extreme overstock conditions detected in PostgreSQL inventory records. All monitored SKUs are operating within normal buffer tolerances, or operational inventory data has not yet been imported.\n\n");
            sb.append("**Recommendation**: Ensure inventory CSV records are up-to-date to maintain real-time safety stock buffers.");
            return sb.toString();
        }

        sb.append("Analyzed live inventory records from PostgreSQL database:\n\n");
        sb.append("- **Stockout Risk Items**: `").append(lowStock.size()).append("` SKU(s) below reorder/safety thresholds.\n");
        for (var item : lowStock) {
            sb.append("  - **").append(item.name()).append("** (SKU: ").append(item.sku()).append(") at ").append(item.warehouse())
                    .append(" | Available: `").append(item.availableQty()).append("` (Reorder: `").append(item.reorderLevel()).append("`)\n");
        }
        sb.append("\n- **Overstocked Items**: `").append(overstock.size()).append("` SKU(s) exceeding 3x reorder capacity.\n");
        for (var item : overstock) {
            sb.append("  - **").append(item.name()).append("** (SKU: ").append(item.sku()).append(") at ").append(item.warehouse())
                    .append(" | Available: `").append(item.availableQty()).append("`\n");
        }
        if (!lowStock.isEmpty()) {
            sb.append("\n**Recommendation**: Create expedited purchase orders for low-stock SKUs before safety buffer depletion.");
        }
        return sb.toString();
    }

    private String buildAiUnavailableMessage(String prompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 📦 Inventory Control Agent (AI Reasoning Offline)\n");
        sb.append("The generative AI reasoning engine is currently unavailable or timed out while processing your request.\n\n");
        if (prompt != null && !prompt.isBlank()) {
            sb.append("**Query received**: \"").append(prompt.trim()).append("\"\n\n");
        }
        sb.append("This inquiry requires active AI computational capabilities. When offline or disconnected from the LLM, the Inventory Agent cannot perform custom calculations or answer general queries.\n\n");
        sb.append("**Available Offline Capabilities**:\n");
        sb.append("The agent can still evaluate live PostgreSQL inventory telemetry. For database-grounded inventory telemetry, you can request:\n");
        sb.append("- *\"Which products are at risk of stockout?\"*\n");
        sb.append("- *\"Show overstocked inventory across warehouses.\"*\n");
        sb.append("- *\"Check current SKU reorder levels.\"*\n");
        return sb.toString();
    }
}
