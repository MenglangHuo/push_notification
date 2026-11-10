package com.onesignal.platform.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtimeNotificationRelayServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ObjectMapper objectMapper;
    private RealtimeNotificationRelayService relayService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        relayService = new RealtimeNotificationRelayService(redisTemplate, messagingTemplate, objectMapper);
    }

    @Test
    void testPublish_SendsToRedisChannel() {
        RealtimeNotificationPayload payload = RealtimeNotificationPayload.builder()
                .notificationId(UUID.randomUUID())
                .appId(UUID.randomUUID())
                .title("Flash Deal!")
                .body("50% discount today")
                .build();

        relayService.publish(payload);
        verify(redisTemplate).convertAndSend(eq(RealtimeNotificationRelayService.REDIS_CHANNEL), anyString());
    }

    @Test
    void testOnRedisMessage_BroadcastsToStompTopics() throws Exception {
        UUID appId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        RealtimeNotificationPayload payload = RealtimeNotificationPayload.builder()
                .notificationId(notifId)
                .appId(appId)
                .title("Breaking News")
                .body("Store reopened")
                .customData(Map.of("category", "news"))
                .build();

        String json = objectMapper.writeValueAsString(payload);
        relayService.onRedisMessage(json);

        verify(messagingTemplate).convertAndSend(eq("/topic/apps/" + appId + "/announcements"), any(RealtimeNotificationPayload.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/apps/" + appId + "/live-feed"), any(RealtimeNotificationPayload.class));
    }

    @Test
    void testOnRedisMessage_DirectUserAndDevice() throws Exception {
        UUID appId = UUID.randomUUID();
        RealtimeNotificationPayload payload = RealtimeNotificationPayload.builder()
                .notificationId(UUID.randomUUID())
                .appId(appId)
                .targetUserId("user_alpha")
                .targetDeviceId("device_beta")
                .title("Personal Alert")
                .body("Your order #123 is ready")
                .build();

        String json = objectMapper.writeValueAsString(payload);
        relayService.onRedisMessage(json);

        verify(messagingTemplate).convertAndSendToUser(eq("user_alpha"), eq("/queue/notifications"), any(RealtimeNotificationPayload.class));
        verify(messagingTemplate).convertAndSendToUser(eq("device_beta"), eq("/queue/notifications"), any(RealtimeNotificationPayload.class));
    }
}
