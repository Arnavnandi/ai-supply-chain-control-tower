package com.supplychain.controltower.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "demand_forecasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private LocalDate forecastDate;

    @Column(nullable = false)
    private Integer predictedQuantity;

    private Double confidenceScore;

    @Column(nullable = false)
    private String methodUsed; // E.g., WEIGHTED_MOVING_AVERAGE, EXPONENTIAL_SMOOTHING
}
