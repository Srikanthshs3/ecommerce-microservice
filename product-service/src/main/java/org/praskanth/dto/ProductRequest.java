package org.praskanth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {

    @NotBlank
    private String name;

    private String description;

    private String kycStatus;

    /** Required for a simple product; for a product with variants the prices come from the variants. */
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal mrpPrice;

    @NotBlank
    private String categoryId;
    private Long sellerId;

    private List<String> imageUrl;

    @DecimalMin(value = "0.0", inclusive = true, message = "Selling price cannot be negative")
    private BigDecimal sellingPrice;
    private String color;

    /** Opening stock of a simple product (required for it). Variants carry their own stock. */
    @PositiveOrZero
    private Integer stock;

    /**
     * Option names, e.g. ["Colour","Size"]. Leave null on update to keep the variants untouched; send the names plus
     * {@code variants} to define or change them.
     */
    @Size(max = 2)
    private List<String> optionNames;

    @Size(max = 100)
    @Valid
    private List<VariantDtos.VariantRequest> variants;
}
