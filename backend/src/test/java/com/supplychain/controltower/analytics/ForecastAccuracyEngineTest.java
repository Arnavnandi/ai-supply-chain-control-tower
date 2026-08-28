package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.service.ForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastAccuracyEngineTest {

    @Mock
    private ForecastService forecastService;

    @Mock
    private DemandForecastingEngine forecastingEngine;

    @InjectMocks
    private ForecastAccuracyEngine accuracyEngine;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(1L).sku("SKU-ACC-001").name("Temperature Sensor").build();
    }

    @Test
    void testCalculateForecastAccuracyReturnsMapeAndRmse() {
        when(forecastService.calculateMonthlySalesFromDatabase(1L)).thenReturn(List.of(120, 150, 180, 200, 220, 250));

        ForecastAccuracyEngine.AccuracyMetrics metrics = accuracyEngine.calculateForecastAccuracy(product);

        assertNotNull(metrics);
        assertEquals("SKU-ACC-001", metrics.getProductSku());
        assertNotNull(metrics.getMapePercentage());
        assertNotNull(metrics.getRmseValue());
        assertNotNull(metrics.getAccuracyRating());
        assertEquals(4, metrics.getComparisons().size());
    }
}
