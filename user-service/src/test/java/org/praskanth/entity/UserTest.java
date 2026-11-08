package org.praskanth.entity;

import org.junit.jupiter.api.Test;
import org.praskanth.TestData;
import org.praskanth.enums.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void hasRole_trueOnlyForAssignedRoles() {
        User u = TestData.user(1L, "a@b.com", UserRole.CUSTOMER, UserRole.SELLER);

        assertThat(u.hasRole(UserRole.CUSTOMER)).isTrue();
        assertThat(u.hasRole(UserRole.SELLER)).isTrue();
        assertThat(u.hasRole(UserRole.ADMIN)).isFalse();
    }

    @Test
    void builderDefaults_unlockedWithNoRoles() {
        User u = User.builder().build();

        assertThat(u.isAccountNonLocked()).isTrue();
        assertThat(u.getRole()).isEmpty();
        assertThat(u.hasRole(UserRole.ADMIN)).isFalse();
    }

    @Test
    void lifecycleCallbacks_setTimestamps() {
        User u = new User();
        u.onCreate();
        u.onUpdate();

        assertThat(u.getCreatedAt()).isNotNull();
        assertThat(u.getUpdatedAt()).isNotNull();
    }

    @Test
    void refreshTokenOnCreate_setsCreatedAt() {
        RefreshToken t = new RefreshToken();
        t.onCreate();

        assertThat(t.getCreatedAt()).isNotNull();
    }
}
