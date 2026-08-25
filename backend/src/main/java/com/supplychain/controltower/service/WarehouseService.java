package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.WarehouseDto;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    @Transactional(readOnly = true)
    public List<WarehouseDto> getAllWarehouses() {
        return warehouseRepository.findAll().stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto getWarehouseById(Long id) {
        Warehouse w = warehouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Warehouse not found with id: " + id));
        return mapToDto(w);
    }

    @Transactional
    public WarehouseDto createWarehouse(WarehouseDto dto) {
        BigDecimal utilizationPct = BigDecimal.ZERO;
        if (dto.getTotalCapacityUnits() != null && dto.getTotalCapacityUnits() > 0) {
            utilizationPct = BigDecimal.valueOf((double) dto.getCurrentUtilizationUnits() / dto.getTotalCapacityUnits() * 100)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Warehouse warehouse = Warehouse.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .location(dto.getLocation())
                .totalCapacityUnits(dto.getTotalCapacityUnits())
                .currentUtilizationUnits(dto.getCurrentUtilizationUnits() != null ? dto.getCurrentUtilizationUnits() : 0)
                .utilizationPercentage(utilizationPct)
                .managerName(dto.getManagerName())
                .contactEmail(dto.getContactEmail())
                .build();

        return mapToDto(warehouseRepository.save(warehouse));
    }

    private WarehouseDto mapToDto(Warehouse w) {
        return WarehouseDto.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .location(w.getLocation())
                .totalCapacityUnits(w.getTotalCapacityUnits())
                .currentUtilizationUnits(w.getCurrentUtilizationUnits())
                .utilizationPercentage(w.getUtilizationPercentage())
                .managerName(w.getManagerName())
                .contactEmail(w.getContactEmail())
                .build();
    }
}
