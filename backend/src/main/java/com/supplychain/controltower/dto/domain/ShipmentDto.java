package com.supplychain.controltower.dto.domain;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentDto {
    private Long id;
    private String trackingCode;
    private Long supplierId;
    private String supplierName;
    private Long destinationWarehouseId;
    private String destinationWarehouseName;
    private Long orderId;
    private String orderNumber;
    private String origin;
    private String destination;
    private LocalDate shippedDate;
    private LocalDate estimatedDeliveryDate;
    private LocalDate actualDeliveryDate;
    private String status;
    private Integer delayDays;
    private String carrierName;
}
