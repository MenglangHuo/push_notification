package com.onesignal.platform.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class MemberDto {

    @Data
    public static class InviteMemberRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotNull(message = "Role ID is required")
        private UUID roleId;

        private UUID appId; // nullable: org-wide if null
    }

    @Data
    public static class AcceptInviteRequest {
        @NotBlank(message = "Invitation token is required")
        private String token;

        @NotBlank(message = "Full name is required")
        private String fullName;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @Builder
    public static class InvitationResponse {
        private UUID id;
        private String email;
        private UUID roleId;
        private String roleName;
        private UUID appId;
        private Instant expiresAt;
        private Instant acceptedAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    public static class MemberResponse {
        private UUID membershipId;
        private UUID userId;
        private String email;
        private String fullName;
        private String status;
        private List<RoleAssignmentInfo> roleAssignments;
    }

    @Data
    @Builder
    public static class RoleAssignmentInfo {
        private UUID assignmentId;
        private UUID roleId;
        private String roleName;
        private UUID appId;
    }

    @Data
    public static class AssignRoleRequest {
        @NotNull(message = "Role ID is required")
        private UUID roleId;
        private UUID appId;
    }
}
