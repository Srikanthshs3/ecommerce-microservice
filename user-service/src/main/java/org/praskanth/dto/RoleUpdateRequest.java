package org.praskanth.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.praskanth.enums.UserRole;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleUpdateRequest {

    @NotEmpty(message = "At least one role must be provided")
    private Set<UserRole> roles;
}
