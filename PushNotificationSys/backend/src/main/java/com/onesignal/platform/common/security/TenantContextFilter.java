package com.onesignal.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Pattern ORG_URI_PATTERN = Pattern.compile("/organizations/([a-fA-F0-9\\-]{36})");
    private static final Pattern APP_URI_PATTERN = Pattern.compile("/apps/([a-fA-F0-9\\-]{36})");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            UUID orgId = parseUuid(request.getHeader("X-Org-Id"));
            UUID appId = parseUuid(request.getHeader("X-App-Id"));

            String uri = request.getRequestURI();
            if (orgId == null) {
                Matcher orgMatcher = ORG_URI_PATTERN.matcher(uri);
                if (orgMatcher.find()) {
                    orgId = parseUuid(orgMatcher.group(1));
                }
            }

            if (appId == null) {
                Matcher appMatcher = APP_URI_PATTERN.matcher(uri);
                if (appMatcher.find()) {
                    appId = parseUuid(appMatcher.group(1));
                } else if (request.getAttribute("authenticatedAppId") instanceof UUID attrAppId) {
                    appId = attrAppId;
                }
            }

            if (orgId == null && request.getAttribute("authenticatedOrgId") instanceof UUID attrOrgId) {
                orgId = attrOrgId;
            }

            UUID userId = null;
            boolean isSuperAdmin = false;

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID uId) {
                boolean isApiKey = auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_API_CLIENT".equals(a.getAuthority()) || "ROLE_SDK_CLIENT".equals(a.getAuthority()));
                if (!isApiKey) {
                    userId = uId;
                    isSuperAdmin = auth.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
                }
            }

            TenantContextHolder.setContext(TenantContextHolder.TenantContext.builder()
                    .userId(userId)
                    .organizationId(orgId)
                    .appId(appId)
                    .superAdmin(isSuperAdmin)
                    .build());

            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private UUID parseUuid(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
