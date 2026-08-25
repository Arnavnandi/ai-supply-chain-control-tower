package com.supplychain.controltower.controller;

import com.supplychain.controltower.dto.domain.ShipmentDto;
import com.supplychain.controltower.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping
    public ResponseEntity<List<ShipmentDto>> getAllShipments() {
        return ResponseEntity.ok(shipmentService.getAllShipments());
    }

    @GetMapping("/delayed")
    public ResponseEntity<List<ShipmentDto>> getDelayedShipments() {
        return ResponseEntity.ok(shipmentService.getDelayedShipments());
    }
}
