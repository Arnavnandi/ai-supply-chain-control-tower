package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.SupplierDto;
import com.supplychain.controltower.entity.Company;
import com.supplychain.controltower.entity.Supplier;
import com.supplychain.controltower.exception.ResourceNotFoundException;
import com.supplychain.controltower.exception.TenantContextMissingException;
import com.supplychain.controltower.repository.CompanyRepository;
import com.supplychain.controltower.repository.SupplierRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<SupplierDto> getAllSuppliers() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return supplierRepository.findByCompanyId(companyId).stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public SupplierDto getSupplierById(Long id) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Supplier s = supplierRepository.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + id));
        return mapToDto(s);
    }

    @Transactional
    public SupplierDto createSupplier(SupplierDto dto) {
        Long companyId = TenantContext.getRequiredCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantContextMissingException("Company not found: " + companyId));

        Supplier supplier = Supplier.builder()
                .company(company)
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

