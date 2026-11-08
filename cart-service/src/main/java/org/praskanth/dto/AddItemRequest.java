package org.praskanth.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddItemRequest {

    @NotBlank
    private String productId;

    /** Required when the product has variants (the id of the chosen colour/size combination); omit for simple products. */
    private String variantId;

    /** Legacy: only used by products without variants. */
    private String size;

    @NotNull
    @Positive
    private Integer quantity;
}
