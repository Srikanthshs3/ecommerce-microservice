package org.praskanth.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Login identity shared by every actor (customer, seller, admin, ...). Role-specific data lives in
 * CustomerProfile / SellerProfile.
 *
 * Deletion is soft: the row is anonymised and hidden (deleted_at) so orders, invoices and audit trails
 * that reference the user id stay intact while PII is wiped and the email becomes reusable.
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP(6), account_non_locked = false, password = '', "
        + "name = NULL, phone_number = NULL, email = CONCAT('deleted-', id, '@deleted.invalid') WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(unique = true, nullable = false, length = 254)
    @ToString.Include
    private String email;

    private String name;

    @Column(unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private boolean emailVerified;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> role = new HashSet<>();

    // No REMOVE cascade: deleting a user must never hard-delete the seller/customer profile rows.
    @OneToOne(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private SellerProfile sellerProfile;

    @OneToOne(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private CustomerProfile customerProfile;

    private Instant createdAt;
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Admin / ban lock. Brute-force lockouts are now temporary and live in Redis (LoginAttemptService).
     */
    @Column(name = "account_non_locked")
    @Builder.Default
    private boolean accountNonLocked = true;

    /**
     * @deprecated Unused since login throttling moved to Redis. Kept only because the existing column is
     * NOT NULL; drop it (and this field) in the next schema migration.
     */
    @Deprecated
    @Column(name = "failed_attempt_count")
    @Builder.Default
    private int failedAttemptCount = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean hasRole(org.praskanth.enums.UserRole userRole) {
        return role.stream().anyMatch(r -> r.getName() == userRole);
    }
}
