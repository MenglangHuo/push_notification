package com.onesignal.platform.app.api.dto;

import com.onesignal.platform.app.domain.ProviderType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class CredentialDto {

    @Data
    public static class SaveCredentialRequest {
        @NotNull(message = "Provider type is required")
        private ProviderType providerType;

        // Raw credential payload (e.g. Firebase service account JSON, or APNs .p8 private key text)
        private String rawCredential;

        // APNs metadata
        private String keyId;
        private String teamId;
        private String bundleId;
    }

    @Data
    @Builder
    public static class CredentialResponse {
        private UUID id;
        private ProviderType providerType;
        private boolean active;
        private Map<String, Object> metadataMasked;
        private Instant updatedAt;
    }

    @Data
    @Builder
    public static class GenerateVapidResponse {
        private String publicKey; // Public VAPID key to embed in client SDKs / Web Push subscriber
    }
}
