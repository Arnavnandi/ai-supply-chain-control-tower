package com.supplychain.controltower.dto.domain;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierDto {
    private Long id;
    private String code;
    private String name;
    private String contactPerson;
    private String email;
    private String phone;
    private String country;
    private BigDecimal reliabilityScore;
    private BigDecimal deliveryPerformancePct;
    private Double averageLeadTimeDays;
    private Double leadTimeVarianceDays;
}
