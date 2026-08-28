package com.supplychain.controltower.analytics;

import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.repository.ShipmentRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogisticsAnalyticsEngine {

    private final ShipmentRepository shipmentRepository;

    @Data
    @Builder
    public static class CarrierPerformanceMetric {
        private String carrierName;
        private Integer totalShipments;
        private Integer delayedShipments;
        private BigDecimal onTimePerformancePct;
        private Double averageDelayDays;
    }

    @Data
    @Builder
    public static class RouteCongestionMetric {
        private String origin;
        private String destination;
        private Integer shipmentCount;
        private Integer delayedCount;
        private Double averageDelayDays;
        private String congestionLevel; // LOW, MODERATE, HIGH
    }

    @Data
    @Builder
    public static class LogisticsAnalyticsSummary {
        private Integer totalShipments;
        private Integer activeDelayedShipments;
        private Integer inTransitShipments;
        private Integer deliveredShipments;
        private BigDecimal averageDelayDaysSystem;
        private List<CarrierPerformanceMetric> carrierMetrics;
        private List<RouteCongestionMetric> topCongestedRoutes;
    }

    public LogisticsAnalyticsSummary analyzeLogisticsPerformance() {
        log.info("[LOGISTICS ANALYTICS ENGINE] Evaluating carrier performance and route bottlenecks across shipments...");

        List<Shipment> shipments = shipmentRepository.findAll();

        int total = shipments.size();
        int delayed = 0;
        int inTransit = 0;
        int delivered = 0;
        int totalDelayDays = 0;

        Map<String, List<Shipment>> byCarrier = new HashMap<>();
        Map<String, List<Shipment>> byRoute = new HashMap<>();

        for (Shipment s : shipments) {
            if (s.getStatus() == Shipment.ShipmentStatus.DELAYED || (s.getDelayDays() != null && s.getDelayDays() > 0)) {
                delayed++;
                if (s.getDelayDays() != null) {
                    totalDelayDays += s.getDelayDays();
                }
            }
            if (s.getStatus() == Shipment.ShipmentStatus.IN_TRANSIT) {
                inTransit++;
            } else if (s.getStatus() == Shipment.ShipmentStatus.DELIVERED) {
                delivered++;
            }

            String carrier = s.getCarrierName() != null ? s.getCarrierName() : "Unknown Carrier";
            byCarrier.computeIfAbsent(carrier, k -> new ArrayList<>()).add(s);

            String routeKey = (s.getOrigin() != null ? s.getOrigin() : "Unknown Origin") + " -> " + (s.getDestination() != null ? s.getDestination() : "Unknown Destination");
            byRoute.computeIfAbsent(routeKey, k -> new ArrayList<>()).add(s);
        }

        // Carrier Metrics
        List<CarrierPerformanceMetric> carrierMetrics = new ArrayList<>();
        for (Map.Entry<String, List<Shipment>> entry : byCarrier.entrySet()) {
            List<Shipment> carrierShipments = entry.getValue();
            int cTotal = carrierShipments.size();
            int cDelayed = (int) carrierShipments.stream().filter(s -> s.getDelayDays() != null && s.getDelayDays() > 0).count();
            double cOnTime = cTotal > 0 ? ((double) (cTotal - cDelayed) / cTotal) * 100.0 : 100.0;
            double cAvgDelay = carrierShipments.stream().filter(s -> s.getDelayDays() != null).mapToInt(Shipment::getDelayDays).average().orElse(0.0);

            carrierMetrics.add(CarrierPerformanceMetric.builder()
                    .carrierName(entry.getKey())
                    .totalShipments(cTotal)
                    .delayedShipments(cDelayed)
                    .onTimePerformancePct(BigDecimal.valueOf(cOnTime).setScale(1, RoundingMode.HALF_UP))
                    .averageDelayDays(Math.round(cAvgDelay * 10.0) / 10.0)
                    .build());
        }

        // Route Congestion Metrics
        List<RouteCongestionMetric> routeMetrics = new ArrayList<>();
        for (Map.Entry<String, List<Shipment>> entry : byRoute.entrySet()) {
            String[] parts = entry.getKey().split(" -> ");
            String origin = parts[0];
            String destination = parts.length > 1 ? parts[1] : "Unknown";

            List<Shipment> rShipments = entry.getValue();
            int rTotal = rShipments.size();
            int rDelayed = (int) rShipments.stream().filter(s -> s.getDelayDays() != null && s.getDelayDays() > 0).count();
            double rAvgDelay = rShipments.stream().filter(s -> s.getDelayDays() != null).mapToInt(Shipment::getDelayDays).average().orElse(0.0);

            String congestion = "LOW";
            if (rDelayed > 5 || rAvgDelay > 3.0) {
                congestion = "HIGH";
            } else if (rDelayed > 2 || rAvgDelay > 1.0) {
                congestion = "MODERATE";
            }

            routeMetrics.add(RouteCongestionMetric.builder()
                    .origin(origin)
                    .destination(destination)
                    .shipmentCount(rTotal)
                    .delayedCount(rDelayed)
                    .averageDelayDays(Math.round(rAvgDelay * 10.0) / 10.0)
                    .congestionLevel(congestion)
                    .build());
        }

        routeMetrics.sort(Comparator.comparing(RouteCongestionMetric::getDelayedCount).reversed());
        List<RouteCongestionMetric> topRoutes = routeMetrics.stream().limit(5).collect(Collectors.toList());

        BigDecimal avgDelaySystem = delayed > 0 ?
                BigDecimal.valueOf((double) totalDelayDays / delayed).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return LogisticsAnalyticsSummary.builder()
                .totalShipments(total)
                .activeDelayedShipments(delayed)
                .inTransitShipments(inTransit)
                .deliveredShipments(delivered)
                .averageDelayDaysSystem(avgDelaySystem)
                .carrierMetrics(carrierMetrics)
                .topCongestedRoutes(topRoutes)
                .build();
    }
}
