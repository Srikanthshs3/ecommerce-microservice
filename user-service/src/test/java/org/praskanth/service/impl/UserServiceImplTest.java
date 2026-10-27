package org.praskanth.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.dto.CustomerRegisterRequest;
import org.praskanth.dto.RoleUpdateRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.dto.UserUpdateRequest;
import org.praskanth.entity.CustomerProfile;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.event.UserRegisteredDomainEvent;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.mapper.UserMapper;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.service.EmailVerificationService;
import org.praskanth.service.LoginAttemptService;
import org.praskanth.service.RefreshTokenService;
import org.praskanth.service.UserAccountFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepo userRepo;
    @Mock RoleRepo roleRepo;
    @Mock UserAccountFactory accountFactory;
    @Mock EmailVerificationService emailVerificationService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock UserMapper userMapper;
    @Mock ApplicationEventPublisher events;
    @InjectMocks UserServiceImpl service;

    private static CustomerRegisterRequest registerRequest() {
        return new CustomerRegisterRequest("Bob@Example.com", "vtoken", "Passw0rd#1", "  Bob  ", "9876543210");
    }

    private static User customer(Long id) {
        User u = TestData.user(id, "c@x.com", UserRole.CUSTOMER);
        u.setCustomerProfile(CustomerProfile.builder().userId(id).user(u).fullName("Test User").build());
        return u;
    }

    // ---------------------------------------------------------------- registerCustomer

    @Test
    @DisplayName("registerCustomer verifies, checks identity, consumes the token, saves and publishes the domain event")
    void registerCustomer_ok() {
        User built = TestData.user(null, "bob@example.com", UserRole.CUSTOMER);
        built.setName("Bob");
        User saved = TestData.user(55L, "bob@example.com", UserRole.CUSTOMER);
        saved.setName("Bob");
        UserResponse response = UserResponse.builder().id(55L).build();
        when(accountFactory.newUser("bob@example.com", "  Bob  ", "9876543210", "Passw0rd#1", UserRole.CUSTOMER))
                .thenReturn(built);
        when(userRepo.saveAndFlush(built)).thenReturn(saved);
        when(userMapper.toResponse(saved, false)).thenReturn(response);

        UserResponse result = service.registerCustomer(registerRequest());

        assertThat(result).isSameAs(response);
        var order = inOrder(emailVerificationService, accountFactory);
        order.verify(emailVerificationService).assertVerified("bob@example.com", "vtoken");
        order.verify(accountFactory).assertIdentityAvailable("bob@example.com", "9876543210");
        order.verify(emailVerificationService).consume("bob@example.com", "vtoken");
        assertThat(built.getCustomerProfile()).isNotNull();
        assertThat(built.getCustomerProfile().getFullName()).isEqualTo("Bob");
        ArgumentCaptor<UserRegisteredDomainEvent> event = ArgumentCaptor.forClass(UserRegisteredDomainEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(55L);
        assertThat(event.getValue().email()).isEqualTo("bob@example.com");
        assertThat(event.getValue().role()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("registerCustomer with an invalid verification token stops before anything is created")
    void registerCustomer_invalidToken_propagates() {
        org.mockito.Mockito.doThrow(new org.praskanth.exception.UnauthorizedException("bad"))
                .when(emailVerificationService).assertVerified("bob@example.com", "vtoken");

        assertThatThrownBy(() -> service.registerCustomer(registerRequest()))
                .isInstanceOf(org.praskanth.exception.UnauthorizedException.class);
        verify(userRepo, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("registerCustomer maps a database uniqueness violation to a conflict and publishes nothing")
    void registerCustomer_dataIntegrity_conflict() {
        User built = TestData.user(null, "bob@example.com", UserRole.CUSTOMER);
        when(accountFactory.newUser("bob@example.com", "  Bob  ", "9876543210", "Passw0rd#1", UserRole.CUSTOMER))
                .thenReturn(built);
        when(userRepo.saveAndFlush(built)).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.registerCustomer(registerRequest()))
                .isInstanceOf(ConflictException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    // ---------------------------------------------------------------- self service

    @Test
    @DisplayName("getCurrentUser returns the mapped user including addresses")
    void getCurrentUser_ok() {
        User user = customer(1L);
        UserResponse response = UserResponse.builder().id(1L).build();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user, true)).thenReturn(response);

        assertThat(service.getCurrentUser(1L)).isSameAs(response);
    }

    @Test
    @DisplayName("getCurrentUser for an unknown id is not found")
    void getCurrentUser_notFound() {
        when(userRepo.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("We couldn't find that user.");
    }

    @Test
    @DisplayName("updateProfile trims name and phone and syncs the customer profile name")
    void updateProfile_ok() {
        User user = customer(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(userMapper.toResponse(user, true)).thenReturn(UserResponse.builder().build());

        service.updateProfile(1L, new UserUpdateRequest("  New Name ", " 9876543210 "));

        assertThat(user.getName()).isEqualTo("New Name");
        assertThat(user.getPhoneNumber()).isEqualTo("9876543210");
        assertThat(user.getCustomerProfile().getFullName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("updateProfile with an unchanged phone does not hit the uniqueness check")
    void updateProfile_samePhone_noCheck() {
        User user = customer(1L);
        user.setPhoneNumber("9876543210");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user, true)).thenReturn(UserResponse.builder().build());

        service.updateProfile(1L, new UserUpdateRequest("Name", "9876543210"));

        verify(userRepo, never()).existsByPhoneNumber(any());
    }

    @Test
    @DisplayName("updateProfile with a phone used by someone else is a conflict")
    void updateProfile_phoneTaken_conflict() {
        User user = customer(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.existsByPhoneNumber("9111111111")).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(1L, new UserUpdateRequest("Name", "9111111111")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("updateProfile with a blank phone clears it; users without a customer profile are fine")
    void updateProfile_blankPhone_clears() {
        User user = TestData.user(2L, "s@x.com", UserRole.SELLER);
        user.setPhoneNumber("9876543210");
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user, true)).thenReturn(UserResponse.builder().build());

        service.updateProfile(2L, new UserUpdateRequest("Seller", "  "));

        assertThat(user.getPhoneNumber()).isNull();
        assertThat(user.getName()).isEqualTo("Seller");
    }

    @Test
    @DisplayName("updateProfile with a null phone clears it")
    void updateProfile_nullPhone_clears() {
        User user = customer(1L);
        user.setPhoneNumber("9876543210");
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user, true)).thenReturn(UserResponse.builder().build());

        service.updateProfile(1L, new UserUpdateRequest("Name", null));

        assertThat(user.getPhoneNumber()).isNull();
    }

    // ---------------------------------------------------------------- admin

    @Test
    @DisplayName("getUserById returns the mapped user")
    void getUserById_ok() {
        User user = customer(3L);
        UserResponse response = UserResponse.builder().id(3L).build();
        when(userRepo.findById(3L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user, true)).thenReturn(response);

        assertThat(service.getUserById(3L)).isSameAs(response);
    }

    @Test
    @DisplayName("getAllUsers maps every row of the page without addresses")
    void getAllUsers_mapsPage() {
        User u1 = customer(1L);
        User u2 = customer(2L);
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepo.findAll(pageable)).thenReturn(new PageImpl<>(List.of(u1, u2), pageable, 2));
        when(userMapper.toResponse(u1, false)).thenReturn(UserResponse.builder().id(1L).build());
        when(userMapper.toResponse(u2, false)).thenReturn(UserResponse.builder().id(2L).build());

        Page<UserResponse> page = service.getAllUsers(pageable);

        assertThat(page.getContent()).extracting(UserResponse::getId).containsExactly(1L, 2L);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateUserRoles replaces the roles and revokes all sessions")
    void updateUserRoles_ok() {
        User user = customer(5L);
        when(userRepo.findById(5L)).thenReturn(Optional.of(user));
        when(roleRepo.findByName(UserRole.CUSTOMER)).thenReturn(Optional.of(TestData.role(UserRole.CUSTOMER)));
        when(roleRepo.findByName(UserRole.SUPPORT_AGENT)).thenReturn(Optional.of(TestData.role(UserRole.SUPPORT_AGENT)));
        when(userMapper.toResponse(user, false)).thenReturn(UserResponse.builder().build());

        service.updateUserRoles(5L, new RoleUpdateRequest(Set.of(UserRole.CUSTOMER, UserRole.SUPPORT_AGENT)), 1L);

        assertThat(user.getRole()).extracting(r -> r.getName())
                .containsExactlyInAnyOrder(UserRole.CUSTOMER, UserRole.SUPPORT_AGENT);
        verify(refreshTokenService).revokeAllForUser(5L);
    }

    @Test
    @DisplayName("updateUserRoles cannot strip the CUSTOMER role of a customer")
    void updateUserRoles_removeCustomer_badRequest() {
        User user = customer(5L);
        when(userRepo.findById(5L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUserRoles(5L, new RoleUpdateRequest(Set.of(UserRole.SUPPORT_AGENT)), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CUSTOMER");
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    @DisplayName("updateUserRoles cannot grant CUSTOMER to a user without a customer profile")
    void updateUserRoles_grantCustomerWithoutProfile_badRequest() {
        User user = TestData.user(6L, "x@x.com", UserRole.SUPPORT_AGENT);
        when(userRepo.findById(6L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUserRoles(6L, new RoleUpdateRequest(Set.of(UserRole.CUSTOMER)), 1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("updateUserRoles cannot grant SELLER by hand")
    void updateUserRoles_grantSeller_badRequest() {
        User user = customer(5L);
        when(userRepo.findById(5L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUserRoles(5L,
                new RoleUpdateRequest(Set.of(UserRole.CUSTOMER, UserRole.SELLER)), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SELLER");
    }

    @Test
    @DisplayName("updateUserRoles cannot strip SELLER from a seller")
    void updateUserRoles_removeSeller_badRequest() {
        User user = customer(5L);
        user.setSellerProfile(SellerProfile.builder().build());
        when(userRepo.findById(5L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateUserRoles(5L, new RoleUpdateRequest(Set.of(UserRole.CUSTOMER)), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SELLER");
    }

    @Test
    @DisplayName("an admin cannot remove their own ADMIN role")
    void updateUserRoles_selfDemotion_badRequest() {
        User admin = customer(1L);
        when(userRepo.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateUserRoles(1L, new RoleUpdateRequest(Set.of(UserRole.CUSTOMER)), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own ADMIN");
    }

    @Test
    @DisplayName("updateUserRoles fails when a requested role is not in the database")
    void updateUserRoles_roleMissing_badRequest() {
        User user = customer(5L);
        when(userRepo.findById(5L)).thenReturn(Optional.of(user));
        when(roleRepo.findByName(UserRole.CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUserRoles(5L, new RoleUpdateRequest(Set.of(UserRole.CUSTOMER)), 1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("not available right now");
    }

    @Test
    @DisplayName("an admin cannot delete themselves")
    void deleteUser_self_badRequest() {
        assertThatThrownBy(() -> service.deleteUser(1L, 1L)).isInstanceOf(BadRequestException.class);
        verify(userRepo, never()).delete(any());
    }

    @Test
    @DisplayName("deleteUser of an unknown id is not found")
    void deleteUser_notFound() {
        when(userRepo.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(2L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("deleteUser closes an active seller profile, revokes sessions and soft-deletes")
    void deleteUser_seller_closesProfile() {
        User user = customer(2L);
        SellerProfile profile = SellerProfile.builder().kycStatus(SellerProfile.KycStatus.ACTIVE).build();
        user.setSellerProfile(profile);
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));
        when(userRepo.softDelete(2L)).thenReturn(1);

        service.deleteUser(2L, 1L);

        assertThat(profile.getKycStatus()).isEqualTo(SellerProfile.KycStatus.CLOSED);
        assertThat(profile.getStatusReason()).isEqualTo("Account deleted");
        verify(refreshTokenService).revokeAllForUser(2L);
        verify(userRepo).softDelete(2L);
    }

    @Test
    @DisplayName("deleteUser keeps the reason of an already closed seller profile")
    void deleteUser_closedSeller_unchanged() {
        User user = customer(2L);
        SellerProfile profile = SellerProfile.builder().kycStatus(SellerProfile.KycStatus.CLOSED).statusReason("old").build();
        user.setSellerProfile(profile);
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));
        when(userRepo.softDelete(2L)).thenReturn(1);

        service.deleteUser(2L, 1L);

        assertThat(profile.getStatusReason()).isEqualTo("old");
        verify(userRepo).softDelete(2L);
    }

    @Test
    @DisplayName("deleteUser works for a plain customer")
    void deleteUser_customer_ok() {
        User user = customer(2L);
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));
        when(userRepo.softDelete(2L)).thenReturn(1);

        service.deleteUser(2L, 1L);

        verify(userRepo).softDelete(2L);
        verify(userRepo, never()).delete(any(User.class)); // we no longer rely on entity deletion
    }

    @Test
    @DisplayName("deleteUser reports not found when the soft delete changed no row")
    void deleteUser_noRowChanged_notFound() {
        User user = customer(2L);
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));
        when(userRepo.softDelete(2L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteUser(2L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("unlockUser re-enables the account and clears the login throttle")
    void unlockUser_ok() {
        User user = customer(2L);
        user.setAccountNonLocked(false);
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));

        service.unlockUser(2L);

        assertThat(user.isAccountNonLocked()).isTrue();
        verify(loginAttemptService).reset("c@x.com");
    }

    @Test
    @DisplayName("unlockUser of an active seller is allowed")
    void unlockUser_activeSeller_ok() {
        User user = customer(2L);
        user.setAccountNonLocked(false);
        user.setSellerProfile(SellerProfile.builder().kycStatus(SellerProfile.KycStatus.ACTIVE).build());
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));

        service.unlockUser(2L);

        assertThat(user.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("unlockUser of a suspended seller is refused")
    void unlockUser_suspendedSeller_badRequest() {
        User user = customer(2L);
        user.setAccountNonLocked(false);
        user.setSellerProfile(SellerProfile.builder().kycStatus(SellerProfile.KycStatus.SUSPENDED).build());
        when(userRepo.findById(2L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.unlockUser(2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SUSPENDED");
        assertThat(user.isAccountNonLocked()).isFalse();
        verify(loginAttemptService, never()).reset(any());
    }
}
