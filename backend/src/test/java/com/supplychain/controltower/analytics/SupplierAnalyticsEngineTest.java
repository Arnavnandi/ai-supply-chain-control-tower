package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.repository.ShipmentRepository;
import com.supplychain.controltower.repository.SupplierProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierAnalyticsEngineTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierProductRepository supplierProductRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private SupplierAnalyticsEngine supplierAnalyticsEngine;

    private Supplier testSupplier;
    private Shipment testShipment;

    @BeforeEach
    void setUp() {
        testSupplier = Supplier.builder()
                .id(1L)
                .code("SUP-TEST")
                .name("Test Supplier Ltd.")
                .country("Taiwan")
                .reliabilityScore(new BigDecimal("95.0"))
                .deliveryPerformancePct(new BigDecimal("92.0"))
                .averageLeadTimeDays(8.0)
                .leadTimeVarianceDays(1.0)
                .build();

        testShipment = Shipment.builder()
                .id(100L)
                .supplier(testSupplier)
                .delayDays(0)
                .status(Shipment.ShipmentStatus.DELIVERED)
                .build();
    }

    @Test
    void analyzeSupplierPerformance_ShouldCalculateOtifAndRiskClassification() {
        when(supplierRepository.findAll()).thenReturn(List.of(testSupplier));
        when(shipmentRepository.findAll()).thenReturn(List.of(testShipment));
        when(supplierProductRepository.findAll()).thenReturn(List.of());

        SupplierAnalyticsEngine.SupplierAnalyticsSummary summary = supplierAnalyticsEngine.analyzeSupplierPerformance();

        assertNotNull(summary);
        assertEquals(1, summary.getTotalSuppliers());
        assertEquals(1, summary.getLowRiskSuppliersCount());
        assertEquals(new BigDecimal("100.0"), summary.getAverageSystemOtifPct());

        SupplierAnalyticsEngine.SupplierPerformanceMetric metric = summary.getSupplierMetrics().get(0);
        assertEquals("SUP-TEST", metric.getSupplierCode());
        assertEquals("PREFERRED_LOW_RISK", metric.getRiskClassification());
        assertEquals(new BigDecimal("100.0"), metric.getOtifScorePct());
    }
}
