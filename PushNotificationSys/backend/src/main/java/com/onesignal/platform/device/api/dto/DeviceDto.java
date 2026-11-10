package com.onesignal.platform.device.api.dto;

import com.onesignal.platform.device.domain.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class DeviceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterDeviceRequest {
        @NotNull(message = "Platform is required (ANDROID, IOS, WEB)")
        private DevicePlatform platform;

        @NotBlank(message = "Push token is required")
        private String pushToken;

        private String externalUserId;
        private String webAuthKey;
        private String webP256dhKey;
        private String deviceModel;
        private String osVersion;
        private String appVersion;
        private String language;
        private String timezone;
        private Map<String, String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceResponse {
        private UUID id;
        private UUID appId;
        private String externalUserId;
        private DevicePlatform platform;
        private String pushTokenMasked;
        private String deviceModel;
        private String osVersion;
        private String appVersion;
        private String language;
        private String timezone;
        private boolean active;
        private Instant lastActiveAt;
        private Instant createdAt;
        private Map<String, String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceStatsResponse {
        private long totalDevices;
        private long activeDevices;
        private long androidActive;
        private long iosActive;
        private long webActive;
    }
}
