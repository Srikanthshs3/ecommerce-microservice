package org.praskanth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.KycStatusUpdateRequest;
import org.praskanth.dto.SellerProfileRequest;
import org.praskanth.dto.SellerPublicResponse;
import org.praskanth.dto.SellerRegisterRequest;
import org.praskanth.dto.SellerResponse;
import org.praskanth.dto.SellerSummaryResponse;
import org.praskanth.entity.BankDetails;
import org.praskanth.entity.BusinessDetails;
import org.praskanth.entity.Role;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.SellerProfile.KycStatus;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.event.UserRegisteredDomainEvent;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.mapper.SellerMapper;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.SellerProfileRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.service.EmailVerificationService;
import org.praskanth.service.RefreshTokenService;
import org.praskanth.service.SellerService;
import org.praskanth.service.UserAccountFactory;
import org.praskanth.util.Emails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerServiceImpl implements SellerService {

    private static final Set<KycStatus> REASON_REQUIRED = Set.of(KycStatus.REJECTED, KycStatus.SUSPENDED, KycStatus.BANNED);

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final SellerProfileRepo sellerRepo;
    private final UserAccountFactory accountFactory;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final SellerMapper sellerMapper;
    private final ApplicationEventPublisher events;

    // ------------------------------------------------------------------ sign-up

    @Override
    @Transactional
    public SellerResponse register(SellerRegisterRequest request) {
        String email = Emails.normalize(request.email());
        NormalizedProfile profile = normalize(request.profile());

        // 1. cheap checks first - none of these spend the email verification token
        emailVerificationService.assertVerified(email, request.verificationToken());
        accountFactory.assertIdentityAvailable(email, request.phoneNumber());
        validateProfileRules(profile, null);

        // 2. spend the token (atomic, single use), then create the account
        emailVerificationService.consume(email, request.verificationToken());

        User user = accountFactory.newUser(email, request.name(), request.phoneNumber(),
                request.password(), UserRole.SELLER);
        user.setSellerProfile(buildProfile(user, profile));

        User saved = saveOrConflict(user);
        events.publishEvent(new UserRegisteredDomainEvent(saved.getId(), saved.getEmail(), saved.getName(),
                UserRole.SELLER.name()));
        log.info("Seller {} registered, KYC PENDING", saved.getId());
        return sellerMapper.toResponse(saved.getSellerProfile(), false);
    }

    @Override
    @Transactional
    public SellerResponse onboardExistingUser(Long userId, SellerProfileRequest request) {
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("We couldn't find that user."));
        if (user.getSellerProfile() != null) {
            throw new ConflictException("You already have a seller profile");
        }
        NormalizedProfile profile = normalize(request);
        validateProfileRules(profile, null);

        Role sellerRole = roleRepo.findByName(UserRole.SELLER)
                .orElseThrow(() -> new ServiceUnavailableException("This option is not available right now. Please contact support if it keeps happening.", new IllegalStateException("Role SELLER is not configured")));
        user.getRole().add(sellerRole);

        SellerProfile sellerProfile = buildProfile(user, profile);
        user.setSellerProfile(sellerProfile);
        try {
            sellerRepo.saveAndFlush(sellerProfile);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("GSTIN or store name is already registered");
        }
        log.info("User {} onboarded as seller, KYC PENDING", userId);
        return sellerMapper.toResponse(sellerProfile, false);
    }

    // ------------------------------------------------------------------ own profile

    @Override
    @Transactional(readOnly = true)
    public SellerResponse getMyProfile(Long userId) {
        return sellerMapper.toResponse(findSeller(userId), true);
    }

    @Override
    @Transactional
    public SellerResponse updateMyProfile(Long userId, SellerProfileRequest request) {
        SellerProfile seller = findSeller(userId);
        if (seller.getKycStatus().isProfileLocked()) {
            throw new ForbiddenOperationException("Your seller account is " + seller.getKycStatus()
                    + "; the profile can no longer be edited. Contact support.");
        }
        NormalizedProfile profile = normalize(request);
        validateProfileRules(profile, userId);

        // Compliance data changes (tax id, PAN, payout account) need a fresh review, like on Amazon.
        BankDetails oldBank = seller.getBankDetails();
        boolean sensitiveChanged =
                !Objects.equals(profile.gstin, seller.getGstin())
                || !Objects.equals(profile.pan, seller.getPan())
                || !Objects.equals(profile.accountNumber, oldBank.getAccountNumber())
                || !Objects.equals(profile.ifsc, oldBank.getIfscCode())
                || !Objects.equals(profile.accountHolder, oldBank.getAccountHolderName());

        seller.setBusinessDetails(profile.toBusinessDetails());
        seller.setGstin(profile.gstin);
        seller.setPan(profile.pan);
        seller.setBankDetails(profile.toBankDetails());

        if (sensitiveChanged && (seller.getKycStatus() == KycStatus.ACTIVE || seller.getKycStatus() == KycStatus.REJECTED)) {
            seller.setKycStatus(KycStatus.PENDING);
            seller.setStatusReason(null);
            log.info("Seller {} changed compliance data; KYC reset to PENDING", userId);
        }
        try {
            sellerRepo.saveAndFlush(seller);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("GSTIN or store name is already registered");
        }
        return sellerMapper.toResponse(seller, true);
    }

    // ------------------------------------------------------------------ storefront

    @Override
    @Transactional(readOnly = true)
    public SellerPublicResponse getPublicProfile(Long sellerId) {
        SellerProfile seller = sellerRepo.findById(sellerId)
                .filter(s -> s.getKycStatus() == KycStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Seller not found")); // same answer for missing and inactive
        return sellerMapper.toPublic(seller);
    }

    // ------------------------------------------------------------------ admin / support

    @Override
    @Transactional(readOnly = true)
    public Page<SellerSummaryResponse> listSellers(KycStatus status, Pageable pageable) {
        Page<SellerProfile> page = status == null
                ? sellerRepo.findAll(pageable)
                : sellerRepo.findByKycStatus(status, pageable);
        return page.map(sellerMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerResponse getSellerById(Long sellerId) {
        return sellerMapper.toResponse(findSeller(sellerId), true);
    }

    @Override
    @Transactional
    public SellerResponse updateKycStatus(Long sellerId, KycStatusUpdateRequest request, Long reviewerId) {
        SellerProfile seller = findSeller(sellerId);
        KycStatus from = seller.getKycStatus();
        KycStatus to = request.status();

        if (from == to) {
            throw new BadRequestException("Seller is already " + to);
        }
        if (!from.canTransitionTo(to)) {
            throw new BadRequestException("Cannot change seller status from " + from + " to " + to);
        }
        String reason = request.reason() == null ? null : request.reason().trim();
        if (REASON_REQUIRED.contains(to) && (reason == null || reason.isEmpty())) {
            throw new BadRequestException("A reason is required when setting status " + to);
        }

        seller.setKycStatus(to);
        seller.setStatusReason(REASON_REQUIRED.contains(to) ? reason : null);
        seller.setKycReviewedAt(Instant.now());
        seller.setKycReviewedBy(reviewerId);

        if (to == KycStatus.BANNED || to == KycStatus.CLOSED) {
            userRepo.findById(sellerId).ifPresent(u -> u.setAccountNonLocked(false));
            refreshTokenService.revokeAllForUser(sellerId);
        }
        // AUDIT: reviewer, seller, transition and reason. Move to an append-only audit table / event for compliance.
        log.info("Seller {} status {} -> {} by reviewer {} (reason: {})", sellerId, from, to, reviewerId, reason);
        return sellerMapper.toResponse(seller, true);
    }

    // ------------------------------------------------------------------ helpers

    private SellerProfile findSeller(Long sellerId) {
        return sellerRepo.findById(sellerId)
                .orElseThrow(() -> new NotFoundException("We couldn't find that seller."));
    }

    private SellerProfile buildProfile(User user, NormalizedProfile p) {
        return SellerProfile.builder()
                .user(user)
                .gstin(p.gstin)
                .pan(p.pan)
                .businessDetails(p.toBusinessDetails())
                .bankDetails(p.toBankDetails())
                .kycStatus(KycStatus.PENDING)
                .termsAcceptedAt(Instant.now())
                .build();
    }

    /** Business rules that annotations cannot express. {@code selfId} excludes the seller's own row on update. */
    private void validateProfileRules(NormalizedProfile p, Long selfId) {
        // Characters 3-12 of a GSTIN are the holder's PAN, so the two must agree.
        if (!p.gstin.substring(2, 12).equals(p.pan)) {
            throw new BadRequestException("PAN does not match the PAN embedded in the GSTIN");
        }
        boolean gstinTaken = selfId == null
                ? sellerRepo.existsByGstin(p.gstin)
                : sellerRepo.existsByGstinAndIdNot(p.gstin, selfId);
        if (gstinTaken) {
            throw new ConflictException("This GSTIN is already registered with another seller");
        }
        boolean nameTaken = selfId == null
                ? sellerRepo.existsByBusinessDetailsBusinessNameIgnoreCase(p.businessName)
                : sellerRepo.existsByBusinessDetailsBusinessNameIgnoreCaseAndIdNot(p.businessName, selfId);
        if (nameTaken) {
            throw new ConflictException("This store name is already taken");
        }
    }

    private User saveOrConflict(User user) {
        try {
            return userRepo.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An account with this email, phone number, GSTIN or store name already exists");
        }
    }

    private static NormalizedProfile normalize(SellerProfileRequest r) {
        return new NormalizedProfile(
                r.gstin().trim().toUpperCase(Locale.ROOT),
                r.pan().trim().toUpperCase(Locale.ROOT),
                r.business().businessName().trim(),
                r.business().legalName().trim(),
                r.business().businessAddress().trim(),
                r.business().businessPhone().trim(),
                Emails.normalize(r.business().businessEmail()),
                r.business().logo(),
                r.business().banner(),
                r.bank().accountHolderName().trim(),
                r.bank().accountNumber().trim(),
                r.bank().ifscCode().trim().toUpperCase(Locale.ROOT));
    }

    /** Request values after trimming / upper-casing, so comparisons and uniqueness checks are reliable. */
    private record NormalizedProfile(String gstin, String pan, String businessName, String legalName,
                                     String businessAddress, String businessPhone, String businessEmail,
                                     String logo, String banner,
                                     String accountHolder, String accountNumber, String ifsc) {

        BusinessDetails toBusinessDetails() {
            return BusinessDetails.builder()
                    .businessName(businessName).legalName(legalName).businessAddress(businessAddress)
                    .businessPhone(businessPhone).businessEmail(businessEmail).logo(logo).banner(banner)
                    .build();
        }

        BankDetails toBankDetails() {
            return BankDetails.builder()
                    .accountHolderName(accountHolder).accountNumber(accountNumber).ifscCode(ifsc)
                    .build();
        }
    }
}
