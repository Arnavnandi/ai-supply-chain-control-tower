package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskCategory riskCategory; // STOCKOUT, OVERSTOCK, SUPPLIER_DELAY, SHIPMENT_DELAY, WAREHOUSE_CAPACITY

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeverityLevel severityLevel; // LOW, MEDIUM, HIGH, CRITICAL

    private String entityType; // Product, Supplier, Shipment, Warehouse
    private Long entityId;

    @Column(nullable = false, length = 1500)
    private String description;

    @Column(length = 2000)
    private String recommendationText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskStatus status; // ACTIVE, MITIGATED, RESOLVED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum RiskCategory {
        STOCKOUT,
        OVERSTOCK,
        SUPPLIER_DELAY,
        SHIPMENT_DELAY,
        WAREHOUSE_CAPACITY
    }

    public enum SeverityLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum RiskStatus {
        ACTIVE,
        MITIGATED,
        RESOLVED
    }
}
