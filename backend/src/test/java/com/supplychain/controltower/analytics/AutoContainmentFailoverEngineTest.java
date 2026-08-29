package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.SupplierRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoContainmentFailoverEngineTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private AutoContainmentFailoverEngine engine;

    @Test
    void testComputeFailoverContainmentPlanNormalFlow() {
        Supplier s1 = new Supplier();
        s1.setCode("SUP-TECH-001");
        s1.setName("Primary Tech Vendor");
        s1.setReliabilityScore(new BigDecimal("75.0"));

        Supplier s2 = new Supplier();
        s2.setCode("SUP-TECH-002");
        s2.setName("Secondary Failover Vendor");
        s2.setReliabilityScore(new BigDecimal("95.0"));

        when(supplierRepository.findAll()).thenReturn(List.of(s1, s2));

        Warehouse w1 = new Warehouse();
        w1.setCode("WH-NORTH");

        Warehouse w2 = new Warehouse();
        w2.setCode("WH-SOUTH");
        w2.setTotalCapacityUnits(100000);
        w2.setUtilizationPercentage(new BigDecimal("50.0"));

        when(warehouseRepository.findAll()).thenReturn(List.of(w1, w2));

        AutoContainmentFailoverEngine.ContainmentFailoverReport report =
                engine.computeFailoverContainmentPlan("SUP-TECH-001", "WH-NORTH");

        assertNotNull(report);
        assertEquals("SUP-TECH-001", report.getFailedSupplierCode());
        assertEquals("CONTAINED", report.getContainmentStatus());
        assertEquals(60.0, report.getPrimaryAllocationPct());
        assertEquals(40.0, report.getFallbackAllocationPct());
        assertEquals(2, report.getAllocations().size());

        AutoContainmentFailoverEngine.FailoverAllocation fallbackAlloc = report.getAllocations().get(1);
        assertEquals("SUP-TECH-002", fallbackAlloc.getSupplierCode());
        assertEquals(40.0, fallbackAlloc.getAllocationPercentage());
        assertEquals(95.0, fallbackAlloc.getReliabilityScore());
        assertEquals("WH-SOUTH", report.getAlternateWarehouseCode());
        assertEquals(50000.0, report.getAlternateWarehouseAvailableCapacityUnits());
    }

    @Test
    void testComputeFailoverContainmentPlanWithEmptyData() {
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());
        when(warehouseRepository.findAll()).thenReturn(Collections.emptyList());

        AutoContainmentFailoverEngine.ContainmentFailoverReport report =
                engine.computeFailoverContainmentPlan("SUP-TECH-001", "WH-NORTH");

        assertNotNull(report);
        assertEquals("CONTAINED", report.getContainmentStatus());
        assertEquals(60.0, report.getPrimaryAllocationPct());
        assertEquals(40.0, report.getFallbackAllocationPct());
        assertNotNull(report.getStrategyExplanation());
    }
}
