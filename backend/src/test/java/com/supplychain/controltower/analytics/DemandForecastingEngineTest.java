package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemandForecastingEngineTest {

    private DemandForecastingEngine forecastingEngine;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        forecastingEngine = new DemandForecastingEngine();
        sampleProduct = Product.builder()
                .id(1L)
                .sku("SKU-TEST-001")
                .name("Test Control Valve")
                .leadTimeDays(7)
                .build();
    }

    @Test
    void testInsufficientDataReturnsInsufficientDataStatus() {
        DemandForecastingEngine.ForecastResult resultNull = forecastingEngine.calculateDemandForecast(sampleProduct, 10, null);
        assertEquals("INSUFFICIENT_DATA", resultNull.getStatus());
        assertTrue(resultNull.getHistoricalData().isEmpty());
        assertTrue(resultNull.getForecastData().isEmpty());

        DemandForecastingEngine.ForecastResult resultTwoPoints = forecastingEngine.calculateDemandForecast(sampleProduct, 10, List.of(100, 150));
        assertEquals("INSUFFICIENT_DATA", resultTwoPoints.getStatus());
        assertEquals(0, resultTwoPoints.getProjected30DayDemand());
    }

    @Test
    void testSufficientDataCalculatesDeterministicForecast() {
        List<Integer> history = List.of(100, 120, 140, 150, 160);
        DemandForecastingEngine.ForecastResult result = forecastingEngine.calculateDemandForecast(sampleProduct, 20, history);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("SKU-TEST-001", result.getProductSku());
        assertEquals(5, result.getHistoricalData().size());
        assertEquals(3, result.getForecastData().size());
        assertTrue(result.getProjected30DayDemand() > 0);
        assertTrue(result.getProjected7DayDemand() > 0);

        // Verify 95% confidence corridor bounds
        DemandForecastingEngine.MonthlyPoint firstForecast = result.getForecastData().get(0);
        assertNotNull(firstForecast.getConfidenceLower());
        assertNotNull(firstForecast.getConfidenceUpper());
        assertTrue(firstForecast.getConfidenceLower() <= firstForecast.getQuantity());
        assertTrue(firstForecast.getConfidenceUpper() >= firstForecast.getQuantity());
    }
}
