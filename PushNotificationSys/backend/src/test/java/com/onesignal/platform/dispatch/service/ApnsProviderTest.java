package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.app.application.CryptoVaultService;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.dispatch.domain.BatchDispatchResult;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import com.onesignal.platform.dispatch.domain.PushRecipient;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApnsProviderTest {

    @Mock
    private CryptoVaultService cryptoVaultService;

    private ApnsTokenService apnsTokenService;
    private ApnsPayloadBuilder apnsPayloadBuilder;
    private ObjectMapper objectMapper;
    private ApnsProvider apnsProvider;

    private App app;
    private ProviderCredential credential;
    private String sampleP8Pem;

    @BeforeEach
    void setUp() throws Exception {
        apnsTokenService = new ApnsTokenService();
        objectMapper = new ObjectMapper();
        apnsPayloadBuilder = new ApnsPayloadBuilder(objectMapper);
        apnsProvider = new ApnsProvider(cryptoVaultService, apnsTokenService, apnsPayloadBuilder, objectMapper);

        // Generate sample EC key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        StringWriter sw = new StringWriter();
        try (PemWriter pw = new PemWriter(sw)) {
            pw.writeObject(new PemObject("PRIVATE KEY", kp.getPrivate().getEncoded()));
        }
        sampleP8Pem = sw.toString();

        app = App.builder()
                .name("Acme iOS App")
                .build();
        app.setId(UUID.randomUUID());

        credential = ProviderCredential.builder()
                .app(app)
                .providerType(ProviderType.APNS)
                .credentialDataEncrypted("vault:encrypted:p8")
                .metadataMasked(Map.of(
                        "key_id", "KEY1234567",
                        "team_id", "TEAM987654",
                        "bundle_id", "com.acme.shop"
                ))
                .build();
    }

    @Test
    void testGetProviderType() {
        assertEquals(ProviderType.APNS, apnsProvider.getProviderType());
    }

    @Test
    void testTestConnection_Success() {
        when(cryptoVaultService.decrypt("vault:encrypted:p8")).thenReturn(sampleP8Pem);
        assertTrue(apnsProvider.testConnection(credential));
    }

    @Test
    void testTestConnection_NullOrEmpty() {
        assertFalse(apnsProvider.testConnection(null));
        ProviderCredential emptyCred = ProviderCredential.builder().build();
        assertFalse(apnsProvider.testConnection(emptyCred));
    }

    @Test
    void testDispatchBatch_EmptyRecipients() {
        BatchDispatchResult result = apnsProvider.dispatchBatch(
                app, credential, List.of(), PushNotificationMessage.builder().build()
        );
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
    }

    @Test
    void testDispatchBatch_SimulatedSampleTokens() {
        when(cryptoVaultService.decrypt("vault:encrypted:p8")).thenReturn(sampleP8Pem);

        List<PushRecipient> recipients = List.of(
                PushRecipient.builder()
                        .deviceId(UUID.randomUUID())
                        .platform(DevicePlatform.IOS)
                        .pushToken("apns_sample_token_valid_1")
                        .build(),
                PushRecipient.builder()
                        .deviceId(UUID.randomUUID())
                        .platform(DevicePlatform.IOS)
                        .pushToken("apns_sample_token_unregistered_2")
                        .build()
        );

        PushNotificationMessage message = PushNotificationMessage.builder()
                .notificationId(UUID.randomUUID())
                .title("Special Offer")
                .body("20% off all shoes today!")
                .build();

        BatchDispatchResult result = apnsProvider.dispatchBatch(app, credential, recipients, message);

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(1, result.getFailures().size());
        assertTrue(result.getFailures().get(0).isShouldDeactivate());
        assertEquals("Unregistered", result.getFailures().get(0).getErrorCode());
    }
}
