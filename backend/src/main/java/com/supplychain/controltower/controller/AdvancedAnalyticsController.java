package com.supplychain.controltower.controller;

import com.supplychain.controltower.analytics.ForecastAccuracyEngine;
import com.supplychain.controltower.analytics.InventoryOptimizationEngine;
import com.supplychain.controltower.analytics.LogisticsAnalyticsEngine;
import com.supplychain.controltower.analytics.SupplierAnalyticsEngine;
import com.supplychain.controltower.analytics.StressTestingEngine;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.service.ExecutiveReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AdvancedAnalyticsController {

    private final ForecastAccuracyEngine accuracyEngine;
    private final InventoryOptimizationEngine optimizationEngine;
    private final StressTestingEngine stressTestingEngine;
    private final SupplierAnalyticsEngine supplierAnalyticsEngine;
    private final LogisticsAnalyticsEngine logisticsAnalyticsEngine;
    private final ExecutiveReportService executiveReportService;
    private final ProductRepository productRepository;

    @GetMapping("/accuracy/{productId}")
    public ResponseEntity<ForecastAccuracyEngine.AccuracyMetrics> getForecastAccuracy(@PathVariable Long productId) {
        log.info("[ADVANCED ANALYTICS] Fetching forecast accuracy for Product ID={}", productId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        return ResponseEntity.ok(accuracyEngine.calculateForecastAccuracy(product));
    }

    @GetMapping("/optimization")
    public ResponseEntity<InventoryOptimizationEngine.SafetyStockOptimizationReport> getSafetyStockOptimization() {
        log.info("[ADVANCED ANALYTICS] Fetching dynamic safety stock optimization report...");
        return ResponseEntity.ok(optimizationEngine.optimizeSafetyStockLevels());
    }

    @PostMapping("/simulate")
    public ResponseEntity<StressTestingEngine.StressTestSimulationResult> runStressTestSimulation(
            @RequestBody StressTestingEngine.StressTestRequest request) {
        log.info("[ADVANCED ANALYTICS] Executing What-If supply chain stress test simulation...");
        return ResponseEntity.ok(stressTestingEngine.runWhatIfSimulation(request));
    }

    @GetMapping("/suppliers")
    public ResponseEntity<SupplierAnalyticsEngine.SupplierAnalyticsSummary> getSupplierAnalytics() {
        log.info("[ADVANCED ANALYTICS] Fetching OTIF performance and supplier risk matrix...");
        return ResponseEntity.ok(supplierAnalyticsEngine.analyzeSupplierPerformance());
    }

    @GetMapping("/logistics")
    public ResponseEntity<LogisticsAnalyticsEngine.LogisticsAnalyticsSummary> getLogisticsAnalytics() {
        log.info("[ADVANCED ANALYTICS] Fetching carrier performance and route transit analytics...");
        return ResponseEntity.ok(logisticsAnalyticsEngine.analyzeLogisticsPerformance());
    }

    @GetMapping("/executive-report")
    public ResponseEntity<ExecutiveReportService.ExecutiveControlReport> getExecutiveReport() {
        log.info("[ADVANCED ANALYTICS] Generating 1-Click Executive Control Briefing report...");
        return ResponseEntity.ok(executiveReportService.generateExecutiveReport());
    }
}
