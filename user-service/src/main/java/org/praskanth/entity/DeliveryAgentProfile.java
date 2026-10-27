package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.praskanth.crypto.EncryptedStringConverter;

import java.time.Instant;
import java.util.Set;

/**
 * Delivery-partner data, one-to-one with the login identity (agent id == user id), like SellerProfile.
 * The DELIVERY_PARTNER role is granted when an admin APPROVES the profile and taken away when it is rejected or
 * suspended, so the role and the profile can never disagree for long.
 */
@Entity
@Table(name = "delivery_agent_profile", indexes = @Index(name = "idx_agent_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DeliveryAgentProfile {

    @Id
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    // varchar, not a MySQL ENUM column: adding a status later must not need a table change
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    @Builder.Default
    private AgentStatus status = AgentStatus.PENDING;

    /** Why the application was rejected / the agent suspended. The agent sees it so they can act on it. */
    @Column(length = 500)
    private String statusReason;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(nullable = false, length = 20)
    private String vehicleType;

    @Column(length = 20)
    private String vehicleNumber;

    @Column(nullable = false, length = 20)
    private String idProofType;

    /** Encrypted at rest (AES-GCM), like bank account numbers. Never returned in full. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 255)
    private String idProofNumber;

    @Column(nullable = false, length = 60)
    private String city;

    /** Comma separated 6-digit pincodes the agent serves, e.g. "500001,500002". Normalised by the service. */
    @Column(nullable = false, length = 300)
    private String serviceablePincodes;

    /** On duty = visible to the parcel pool. Only an APPROVED agent can switch it on. */
    @Column(nullable = false)
    @Builder.Default
    private boolean onDuty = false;

    private Instant reviewedAt;
    private Long reviewedBy;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum AgentStatus {
        PENDING, APPROVED, REJECTED, SUSPENDED;

        private static final java.util.Map<AgentStatus, Set<AgentStatus>> NEXT = java.util.Map.of(
                PENDING, Set.of(APPROVED, REJECTED),
                APPROVED, Set.of(SUSPENDED),
                SUSPENDED, Set.of(APPROVED),
                REJECTED, Set.of(APPROVED, PENDING));

        public boolean canTransitionTo(AgentStatus next) {
            return NEXT.getOrDefault(this, Set.of()).contains(next);
        }
    }
}
