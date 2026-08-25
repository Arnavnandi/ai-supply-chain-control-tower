package com.supplychain.controltower.dto.domain;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private Long id;
    private String orderNumber;
    private String customerName;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private String status;
    private BigDecimal totalAmount;
    private List<OrderItemDto> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemDto {
        private Long id;
        private Long productId;
        private String productSku;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
