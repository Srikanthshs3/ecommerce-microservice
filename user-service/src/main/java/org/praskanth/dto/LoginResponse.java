package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private Long expiresIn;
    private String email;
    private String name;
    /** Highest-privilege role, kept so existing clients that read "role" keep working. */
    private String role;
    /** All roles of the user (a person can be CUSTOMER and SELLER at once). */
    private Set<String> roles;
}
