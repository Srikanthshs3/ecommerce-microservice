package org.praskanth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.UserRepo;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepo userRepo;
    @InjectMocks CustomUserDetailsService service;

    @Test
    @DisplayName("loadUserByUsername wraps the user into AuthUser with role authorities")
    void loadUserByUsername_found() {
        User user = TestData.user(1L, "a@b.com", UserRole.SELLER);
        when(userRepo.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("a@b.com");

        assertThat(details.getUsername()).isEqualTo("a@b.com");
        assertThat(details.getPassword()).isEqualTo("encoded-pw");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("SELLER");
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("loadUserByUsername for unknown email throws NotFoundException")
    void loadUserByUsername_missing() {
        when(userRepo.findByEmail("x@y.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("x@y.com")).isInstanceOf(NotFoundException.class);
    }
}
