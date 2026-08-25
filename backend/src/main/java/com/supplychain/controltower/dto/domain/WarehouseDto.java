package com.supplychain.controltower.dto.domain;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseDto {
    private Long id;
    private String code;
    private String name;
    private String location;
    private Integer totalCapacityUnits;
    private Integer currentUtilizationUnits;
    private BigDecimal utilizationPercentage;
    private String managerName;
    private String contactEmail;
}
