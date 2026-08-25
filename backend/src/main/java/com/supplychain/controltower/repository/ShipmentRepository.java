package com.supplychain.controltower.repository;

import com.supplychain.controltower.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByTrackingCode(String trackingCode);
    List<Shipment> findByStatus(Shipment.ShipmentStatus status);
    List<Shipment> findBySupplierId(Long supplierId);
    List<Shipment> findByDestinationWarehouseId(Long warehouseId);
}
