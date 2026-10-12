package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.analytics.DemandForecastingEngine;
import com.supplychain.controltower.entity.Product;
import com.supplychain.controltower.repository.ProductRepository;
import com.supplychain.controltower.service.ForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForecastTools {

    private final ProductRepository productRepository;
    private final ForecastService forecastService;

    @Description("Retrieves 3-month demand forecasts, projected 7-day/30-day burn rates, confidence corridors, and stockout warnings for product SKUs based on 12-month historical sales data.")
    public List<ForecastItemRecord> getDemandForecasts() {
        Long companyId = com.supplychain.controltower.security.TenantContext.getRequiredCompanyId();
        log.info("[SPRING AI TOOL EXECUTING] getDemandForecasts() querying database demand analytics for companyId: {}...", companyId);
        List<Product> products = productRepository.findByCompanyId(companyId);
        List<ForecastItemRecord> results = new ArrayList<>();

        for (Product p : products) {
            try {
                DemandForecastingEngine.ForecastResult f = forecastService.getDemandForecast(p.getId());
                if (f != null) {
                    results.add(new ForecastItemRecord(
                            p.getId(),
                            p.getSku(),
                            p.getName(),
                            f.getProjected7DayDemand(),
                            f.getProjected30DayDemand(),
                            f.getDaysUntilStockout(),
                            f.getStockoutWarning(),
                            "INSUFFICIENT_DATA".equals(f.getStatus()) ? "INSUFFICIENT_DATA" : f.getMethod()
                    ));
                }
            } catch (Exception ex) {
                log.warn("[SPRING AI TOOL] Error computing forecast for product ID {}: {}", p.getId(), ex.getMessage());
            }
        }

        log.info("[SPRING AI TOOL COMPLETE] getDemandForecasts() returned {} items.", results.size());
        return results;
    }

    public record ForecastItemRecord(
            Long productId,
            String sku,
            String name,
            Integer projected7DayDemand,
            Integer projected30DayDemand,
            Integer daysUntilStockout,
            Boolean stockoutWarning,
            String method
    ) {}
}
