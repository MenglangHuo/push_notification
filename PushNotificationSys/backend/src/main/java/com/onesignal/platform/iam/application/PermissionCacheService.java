package com.onesignal.platform.iam.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
    private static final String VERSION_PREFIX = "auth:perms:ver:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    // L1 in-memory cache to eliminate Redis network roundtrips for high-frequency checks
    private final Cache<String, Set<String>> l1Cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15))
            .maximumSize(10_000)
            .build();

    private long getVersion(UUID userId, UUID orgId) {
        String verKey = VERSION_PREFIX + userId + ":" + orgId;
        try {
            Object val = redisTemplate.opsForValue().get(verKey);
            if (val instanceof Number num) {
                return num.longValue();
            } else if (val != null) {
                return Long.parseLong(val.toString());
            }
        } catch (Exception e) {
            log.warn("Redis error reading permission version for user {} org {}: {}", userId, orgId, e.getMessage());
        }
        return 1L;
    }

    private String buildKey(UUID userId, UUID orgId, long version, UUID appId) {
        String appPart = appId != null ? appId.toString() : "_ALL_";
        return KEY_PREFIX + userId + ":" + orgId + ":" + version + ":" + appPart;
    }

    public Set<String> getPermissions(UUID userId, UUID orgId, UUID appId) {
        if (userId == null || orgId == null) {
            return Collections.emptySet();
        }

        long version = getVersion(userId, orgId);
        String cacheKey = buildKey(userId, orgId, version, appId);

        // 1. Check L1 in-memory cache
        Set<String> l1Cached = l1Cache.getIfPresent(cacheKey);
        if (l1Cached != null) {
            return l1Cached;
        }

        // 2. Check L2 Redis cache
        try {
            @SuppressWarnings("unchecked")
            Set<String> cached = (Set<String>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                l1Cache.put(cacheKey, cached);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis error reading permission cache: {}", e.getMessage());
        }

        // 3. Cache miss: resolve from DB
        Set<String> permissions = roleAssignmentRepository.findEffectivePermissions(userId, orgId, appId);

        try {
            redisTemplate.opsForValue().set(cacheKey, permissions, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis error setting permission cache: {}", e.getMessage());
        }

        l1Cache.put(cacheKey, permissions);
        return permissions;
    }

    /**
     * Atomically evicts permissions for a user across all app scopes in O(1) time
     * by incrementing the version counter, avoiding Redis KEYS scans.
     */
    public void evictUserPermissions(UUID userId, UUID orgId) {
        if (userId == null || orgId == null) {
            return;
        }
        try {
            String verKey = VERSION_PREFIX + userId + ":" + orgId;
            redisTemplate.opsForValue().increment(verKey);
            log.debug("Incremented permission version for user {} in org {}", userId, orgId);
        } catch (Exception e) {
            log.warn("Failed to increment permission version in Redis: {}", e.getMessage());
        }
        l1Cache.invalidateAll();
    }
}
