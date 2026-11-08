package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.DeliveryAgentDtos.AgentRequest;
import org.praskanth.dto.DeliveryAgentDtos.AgentResponse;
import org.praskanth.dto.DeliveryAgentDtos.PartnerRegisterRequest;
import org.praskanth.entity.DeliveryAgentProfile;
import org.praskanth.entity.DeliveryAgentProfile.AgentStatus;
import org.praskanth.entity.Role;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.event.UserRegisteredDomainEvent;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.DeliveryAgentRepo;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.util.Emails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Delivery partner onboarding, mirroring the seller KYC flow:
 *   a person REGISTERS as a delivery partner (own account, role DELIVERY_PARTNER, profile PENDING)
 *   -> admin APPROVES or REJECTS (reason shown; the partner may fix the details and re-apply)
 *   an approved agent can be SUSPENDED (off duty) and later approved again.
 * The PROFILE STATUS is the gate (shipping-service refuses anyone who is not APPROVED); the role only says "this account is a partner".
 * Role changes revoke the user's refresh tokens, so the next login carries the new roles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryAgentService {

    private static final Set<AgentStatus> REASON_REQUIRED = Set.of(AgentStatus.REJECTED, AgentStatus.SUSPENDED);
    private static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");
    private static final int MAX_PINCODES = 20;

    private final DeliveryAgentRepo agents;
    private final UserRepo users;
    private final RoleRepo roles;
    private final RefreshTokenService refreshTokens;
    private final EmailVerificationService emailVerification;
    private final UserAccountFactory accountFactory;
    private final ApplicationEventPublisher events;

    // ------------------------------------------------------------------ sign-up

    /** Public sign-up of a delivery partner: new login (role DELIVERY_PARTNER) + profile PENDING, like a seller with KYC PENDING. */
    @Transactional
    public AgentResponse register(PartnerRegisterRequest request) {
        String email = Emails.normalize(request.email());

        // 1. cheap checks first - none of these spend the email verification token
        emailVerification.assertVerified(email, request.verificationToken());
        accountFactory.assertIdentityAvailable(email, request.phoneNumber());
        parsePincodes(request.pincodes());

        // 2. spend the token (atomic, single use), then create the account and the profile
        emailVerification.consume(email, request.verificationToken());
        User user = accountFactory.newUser(email, request.name(), request.phoneNumber(), request.password(), UserRole.DELIVERY_PARTNER);
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An account with this email or phone number already exists");
        }

        DeliveryAgentProfile profile = DeliveryAgentProfile.builder().user(user).status(AgentStatus.PENDING).build();
        fill(profile, new AgentRequest(request.phoneNumber(), request.vehicleType(), request.vehicleNumber(),
                request.idProofType(), request.idProofNumber(), request.city(), request.pincodes()));
        agents.save(profile);

        events.publishEvent(new UserRegisteredDomainEvent(user.getId(), user.getEmail(), user.getName(), UserRole.DELIVERY_PARTNER.name()));
        log.info("Delivery partner {} registered, status PENDING", user.getId());
        return toResponse(profile);
    }

    // ------------------------------------------------------------------ the agent

    @Transactional
    public AgentResponse apply(Long userId, AgentRequest request) {
        DeliveryAgentProfile existing = agents.findById(userId).orElse(null);
        if (existing == null) {
            // customers no longer turn into partners from a menu link: a partner signs up with their own account
            throw new ForbiddenOperationException("Delivery partners register with their own account (Register > Delivery partner).");
        }
        if (existing.getStatus() != AgentStatus.REJECTED) {
            throw new ConflictException("You already have a delivery partner profile (" + existing.getStatus() + ").");
        }
        DeliveryAgentProfile profile = existing;
        fill(profile, request);
        profile.setStatus(AgentStatus.PENDING); // a re-application goes back to review
        profile.setStatusReason(null);
        agents.save(profile);
        log.info("User {} applied as a delivery partner", userId);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public AgentResponse me(Long userId) {
        return toResponse(find(userId));
    }

    /** Edit contact, vehicle and service area. Not while suspended; an approved agent keeps working. */
    @Transactional
    public AgentResponse updateMe(Long userId, AgentRequest request) {
        DeliveryAgentProfile profile = find(userId);
        if (profile.getStatus() == AgentStatus.SUSPENDED || profile.getStatus() == AgentStatus.REJECTED) {
            throw new ConflictException("Your profile is " + profile.getStatus() + ", so it cannot be edited now.");
        }
        fill(profile, request);
        return toResponse(agents.save(profile));
    }

    @Transactional
    public AgentResponse setDuty(Long userId, boolean onDuty) {
        DeliveryAgentProfile profile = find(userId);
        if (profile.getStatus() != AgentStatus.APPROVED) {
            throw new ConflictException("Only an approved delivery partner can go on duty (you are " + profile.getStatus() + ").");
        }
        profile.setOnDuty(onDuty);
        return toResponse(agents.save(profile));
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public Page<AgentResponse> list(AgentStatus status, Pageable pageable) {
        Page<DeliveryAgentProfile> page = status == null ? agents.findAll(pageable) : agents.findByStatus(status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AgentResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public AgentResponse review(Long id, AgentStatus to, String reason, Long adminId) {
        DeliveryAgentProfile profile = find(id);
        AgentStatus from = profile.getStatus();
        if (from == to) {
            throw new BadRequestException("The agent is already " + to);
        }
        if (!from.canTransitionTo(to)) {
            throw new BadRequestException("Cannot change a delivery partner from " + from + " to " + to);
        }
        String why = reason == null ? null : reason.trim();
        if (REASON_REQUIRED.contains(to) && (why == null || why.isEmpty())) {
            throw new BadRequestException("A reason is required when setting status " + to);
        }

        profile.setStatus(to);
        profile.setStatusReason(REASON_REQUIRED.contains(to) ? why : null);
        profile.setReviewedAt(Instant.now());
        profile.setReviewedBy(adminId);

        User user = profile.getUser();
        if (to == AgentStatus.APPROVED) {
            Role role = roles.findByName(UserRole.DELIVERY_PARTNER)
                    .orElseThrow(() -> new ServiceUnavailableException("This option is not available right now. Please contact support if it keeps happening.", new IllegalStateException("Role DELIVERY_PARTNER is missing in the database")));
            user.getRole().add(role);
        } else {
            // a partner who signed up as a partner has no other role: keep it (the profile status is the gate).
            // Someone who also holds another role (an old-style applicant who was a customer) loses only this one.
            if (user.getRole().size() > 1) {
                user.getRole().removeIf(r -> r.getName() == UserRole.DELIVERY_PARTNER);
            }
            profile.setOnDuty(false);
        }
        users.save(user);
        agents.save(profile);
        refreshTokens.revokeAllForUser(user.getId()); // new roles only reach the token at the next login
        log.info("Admin {} moved delivery partner {} {} -> {} (reason: {})", adminId, id, from, to, why);
        return toResponse(profile);
    }

    // ------------------------------------------------------------------ helpers

    private DeliveryAgentProfile find(Long id) {
        return agents.findById(id)
                .orElseThrow(() -> new NotFoundException("No delivery partner profile was found for this account."));
    }

    private void fill(DeliveryAgentProfile p, AgentRequest r) {
        p.setPhone(r.phone().trim());
        p.setVehicleType(r.vehicleType().trim().toUpperCase(Locale.ROOT));
        p.setVehicleNumber(r.vehicleNumber() == null || r.vehicleNumber().isBlank() ? null : r.vehicleNumber().trim().toUpperCase(Locale.ROOT));
        p.setIdProofType(r.idProofType().trim().toUpperCase(Locale.ROOT));
        p.setIdProofNumber(r.idProofNumber().trim().toUpperCase(Locale.ROOT));
        p.setCity(r.city().trim());
        p.setServiceablePincodes(String.join(",", parsePincodes(r.pincodes())));
    }

    /** "500001, 500002 500001" -> [500001, 500002]. Rejects anything that is not a 6-digit pincode. */
    static List<String> parsePincodes(String raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String token : raw.split("[,\\s;]+")) {
            if (token.isBlank()) {
                continue;
            }
            if (!PINCODE.matcher(token).matches()) {
                throw new BadRequestException("'" + token + "' is not a valid 6-digit pincode");
            }
            out.add(token);
        }
        if (out.isEmpty()) {
            throw new BadRequestException("Enter at least one pincode you can deliver to");
        }
        if (out.size() > MAX_PINCODES) {
            throw new BadRequestException("You can serve at most " + MAX_PINCODES + " pincodes");
        }
        return List.copyOf(out);
    }

    private AgentResponse toResponse(DeliveryAgentProfile p) {
        String id = p.getIdProofNumber();
        String masked = id == null ? null : "XXXX" + id.substring(Math.max(0, id.length() - 4));
        User u = p.getUser();
        return new AgentResponse(p.getId(), u.getName(), u.getEmail(), p.getPhone(), p.getVehicleType(), p.getVehicleNumber(),
                p.getIdProofType(), masked, p.getCity(), Arrays.asList(p.getServiceablePincodes().split(",")),
                p.getStatus(), p.getStatusReason(), p.isOnDuty(), p.getCreatedAt(), p.getReviewedAt());
    }
}
