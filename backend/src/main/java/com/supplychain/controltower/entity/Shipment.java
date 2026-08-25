package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String trackingCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destinationWarehouse;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    private String origin;
    private String destination;

    @Column(nullable = false)
    private LocalDate shippedDate;

    @Column(nullable = false)
    private LocalDate estimatedDeliveryDate;

    private LocalDate actualDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    private Integer delayDays;
    private String carrierName;

    public enum ShipmentStatus {
        PENDING,
        IN_TRANSIT,
        DELIVERED,
        DELAYED,
        CANCELLED
    }
}
