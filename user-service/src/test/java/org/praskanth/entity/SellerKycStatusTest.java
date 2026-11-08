package org.praskanth.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.praskanth.entity.SellerProfile.KycStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SellerKycStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, ACTIVE", "PENDING, REJECTED", "PENDING, CLOSED",
            "REJECTED, PENDING", "REJECTED, CLOSED",
            "ACTIVE, PENDING", "ACTIVE, SUSPENDED", "ACTIVE, DEACTIVATED", "ACTIVE, BANNED", "ACTIVE, CLOSED",
            "SUSPENDED, ACTIVE", "SUSPENDED, BANNED", "SUSPENDED, CLOSED",
            "DEACTIVATED, ACTIVE", "DEACTIVATED, CLOSED",
            "BANNED, CLOSED"
    })
    void canTransitionTo_allowedTransitions(KycStatus from, KycStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, SUSPENDED", "PENDING, BANNED", "PENDING, DEACTIVATED", "PENDING, PENDING",
            "REJECTED, ACTIVE", "REJECTED, BANNED",
            "ACTIVE, REJECTED", "ACTIVE, ACTIVE",
            "SUSPENDED, PENDING", "SUSPENDED, DEACTIVATED",
            "DEACTIVATED, BANNED", "DEACTIVATED, SUSPENDED",
            "BANNED, ACTIVE", "BANNED, PENDING",
            "CLOSED, ACTIVE", "CLOSED, PENDING", "CLOSED, CLOSED"
    })
    void canTransitionTo_forbiddenTransitions(KycStatus from, KycStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(KycStatus.class)
    void closed_isTerminal(KycStatus target) {
        assertThat(KycStatus.CLOSED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = KycStatus.class, names = {"SUSPENDED", "BANNED", "CLOSED"})
    void isProfileLocked_trueForSuspendedBannedClosed(KycStatus status) {
        assertThat(status.isProfileLocked()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = KycStatus.class, names = {"PENDING", "ACTIVE", "REJECTED", "DEACTIVATED"})
    void isProfileLocked_falseForOthers(KycStatus status) {
        assertThat(status.isProfileLocked()).isFalse();
    }

    @Test
    void sellerProfileBuilder_defaultsToPending() {
        assertThat(SellerProfile.builder().build().getKycStatus()).isEqualTo(KycStatus.PENDING);
    }
}
