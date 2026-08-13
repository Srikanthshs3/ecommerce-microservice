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
    private Set<String> role;
    private String kycStatus;   // PENDING, APPROVED, REJECTED, or null for customers
    private String storeName;   // Set if seller
    private Instant createdAt;
    private List<AddressResponse> addresses; // List of addresses for the user
}
