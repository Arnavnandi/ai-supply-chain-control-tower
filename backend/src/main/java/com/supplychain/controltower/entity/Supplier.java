package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String contactPerson;
    private String email;
    private String phone;
    private String country;

    @Column(precision = 5, scale = 2)
    private BigDecimal reliabilityScore; // 0 to 100%

    @Column(precision = 5, scale = 2)
    private BigDecimal deliveryPerformancePct; // On-time delivery rate %

    private Double averageLeadTimeDays;
    private Double leadTimeVarianceDays; // Standard deviation / variance in delivery time
}
