package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seller_profile")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SellerProfile {

    @Id
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private String storeName;

    private String businessRegistrationNumber;
    private String taxIdentifier;

    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus;

    private String bankAccountNumber;
    private String bankIFSCCode;

    public enum KycStatus {
        PENDING, APPROVED, REJECTED, SUSPENDED
    }


}
