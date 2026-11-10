package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.app.application.CryptoVaultService;
import com.onesignal.platform.app.application.VapidKeyGenerator;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.dispatch.domain.BatchDispatchResult;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import com.onesignal.platform.dispatch.domain.PushRecipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebPushProviderTest {

    @Mock
    private CryptoVaultService cryptoVaultService;

    private WebPushCryptoService webPushCryptoService;
    private SsrfValidator ssrfValidator;
    private ObjectMapper objectMapper;
    private WebPushProvider webPushProvider;

    private App app;
    private ProviderCredential credential;
    private VapidKeyGenerator.VapidKeyPair vapidKeyPair;

    @BeforeEach
    void setUp() {
        webPushCryptoService = new WebPushCryptoService();
        ssrfValidator = new SsrfValidator();
        objectMapper = new ObjectMapper();
        webPushProvider = new WebPushProvider(cryptoVaultService, webPushCryptoService, ssrfValidator, objectMapper);

        vapidKeyPair = new VapidKeyGenerator().generate();

        app = App.builder()
                .name("Acme Web Store")
                .build();
        app.setId(UUID.randomUUID());

        credential = ProviderCredential.builder()
                .app(app)
                .providerType(ProviderType.WEBPUSH)
                .credentialDataEncrypted("vault:encrypted:vapid_priv")
                .metadataMasked(Map.of("public_key", vapidKeyPair.getPublicKey()))
                .build();
    }

    @Test
    void testGetProviderType() {
        assertEquals(ProviderType.WEBPUSH, webPushProvider.getProviderType());
    }

    @Test
    void testTestConnection_Success() {
        when(cryptoVaultService.decrypt("vault:encrypted:vapid_priv")).thenReturn(vapidKeyPair.getPrivateKey());
        assertTrue(webPushProvider.testConnection(credential));
    }

    @Test
    void testTestConnection_NullOrEmpty() {
        assertFalse(webPushProvider.testConnection(null));
        ProviderCredential emptyCred = ProviderCredential.builder().build();
        assertFalse(webPushProvider.testConnection(emptyCred));
    }

    @Test
    void testDispatchBatch_EmptyRecipients() {
        BatchDispatchResult result = webPushProvider.dispatchBatch(
                app, credential, List.of(), PushNotificationMessage.builder().build()
        );
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
    }

    @Test
    void testDispatchBatch_SimulatedSampleTokens() {
        when(cryptoVaultService.decrypt("vault:encrypted:vapid_priv")).thenReturn(vapidKeyPair.getPrivateKey());

        List<PushRecipient> recipients = List.of(
                PushRecipient.builder()
                        .deviceId(UUID.randomUUID())
                        .platform(DevicePlatform.WEB)
                        .pushToken("https://fcm.googleapis.com/fcm/send/sample_web_token_valid")
                        .webAuthKey("auth_123")
                        .webP256dhKey("p256_123")
                        .build(),
                PushRecipient.builder()
                        .deviceId(UUID.randomUUID())
                        .platform(DevicePlatform.WEB)
                        .pushToken("https://fcm.googleapis.com/fcm/send/sample_web_token_expired")
                        .webAuthKey("auth_456")
                        .webP256dhKey("p256_456")
                        .build()
        );

        PushNotificationMessage message = PushNotificationMessage.builder()
                .notificationId(UUID.randomUUID())
                .title("Special Flash Sale")
                .body("Get 20% off all laptops!")
                .build();

        BatchDispatchResult result = webPushProvider.dispatchBatch(app, credential, recipients, message);

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(1, result.getFailures().size());
        assertTrue(result.getFailures().get(0).isShouldDeactivate());
        assertEquals("EXPIRED", result.getFailures().get(0).getErrorCode());
    }
}
