package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.WarehouseDto;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Warehouse;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.WarehouseRepository;
import com.supplychain.controltower.security.TenantContext;
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
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<WarehouseDto> getAllWarehouses() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return warehouseRepository.findByCompanyId(companyId).stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto getWarehouseById(Long id) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Warehouse w = warehouseRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));
        return mapToDto(w);
    }

    @Transactional
    public WarehouseDto createWarehouse(WarehouseDto dto) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found: " + companyId));

        BigDecimal utilizationPct = BigDecimal.ZERO;
        if (dto.getTotalCapacityUnits() != null && dto.getTotalCapacityUnits() > 0) {
            utilizationPct = BigDecimal.valueOf((double) dto.getCurrentUtilizationUnits() / dto.getTotalCapacityUnits() * 100)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Warehouse warehouse = Warehouse.builder()
                .company(company)
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

