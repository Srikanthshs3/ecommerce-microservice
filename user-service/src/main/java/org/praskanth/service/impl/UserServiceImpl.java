package org.praskanth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.CustomerRegisterRequest;
import org.praskanth.dto.RoleUpdateRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.dto.UserUpdateRequest;
import org.praskanth.entity.CustomerProfile;
import org.praskanth.entity.Role;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.event.UserRegisteredDomainEvent;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.mapper.UserMapper;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.service.EmailVerificationService;
import org.praskanth.service.LoginAttemptService;
import org.praskanth.service.RefreshTokenService;
import org.praskanth.service.UserAccountFactory;
import org.praskanth.service.UserService;
import org.praskanth.util.Emails;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserAccountFactory accountFactory;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- customer registration

    @Override
    @Transactional
    public UserResponse registerCustomer(CustomerRegisterRequest request) {
        String email = Emails.normalize(request.email());

        emailVerificationService.assertVerified(email, request.verificationToken());
        accountFactory.assertIdentityAvailable(email, request.phoneNumber());
        emailVerificationService.consume(email, request.verificationToken());

        User user = accountFactory.newUser(email, request.name(), request.phoneNumber(),
                request.password(), UserRole.CUSTOMER);
        user.setCustomerProfile(CustomerProfile.builder().user(user).fullName(request.name().trim()).build());

        User saved = saveOrConflict(user);
        events.publishEvent(new UserRegisteredDomainEvent(saved.getId(), saved.getEmail(), saved.getName(),
                UserRole.CUSTOMER.name()));
        log.info("Customer {} registered", saved.getId());
        return userMapper.toResponse(saved, false);
    }

    // ---------------------------------------------------------------- self service

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userMapper.toResponse(findUser(userId), true);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = findUser(userId);
        user.setName(request.name().trim());

        String phone = request.phoneNumber() == null || request.phoneNumber().isBlank()
                ? null : request.phoneNumber().trim();
        if (phone != null && !phone.equals(user.getPhoneNumber()) && userRepo.existsByPhoneNumber(phone)) {
            throw new ConflictException("This phone number is already in use");
        }
        user.setPhoneNumber(phone);
        if (user.getCustomerProfile() != null) {
            user.getCustomerProfile().setFullName(user.getName());
        }
        log.info("Profile updated for user {}", userId);
        return userMapper.toResponse(user, true);
    }

    // ---------------------------------------------------------------- admin / support

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        return userMapper.toResponse(findUser(userId), true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepo.findAll(pageable).map(u -> userMapper.toResponse(u, false));
    }

    @Override
    @Transactional
    public UserResponse updateUserRoles(Long userId, RoleUpdateRequest request, Long actingAdminId) {
        User user = findUser(userId);
        Set<UserRole> requested = request.getRoles();

        // CUSTOMER and SELLER are backed by profile rows, so admins cannot grant or strip them by hand.
        if (requested.contains(UserRole.CUSTOMER) != (user.getCustomerProfile() != null)) {
            throw new BadRequestException("CUSTOMER role is tied to the customer profile and cannot be changed here");
        }
        if (requested.contains(UserRole.SELLER) != (user.getSellerProfile() != null)) {
            throw new BadRequestException("SELLER role is granted through seller onboarding and removed by changing the KYC status");
        }
        if (userId.equals(actingAdminId) && !requested.contains(UserRole.ADMIN)) {
            throw new BadRequestException("You cannot remove your own ADMIN role");
        }

        Set<Role> roles = new HashSet<>();
        for (UserRole name : requested) {
            roles.add(roleRepo.findByName(name)
                    .orElseThrow(() -> new ServiceUnavailableException("This option is not available right now. Please contact support if it keeps happening.", new IllegalStateException("Role " + name + " not found"))));
        }
        user.setRole(roles);

        // Existing access tokens carry the old roles for up to 15 minutes; force a fresh login for the refresh path.
        refreshTokenService.revokeAllForUser(userId);
        log.info("Admin {} set roles of user {} to {}", actingAdminId, userId, requested);
        return userMapper.toResponse(user, false);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, Long actingAdminId) {
        if (userId.equals(actingAdminId)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        User user = findUser(userId);
        if (user.getSellerProfile() != null
                && user.getSellerProfile().getKycStatus() != SellerProfile.KycStatus.CLOSED) {
            user.getSellerProfile().setKycStatus(SellerProfile.KycStatus.CLOSED);
            user.getSellerProfile().setStatusReason("Account deleted");
        }
        refreshTokenService.revokeAllForUser(userId); // also flushes the seller-profile change above
        // One explicit UPDATE instead of userRepo.delete(entity): it cannot be "forgotten" by the persistence context.
        if (userRepo.softDelete(userId) != 1) {
            throw new NotFoundException("We couldn't find that user.");
        }
        log.info("Admin {} deleted user {}", actingAdminId, userId);
    }

    @Override
    @Transactional
    public void unlockUser(Long userId) {
        User user = findUser(userId);
        if (user.getSellerProfile() != null && user.getSellerProfile().getKycStatus().isProfileLocked()) {
            throw new BadRequestException("This seller is " + user.getSellerProfile().getKycStatus()
                    + "; change the seller status instead of unlocking the account");
        }
        user.setAccountNonLocked(true);
        loginAttemptService.reset(user.getEmail());
        log.info("User {} unlocked", userId);
    }

    // ---------------------------------------------------------------- helpers

    private User findUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("We couldn't find that user."));
    }

    private User saveOrConflict(User user) {
        try {
            return userRepo.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An account with this email or phone number already exists");
        }
    }
}
