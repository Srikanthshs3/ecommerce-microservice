package org.praskanth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.praskanth.entity.DeliveryAgentProfile.AgentStatus;

import java.time.Instant;
import java.util.List;

public final class DeliveryAgentDtos {

    private DeliveryAgentDtos() {}

    /** Apply (or re-apply after a rejection) and edit-my-details. */
    public record AgentRequest(
            @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "Enter a valid phone number") String phone,
            @NotBlank @Pattern(regexp = "(?i)^(BICYCLE|SCOOTER|BIKE|VAN)$", message = "Vehicle must be BICYCLE, SCOOTER, BIKE or VAN") String vehicleType,
            @Size(max = 20) String vehicleNumber,
            @NotBlank @Pattern(regexp = "(?i)^(AADHAAR|DRIVING_LICENCE|VOTER_ID|PAN)$", message = "ID proof must be AADHAAR, DRIVING_LICENCE, VOTER_ID or PAN") String idProofType,
            @NotBlank @Pattern(regexp = "(?i)^[A-Z0-9]{6,20}$", message = "ID number must be 6-20 letters or digits") String idProofNumber,
            @NotBlank @Size(max = 60) String city,
            /** Comma or space separated 6-digit pincodes, 1 to 20 of them. */
            @NotBlank @Size(max = 300) String pincodes) {}


    /**
     * Delivery partner sign-up = login credentials + verified email token + partner details + agreement.
     * Same three steps as customers and sellers: /auth/register/initiate -> /auth/register/verify-otp -> POST /delivery-agents/register.
     * The account phone number doubles as the partner's contact phone.
     */
    public record PartnerRegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String verificationToken,
            @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE) String password,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "Enter a valid phone number") String phoneNumber,
            @NotBlank @Pattern(regexp = "(?i)^(BICYCLE|SCOOTER|BIKE|VAN)$", message = "Vehicle must be BICYCLE, SCOOTER, BIKE or VAN") String vehicleType,
            @Size(max = 20) String vehicleNumber,
            @NotBlank @Pattern(regexp = "(?i)^(AADHAAR|DRIVING_LICENCE|VOTER_ID|PAN)$", message = "ID proof must be AADHAAR, DRIVING_LICENCE, VOTER_ID or PAN") String idProofType,
            @NotBlank @Pattern(regexp = "(?i)^[A-Z0-9]{6,20}$", message = "ID number must be 6-20 letters or digits") String idProofNumber,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Size(max = 300) String pincodes,
            @AssertTrue(message = "You must accept the delivery partner terms") boolean acceptTerms) {
        @Override
        public String toString() {
            return "PartnerRegisterRequest[email=" + email + "]"; // never print secrets
        }
    }

    public record StatusRequest(@NotNull AgentStatus status, @Size(max = 500) String reason) {}

    public record DutyRequest(@NotNull Boolean onDuty) {}

    /** The id number is only ever shown masked. */
    public record AgentResponse(Long id, String name, String email, String phone, String vehicleType, String vehicleNumber,
                                String idProofType, String idProofMasked, String city, List<String> pincodes,
                                AgentStatus status, String statusReason, boolean onDuty, Instant createdAt,
                                Instant reviewedAt) {}
}
