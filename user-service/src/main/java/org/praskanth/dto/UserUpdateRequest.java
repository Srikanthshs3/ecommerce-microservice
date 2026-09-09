package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserUpdateRequest {

    @NotBlank(message = "Name cannot be empty")
    private String name;

    private String GSTIN;
}
