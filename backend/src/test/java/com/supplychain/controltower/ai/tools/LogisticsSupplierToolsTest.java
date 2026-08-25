package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.SupplierProduct;
import com.supplychain.controltower.repository.ShipmentRepository;
import com.supplychain.controltower.repository.SupplierProductRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogisticsSupplierToolsTest {

    private SupplierRepository supplierRepository;
    private SupplierProductRepository supplierProductRepository;
    private ShipmentRepository shipmentRepository;
    private SupplierTools supplierTools;
    private LogisticsTools logisticsTools;

    @BeforeEach
    void setUp() {
        supplierRepository = mock(SupplierRepository.class);
        supplierProductRepository = mock(SupplierProductRepository.class);
        shipmentRepository = mock(ShipmentRepository.class);
        supplierTools = new SupplierTools(supplierRepository, supplierProductRepository);
        logisticsTools = new LogisticsTools(shipmentRepository);
    }

    @Test
    void testGetSupplierPerformance() {
        Supplier s = Supplier.builder()
                .id(1L)
                .code("SUP-01")
                .name("Apex Dynamics")
                .reliabilityScore(new BigDecimal("95.50"))
                .deliveryPerformancePct(new BigDecimal("92.00"))
                .averageLeadTimeDays(7.5)
                .build();

        when(supplierRepository.findAll()).thenReturn(List.of(s));

        List<SupplierTools.SupplierPerformanceRecord> result = supplierTools.getSupplierPerformance();
        assertEquals(1, result.size());
        assertEquals("SUP-01", result.get(0).code());
        assertEquals(new BigDecimal("95.50"), result.get(0).reliabilityScore());
        verify(supplierRepository, times(1)).findAll();
    }

    @Test
    void testGetDelayedShipments() {
        Supplier s = Supplier.builder().name("Global Logistics").build();
        Shipment shipment = Shipment.builder()
                .id(10L)
                .trackingCode("TRK-100")
                .supplier(s)
                .origin("Chicago, IL")
                .destination("Dallas, TX")
                .estimatedDeliveryDate(LocalDate.now().minusDays(1))
                .delayDays(2)
                .carrierName("Express Cargo")
                .status(Shipment.ShipmentStatus.DELAYED)
                .build();

        when(shipmentRepository.findByStatus(Shipment.ShipmentStatus.DELAYED)).thenReturn(List.of(shipment));

        List<LogisticsTools.DelayedShipmentRecord> result = logisticsTools.getDelayedShipments();
        assertEquals(1, result.size());
        assertEquals("TRK-100", result.get(0).trackingCode());
        assertEquals(2, result.get(0).delayDays());
        assertEquals("Express Cargo", result.get(0).carrier());
        verify(shipmentRepository, times(1)).findByStatus(Shipment.ShipmentStatus.DELAYED);
    }
}
