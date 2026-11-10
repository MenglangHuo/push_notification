package com.onesignal.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.sdk-per-minute:120}")
    private long sdkLimit;

    @Value("${app.rate-limit.api-per-minute:300}")
    private long apiLimit;

    @Value("${app.rate-limit.admin-per-minute:600}")
    private long adminLimit;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Bypass swagger, actuator, websocket, error, and static endpoints
        if (path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/actuator") ||
            path.startsWith("/ws") ||
            path.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        String keyType;
        String identifier;
        long limit;

        UUID appId = TenantContextHolder.getAppId();
        String clientIp = getClientIp(request);

        if (path.startsWith("/sdk/v1/")) {
            keyType = "SDK";
            String sdkKey = request.getHeader("X-Sdk-Key");
            identifier = (appId != null ? appId.toString() : (sdkKey != null ? sdkKey : clientIp)) + ":" + clientIp;
            limit = sdkLimit;
        } else if (path.startsWith("/api/v1/")) {
            keyType = "API";
            String apiKey = request.getHeader("X-Api-Key");
            identifier = appId != null ? appId.toString() : (apiKey != null ? apiKey : clientIp);
            limit = apiLimit;
        } else if (path.startsWith("/admin/api/v1/")) {
            keyType = "ADMIN";
            UUID userId = TenantContextHolder.getUserId();
            identifier = userId != null ? userId.toString() : clientIp;
            limit = adminLimit;
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult result = rateLimitingService.tryAcquire(keyType, identifier, limit);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetSeconds()));

        if (!result.isAllowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.getResetSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = Map.of(
                    "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "error", "TOO_MANY_REQUESTS",
                    "message", String.format("Rate limit quota exceeded for %s endpoint. Please retry in %d seconds.", keyType, result.getResetSeconds()),
                    "retryAfterSeconds", result.getResetSeconds()
            );

            objectMapper.writeValue(response.getOutputStream(), errorBody);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
