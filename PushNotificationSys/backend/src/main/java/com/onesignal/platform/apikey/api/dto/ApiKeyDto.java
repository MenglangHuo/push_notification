package com.onesignal.platform.apikey.api.dto;

import com.onesignal.platform.apikey.domain.KeyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApiKeyDto {

    @Data
    public static class CreateApiKeyRequest {
        @NotBlank(message = "API key name is required")
        private String name;

        @NotNull(message = "Key type is required (SERVER_REST or SDK_PUBLIC)")
        private KeyType keyType;

        private List<String> scopes;
        private List<String> allowedOrigins;
        private List<String> allowedBundleIds;
        private Instant expiresAt;
    }

    @Data
    @Builder
    public static class ApiKeyCreatedResponse {
        private UUID id;
        private String name;
        private KeyType keyType;
        private String keyPrefix;
        private String rawSecretKey; // Shown ONCE only!
        private List<String> scopes;
        private List<String> allowedOrigins;
        private List<String> allowedBundleIds;
        private Instant expiresAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    public static class ApiKeySummaryResponse {
        private UUID id;
        private String name;
        private KeyType keyType;
        private String keyPrefix;
        private List<String> scopes;
        private List<String> allowedOrigins;
        private List<String> allowedBundleIds;
        private Instant expiresAt;
        private Instant lastUsedAt;
        private boolean revoked;
        private Instant createdAt;
    }

    @Data
    @Builder
    public static class RotateApiKeyResponse {
        private UUID id;
        private String name;
        private String keyPrefix;
        private String rawSecretKey; // Shown ONCE only!
    }
}
