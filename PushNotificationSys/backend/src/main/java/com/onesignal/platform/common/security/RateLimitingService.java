package com.onesignal.platform.common.security;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public RateLimitResult tryAcquire(String keyType, String identifier, long limit) {
        if (identifier == null || identifier.isBlank()) {
            identifier = "anonymous";
        }

        long epochSeconds = Instant.now().getEpochSecond();
        long currentMinute = epochSeconds / 60;
        long resetSeconds = 60 - (epochSeconds % 60);

        String redisKey = String.format("ratelimit:%s:%s:%d", keyType, identifier, currentMinute);

        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(65));
            }

            long currentCount = count != null ? count : 1;
            if (currentCount > limit) {
                meterRegistry.counter("push_rate_limit_exceeded_total", "type", keyType).increment();
                log.warn("Rate limit exceeded for [{}:{}] (current: {}, limit: {})", keyType, identifier, currentCount, limit);
                return RateLimitResult.reject(limit, resetSeconds);
            }

            long remaining = Math.max(0, limit - currentCount);
            return RateLimitResult.allow(limit, remaining, resetSeconds);
        } catch (Exception e) {
            log.error("Redis rate limit check failed for key [{}], failing open", redisKey, e);
            // Fail open on Redis connectivity error to avoid dropping critical push requests
            return RateLimitResult.allow(limit, limit, resetSeconds);
        }
    }
}
