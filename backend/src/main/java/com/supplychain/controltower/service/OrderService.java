package com.supplychain.controltower.service;

import com.supplychain.controltower.dto.domain.OrderDto;
import com.supplychain.controltower.entity.CustomerOrder;
import com.supplychain.controltower.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final CustomerOrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<OrderDto> getAllOrders() {
        return orderRepository.findByOrderByOrderDateDesc().stream().map(this::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderById(Long id) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        return mapToDto(order);
    }

    private OrderDto mapToDto(CustomerOrder o) {
        List<OrderDto.OrderItemDto> items = o.getItems().stream().map(item ->
                OrderDto.OrderItemDto.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productSku(item.getProduct().getSku())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build()
        ).toList();

        return OrderDto.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .customerName(o.getCustomerName())
                .orderDate(o.getOrderDate())
                .expectedDeliveryDate(o.getExpectedDeliveryDate())
                .status(o.getStatus().name())
                .totalAmount(o.getTotalAmount())
                .items(items)
                .build();
    }
}
