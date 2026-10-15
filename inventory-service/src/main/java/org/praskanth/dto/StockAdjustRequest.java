package org.praskanth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class StockAdjustRequest {

    @NotNull
    @Positive
    private Integer quantity;
}
