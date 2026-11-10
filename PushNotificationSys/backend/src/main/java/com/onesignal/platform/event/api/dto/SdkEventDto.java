package com.onesignal.platform.event.api.dto;

import com.onesignal.platform.event.domain.ClientEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SdkEventDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestEventsRequest {
        @NotEmpty(message = "Events array cannot be empty")
        @Valid
        private List<EventItem> events;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventItem {
        private UUID notificationId;
        private UUID deviceId;

        @NotNull(message = "eventType is required")
        private ClientEventType eventType;

        private String eventName;
        private Map<String, Object> properties;
        private Instant timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestEventsResponse {
        private int acceptedCount;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientEventResponse {
        private UUID id;
        private UUID appId;
        private UUID notificationId;
        private UUID deviceId;
        private ClientEventType eventType;
        private String eventName;
        private Map<String, Object> properties;
        private Instant clientTimestamp;
        private Instant createdAt;
    }
}
