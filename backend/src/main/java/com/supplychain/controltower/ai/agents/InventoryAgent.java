package com.supplychain.controltower.ai.agents;

import com.supplychain.controltower.ai.tools.InventoryTools;
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
public class InventoryAgent {

    private final InventoryTools inventoryTools;
    private final ChatClient chatClient;

    public String processQuery(String prompt) {
        log.info("[INVENTORY AGENT] Processing query: '{}'", prompt);
        String apiKey = System.getenv("GEMINI_API_KEY");
        boolean validKey = apiKey != null && !apiKey.isBlank() && !"unconfigured".equalsIgnoreCase(apiKey) && !apiKey.contains("your-api-key");
        if (!validKey) {
            return generateFallbackAnalysis(prompt);
        }
        try {
            return CompletableFuture.supplyAsync(() -> chatClient.prompt()
                    .system("""
                            You are the Specialized Inventory Control Agent.
                            Your sole responsibility is analyzing SKU stock levels, safety stock thresholds, stockout risks, and overstocking across warehouses.
                            Always ground your analysis in PostgreSQL database data via tools getLowStockProducts and getOverstockedProducts.
                            """)
                    .user(prompt)
                    .functions("getLowStockProducts", "getOverstockedProducts")
                    .call()
                    .content())
                    .orTimeout(2, TimeUnit.SECONDS)
                    .join();
        } catch (Exception ex) {
            log.warn("[INVENTORY AGENT FALLBACK] Executing data-grounded fallback: {}", ex.getMessage());
            return generateFallbackAnalysis(prompt);
        }
    }

    public String generateFallbackAnalysis(String prompt) {
        List<InventoryTools.InventoryItemRecord> lowStock = inventoryTools.getLowStockProducts();
        List<InventoryTools.InventoryItemRecord> overstock = inventoryTools.getOverstockedProducts();

        StringBuilder sb = new StringBuilder();
        sb.append("### 📦 Inventory Control Agent Analysis\n");
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
        sb.append("\n**Recommendation**: Create expedited purchase orders for low-stock SKUs before safety buffer depletion.");
        return sb.toString();
    }
}
