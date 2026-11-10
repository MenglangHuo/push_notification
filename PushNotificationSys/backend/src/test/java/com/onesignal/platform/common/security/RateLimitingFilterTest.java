package com.onesignal.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private RateLimitingService rateLimitingService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RateLimitingFilter rateLimitingFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimitingFilter, "sdkLimit", 100L);
        ReflectionTestUtils.setField(rateLimitingFilter, "apiLimit", 200L);
        ReflectionTestUtils.setField(rateLimitingFilter, "adminLimit", 500L);
    }

    @Test
    @DisplayName("Request within quota sets X-RateLimit headers and executes filter chain")
    void testRequestWithinQuota() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sdk/v1/devices");
        request.addHeader("X-Sdk-Key", "sdk_live_test123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitingService.tryAcquire(eq("SDK"), anyString(), eq(100L)))
                .thenReturn(RateLimitResult.allow(100L, 99L, 45L));

        rateLimitingFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("99");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("45");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Request exceeding quota returns HTTP 429 Too Many Requests and blocks chain")
    void testRequestExceedingQuota() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sdk/v1/events");
        request.addHeader("X-Sdk-Key", "sdk_live_test123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitingService.tryAcquire(eq("SDK"), anyString(), eq(100L)))
                .thenReturn(RateLimitResult.reject(100L, 30L));

        rateLimitingFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getContentAsString()).contains("TOO_MANY_REQUESTS");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Bypass endpoints like actuator or swagger are not intercepted")
    void testBypassEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(rateLimitingService, never()).tryAcquire(anyString(), anyString(), anyLong());
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
