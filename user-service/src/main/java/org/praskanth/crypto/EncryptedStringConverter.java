package org.praskanth.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Transparent AES-256-GCM column encryption for sensitive fields (bank account numbers).
 * Values are stored as "enc:v1:" + base64(iv || ciphertext). Rows written before this converter
 * existed (plain text, no prefix) are still readable and get encrypted on their next update.
 * The key comes from app.crypto.key (base64, 32 bytes) - inject it from a secret manager, never from git.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedStringConverter(@Value("${app.crypto.key}") String base64Key) {
        this.key = new SecretKeySpec(decodeKey(base64Key), "AES");
    }

    /**
     * Decodes the configured key. Tolerates what commonly sneaks in when the value travels through a .env file,
     * docker-compose or a CI secret: surrounding quotes, leading/trailing whitespace, a stray line break, or a
     * trailing " # comment". Anything else gives an error that says what is wrong WITHOUT printing the key.
     */
    static byte[] decodeKey(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("app.crypto.key (APP_CRYPTO_KEY) is empty. Set it to a base64 string of exactly 32 bytes "
                    + "(generate one with: openssl rand -base64 32).");
        }
        String value = configured.strip();
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).strip();
        }
        value = value.split("\\s+")[0]; // drop a trailing "  # comment" or anything after the first whitespace
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.crypto.key (APP_CRYPTO_KEY) is not valid base64 (" + e.getMessage() + "; length "
                    + configured.length() + "). Check for spaces, quotes or a placeholder text in your .env / docker-compose value.", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("app.crypto.key (APP_CRYPTO_KEY) must decode to exactly 32 bytes but decodes to "
                    + bytes.length + " (generate one with: openssl rand -base64 32).");
        }
        return bytes;
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // null or legacy plain-text row
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to decrypt value (wrong app.crypto.key?)", e);
        }
    }
}
