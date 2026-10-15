package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private String phoneNumber;
    private Set<String> role;
    private boolean emailVerified;
    /** False = blocked (too many failed logins, or blocked by an admin). Shown on the admin Users page. */
    private boolean accountNonLocked;
    private Instant createdAt;
    /** Null in list views (avoids N+1 loading); populated on single-user reads. */
    private List<AddressResponse> addresses;
}
