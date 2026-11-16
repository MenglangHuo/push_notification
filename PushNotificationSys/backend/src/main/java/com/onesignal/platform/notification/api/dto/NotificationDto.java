package com.onesignal.platform.notification.api.dto;

import com.onesignal.platform.notification.domain.NotificationPriority;
import com.onesignal.platform.notification.domain.NotificationStatus;
import com.onesignal.platform.notification.domain.TargetType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class NotificationDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateNotificationRequest {
        private UUID appId;

        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Body message is required")
        private String body;

        @com.fasterxml.jackson.annotation.JsonAlias({"idempotency", "idempotency_key"})
        private String idempotencyKey;

        public String getIdempotency() {
            return idempotencyKey;
        }

        public void setIdempotency(String idempotency) {
            this.idempotencyKey = idempotency;
        }

        private String imageUrl;
        private String deepLink;
        private Map<String, Object> customData;
        private NotificationPriority priority;
        private TargetType targetType;
        private Map<String, Object> targetPayload;
        private Instant scheduledAt;
        private com.onesignal.platform.notification.domain.ScheduleType scheduleType;
        private String userTargetTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationResponse {
        private UUID id;
        private UUID appId;
        private String title;
        private String body;
        private String imageUrl;
        private String deepLink;
        private Map<String, Object> customData;
        private NotificationPriority priority;
        private TargetType targetType;
        private Map<String, Object> targetPayload;
        private NotificationStatus status;
        private String idempotencyKey;

        @com.fasterxml.jackson.annotation.JsonProperty("idempotency")
        public String getIdempotency() {
            return idempotencyKey;
        }
        private Instant scheduledAt;
        private com.onesignal.platform.notification.domain.ScheduleType scheduleType;
        private String userTargetTime;
        private int sentCount;
        private int deliveredCount;
        private int failedCount;
        private int openedCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryLogResponse {
        private UUID id;
        private UUID notificationId;
        private UUID deviceId;
        private UUID appId;
        private String provider;
        private String status;
        private String statusCode;
        private String errorMessage;
        private Integer latencyMs;
        private Instant createdAt;
    }
}
