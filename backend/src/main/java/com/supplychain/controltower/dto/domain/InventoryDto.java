package com.supplychain.controltower.dto.domain;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDto {
    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private BigDecimal productPrice;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Integer quantityAvailable;
    private Integer reservedQuantity;
    private Integer reorderLevel;
    private Integer safetyStock;
    private LocalDateTime lastRestockedAt;
    private String status; // LOW_STOCK, OVERSTOCK, OPTIMAL, CRITICAL
}
