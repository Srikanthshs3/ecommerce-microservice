package org.praskanth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessDetails {

    /** Public store / display name shown to customers ("Sold by ..."). Unique across sellers. */
    private String businessName;

    /** Registered legal entity name, used for invoicing and KYC. */
    @Column(name = "legal_name")
    private String legalName;

    @Column(length = 500)
    private String businessAddress;
    private String businessPhone;
    private String businessEmail;
    @Column(length = 500)
    private String logo;
    @Column(length = 500)
    private String banner;
}
