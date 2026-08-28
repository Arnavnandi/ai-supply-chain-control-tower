package com.supplychain.controltower.service;

import com.supplychain.controltower.analytics.DemandForecastingEngine;
import com.supplychain.controltower.analytics.RiskAnalysisEngine;
import com.supplychain.controltower.dto.dashboard.DashboardSummaryDto;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyChainIntelligenceServiceTest {

    @Mock
    private RiskAnalysisEngine riskAnalysisEngine;

    @Mock
    private DemandForecastingEngine demandForecastingEngine;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private ForecastService forecastService;

    @InjectMocks
    private SupplyChainIntelligenceService intelligenceService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder().id(1L).sku("SKU-001").name("Microcontroller").build();
    }

    @Test
    void testGetControlTowerIntelligenceAggregatesReport() {
        DashboardSummaryDto kpis = DashboardSummaryDto.builder()
                .totalProducts(10L)
                .totalInventoryUnits(5000L)
                .lowStockProductsCount(2L)
                .delayedShipmentsCount(1L)
                .build();

        RiskAnalysisEngine.ControlTowerRiskReport riskReport = RiskAnalysisEngine.ControlTowerRiskReport.builder()
                .overallRiskScore(45.0)
                .riskLevel("MODERATE")
                .criticalRisksCount(1)
                .highRisksCount(1)
                .mediumRisksCount(0)
                .lowRisksCount(0)
                .riskItems(List.of(
                        RiskAnalysisEngine.ExplainableRiskItem.builder()
                                .id("INV-1")
                                .category("INVENTORY")
                                .title("Stockout Risk")
                                .severity("CRITICAL")
                                .problemDetected("Stockout Risk for product")
                                .dataCause("Available stock < safety stock")
                                .actionRecommended("Order 100 units")
                                .status("ACTIVE")
                                .build()
                ))
                .build();

        DemandForecastingEngine.ForecastResult forecast = DemandForecastingEngine.ForecastResult.builder()
                .productId(1L)
                .productSku("SKU-001")
                .productName("Microcontroller")
                .status("SUCCESS")
                .projected30DayDemand(120)
                .build();

        when(dashboardService.getDashboardSummary()).thenReturn(kpis);
        when(riskAnalysisEngine.evaluateSystemRisks()).thenReturn(riskReport);
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));
        when(forecastService.getDemandForecast(anyLong())).thenReturn(forecast);

        SupplyChainIntelligenceService.IntelligenceSummaryDto result = intelligenceService.getControlTowerIntelligence();

        assertNotNull(result);
        assertEquals(kpis, result.getSummaryKpis());
        assertEquals(riskReport, result.getRiskReport());
        assertEquals(1, result.getTopForecasts().size());
        assertEquals(1, result.getPrioritizedRecommendations().size());
        assertTrue(result.getExecutiveAiBriefing().contains("MODERATE RISK"));
    }
}
