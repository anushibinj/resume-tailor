package com.resumetailor.llm;

import com.resumetailor.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCipherTest {

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static ApiKeyCipher cipherWith(String key) {
        return new ApiKeyCipher(new SecurityProperties(key));
    }

    @Test
    void roundTripsAnApiKey() {
        ApiKeyCipher cipher = cipherWith(randomKey());

        String encrypted = cipher.encrypt("sk-test-abcdef123456");

        assertThat(encrypted).isNotEqualTo("sk-test-abcdef123456");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-test-abcdef123456");
    }

    @Test
    void producesDifferentCiphertextEachTimeSoKeysCannotBeCompared() {
        ApiKeyCipher cipher = cipherWith(randomKey());

        assertThat(cipher.encrypt("same-key")).isNotEqualTo(cipher.encrypt("same-key"));
    }

    @Test
    void refusesToDecryptTamperedCiphertext() {
        ApiKeyCipher cipher = cipherWith(randomKey());
        String encrypted = cipher.encrypt("sk-test-abcdef123456");

        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    void cannotDecryptWithADifferentKey() {
        String encrypted = cipherWith(randomKey()).encrypt("sk-test-abcdef123456");

        assertThatThrownBy(() -> cipherWith(randomKey()).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithoutAKey() {
        assertThatThrownBy(() -> cipherWith(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl rand -base64 32");
        assertThatThrownBy(() -> cipherWith("  "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAKeyOfTheWrongLength() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> cipherWith(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void refusesAKeyThatIsNotBase64() {
        assertThatThrownBy(() -> cipherWith("not base64 !!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void nullsPassThroughSoAProfileWithoutAKeyIsValid() {
        ApiKeyCipher cipher = cipherWith(randomKey());

        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.decrypt("")).isNull();
    }

    @Test
    void hintExposesOnlyTheLastFourCharacters() {
        assertThat(ApiKeyCipher.hint("sk-proj-abcdef4f2a")).isEqualTo("4f2a");
        assertThat(ApiKeyCipher.hint("abc")).isEmpty();
        assertThat(ApiKeyCipher.hint(null)).isEmpty();
    }
}
