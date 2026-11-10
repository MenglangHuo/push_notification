package com.onesignal.platform.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.dispatch.domain.PushNotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApnsPayloadBuilder {

    private final ObjectMapper objectMapper;

    /**
     * Builds standard Apple APNs JSON payload according to Apple's specifications.
     */
    public String buildPayloadJson(PushNotificationMessage message) {
        try {
            Map<String, Object> root = new HashMap<>();

            // Build 'aps' dictionary
            Map<String, Object> aps = new HashMap<>();

            Map<String, Object> alert = new HashMap<>();
            if (message.getTitle() != null && !message.getTitle().isBlank()) {
                alert.put("title", message.getTitle());
            }
            if (message.getBody() != null && !message.getBody().isBlank()) {
                alert.put("body", message.getBody());
            }
            aps.put("alert", alert);

            aps.put("sound", "default");
            aps.put("badge", 1);

            // If image is present, set mutable-content = 1 for Notification Service Extension
            if (message.getImageUrl() != null && !message.getImageUrl().isBlank()) {
                aps.put("mutable-content", 1);
                root.put("image_url", message.getImageUrl());
            }

            root.put("aps", aps);

            if (message.getDeepLink() != null && !message.getDeepLink().isBlank()) {
                root.put("deep_link", message.getDeepLink());
            }

            if (message.getData() != null && !message.getData().isEmpty()) {
                root.put("custom_data", message.getData());
                // Also merge custom data at root level for iOS SDK convenience
                message.getData().forEach((k, v) -> {
                    if (!"aps".equals(k) && !root.containsKey(k)) {
                        root.put(k, v);
                    }
                });
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize APNs payload: {}", e.getMessage());
            return "{\"aps\":{\"alert\":{\"title\":\"" + escapeJson(message.getTitle()) + "\",\"body\":\"" + escapeJson(message.getBody()) + "\"},\"sound\":\"default\"}}";
        }
    }

    public byte[] buildPayloadBytes(PushNotificationMessage message) {
        return buildPayloadJson(message).getBytes(StandardCharsets.UTF_8);
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
