package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReleaseRequest(@NotBlank @Size(max = 64) String checkoutRef) {
}
