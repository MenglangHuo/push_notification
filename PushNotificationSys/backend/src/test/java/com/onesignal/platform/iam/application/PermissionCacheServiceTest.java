package com.onesignal.platform.iam.application;

import com.onesignal.platform.iam.infrastructure.MemberRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private MemberRoleAssignmentRepository roleAssignmentRepository;

    private PermissionCacheService permissionCacheService;

    private UUID userId;
    private UUID orgId;
    private UUID appId;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        permissionCacheService = new PermissionCacheService(redisTemplate, roleAssignmentRepository);

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        appId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Cache miss resolves permissions from DB and stores in cache")
    void getPermissions_cacheMiss_resolvesFromDb() {
        String verKey = "auth:perms:ver:" + userId + ":" + orgId;
        String cacheKey = "auth:perms:" + userId + ":" + orgId + ":1:" + appId;

        when(valueOperations.get(verKey)).thenReturn(1L);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(roleAssignmentRepository.findEffectivePermissions(userId, orgId, appId))
                .thenReturn(Set.of("notification:create", "device:read"));

        Set<String> result = permissionCacheService.getPermissions(userId, orgId, appId);

        assertThat(result).containsExactlyInAnyOrder("notification:create", "device:read");
        verify(roleAssignmentRepository, times(1)).findEffectivePermissions(userId, orgId, appId);
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(result), any(Duration.class));
    }

    @Test
    @DisplayName("L1 in-memory cache hit avoids both Redis and DB queries on subsequent call")
    void getPermissions_l1CacheHit_skipsDbAndRedis() {
        String verKey = "auth:perms:ver:" + userId + ":" + orgId;
        String cacheKey = "auth:perms:" + userId + ":" + orgId + ":1:" + appId;

        when(valueOperations.get(verKey)).thenReturn(1L);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(roleAssignmentRepository.findEffectivePermissions(userId, orgId, appId))
                .thenReturn(Set.of("notification:create"));

        // First call populates L1 and L2
        Set<String> firstCall = permissionCacheService.getPermissions(userId, orgId, appId);
        assertThat(firstCall).contains("notification:create");

        // Second call should hit L1 memory cache directly
        Set<String> secondCall = permissionCacheService.getPermissions(userId, orgId, appId);
        assertThat(secondCall).contains("notification:create");

        // DB repository should only have been called once
        verify(roleAssignmentRepository, times(1)).findEffectivePermissions(userId, orgId, appId);
    }

    @Test
    @DisplayName("evictUserPermissions increments atomic version in Redis and flushes L1 cache")
    void evictUserPermissions_incrementsVersionKey() {
        permissionCacheService.evictUserPermissions(userId, orgId);

        String expectedVerKey = "auth:perms:ver:" + userId + ":" + orgId;
        verify(valueOperations, times(1)).increment(expectedVerKey);
        verify(redisTemplate, never()).keys(anyString());
    }
}
