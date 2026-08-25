package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationType type; // REORDER_STOCK, REALLOCATE_INVENTORY, CHANGE_SUPPLIER, EXPEDITE_SHIPMENT

    @Column(columnDefinition = "TEXT", nullable = false)
    private String actionPayloadJson; // Details like productId, targetQty, warehouseId, supplierId

    @Column(length = 2000)
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status; // PENDING_APPROVAL, APPROVED, REJECTED, EXECUTED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime executedAt;
    private String executedBy;

    public enum RecommendationType {
        REORDER_STOCK,
        REALLOCATE_INVENTORY,
        CHANGE_SUPPLIER,
        EXPEDITE_SHIPMENT
    }

    public enum ApprovalStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        EXECUTED
    }
}
