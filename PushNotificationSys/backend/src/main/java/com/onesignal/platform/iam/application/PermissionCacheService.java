package com.onesignal.platform.iam.application;

import com.onesignal.platform.iam.infrastructure.MemberRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MemberRoleAssignmentRepository roleAssignmentRepository;

    private static final String KEY_PREFIX = "auth:perms:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private String buildKey(UUID userId, UUID orgId, UUID appId) {
        String appPart = appId != null ? appId.toString() : "_ALL_";
        return KEY_PREFIX + userId + ":" + orgId + ":" + appPart;
    }

    public Set<String> getPermissions(UUID userId, UUID orgId, UUID appId) {
        if (userId == null || orgId == null) {
            return Collections.emptySet();
        }

        String cacheKey = buildKey(userId, orgId, appId);
        try {
            @SuppressWarnings("unchecked")
            Set<String> cached = (Set<String>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis error reading permission cache: {}", e.getMessage());
        }

        // Cache miss: resolve from DB
        Set<String> permissions = roleAssignmentRepository.findEffectivePermissions(userId, orgId, appId);

        try {
            redisTemplate.opsForValue().set(cacheKey, permissions, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis error setting permission cache: {}", e.getMessage());
        }

        return permissions;
    }

    public void evictUserPermissions(UUID userId, UUID orgId) {
        if (userId == null || orgId == null) {
            return;
        }
        try {
            String pattern = KEY_PREFIX + userId + ":" + orgId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Evicted {} permission cache keys for user {} in org {}", keys.size(), userId, orgId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict permission cache: {}", e.getMessage());
        }
    }
}
