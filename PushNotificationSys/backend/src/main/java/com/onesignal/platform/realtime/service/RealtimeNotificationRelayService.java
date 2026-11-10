package com.onesignal.platform.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeNotificationRelayService {

    public static final String REDIS_CHANNEL = "realtime:notifications";

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a realtime notification event to Redis Pub/Sub to be received by all backend cluster instances.
     */
    public void publish(RealtimeNotificationPayload payload) {
        if (payload.getTimestamp() == null) {
            payload.setTimestamp(System.currentTimeMillis());
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("Relaying notification {} to Redis channel {}", payload.getNotificationId(), REDIS_CHANNEL);
            redisTemplate.convertAndSend(REDIS_CHANNEL, json);
        } catch (Exception e) {
            log.error("Failed to publish realtime notification to Redis: {}", e.getMessage(), e);
            // Fallback: deliver locally
            deliverLocally(payload);
        }
    }

    /**
     * Invoked when any cluster node broadcasts a message on Redis Pub/Sub channel.
     */
    public void onRedisMessage(String messageJson) {
        try {
            RealtimeNotificationPayload payload = objectMapper.readValue(messageJson, RealtimeNotificationPayload.class);
            deliverLocally(payload);
        } catch (Exception e) {
            log.error("Failed to parse Redis realtime notification message: {}", e.getMessage());
        }
    }

    private void deliverLocally(RealtimeNotificationPayload payload) {
        log.info("Delivering realtime notification {} locally via STOMP broker (app {})",
                payload.getNotificationId(), payload.getAppId());

        // 1. Direct user destination if targetUserId is set
        if (payload.getTargetUserId() != null && !payload.getTargetUserId().isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    payload.getTargetUserId(),
                    "/queue/notifications",
                    payload
            );
        }

        // 2. Direct device destination if targetDeviceId is set
        if (payload.getTargetDeviceId() != null && !payload.getTargetDeviceId().isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    payload.getTargetDeviceId(),
                    "/queue/notifications",
                    payload
            );
        }

        // 3. App-wide announcement topic for connected clients
        if (payload.getAppId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/apps/" + payload.getAppId() + "/announcements",
                    payload
            );

            // 4. Live feed topic for Portal operators & Dashboard
            messagingTemplate.convertAndSend(
                    "/topic/apps/" + payload.getAppId() + "/live-feed",
                    payload
            );
        }
    }
}
