package com.supplychain.controltower.dto.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
    private Long id;

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be non-negative")
    private BigDecimal price;

    private Long categoryId;
    private String categoryName;

    @NotNull(message = "Reorder level is required")
    private Integer reorderLevel;

    @NotNull(message = "Safety stock is required")
    private Integer safetyStock;

    @NotNull(message = "Lead time in days is required")
    private Integer leadTimeDays;

    private String unitOfMeasure;
}
