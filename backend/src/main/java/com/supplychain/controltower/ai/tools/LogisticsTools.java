package com.supplychain.controltower.ai.tools;

import com.supplychain.controltower.entity.Shipment;
import com.supplychain.controltower.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogisticsTools {

    private final ShipmentRepository shipmentRepository;

    @Description("Retrieves all currently delayed shipments, including delay days and carrier information.")
    public List<DelayedShipmentRecord> getDelayedShipments() {
        log.info("[SPRING AI TOOL EXECUTING] getDelayedShipments() querying PostgreSQL database...");
        List<DelayedShipmentRecord> results = shipmentRepository.findByStatus(Shipment.ShipmentStatus.DELAYED).stream().map(s ->
                new DelayedShipmentRecord(
                        s.getId(),
                        s.getTrackingCode(),
                        s.getSupplier() != null ? s.getSupplier().getName() : "N/A",
                        s.getOrigin(),
                        s.getDestination(),
                        s.getEstimatedDeliveryDate(),
                        s.getDelayDays(),
                        s.getCarrierName()
                )
        ).toList();
        log.info("[SPRING AI TOOL COMPLETE] getDelayedShipments() returned {} delayed shipments.", results.size());
        return results;
    }

    public record DelayedShipmentRecord(
            Long id,
            String trackingCode,
            String supplierName,
            String origin,
            String destination,
            LocalDate expectedDelivery,
            Integer delayDays,
            String carrier
    ) {}
}

