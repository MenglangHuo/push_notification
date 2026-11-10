package com.onesignal.platform.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RateLimitResult {
    private final boolean allowed;
    private final long limit;
    private final long remaining;
    private final long resetSeconds;

    public static RateLimitResult allow(long limit, long remaining, long resetSeconds) {
        return new RateLimitResult(true, limit, remaining, resetSeconds);
    }

    public static RateLimitResult reject(long limit, long resetSeconds) {
        return new RateLimitResult(false, limit, 0, resetSeconds);
    }
}
