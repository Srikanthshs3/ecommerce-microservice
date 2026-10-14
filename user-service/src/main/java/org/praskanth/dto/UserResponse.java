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
    private Instant createdAt;
    /** Null in list views (avoids N+1 loading); populated on single-user reads. */
    private List<AddressResponse> addresses;
}
