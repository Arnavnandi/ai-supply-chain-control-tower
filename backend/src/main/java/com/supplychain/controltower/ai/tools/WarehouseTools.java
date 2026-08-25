package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseTools {

    private final WarehouseRepository warehouseRepository;

    @Description("Retrieves warehouse capacity utilization metrics for all distribution centers.")
    public List<WarehouseUtilizationRecord> getWarehouseUtilization() {
        log.info("[SPRING AI TOOL EXECUTING] getWarehouseUtilization() querying PostgreSQL database...");
        List<WarehouseUtilizationRecord> results = warehouseRepository.findAll().stream().map(w ->
                new WarehouseUtilizationRecord(
                        w.getId(),
                        w.getCode(),
                        w.getName(),
                        w.getLocation(),
                        w.getTotalCapacityUnits(),
                        w.getCurrentUtilizationUnits(),
                        w.getUtilizationPercentage()
                )
        ).toList();
        log.info("[SPRING AI TOOL COMPLETE] getWarehouseUtilization() returned {} warehouses.", results.size());
        return results;
    }

    public record WarehouseUtilizationRecord(
            Long id,
            String code,
            String name,
            String location,
            Integer totalCapacity,
            Integer usedUnits,
            BigDecimal utilizationPct
    ) {}
}

