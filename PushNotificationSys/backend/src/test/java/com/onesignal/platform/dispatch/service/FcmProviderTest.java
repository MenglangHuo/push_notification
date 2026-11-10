package com.onesignal.platform.dispatch.service;

import com.onesignal.platform.app.application.CryptoVaultService;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.dispatch.domain.BatchDispatchResult;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import com.onesignal.platform.dispatch.domain.PushRecipient;
import com.onesignal.platform.notification.domain.NotificationPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FcmProviderTest {

    @Mock
    private CryptoVaultService cryptoVaultService;

    @InjectMocks
    private FcmProvider fcmProvider;

    private App app;
    private PushNotificationMessage message;

    @BeforeEach
    void setUp() {
        UUID appId = UUID.randomUUID();
        app = App.builder().name("Test App").build();
        app.setId(appId);
        message = PushNotificationMessage.builder()
                .notificationId(UUID.randomUUID())
                .appId(appId)
                .title("Special Offer")
                .body("Get 30% off today!")
                .priority(NotificationPriority.HIGH)
                .build();
    }

    @Test
    void testDispatchSimulatedWithValidAndUnregisteredTokens() {
        List<PushRecipient> recipients = List.of(
                PushRecipient.builder()
                        .deviceId(UUID.randomUUID())
                        .platform(DevicePlatform.ANDROID)
                        .pushToken("fcm_valid_token_1")
                        .build(),
                PushRecipient.builder()
                        .deviceId(UUID.randomUUID())
                        .platform(DevicePlatform.ANDROID)
                        .pushToken("unregistered_device_token_2")
                        .build()
        );

        // credential null triggers simulated mode
        BatchDispatchResult result = fcmProvider.dispatchBatch(app, null, recipients, message);

        assertNotNull(result);
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(1, result.getFailures().size());
        assertTrue(result.getFailures().get(0).isShouldDeactivate());
        assertEquals("UNREGISTERED", result.getFailures().get(0).getErrorCode());
    }

    @Test
    void testEmptyRecipientsReturnsZero() {
        BatchDispatchResult result = fcmProvider.dispatchBatch(app, null, List.of(), message);
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
    }
}
