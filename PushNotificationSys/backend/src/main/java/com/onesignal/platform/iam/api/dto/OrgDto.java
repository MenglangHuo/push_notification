package com.onesignal.platform.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class OrgDto {

    @Data
    public static class CreateOrgRequest {
        @NotBlank(message = "Organization name is required")
        private String name;

        @NotBlank(message = "Slug is required")
        private String slug;
    }

    @Data
    public static class UpdateOrgRequest {
        @NotBlank(message = "Organization name is required")
        private String name;
        private String tier;
        private Map<String, Object> quotas;
    }

    @Data
    @Builder
    public static class OrgResponse {
        private UUID id;
        private String name;
        private String slug;
        private String status;
        private String tier;
        private Map<String, Object> quotas;
        private Instant createdAt;
    }
}
