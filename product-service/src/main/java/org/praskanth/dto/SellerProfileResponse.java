package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The only part of user-service's seller profile that product-service needs: the CURRENT KYC status
 * (PENDING, ACTIVE, REJECTED, SUSPENDED, ...). Everything else in the payload is ignored on purpose, so
 * user-service can evolve its seller DTO without breaking product creation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SellerProfileResponse(Long id, String kycStatus) {
}
