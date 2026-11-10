package com.onesignal.platform.common.security;

import com.onesignal.platform.iam.application.PermissionCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomPermissionEvaluatorTest {

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CustomPermissionEvaluator permissionEvaluator;

    private UUID userId;
    private UUID orgId;
    private UUID appId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        appId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Super Admin always evaluates to true for any permission")
    void hasPermission_superAdmin_returnsTrue() {
        TenantContextHolder.setContext(TenantContextHolder.TenantContext.builder()
                .userId(userId)
                .organizationId(orgId)
                .superAdmin(true)
                .build());

        boolean result = permissionEvaluator.hasPermission(authentication, orgId, "org", "org:delete");
        assertThat(result).isTrue();
        verifyNoInteractions(permissionCacheService);
    }

    @Test
    @DisplayName("User with granted permission in org returns true")
    void hasPermission_grantedPermission_returnsTrue() {
        TenantContextHolder.setContext(TenantContextHolder.TenantContext.builder()
                .userId(userId)
                .organizationId(orgId)
                .appId(appId)
                .superAdmin(false)
                .build());

        when(permissionCacheService.getPermissions(userId, orgId, appId))
                .thenReturn(Set.of("notification:create", "notification:read"));

        boolean result = permissionEvaluator.hasPermission(authentication, appId, "app", "notification:create");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("User lacking permission returns false")
    void hasPermission_missingPermission_returnsFalse() {
        TenantContextHolder.setContext(TenantContextHolder.TenantContext.builder()
                .userId(userId)
                .organizationId(orgId)
                .appId(appId)
                .superAdmin(false)
                .build());

        when(permissionCacheService.getPermissions(userId, orgId, appId))
                .thenReturn(Set.of("notification:read"));

        boolean result = permissionEvaluator.hasPermission(authentication, appId, "app", "notification:create");
        assertThat(result).isFalse();
    }
}
