package org.praskanth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.praskanth.enums.UserRole;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "name is required")
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "email is required")
    private String email;

    @NotBlank(message = "password is required")
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    //@Column(name = "phone_number", nullable = false, unique = true)
    //@NotBlank(message = "phone number is required")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    /*@OneToMany
    private Set<Address> address = new HashSet<>();*/

    //@Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    //@Builder.Default
    //@Column(nullable = false)
    private boolean emailVerified = true;

    /*@ManyToMany
    @JsonIgnore
    private Set<Coupon> usedCoupons = new HashSet<>();*/


}
