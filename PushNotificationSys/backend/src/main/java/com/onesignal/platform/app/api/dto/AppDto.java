package com.onesignal.platform.app.api.dto;

import com.onesignal.platform.app.domain.ProviderType;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AppDto {

    @Data
    public static class CreateAppRequest {
        @NotBlank(message = "App name is required")
        private String name;
        private String description;
    }

    @Data
    public static class UpdateAppRequest {
        @NotBlank(message = "App name is required")
        private String name;
        private String description;
    }

    @Data
    @Builder
    public static class AppResponse {
        private UUID id;
        private UUID organizationId;
        private String name;
        private String description;
        private List<ProviderType> configuredProviders;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
