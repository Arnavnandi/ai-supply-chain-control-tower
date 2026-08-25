package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.ShipmentDto;
import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.repository.ShipmentRepository;
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
        return shipmentRepository.findAll().stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ShipmentDto> getDelayedShipments() {
        return shipmentRepository.findByStatus(Shipment.ShipmentStatus.DELAYED).stream().map(this::mapToDto).toList();
    }

    private ShipmentDto mapToDto(Shipment s) {
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
                .build();
    }
}
