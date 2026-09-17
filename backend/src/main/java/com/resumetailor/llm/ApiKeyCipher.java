package com.resumetailor.llm;

import com.resumetailor.config.SecurityProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for LLM API keys at rest.
 *
 * <p>GCM is authenticated, so a tampered or truncated ciphertext fails loudly instead
 * of decrypting to garbage. A fresh 12-byte IV is generated per encryption and stored
 * as a prefix of the ciphertext.
 *
 * <p>The key is validated in the constructor: a missing or wrong-sized ENCRYPTION_KEY
 * stops the application at startup rather than at the first save.
 */
@Component
public class ApiKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCipher(SecurityProperties properties) {
        this.secretKey = loadKey(properties.encryptionKey());
    }

    private static SecretKey loadKey(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("""
                    ENCRYPTION_KEY is not set. It encrypts your stored LLM API keys.
                    Generate one and put it in backend/.env:
                      openssl rand -base64 32""");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY must be base64. Generate one with: openssl rand -base64 32", ex);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY must decode to exactly 32 bytes (got " + decoded.length
                            + "). Generate one with: openssl rand -base64 32");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    /** Returns base64(iv || ciphertext), or null for a null input. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt API key", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to decrypt a stored API key. Did ENCRYPTION_KEY change? "
                            + "Re-enter the key for this profile in Settings.", ex);
        }
    }

    /** Last four characters, for display as "sk-...4f2a". Never returns the key itself. */
    public static String hint(String plaintext) {
        if (plaintext == null || plaintext.length() < 4) {
            return "";
        }
        return plaintext.substring(plaintext.length() - 4);
    }
}
