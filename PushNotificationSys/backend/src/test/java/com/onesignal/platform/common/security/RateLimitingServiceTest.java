package com.onesignal.platform.common.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private RateLimitingService rateLimitingService;

    @Test
    @DisplayName("tryAcquire allows request when count <= limit")
    void testAllowWhenUnderLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(5L);

        RateLimitResult result = rateLimitingService.tryAcquire("SDK", "test-client", 100L);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getLimit()).isEqualTo(100L);
        assertThat(result.getRemaining()).isEqualTo(95L);
        assertThat(result.getResetSeconds()).isPositive();
    }

    @Test
    @DisplayName("tryAcquire sets TTL on initial counter creation")
    void testSetsTtlOnFirstIncrement() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RateLimitResult result = rateLimitingService.tryAcquire("SDK", "test-client", 100L);

        assertThat(result.isAllowed()).isTrue();
        verify(redisTemplate, times(1)).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("tryAcquire rejects request and records metric when count > limit")
    void testRejectWhenOverLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(101L);
        when(meterRegistry.counter(eq("push_rate_limit_exceeded_total"), eq("type"), eq("SDK"))).thenReturn(counter);

        RateLimitResult result = rateLimitingService.tryAcquire("SDK", "test-client", 100L);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRemaining()).isZero();
        verify(counter, times(1)).increment();
    }

    @Test
    @DisplayName("tryAcquire fails open gracefully when Redis throws an exception")
    void testFailOpenOnRedisException() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        RateLimitResult result = rateLimitingService.tryAcquire("SDK", "test-client", 100L);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isEqualTo(100L);
    }
}
