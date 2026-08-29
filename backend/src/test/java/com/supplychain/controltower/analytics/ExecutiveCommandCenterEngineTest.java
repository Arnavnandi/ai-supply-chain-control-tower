package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.InventoryRepository;
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
class ExecutiveCommandCenterEngineTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private HistoricalMitigationEfficacyEngine efficacyEngine;

    @InjectMocks
    private ExecutiveCommandCenterEngine commandCenterEngine;

    @Test
    void testGenerateExecutiveCommandCenterReportWithHealthyData() {
        Supplier supplier = new Supplier();
        supplier.setReliabilityScore(new BigDecimal("90.0"));
        when(supplierRepository.findAll()).thenReturn(List.of(supplier));

        Inventory inventory = new Inventory();
        inventory.setQuantityAvailable(100);
        inventory.setSafetyStock(50);
        when(inventoryRepository.findAll()).thenReturn(List.of(inventory));

        Warehouse warehouse = new Warehouse();
        warehouse.setUtilizationPercentage(new BigDecimal("60.0"));
        when(warehouseRepository.findAll()).thenReturn(List.of(warehouse));

        HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport effReport =
                HistoricalMitigationEfficacyEngine.HistoricalEfficacyReport.builder()
                        .overallSuccessRatePct(95.0)
                        .build();
        when(efficacyEngine.calculateHistoricalEfficacy()).thenReturn(effReport);

        ExecutiveCommandCenterEngine.ExecutiveScorecardReport report =
                commandCenterEngine.generateExecutiveCommandCenterReport();

        assertNotNull(report);
        assertTrue(report.getOverallResiliencyIndex() >= 85.0);
        assertEquals("OPTIMAL", report.getResiliencyStatusBand());
        assertNotNull(report.getExecutiveBriefingSummary());
    }

    @Test
    void testGenerateExecutiveCommandCenterReportWithEmptyDataHandling() {
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());
        when(inventoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(warehouseRepository.findAll()).thenReturn(Collections.emptyList());
        when(efficacyEngine.calculateHistoricalEfficacy()).thenReturn(null);

        ExecutiveCommandCenterEngine.ExecutiveScorecardReport report =
                commandCenterEngine.generateExecutiveCommandCenterReport();

        assertNotNull(report);
        assertTrue(report.getOverallResiliencyIndex() > 0.0);
        assertNotNull(report.getResiliencyStatusBand());
    }
}
