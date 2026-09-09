package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.praskanth.dto.BankDetails;
import org.praskanth.dto.BusinessDetails;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seller_profile")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SellerProfile {

    @Id
    private Long id;

    @Column(name = "user_id", updatable = false, insertable = false)
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus;

    @Embedded
    private BankDetails bankDetails = new BankDetails();

    @Embedded
    private BusinessDetails  businessDetails = new BusinessDetails();


    @OneToMany(mappedBy = "sellerProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Address> addresses = new ArrayList<>();

    private String GSTIN;

    public enum KycStatus {
        PENDING, ACTIVE, REJECTED, SUSPENDED, DEACTIVATED, BANNED, CLOSED
    }


}
