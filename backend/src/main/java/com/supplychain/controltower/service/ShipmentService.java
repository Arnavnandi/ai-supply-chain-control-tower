package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.ShipmentDto;
import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.repository.ShipmentRepository;
import com.supplychain.controltower.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    @Transactional(readOnly = true)
    public List<ShipmentDto> getAllShipments() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return shipmentRepository.findByCompanyId(companyId).stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ShipmentDto> getDelayedShipments() {
        Long companyId = TenantContext.getRequiredCompanyId();
        return shipmentRepository.findByCompanyIdAndStatus(companyId, Shipment.ShipmentStatus.DELAYED).stream().map(this::mapToDto).toList();
    }

    private ShipmentDto mapToDto(Shipment s) {
        boolean isDemo = s.getCompany() != null && "DEMO".equalsIgnoreCase(s.getCompany().getCode());
        return ShipmentDto.builder()
                .id(s.getId())
                .trackingCode(s.getTrackingCode())
                .supplierId(s.getSupplier() != null ? s.getSupplier().getId() : null)
                .supplierName(s.getSupplier() != null ? s.getSupplier().getName() : "N/A")
                .destinationWarehouseId(s.getDestinationWarehouse() != null ? s.getDestinationWarehouse().getId() : null)
                .destinationWarehouseName(s.getDestinationWarehouse() != null ? s.getDestinationWarehouse().getName() : "N/A")
                .orderId(s.getOrder() != null ? s.getOrder().getId() : null)
                .orderNumber(s.getOrder() != null ? s.getOrder().getOrderNumber() : "N/A")
                .origin(s.getOrigin())
                .destination(s.getDestination())
                .shippedDate(s.getShippedDate())
                .estimatedDeliveryDate(s.getEstimatedDeliveryDate())
                .actualDeliveryDate(s.getActualDeliveryDate())
                .status(s.getStatus().name())
                .delayDays(s.getDelayDays())
                .carrierName(s.getCarrierName())
                .isDemo(isDemo)
                .build();
    }
}

