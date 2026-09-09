package org.praskanth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequest {

    private Long userId;

    private Long addressId;

    /*@NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;*/
}
