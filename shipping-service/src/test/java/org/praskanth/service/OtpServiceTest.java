package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.praskanth.entity.Shipment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The delivery OTP is derived (HMAC), never stored. These tests pin down that it is stable, private and per-attempt. */
class OtpServiceTest {

    private OtpService otp;

    @BeforeEach
    void setUp() {
        otp = new OtpService();
        ReflectionTestUtils.setField(otp, "otpSecret", "shipping-secret-for-tests-0123456789");
        ReflectionTestUtils.setField(otp, "fallbackSecret", "");
    }

    private static Shipment shipment(long orderId, int version) {
        return Shipment.builder().orderId(orderId).otpVersion(version).build();
    }

    @Test
    void codeIsSixDigits() {
        assertThat(otp.codeFor(shipment(42, 0))).matches("\\d{6}");
    }

    @Test
    void sameShipmentAlwaysGivesTheSameCode() {
        assertThat(otp.codeFor(shipment(42, 1))).isEqualTo(otp.codeFor(shipment(42, 1)));
    }

    @Test
    void newPickupAttemptInvalidatesThePreviousCode() {
        // different version -> different HMAC input; (a 1-in-a-million collision would be a flaky false alarm)
        assertThat(otp.codeFor(shipment(42, 1))).isNotEqualTo(otp.codeFor(shipment(42, 2)));
    }

    @Test
    void differentOrdersGetDifferentCodes() {
        assertThat(otp.codeFor(shipment(1, 0))).isNotEqualTo(otp.codeFor(shipment(2, 0)));
    }

    @Test
    void differentSecretGivesDifferentCode() {
        String a = otp.codeFor(shipment(42, 0));
        ReflectionTestUtils.setField(otp, "otpSecret", "another-secret-for-tests-9876543210");
        assertThat(otp.codeFor(shipment(42, 0))).isNotEqualTo(a);
    }

    @Test
    void matchesAcceptsTheRightCodeEvenWithSpaces() {
        Shipment s = shipment(7, 3);
        assertThat(otp.matches(s, otp.codeFor(s))).isTrue();
        assertThat(otp.matches(s, "  " + otp.codeFor(s) + " ")).isTrue();
    }

    @Test
    void matchesRejectsWrongNullAndEmptyCodes() {
        Shipment s = shipment(7, 3);
        String right = otp.codeFor(s);
        String wrong = right.equals("000000") ? "000001" : "000000";
        assertThat(otp.matches(s, wrong)).isFalse();
        assertThat(otp.matches(s, null)).isFalse();
        assertThat(otp.matches(s, "")).isFalse();
    }

    @Test
    void fallsBackToJwtSecretWhenNoOtpSecretIsConfigured() {
        ReflectionTestUtils.setField(otp, "otpSecret", "");
        ReflectionTestUtils.setField(otp, "fallbackSecret", "jwt-secret-for-tests-0123456789abcdef");
        assertThat(otp.codeFor(shipment(1, 0))).matches("\\d{6}");
    }

    @Test
    void failsLoudlyWhenNoSecretAtAll() {
        ReflectionTestUtils.setField(otp, "otpSecret", "");
        ReflectionTestUtils.setField(otp, "fallbackSecret", "");
        assertThatThrownBy(() -> otp.codeFor(shipment(1, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHIPPING_OTP_SECRET");
    }
}
