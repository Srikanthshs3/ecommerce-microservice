package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplyCouponRequest {

    @NotBlank
    @Size(max = 50)
    private String code;
}
