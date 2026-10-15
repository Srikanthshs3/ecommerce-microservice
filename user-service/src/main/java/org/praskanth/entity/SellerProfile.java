package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Seller-specific data, one-to-one with the login identity (seller id == user id).
 * GSTIN uniqueness is enforced by the database so concurrent registrations cannot both win.
 */
@Entity
@Table(name = "seller_profile",
        uniqueConstraints = @UniqueConstraint(name = "uk_seller_gstin", columnNames = "gstin"),
        indexes = @Index(name = "idx_seller_kyc_status", columnList = "kyc_status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SellerProfile {

    @Id
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id", updatable = false, insertable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status")
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    /** Why the seller was rejected / suspended / banned. Shown to the seller so they can act on it. */
    @Column(length = 500)
    private String statusReason;

    private Instant kycReviewedAt;
    private Long kycReviewedBy;

    @Embedded
    @Builder.Default
    private BankDetails bankDetails = new BankDetails();

    @Embedded
    @Builder.Default
    private BusinessDetails businessDetails = new BusinessDetails();

    @OneToMany(mappedBy = "sellerProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan", length = 10)
    private String pan;

    private Instant termsAcceptedAt;
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

    /**
     * Seller lifecycle. PENDING -> ACTIVE/REJECTED after review; ACTIVE sellers can be suspended,
     * deactivated, banned or closed; BANNED/CLOSED are terminal (BANNED can still be closed).
     */
    public enum KycStatus {
        PENDING, ACTIVE, REJECTED, SUSPENDED, DEACTIVATED, BANNED, CLOSED;

        public boolean canTransitionTo(KycStatus target) {
            return allowedTargets().contains(target);
        }

        private Set<KycStatus> allowedTargets() {
            return switch (this) {
                case PENDING -> EnumSet.of(ACTIVE, REJECTED, CLOSED);
                case REJECTED -> EnumSet.of(PENDING, CLOSED);
                case ACTIVE -> EnumSet.of(PENDING, SUSPENDED, DEACTIVATED, BANNED, CLOSED);
                case SUSPENDED -> EnumSet.of(ACTIVE, BANNED, CLOSED);
                case DEACTIVATED -> EnumSet.of(ACTIVE, CLOSED);
                case BANNED -> EnumSet.of(CLOSED);
                case CLOSED -> EnumSet.noneOf(KycStatus.class);
            };
        }

        /** Statuses in which the seller may not edit their own profile. */
        public boolean isProfileLocked() {
            return this == SUSPENDED || this == BANNED || this == CLOSED;
        }
    }
}
