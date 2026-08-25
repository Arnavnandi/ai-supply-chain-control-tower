package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private Integer totalCapacityUnits;

    @Column(nullable = false)
    private Integer currentUtilizationUnits;

    @Column(precision = 5, scale = 2)
    private BigDecimal utilizationPercentage;

    private String managerName;
    private String contactEmail;
}
