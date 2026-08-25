package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.SupplierDto;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public List<SupplierDto> getAllSuppliers() {
        return supplierRepository.findAll().stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public SupplierDto getSupplierById(Long id) {
        Supplier s = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        return mapToDto(s);
    }

    @Transactional
    public SupplierDto createSupplier(SupplierDto dto) {
        Supplier supplier = Supplier.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .contactPerson(dto.getContactPerson())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .country(dto.getCountry())
                .reliabilityScore(dto.getReliabilityScore())
                .deliveryPerformancePct(dto.getDeliveryPerformancePct())
                .averageLeadTimeDays(dto.getAverageLeadTimeDays())
                .leadTimeVarianceDays(dto.getLeadTimeVarianceDays())
                .build();

        return mapToDto(supplierRepository.save(supplier));
    }

    private SupplierDto mapToDto(Supplier s) {
        return SupplierDto.builder()
                .id(s.getId())
                .code(s.getCode())
                .name(s.getName())
                .contactPerson(s.getContactPerson())
                .email(s.getEmail())
                .phone(s.getPhone())
                .country(s.getCountry())
                .reliabilityScore(s.getReliabilityScore())
                .deliveryPerformancePct(s.getDeliveryPerformancePct())
                .averageLeadTimeDays(s.getAverageLeadTimeDays())
                .leadTimeVarianceDays(s.getLeadTimeVarianceDays())
                .build();
    }
}
