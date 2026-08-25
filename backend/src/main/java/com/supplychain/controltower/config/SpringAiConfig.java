package com.supplychain.controltower.config;

import com.supplychain.controltower.ai.tools.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;

@Configuration
public class SpringAiConfig {

    public record EmptyRequest() {}
    public record ProductIdRequest(Long productId) {}

    @Bean
    @Description("Retrieves all products that are at risk of stockout or currently below safety stock / reorder levels.")
    public Function<EmptyRequest, List<InventoryTools.InventoryItemRecord>> getLowStockProducts(InventoryTools inventoryTools) {
        return request -> inventoryTools.getLowStockProducts();
    }

    @Bean
    @Description("Retrieves products that are overstocked (inventory exceeds 3x reorder level).")
    public Function<EmptyRequest, List<InventoryTools.InventoryItemRecord>> getOverstockedProducts(InventoryTools inventoryTools) {
        return request -> inventoryTools.getOverstockedProducts();
    }

    @Bean
    @Description("Retrieves supplier performance metrics including reliability scores, delivery rates, and average lead times.")
    public Function<EmptyRequest, List<SupplierTools.SupplierPerformanceRecord>> getSupplierPerformance(SupplierTools supplierTools) {
        return request -> supplierTools.getSupplierPerformance();
    }

    @Bean
    @Description("Compares supplier pricing, lead times, and reliability scores for a specific product ID.")
    public Function<ProductIdRequest, List<SupplierTools.SupplierComparisonRecord>> getSuppliersForProduct(SupplierTools supplierTools) {
        return request -> supplierTools.getSuppliersForProduct(request != null ? request.productId() : null);
    }

    @Bean
    @Description("Retrieves all currently delayed shipments, including delay days and carrier information.")
    public Function<EmptyRequest, List<LogisticsTools.DelayedShipmentRecord>> getDelayedShipments(LogisticsTools logisticsTools) {
        return request -> logisticsTools.getDelayedShipments();
    }

    @Bean
    @Description("Retrieves warehouse capacity utilization metrics for all distribution centers.")
    public Function<EmptyRequest, List<WarehouseTools.WarehouseUtilizationRecord>> getWarehouseUtilization(WarehouseTools warehouseTools) {
        return request -> warehouseTools.getWarehouseUtilization();
    }

    @Bean
    @Description("Retrieves active supply chain risk alerts across inventory, suppliers, warehouses, and logistics.")
    public Function<EmptyRequest, List<AnalyticsTools.RiskAlertRecord>> getSupplyChainRisks(AnalyticsTools analyticsTools) {
        return request -> analyticsTools.getSupplyChainRisks();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        You are the AI Executive Control Tower Assistant for an enterprise Supply Chain Intelligence Platform.
                        
                        ROLE & RESPONSIBILITIES:
                        1. Analyze inventory stockouts, supplier reliability, shipment transit delays, warehouse utilization, and operational risks.
                        2. ALWAYS prefer calling database-backed tools to retrieve live operational telemetry from PostgreSQL over assuming or guessing database state.
                        3. Clearly distinguish verified database facts from analytical recommendations or strategic advice.
                        
                        AVAILABLE DATABASE TOOLS:
                        - getLowStockProducts: Retrieves products below safety stock / reorder thresholds.
                        - getOverstockedProducts: Retrieves products exceeding 3x reorder level.
                        - getSupplierPerformance: Retrieves reliability ratings, delivery performance %, and lead times for suppliers.
                        - getSuppliersForProduct: Compares supplier contract prices and lead times for a given product ID.
                        - getDelayedShipments: Retrieves active delayed shipments in transit with delay days and carrier details.
                        - getWarehouseUtilization: Retrieves storage capacity and utilization percentages for warehouses.
                        - getSupplyChainRisks: Retrieves active operational risk alerts.
                        
                        When responding, provide concise, executive summaries using clear markdown formatting.
                        """)
                .defaultFunctions(
                        "getLowStockProducts",
                        "getOverstockedProducts",
                        "getSupplierPerformance",
                        "getSuppliersForProduct",
                        "getDelayedShipments",
                        "getWarehouseUtilization",
                        "getSupplyChainRisks"
                )
                .build();
    }
}

