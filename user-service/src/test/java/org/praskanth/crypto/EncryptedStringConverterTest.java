package org.praskanth.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_B;

    static {
        byte[] b = new byte[32];
        b[0] = 1;
        KEY_B = Base64.getEncoder().encodeToString(b);
    }

    private final EncryptedStringConverter converter = new EncryptedStringConverter(KEY_A);

    @ParameterizedTest
    @ValueSource(strings = {"123456789012", "", "unicode-é€", "a long value with spaces and symbols !@#$%^&*()"})
    void roundTrip_returnsOriginal(String plain) {
        String stored = converter.convertToDatabaseColumn(plain);

        assertThat(stored).startsWith("enc:v1:");
        assertThat(stored).doesNotContain(plain.isEmpty() ? "zzzz" : plain);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(plain);
    }

    @Test
    void encrypt_isRandomised() {
        String a = converter.convertToDatabaseColumn("same");
        String b = converter.convertToDatabaseColumn("same");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void nullValues_passThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void legacyPlainText_isReadAsIs() {
        assertThat(converter.convertToEntityAttribute("123456789")).isEqualTo("123456789");
    }

    @Test
    void decrypt_withWrongKey_fails() {
        String stored = converter.convertToDatabaseColumn("secret");
        EncryptedStringConverter other = new EncryptedStringConverter(KEY_B);

        assertThatThrownBy(() -> other.convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    @Test
    void decrypt_tamperedCiphertext_fails() {
        String stored = converter.convertToDatabaseColumn("secret");
        byte[] raw = Base64.getDecoder().decode(stored.substring("enc:v1:".length()));
        raw[raw.length - 1] ^= 0x01;
        String tampered = "enc:v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decodeKey_toleratesQuotesWhitespaceAndComment() {
        assertThat(EncryptedStringConverter.decodeKey("  \"" + KEY_A + "\"  ")).hasSize(32);
        assertThat(EncryptedStringConverter.decodeKey("'" + KEY_A + "'")).hasSize(32);
        assertThat(EncryptedStringConverter.decodeKey(KEY_A + "  # my comment")).hasSize(32);
        assertThat(EncryptedStringConverter.decodeKey(KEY_A + "\n")).hasSize(32);
    }

    @Test
    void decodeKey_blankOrNull_rejected() {
        assertThatThrownBy(() -> EncryptedStringConverter.decodeKey(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> EncryptedStringConverter.decodeKey("   "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("empty");
    }

    @Test
    void decodeKey_notBase64_rejectedWithoutLeakingKey() {
        assertThatThrownBy(() -> EncryptedStringConverter.decodeKey("not*base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid base64")
                .hasMessageNotContaining("not*base64!");
    }

    @Test
    void decodeKey_wrongLength_rejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> EncryptedStringConverter.decodeKey(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageContaining("16");
    }
}
