package org.praskanth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountFactoryTest {

    @Mock RoleRepo roleRepo;
    @Mock UserRepo userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserAccountFactory factory;

    @Test
    @DisplayName("assertIdentityAvailable rejects a duplicate email (normalised)")
    void assertIdentityAvailable_duplicateEmail_conflict() {
        when(userRepo.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> factory.assertIdentityAvailable(" A@B.com ", "9999999999"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");
        verify(userRepo, never()).existsByPhoneNumber(anyString());
    }

    @Test
    @DisplayName("assertIdentityAvailable rejects a duplicate phone number")
    void assertIdentityAvailable_duplicatePhone_conflict() {
        when(userRepo.existsByEmail("a@b.com")).thenReturn(false);
        when(userRepo.existsByPhoneNumber("9999999999")).thenReturn(true);

        assertThatThrownBy(() -> factory.assertIdentityAvailable("a@b.com", " 9999999999 "))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("phone");
    }

    @Test
    @DisplayName("assertIdentityAvailable skips the phone check for blank/null phone")
    void assertIdentityAvailable_blankPhone_skipsPhoneCheck() {
        when(userRepo.existsByEmail("a@b.com")).thenReturn(false);

        assertThatCode(() -> factory.assertIdentityAvailable("a@b.com", "  ")).doesNotThrowAnyException();
        assertThatCode(() -> factory.assertIdentityAvailable("a@b.com", null)).doesNotThrowAnyException();
        verify(userRepo, never()).existsByPhoneNumber(anyString());
    }

    @Test
    @DisplayName("newUser builds a verified user with normalised fields and encoded password")
    void newUser_ok() {
        when(roleRepo.findByName(UserRole.CUSTOMER)).thenReturn(Optional.of(TestData.role(UserRole.CUSTOMER)));
        when(passwordEncoder.encode("Raw#1234")).thenReturn("encoded");

        User user = factory.newUser(" Bob@Example.com ", "  Bob  ", " 9876543210 ", "Raw#1234", UserRole.CUSTOMER);

        assertThat(user.getEmail()).isEqualTo("bob@example.com");
        assertThat(user.getName()).isEqualTo("Bob");
        assertThat(user.getPhoneNumber()).isEqualTo("9876543210");
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.hasRole(UserRole.CUSTOMER)).isTrue();
    }

    @Test
    @DisplayName("newUser keeps a null name and turns a blank phone into null")
    void newUser_nullName_blankPhone() {
        when(roleRepo.findByName(UserRole.SELLER)).thenReturn(Optional.of(TestData.role(UserRole.SELLER)));
        when(passwordEncoder.encode("pw")).thenReturn("encoded");

        User user = factory.newUser("a@b.com", null, "   ", "pw", UserRole.SELLER);

        assertThat(user.getName()).isNull();
        assertThat(user.getPhoneNumber()).isNull();
    }

    @Test
    @DisplayName("newUser fails when the role row is missing")
    void newUser_roleMissing_badRequest() {
        when(roleRepo.findByName(UserRole.SELLER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.newUser("a@b.com", "A", null, "pw", UserRole.SELLER))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("not available right now");
        verify(passwordEncoder, never()).encode(anyString());
    }
}
