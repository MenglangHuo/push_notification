package com.onesignal.platform.realtime.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeNotificationPayload implements Serializable {
    private UUID notificationId;
    private UUID appId;
    private String targetUserId;
    private String targetDeviceId;
    private String title;
    private String body;
    private String imageUrl;
    private String deepLink;
    private Map<String, Object> customData;
    private String priority;
    private Long timestamp;
}
