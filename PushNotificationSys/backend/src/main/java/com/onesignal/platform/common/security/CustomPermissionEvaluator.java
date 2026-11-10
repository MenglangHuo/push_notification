package com.onesignal.platform.common.security;

import com.onesignal.platform.iam.application.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final PermissionCacheService permissionCacheService;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }

        if (TenantContextHolder.isSuperAdmin()) {
            return true;
        }

        UUID userId = TenantContextHolder.getUserId();
        UUID orgId = TenantContextHolder.getOrganizationId();
        if (userId == null || orgId == null) {
            return false;
        }

        UUID appId = null;
        if (targetDomainObject instanceof UUID uuid) {
            appId = uuid;
        } else if (targetDomainObject instanceof String str && !str.isBlank()) {
            try {
                appId = UUID.fromString(str);
            } catch (IllegalArgumentException ignored) {}
        } else {
            appId = TenantContextHolder.getAppId();
        }

        String requiredPermission = permission.toString();
        Set<String> effectivePermissions = permissionCacheService.getPermissions(userId, orgId, appId);
        return effectivePermissions.contains(requiredPermission);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }

        if (TenantContextHolder.isSuperAdmin()) {
            return true;
        }

        UUID userId = TenantContextHolder.getUserId();
        UUID orgId = TenantContextHolder.getOrganizationId();
        if (userId == null || orgId == null) {
            return false;
        }

        UUID appId = null;
        if ("app".equalsIgnoreCase(targetType) && targetId != null) {
            try {
                appId = targetId instanceof UUID ? (UUID) targetId : UUID.fromString(targetId.toString());
            } catch (IllegalArgumentException ignored) {}
        } else {
            appId = TenantContextHolder.getAppId();
        }

        String requiredPermission = permission.toString();
        Set<String> effectivePermissions = permissionCacheService.getPermissions(userId, orgId, appId);
        return effectivePermissions.contains(requiredPermission);
    }
}
