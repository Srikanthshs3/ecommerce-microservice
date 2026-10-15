package org.praskanth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ValidateRequest(@NotBlank @Size(max = 50) String code, @NotEmpty @Valid List<CouponLine> lines) {
}
