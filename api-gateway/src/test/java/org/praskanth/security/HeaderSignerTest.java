package org.praskanth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The gateway signs the identity headers so a service can tell "this really came from the gateway". */
class HeaderSignerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 chars
    private final HeaderSigner signer = new HeaderSigner(SECRET);
    private final long now = 1_800_000_000L;

    @Test
    void secretShorterThan32CharactersIsRejected() {
        assertThatThrownBy(() -> new HeaderSigner("too-short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HeaderSigner(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signatureIsDeterministic() {
        assertThat(signer.sign("7", "a@b.com", "CUSTOMER", now)).isEqualTo(signer.sign("7", "a@b.com", "CUSTOMER", now));
    }

    @Test
    void aValidSignatureVerifies() {
        String sig = signer.sign("7", "a@b.com", "CUSTOMER", now);
        assertThat(signer.verify("7", "a@b.com", "CUSTOMER", String.valueOf(now), sig, now + 5, 30)).isTrue();
    }

    @Test
    void tamperingWithAnyFieldBreaksTheSignature() {
        String sig = signer.sign("7", "a@b.com", "CUSTOMER", now);
        String ts = String.valueOf(now);
        assertThat(signer.verify("8", "a@b.com", "CUSTOMER", ts, sig, now, 30)).isFalse();
        assertThat(signer.verify("7", "evil@b.com", "CUSTOMER", ts, sig, now, 30)).isFalse();
        assertThat(signer.verify("7", "a@b.com", "ADMIN", ts, sig, now, 30)).isFalse();
    }

    @Test
    void aDifferentSecretCannotForgeASignature() {
        HeaderSigner other = new HeaderSigner("ffffffffffffffffffffffffffffffff");
        String forged = other.sign("7", "a@b.com", "ADMIN", now);
        assertThat(signer.verify("7", "a@b.com", "ADMIN", String.valueOf(now), forged, now, 30)).isFalse();
    }

    @Test
    void oldOrFutureTimestampsAreRejectedToStopReplay() {
        String sig = signer.sign("7", "a@b.com", "CUSTOMER", now);
        assertThat(signer.verify("7", "a@b.com", "CUSTOMER", String.valueOf(now), sig, now + 31, 30)).isFalse();
        assertThat(signer.verify("7", "a@b.com", "CUSTOMER", String.valueOf(now), sig, now - 31, 30)).isFalse();
        assertThat(signer.verify("7", "a@b.com", "CUSTOMER", String.valueOf(now), sig, now + 30, 30)).isTrue();
    }

    @Test
    void missingOrMalformedInputIsRejectedNotThrown() {
        String sig = signer.sign("7", "a@b.com", "CUSTOMER", now);
        assertThat(signer.verify("7", "a@b.com", "CUSTOMER", null, sig, now, 30)).isFalse();
        assertThat(signer.verify("7", "a@b.com", "CUSTOMER", String.valueOf(now), null, now, 30)).isFalse();
        assertThat(signer.verify("7", "a@b.com", "CUSTOMER", "not-a-number", sig, now, 30)).isFalse();
    }

    @Test
    void lineBreaksInValuesAreRefusedBecauseTheyCouldFakeAnotherFieldBoundary() {
        assertThatThrownBy(() -> signer.sign("7", "a@b.com\nADMIN", "CUSTOMER", now))
                .isInstanceOf(IllegalArgumentException.class);
        String sig = signer.sign("7", "a@b.com", "CUSTOMER", now);
        assertThat(signer.verify("7", "a@b.com\r", "CUSTOMER", String.valueOf(now), sig, now, 30)).isFalse();
    }
}
