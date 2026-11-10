package com.onesignal.platform.dispatch.service;

import com.onesignal.platform.app.application.VapidKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebPushCryptoServiceTest {

    private WebPushCryptoService cryptoService;
    private VapidKeyGenerator vapidKeyGenerator;

    @BeforeEach
    void setUp() {
        cryptoService = new WebPushCryptoService();
        vapidKeyGenerator = new VapidKeyGenerator();
    }

    @Test
    void testCreateVapidToken_ProducesValidJwt() {
        VapidKeyGenerator.VapidKeyPair keyPair = vapidKeyGenerator.generate();
        String endpoint = "https://fcm.googleapis.com/fcm/send/sample_endpoint_123";

        String jwt = cryptoService.createVapidToken(endpoint, keyPair.getPrivateKey(), "mailto:test@onesignal.local");
        assertNotNull(jwt);

        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT must consist of header, payload, and signature");
    }

    @Test
    void testEncryptPayload_ProducesValidAes128GcmRecord() {
        VapidKeyGenerator.VapidKeyPair clientKeyPair = vapidKeyGenerator.generate();
        // 16-byte random auth secret base64url encoded
        String clientAuth = "AAAAAAAAAAAAAAAAAAAAAA";

        byte[] plaintext = "{\"title\":\"Hello\",\"body\":\"World\"}".getBytes();
        byte[] encrypted = cryptoService.encryptPayload(plaintext, clientKeyPair.getPublicKey(), clientAuth);

        assertNotNull(encrypted);
        assertTrue(encrypted.length > plaintext.length + 86,
                "Encrypted aes128gcm payload must contain salt (16B), rs (4B), idlen (1B), keyid (65B), tag (16B), and ciphertext");
    }
}
