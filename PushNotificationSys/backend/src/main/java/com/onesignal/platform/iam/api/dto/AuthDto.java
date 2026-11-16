package com.onesignal.platform.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AuthDto {

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @Builder
    public static class LoginResponse {
        private String accessToken;
        private boolean mfaRequired;
        private String mfaTicket;
        private UserProfileResponse user;
    }

    @Data
    public static class MfaVerifyRequest {
        @NotBlank(message = "MFA ticket is required")
        private String mfaTicket;

        @NotBlank(message = "TOTP code is required")
        private String totpCode;
    }

    @Data
    @Builder
    public static class RefreshTokenResponse {
        private String accessToken;
    }

    @Data
    @Builder
    public static class UserProfileResponse {
        private UUID id;
        private String email;
        private String fullName;
        private String avatarUrl;
        private boolean superAdmin;
        private boolean mfaEnabled;
        private List<OrgMembershipSummary> organizations;
    }

    @Data
    public static class UpdateProfileRequest {
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        private String fullName;

        @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters")
        private String avatarUrl;

        private Boolean removeAvatar;
    }

    @Data
    @Builder
    public static class MfaSetupResponse {
        private String secretKey;
        private String qrCodeDataUrl;
        private String otpauthUri;
        private List<String> backupCodes;
    }

    @Data
    public static class MfaConfirmRequest {
        @NotBlank(message = "TOTP code is required")
        private String totpCode;
    }

    @Data
    public static class MfaDisableRequest {
        @NotBlank(message = "Password is required to disable MFA")
        private String password;
    }

    @Data
    @Builder
    public static class MfaStatusResponse {
        private boolean enabled;
        private int remainingBackupCodes;
    }

    @Data
    @Builder
    public static class OrgMembershipSummary {
        private UUID orgId;
        private String name;
        private String slug;
        private String tier;
        private List<String> roles;
        private Set<String> permissions;
    }
}
