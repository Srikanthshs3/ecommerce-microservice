package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SellerResponse {
    private Long id;
    private Long userId;
    private String GSTIN;
    private String kycStatus;
    private List<AddressResponse> addresses;
    private BusinessDetails businessDetails;
    private BankDetails bankDetails;

}
