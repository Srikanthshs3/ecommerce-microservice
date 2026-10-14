package org.praskanth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchCheckItem {
    @NotBlank
    private String productId;
    private String size;
    private String color;
    @NotNull
    @Min(1)
    private Integer quantity;
}