package org.praskanth.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {

    @NotBlank
    private String name;

    private String description;

    private String kycStatus;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal mrpPrice;

    @NotBlank
    private String categoryId;
    private Long sellerId;

    private List<String> imageUrl;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Selling price cannot be negative")
    private BigDecimal sellingPrice;
    private String color;

    @NotNull
    @PositiveOrZero
    private Integer stock;
}
