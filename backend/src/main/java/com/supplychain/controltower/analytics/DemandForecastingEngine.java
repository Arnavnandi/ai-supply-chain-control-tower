package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Product;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class DemandForecastingEngine {

    @Data
    @Builder
    public static class ForecastResult {
        private Long productId;
        private String productSku;
        private String productName;
        private String status; // SUCCESS, INSUFFICIENT_DATA
        private String message;
        private String method; // WEIGHTED_MOVING_AVERAGE, EXPONENTIAL_SMOOTHING
        private List<MonthlyPoint> historicalData;
        private List<MonthlyPoint> forecastData;
        private Integer projected7DayDemand;
        private Integer projected30DayDemand;
        private Integer daysUntilStockout;
        private Boolean stockoutWarning;
    }

    @Data
    @Builder
    public static class MonthlyPoint {
        private String monthLabel;
        private LocalDate date;
        private Integer quantity;
        private Integer confidenceLower;
        private Integer confidenceUpper;
    }

    public ForecastResult calculateDemandForecast(Product product, int currentAvailableQty, List<Integer> historicalMonthlySales) {
        if (historicalMonthlySales == null || historicalMonthlySales.size() < 3) {
            return ForecastResult.builder()
                    .productId(product != null ? product.getId() : null)
                    .productSku(product != null ? product.getSku() : "N/A")
                    .productName(product != null ? product.getName() : "Unknown Product")
                    .status("INSUFFICIENT_DATA")
                    .message("Insufficient historical sales data (minimum 3 monthly sales records required for weighted moving average & exponential smoothing).")
                    .method("NONE")
                    .historicalData(java.util.Collections.emptyList())
                    .forecastData(java.util.Collections.emptyList())
                    .projected7DayDemand(0)
                    .projected30DayDemand(0)
                    .daysUntilStockout(999)
                    .stockoutWarning(false)
                    .build();
        }

        // 1. Calculate 3-Month Weighted Moving Average (weights: 0.5 for most recent, 0.3 for t-1, 0.2 for t-2)
        int n = historicalMonthlySales.size();
        double w1 = 0.5, w2 = 0.3, w3 = 0.2;
        double nextMonthForecast = (historicalMonthlySales.get(n - 1) * w1) +
                                   (historicalMonthlySales.get(n - 2) * w2) +
                                   (historicalMonthlySales.get(n - 3) * w3);

        // Exponential smoothing (alpha = 0.3)
        double alpha = 0.3;
        double expForecast = historicalMonthlySales.get(0);
        for (int i = 1; i < n; i++) {
            expForecast = alpha * historicalMonthlySales.get(i) + (1 - alpha) * expForecast;
        }

        int finalMonthlyForecast = (int) Math.round((nextMonthForecast + expForecast) / 2.0);
        int projected7DayDemand = (int) Math.ceil((finalMonthlyForecast / 30.0) * 7);
        int projected30DayDemand = finalMonthlyForecast;

        double dailyBurnRate = finalMonthlyForecast / 30.0;
        int daysUntilStockout = dailyBurnRate > 0 ? (int) Math.floor(currentAvailableQty / dailyBurnRate) : 999;
        boolean stockoutWarning = daysUntilStockout <= (product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 7);

        // Calculate Standard Deviation of historical sales for confidence corridors
        double sum = 0;
        for (int s : historicalMonthlySales) sum += s;
        double mean = sum / n;
        double sqDiffSum = 0;
        for (int s : historicalMonthlySales) sqDiffSum += Math.pow(s - mean, 2);
        double stdDev = Math.sqrt(sqDiffSum / Math.max(1, n - 1));

        // Historical monthly points
        List<MonthlyPoint> historyPoints = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = 0; i < n; i++) {
            int qty = historicalMonthlySales.get(i);
            historyPoints.add(MonthlyPoint.builder()
                    .monthLabel(now.minusMonths(n - i).getMonth().name().substring(0, 3))
                    .date(now.minusMonths(n - i))
                    .quantity(qty)
                    .confidenceLower(qty)
                    .confidenceUpper(qty)
                    .build());
        }

        // Forecast monthly points for next 3 months with 95% confidence bounds (± 1.96 * stdDev)
        List<MonthlyPoint> forecastPoints = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            int futureQty = (int) Math.round(finalMonthlyForecast * (1 + (i * 0.03))); // 3% trend factor
            int lower = Math.max(0, (int) Math.round(futureQty - (1.96 * stdDev)));
            int upper = (int) Math.round(futureQty + (1.96 * stdDev));

            forecastPoints.add(MonthlyPoint.builder()
                    .monthLabel(now.plusMonths(i).getMonth().name().substring(0, 3))
                    .date(now.plusMonths(i))
                    .quantity(futureQty)
                    .confidenceLower(lower)
                    .confidenceUpper(upper)
                    .build());
        }

        return ForecastResult.builder()
                .productId(product.getId())
                .productSku(product.getSku())
                .productName(product.getName())
                .status("SUCCESS")
                .message("Demand forecast calculated using Hybrid Weighted Moving Average and Exponential Smoothing.")
                .method("HYBRID_WEIGHTED_MOVING_AVERAGE_EXPONENTIAL_SMOOTHING")
                .historicalData(historyPoints)
                .forecastData(forecastPoints)
                .projected7DayDemand(projected7DayDemand)
                .projected30DayDemand(projected30DayDemand)
                .daysUntilStockout(daysUntilStockout)
                .stockoutWarning(stockoutWarning)
                .build();
    }
}
