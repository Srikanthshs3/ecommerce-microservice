package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.entity.RefreshToken;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.repository.RefreshTokenRepo;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepo repo;
    @InjectMocks RefreshTokenService service;

    private final User user = TestData.user(1L, "a@b.com", UserRole.CUSTOMER);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshExpirationDays", 7);
    }

    private RefreshToken stored(String raw, boolean revoked, Instant expiry, String family) {
        return RefreshToken.builder()
                .id(10L)
                .tokenHash(RefreshTokenService.hash(raw))
                .user(user)
                .familyId(family)
                .expiryDate(expiry)
                .revoked(revoked)
                .build();
    }

    @Test
    @DisplayName("hash is deterministic 64-char lowercase hex SHA-256")
    void hash_deterministicHex() {
        String h = RefreshTokenService.hash("abc");

        assertThat(h).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(RefreshTokenService.hash("abc")).isEqualTo(h);
    }

    @Test
    @DisplayName("getRefreshTokenDuration uses the configured days")
    void getRefreshTokenDuration_days() {
        assertThat(service.getRefreshTokenDuration()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("issueNewSession stores only the hash, in a fresh family, and returns the raw token")
    void issueNewSession_storesHashedToken() {
        String raw = service.issueNewSession(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(raw).isNotBlank();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getFamilyId()).isNotBlank();
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiryDate()).isBetween(Instant.now().plus(Duration.ofDays(7)).minusSeconds(60),
                Instant.now().plus(Duration.ofDays(7)).plusSeconds(60));
    }

    @Test
    @DisplayName("each new session gets its own family")
    void issueNewSession_twice_differentFamilies() {
        service.issueNewSession(user);
        service.issueNewSession(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getFamilyId()).isNotEqualTo(captor.getAllValues().get(1).getFamilyId());
    }

    @Test
    @DisplayName("rotate with null or blank token is unauthorized")
    void rotate_blank_unauthorized() {
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.rotate("  ")).isInstanceOf(UnauthorizedException.class);
        verify(repo, never()).findByTokenHashForUpdate(anyString());
    }

    @Test
    @DisplayName("rotate with an unknown token is unauthorized")
    void rotate_unknown_unauthorized() {
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("x"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("x"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Your session has ended. Please sign in again.");
    }

    @Test
    @DisplayName("rotate revokes the old token and issues a child in the same family")
    void rotate_valid_revokesAndIssuesChild() {
        RefreshToken current = stored("old", false, Instant.now().plus(Duration.ofDays(1)), "fam-1");
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));

        RefreshTokenService.RotatedToken rotated = service.rotate("old");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotated.user()).isSameAs(user);
        assertThat(rotated.newRawToken()).isNotBlank().isNotEqualTo("old");
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isEqualTo("fam-1");
        assertThat(captor.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(rotated.newRawToken()));
    }

    @Test
    @DisplayName("rotate of a legacy token without family assigns a new family to the child")
    void rotate_noFamily_createsFamily() {
        RefreshToken current = stored("legacy", false, Instant.now().plus(Duration.ofDays(1)), null);
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("legacy"))).thenReturn(Optional.of(current));

        service.rotate("legacy");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isNotBlank();
    }

    @Test
    @DisplayName("reuse of a rotated token revokes the whole family and fails")
    void rotate_revokedToken_revokesFamily() {
        RefreshToken current = stored("old", true, Instant.now().plus(Duration.ofDays(1)), "fam-1");
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));
        when(repo.revokeFamily("fam-1")).thenReturn(2);

        assertThatThrownBy(() -> service.rotate("old"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("session has ended");
        verify(repo).revokeFamily("fam-1");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("reuse of a revoked legacy token without family just fails")
    void rotate_revokedNoFamily_fails() {
        RefreshToken current = stored("old", true, Instant.now().plus(Duration.ofDays(1)), null);
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(UnauthorizedException.class);
        verify(repo, never()).revokeFamily(anyString());
    }

    @Test
    @DisplayName("rotate of an expired token fails")
    void rotate_expired_fails() {
        RefreshToken current = stored("old", false, Instant.now().minusSeconds(10), "fam-1");
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate("old"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
        assertThat(current.isRevoked()).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("revoke with null or blank token is a no-op")
    void revoke_blank_noop() {
        service.revoke(null);
        service.revoke("");

        verify(repo, never()).findByTokenHashForUpdate(anyString());
    }

    @Test
    @DisplayName("revoke of an unknown token is ignored")
    void revoke_unknown_ignored() {
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("x"))).thenReturn(Optional.empty());

        service.revoke("x");

        verify(repo, never()).revokeFamily(anyString());
    }

    @Test
    @DisplayName("revoke of a token with a family revokes the family")
    void revoke_withFamily_revokesFamily() {
        RefreshToken t = stored("tok", false, Instant.now().plusSeconds(100), "fam-9");
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("tok"))).thenReturn(Optional.of(t));

        service.revoke("tok");

        verify(repo).revokeFamily("fam-9");
    }

    @Test
    @DisplayName("revoke of a legacy token without family flags only that token")
    void revoke_noFamily_flagsToken() {
        RefreshToken t = stored("tok", false, Instant.now().plusSeconds(100), null);
        when(repo.findByTokenHashForUpdate(RefreshTokenService.hash("tok"))).thenReturn(Optional.of(t));

        service.revoke("tok");

        assertThat(t.isRevoked()).isTrue();
        verify(repo, never()).revokeFamily(anyString());
    }

    @Test
    @DisplayName("revokeAllForUser delegates to the repository")
    void revokeAllForUser_delegates() {
        service.revokeAllForUser(1L);

        verify(repo).revokeAllByUserId(1L);
    }

    @Test
    @DisplayName("purgeExpired deletes tokens expired more than a day ago and returns the count")
    void purgeExpired_usesOneDayGrace() {
        when(repo.deleteExpiredBefore(any(Instant.class))).thenReturn(3);

        int deleted = service.purgeExpired();

        assertThat(deleted).isEqualTo(3);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repo).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(Instant.now().minus(Duration.ofDays(1)).minusSeconds(60),
                Instant.now().minus(Duration.ofDays(1)).plusSeconds(60));
    }
}
