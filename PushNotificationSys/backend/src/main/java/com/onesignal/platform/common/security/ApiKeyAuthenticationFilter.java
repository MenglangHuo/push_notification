package com.onesignal.platform.common.security;

import com.onesignal.platform.apikey.application.ApiKeyService;
import com.onesignal.platform.apikey.domain.ApiKey;
import com.onesignal.platform.apikey.domain.KeyType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Customer Server REST API (/api/v1/**)
        String apiKeyHeader = request.getHeader("X-Api-Key");
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            Optional<ApiKey> keyOpt = apiKeyService.verifyAndTouchKey(apiKeyHeader.trim(), KeyType.SERVER_REST);
            if (keyOpt.isPresent()) {
                authenticateApiKey(request, keyOpt.get(), "ROLE_API_CLIENT");
            }
        }

        // 2. Client Mobile / Web SDK API (/sdk/v1/**)
        String sdkKeyHeader = request.getHeader("X-Sdk-Key");
        if (sdkKeyHeader != null && !sdkKeyHeader.isBlank()) {
            Optional<ApiKey> keyOpt = apiKeyService.verifyAndTouchKey(sdkKeyHeader.trim(), KeyType.SDK_PUBLIC);
            if (keyOpt.isPresent()) {
                authenticateApiKey(request, keyOpt.get(), "ROLE_SDK_CLIENT");
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateApiKey(HttpServletRequest request, ApiKey apiKey, String baseRole) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(baseRole));

        if (apiKey.getScopes() != null) {
            for (String scope : apiKey.getScopes()) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
        }

        // Principal is appId
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(apiKey.getApp().getId(), apiKey.getKeyPrefix(), authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Store attributes for TenantContextFilter
        request.setAttribute("authenticatedAppId", apiKey.getApp().getId());
        request.setAttribute("authenticatedOrgId", apiKey.getApp().getOrganization().getId());
    }
}
