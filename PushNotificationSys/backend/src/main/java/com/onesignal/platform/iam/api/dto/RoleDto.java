package com.onesignal.platform.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RoleDto {

    @Data
    public static class CreateRoleRequest {
        @NotBlank(message = "Role name is required")
        private String name;

        private String description;

        @NotEmpty(message = "At least one permission code is required")
        private Set<String> permissionCodes;
    }

    @Data
    public static class UpdateRoleRequest {
        @NotBlank(message = "Role name is required")
        private String name;

        private String description;

        @NotEmpty(message = "At least one permission code is required")
        private Set<String> permissionCodes;
    }

    @Data
    @Builder
    public static class RoleResponse {
        private UUID id;
        private UUID organizationId;
        private String name;
        private String description;
        private boolean system;
        private List<String> permissions;
    }

    @Data
    @Builder
    public static class PermissionResponse {
        private UUID id;
        private String code;
        private String resource;
        private String action;
        private String description;
    }
}
