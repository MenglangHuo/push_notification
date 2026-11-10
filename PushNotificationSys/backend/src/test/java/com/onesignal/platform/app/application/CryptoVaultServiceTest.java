package com.onesignal.platform.app.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoVaultServiceTest {

    private CryptoVaultService cryptoVaultService;

    @BeforeEach
    void setUp() {
        cryptoVaultService = new CryptoVaultService();
        // 32-byte master key
        ReflectionTestUtils.setField(cryptoVaultService, "masterKeyConfig", "k4P8q3Z1y7W2v9T0x5R8u1O3m6L9j2H5g7E0b3D6a8=");
        cryptoVaultService.init();
    }

    @Test
    @DisplayName("Encrypt and decrypt roundtrip matches original plaintext")
    void encrypt_decrypt_success() {
        String secretJson = "{\"project_id\":\"test-firebase-123\",\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nMIIEvgIBADANBgk...\"}";

        String encrypted = cryptoVaultService.encrypt(secretJson);
        assertThat(encrypted).isNotNull().isNotEqualTo(secretJson);

        String decrypted = cryptoVaultService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(secretJson);
    }

    @Test
    @DisplayName("Tampered ciphertext throws decryption failure exception")
    void decrypt_tamperedCiphertext_throwsException() {
        String encrypted = cryptoVaultService.encrypt("secret_api_token_123");

        // Tamper with ciphertext by altering characters
        char[] chars = encrypted.toCharArray();
        chars[chars.length - 2] = (chars[chars.length - 2] == 'A') ? 'B' : 'A';
        String tampered = new String(chars);

        assertThatThrownBy(() -> cryptoVaultService.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Decryption failure");
    }

    @Test
    @DisplayName("Null input returns null")
    void nullInput_returnsNull() {
        assertThat(cryptoVaultService.encrypt(null)).isNull();
        assertThat(cryptoVaultService.decrypt(null)).isNull();
    }
}
