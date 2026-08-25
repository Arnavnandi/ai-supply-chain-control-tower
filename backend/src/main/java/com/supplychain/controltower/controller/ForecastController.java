package com.supplychain.controltower.controller;

import com.supplychain.controltower.analytics.DemandForecastingEngine;
import com.supplychain.controltower.entity.Inventory;
import com.supplychain.controltower.entity.OrderItem;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.InventoryRepository;
import com.supplychain.controltower.repository.OrderItemRepository;
import com.supplychain.controltower.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@Slf4j
public class ForecastController {

    private final DemandForecastingEngine forecastingEngine;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderItemRepository orderItemRepository;

    @GetMapping("/{productId}")
    public ResponseEntity<DemandForecastingEngine.ForecastResult> getForecastForProduct(@PathVariable Long productId) {
        log.info("[DEMAND FORECAST REQUEST] Calculating forecast for productId={}", productId);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        List<Inventory> inventoryList = inventoryRepository.findByProductId(productId);
        int totalAvailableQty = inventoryList.stream().mapToInt(Inventory::getQuantityAvailable).sum();

        // Calculate real historical sales from database OrderItem and CustomerOrder records
        List<Integer> salesHistory = calculateMonthlySalesFromDatabase(productId);
        log.info("[DEMAND FORECAST DATA] ProductId={} | Real Sales History Aggregated: {}", productId, salesHistory);

        DemandForecastingEngine.ForecastResult result = forecastingEngine.calculateDemandForecast(product, totalAvailableQty, salesHistory);
        return ResponseEntity.ok(result);
    }

    private List<Integer> calculateMonthlySalesFromDatabase(Long productId) {
        List<OrderItem> orderItems = orderItemRepository.findByProductIdWithOrderDate(productId);
        if (orderItems == null || orderItems.isEmpty()) {
            return List.of();
        }

        Map<YearMonth, Integer> monthlyTotals = new HashMap<>();
        for (OrderItem item : orderItems) {
            if (item.getOrder() != null && item.getOrder().getOrderDate() != null) {
                YearMonth ym = YearMonth.from(item.getOrder().getOrderDate());
                monthlyTotals.merge(ym, item.getQuantity() != null ? item.getQuantity() : 0, Integer::sum);
            }
        }

        YearMonth currentMonth = YearMonth.now();
        List<Integer> salesHistory = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = currentMonth.minusMonths(i);
            salesHistory.add(monthlyTotals.getOrDefault(ym, 0));
        }

        boolean hasNonZero = salesHistory.stream().anyMatch(q -> q > 0);
        if (!hasNonZero) {
            List<Integer> nonZeroValues = monthlyTotals.values().stream().filter(q -> q > 0).toList();
            return nonZeroValues.isEmpty() ? List.of() : nonZeroValues;
        }

        return salesHistory;
    }
}

