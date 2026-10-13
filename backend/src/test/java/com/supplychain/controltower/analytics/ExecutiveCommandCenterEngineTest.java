package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.ShipmentRepository;
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
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ExecutiveCommandCenterEngineTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private HistoricalMitigationEfficacyEngine efficacyEngine;

    @InjectMocks
    private ExecutiveCommandCenterEngine commandCenterEngine;

    @Test
    void testGenerateExecutiveCommandCenterReportWithHealthyData() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        Supplier supplier = new Supplier();
        supplier.setReliabilityScore(new BigDecimal("90.0"));
        when(supplierRepository.findByCompanyId(1L)).thenReturn(List.of(supplier));
        when(shipmentRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());

        Inventory inventory = new Inventory();
        inventory.setQuantityAvailable(100);
        inventory.setSafetyStock(50);
        when(inventoryRepository.findByCompanyId(1L)).thenReturn(List.of(inventory));

        Warehouse warehouse = new Warehouse();
        warehouse.setUtilizationPercentage(new BigDecimal("60.0"));
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(List.of(warehouse));

        HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport effReport =
                HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport.builder()
                        .overallSuccessRatePct(95.0)
                        .totalHistoricalExecutions(10)
                        .build();
        when(efficacyEngine.calculateHistoricalEfficacy()).thenReturn(effReport);

        ExecutiveCommandCenterEngine.ExecutiveScorecardReport report =
                commandCenterEngine.generateExecutiveCommandCenterReport();

        assertNotNull(report);
        assertNotNull(report.getOverallResiliencyIndex());
        assertTrue(report.getOverallResiliencyIndex() >= 85.0);
        assertEquals("OPTIMAL", report.getResiliencyStatusBand());
        assertTrue(report.getHasSufficientData());
        assertNotNull(report.getExecutiveBriefingSummary());
    }

    @Test
    void testGenerateExecutiveCommandCenterReportWithEmptyDataHandling() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        when(supplierRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(shipmentRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(inventoryRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(warehouseRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(efficacyEngine.calculateHistoricalEfficacy()).thenReturn(null);

        ExecutiveCommandCenterEngine.ExecutiveScorecardReport report =
                commandCenterEngine.generateExecutiveCommandCenterReport();

        assertNotNull(report);
        assertNull(report.getOverallResiliencyIndex());
        assertEquals("INSUFFICIENT_DATA", report.getResiliencyStatusBand());
        assertFalse(report.getHasSufficientData());
        assertTrue(report.getExecutiveBriefingSummary().contains("Insufficient operational telemetry"));
    }

    @Test
    void testGenerateExecutiveCommandCenterReportWithPartiallyPopulatedData() {
        com.supplychain.controltower.security.TenantContext.setCompanyId(1L);
        Supplier supplier = new Supplier();
        supplier.setReliabilityScore(new BigDecimal("90.0"));
        when(supplierRepository.findByCompanyId(1L)).thenReturn(List.of(supplier));
        when(shipmentRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());

        Inventory inventory = new Inventory();
        inventory.setQuantityAvailable(100);
        inventory.setSafetyStock(50);
        when(inventoryRepository.findByCompanyId(1L)).thenReturn(List.of(inventory));

        when(warehouseRepository.findByCompanyId(1L)).thenReturn(Collections.emptyList());
        when(efficacyEngine.calculateHistoricalEfficacy()).thenReturn(null);

        ExecutiveCommandCenterEngine.ExecutiveScorecardReport report =
                commandCenterEngine.generateExecutiveCommandCenterReport();

        assertNotNull(report);
        assertNotNull(report.getOverallResiliencyIndex());
        assertTrue(report.getOverallResiliencyIndex() >= 85.0);
        assertNotEquals("INSUFFICIENT_DATA", report.getResiliencyStatusBand());
        assertTrue(report.getHasSufficientData());
        assertNull(report.getWarehouseCapacityComponentScore());
        assertNull(report.getHistoricalEfficacyComponentScore());
    }
}
