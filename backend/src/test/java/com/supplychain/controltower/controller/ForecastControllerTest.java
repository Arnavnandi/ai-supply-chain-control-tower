package com.supplychain.controltower.controller;

import com.supplychain.controltower.analytics.DemandForecastingEngine;
import com.supplychain.controltower.entity.CustomerOrder;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.OrderItem;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.OrderItemRepository;
import com.supplychain.controltower.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForecastControllerTest {

    private DemandForecastingEngine forecastingEngine;
    private ProductRepository productRepository;
    private InventoryRepository inventoryRepository;
    private OrderItemRepository orderItemRepository;
    private ForecastController forecastController;

    @BeforeEach
    void setUp() {
        forecastingEngine = new DemandForecastingEngine();
        productRepository = mock(ProductRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);

        forecastController = new ForecastController(
                forecastingEngine,
                productRepository,
                inventoryRepository,
                orderItemRepository
        );
    }

    @Test
    void testGetForecastForProductWithSufficientOrderItems() {
        Product p = Product.builder().id(100L).sku("SKU-FCAST-100").name("Hydraulic Pump").leadTimeDays(7).build();
        Inventory inv = Inventory.builder().product(p).quantityAvailable(50).build();

        CustomerOrder order1 = CustomerOrder.builder().orderDate(LocalDate.now().minusDays(90)).build();
        CustomerOrder order2 = CustomerOrder.builder().orderDate(LocalDate.now().minusDays(60)).build();
        CustomerOrder order3 = CustomerOrder.builder().orderDate(LocalDate.now().minusDays(30)).build();

        OrderItem item1 = OrderItem.builder().product(p).order(order1).quantity(120).build();
        OrderItem item2 = OrderItem.builder().product(p).order(order2).quantity(140).build();
        OrderItem item3 = OrderItem.builder().product(p).order(order3).quantity(160).build();

        when(productRepository.findById(100L)).thenReturn(Optional.of(p));
        when(inventoryRepository.findByProductId(100L)).thenReturn(List.of(inv));
        when(orderItemRepository.findByProductIdWithOrderDate(100L)).thenReturn(List.of(item1, item2, item3));

        ResponseEntity<DemandForecastingEngine.ForecastResult> response = forecastController.getForecastForProduct(100L);

        assertNotNull(response.getBody());
        assertEquals("SKU-FCAST-100", response.getBody().getProductSku());
        assertEquals("SUCCESS", response.getBody().getStatus());
        assertNotNull(response.getBody().getProjected30DayDemand());
        verify(orderItemRepository, times(1)).findByProductIdWithOrderDate(100L);
    }

    @Test
    void testGetForecastForProductWithoutOrderItemsReturnsInsufficientData() {
        Product p = Product.builder().id(200L).sku("SKU-NEW-200").name("New Component").leadTimeDays(5).build();
        Inventory inv = Inventory.builder().product(p).quantityAvailable(30).build();

        when(productRepository.findById(200L)).thenReturn(Optional.of(p));
        when(inventoryRepository.findByProductId(200L)).thenReturn(List.of(inv));
        when(orderItemRepository.findByProductIdWithOrderDate(200L)).thenReturn(List.of());

        ResponseEntity<DemandForecastingEngine.ForecastResult> response = forecastController.getForecastForProduct(200L);

        assertNotNull(response.getBody());
        assertEquals("SKU-NEW-200", response.getBody().getProductSku());
        assertEquals("INSUFFICIENT_DATA", response.getBody().getStatus());
        assertEquals(0, response.getBody().getProjected30DayDemand());
        verify(orderItemRepository, times(1)).findByProductIdWithOrderDate(200L);
    }
}
