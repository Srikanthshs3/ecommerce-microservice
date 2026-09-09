package org.praskanth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class UpdateQuantityRequest {

    /** Zero removes the item from the cart. */
    @NotNull
    @PositiveOrZero
    private Integer quantity;
}
