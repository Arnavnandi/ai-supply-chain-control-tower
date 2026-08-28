package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.repository.ShipmentRepository;
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
class LogisticsAnalyticsEngineTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private LogisticsAnalyticsEngine logisticsAnalyticsEngine;

    private Shipment onTimeShipment;
    private Shipment delayedShipment;

    @BeforeEach
    void setUp() {
        onTimeShipment = Shipment.builder()
                .id(1L)
                .trackingCode("TRK-001")
                .carrierName("Global Express")
                .origin("Tokyo")
                .destination("Chicago")
                .delayDays(0)
                .status(Shipment.ShipmentStatus.DELIVERED)
                .build();

        delayedShipment = Shipment.builder()
                .id(2L)
                .trackingCode("TRK-002")
                .carrierName("Global Express")
                .origin("Tokyo")
                .destination("Chicago")
                .delayDays(4)
                .status(Shipment.ShipmentStatus.DELAYED)
                .build();
    }

    @Test
    void analyzeLogisticsPerformance_ShouldComputeCarrierAndRouteMetrics() {
        when(shipmentRepository.findAll()).thenReturn(List.of(onTimeShipment, delayedShipment));

        LogisticsAnalyticsEngine.LogisticsAnalyticsSummary summary = logisticsAnalyticsEngine.analyzeLogisticsPerformance();

        assertNotNull(summary);
        assertEquals(2, summary.getTotalShipments());
        assertEquals(1, summary.getActiveDelayedShipments());
        assertEquals(new BigDecimal("4.0"), summary.getAverageDelayDaysSystem());

        assertFalse(summary.getCarrierMetrics().isEmpty());
        LogisticsAnalyticsEngine.CarrierPerformanceMetric carrier = summary.getCarrierMetrics().get(0);
        assertEquals("Global Express", carrier.getCarrierName());
        assertEquals(2, carrier.getTotalShipments());
        assertEquals(1, carrier.getDelayedShipments());
        assertEquals(new BigDecimal("50.0"), carrier.getOnTimePerformancePct());
    }
}
