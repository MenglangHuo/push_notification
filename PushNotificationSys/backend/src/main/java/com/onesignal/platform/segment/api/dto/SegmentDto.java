package com.onesignal.platform.segment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class SegmentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSegmentRequest {
        @NotBlank(message = "Segment name is required")
        @Size(max = 128, message = "Segment name cannot exceed 128 characters")
        private String name;

        @Size(max = 512, message = "Description cannot exceed 512 characters")
        private String description;

        private Map<String, Object> filterAst;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSegmentRequest {
        @NotBlank(message = "Segment name is required")
        @Size(max = 128, message = "Segment name cannot exceed 128 characters")
        private String name;

        @Size(max = 512, message = "Description cannot exceed 512 characters")
        private String description;

        private Map<String, Object> filterAst;

        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentResponse {
        private UUID id;
        private UUID appId;
        private String name;
        private String description;
        private Map<String, Object> filterAst;
        private boolean active;
        private long estimatedCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceEstimateRequest {
        private Map<String, Object> filterAst;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceEstimateResponse {
        private long totalCount;
        private long androidCount;
        private long iosCount;
        private long webCount;
    }
}
