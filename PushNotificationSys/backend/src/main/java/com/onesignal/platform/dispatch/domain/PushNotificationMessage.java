package com.onesignal.platform.dispatch.domain;

import com.onesignal.platform.notification.domain.NotificationPriority;
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
public class PushNotificationMessage implements Serializable {
    private UUID notificationId;
    private UUID appId;
    private String title;
    private String body;
    private String imageUrl;
    private String deepLink;
    private Map<String, Object> data;
    private NotificationPriority priority;
}
